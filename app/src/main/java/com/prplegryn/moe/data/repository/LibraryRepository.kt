package com.prplegryn.moe.data.repository

import com.prplegryn.moe.data.cloud.GuangyaClient
import com.prplegryn.moe.data.local.MoeDatabase
import com.prplegryn.moe.data.model.CloudAuthState
import com.prplegryn.moe.data.model.CloudFile
import com.prplegryn.moe.data.model.CloudProfile
import com.prplegryn.moe.data.model.LibrarySnapshot
import com.prplegryn.moe.data.model.LoginPreparation
import com.prplegryn.moe.data.model.MediaResource
import com.prplegryn.moe.data.model.SmsRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val database: MoeDatabase,
) {
    private var guangyaClient = createClient()

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

    suspend fun accountProfile(): CloudProfile {
        if (!isLoggedIn()) throw IOException("Guangya account is not logged in")
        return guangyaClient.userInfo()
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
        return withContext(Dispatchers.IO) { database.upsertResources(files) }
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
                ?: throw IOException("未找到获取路径：$path")
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
