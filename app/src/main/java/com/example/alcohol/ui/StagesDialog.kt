package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
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
                    "Эти этапы получает каждая новая партия. Уже сваренные партии остаются как есть.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (steps.isEmpty()) Text("Этапов нет: новые партии будут без этапов")
                steps.forEachIndexed { index, step ->
                    Column {
                        Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleSmall)
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
            text = "Этап «${step.title}» уберётся из процесса. Уже сваренные партии не изменятся.",
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
                                    warehouse.addStageTemplate(drinkType, title, days)
                                } else {
                                    warehouse.updateStageTemplate(existing.id, title, days)
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
