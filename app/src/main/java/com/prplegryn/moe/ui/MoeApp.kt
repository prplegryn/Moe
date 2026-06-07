package com.prplegryn.moe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.prplegryn.moe.data.model.LibraryItem
import com.prplegryn.moe.data.model.MovieMetadata
import com.prplegryn.moe.data.model.WatchProgress
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
        onRefresh = viewModel::refresh,
        onScrapeMissing = viewModel::scrapeMissing,
        onPhone = viewModel::updatePhone,
        onCode = viewModel::updateCode,
        onSendSms = viewModel::sendSms,
        onLogin = viewModel::completeLogin,
        onLogout = viewModel::logout,
        onImport = viewModel::importCloudVideos,
        onPlay = viewModel::openPlayer,
        onScrape = viewModel::scrape,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoeScaffold(
    state: MoeUiState,
    snackbarHostState: SnackbarHostState,
    onTab: (MoeTab) -> Unit,
    onRefresh: () -> Unit,
    onScrapeMissing: () -> Unit,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onImport: () -> Unit,
    onPlay: (LibraryItem) -> Unit,
    onScrape: (LibraryItem) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Moe") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onScrapeMissing, enabled = !state.isLoading && state.snapshot.items.isNotEmpty()) {
                        Icon(Icons.Outlined.Search, contentDescription = "匹配")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == MoeTab.Library,
                    onClick = { onTab(MoeTab.Library) },
                    icon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) },
                    label = { Text("影视库") },
                )
                NavigationBarItem(
                    selected = state.tab == MoeTab.Cloud,
                    onClick = { onTab(MoeTab.Cloud) },
                    icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                    label = { Text("光鸭") },
                )
                NavigationBarItem(
                    selected = state.tab == MoeTab.Settings,
                    onClick = { onTab(MoeTab.Settings) },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                MoeTab.Library -> LibraryScreen(
                    items = state.snapshot.items,
                    isLoading = state.isLoading,
                    onPlay = onPlay,
                    onScrape = onScrape,
                    onImport = { onTab(MoeTab.Cloud) },
                )
                MoeTab.Cloud -> CloudScreen(
                    state = state,
                    onPhone = onPhone,
                    onCode = onCode,
                    onSendSms = onSendSms,
                    onLogin = onLogin,
                    onLogout = onLogout,
                    onImport = onImport,
                )
                MoeTab.Settings -> SettingsScreen(state)
            }
            if (state.isLoading) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
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
}

@Composable
private fun LibraryScreen(
    items: List<LibraryItem>,
    isLoading: Boolean,
    onPlay: (LibraryItem) -> Unit,
    onScrape: (LibraryItem) -> Unit,
    onImport: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            title = "影视库为空",
            action = "导入",
            onAction = onImport,
            enabled = !isLoading,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.resource.id }) { item ->
            LibraryItemCard(
                item = item,
                isLoading = isLoading,
                onPlay = { onPlay(item) },
                onScrape = { onScrape(item) },
            )
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onScrape: () -> Unit,
) {
    val metadata = item.metadata
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(metadata)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = metadata?.title ?: item.resource.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildMetaLine(metadata, item.resource.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata?.genres?.isNotEmpty() == true) {
                    Text(
                        text = metadata.genres.take(4).joinToString(" / "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProgressLine(item.progress)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlay, enabled = !isLoading) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("播放")
                    }
                    OutlinedButton(onClick = onScrape, enabled = !isLoading) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("匹配")
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(metadata: MovieMetadata?) {
    val url = metadata?.posterUrl ?: metadata?.coverUrl
    Surface(
        modifier = Modifier.width(92.dp).aspectRatio(2f / 3f),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (url.isNullOrBlank()) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = metadata?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProgressLine(progress: WatchProgress?) {
    val fraction = progress?.fraction ?: 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Text(
            text = if (progress == null || progress.positionMs <= 0) "未播放" else formatProgress(progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CloudScreen(
    state: MoeUiState,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onImport: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("光鸭网盘", style = MaterialTheme.typography.titleLarge)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onSendSms, enabled = !state.isLoading) {
                                Icon(Icons.Outlined.Login, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("发送验证码")
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
                        ) {
                            Text("登录")
                        }
                        state.captchaUrl?.let { url ->
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = auth.phone ?: "已登录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onImport, enabled = !state.isLoading) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("导入视频")
                            }
                            OutlinedButton(onClick = onLogout, enabled = !state.isLoading) {
                                Icon(Icons.Outlined.Logout, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("退出")
                            }
                        }
                    }
                }
            }
        }
        item {
            MetricCard(
                title = "已导入",
                value = "${state.snapshot.items.size}",
                support = "本地播放记录 ${state.snapshot.items.count { it.progress != null }}",
            )
        }
    }
}

@Composable
private fun SettingsScreen(state: MoeUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MetricCard(
                title = "刮削优先级",
                value = "JavDB > JavBus",
                support = "已匹配 ${state.snapshot.items.count { it.metadata != null }} / ${state.snapshot.items.size}",
            )
        }
        item {
            MetricCard(
                title = "Release 签名",
                value = "固定",
                support = "app/signing/moe-release.p12",
            )
        }
        item {
            MetricCard(
                title = "播放进度",
                value = "${state.snapshot.items.count { it.progress?.positionMs ?: 0L > 0L }}",
                support = "SQLite 本地保存",
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, support: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(support, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, action: String, onAction: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAction, enabled = enabled) {
                Text(action)
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
    val player = remember(state.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(state.url))
            prepare()
            if (state.startPositionMs > 0L) seekTo(state.startPositionMs)
            playWhenReady = true
        }
    }

    fun duration(): Long {
        val value = player.duration
        return if (value == C.TIME_UNSET || value < 0L) 0L else value
    }

    BackHandler {
        onClose(player.currentPosition, duration())
    }

    LaunchedEffect(player) {
        while (true) {
            delay(2_000L)
            onProgress(player.currentPosition, duration())
        }
    }

    DisposableEffect(player) {
        onDispose {
            onClose(player.currentPosition, duration())
            player.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { this.player = player } },
            update = { it.player = player },
        )
        IconButton(
            onClick = { onClose(player.currentPosition, duration()) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
        }
    }
}

private fun buildMetaLine(metadata: MovieMetadata?, size: Long): String {
    if (metadata == null) return formatSize(size)
    return listOfNotNull(
        metadata.contentId,
        metadata.releaseDate,
        metadata.runtimeMinutes.takeIf { it > 0 }?.let { "${it}分钟" },
        formatSize(size),
    ).joinToString(" · ")
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
