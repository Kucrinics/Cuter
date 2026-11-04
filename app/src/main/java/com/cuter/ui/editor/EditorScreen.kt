package com.cuter.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.cuter.CuterApp
import com.cuter.data.CanvasRatio
import com.cuter.data.Clip
import com.cuter.data.Project
import com.cuter.data.TextSpec
import com.cuter.media.FormatUtil
import com.cuter.media.GalleryPublisher
import com.cuter.media.PreviewController
import com.cuter.ui.theme.Accent
import com.cuter.ui.theme.Ink
import com.cuter.ui.theme.Panel
import com.cuter.ui.theme.PanelHigh
import com.cuter.ui.theme.TextPrimary
import com.cuter.ui.theme.TextSecondary

private const val MAX_IMPORT = 30

private data class TextEditTarget(val clipId: String?, val initial: String)

/** Owns the editor ViewModel for exactly one visit to the editor. */
private class EditorViewModelOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@UnstableApi
@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CuterApp

    val owner = remember(projectId) { EditorViewModelOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    val factory = remember(projectId, app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditorViewModel(app, projectId) as T
        }
    }
    val vm: EditorViewModel = viewModel(viewModelStoreOwner = owner, key = "editor", factory = factory)

    val project by vm.project.collectAsStateWithLifecycle()
    val selectedClipId by vm.selectedClipId.collectAsStateWithLifecycle()
    val playheadMs by vm.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val exportState by vm.exportState.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val loaded = project
    val selectedClip = loaded?.clip(selectedClipId)

    val controller = remember { PreviewController(context) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val snackbar = remember { SnackbarHostState() }
    var panel by remember { mutableStateOf(ToolPanel.NONE) }
    var showRename by remember { mutableStateOf(false) }
    var textEdit by remember { mutableStateOf<TextEditTarget?>(null) }

    val visualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMPORT)
    ) { uris ->
        if (uris.isNotEmpty()) vm.addMedia(uris)
    }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.addMedia(uris)
    }
    val stickerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.addImageSticker(uri)
    }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.export()
        } else {
            vm.message.value = "Без доступа к памяти видео не сохранить"
        }
    }

    val requestExport: () -> Unit = {
        if (loaded == null) {
            vm.message.value = "Проект ещё загружается"
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.export()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) controller.play() else controller.pause()
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.dismissMessage()
    }

    BackHandler {
        if (panel != ToolPanel.NONE) panel = ToolPanel.NONE else onBack()
    }

    val togglePlay: () -> Unit = {
        if (vm.isPlaying.value) {
            vm.isPlaying.value = false
        } else {
            val duration = loaded?.durationMs ?: 0
            if (duration > 0 && vm.playheadMs.value >= duration - 60) {
                vm.seekTo(0)
                controller.seekTo(0)
            }
            vm.isPlaying.value = true
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            EditorTopBar(
                project = loaded,
                canUndo = canUndo,
                canRedo = canRedo,
                onBack = onBack,
                onRename = { showRename = true },
                onUndo = { vm.undo() },
                onRedo = { vm.redo() },
                onRatio = { vm.setRatio(it) },
                onExport = requestExport,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                if (loaded == null) {
                    CircularProgressIndicator(
                        color = Accent,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    PreviewPane(
                        modifier = Modifier.fillMaxSize(),
                        project = loaded,
                        controller = controller,
                        selectedClip = selectedClip,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        onTogglePlay = togglePlay,
                        onTick = { vm.tick(it) },
                        onEnded = { vm.onPlaybackEnded() },
                        onError = { vm.message.value = it },
                        onMoveOverlay = { dx, dy ->
                            vm.editSelectedContinuous { clip ->
                                val text = clip.text
                                val sticker = clip.sticker
                                when {
                                    text != null -> clip.copy(
                                        text = text.copy(
                                            x = (text.x + dx).coerceIn(0.02f, 0.98f),
                                            y = (text.y + dy).coerceIn(0.02f, 0.98f),
                                        )
                                    )

                                    sticker != null -> clip.copy(
                                        sticker = sticker.copy(
                                            x = (sticker.x + dx).coerceIn(0.02f, 0.98f),
                                            y = (sticker.y + dy).coerceIn(0.02f, 0.98f),
                                        )
                                    )

                                    else -> clip
                                }
                            }
                        },
                        onPushUndo = { vm.pushUndo() },
                        onOpenMedia = { panel = ToolPanel.MEDIA },
                    )

                    val importing = importState as? ImportState.Running
                    if (importing != null) {
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(PanelHigh)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Импорт ${importing.done}/${importing.total}",
                                color = TextPrimary,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            if (loaded != null) {
                QuickActionsRow(
                    hasSelection = selectedClip != null,
                    onSplit = { vm.splitSelected() },
                    onDuplicate = { vm.duplicateSelected() },
                    onDelete = { vm.deleteSelected() },
                )

                TimelineEditor(
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    project = loaded,
                    selectedClipId = selectedClipId,
                    playheadMs = playheadMs,
                    isPlaying = isPlaying,
                    onSelect = { vm.select(it) },
                    onSeek = { ms ->
                        vm.seekTo(ms)
                        controller.seekTo(ms)
                    },
                    onScrubStart = { vm.isPlaying.value = false },
                    onMove = vm::moveClip,
                    onTrimStart = vm::trimClipStart,
                    onTrimEnd = vm::trimClipEnd,
                    onPushUndo = { vm.pushUndo() },
                    onToggleTrackMute = { trackId ->
                        val track = loaded.tracks.firstOrNull { it.id == trackId }
                        if (track != null) vm.setTrackMuted(trackId, !track.muted)
                    },
                )

                if (panel != ToolPanel.NONE) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .background(Panel)
                            .padding(horizontal = 12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                panel.label.uppercase(),
                                color = TextSecondary,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable { panel = ToolPanel.NONE }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Свернуть панель",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("Свернуть", color = TextSecondary, fontSize = 10.sp)
                            }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            when (panel) {
                                ToolPanel.MEDIA -> MediaPanel(
                                    project = loaded,
                                    onAddVisual = {
                                        visualPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                            )
                                        )
                                    },
                                    onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
                                    onPickAsset = { vm.addExistingAsset(it) },
                                )

                                ToolPanel.TEXT -> TextPanel(
                                    selected = selectedClip,
                                    onAddText = {
                                        vm.addText("Новый текст")
                                        textEdit = TextEditTarget(vm.selectedClip?.id, "Новый текст")
                                    },
                                    onEditText = {
                                        val spec = selectedClip?.text
                                        if (spec != null) {
                                            textEdit = TextEditTarget(selectedClip.id, spec.text)
                                        }
                                    },
                                    onUpdate = { spec ->
                                        vm.editSelectedContinuous { it.copy(text = spec) }
                                    },
                                )

                                ToolPanel.STICKER -> StickerPanel(
                                    selected = selectedClip,
                                    onAddSticker = { vm.addSticker(it) },
                                    onPickImage = {
                                        stickerPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    onUpdate = { spec ->
                                        vm.editSelectedContinuous { it.copy(sticker = spec) }
                                    },
                                )

                                ToolPanel.EFFECTS -> EffectsPanel(
                                    selected = selectedClip,
                                    onFilter = vm::setFilter,
                                    onSpeed = vm::setSpeed,
                                    onTransition = vm::setTransition,
                                    onVolume = { value ->
                                        vm.editSelectedContinuous { it.copy(volume = value.coerceIn(0f, 2f)) }
                                    },
                                    onFadeIn = { ms ->
                                        vm.editSelectedContinuous { it.copy(fadeInMs = ms) }
                                    },
                                    onFadeOut = { ms ->
                                        vm.editSelectedContinuous { it.copy(fadeOutMs = ms) }
                                    },
                                    onMute = vm::setMuted,
                                )

                                ToolPanel.AUDIO -> AudioPanel(
                                    project = loaded,
                                    selected = selectedClip,
                                    onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
                                    onPickAsset = { vm.addExistingAsset(it) },
                                    onVolume = { value ->
                                        vm.editSelectedContinuous { it.copy(volume = value.coerceIn(0f, 2f)) }
                                    },
                                    onFadeIn = { ms ->
                                        vm.editSelectedContinuous { it.copy(fadeInMs = ms) }
                                    },
                                    onFadeOut = { ms ->
                                        vm.editSelectedContinuous { it.copy(fadeOutMs = ms) }
                                    },
                                    onMute = vm::setMuted,
                                )

                                ToolPanel.NONE -> Unit
                            }
                        }
                    }
                }
            }

            ToolDock(panel = panel, onSelect = { tool ->
                panel = if (panel == tool) ToolPanel.NONE else tool
            })
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 76.dp),
        )
    }

    if (showRename && loaded != null) {
        RenameDialog(
            initial = loaded.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                vm.renameProject(name)
                showRename = false
            },
        )
    }

    textEdit?.let { target ->
        TextContentDialog(
            initial = target.initial,
            onDismiss = { textEdit = null },
            onConfirm = { value ->
                val clipId = target.clipId
                if (clipId != null) {
                    vm.select(clipId)
                    vm.updateSelected { clip ->
                        clip.copy(text = (clip.text ?: TextSpec()).copy(text = value))
                    }
                }
                textEdit = null
            },
        )
    }

    ExportDialog(
        state = exportState,
        onDismiss = { vm.resetExport() },
        onCancel = { vm.cancelExport() },
        onRetry = {
            vm.resetExport()
            requestExport()
        },
        onShare = { uri ->
            val done = exportState
            if (done is ExportState.Done) {
                GalleryPublisher.shareFile(context, done.cachedFile)
            } else {
                GalleryPublisher.share(context, uri)
            }
        },
    )
}

@Composable
private fun EditorTopBar(
    project: Project?,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRatio: (CanvasRatio) -> Unit,
    onExport: () -> Unit,
) {
    var ratioMenu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(Icons.Rounded.ArrowBack, "Назад", onClick = onBack)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .clickable(onClick = onRename),
        ) {
            Text(
                text = project?.name ?: "Загрузка…",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project?.let { "${it.ratio.label} · ${FormatUtil.formatMs(it.durationMs)}" } ?: " ",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        IconTap(Icons.Rounded.Undo, "Отменить", enabled = canUndo, onClick = onUndo)
        IconTap(Icons.Rounded.Redo, "Повторить", enabled = canRedo, onClick = onRedo)
        Box {
            IconTap(Icons.Rounded.AspectRatio, "Формат кадра", onClick = { ratioMenu = true })
            DropdownMenu(expanded = ratioMenu, onDismissRequest = { ratioMenu = false }) {
                CanvasRatio.entries.forEach { ratio ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${ratio.label}${if (project?.ratio == ratio) "  ✓" else ""}",
                                fontSize = 13.sp,
                            )
                        },
                        onClick = {
                            ratioMenu = false
                            onRatio(ratio)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Accent)
                .clickable(onClick = onExport)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.FileDownload,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text("Экспорт", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IconTap(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.32f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun QuickActionsRow(
    hasSelection: Boolean,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction(
            icon = Icons.Rounded.ContentCut,
            label = "Разделить",
            enabled = hasSelection,
            onClick = onSplit,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Rounded.ContentCopy,
            label = "Дублировать",
            enabled = hasSelection,
            onClick = onDuplicate,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Rounded.Delete,
            label = "Удалить",
            enabled = hasSelection,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = TextSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ToolDock(panel: ToolPanel, onSelect: (ToolPanel) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel)) {
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            ToolPanel.entries.filter { it != ToolPanel.NONE }.forEach { tool ->
                val selected = panel == tool
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tool) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        tool.icon,
                        contentDescription = tool.label,
                        tint = if (selected) Accent else TextSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tool.label,
                        color = if (selected) Accent else TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Suppress("unused")
private fun Clip.displayName(): String = title.ifBlank { "Клип" }
