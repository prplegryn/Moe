package com.prplegryn.moe.data.repository

import com.prplegryn.moe.data.cloud.GuangyaClient
import com.prplegryn.moe.data.local.MoeDatabase
import com.prplegryn.moe.data.model.CloudAuthState
import com.prplegryn.moe.data.model.LibraryItem
import com.prplegryn.moe.data.model.LibrarySnapshot
import com.prplegryn.moe.data.model.LoginPreparation
import com.prplegryn.moe.data.model.MediaResource
import com.prplegryn.moe.data.model.SmsRequest
import com.prplegryn.moe.data.scraper.MetadataAggregator
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val database: MoeDatabase,
    private val aggregator: MetadataAggregator,
) {
    private var guangyaClient = createClient()

    fun snapshot(): LibrarySnapshot = database.snapshot()

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
            val signInCaptcha = guangyaClient.loginSignInCaptcha(phone)
            val signInToken = signInCaptcha.captchaToken
                ?: throw IOException(signInCaptcha.verificationUrl ?: "光鸭登录需要额外验证码校验")
            guangyaClient.loginSmsSend(
                phone = phone,
                captchaToken = token,
                signInCaptchaToken = signInToken,
            )
        }
        return LoginPreparation(captcha = captcha, request = request)
    }

    suspend fun completeSmsLogin(request: SmsRequest, code: String): CloudAuthState {
        val verificationToken = guangyaClient.loginSmsVerify(request.verificationId, code)
        return guangyaClient.loginSmsSignIn(
            username = request.username,
            verificationCode = code,
            verificationToken = verificationToken,
            captchaToken = request.signInCaptchaToken,
        )
    }

    suspend fun importCloudVideos(): Int {
        if (!isLoggedIn()) throw IOException("Guangya account is not logged in")
        val files = mutableListOf<com.prplegryn.moe.data.model.CloudFile>()
        var page = 0
        do {
            val batch = guangyaClient.listVideos(page = page, pageSize = 100)
            files += batch.filter { !it.isDirectory }
            page++
        } while (batch.size >= 100 && page < 50)
        return withContext(Dispatchers.IO) { database.upsertResources(files) }
    }

    suspend fun scrapeMissing(): Int {
        val items = withContext(Dispatchers.IO) { database.snapshot().items }
        var saved = 0
        for (item in items) {
            if (item.metadata != null) continue
            val metadata = aggregator.scrape(item.resource) ?: continue
            withContext(Dispatchers.IO) { database.saveMetadata(metadata) }
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
}
