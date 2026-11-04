package com.cuter.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuter.ui.theme.Accent
import com.cuter.ui.theme.Ink
import com.cuter.ui.theme.Panel
import com.cuter.ui.theme.Pink
import com.cuter.ui.theme.TextPrimary
import com.cuter.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun TextContentDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Текст") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                label = { Text("Содержимое") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.ifBlank { "Текст" }) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
            ) { Text("Готово", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Название проекта") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
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

@Composable
fun ExportDialog(
    state: ExportState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onShare: (Uri) -> Unit,
) {
    when (state) {
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            containerColor = Panel,
            title = { Text("Экспорт видео") },
            text = {
                Column {
                    Text(
                        "Собираем ролик: ${(state.progress * 100).roundToInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = com.cuter.ui.theme.Stroke,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Не закрывайте приложение. На слабых устройствах экспорт 1080p может занять несколько минут.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onCancel) { Text("Отменить", color = Pink) }
            },
        )

        is ExportState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Panel,
            icon = { Icon(Icons.Rounded.CheckCircle, null, tint = Accent, modifier = Modifier.size(34.dp)) },
            title = { Text("Готово!") },
            text = {
                Column {
                    Text("Видео сохранено в Галерею → Movies/Cuter.", color = TextPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(state.fileName, color = TextSecondary, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onShare(state.uri) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                ) { Text("Поделиться", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        )

        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Panel,
            icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = Pink, modifier = Modifier.size(30.dp)) },
            title = { Text("Экспорт не удался") },
            text = { Text(state.message, color = TextSecondary, fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                ) { Text("Повторить") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        )

        ExportState.Idle -> Unit
    }
}
