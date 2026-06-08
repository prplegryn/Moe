package com.prplegryn.moe.data.model

import kotlinx.serialization.Serializable

data class CloudAuthState(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long?,
    val deviceId: String,
    val phone: String?,
)

data class SmsCaptcha(
    val captchaToken: String?,
    val verificationUrl: String?,
)

data class SmsRequest(
    val phone: String,
    val username: String,
    val captchaToken: String,
    val verificationId: String,
)

data class LoginPreparation(
    val captcha: SmsCaptcha,
    val request: SmsRequest?,
)

data class CloudFile(
    val fileId: String,
    val parentId: String?,
    val name: String,
    val size: Long,
    val fileType: Int,
    val isDirectory: Boolean,
    val updatedAt: Long,
)

data class MediaResource(
    val id: Long,
    val cloudFileId: String,
    val parentId: String?,
    val name: String,
    val size: Long,
    val fileType: Int,
    val isDirectory: Boolean,
    val downloadUrl: String?,
    val importedAt: Long,
    val updatedAt: Long,
)

data class LibraryItem(
    val resource: MediaResource,
    val metadata: MovieMetadata?,
    val progress: WatchProgress?,
)

data class WatchProgress(
    val resourceId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

@Serializable
data class ActressInfo(
    val name: String,
    val thumbUrl: String? = null,
)

@Serializable
data class Rating(
    val score: Double,
    val votes: Int = 0,
)

data class ScraperResult(
    val source: String,
    val sourceUrl: String,
    val language: String,
    val id: String,
    val contentId: String,
    val title: String,
    val originalTitle: String,
    val description: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int,
    val director: String?,
    val maker: String?,
    val label: String?,
    val series: String?,
    val rating: Rating?,
    val actresses: List<ActressInfo>,
    val genres: List<String>,
    val posterUrl: String?,
    val coverUrl: String?,
    val trailerUrl: String?,
    val screenshots: List<String>,
)

data class MovieMetadata(
    val id: Long = 0L,
    val resourceId: Long,
    val contentId: String,
    val title: String,
    val originalTitle: String,
    val description: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int,
    val director: String?,
    val maker: String?,
    val label: String?,
    val series: String?,
    val ratingScore: Double?,
    val ratingVotes: Int?,
    val posterUrl: String?,
    val coverUrl: String?,
    val trailerUrl: String?,
    val sourceName: String,
    val sourceUrl: String,
    val actresses: List<ActressInfo>,
    val genres: List<String>,
    val screenshots: List<String>,
    val updatedAt: Long,
)

data class LibrarySnapshot(
    val auth: CloudAuthState?,
    val items: List<LibraryItem>,
)
