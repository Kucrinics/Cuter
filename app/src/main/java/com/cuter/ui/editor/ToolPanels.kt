package com.cuter.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuter.data.Clip
import com.cuter.data.FilterType
import com.cuter.data.FontPreset
import com.cuter.data.MediaAsset
import com.cuter.data.MediaType
import com.cuter.data.Project
import com.cuter.data.StickerSpec
import com.cuter.data.TextAlignPreset
import com.cuter.data.TextAnimation
import com.cuter.data.TextSpec
import com.cuter.data.TransitionType
import com.cuter.media.FormatUtil
import com.cuter.ui.components.ColorSwatch
import com.cuter.ui.components.Hint
import com.cuter.ui.components.IconTextButton
import com.cuter.ui.components.SectionLabel
import com.cuter.ui.components.ToolChip
import com.cuter.ui.theme.Accent
import com.cuter.ui.theme.Amber
import com.cuter.ui.theme.Ink
import com.cuter.ui.theme.Panel
import com.cuter.ui.theme.PanelHigh
import com.cuter.ui.theme.Pink
import com.cuter.ui.theme.Stroke
import com.cuter.ui.theme.TextPrimary
import com.cuter.ui.theme.TextSecondary
import java.io.File

enum class ToolPanel(val label: String, val icon: ImageVector) {
    NONE("", Icons.Rounded.Add),
    MEDIA("Медиа", Icons.Rounded.Movie),
    TEXT("Текст", Icons.Rounded.TextFields),
    STICKER("Стикер", Icons.Rounded.EmojiEmotions),
    EFFECTS("Эффекты", Icons.Rounded.AutoAwesome),
    AUDIO("Звук", Icons.Rounded.MusicNote),
}

private val TextColors = listOf(
    0xFFFFFFFF, 0xFF0B0B10, 0xFF2AE0B0, 0xFFFF4D8D,
    0xFFFFC857, 0xFF6FA8FF, 0xFF9B7BFF, 0xFFFF7A45,
)

private val Emojis = listOf(
    "🔥", "❤️", "😂", "😎", "🎉", "✨", "💥", "👍",
    "⭐", "💯", "😍", "🥰", "🤯", "👀", "🎵", "🎬",
    "🚀", "🌈", "💫", "🦋", "🍕", "☕", "🌸", "⚡",
)

@Composable
fun MediaPanel(
    project: Project,
    onAddVisual: () -> Unit,
    onAddAudio: () -> Unit,
    onPickAsset: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTextButton(Icons.Rounded.PhotoLibrary, "Видео и фото", tint = Accent, onClick = onAddVisual)
            IconTextButton(Icons.Rounded.MusicNote, "Аудио", onClick = onAddAudio)
        }
        Spacer(Modifier.height(10.dp))
        if (project.assets.isEmpty()) {
            Hint("Импортируйте видео, фото или музыку с устройства. Файлы копируются в проект.")
        } else {
            SectionLabel("Файлы проекта")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(project.assets, key = { it.id }) { asset ->
                    AssetCard(asset = asset, projectId = project.id, onClick = { onPickAsset(asset.id) })
                }
            }
        }
    }
}

@Composable
private fun AssetCard(asset: MediaAsset, projectId: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbFile = asset.thumbName?.let { File(context.filesDir, "projects/$projectId/thumbs/$it") }
    val bitmap = androidx.compose.runtime.remember(thumbFile?.absolutePath) {
        thumbFile?.takeIf { it.exists() }?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }
    Column(
        Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PanelHigh)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(58.dp).background(Ink)) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = if (asset.type == MediaType.AUDIO) Icons.Rounded.GraphicEq else Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = if (asset.type == MediaType.AUDIO) Amber else TextSecondary,
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                )
            }
            Text(
                text = FormatUtil.formatMs(asset.durationMs),
                color = TextPrimary,
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        Text(
            text = asset.name,
            color = TextPrimary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun TextPanel(
    selected: Clip?,
    onAddText: () -> Unit,
    onEditText: () -> Unit,
    onUpdate: (TextSpec) -> Unit,
) {
    val spec = selected?.text
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTextButton(Icons.Rounded.Add, "Добавить текст", tint = Accent, onClick = onAddText)
            if (spec != null) {
                IconTextButton(Icons.Rounded.TextFields, "Изменить", onClick = onEditText)
            }
        }
        if (spec == null) {
            Hint("Выберите текстовый клип на таймлайне, чтобы настроить шрифт, цвет и анимацию.")
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel("Размер и поворот")
        LabeledSlider("Размер", spec.size, 16f..160f, valueText = "${spec.size.toInt()}") {
            onUpdate(spec.copy(size = it))
        }
        LabeledSlider("Масштаб", spec.scale, 0.4f..2.5f, valueText = String.format("%.2f", spec.scale)) {
            onUpdate(spec.copy(scale = it))
        }
        LabeledSlider("Поворот", spec.rotation, -180f..180f, valueText = "${spec.rotation.toInt()}°") {
            onUpdate(spec.copy(rotation = it))
        }
        Spacer(Modifier.height(6.dp))
        SectionLabel("Цвет")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextColors.forEach { color ->
                ColorSwatch(color, spec.color == color) { onUpdate(spec.copy(color = color)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionLabel("Шрифт")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FontPreset.entries.forEach { font ->
                ToolChip(font.label, spec.font == font) { onUpdate(spec.copy(font = font)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolChip("Жирный", spec.bold) { onUpdate(spec.copy(bold = !spec.bold)) }
            ToolChip("Курсив", spec.italic) { onUpdate(spec.copy(italic = !spec.italic)) }
            ToolChip("Тень", spec.shadow) { onUpdate(spec.copy(shadow = !spec.shadow)) }
            ToolChip("Плашка", spec.background != 0L) {
                onUpdate(spec.copy(background = if (spec.background != 0L) 0L else 0x99000000))
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel("Выравнивание")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextAlignPreset.entries.forEach { align ->
                ToolChip(align.label, spec.align == align) { onUpdate(spec.copy(align = align)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel("Анимация")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextAnimation.entries.forEach { animation ->
                ToolChip(animation.label, spec.animation == animation) { onUpdate(spec.copy(animation = animation)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun StickerPanel(
    selected: Clip?,
    onAddSticker: (String) -> Unit,
    onPickImage: () -> Unit,
    onUpdate: (StickerSpec) -> Unit,
) {
    val spec = selected?.sticker
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTextButton(Icons.Rounded.PhotoLibrary, "Из галереи", onClick = onPickImage)
            if (spec != null) {
                IconTextButton(Icons.Rounded.EmojiEmotions, "Эмодзи", onClick = onPickImage)
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel("Стикеры")
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Emojis) { emoji ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelHigh)
                        .clickable { onAddSticker(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 20.sp)
                }
            }
        }
        if (spec == null) {
            Hint("Выберите стикер на таймлайне, чтобы менять размер и поворот.")
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        LabeledSlider("Масштаб", spec.scale, 0.3f..3f, valueText = String.format("%.2f", spec.scale)) {
            onUpdate(spec.copy(scale = it))
        }
        LabeledSlider("Поворот", spec.rotation, -180f..180f, valueText = "${spec.rotation.toInt()}°") {
            onUpdate(spec.copy(rotation = it))
        }
    }
}

@Composable
fun EffectsPanel(
    selected: Clip?,
    onFilter: (FilterType) -> Unit,
    onSpeed: (Float) -> Unit,
    onTransition: (TransitionType) -> Unit,
    onVolume: (Float) -> Unit,
    onFadeIn: (Long) -> Unit,
    onFadeOut: (Long) -> Unit,
    onMute: (Boolean) -> Unit,
) {
    val clip = selected
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (clip == null || clip.isOverlay) {
            Hint("Выберите видео или аудиоклип, чтобы применить фильтр, скорость и переход.")
            return@Column
        }
        if (clip.type != MediaType.AUDIO) {
            SectionLabel("Фильтры")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterType.entries.forEach { filter ->
                    ToolChip(filter.label, clip.filter == filter) { onFilter(filter) }
                }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Скорость")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { speed ->
                    ToolChip("${speed}x", kotlin.math.abs(clip.speed - speed) < 0.01f) { onSpeed(speed) }
                }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Переход в конце клипа")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransitionType.entries.forEach { transition ->
                    ToolChip(transition.label, clip.transition == transition) { onTransition(transition) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        SectionLabel("Звук")
        LabeledSlider("Громкость", clip.volume, 0f..2f, valueText = "${(clip.volume * 100).toInt()}%") { onVolume(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip(if (clip.muted) "Включить звук" else "Заглушить", clip.muted) { onMute(!clip.muted) }
        }
        LabeledSlider("Появление", clip.fadeInMs.toFloat(), 0f..2000f, valueText = "${clip.fadeInMs} мс") { onFadeIn(it.toLong()) }
        LabeledSlider("Затухание", clip.fadeOutMs.toFloat(), 0f..2000f, valueText = "${clip.fadeOutMs} мс") { onFadeOut(it.toLong()) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun AudioPanel(
    project: Project,
    selected: Clip?,
    onAddAudio: () -> Unit,
    onPickAsset: (String) -> Unit,
    onVolume: (Float) -> Unit,
    onFadeIn: (Long) -> Unit,
    onFadeOut: (Long) -> Unit,
    onMute: (Boolean) -> Unit,
) {
    val clip = selected?.takeIf { it.type == MediaType.AUDIO }
    val audioAssets = project.assets.filter { it.type == MediaType.AUDIO }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTextButton(Icons.Rounded.MusicNote, "Добавить музыку", tint = Amber, onClick = onAddAudio)
        }
        if (audioAssets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(audioAssets, key = { it.id }) { asset ->
                    Box(
                        Modifier
                            .width(130.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PanelHigh)
                            .clickable { onPickAsset(asset.id) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    asset.name,
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    FormatUtil.formatMs(asset.durationMs),
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (clip == null) {
            Hint("Выберите аудиоклип на таймлайне или добавьте музыку из файлов.")
            return@Column
        }
        LabeledSlider("Громкость", clip.volume, 0f..2f, valueText = "${(clip.volume * 100).toInt()}%") { onVolume(it) }
        LabeledSlider("Появление", clip.fadeInMs.toFloat(), 0f..3000f, valueText = "${clip.fadeInMs} мс") { onFadeIn(it.toLong()) }
        LabeledSlider("Затухание", clip.fadeOutMs.toFloat(), 0f..3000f, valueText = "${clip.fadeOutMs} мс") { onFadeOut(it.toLong()) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip("Заглушить", clip.muted) { onMute(!clip.muted) }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(62.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        Text(
            valueText,
            color = TextPrimary,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}
