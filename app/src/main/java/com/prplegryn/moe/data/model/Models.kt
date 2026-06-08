package com.prplegryn.moe.data.model

data class CloudAuthState(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long?,
    val deviceId: String,
    val phone: String?,
)

data class CloudProfile(
    val displayName: String,
    val avatarUrl: String?,
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

data class LibrarySnapshot(
    val auth: CloudAuthState?,
    val items: List<LibraryItem>,
    val settings: AppSettings = AppSettings(),
)

data class AppSettings(
    val importPath: String = "",
    val importFolderId: String? = null,
)
