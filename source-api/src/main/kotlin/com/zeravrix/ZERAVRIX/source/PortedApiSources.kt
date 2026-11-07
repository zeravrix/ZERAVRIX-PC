package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.jsoup.Jsoup

class AzoraMihonSource : DesktopJsonSource(), CatalogueSource {
    override val id: Long = 0x415A4F5241L
    override val name: String = "Azora"
    override val language: String = "ar"
    override val baseUrl: String = "https://azorafly.com"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)
    private val apiUrl = "https://api.azorafly.com"
    private val perPage = 18

    private suspend fun api(url: String): Any? = DesktopJson.parse(getChecked(url, mapOf("Accept" to "application/json", "Referer" to "$baseUrl/")))
    private fun list(root: Any?): List<Any?> = root.jsonMap().listValue("posts", "results", "items")
    private fun hasNext(root: Any?, page: Int, count: Int): Boolean {
        val map = root.jsonMap()
        val total = map.number("totalCount", "total").orElseNull()?.toInt() ?: return count >= perPage
        return total > page * perPage
    }

    private fun manga(raw: Map<String, Any?>): Manga? {
        if ((raw["isNovel"] as? Boolean) == true || raw.string("seriesType").equals("novel", true)) return null
        val id = raw.string("id") ?: return null
        val slug = raw.string("slug") ?: id
        val title = raw.string("postTitle", "title", "name") ?: "Untitled"
        val content = raw.string("postContent").orEmpty()
        val alternative = raw.string("alternativeTitles")
        val description = Jsoup.parse(content.replace("\n", "<br>")).text() + if (!alternative.isNullOrBlank()) "\n\nAlternative Names: $alternative" else ""
        val genres = buildList {
            when (raw.string("seriesType")) {
                "MANGA" -> add("Manga")
                "MANHUA" -> add("Manhua")
                "MANHWA" -> add("Manhwa")
            }
            addAll(raw.listValue("genres").mapNotNull { it.jsonMap().string("name") })
        }.distinct()
        val status = when (raw.string("seriesStatus")) {
            "ONGOING", "COMING_SOON", "MASS_RELEASED" -> "ONGOING"
            "COMPLETED" -> "COMPLETED"
            "CANCELLED", "DROPPED" -> "CANCELLED"
            else -> null
        }
        return Manga("$baseUrl/series/$slug#$id", title, raw.string("featuredImage", "thumbnailUrl"), description.trim(), this.id, raw.string("author"), raw.string("artist"), genres, status)
    }

    override suspend fun popular(page: Int): MangaPageResult = searchPage(page, "", "totalViews", "desc")
    override suspend fun latest(page: Int): MangaPageResult = searchPage(page, "", "lastChapterAddedAt", "desc")
    override suspend fun search(page: Int, query: String): MangaPageResult = searchPage(page, query, "postTitle", "asc")

    private suspend fun searchPage(page: Int, query: String, orderBy: String, direction: String): MangaPageResult {
        val url = "$apiUrl/api/query?page=$page&perPage=$perPage&searchTerm=${URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)}&orderBy=$orderBy&orderDirection=$direction"
        val root = api(url)
        val items = list(root).mapNotNull { manga(it.jsonMap()) }.distinctBy { it.id }
        return MangaPageResult(items, hasNext(root, page, items.size))
    }

    override suspend fun details(manga: Manga): Manga {
        val slug = manga.id.substringAfter("/series/").substringBefore('#').substringBefore('/')
        val root = api("$apiUrl/api/post?postSlug=${URLEncoder.encode(slug, StandardCharsets.UTF_8)}").jsonMap()
        return manga(root.mapValue("post") ?: root) ?: manga
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val id = manga.id.substringAfter('#').substringBefore('/')
        val root = api("$apiUrl/api/post?postSlug=${URLEncoder.encode(manga.id.substringAfter("/series/").substringBefore('#'), StandardCharsets.UTF_8)}").jsonMap()
        val inline = root.mapValue("post")?.listValue("chapters").orEmpty()
        val total = root.number("totalChapterCount")?.toInt() ?: inline.size
        val chapterValues = if (inline.size < total) {
            api("$apiUrl/api/chapters?postId=$id").jsonMap().mapValue("post")?.listValue("chapters").orEmpty()
        } else inline
        return chapterValues.mapNotNull { rawValue ->
            val raw = rawValue.jsonMap()
            val chapterId = raw.string("id") ?: return@mapNotNull null
            val slug = raw.string("slug") ?: chapterId
            val number = raw.string("number", "chapter") ?: "0"
            val title = raw.string("title")
            val locked = raw["isLocked"] as? Boolean == true || raw["isTimeLocked"] as? Boolean == true || ((raw.number("price") ?: 0f) != 0f && raw["chapterPurchased"] as? Boolean != true)
            Chapter("$baseUrl/series/${manga.id.substringAfter("/series/").substringBefore('#')}/$slug#$chapterId", manga.id, "${if (locked) "[Locked] " else ""}Chapter $number${if (title.isNullOrBlank()) "" else " - $title"}", number.toFloatOrNull(), this.id, scanlator = raw.mapValue("createdBy")?.string("name"), uploadDate = raw.string("createdAt", "created_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() })
        }.distinctBy { it.id }
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val chapterId = chapter.id.substringAfter('#').substringBefore('/')
        val root = api("$apiUrl/api/chapter?chapterId=$chapterId").jsonMap().mapValue("chapter") ?: error("Azora returned no chapter data")
        if (root["isShortLinkLocked"] == true) error("Azora chapter is locked by a short link")
        if (root["isLockedByCoins"] == true) error("Azora chapter requires coins")
        if (root["isPermanentlyLocked"] == true) error("Azora chapter is permanently locked")
        return root.listValue("images", "pages").mapNotNull { rawValue ->
            val raw = rawValue.jsonMap(); val url = raw.string("url", "image") ?: return@mapNotNull null
            MangaPage(0, url.replace(" ", "%20"), chapter.id, mapOf("Referer" to chapter.id))
        }.sortedWith(compareBy<MangaPage> { it.index }).mapIndexed { index, page -> page.copy(index = index) }
    }
}

private fun Float?.orElseNull(): Float? = this


class DilarMihonSource : DesktopJsonSource(), CatalogueSource {
    override val id: Long = 0x44494C4152L
    override val name: String = "Dilar"
    override val language: String = "ar"
    override val baseUrl: String = "https://dilar.tube"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)
    private val keyPair by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
    }
    private val clientPubRaw by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pointToRaw(keyPair.public as java.security.interfaces.ECPublicKey)
    }
    private val clientPubB64 by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(clientPubRaw)
    }
    private val cryptoHeaders get() = mapOf("Accept" to "application/json", "X-DH-Pub" to clientPubB64, "X-Crypto-Caps" to "1,2,3,4,5,6,7,8,9,10")

    private suspend fun api(url: String, headers: Map<String, String> = cryptoHeaders): Any? = DesktopJson.parse(getChecked(url, headers))

    private fun series(raw: Map<String, Any?>): Manga? {
        if (raw.mapValue("seriesType")?.string("title") == "رواية") return null
        val id = raw.string("id") ?: return null
        val title = raw.string("title", "name") ?: "Untitled"
        val cover = raw.string("cover")?.let { "$baseUrl/uploads/manga/cover/$id/large_${it.substringBeforeLast('.')}.webp" }
        val authors = raw.listValue("staff").mapNotNull { member ->
            val map = member.jsonMap()
            map.string("name")?.takeIf { map.mapValue("Staff")?.string("role") in setOf("Author", "Artist") }
        }
        val genres = raw.listValue("categories").mapNotNull { it.jsonMap().string("name") }
        return Manga("$baseUrl/series/$id/${raw.string("title") ?: id}", title, cover, raw.string("summary").orEmpty(), this.id, authors.joinToString().ifBlank { null }, null, genres, raw.string("translation_status"))
    }

    override suspend fun popular(page: Int): MangaPageResult {
        val root = api("$baseUrl/api/rankings").jsonMap()
        val items = root.listValue("topSeries").mapNotNull { series(it.jsonMap()) }.distinctBy { it.id }
        return MangaPageResult(items, false)
    }

    override suspend fun latest(page: Int): MangaPageResult {
        val root = api("$baseUrl/api/series?page=$page").jsonMap()
        val items = root.listValue("series").mapNotNull { series(it.jsonMap()) }.distinctBy { it.id }
        val current = root.number("currentPage")?.toInt() ?: page
        val total = root.number("totalPages")?.toInt() ?: current
        return MangaPageResult(items, current < total)
    }

    override suspend fun search(page: Int, query: String): MangaPageResult {
        val body = "{\"query\":\"${query.replace("\\", "\\\\").replace("\"", "\\\"")}\",\"page\":$page}"
        val root = DesktopJson.parse(postChecked("$baseUrl/api/search/filter", body, cryptoHeaders)).jsonMap()
        val items = root.listValue("rows").mapNotNull { series(it.jsonMap()) }.distinctBy { it.id }
        val total = root.number("total")?.toInt() ?: items.size
        val current = root.number("page")?.toInt() ?: page
        val perPage = root.number("perPage")?.toInt() ?: 20
        return MangaPageResult(items, total > current * perPage)
    }

    override suspend fun details(manga: Manga): Manga {
        val seriesId = manga.id.substringAfter("/series/").substringBefore('/')
        return series(api("$baseUrl/api/series/$seriesId").jsonMap()) ?: manga
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val seriesId = manga.id.substringAfter("/series/").substringBefore('/')
        val root = api("$baseUrl/api/series/$seriesId/chapters").jsonMap()
        return root.listValue("chapters").flatMap { chapterValue ->
            val chapter = chapterValue.jsonMap()
            val chapterNumber = chapter.string("chapter") ?: return@flatMap emptyList()
            val title = chapter.string("title")
            chapter.listValue("releases").mapNotNull { releaseValue ->
                val release = releaseValue.jsonMap(); val releaseId = release.string("id") ?: return@mapNotNull null
                Chapter("$baseUrl/series/$seriesId/$chapterNumber#$releaseId", manga.id, "$chapterNumber${if (title.isNullOrBlank()) "" else " - $title"}", chapterNumber.removeSuffix(".00").toFloatOrNull(), this.id, uploadDate = release.string("created_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() })
            }
        }.distinctBy { it.id }.sortedWith(compareByDescending<Chapter> { it.number ?: Float.NEGATIVE_INFINITY }.thenBy { it.title })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val releaseId = chapter.id.substringAfter('#').substringBefore('/')
        val chapterApi = "$baseUrl/api/chapters/$releaseId"
        val unlock = DesktopJson.parse(postChecked("$chapterApi/unlock/free", "{}", cryptoHeaders)).jsonMap().string("token")
            ?: error("Dilar did not return a free-chapter unlock token")
        val encrypted = api(chapterApi, cryptoHeaders + mapOf("X-Unlock-Free-Chapter" to unlock)).jsonMap()
        val decrypted = decrypt(encrypted)
        val payload = decrypted.jsonMap()
        val storage = payload.string("storage_key") ?: error("Dilar page response did not contain a storage key")
        return payload.listValue("pages").mapNotNull { value ->
            val page = value.jsonMap(); val path = page.string("url") ?: return@mapNotNull null
            MangaPage(0, "$baseUrl/uploads/releases/$storage/hq/$path", chapter.id, cryptoHeaders)
        }.sortedBy { it.index }.mapIndexed { index, page -> page.copy(index = index) }
    }

    private fun decrypt(data: Map<String, Any?>): Map<String, Any?> {
        val version = data.number("v")?.toInt() ?: error("Dilar encrypted response has no protocol version")
        val serverPubRaw = decodeUrl(data.string("epk") ?: error("Dilar response has no public key"))
        val iv = decodeUrl(data.string("iv") ?: error("Dilar response has no IV"))
        val serverKey = rawToPoint(serverPubRaw)
        val sharedSecret = javax.crypto.KeyAgreement.getInstance("ECDH").apply {
            init(keyPair.private)
            doPhase(serverKey, true)
        }.generateSecret()
        val e = data.number("e")?.toInt() ?: 0
        val eText = e.toString()
        val saltAndInfo = when (version) {
            1 -> clientPubRaw + serverPubRaw to "dilar.response.ecies.v1|$eText".toByteArray()
            2 -> serverPubRaw + clientPubRaw to "dilar.response.ecies.v2|$eText".toByteArray()
            3 -> sha256(serverPubRaw + clientPubRaw) to "dilar.response.ecies.v3|$eText".toByteArray()
            4 -> sha256(clientPubRaw + serverPubRaw + iv) to "dilar.response.ecies.v4|$eText|${data.string("iv")}".toByteArray()
            5 -> hmac(iv, serverPubRaw + clientPubRaw) to "dilar.response.ecies.v5|$eText".toByteArray()
            6 -> sha256(sha256(clientPubRaw) + sha256(serverPubRaw) + iv) to "dilar.response.ecies.v6|$eText|${data.string("iv")}".toByteArray()
            7 -> hkdf(iv, serverPubRaw, "dilar.response.ecies.v7.salt".toByteArray(), 32) to "dilar.response.ecies.v7|$eText".toByteArray()
            8 -> sha256(u16(clientPubRaw.size) + clientPubRaw + u16(serverPubRaw.size) + serverPubRaw + u16(iv.size) + iv) to "dilar.response.ecies.v8|$eText|${iv.hex()}".toByteArray()
            9 -> hmac(iv, u16(serverPubRaw.size) + serverPubRaw + u16(clientPubRaw.size) + clientPubRaw, "HmacSHA512").copyOfRange(0, 32) to "dilar.response.ecies.v9|$eText|${sha256(iv).hex().take(16)}".toByteArray()
            10 -> sha512(u16(clientPubRaw.size) + clientPubRaw + u16(serverPubRaw.size) + serverPubRaw + u16(iv.size) + iv) to "dilar.response.ecies.v10|$eText|${sha512(iv).hex().take(24)}".toByteArray()
            else -> error("Unsupported Dilar encryption protocol version: $version")
        }
        val key = if (version == 10) hkdf(sharedSecret, saltAndInfo.first, saltAndInfo.second, 32, "HmacSHA512") else hkdf(sharedSecret, saltAndInfo.first, saltAndInfo.second, 32)
        val ciphertext = decodeUrl(data.string("ct") ?: error("Dilar response has no ciphertext"))
        val tag = decodeUrl(data.string("tag") ?: error("Dilar response has no authentication tag"))
        val plain = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        }.doFinal(ciphertext + tag).toString(Charsets.UTF_8)
        return DesktopJson.parse(plain).jsonMap()
    }

    private fun decodeUrl(value: String): ByteArray {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        return java.util.Base64.getUrlDecoder().decode(padded)
    }

    private fun pointToRaw(publicKey: java.security.interfaces.ECPublicKey): ByteArray = byteArrayOf(4) + publicKey.w.affineX.toFixedBytes(32) + publicKey.w.affineY.toFixedBytes(32)
    private fun rawToPoint(raw: ByteArray): java.security.interfaces.ECPublicKey {
        require(raw.size == 65 && raw[0].toInt() == 4) { "Invalid Dilar P-256 public key" }
        val params = java.security.AlgorithmParameters.getInstance("EC").apply { init(java.security.spec.ECGenParameterSpec("secp256r1")) }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        return java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.ECPublicKeySpec(java.security.spec.ECPoint(java.math.BigInteger(1, raw.copyOfRange(1, 33)), java.math.BigInteger(1, raw.copyOfRange(33, 65))), params)) as java.security.interfaces.ECPublicKey
    }
    private fun java.math.BigInteger.toFixedBytes(size: Int): ByteArray = toByteArray().let { bytes -> when { bytes.size == size -> bytes; bytes.size > size -> bytes.copyOfRange(bytes.size - size, bytes.size); else -> ByteArray(size - bytes.size) + bytes } }
    private fun u16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun sha256(value: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(value)
    private fun sha512(value: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-512").digest(value)
    private fun hmac(key: ByteArray, value: ByteArray, algorithm: String = "HmacSHA256"): ByteArray = javax.crypto.Mac.getInstance(algorithm).apply { init(javax.crypto.spec.SecretKeySpec(key, algorithm)) }.doFinal(value)
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int, algorithm: String = "HmacSHA256"): ByteArray {
        val prk = hmac(salt, ikm, algorithm); var previous = ByteArray(0); val output = java.io.ByteArrayOutputStream(); var counter = 1
        while (output.size() < length) { previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()), algorithm); output.write(previous); counter++ }
        return output.toByteArray().copyOf(length)
    }
}
