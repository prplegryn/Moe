package com.prplegryn.moe.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.prplegryn.moe.data.model.CloudFile
import com.prplegryn.moe.data.model.CloudProfile
import com.prplegryn.moe.data.model.LibraryItem
import com.prplegryn.moe.data.model.WatchProgress
import com.prplegryn.moe.ui.player.Vr180PlayerView
import kotlinx.coroutines.delay

@Composable
fun MoeApp(viewModel: MoeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    val player = state.activePlayer
    if (player != null) {
        PlayerScreen(
            state = player,
            onProgress = { position, duration ->
                viewModel.savePlayback(player.item.resource.id, position, duration)
            },
            onClose = { position, duration ->
                viewModel.closePlayer(position, duration)
            },
        )
        return
    }

    MoeScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onTab = viewModel::selectTab,
        onPhone = viewModel::updatePhone,
        onCode = viewModel::updateCode,
        onSendSms = viewModel::sendSms,
        onLogin = viewModel::completeLogin,
        onLogout = viewModel::logout,
        onFetchVideos = viewModel::fetchCloudVideos,
        onOpenPlayer = viewModel::openPlayer,
        onSaveImportPath = viewModel::saveImportPath,
        onRefreshDirectories = viewModel::refreshDirectoryPicker,
        onOpenDirectory = viewModel::openDirectory,
        onSelectDirectoryCrumb = viewModel::selectDirectoryCrumb,
    )
}

@Composable
private fun MoeScaffold(
    state: MoeUiState,
    snackbarHostState: SnackbarHostState,
    onTab: (MoeTab) -> Unit,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onFetchVideos: () -> Unit,
    onOpenPlayer: (LibraryItem) -> Unit,
    onSaveImportPath: () -> Unit,
    onRefreshDirectories: () -> Unit,
    onOpenDirectory: (CloudFile) -> Unit,
    onSelectDirectoryCrumb: (Int) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                TopTabs(
                    selected = state.tab,
                    onTab = onTab,
                    modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 10.dp),
                )
                Box(Modifier.fillMaxSize()) {
                    when (state.tab) {
                        MoeTab.Home -> HomeScreen(
                            state = state,
                            onPhone = onPhone,
                            onCode = onCode,
                            onSendSms = onSendSms,
                            onLogin = onLogin,
                            onLogout = onLogout,
                            onFetchVideos = onFetchVideos,
                            onSaveImportPath = onSaveImportPath,
                            onRefreshDirectories = onRefreshDirectories,
                            onOpenDirectory = onOpenDirectory,
                            onSelectDirectoryCrumb = onSelectDirectoryCrumb,
                        )
                        MoeTab.Library -> LibraryScreen(
                            items = state.snapshot.items,
                            isLoading = state.isLoading,
                            onOpenPlayer = onOpenPlayer,
                            onGoHome = { onTab(MoeTab.Home) },
                        )
                    }
                }
            }
            BusyOverlay(state.isLoading)
        }
    }
}

@Composable
private fun TopTabs(
    selected: MoeTab,
    onTab: (MoeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TabButton(
                    text = "主页",
                    selected = selected == MoeTab.Home,
                    onClick = { onTab(MoeTab.Home) },
                    modifier = Modifier.weight(1f),
                )
                TabButton(
                    text = "库",
                    selected = selected == MoeTab.Library,
                    onClick = { onTab(MoeTab.Library) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HomeScreen(
    state: MoeUiState,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onFetchVideos: () -> Unit,
    onSaveImportPath: () -> Unit,
    onRefreshDirectories: () -> Unit,
    onOpenDirectory: (CloudFile) -> Unit,
    onSelectDirectoryCrumb: (Int) -> Unit,
) {
    var accountExpanded by rememberSaveable { mutableStateOf(false) }
    var pathExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ExpandableSetting(
                title = "光鸭网盘登录",
                expanded = accountExpanded,
                onToggle = { accountExpanded = !accountExpanded },
                summary = {
                    AccountSummary(
                        profile = state.profile,
                        isLoggedIn = state.snapshot.auth != null,
                    )
                },
            ) {
                AccountSettingsContent(
                    state = state,
                    onPhone = onPhone,
                    onCode = onCode,
                    onSendSms = onSendSms,
                    onLogin = onLogin,
                    onLogout = onLogout,
                    onFetchVideos = onFetchVideos,
                )
            }
        }
        item {
            ExpandableSetting(
                title = "获取路径设置",
                expanded = pathExpanded,
                onToggle = { pathExpanded = !pathExpanded },
                summary = {
                    Text(
                        text = currentFolderLabel(state.snapshot.settings.importPath),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ) {
                DirectoryPicker(
                    state = state,
                    onRefreshDirectories = onRefreshDirectories,
                    onOpenDirectory = onOpenDirectory,
                    onSelectDirectoryCrumb = onSelectDirectoryCrumb,
                    onSaveImportPath = onSaveImportPath,
                )
            }
        }
    }
}

@Composable
private fun ExpandableSetting(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    summary()
                }
                Icon(
                    Icons.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AccountSummary(profile: CloudProfile?, isLoggedIn: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccountAvatar(profile = profile, isLoggedIn = isLoggedIn)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = profile?.displayName ?: if (isLoggedIn) "光鸭账号" else "未登录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile?.phone?.takeIf { it != profile.displayName }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AccountAvatar(profile: CloudProfile?, isLoggedIn: Boolean) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (isLoggedIn) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = profile?.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = profile.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = profile?.displayName?.take(1)?.takeIf { it.isNotBlank() } ?: "光",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isLoggedIn) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun AccountSettingsContent(
    state: MoeUiState,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onFetchVideos: () -> Unit,
) {
    val auth = state.snapshot.auth
    if (auth == null) {
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhone,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onSendSms,
                enabled = !state.isLoading && state.smsRequest == null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.smsRequest == null) "发送验证码" else "已发送")
            }
        }
        OutlinedTextField(
            value = state.code,
            onValueChange = onCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(
            onClick = onLogin,
            enabled = !state.isLoading && state.smsRequest != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("登录")
        }
        state.captchaUrl?.let { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Text(
            text = state.profile?.displayName ?: auth.phone ?: "已登录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        auth.phone?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onFetchVideos,
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("获取视频")
            }
            OutlinedButton(onClick = onLogout, enabled = !state.isLoading) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("退出")
            }
        }
    }
}

@Composable
private fun DirectoryPicker(
    state: MoeUiState,
    onRefreshDirectories: () -> Unit,
    onOpenDirectory: (CloudFile) -> Unit,
    onSelectDirectoryCrumb: (Int) -> Unit,
    onSaveImportPath: () -> Unit,
) {
    val picker = state.directoryPicker
    val canBrowse = !state.isLoading && state.snapshot.auth != null
    val current = currentFolderLabel(state.importPathDraft)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "当前：$current",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.snapshot.auth == null) {
            Text(
                text = "登录后可选择光鸭文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(count = picker.crumbs.size) { index ->
                    TextButton(onClick = { onSelectDirectoryCrumb(index) }, enabled = canBrowse) {
                        Text(picker.crumbs[index].name)
                    }
                }
            }
            if (!picker.isLoaded) {
                OutlinedButton(onClick = onRefreshDirectories, enabled = canBrowse) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("读取目录")
                }
            } else if (picker.folders.isEmpty()) {
                Text(
                    text = "当前目录没有子目录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    picker.folders.take(80).forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = canBrowse) { onOpenDirectory(folder) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = folder.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
            Button(onClick = onSaveImportPath, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存路径")
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    items: List<LibraryItem>,
    isLoading: Boolean,
    onOpenPlayer: (LibraryItem) -> Unit,
    onGoHome: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyLibrary(onGoHome = onGoHome, enabled = !isLoading)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${items.size} 个视频文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.resource.id }) { item ->
            VideoFileRow(item = item, onClick = { onOpenPlayer(item) })
        }
    }
}

@Composable
private fun VideoFileRow(item: LibraryItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = item.resource.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatSize(item.resource.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.progress?.takeIf { it.positionMs > 0L }?.let {
                        Text(
                            text = formatProgress(it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item.progress?.takeIf { it.positionMs > 0L }?.let {
                    LinearProgressIndicator(
                        progress = it.fraction,
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onGoHome: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("库里还没有视频", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onGoHome, enabled = enabled) {
                Text("去主页获取")
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    onProgress: (Long, Long) -> Unit,
    onClose: (Long, Long) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var closed by remember { mutableStateOf(false) }
    var projection by rememberSaveable(state.url) { mutableStateOf(0.54f) }
    var yaw by rememberSaveable(state.url) { mutableStateOf(0f) }
    var pitch by rememberSaveable(state.url) { mutableStateOf(0f) }
    var currentPositionMs by remember { mutableStateOf(state.startPositionMs) }
    var scrubStartMs by remember { mutableStateOf(0L) }
    var scrubTargetMs by remember { mutableStateOf(state.startPositionMs) }
    var scrubDeltaPx by remember { mutableStateOf(0f) }
    var scrubVisible by remember { mutableStateOf(false) }
    var scrubActive by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    val renderViewRef = remember { arrayOfNulls<Vr180PlayerView>(1) }

    val player = remember(state.url) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            setMediaItem(ExoMediaItem.fromUri(state.url))
            if (state.startPositionMs > 0L) seekTo(state.startPositionMs)
            prepare()
            playWhenReady = true
            play()
        }
    }

    fun duration(): Long {
        val value = player.duration
        return if (value == C.TIME_UNSET || value < 0L) 0L else value
    }

    fun closeOnce() {
        if (!closed) {
            closed = true
            onClose(player.currentPosition, duration())
        }
    }

    BackHandler { closeOnce() }

    LaunchedEffect(player) {
        player.playWhenReady = true
        player.play()
        while (true) {
            delay(2_000L)
            onProgress(player.currentPosition, duration())
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackStateValue: Int) {
                playbackState = playbackStateValue
                if (playbackStateValue == Player.STATE_READY && player.playWhenReady && !player.isPlaying) {
                    player.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playerError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = listOfNotNull(error.errorCodeName, error.message)
                    .joinToString("：")
                    .ifBlank { "播放失败" }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(250L)
            if (!scrubActive) currentPositionMs = player.currentPosition
        }
    }

    LaunchedEffect(scrubActive) {
        if (!scrubActive && scrubVisible) {
            delay(500L)
            scrubVisible = false
        }
    }

    DisposableEffect(activity, player) {
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val decorView = activity?.window?.decorView
        val previousUiVisibility = decorView?.systemUiVisibility ?: 0
        val keepScreenOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val hadKeepScreenOn = activity?.window?.attributes?.flags?.let { it and keepScreenOn != 0 } ?: false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(keepScreenOn)
        decorView?.systemUiVisibility = (
            previousUiVisibility
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        onDispose {
            closeOnce()
            player.clearVideoSurface()
            renderViewRef[0]?.releaseVideoSurface()
            if (!hadKeepScreenOn) activity?.window?.clearFlags(keepScreenOn)
            decorView?.systemUiVisibility = previousUiVisibility
            activity?.requestedOrientation = previous
            player.release()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val topHeight = maxHeight * 0.2f
        val bottomHeight = maxHeight * 0.2f
        val middleHeight = maxHeight * 0.6f
        val durationMs = duration()
        val progressPosition = if (scrubVisible) scrubTargetMs else currentPositionMs

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                Vr180PlayerView(viewContext).apply {
                    renderViewRef[0] = this
                    onSurfaceAvailable = { surface ->
                        player.setVideoSurface(surface)
                        player.playWhenReady = true
                        player.play()
                    }
                    setProjection(projection)
                    setLook(yaw, pitch)
                }
            },
            update = { view ->
                view.onSurfaceAvailable = { surface ->
                    player.setVideoSurface(surface)
                    player.playWhenReady = true
                    player.play()
                }
                view.setProjection(projection)
                view.setLook(yaw, pitch)
            },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topHeight)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        projection = (projection + dragAmount.x / size.width.toFloat() * 0.36f)
                            .coerceIn(0.2f, 0.9f)
                    }
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(middleHeight)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        yaw = 0f
                        pitch = 0f
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw = (yaw + dragAmount.x / size.width.toFloat() * 0.9f).coerceIn(-1.35f, 1.35f)
                        pitch = (pitch - dragAmount.y / size.height.toFloat() * 0.7f).coerceIn(-0.7f, 0.7f)
                    }
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomHeight)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (player.isPlaying) player.pause() else player.play()
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            val availableDuration = duration()
                            scrubStartMs = player.currentPosition.coerceIn(0L, availableDuration.coerceAtLeast(0L))
                            scrubTargetMs = scrubStartMs
                            scrubDeltaPx = 0f
                            scrubVisible = true
                            scrubActive = true
                        },
                        onDragEnd = {
                            if (duration() > 0L) {
                                player.seekTo(scrubTargetMs)
                                currentPositionMs = scrubTargetMs
                            }
                            scrubActive = false
                        },
                        onDragCancel = {
                            scrubActive = false
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val availableDuration = duration()
                        if (availableDuration > 0L) {
                            scrubDeltaPx += dragAmount.x
                            val deltaMs = (scrubDeltaPx / size.width.toFloat() * availableDuration * 0.55f).toLong()
                            scrubTargetMs = (scrubStartMs + deltaMs).coerceIn(0L, availableDuration)
                            currentPositionMs = scrubTargetMs
                        }
                    }
                },
        )

        AnimatedVisibility(
            visible = scrubVisible && durationMs > 0L,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topHeight + 12.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = (progressPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
                Text(
                    text = "${formatTime(progressPosition)} / ${formatTime(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        playerError?.let { error ->
            Text(
                text = error,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .background(Color.Black.copy(alpha = 0.62f), MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } ?: if (playbackState == Player.STATE_BUFFERING && currentPositionMs == 0L) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun BusyOverlay(isLoading: Boolean) {
    AnimatedVisibility(
        visible = isLoading,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn() + slideInVertically { -it / 8 },
        exit = fadeOut(),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier.padding(top = 10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("处理中", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun currentFolderLabel(path: String): String {
    val trimmed = path.trim().trim('/')
    return trimmed.substringAfterLast('/').ifBlank { "全盘视频" }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}" else "%.1f %s".format(value, units[unit])
}

private fun formatProgress(progress: WatchProgress): String {
    return "${formatTime(progress.positionMs)} / ${formatTime(progress.durationMs)}"
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hour = total / 3600
    val minute = (total % 3600) / 60
    val second = total % 60
    return if (hour > 0) "%d:%02d:%02d".format(hour, minute, second) else "%02d:%02d".format(minute, second)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
