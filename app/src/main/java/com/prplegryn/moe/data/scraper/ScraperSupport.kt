package com.prplegryn.moe.data.scraper

import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.MediaResource
import com.prplegryn.moe.data.model.MovieMetadata
import com.prplegryn.moe.data.model.Rating
import com.prplegryn.moe.data.model.ScraperResult
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element

interface MetadataScraper {
    val name: String
    suspend fun search(query: String): ScraperResult?
}

class MetadataAggregator(
    private val scrapers: List<MetadataScraper>,
    private val priority: List<String> = listOf("javdb", "javbus"),
) {
    suspend fun scrape(resource: MediaResource): MovieMetadata? {
        val query = MovieIdParser.extract(resource.name)
        if (query.isBlank()) return null
        val results = scrapers.mapNotNull { scraper ->
            runCatching { scraper.search(query) }.getOrNull()
        }
        if (results.isEmpty()) return null
        return aggregate(resource.id, results)
    }

    private fun aggregate(resourceId: Long, results: List<ScraperResult>): MovieMetadata {
        val bySource = results.associateBy { it.source }
        fun sourceValue(block: (ScraperResult) -> String?): String? = priority
            .asSequence()
            .mapNotNull { bySource[it] }
            .mapNotNull { block(it)?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()

        fun sourceInt(block: (ScraperResult) -> Int): Int = priority
            .asSequence()
            .mapNotNull { bySource[it] }
            .map { block(it) }
            .firstOrNull { it > 0 } ?: 0

        fun sourceRating(): Rating? = priority
            .asSequence()
            .mapNotNull { bySource[it]?.rating }
            .firstOrNull()

        val source = priority.firstNotNullOfOrNull { bySource[it] } ?: results.first()
        val rating = sourceRating()
        val actresses = mergeDistinct(results.flatMap { it.actresses }, ActressInfo::name)
        val genres = mergeDistinct(results.flatMap { it.genres }) { it.lowercase(Locale.ROOT) }
        val screenshots = mergeDistinct(results.flatMap { it.screenshots }) { it }
        val contentId = sourceValue { it.contentId } ?: sourceValue { it.id } ?: "UNKNOWN"
        val title = sourceValue { it.title } ?: contentId
        val originalTitle = sourceValue { it.originalTitle } ?: title

        return MovieMetadata(
            resourceId = resourceId,
            contentId = contentId,
            title = title,
            originalTitle = originalTitle,
            description = sourceValue { it.description },
            releaseDate = sourceValue { it.releaseDate },
            runtimeMinutes = sourceInt { it.runtimeMinutes },
            director = sourceValue { it.director },
            maker = sourceValue { it.maker },
            label = sourceValue { it.label },
            series = sourceValue { it.series },
            ratingScore = rating?.score,
            ratingVotes = rating?.votes,
            posterUrl = normalizeDmmPoster(sourceValue { it.posterUrl }),
            coverUrl = normalizeDmmPoster(sourceValue { it.coverUrl }),
            trailerUrl = sourceValue { it.trailerUrl },
            sourceName = source.source,
            sourceUrl = source.sourceUrl,
            actresses = actresses,
            genres = genres,
            screenshots = screenshots,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun normalizeDmmPoster(url: String?): String? {
        if (url.isNullOrBlank()) return url
        return runCatching {
            val uri = URI(url)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return url
            if ((host == "pics.dmm.co.jp" || host.endsWith(".dmm.co.jp")) && uri.path.endsWith("ps.jpg", true)) {
                url.dropLast(6) + "pl.jpg"
            } else {
                url
            }
        }.getOrDefault(url)
    }

    private fun <T, K> mergeDistinct(items: List<T>, key: (T) -> K): List<T> {
        val seen = linkedSetOf<K>()
        return items.filter { seen.add(key(it)) }
    }
}

object MovieIdParser {
    private val fc2 = Regex("""(?i)\bFC2[-_\s]*(?:PPV[-_\s]*)?(\d{3,8})\b""")
    private val standard = Regex("""(?i)\b([A-Z]{2,8})[-_\s]?(\d{2,6})\b""")
    private val ignoredPrefixes = setOf("HEVC", "H264", "H265", "X264", "X265", "FHD", "UHD")

    fun extract(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
            .replace(Regex("""[\[\](){}]"""), " ")
            .replace(Regex("""(?i)(1080p|2160p|720p|4k|8k|x264|x265|h264|h265|hevc|aac)"""), " ")
        fc2.find(base)?.let { return "FC2-${it.groupValues[1]}" }
        standard.findAll(base).forEach { match ->
            val prefix = match.groupValues[1].uppercase(Locale.ROOT)
            if (prefix !in ignoredPrefixes) {
                return "$prefix-${match.groupValues[2]}"
            }
        }
        return base.trim()
    }
}

internal val htmlClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

internal suspend fun fetchHtml(url: String, headers: Headers = standardHtmlHeaders()): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .get()
            .build()
        htmlClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(160)}")
            }
            body
        }
    }

internal fun standardHtmlHeaders(extra: Map<String, String> = emptyMap()): Headers {
    val builder = Headers.Builder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "ja,en-US;q=0.8,en;q=0.6,zh-CN;q=0.5")
        .add("Cache-Control", "no-cache")
        .add("User-Agent", "Moe/0.1 Android; Javinizer-compatible metadata scraper")
    extra.forEach { (key, value) -> builder.add(key, value) }
    return builder.build()
}

internal fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

internal fun clean(value: String?): String = value
    ?.replace('\u00a0', ' ')
    ?.replace(Regex("""\s+"""), " ")
    ?.trim()
    .orEmpty()

internal fun normalizedId(value: String): String =
    value.uppercase(Locale.ROOT).replace(Regex("""[^A-Z0-9]"""), "")

internal fun idsMatch(candidate: String, target: String): Boolean {
    val c = normalizedId(candidate)
    val t = normalizedId(target)
    return c == t || (t.length >= 4 && c.contains(t))
}

internal fun labelContains(label: String, vararg needles: String): Boolean {
    val normalized = label.lowercase(Locale.ROOT)
    return needles.any { normalized.contains(it.lowercase(Locale.ROOT)) }
}

internal fun firstUrl(element: Element?, attrs: List<String> = listOf("src", "href", "data-src", "data-original")): String? {
    if (element == null) return null
    for (attr in attrs) {
        val raw = clean(element.attr(attr))
        if (raw.isNotEmpty()) {
            val absolute = element.absUrl(attr)
            return absolute.ifBlank { raw }
        }
    }
    return null
}

internal fun parseRuntimeMinutes(value: String): Int {
    val number = Regex("""(\d+)""").find(value)?.groupValues?.getOrNull(1) ?: return 0
    return number.toIntOrNull() ?: 0
}

internal fun parseDate(value: String): String? {
    val match = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""").find(value) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return "%04d-%02d-%02d".format(year, month, day)
}

internal fun parseRating(value: String): Rating? {
    val score = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(value)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        ?: return null
    val votes = Regex("""\(([0-9,]+)""").find(value)?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()
        ?: 0
    return Rating(score = max(0.0, score), votes = votes)
}
