package com.prplegryn.moe.data.scraper

import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.Rating
import com.prplegryn.moe.data.model.ScraperResult
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.Request

class LibreDmmScraper(
    private val baseUrl: String = "https://www.libredmm.com",
) : MetadataScraper {
    override val name = "libredmm"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): ScraperResult? {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return null
        var targetUrl = buildSearchUrl(cleanQuery)
        repeat(POLL_ATTEMPTS) { attempt ->
            val response = runCatching { fetchMovieJson(targetUrl) }.getOrNull() ?: return@repeat
            targetUrl = normalizeMovieUrl(response.finalUrl) ?: response.finalUrl
            when (response.statusCode) {
                200 -> {
                    if (response.root.string("err").isNotBlank()) return null
                    val result = response.root.toResult(sourceUrl = targetUrl, fallbackId = cleanQuery)
                    if (idsMatch(result.id, cleanQuery) || idsMatch(result.contentId, cleanQuery)) return result
                }
                202 -> {
                    if (attempt < POLL_ATTEMPTS - 1) delay(POLL_INTERVAL_MS)
                }
                404 -> return null
                else -> Unit
            }
        }
        return null
    }

    private suspend fun fetchMovieJson(targetUrl: String): LibreDmmResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(targetUrl)
            .headers(libreHeaders())
            .get()
            .build()
        htmlClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code !in setOf(200, 202, 404)) {
                throw IOException("HTTP ${response.code}: ${text.take(160)}")
            }
            val cleanBody = stripResponseNoise(text)
            val root = if (cleanBody.isBlank()) {
                JsonObject(emptyMap())
            } else {
                json.parseToJsonElement(cleanBody).jsonObject
            }
            LibreDmmResponse(
                root = root,
                finalUrl = response.request.url.toString(),
                statusCode = response.code,
            )
        }
    }

    private fun JsonObject.toResult(sourceUrl: String, fallbackId: String): ScraperResult {
        val source = string("url").ifBlank { sourceUrl.removeSuffix(".json") }
        val id = string("normalized_id").ifBlank { extractIdFromUrl(sourceUrl).ifBlank { fallbackId.uppercase(Locale.ROOT) } }
        val contentId = string("subtitle").ifBlank { normalizeLibreId(id) }
        val title = string("title").ifBlank { id }
        val cover = normalizeDmmPoster(
            resolveUrl(sourceUrl, string("cover_image_url").ifBlank { string("thumbnail_image_url") }),
        )

        return ScraperResult(
            source = name,
            sourceUrl = source,
            language = "ja",
            id = id,
            contentId = contentId,
            title = title,
            originalTitle = title,
            description = string("description").ifBlank { null },
            releaseDate = parseDate(string("date")),
            runtimeMinutes = int("volume").takeIf { it > 0 }?.let { it / 60 } ?: 0,
            director = stringArray("directors").firstOrNull(),
            maker = stringArray("makers").firstOrNull(),
            label = stringArray("labels").firstOrNull(),
            series = null,
            rating = double("review").takeIf { it > 0.0 }?.let { Rating(score = it) },
            actresses = actresses(sourceUrl),
            genres = stringArray("genres"),
            posterUrl = cover,
            coverUrl = cover,
            trailerUrl = null,
            screenshots = stringArray("sample_image_urls")
                .map { normalizeDmmScreenshot(resolveUrl(sourceUrl, it)) }
                .filter { it.isNotBlank() }
                .distinct(),
        )
    }

    private fun JsonObject.actresses(sourceUrl: String): List<ActressInfo> {
        return array("actresses")
            ?.mapNotNull { element ->
                val actress = element as? JsonObject ?: return@mapNotNull null
                val name = actress.string("name")
                if (name.isBlank()) return@mapNotNull null
                ActressInfo(
                    name = name,
                    thumbUrl = resolveUrl(sourceUrl, actress.string("image_url")).toHttps().takeIf { it.isNotBlank() },
                )
            }
            ?.distinctBy { it.name }
            .orEmpty()
    }

    private fun buildSearchUrl(query: String): String =
        "${baseUrl.trimEnd('/')}/search?q=${urlEncode(query)}&format=json"

    private fun normalizeMovieUrl(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host != "libredmm.com" && !host.endsWith(".libredmm.com")) return null
        val parts = uri.path.trim('/').split('/')
        if (parts.size < 2 || !parts[0].equals("movies", ignoreCase = true)) return null
        val id = parts[1].removeSuffix(".json").takeIf { it.isNotBlank() } ?: return null
        return "${baseUrl.trimEnd('/')}/movies/$id.json"
    }

    private fun extractIdFromUrl(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return ""
        val parts = uri.path.trim('/').split('/')
        return if (parts.size >= 2 && parts[0].equals("movies", ignoreCase = true)) {
            parts[1].removeSuffix(".json")
        } else {
            ""
        }
    }

    private fun libreHeaders(): Headers = Headers.Builder()
        .add("Accept", "application/json,text/plain,*/*")
        .add("Accept-Language", "ja,en-US;q=0.8,en;q=0.6")
        .add("Cache-Control", "no-cache")
        .add("Referer", "$baseUrl/")
        .add("User-Agent", BROWSER_USER_AGENT)
        .build()

    private data class LibreDmmResponse(
        val root: JsonObject,
        val finalUrl: String,
        val statusCode: Int,
    )

    private companion object {
        const val POLL_ATTEMPTS = 4
        const val POLL_INTERVAL_MS = 1_500L
    }
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.double(key: String): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

private fun JsonObject.array(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonObject.stringArray(key: String): List<String> =
    array(key)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        ?.distinct()
        .orEmpty()

private fun normalizeLibreId(value: String): String =
    value.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")

private fun normalizeDmmPoster(url: String): String {
    return url.replace(Regex("""(?i)ps\.jpg($|\?)""")) { match ->
        "pl.jpg" + match.groupValues[1]
    }
}

private fun normalizeDmmScreenshot(url: String): String {
    return url.replace(Regex("""(?i)(/[^/]+?)(-\d+\.jpg)$""")) { match ->
        val base = match.groupValues[1]
        if (base.endsWith("jp", ignoreCase = true)) match.value else "${base}jp${match.groupValues[2]}"
    }
}

private fun resolveUrl(base: String, raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.toHttps()
        value.startsWith("https://") -> value
        else -> runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }
}

private fun String.toHttps(): String =
    if (startsWith("http://")) "https://" + removePrefix("http://") else this

private fun stripResponseNoise(value: String): String {
    val clean = value
        .replace(Regex("""\u001B\[[0-9;]*[A-Za-z]"""), "")
        .replace("\u001B", "")
        .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "")
        .trim()
    val start = clean.indexOfFirst { it == '{' || it == '[' }
    return if (start > 0) clean.substring(start) else clean
}
