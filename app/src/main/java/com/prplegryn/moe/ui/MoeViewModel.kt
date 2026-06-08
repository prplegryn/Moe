package com.prplegryn.moe.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prplegryn.moe.MoeApplication
import com.prplegryn.moe.data.model.CloudFile
import com.prplegryn.moe.data.model.LibraryItem
import com.prplegryn.moe.data.model.LibrarySnapshot
import com.prplegryn.moe.data.model.SmsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MoeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MoeApplication).repository
    private val _uiState = MutableStateFlow(MoeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: MoeTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun updatePhone(value: String) {
        _uiState.update { state ->
            if (isSamePhone(state.phone, value)) {
                state.copy(phone = value)
            } else {
                state.copy(phone = value, code = "", smsRequest = null, captchaUrl = null)
            }
        }
    }

    fun updateCode(value: String) {
        _uiState.update { it.copy(code = value) }
    }

    fun updateAuthJson(value: String) {
        _uiState.update { it.copy(authJsonDraft = value) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun refresh() {
        val snapshot = repository.snapshot()
        _uiState.update {
            it.copy(
                snapshot = snapshot,
                importPathDraft = snapshot.settings.importPath,
            )
        }
    }

    fun openDetails(item: LibraryItem) {
        _uiState.update { it.copy(selectedItemId = item.resource.id) }
    }

    fun closeDetails() {
        _uiState.update { it.copy(selectedItemId = null) }
    }

    fun saveImportPath() {
        val state = uiState.value
        val selectedFolderId = if (state.directoryPicker.isLoaded) {
            state.directoryPicker.currentFolderId
        } else {
            state.snapshot.settings.importFolderId
        }
        repository.saveImportPath(
            path = state.importPathDraft,
            folderId = selectedFolderId,
        )
        _uiState.update { it.copy(snapshot = repository.snapshot(), message = "导入路径已保存") }
    }

    fun refreshDirectoryPicker() = launchBusy {
        val folders = repository.listImportDirectories(parentId = null)
        _uiState.update {
            it.copy(
                importPathDraft = "",
                directoryPicker = DirectoryPickerState(
                    currentFolderId = null,
                    crumbs = listOf(DirectoryCrumb("根目录", null)),
                    folders = folders,
                    isLoaded = true,
                ),
                message = "目录已刷新",
            )
        }
    }

    fun openDirectory(folder: CloudFile) = launchBusy {
        val folders = repository.listImportDirectories(parentId = folder.fileId)
        val nextCrumbs = uiState.value.directoryPicker.crumbs + DirectoryCrumb(folder.name, folder.fileId)
        _uiState.update {
            it.copy(
                importPathDraft = nextCrumbs.toImportPath(),
                directoryPicker = it.directoryPicker.copy(
                    currentFolderId = folder.fileId,
                    crumbs = nextCrumbs,
                    folders = folders,
                    isLoaded = true,
                ),
            )
        }
    }

    fun selectDirectoryCrumb(index: Int) = launchBusy {
        val current = uiState.value.directoryPicker.crumbs
        val nextCrumbs = current.take(index + 1).ifEmpty { listOf(DirectoryCrumb("根目录", null)) }
        val target = nextCrumbs.last()
        val folders = repository.listImportDirectories(parentId = target.folderId)
        _uiState.update {
            it.copy(
                importPathDraft = nextCrumbs.toImportPath(),
                directoryPicker = it.directoryPicker.copy(
                    currentFolderId = target.folderId,
                    crumbs = nextCrumbs,
                    folders = folders,
                    isLoaded = true,
                ),
            )
        }
    }

    fun sendSms() = launchBusy {
        val state = uiState.value
        val phone = state.phone.trim()
        require(phone.isNotBlank()) { "请输入手机号" }
        val existing = state.smsRequest
        if (existing != null && isSamePhone(existing.phone, phone)) {
            _uiState.update { it.copy(message = "验证码已发送，请直接输入收到的验证码") }
            return@launchBusy
        }
        val preparation = repository.prepareSmsLogin(phone)
        _uiState.update {
            it.copy(
                smsRequest = preparation.request,
                captchaUrl = preparation.captcha.verificationUrl,
                message = if (preparation.request == null) {
                    preparation.captcha.verificationUrl ?: "光鸭需要验证码校验"
                } else {
                    "验证码已发送"
                },
            )
        }
    }

    fun completeLogin() = launchBusy {
        val request = uiState.value.smsRequest ?: error("请先发送验证码")
        val code = uiState.value.code.trim()
        require(code.isNotBlank()) { "请输入验证码" }
        repository.completeSmsLogin(request, code)
        _uiState.update {
            it.copy(
                code = "",
                smsRequest = null,
                captchaUrl = null,
                snapshot = repository.snapshot(),
                message = "已登录光鸭",
            )
        }
    }

    fun importAuthJson() = launchBusy {
        val rawJson = uiState.value.authJsonDraft.trim()
        require(rawJson.isNotBlank()) { "请粘贴光鸭凭据 JSON" }
        repository.importAuthJson(rawJson)
        _uiState.update {
            it.copy(
                authJsonDraft = "",
                code = "",
                smsRequest = null,
                captchaUrl = null,
                snapshot = repository.snapshot(),
                message = "已导入光鸭登录凭据",
            )
        }
    }

    fun logout() {
        repository.logout()
        _uiState.update { it.copy(snapshot = repository.snapshot(), message = "已退出登录") }
    }

    fun importCloudVideos() = launchBusy {
        val count = repository.importCloudVideos()
        _uiState.update {
            it.copy(
                snapshot = repository.snapshot(),
                message = "已导入 $count 个视频，已自动匹配资料",
            )
        }
    }

    fun scrapeMissing() = launchBusy {
        val count = repository.scrapeMissing()
        _uiState.update {
            it.copy(
                snapshot = repository.snapshot(),
                message = "已匹配 $count 个条目",
            )
        }
    }

    fun scrape(item: LibraryItem) = launchBusy {
        val ok = repository.scrape(item)
        _uiState.update {
            it.copy(
                snapshot = repository.snapshot(),
                message = if (ok) "已匹配 ${item.resource.name}" else "未找到匹配资料",
            )
        }
    }

    fun openPlayer(item: LibraryItem) = launchBusy {
        val url = repository.playableUrl(item.resource)
        _uiState.update {
            it.copy(
                activePlayer = PlayerUiState(
                    item = item,
                    url = url,
                    startPositionMs = item.progress?.positionMs ?: 0L,
                ),
            )
        }
    }

    fun savePlayback(resourceId: Long, positionMs: Long, durationMs: Long) {
        repository.saveProgress(resourceId, positionMs, durationMs)
        _uiState.update { it.copy(snapshot = repository.snapshot()) }
    }

    fun closePlayer(positionMs: Long, durationMs: Long) {
        val player = uiState.value.activePlayer
        if (player != null) {
            repository.saveProgress(player.item.resource.id, positionMs, durationMs)
        }
        _uiState.update { it.copy(activePlayer = null, snapshot = repository.snapshot()) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { block() }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.userFacingMessage()) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

enum class MoeTab {
    Library,
    Cloud,
    Settings,
}

data class MoeUiState(
    val tab: MoeTab = MoeTab.Library,
    val snapshot: LibrarySnapshot = LibrarySnapshot(auth = null, items = emptyList()),
    val isLoading: Boolean = false,
    val message: String? = null,
    val phone: String = "",
    val code: String = "",
    val authJsonDraft: String = "",
    val importPathDraft: String = "",
    val selectedItemId: Long? = null,
    val directoryPicker: DirectoryPickerState = DirectoryPickerState(),
    val smsRequest: SmsRequest? = null,
    val captchaUrl: String? = null,
    val activePlayer: PlayerUiState? = null,
)

data class PlayerUiState(
    val item: LibraryItem,
    val url: String,
    val startPositionMs: Long,
)

data class DirectoryCrumb(
    val name: String,
    val folderId: String?,
)

data class DirectoryPickerState(
    val currentFolderId: String? = null,
    val crumbs: List<DirectoryCrumb> = listOf(DirectoryCrumb("根目录", null)),
    val folders: List<CloudFile> = emptyList(),
    val isLoaded: Boolean = false,
)

private fun List<DirectoryCrumb>.toImportPath(): String {
    val parts = drop(1).map { it.name.trim() }.filter { it.isNotBlank() }
    return if (parts.isEmpty()) "" else "/" + parts.joinToString("/")
}

private fun isSamePhone(left: String, right: String): Boolean {
    val leftKey = phoneKey(left)
    val rightKey = phoneKey(right)
    return leftKey.isNotBlank() && leftKey == rightKey
}

private fun phoneKey(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (digits.length > 11 && digits.startsWith("86")) digits.drop(2) else digits
}

private fun Throwable.userFacingMessage(): String {
    val raw = message ?: return "操作失败"
    return when {
        "HTTP 429" in raw || "resource_exhausted" in raw || "text message per day" in raw ->
            "今日短信验证码额度已用完（同一手机号每天最多 10 条）。如果已经收到验证码，请直接输入后登录；否则需要明天再试。"
        else -> raw
    }
}
