package com.prplegryn.moe.data.scraper

import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.ScraperResult
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

class R18DevScraper(
    private val baseUrl: String = "https://r18.dev",
) : MetadataScraper {
    override val name = "r18dev"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): ScraperResult? {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return null
        val normalized = normalizeR18Id(cleanQuery)
        val candidates = listOf(
            "$baseUrl/videos/vod/movies/detail/-/combined=$normalized/json",
            "$baseUrl/videos/vod/movies/detail/-/dvd_id=$normalized/json",
        )
        for (url in candidates) {
            val result = runCatching { parseMovie(url, cleanQuery) }.getOrNull()
            if (result != null && idsMatch(result.id, cleanQuery)) return result
        }
        return null
    }

    private suspend fun parseMovie(url: String, fallbackId: String): ScraperResult {
        val root = json.parseToJsonElement(fetchHtml(url, standardHtmlHeaders(mapOf("Referer" to "$baseUrl/")))).jsonObject
        val contentId = root.string("content_id")
        val dvdId = root.string("dvd_id").ifBlank { fallbackId.uppercase(Locale.ROOT) }
        val titleJa = root.string("title_ja")
        val titleEn = root.string("title_en").ifBlank { root.string("title") }
        val originalTitle = titleJa.ifBlank { titleEn.ifBlank { dvdId } }
        val poster = normalizeDmmPoster(root.string("jacket_full_url").ifBlank {
            root.obj("images")?.obj("jacket_image")?.string("large2").orEmpty()
                .ifBlank { root.obj("images")?.obj("jacket_image")?.string("large").orEmpty() }
        })

        return ScraperResult(
            source = name,
            sourceUrl = url,
            language = "ja",
            id = dvdId,
            contentId = contentId.ifBlank { normalizeR18Id(dvdId) },
            title = originalTitle,
            originalTitle = originalTitle,
            description = root.string("description").ifBlank { root.string("description_en") }.ifBlank { null },
            releaseDate = parseDate(root.string("release_date")),
            runtimeMinutes = root.int("runtime_mins").takeIf { it > 0 } ?: root.int("runtime_minutes"),
            director = root.array("directors")?.firstObject()?.let { director ->
                director.string("name_kanji").ifBlank { director.string("name_romaji") }
            }?.ifBlank { root.string("director") }?.ifBlank { null },
            maker = root.string("maker_name_ja").ifBlank {
                root.string("maker_name_en").ifBlank { root.obj("maker")?.string("name").orEmpty() }
            }.ifBlank { null },
            label = root.string("label_name_ja").ifBlank {
                root.string("label_name_en").ifBlank { root.obj("label")?.string("name").orEmpty() }
            }.ifBlank { null },
            series = root.string("series_name_ja").ifBlank {
                root.string("series_name_en").ifBlank {
                    root.string("series_name").ifBlank { root.obj("series")?.string("name").orEmpty() }
                }
            }.ifBlank { null },
            rating = null,
            actresses = extractActresses(root),
            genres = root.array("categories")
                ?.mapNotNull { element ->
                    (element as? JsonObject)?.let { it.string("name_ja").ifBlank { it.string("name_en").ifBlank { it.string("name") } } }
                }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                .orEmpty(),
            posterUrl = poster,
            coverUrl = poster,
            trailerUrl = root.string("sample_url").ifBlank {
                root.obj("sample")?.string("high").orEmpty().ifBlank { root.obj("sample")?.string("low").orEmpty() }
            }.ifBlank { null },
            screenshots = extractScreenshots(root),
        )
    }

    private fun extractActresses(root: JsonObject): List<ActressInfo> {
        return root.array("actresses")
            ?.mapNotNull { element ->
                val actress = element as? JsonObject ?: return@mapNotNull null
                val name = actress.string("name_kanji").ifBlank {
                    actress.string("name_romaji").ifBlank { actress.string("name") }
                }
                if (name.isBlank()) return@mapNotNull null
                val thumb = actress.string("image_url").let { url ->
                    when {
                        url.isBlank() -> null
                        url.startsWith("http") -> url
                        else -> "https://pics.dmm.co.jp/mono/actjpgs/$url"
                    }
                }
                ActressInfo(name = name, thumbUrl = thumb)
            }
            ?.distinctBy { it.name }
            .orEmpty()
    }

    private fun extractScreenshots(root: JsonObject): List<String> {
        val gallery = root.array("gallery")
            ?.mapNotNull { (it as? JsonObject)?.string("image_full")?.takeIf(String::isNotBlank) }
            .orEmpty()
        val fallback = root.obj("images")
            ?.array("sample_images")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            .orEmpty()
        return (gallery.ifEmpty { fallback })
            .map(::normalizeDmmScreenshot)
            .distinct()
    }

    private fun normalizeR18Id(value: String): String =
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
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.obj(key: String): JsonObject? =
    this[key] as? JsonObject

private fun JsonObject.array(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonArray.firstObject(): JsonObject? =
    firstOrNull { it !is JsonNull } as? JsonObject
