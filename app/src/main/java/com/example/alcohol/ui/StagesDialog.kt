package com.example.alcohol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.StageTemplate
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

/**
 * Общий процесс для пива или сидра: этапы, которые получает каждая новая партия.
 * Этапы можно добавлять, менять, удалять и двигать. Уже сваренные партии не меняются.
 */
@Composable
fun StagesDialog(warehouse: Warehouse, drinkType: DrinkType, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val steps by warehouse.stageTemplates(drinkType).collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StageTemplate?>(null) }
    var deleting by remember { mutableStateOf<StageTemplate?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (drinkType == DrinkType.CIDER) "Этапы сидра" else "Этапы пива") },
        text = {
            // Список читается здесь, внутри содержимого окна, чтобы оно обновлялось при изменениях.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Изменения применяются и к уже сваренным партиям: новый этап появится в них, названия и сроки обновятся. Удалённый этап уйдёт из партий, где он ещё не выполнен. В партии любой этап можно отменить или отметить выполненным.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (steps.isEmpty()) Text("Этапов нет: новые партии будут без этапов")
                steps.forEachIndexed { index, step ->
                    Column {
                        Text("${index + 1}. ${stageLabel(step.icon, step.title)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (index == 0) {
                                "через ${step.daysAfterPrevious} дн. после постановки"
                            } else {
                                "через ${step.daysAfterPrevious} дн. после предыдущего"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { scope.launch { warehouse.moveStageTemplate(step.id, up = true) } },
                                enabled = index > 0,
                            ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Выше") }
                            IconButton(
                                onClick = { scope.launch { warehouse.moveStageTemplate(step.id, up = false) } },
                                enabled = index < steps.lastIndex,
                            ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ниже") }
                            IconButton(onClick = { editing = step }) {
                                Icon(Icons.Default.Edit, contentDescription = "Изменить этап")
                            }
                            IconButton(onClick = { deleting = step }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить этап")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить этап")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } },
    )

    if (adding) {
        StageEditDialog(warehouse = warehouse, drinkType = drinkType, existing = null, onDismiss = { adding = false })
    }
    editing?.let { step ->
        StageEditDialog(warehouse = warehouse, drinkType = drinkType, existing = step, onDismiss = { editing = null })
    }
    deleting?.let { step ->
        ConfirmDeleteDialog(
            text = "Этап «${stageLabel(step.icon, step.title)}» уберётся из процесса и из партий, где он ещё не выполнен. Выполненные этапы в партиях останутся.",
            onConfirm = { scope.launch { warehouse.deleteStageTemplate(step.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun StageEditDialog(
    warehouse: Warehouse,
    drinkType: DrinkType,
    existing: StageTemplate?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var daysText by remember { mutableStateOf(existing?.daysAfterPrevious?.toString() ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text(if (existing == null) "Новый этап" else "Изменить этап") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название, например «Розлив»") },
                    singleLine = true,
                )
                Text("Значок", style = MaterialTheme.typography.titleSmall)
                // Таблица значков: нажми на нужный, повторное нажатие снимает выбор.
                stageEmojis.chunked(EMOJI_COLUMNS).forEach { rowEmojis ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowEmojis.forEach { emoji ->
                            val selected = emoji == icon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { icon = if (selected) "" else emoji },
                                contentAlignment = Alignment.Center,
                            ) { Text(emoji, fontSize = 26.sp) }
                        }
                    }
                }
                Text(
                    "Выбрано: ${stageLabel(icon, title.ifBlank { "название этапа" })}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Через сколько дней после предыдущего") },
                    singleLine = true,
                )
                Text(
                    "У первого этапа срок считается от даты постановки партии.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val days = daysText.trim().toIntOrNull()
                when {
                    title.isBlank() -> error = "Укажи название"
                    days == null || days < 0 -> error = "Укажи срок в днях, целое число"
                    else -> {
                        error = null
                        scope.launch {
                            try {
                                if (existing == null) {
                                    warehouse.addStageTemplate(drinkType, title, days, icon)
                                } else {
                                    warehouse.updateStageTemplate(existing.id, title, days, icon)
                                }
                                onDismiss()
                            } catch (e: IllegalArgumentException) {
                                error = e.message ?: "Не удалось сохранить"
                            }
                        }
                    }
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private const val EMOJI_COLUMNS = 6

/** Таблица значков для этапов: брожение, карбонизация, розлив, перелив, дозревание и другое. */
private val stageEmojis = listOf(
    "🌾", "🔥", "❄️", "🧊", "🌡️", "🧪",
    "🫧", "🍾", "🍺", "🍻", "🍷", "🥂",
    "🍎", "🍏", "🍇", "🍯", "🍋", "🌿",
    "🔄", "⏳", "⏰", "📅", "🔍", "✅",
    "🧹", "💧", "🧂", "🪣", "🔔", "📦",
    "🥃", "🛢️", "⚗️", "🧫", "⭐", "❤️",
)
