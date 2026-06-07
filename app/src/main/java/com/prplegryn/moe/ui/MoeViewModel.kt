package com.prplegryn.moe.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prplegryn.moe.MoeApplication
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
        _uiState.update { it.copy(phone = value) }
    }

    fun updateCode(value: String) {
        _uiState.update { it.copy(code = value) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun refresh() {
        _uiState.update { it.copy(snapshot = repository.snapshot()) }
    }

    fun sendSms() = launchBusy {
        val phone = uiState.value.phone.trim()
        require(phone.isNotBlank()) { "请输入手机号" }
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

    fun logout() {
        repository.logout()
        _uiState.update { it.copy(snapshot = repository.snapshot(), message = "已退出登录") }
    }

    fun importCloudVideos() = launchBusy {
        val count = repository.importCloudVideos()
        _uiState.update {
            it.copy(
                snapshot = repository.snapshot(),
                message = "已导入 $count 个视频",
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
                    _uiState.update { it.copy(message = error.message ?: "操作失败") }
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
    val smsRequest: SmsRequest? = null,
    val captchaUrl: String? = null,
    val activePlayer: PlayerUiState? = null,
)

data class PlayerUiState(
    val item: LibraryItem,
    val url: String,
    val startPositionMs: Long,
)
