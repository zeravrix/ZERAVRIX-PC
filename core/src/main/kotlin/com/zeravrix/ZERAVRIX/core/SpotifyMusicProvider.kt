package com.zeravrix.zeravrix.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.Properties
import java.util.UUID

/** Provider-neutral contract for optional online music metadata and control. */
interface MusicProvider {
    val id: String
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun playlists(): Result<List<SpotifyPlaylist>>
    suspend fun playlistTracks(playlistId: String): Result<List<SpotifyTrack>>
    suspend fun devices(): Result<List<SpotifyDevice>>
    suspend fun play(trackUri: String, deviceId: String? = null): Result<Unit>
    suspend fun pause(deviceId: String? = null): Result<Unit>
}

data class SpotifyPlaylist(val id: String, val name: String, val trackCount: Int, val imageUrl: String? = null)
data class SpotifyTrack(val id: String, val name: String, val artists: List<String>, val album: String?, val uri: String)
data class SpotifyDevice(val id: String, val name: String, val type: String, val active: Boolean, val restricted: Boolean)

/**
 * Official Spotify Web API integration. It controls playback on Spotify Connect
 * devices; it never downloads, caches, or decodes protected Spotify audio.
 * A client ID must be supplied by the application owner through the constructor
 * or ZERAVRIX_SPOTIFY_CLIENT_ID. No client secret is accepted or stored.
 */
class SpotifyMusicProvider(
    private val root: Path,
    private val clientId: String = System.getenv("ZERAVRIX_SPOTIFY_CLIENT_ID").orEmpty(),
    private val redirectPort: Int = 8765,
) : MusicProvider {
    override val id: String = "spotify"
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val tokenFile = root.resolve("spotify.properties")
    private val redirectUri = "http://127.0.0.1:$redirectPort/callback"
    private val stateLock = Any()

    override suspend fun connect(): Result<Unit> = runCatching {
        require(clientId.isNotBlank()) { "Spotify is not configured. Add a Spotify client ID first." }
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val server = ServerSocket(redirectPort, 1, java.net.InetAddress.getByName("127.0.0.1"))
        try {
            val params = linkedMapOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to "user-read-private user-read-email playlist-read-private playlist-read-collaborative user-read-playback-state user-modify-playback-state",
                "code_challenge_method" to "S256",
                "code_challenge" to challenge,
                "state" to state,
            )
            val authUrl = "https://accounts.spotify.com/authorize?" + params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            require(Desktop.isDesktopSupported()) { "The Windows default browser is unavailable." }
            Desktop.getDesktop().browse(URI(authUrl))
            server.soTimeout = 180_000
            val socket = server.accept()
            val requestLine = socket.getInputStream().bufferedReader().readLine().orEmpty()
            val query = requestLine.substringAfter('?', "").substringBefore(' ')
                .split('&').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { encode(it[0]) to decode(it[1]) } }.toMap()
            socket.getOutputStream().bufferedWriter().use { writer ->
                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\nYou may close this window and return to ZERAVRIX.")
                writer.flush()
            }
            socket.close()
            require(query["state"] == state) { "Spotify authorization state did not match." }
            val code = query["code"] ?: error("Spotify authorization failed: ${query["error"] ?: "no code returned"}")
            val token = postForm("https://accounts.spotify.com/api/token", mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ))
            saveToken(token)
        } finally {
            server.close()
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            synchronized(stateLock) { Files.deleteIfExists(tokenFile) }
        }
    }

    override suspend fun playlists(): Result<List<SpotifyPlaylist>> = apiJson("https://api.spotify.com/v1/me/playlists?limit=50").mapCatching { root ->
        root.getAsJsonArray("items").map { item ->
            val image = item.asJsonObject.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.get("url")?.asString
            SpotifyPlaylist(item.asJsonObject["id"].asString, item.asJsonObject["name"].asString, item.asJsonObject["tracks"].asJsonObject["total"].asInt, image)
        }
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<SpotifyTrack>> = apiJson("https://api.spotify.com/v1/playlists/${encode(playlistId)}/tracks?limit=100").mapCatching { root ->
        root.getAsJsonArray("items").mapNotNull { item ->
            val track = item.asJsonObject["track"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            SpotifyTrack(track["id"]?.asString.orEmpty(), track["name"]?.asString.orEmpty(), track["artists"]?.asJsonArray?.map { it.asJsonObject["name"].asString } ?: emptyList(), track["album"]?.asJsonObject?.get("name")?.asString, track["uri"]?.asString.orEmpty())
        }
    }

    override suspend fun devices(): Result<List<SpotifyDevice>> = apiJson("https://api.spotify.com/v1/me/player/devices").mapCatching { root ->
        root.getAsJsonArray("devices").map { item ->
            val d = item.asJsonObject
            SpotifyDevice(d["id"]?.asString.orEmpty(), d["name"]?.asString.orEmpty(), d["type"]?.asString.orEmpty(), d["is_active"]?.asBoolean == true, d["is_restricted"]?.asBoolean == true)
        }
    }

    override suspend fun play(trackUri: String, deviceId: String?): Result<Unit> = playerCommand("PUT", "https://api.spotify.com/v1/me/player/play", deviceId, "{\"uris\":[\"${trackUri.replace("\"", "") }\"]}")
    override suspend fun pause(deviceId: String?): Result<Unit> = playerCommand("PUT", "https://api.spotify.com/v1/me/player/pause", deviceId, "")

    private suspend fun playerCommand(method: String, endpoint: String, deviceId: String?, body: String): Result<Unit> = runCatching {
        val uri = if (deviceId.isNullOrBlank()) endpoint else "$endpoint?device_id=${encode(deviceId)}"
        val request = authorizedRequest(uri, method, body)
        val response = withContext(Dispatchers.IO) { client.send(request, HttpResponse.BodyHandlers.ofString()) }
        check(response.statusCode() in 200..299) { "Spotify playback request failed with HTTP ${response.statusCode()} (Premium account and an available device may be required)." }
    }

    private suspend fun apiJson(url: String): Result<JsonObject> = runCatching {
        val response = withContext(Dispatchers.IO) { client.send(authorizedRequest(url, "GET", null), HttpResponse.BodyHandlers.ofString()) }
        check(response.statusCode() in 200..299) { "Spotify API request failed with HTTP ${response.statusCode()}" }
        JsonParser.parseString(response.body()).asJsonObject
    }

    private suspend fun authorizedRequest(url: String, method: String, body: String?): HttpRequest {
        val token = accessToken()
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer $token")
        return when (method) {
            "PUT" -> builder.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body.orEmpty())).build()
            else -> builder.GET().build()
        }
    }

    private suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        val props = loadToken()
        val expiresAt = props.getProperty("expiresAt", "0").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() < expiresAt - 60_000L) return@withContext props.getProperty("accessToken") ?: error("Spotify is not connected")
        val refresh = props.getProperty("refreshToken") ?: error("Spotify is not connected")
        val refreshed = postForm("https://accounts.spotify.com/api/token", mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId))
        saveToken(refreshed, refresh)
        refreshed["access_token"]?.asString ?: error("Spotify did not return an access token")
    }

    private fun postForm(url: String, values: Map<String, String>): JsonObject {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(values.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" })).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Spotify token request failed with HTTP ${response.statusCode()}" }
        return JsonParser.parseString(response.body()).asJsonObject
    }

    private fun saveToken(token: JsonObject, previousRefresh: String? = null) {
        val props = loadToken()
        props.setProperty("accessToken", token["access_token"]?.asString.orEmpty())
        props.setProperty("refreshToken", token["refresh_token"]?.asString ?: previousRefresh.orEmpty())
        props.setProperty("expiresAt", (System.currentTimeMillis() + (token["expires_in"]?.asLong ?: 3600L) * 1000L).toString())
        synchronized(stateLock) {
            Files.createDirectories(root)
            Files.newOutputStream(tokenFile).use { props.store(it, "ZERAVRIX Spotify token metadata; do not share") }
        }
    }

    private fun loadToken(): Properties = Properties().also { props -> if (Files.isRegularFile(tokenFile)) Files.newInputStream(tokenFile).use(props::load) }
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
    private fun randomUrlSafe(length: Int): String = ByteArray(length).also { SecureRandom().nextBytes(it) }.let(::base64Url).take(length)
    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
