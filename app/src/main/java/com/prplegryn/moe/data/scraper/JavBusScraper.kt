package com.prplegryn.moe.data.scraper

import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.ScraperResult
import java.util.Locale
import okhttp3.Headers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class JavBusScraper(
    private val baseUrl: String = "https://www.javbus.com",
) : MetadataScraper {
    override val name = "javbus"

    private val headers: Headers = standardHtmlHeaders(
        mapOf(
            "Cookie" to "age=verified; dv=1; existmag=mag",
            "Referer" to "$baseUrl/",
        ),
    )

    override suspend fun search(query: String): ScraperResult? {
        val detailUrl = findDetailUrl(query.trim()) ?: return null
        val doc = Jsoup.parse(fetchHtml(detailUrl, headers), detailUrl)
        return parseDetail(doc, detailUrl, query)
    }

    private suspend fun findDetailUrl(query: String): String? {
        if (query.isBlank()) return null
        if (query.startsWith("http://") || query.startsWith("https://")) return applyLanguage(query)
        val hosts = if (baseUrl.contains("javbus.com")) listOf(baseUrl, "https://www.javbus.org") else listOf(baseUrl)
        val paths = listOf(
            "/search/${urlEncode(query)}&type=0&parent=uc",
            "/uncensored/search/${urlEncode(query)}&type=0&parent=uc",
        )
        for (host in hosts) {
            for (path in paths) {
                val url = host.trimEnd('/') + path
                val doc = runCatching { Jsoup.parse(fetchHtml(url, headers), host) }.getOrNull() ?: continue
                findDetailInSearch(doc, host, query)?.let { return applyLanguage(it) }
            }
        }
        return null
    }

    private fun findDetailInSearch(doc: Document, host: String, query: String): String? {
        var fallback: String? = null
        doc.select("a.movie-box[href]").forEach { link ->
            val href = link.absUrl("href").ifBlank { host.trimEnd('/') + "/" + link.attr("href").trimStart('/') }
            if (fallback == null) fallback = href
            val dateText = clean(link.selectFirst("date")?.text())
            val title = clean(link.attr("title"))
            if (idsMatch(dateText, query) || idsMatch(title, query) || idsMatch(href, query)) {
                return href
            }
        }
        return fallback
    }

    private fun parseDetail(doc: Document, sourceUrl: String, fallbackId: String): ScraperResult {
        var id = extractInfoValue(doc, "品番", "識別碼", "识别码", "id")
            .ifBlank { fallbackId.uppercase(Locale.ROOT) }
        var title = clean(doc.selectFirst("h3")?.text())
        if (title.isBlank()) title = clean(doc.selectFirst("a.bigImage img")?.attr("title"))

        if (title.isNotBlank()) {
            val normalized = normalizedId(id)
            if (normalized.isNotBlank() && normalizedId(title).startsWith(normalized)) {
                title = clean(title.replaceFirst(Regex(Regex.escape(id), RegexOption.IGNORE_CASE), ""))
            }
        }
        if (title.isBlank()) title = id

        val releaseDate = parseDate(extractInfoValue(doc, "発売日", "發行日期", "发行日期", "date"))
        val runtime = parseRuntimeMinutes(extractInfoValue(doc, "収録時間", "長度", "长度", "runtime", "length"))
        val cover = extractCoverUrl(doc, sourceUrl)
        val genres = extractGenres(doc)
        val actresses = extractActresses(doc)
        val rating = parseRating(clean(doc.selectFirst(".score, .rating")?.text()))

        return ScraperResult(
            source = name,
            sourceUrl = sourceUrl,
            language = "ja",
            id = clean(id),
            contentId = clean(id),
            title = title,
            originalTitle = title,
            description = extractDescription(doc),
            releaseDate = releaseDate,
            runtimeMinutes = runtime,
            director = extractInfoLinkValue(doc, "監督", "導演", "导演", "director"),
            maker = extractInfoLinkValue(doc, "メーカー", "製作商", "制作商", "maker", "studio"),
            label = extractInfoLinkValue(doc, "レーベル", "發行商", "发行商", "label"),
            series = extractInfoLinkValue(doc, "シリーズ", "系列", "series"),
            rating = rating,
            actresses = actresses,
            genres = genres,
            posterUrl = cover,
            coverUrl = cover,
            trailerUrl = firstUrl(doc.selectFirst("a[href*=sample], a[href*=trailer], video source")),
            screenshots = doc
                .select(
                    "#sample-waterfall a[href], #sample-waterfall img, " +
                        ".sample-box[href], .sample-box img, " +
                        "a[href$=.jpg], a[href$=.jpeg], a[href$=.png]",
                )
                .mapNotNull { firstUrl(it, imageAttrs) }
                .filter { it != cover }
                .distinct(),
        )
    }

    private fun applyLanguage(url: String): String = url

    private fun extractInfoValue(doc: Document, vararg labels: String): String {
        val p = infoParagraph(doc, *labels) ?: return ""
        val header = clean(p.selectFirst("span.header")?.text())
        return clean(p.text().removePrefix(header).trimStart(':', '：', ' '))
    }

    private fun extractInfoLinkValue(doc: Document, vararg labels: String): String? {
        val p = infoParagraph(doc, *labels) ?: return null
        val link = clean(p.selectFirst("a")?.text())
        if (link.isNotBlank()) return link
        return extractInfoValue(doc, *labels).ifBlank { null }
    }

    private fun infoParagraph(doc: Document, vararg labels: String): Element? {
        return doc.select("#info p, .info p").firstOrNull { p ->
            val header = clean(p.selectFirst("span.header")?.text()).trimEnd(':', '：')
            if (header.isNotBlank() && labelContains(header, *labels)) {
                true
            } else {
                val prefix = clean(p.text()).substringBefore(':').substringBefore('：')
                labelContains(prefix, *labels)
            }
        }
    }

    private fun extractActresses(doc: Document): List<ActressInfo> {
        val names = linkedMapOf<String, String?>()
        fun add(name: String, thumb: String?) {
            val cleanName = clean(name)
            if (cleanName.isBlank()) return
            if (cleanName.contains("画像を拡大") || cleanName.contains("点击放大") || cleanName.contains("click", true)) return
            names.putIfAbsent(cleanName, thumb)
        }
        doc.select("#star-div a[href*='/star/'], #avatar-waterfall a[href*='/star/'], .star-name a[href*='/star/']").forEach { link ->
            val img = link.selectFirst("img")
            add(
                name = clean(img?.attr("title")).ifBlank { clean(link.attr("title")) }.ifBlank { clean(link.text()) },
                thumb = firstUrl(img, imageAttrs),
            )
        }
        doc.select("#info a[href*='/star/'], .info a[href*='/star/']").forEach { link ->
            add(clean(link.text()), null)
        }
        return names.map { (name, thumb) -> ActressInfo(name, thumb) }
    }

    private fun extractGenres(doc: Document): List<String> = buildList {
        doc.select("#genre-toggle a, #info a[href*='/genre/'], .info a[href*='/genre/']").forEach { link ->
            val genre = clean(link.text())
            if (genre.isNotBlank() && genre !in this) add(genre)
        }
    }

    private fun extractCoverUrl(doc: Document, sourceUrl: String): String? {
        val selectors = listOf(
            "a.bigImage[href]",
            "a.bigImage img[src]",
            "a.bigImage img[data-src]",
            "#cover img[src]",
            "#cover img[data-src]",
        )
        for (selector in selectors) {
            val node = doc.selectFirst(selector) ?: continue
            val attr = if (selector.contains("[href]")) "href" else "src"
            val url = firstUrl(node, listOf(attr, "data-src", "data-original", "content"))
            if (!url.isNullOrBlank()) return url.ifBlank { sourceUrl }
        }
        return null
    }

    private fun extractDescription(doc: Document): String? {
        val selectors = listOf(".movie-introduction", "#movie-introduction", ".introduction", ".story")
        return selectors.firstNotNullOfOrNull { selector ->
            clean(doc.selectFirst(selector)?.text()).ifBlank { null }
        }
    }

    private companion object {
        val imageAttrs = listOf("href", "src", "data-src", "data-original", "content")
    }
}
