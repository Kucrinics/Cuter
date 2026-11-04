package com.cuter.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuter.data.CanvasRatio
import com.cuter.data.ProjectSummary
import com.cuter.media.FormatUtil
import com.cuter.ui.components.ToolChip
import com.cuter.ui.theme.Accent
import com.cuter.ui.theme.Ink
import com.cuter.ui.theme.Panel
import com.cuter.ui.theme.PanelHigh
import com.cuter.ui.theme.Pink
import com.cuter.ui.theme.Stroke
import com.cuter.ui.theme.TextPrimary
import com.cuter.ui.theme.TextSecondary

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenProject: (String) -> Unit) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectSummary?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectSummary?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.dismissMessage()
        }
    }

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Brush.linearGradient(listOf(Accent, Pink))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Movie, null, tint = Ink, modifier = Modifier.size(21.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Cuter",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = (-0.5).sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Мобильный видеоредактор: таймлайн, текст, эффекты, музыка и экспорт MP4.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Новый проект", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Проекты", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Text("${projects.size}", fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyProjects { showCreate = true }
                }
            } else {
                items(projects, key = { it.id }) { summary ->
                    ProjectCard(
                        summary = summary,
                        onOpen = { onOpenProject(summary.id) },
                        onRename = { pendingRename = summary },
                        onDuplicate = { vm.duplicate(summary.id) },
                        onDelete = { pendingDelete = summary },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, ratio ->
                showCreate = false
                vm.create(name, ratio) { id -> onOpenProject(id) }
            },
        )
    }

    pendingRename?.let { summary ->
        RenameProjectDialog(
            initial = summary.name,
            onDismiss = { pendingRename = null },
            onConfirm = { name ->
                vm.rename(summary.id, name)
                pendingRename = null
            },
        )
    }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить проект?") },
            text = { Text("«${summary.name}» и все его медиафайлы будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(summary.id)
                    pendingDelete = null
                }) { Text("Удалить", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
            containerColor = Panel,
        )
    }
}

@Composable
private fun EmptyProjects(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onCreate)
            .padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Accent.copy(alpha = 0.8f),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("Здесь появятся твои ролики", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Создай проект и добавь видео, фото или музыку",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ProjectCard(
    summary: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val bitmap = remember(summary.thumbPath) {
        summary.thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .background(PanelHigh),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Rounded.Movie,
                    null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .align(Alignment.BottomCenter),
            )
            Text(
                text = FormatUtil.formatMs(summary.durationMs),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Text(
                text = summary.ratio.label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.MoreVert, "Меню", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Дублировать") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Pink) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = summary.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${summary.clipCount} клипов · ${FormatUtil.formatMs(summary.durationMs)}",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, CanvasRatio) -> Unit) {
    var name by remember { mutableStateOf("Мой ролик") }
    var ratio by remember { mutableStateOf(CanvasRatio.RATIO_9_16) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Новый проект") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(50) },
                    label = { Text("Название") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Формат кадра", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasRatio.entries.forEach { item ->
                        ToolChip(item.label, item == ratio) { ratio = item }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, ratio) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
            ) { Text("Создать", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun RenameProjectDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Переименовать") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) { Text("Сохранить", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
