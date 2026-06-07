package com.prplegryn.moe.data.cloud

import com.prplegryn.moe.data.model.CloudAuthState
import com.prplegryn.moe.data.model.CloudFile
import com.prplegryn.moe.data.model.SmsCaptcha
import com.prplegryn.moe.data.model.SmsRequest
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GuangyaClient(
    private var authState: CloudAuthState?,
    private val onAuthChanged: (CloudAuthState) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val currentAuth: CloudAuthState?
        get() = authState

    private val deviceId: String
        get() = authState?.deviceId ?: generateDid()

    suspend fun loginSmsInit(phone: String, captchaToken: String? = null): SmsCaptcha {
        val body = buildJsonObject {
            put("client_id", CLIENT_ID)
            put("action", "POST:/v1/auth/verification")
            put("device_id", deviceId)
            putJsonObject("meta") {
                put("phone_number", phone)
            }
            captchaToken?.let { put("captcha_token", it) }
        }
        val result = postPublicJson(
            url = "https://account.guangyapan.com/v1/shield/captcha/init",
            body = body,
            headers = accountHeaders(),
        )
        return SmsCaptcha(
            captchaToken = result.deepString("captcha_token", "captchaToken"),
            verificationUrl = result.deepString("url", "captcha_url", "captchaUrl"),
        )
    }

    suspend fun loginSmsSend(phone: String, captchaToken: String, target: String = "ANY"): SmsRequest {
        val result = postPublicJson(
            url = "https://account.guangyapan.com/v1/auth/verification",
            body = buildJsonObject {
                put("phone_number", phone)
                put("target", target)
                put("client_id", CLIENT_ID)
            },
            headers = accountHeaders(extra = mapOf("x-captcha-token" to captchaToken)),
        )
        val verificationId = result.deepString("verification_id", "verificationId")
            ?: throw IOException("Guangya SMS send did not return verification_id")
        return SmsRequest(
            phone = phone,
            captchaToken = captchaToken,
            verificationId = verificationId,
        )
    }

    suspend fun loginSmsVerify(verificationId: String, verificationCode: String): String {
        val result = postPublicJson(
            url = "https://account.guangyapan.com/v1/auth/verification/verify",
            body = buildJsonObject {
                put("verification_id", verificationId)
                put("verification_code", verificationCode)
                put("client_id", CLIENT_ID)
            },
            headers = accountHeaders(),
        )
        return result.deepString("verification_token", "verificationToken")
            ?: throw IOException("Guangya SMS verify did not return verification_token")
    }

    suspend fun loginSmsSignIn(
        phone: String,
        verificationCode: String,
        verificationToken: String,
        captchaToken: String,
    ): CloudAuthState {
        val result = postPublicJson(
            url = "https://account.guangyapan.com/v1/auth/signin",
            body = buildJsonObject {
                put("verification_code", verificationCode)
                put("verification_token", verificationToken)
                put("username", phone)
                put("client_id", CLIENT_ID)
            },
            headers = accountHeaders(extra = mapOf("x-captcha-token" to captchaToken)),
        )
        return updateAuthFromTokenResult(result, phone)
    }

    suspend fun listVideos(page: Int = 0, pageSize: Int = 100): List<CloudFile> {
        val result = apiPost(
            url = "https://api.guangyapan.com/userres/v1/file/get_file_list",
            body = buildJsonObject {
                put("parentId", "*")
                put("page", page)
                put("pageSize", pageSize)
                put("orderBy", 3)
                put("sortType", 1)
                put("resType", 1)
                put("needPlayRecord", true)
                put("fileTypes", JsonArray(listOf(JsonPrimitive(2))))
            },
        )
        return parseCloudFiles(result)
    }

    suspend fun listFiles(parentId: String? = null, page: Int = 0, pageSize: Int = 100): List<CloudFile> {
        val result = apiPost(
            url = "https://api.guangyapan.com/userres/v1/file/get_file_list",
            body = buildJsonObject {
                put("parentId", parentId ?: "")
                put("page", page)
                put("pageSize", pageSize)
                put("orderBy", 0)
                put("sortType", 0)
            },
        )
        return parseCloudFiles(result)
    }

    suspend fun downloadUrl(fileId: String): String {
        val result = apiPost(
            url = "https://api.guangyapan.com/nd.bizuserres.s/v1/get_res_download_url",
            body = buildJsonObject {
                put("fileId", fileId)
            },
        )
        return result.findUrl()
            ?: throw IOException("Guangya did not return a playable download URL")
    }

    suspend fun refreshToken(): CloudAuthState {
        val refreshToken = authState?.refreshToken ?: throw IOException("Missing Guangya refresh token")
        val result = postPublicJson(
            url = "https://account.guangyapan.com/v1/auth/token",
            body = buildJsonObject {
                put("client_id", CLIENT_ID)
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
            },
            headers = accountHeaders(extra = mapOf("x-action" to "401")),
        )
        return updateAuthFromTokenResult(result, authState?.phone)
    }

    private suspend fun apiPost(url: String, body: JsonObject): JsonObject {
        ensureFreshToken()
        val first = executeJsonRequest(url, body, apiHeaders(), canThrowUnauthorized = false)
        if (first.statusCode == 401 && authState?.refreshToken != null) {
            refreshToken()
            val second = executeJsonRequest(url, body, apiHeaders(), canThrowUnauthorized = true)
            return second.body
        }
        if (first.statusCode !in 200..299) {
            throw IOException("Guangya API returned HTTP ${first.statusCode}")
        }
        return first.body
    }

    private suspend fun postPublicJson(url: String, body: JsonObject, headers: Headers): JsonObject {
        val result = executeJsonRequest(url, body, headers, canThrowUnauthorized = true)
        return result.body
    }

    private suspend fun executeJsonRequest(
        url: String,
        body: JsonObject,
        headers: Headers,
        canThrowUnauthorized: Boolean,
    ): JsonResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .post(body.toString().toRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 && !canThrowUnauthorized) {
                return@withContext JsonResponse(response.code, JsonObject(emptyMap()))
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${text.take(200)}")
            }
            val root = if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
            JsonResponse(response.code, root.jsonObject)
        }
    }

    private suspend fun ensureFreshToken() {
        val state = authState ?: throw IOException("Guangya account is not logged in")
        val expiresAt = state.expiresAtMillis ?: return
        if (System.currentTimeMillis() + 60_000L >= expiresAt && state.refreshToken != null) {
            refreshToken()
        }
    }

    private fun updateAuthFromTokenResult(result: JsonObject, phone: String?): CloudAuthState {
        val accessToken = result.deepString("access_token", "accessToken")
            ?: throw IOException("Guangya token response did not include access_token")
        val refreshToken = result.deepString("refresh_token", "refreshToken") ?: authState?.refreshToken
        val expiresInSeconds = result.deepLong("expires_in", "expiresIn")
        val newState = CloudAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresInSeconds?.let { System.currentTimeMillis() + it * 1000L },
            deviceId = deviceId,
            phone = phone,
        )
        authState = newState
        onAuthChanged(newState)
        return newState
    }

    private fun apiHeaders(): Headers = Headers.Builder()
        .add("accept", "application/json, text/plain, */*")
        .add("authorization", "Bearer ${authState?.accessToken.orEmpty()}")
        .add("content-type", "application/json")
        .add("did", deviceId)
        .add("dt", "4")
        .add("origin", "https://www.guangyapan.com")
        .add("referer", "https://www.guangyapan.com/")
        .add("traceparent", generateTraceparent())
        .add("user-agent", USER_AGENT)
        .build()

    private fun accountHeaders(extra: Map<String, String> = emptyMap()): Headers {
        val builder = Headers.Builder()
            .add("accept", "*/*")
            .add("content-type", "application/json")
            .add("origin", "https://www.guangyapan.com")
            .add("referer", "https://www.guangyapan.com/")
            .add("user-agent", USER_AGENT)
            .add("x-client-id", CLIENT_ID)
            .add("x-client-version", "0.0.1")
            .add("x-device-id", deviceId)
            .add("x-device-model", "chrome%2F147.0.0.0")
            .add("x-device-name", "PC-Chrome")
            .add("x-device-sign", "wdi10.$deviceId${randomHex(16)}")
            .add("x-net-work-type", "NONE")
            .add("x-os-version", "MacIntel")
            .add("x-platform-version", "1")
            .add("x-protocol-version", "301")
            .add("x-provider-name", "NONE")
            .add("x-sdk-version", "9.0.2")
        extra.forEach { (key, value) -> builder.add(key, value) }
        return builder.build()
    }

    private fun parseCloudFiles(root: JsonObject): List<CloudFile> {
        val array = root.findArray("list", "files", "items", "records", "rows", "data") ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.deepString("fileId", "file_id", "id", "fid") ?: return@mapNotNull null
            val name = obj.deepString("fileName", "filename", "file_name", "name", "title") ?: return@mapNotNull null
            val fileType = obj.deepInt("fileType", "file_type", "type") ?: 2
            val isDirectory = obj.deepBoolean("isDir", "is_dir", "isDirectory", "folder", "directory")
                ?: (fileType == 0)
            CloudFile(
                fileId = id,
                parentId = obj.deepString("parentId", "parent_id", "pid"),
                name = name,
                size = obj.deepLong("size", "fileSize", "file_size") ?: 0L,
                fileType = fileType,
                isDirectory = isDirectory,
                updatedAt = obj.deepLong("updatedAt", "updateTime", "updated_time", "mtime") ?: 0L,
            )
        }
    }

    private fun generateDid(): String {
        val raw = UUID.randomUUID().toString() + randomHex(8)
        return md5(raw.toByteArray())
    }

    private fun generateTraceparent(): String = "00-${randomHex(16)}-${randomHex(8)}-01"

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class JsonResponse(val statusCode: Int, val body: JsonObject)

    companion object {
        private const val CLIENT_ID = "aMe-8VSlkrbQXpUR"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    }
}

private fun JsonObject.deepString(vararg keys: String): String? {
    for (key in keys) {
        findByKey(key)?.asString()?.let { return it }
    }
    return null
}

private fun JsonObject.deepLong(vararg keys: String): Long? {
    for (key in keys) {
        findByKey(key)?.asLong()?.let { return it }
    }
    return null
}

private fun JsonObject.deepInt(vararg keys: String): Int? {
    for (key in keys) {
        findByKey(key)?.asInt()?.let { return it }
    }
    return null
}

private fun JsonObject.deepBoolean(vararg keys: String): Boolean? {
    for (key in keys) {
        findByKey(key)?.asBoolean()?.let { return it }
    }
    return null
}

private fun JsonObject.findUrl(): String? {
    val urlKeys = listOf("downloadUrl", "download_url", "url", "playUrl", "play_url", "link")
    for (key in urlKeys) {
        deepString(key)?.takeIf { it.startsWith("http") }?.let { return it }
    }
    for (value in values) {
        when (value) {
            is JsonObject -> value.findUrl()?.let { return it }
            is JsonArray -> value.firstNotNullOfOrNull { element ->
                (element as? JsonObject)?.findUrl() ?: element.asString()?.takeIf { it.startsWith("http") }
            }?.let { return it }
            else -> value.asString()?.takeIf { it.startsWith("http") }?.let { return it }
        }
    }
    return null
}

private fun JsonObject.findArray(vararg keys: String): JsonArray? {
    for (key in keys) {
        val value = findByKey(key)
        when (value) {
            is JsonArray -> return value
            is JsonObject -> value.findArray("list", "files", "items", "records", "rows")?.let { return it }
        }
    }
    for (value in values) {
        if (value is JsonObject) {
            value.findArray("list", "files", "items", "records", "rows")?.let { return it }
        }
    }
    return null
}

private fun JsonObject.findByKey(target: String): JsonElement? {
    entries.firstOrNull { it.key.equals(target, ignoreCase = true) }?.let { return it.value }
    for (value in values) {
        if (value is JsonObject) {
            value.findByKey(target)?.let { return it }
        }
    }
    return null
}

private fun JsonElement.asString(): String? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    else -> null
}

private fun JsonElement.asLong(): Long? = when (this) {
    is JsonPrimitive -> longOrNull ?: contentOrNull?.filter { it.isDigit() }?.toLongOrNull()
    else -> null
}

private fun JsonElement.asInt(): Int? = when (this) {
    is JsonPrimitive -> intOrNull ?: asLong()?.toInt()
    else -> null
}

private fun JsonElement.asBoolean(): Boolean? = when (this) {
    is JsonPrimitive -> booleanOrNull ?: contentOrNull?.let {
        when (it.lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }
    }
    else -> null
}
