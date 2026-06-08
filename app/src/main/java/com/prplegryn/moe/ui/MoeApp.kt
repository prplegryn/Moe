package com.prplegryn.moe.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.CloudFile
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

    val selectedItem = state.snapshot.items.firstOrNull { it.resource.id == state.selectedItemId }
    if (selectedItem != null) {
        DetailScreen(
            item = selectedItem,
            isLoading = state.isLoading,
            onBack = viewModel::closeDetails,
            onPlay = { viewModel.openPlayer(selectedItem) },
        )
        return
    }

    MoeScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onTab = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onPhone = viewModel::updatePhone,
        onCode = viewModel::updateCode,
        onSendSms = viewModel::sendSms,
        onLogin = viewModel::completeLogin,
        onLogout = viewModel::logout,
        onImport = viewModel::importCloudVideos,
        onOpenDetails = viewModel::openDetails,
        onSaveImportPath = viewModel::saveImportPath,
        onRefreshDirectories = viewModel::refreshDirectoryPicker,
        onOpenDirectory = viewModel::openDirectory,
        onSelectDirectoryCrumb = viewModel::selectDirectoryCrumb,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoeScaffold(
    state: MoeUiState,
    snackbarHostState: SnackbarHostState,
    onTab: (MoeTab) -> Unit,
    onRefresh: () -> Unit,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendSms: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onImport: () -> Unit,
    onOpenDetails: (LibraryItem) -> Unit,
    onSaveImportPath: () -> Unit,
    onRefreshDirectories: () -> Unit,
    onOpenDirectory: (CloudFile) -> Unit,
    onSelectDirectoryCrumb: (Int) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Moe", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onImport, enabled = !state.isLoading && state.snapshot.auth != null) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "导入")
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
                MoeTab.Library -> LibraryPosterGrid(
                    items = state.snapshot.items,
                    isLoading = state.isLoading,
                    onOpenDetails = onOpenDetails,
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
                MoeTab.Settings -> SettingsScreen(
                    state = state,
                    onSaveImportPath = onSaveImportPath,
                    onRefreshDirectories = onRefreshDirectories,
                    onOpenDirectory = onOpenDirectory,
                    onSelectDirectoryCrumb = onSelectDirectoryCrumb,
                )
            }
            BusyOverlay(state.isLoading)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPosterGrid(
    items: List<LibraryItem>,
    isLoading: Boolean,
    onOpenDetails: (LibraryItem) -> Unit,
    onImport: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            title = "还没有影片",
            action = "连接光鸭并导入",
            onAction = onImport,
            enabled = !isLoading,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(items)
        }
        items(items, key = { it.resource.id }) { item ->
            PosterTile(
                item = item,
                onClick = { onOpenDetails(item) },
            )
        }
    }
}

@Composable
private fun LibraryHeader(items: List<LibraryItem>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "全部影片",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${items.size} 部 · ${items.count { it.metadata != null }} 部已匹配资料",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PosterTile(item: LibraryItem, onClick: () -> Unit) {
    val metadata = item.metadata
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(7f / 10f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            CroppedLibraryPosterImage(
                url = metadata?.posterUrl ?: metadata?.coverUrl,
                title = displayTitle(item),
                modifier = Modifier.fillMaxSize(),
            )
            if ((item.progress?.positionMs ?: 0L) > 0L) {
                LinearProgressIndicator(
                    progress = item.progress?.fraction ?: 0f,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Text(
            text = displayTitle(item),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artistLine(metadata),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ContentIdBadge(metadata?.contentId ?: item.resource.name.substringBeforeLast('.'))
    }
}

@Composable
private fun CroppedLibraryPosterImage(
    url: String?,
    title: String,
    modifier: Modifier,
) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Movie,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Box(modifier = modifier.clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.CenterEnd) {
        AsyncImage(
            model = rememberMediaImageRequest(url),
            contentDescription = title,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1.48f)
                .align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ContentIdBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Color.Black)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    item: LibraryItem,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    var zoomedPreview by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = zoomedPreview != null) { zoomedPreview = null }
    BackHandler(enabled = zoomedPreview == null, onBack = onBack)
    val metadata = item.metadata
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = displayTitle(item),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    HeroArtwork(item = item, onPlay = onPlay, isLoading = isLoading)
                }
                item {
                    DetailTitle(item)
                }
                item {
                    PreviewStrip(metadata, onPreviewClick = { zoomedPreview = it })
                }
                item {
                    ActressStrip(metadata?.actresses.orEmpty())
                }
                item {
                    DetailInfo(item)
                }
            }
        }
        zoomedPreview?.let { url ->
            ZoomablePreviewOverlay(url = url, onClose = { zoomedPreview = null })
        }
    }
}

@Composable
private fun HeroArtwork(item: LibraryItem, onPlay: () -> Unit, isLoading: Boolean) {
    val metadata = item.metadata
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        PosterImage(
            url = metadata?.posterUrl ?: metadata?.coverUrl,
            title = displayTitle(item),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        FloatingActionButton(
            onClick = { if (!isLoading) onPlay() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = "播放")
        }
    }
}

@Composable
private fun DetailTitle(item: LibraryItem) {
    val metadata = item.metadata
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = displayTitle(item),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoPill(metadata?.contentId ?: item.resource.name.substringBeforeLast('.'))
            metadata?.releaseDate?.let { InfoPill(it) }
            metadata?.runtimeMinutes?.takeIf { it > 0 }?.let { InfoPill("${it}分钟") }
        }
        Text(
            text = artistLine(metadata),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewStrip(metadata: MovieMetadata?, onPreviewClick: (String) -> Unit) {
    val previews = metadata?.screenshots.orEmpty().ifEmpty {
        listOfNotNull(metadata?.coverUrl, metadata?.posterUrl)
    }
    if (previews.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "预览",
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(previews) { url ->
                PreviewThumbnail(url = url, onClick = { onPreviewClick(url) })
            }
        }
    }
}

@Composable
private fun PreviewThumbnail(url: String, onClick: () -> Unit) {
    val request = rememberMediaImageRequest(url)
    val painter = rememberAsyncImagePainter(model = request)
    val state = painter.state
    val previewHeight = 128.dp
    val aspect = when (state) {
        is AsyncImagePainter.State.Success -> {
            val drawable = state.result.drawable
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 16
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 9
            (width.toFloat() / height.toFloat()).coerceIn(0.55f, 2.4f)
        }
        else -> 16f / 9f
    }
    val previewWidth = (previewHeight * aspect).coerceIn(82.dp, 260.dp)
    Box(
        modifier = Modifier
            .height(previewHeight)
            .width(previewWidth)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = "预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ZoomablePreviewOverlay(url: String, onClose: () -> Unit) {
    var scale by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
    ) {
        AsyncImage(
            model = rememberMediaImageRequest(url),
            contentDescription = "预览大图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.7f, 5f)
                        offset += pan
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
        }
    }
}

@Composable
private fun ActressStrip(actresses: List<ActressInfo>) {
    if (actresses.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "演员",
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(actresses) { actress ->
                Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (actress.thumbUrl.isNullOrBlank()) {
                            Text(
                                text = actress.name.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            AsyncImage(
                                model = rememberMediaImageRequest(actress.thumbUrl),
                                contentDescription = actress.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailInfo(item: LibraryItem) {
    val metadata = item.metadata
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        metadata?.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DetailRow("片商", metadata?.maker)
        DetailRow("系列", metadata?.series)
        DetailRow("导演", metadata?.director)
        DetailRow("标签", metadata?.genres?.joinToString(" / "))
        DetailRow("来源", metadata?.sourceName)
        DetailRow("文件", item.resource.name)
        DetailRow("大小", formatSize(item.resource.size))
        item.progress?.let {
            DetailRow("进度", formatProgress(it))
        }
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
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("光鸭网盘", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                        FilledTonalButton(onClick = onSendSms, enabled = !state.isLoading) {
                            Icon(Icons.Outlined.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("发送验证码")
                        }
                        OutlinedTextField(
                            value = state.code,
                            onValueChange = onCode,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Button(onClick = onLogin, enabled = !state.isLoading && state.smsRequest != null) {
                            Text("登录")
                        }
                    } else {
                        Text(
                            text = auth.phone ?: "已登录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onImport, enabled = !state.isLoading) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("导入并匹配")
                            }
                            OutlinedButton(onClick = onLogout, enabled = !state.isLoading) {
                                Icon(Icons.Outlined.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("退出")
                            }
                        }
                    }
                }
            }
        }
        item {
            MetricCard(
                title = "导入路径",
                value = state.snapshot.settings.importPath.ifBlank { "全盘视频" },
                support = "在设置中配置路径，导入时会自动匹配资料",
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MoeUiState,
    onSaveImportPath: () -> Unit,
    onRefreshDirectories: () -> Unit,
    onOpenDirectory: (CloudFile) -> Unit,
    onSelectDirectoryCrumb: (Int) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Text(
                            "导入路径",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = onRefreshDirectories, enabled = !state.isLoading && state.snapshot.auth != null) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新目录")
                        }
                    }
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
        item {
            MetricCard(
                title = "资料匹配",
                value = "导入时自动",
                support = "R18Dev 单一数据源，导入后自动搜刮",
            )
        }
        item {
            MetricCard(
                title = "播放进度",
                value = "${state.snapshot.items.count { it.progress?.positionMs ?: 0L > 0L }}",
                support = "本地保存，进入播放时自动续播",
            )
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
    val pathLabel = state.importPathDraft.ifBlank { "根目录" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "当前：$pathLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
                Text("获取目录结构")
            }
        } else if (picker.folders.isEmpty()) {
            Text(
                text = "当前目录没有子目录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                picker.folders.take(80).forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = canBrowse) { onOpenDirectory(folder) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
        Button(onClick = onSaveImportPath, enabled = !state.isLoading) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("保存当前路径")
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

    fun closeOnce() {
        if (!closed) {
            closed = true
            onClose(player.currentPosition, duration())
        }
    }

    BackHandler { closeOnce() }

    LaunchedEffect(player) {
        while (true) {
            delay(2_000L)
            onProgress(player.currentPosition, duration())
        }
    }

    DisposableEffect(activity, player) {
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            closeOnce()
            activity?.requestedOrientation = previous
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { this.player = player } },
            update = { it.player = player },
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayTitle(state.item),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { closeOnce() }) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
            }
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

@Composable
private fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Movie,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = rememberMediaImageRequest(url),
            contentDescription = title,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

@Composable
private fun rememberMediaImageRequest(url: String?): ImageRequest? {
    val context = LocalContext.current
    return remember(url) {
        if (url.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .addHeader("User-Agent", MEDIA_IMAGE_USER_AGENT)
                .addHeader("Referer", imageReferer(url))
                .build()
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricCard(title: String, value: String, support: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
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

private fun artistLine(metadata: MovieMetadata?): String {
    return metadata?.actresses?.take(3)?.joinToString(" / ") { it.name }
        ?.takeIf { it.isNotBlank() }
        ?: metadata?.maker
        ?: "未知艺术家"
}

private fun displayTitle(item: LibraryItem): String {
    val metadata = item.metadata
    return metadata?.originalTitle?.takeIf { it.isNotBlank() }
        ?: metadata?.title?.takeIf { it.isNotBlank() }
        ?: item.resource.name.substringBeforeLast('.').ifBlank { item.resource.name }
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

private const val MEDIA_IMAGE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private fun imageReferer(url: String): String {
    val lower = url.lowercase()
    return when {
        "javbus" in lower -> "https://www.javbus.com/"
        "javdb" in lower || "jdbstatic" in lower -> "https://javdb.com/"
        "dmm.co.jp" in lower || "dmm.com" in lower -> "https://www.dmm.co.jp/"
        else -> "https://www.guangyapan.com/"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
