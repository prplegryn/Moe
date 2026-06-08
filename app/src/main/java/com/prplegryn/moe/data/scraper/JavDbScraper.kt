package com.prplegryn.moe.data.scraper

import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.ScraperResult
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class JavDbScraper(
    private val baseUrl: String = "https://javdb.com",
) : MetadataScraper {
    override val name = "javdb"

    override suspend fun search(query: String): ScraperResult? {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return null
        directLookup(cleanQuery)?.let { return it }

        val searchUrl = "$baseUrl/search?q=${urlEncode(cleanQuery)}&f=all"
        val searchDoc = Jsoup.parse(fetchHtml(searchUrl), baseUrl)
        val detailUrl = findDetailUrl(searchDoc, cleanQuery) ?: return null
        return parseDetail(Jsoup.parse(fetchHtml(detailUrl), detailUrl), detailUrl, cleanQuery)
    }

    private suspend fun directLookup(query: String): ScraperResult? {
        if (!Regex("""^[A-Za-z0-9]{3,12}$""").matches(query)) return null
        val url = "$baseUrl/v/$query"
        return runCatching {
            parseDetail(Jsoup.parse(fetchHtml(url), url), url, query)
        }.getOrNull()?.takeIf { it.title.isNotBlank() && !idsMatch(it.title, query) }
    }

    private fun findDetailUrl(doc: Document, query: String): String? {
        var fallback: String? = null
        doc.select(".movie-list .item").forEach { item ->
            val link = item.selectFirst("a[href]") ?: return@forEach
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            if (fallback == null && href.contains("/v/")) fallback = href

            val candidates = listOf(
                item.selectFirst(".uid")?.text(),
                item.selectFirst(".video-title strong")?.text(),
                item.selectFirst(".video-title")?.text(),
            ).map(::clean)
            if (candidates.any { idsMatch(it, query) }) return href
        }
        return fallback.takeUnless { MovieIdParser.isLikelyContentId(query) }
    }

    private fun parseDetail(doc: Document, sourceUrl: String, fallbackId: String): ScraperResult {
        val titleNode = doc.selectFirst(".title.is-4")
        val idFromTitle = clean(titleNode?.selectFirst("strong")?.text())
        var id = idFromTitle.ifBlank { fallbackId.trim().uppercase(Locale.ROOT) }
        var title = clean(titleNode?.text())
        if (idFromTitle.isNotBlank()) {
            title = clean(title.removePrefix(idFromTitle))
        }
        if (title.isBlank()) {
            title = clean(doc.selectFirst("meta[property=og:title]")?.attr("content"))
        }

        var description: String? = clean(doc.selectFirst("span[itemprop=description]")?.text())
            .ifBlank { clean(doc.selectFirst(".movie-panel-info .movie-description")?.text()) }
            .ifBlank { null }
        var releaseDate: String? = null
        var runtime = 0
        var director: String? = null
        var maker: String? = null
        var label: String? = null
        var series: String? = null
        var rating = com.prplegryn.moe.data.model.Rating(score = 0.0)
        var parsedRating: com.prplegryn.moe.data.model.Rating? = null
        var actresses = emptyList<ActressInfo>()
        var genres = emptyList<String>()

        doc.select(".movie-panel-info .panel-block").forEach { block ->
            val labelText = clean(block.selectFirst("strong")?.text()).trimEnd(':', '：')
            val valueNode = block.selectFirst(".value") ?: block
            val value = clean(valueNode.text())
            when {
                labelContains(labelText, "番號", "番号", "識別碼", "识别码", "id") && value.isNotBlank() -> id = value
                labelContains(labelText, "日期", "release") -> releaseDate = parseDate(value)
                labelContains(labelText, "時長", "长度", "長度", "runtime", "duration") -> runtime = parseRuntimeMinutes(value)
                labelContains(labelText, "導演", "导演", "director") -> director = firstText(valueNode)
                labelContains(labelText, "片商", "maker", "studio") -> maker = firstText(valueNode)
                labelContains(labelText, "發行", "发行", "label", "publisher") -> label = firstText(valueNode)
                labelContains(labelText, "系列", "series") -> series = firstText(valueNode)
                labelContains(labelText, "評分", "评分", "rating", "score") -> parsedRating = parseRating(value)
                labelContains(labelText, "類別", "类别", "genre", "tag") -> genres = extractStringList(valueNode)
                labelContains(labelText, "演員", "演员", "actress", "cast", "star") -> {
                    val found = extractActresses(valueNode)
                    if (found.isNotEmpty()) actresses = found
                }
                labelContains(labelText, "簡介", "简介", "description") -> description = value.ifBlank { description }
            }
        }
        rating = parsedRating ?: rating
        if (actresses.isEmpty()) {
            actresses = extractActressesFromDocument(doc)
        }

        val cover = extractFirstImage(
            doc,
            "meta[property=og:image]",
            "meta[name=twitter:image]",
            ".column-video-cover img.video-cover",
            ".column-video-cover img",
            ".column-video-cover a[href]",
            "img.video-cover",
            ".video-meta-panel img.video-cover",
            "img[src*=cover]",
            "img[data-src*=cover]",
            "img[src*=jacket]",
            "img[data-src*=jacket]",
        )
        val screenshots = doc
            .select(
                ".tile-images a[href], .tile-images img, " +
                    ".preview-images a[href], .preview-images img, " +
                    "a[href$=.jpg], a[href$=.jpeg], a[href$=.png], " +
                    "img[data-src$=.jpg], img[data-original$=.jpg]",
            )
            .mapNotNull { firstUrl(it, imageAttrs) }
            .filter { it != cover }
            .distinct()

        return ScraperResult(
            source = name,
            sourceUrl = sourceUrl,
            language = "ja",
            id = clean(id).ifBlank { fallbackId },
            contentId = clean(id).ifBlank { fallbackId },
            title = title.ifBlank { clean(id).ifBlank { fallbackId } },
            originalTitle = title.ifBlank { clean(id).ifBlank { fallbackId } },
            description = description,
            releaseDate = releaseDate,
            runtimeMinutes = runtime,
            director = director,
            maker = maker,
            label = label,
            series = series,
            rating = rating.takeIf { it.score > 0.0 },
            actresses = actresses,
            genres = genres,
            posterUrl = cover,
            coverUrl = cover,
            trailerUrl = firstUrl(doc.selectFirst("video source, a[href*=sample], a[href*=trailer]")),
            screenshots = screenshots,
        )
    }

    private fun extractFirstImage(doc: Document, vararg selectors: String): String? {
        for (selector in selectors) {
            val url = firstUrl(doc.selectFirst(selector), imageAttrs)
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private fun firstText(element: Element): String? =
        clean(element.selectFirst("a")?.text()).ifBlank { clean(element.text()) }.ifBlank { null }

    private fun extractStringList(element: Element): List<String> = element.select("a, span.tag, .tag")
        .map { clean(it.text()) }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty {
            clean(element.text()).split(',', '、', '/', ' ')
                .map(::clean)
                .filter { it.length > 1 }
        }

    private fun extractActresses(element: Element): List<ActressInfo> = element.select("a")
        .mapNotNull(::actressFromLink)
        .distinctBy { it.name }

    private fun extractActressesFromDocument(doc: Document): List<ActressInfo> = doc
        .select(".actors a, .actor-section a, .star-name a, a[href*='/actors/'], a[href*='/actresses/']")
        .mapNotNull(::actressFromLink)
        .distinctBy { it.name }

    private fun actressFromLink(link: Element): ActressInfo? {
        val img = link.selectFirst("img")
        val name = clean(img?.attr("title"))
            .ifBlank { clean(img?.attr("alt")) }
            .ifBlank { clean(link.attr("title")) }
            .ifBlank { clean(link.text()) }
        if (name.isBlank()) return null
        return ActressInfo(name = name, thumbUrl = firstUrl(img, imageAttrs))
    }

    private companion object {
        val imageAttrs = listOf("href", "src", "data-src", "data-original", "content")
    }
}
