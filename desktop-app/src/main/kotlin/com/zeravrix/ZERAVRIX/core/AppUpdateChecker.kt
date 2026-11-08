package com.zeravrix.zeravrix.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val version: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val body: String = ""
)

object AppUpdateChecker {
    const val CURRENT_VERSION = "1.7.0"
    private const val GITHUB_API_URL = "https://api.github.com/repos/zeravrix/ZERAVRIX-PC/releases/latest"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun checkForUpdate(): AppUpdateInfo? {
        return runCatching {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("User-Agent", "ZERAVRIX-Desktop/$CURRENT_VERSION")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "").trim()
                val htmlUrl = json.optString("html_url", "")
                val releaseNotes = json.optString("body", "")

                val cleanRemote = tagName.removePrefix("v").removePrefix("V").trim()
                if (!isNewer(cleanRemote, CURRENT_VERSION)) return null

                // Extract direct download link for installer (.exe or archive) if available
                var directDownloadUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (assetName.endsWith(".exe", ignoreCase = true) || assetName.endsWith("-Setup.exe", ignoreCase = true)) {
                            directDownloadUrl = downloadUrl
                            break
                        }
                    }
                }

                AppUpdateInfo(
                    version = cleanRemote,
                    releaseUrl = htmlUrl,
                    downloadUrl = directDownloadUrl,
                    body = releaseNotes
                )
            }
        }.getOrNull()
    }

    fun isNewer(remote: String, current: String): Boolean {
        val rParts = remote.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val cParts = current.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val len = maxOf(rParts.size, cParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
