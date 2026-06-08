package com.prplegryn.moe.data.repository

import com.prplegryn.moe.data.cloud.GuangyaClient
import com.prplegryn.moe.data.local.MoeDatabase
import com.prplegryn.moe.data.model.CloudFile
import com.prplegryn.moe.data.model.CloudAuthState
import com.prplegryn.moe.data.model.LibraryItem
import com.prplegryn.moe.data.model.LibrarySnapshot
import com.prplegryn.moe.data.model.LoginPreparation
import com.prplegryn.moe.data.model.MediaResource
import com.prplegryn.moe.data.model.MovieMetadata
import com.prplegryn.moe.data.model.SmsRequest
import com.prplegryn.moe.data.scraper.MetadataAggregator
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

class LibraryRepository(
    private val database: MoeDatabase,
    private val aggregator: MetadataAggregator,
) {
    private var guangyaClient = createClient()
    private val credentialJson = Json { ignoreUnknownKeys = true }

    fun snapshot(): LibrarySnapshot = database.snapshot()

    fun saveImportPath(path: String, folderId: String?) {
        database.saveImportPath(path, folderId)
    }

    suspend fun listImportDirectories(parentId: String?): List<CloudFile> {
        if (!isLoggedIn()) throw IOException("Guangya account is not logged in")
        return listFolderPage(parentId)
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
    }

    fun isLoggedIn(): Boolean = database.getAuth() != null

    fun logout() {
        database.clearAuth()
        guangyaClient = createClient()
    }

    fun importAuthJson(rawJson: String): CloudAuthState {
        val root = runCatching { credentialJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { throw IOException("凭据 JSON 格式不正确") }
        val refreshToken = root.deepString("refresh_token", "refreshToken")
        val accessToken = root.deepString("access_token", "accessToken").orEmpty()
        if (accessToken.isBlank() && refreshToken.isNullOrBlank()) {
            throw IOException("凭据缺少 access_token 或 refresh_token")
        }
        val expiresAtMillis = normalizeImportedExpiry(
            root.deepLong("expires_at_millis", "expiresAtMillis", "expires_at", "expiresAt"),
        ) ?: root.deepLong("expires_in", "expiresIn")?.let { System.currentTimeMillis() + it * 1000L }
        val auth = CloudAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAtMillis ?: if (accessToken.isBlank()) 0L else null,
            deviceId = root.deepString("device_id", "deviceId", "did")
                ?: database.getAuth()?.deviceId
                ?: throw IOException("凭据缺少 device_id"),
            phone = root.deepString("phone", "username", "mobile"),
        )
        database.saveAuth(auth)
        guangyaClient = createClient()
        return auth
    }

    suspend fun prepareSmsLogin(phone: String): LoginPreparation {
        val captcha = guangyaClient.loginSmsInit(phone)
        val token = captcha.captchaToken
        val request = if (token.isNullOrBlank()) {
            null
        } else {
            guangyaClient.loginSmsSend(
                phone = phone,
                captchaToken = token,
            )
        }
        return LoginPreparation(captcha = captcha, request = request)
    }

    suspend fun completeSmsLogin(request: SmsRequest, code: String): CloudAuthState {
        val verificationToken = guangyaClient.loginSmsVerify(request.verificationId, code)
        val signInCaptcha = guangyaClient.loginSignInCaptcha(request.username)
        val signInToken = signInCaptcha.captchaToken
            ?: throw IOException(signInCaptcha.verificationUrl ?: "光鸭登录需要额外验证码校验")
        return guangyaClient.loginSmsSignIn(
            username = request.username,
            verificationCode = code,
            verificationToken = verificationToken,
            captchaToken = signInToken,
        )
    }

    suspend fun importCloudVideos(): Int {
        if (!isLoggedIn()) throw IOException("Guangya account is not logged in")
        val settings = database.getSettings()
        val importPath = settings.importPath.trim()
        val selectedFolderId = settings.importFolderId?.takeIf { it.isNotBlank() }
        val files = when {
            selectedFolderId != null -> listVideosUnder(selectedFolderId)
            importPath.isBlank() -> listAllVideos()
            else -> listVideosUnder(resolveFolderPath(importPath))
        }
        val imported = withContext(Dispatchers.IO) { database.upsertResources(files) }
        scrapeMissing()
        return imported
    }

    suspend fun scrapeMissing(): Int {
        val items = withContext(Dispatchers.IO) { database.snapshot().items }
        var saved = 0
        for (item in items) {
            val metadata = item.metadata
            if (metadata != null && metadata.sourceName == R18DEV_SOURCE && metadata.hasArtwork()) continue
            val scraped = aggregator.scrape(item.resource) ?: continue
            withContext(Dispatchers.IO) { database.saveMetadata(scraped) }
            saved++
        }
        return saved
    }

    suspend fun scrape(item: LibraryItem): Boolean {
        val metadata = aggregator.scrape(item.resource) ?: return false
        withContext(Dispatchers.IO) { database.saveMetadata(metadata) }
        return true
    }

    suspend fun playableUrl(resource: MediaResource): String {
        val fresh = runCatching { guangyaClient.downloadUrl(resource.cloudFileId) }.getOrNull()
        if (!fresh.isNullOrBlank()) {
            withContext(Dispatchers.IO) { database.updateDownloadUrl(resource.id, fresh) }
            return fresh
        }
        return resource.downloadUrl ?: throw IOException("Unable to resolve playable URL")
    }

    fun saveProgress(resourceId: Long, positionMs: Long, durationMs: Long) {
        database.saveProgress(resourceId, positionMs, durationMs)
    }

    private fun createClient(): GuangyaClient {
        return GuangyaClient(database.getAuth()) { auth ->
            database.saveAuth(auth)
        }
    }

    private suspend fun listAllVideos(): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        var page = 0
        do {
            val batch = guangyaClient.listVideos(page = page, pageSize = 100)
            files += batch.filter { !it.isDirectory }
            page++
        } while (batch.size >= 100 && page < 50)
        return files
    }

    private suspend fun resolveFolderPath(path: String): String? {
        val parts = path.trim().trim('/').split('/').map(String::trim).filter(String::isNotBlank)
        if (parts.isEmpty()) return null
        var parentId: String? = null
        for (part in parts) {
            val children = listFolderPage(parentId)
            val folder = children.firstOrNull { it.isDirectory && it.name.equals(part, ignoreCase = true) }
                ?: throw IOException("未找到导入路径：$path")
            parentId = folder.fileId
        }
        return parentId
    }

    private suspend fun listVideosUnder(parentId: String?): List<CloudFile> {
        val output = mutableListOf<CloudFile>()
        val stack = mutableListOf<String?>()
        stack.add(parentId)
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.lastIndex)
            val children = listFolderPage(current)
            for (file in children) {
                if (file.isDirectory) {
                    stack.add(file.fileId)
                } else if (file.fileType == 2 || file.name.isVideoFileName()) {
                    output.add(file)
                }
            }
        }
        return output
    }

    private suspend fun listFolderPage(parentId: String?): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        var page = 0
        do {
            val batch = guangyaClient.listFiles(parentId = parentId, page = page, pageSize = 100)
            files += batch
            page++
        } while (batch.size >= 100 && page < 50)
        return files
    }
}

private fun String.isVideoFileName(): Boolean {
    val lower = lowercase()
    return listOf(".mp4", ".mkv", ".avi", ".wmv", ".flv", ".mov", ".m4v", ".ts", ".webm").any(lower::endsWith)
}

private fun MovieMetadata.hasArtwork(): Boolean {
    return !posterUrl.isNullOrBlank() && !coverUrl.isNullOrBlank() && screenshots.isNotEmpty()
}

private const val R18DEV_SOURCE = "r18dev"

private fun normalizeImportedExpiry(value: Long?): Long? {
    if (value == null || value <= 0L) return null
    return if (value < 10_000_000_000L) value * 1000L else value
}

private fun JsonObject.deepString(vararg keys: String): String? {
    val found = findCredentialField(keys.map { it.lowercase() }.toSet()) as? JsonPrimitive ?: return null
    return found.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.deepLong(vararg keys: String): Long? {
    val found = findCredentialField(keys.map { it.lowercase() }.toSet()) as? JsonPrimitive ?: return null
    return found.longOrNull ?: found.contentOrNull?.trim()?.toLongOrNull()
}

private fun JsonElement.findCredentialField(keys: Set<String>): JsonElement? {
    when (this) {
        is JsonObject -> {
            for ((key, value) in this) {
                if (key.lowercase() in keys) return value
            }
            for (value in values) {
                val found = value.findCredentialField(keys)
                if (found != null) return found
            }
        }
        is JsonArray -> {
            for (value in this) {
                val found = value.findCredentialField(keys)
                if (found != null) return found
            }
        }
    }
    return null
}
