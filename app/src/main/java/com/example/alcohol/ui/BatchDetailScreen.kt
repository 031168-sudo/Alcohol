package com.example.alcohol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.BatchStage
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

/** Открытая партия: этапы с плановыми датами, отметка выполненного этапа и прогноз. */
@Composable
fun BatchDetailScreen(batchId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val batch by warehouse.batch(batchId).collectAsState(initial = null)
    val stages by warehouse.stages(batchId).collectAsState(initial = emptyList())
    var completing by remember { mutableStateOf<BatchStage?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
        batch?.let { b ->
            item {
                Text(b.name, style = MaterialTheme.typography.titleLarge)
                Text("Поставлено ${formatDate(b.startedAt)} · ${b.volumeL} л", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Text("Этапы", style = MaterialTheme.typography.titleMedium) }

        items(stages, key = { it.id }) { stage ->
            val done = stage.doneAt
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = done == null) { completing = stage },
            ) {
                Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(stageTitle(stage.type), style = MaterialTheme.typography.titleSmall)
                        if (done != null) {
                            Text("Выполнено ${formatDate(done)}", style = MaterialTheme.typography.bodyMedium)
                            val details = listOfNotNull(
                                stage.temperatureC?.let { "%.1f °C".format(it) },
                                stage.gravity?.let { "%.1f %%".format(it) },
                                stage.notes.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                        } else {
                            val planned = stage.plannedAt?.let { formatDate(it) } ?: "не запланировано"
                            Text("Прогноз: $planned. Нажми, чтобы отметить.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // Выполненный этап можно поправить: дату, температуру, плотность, заметки.
                    if (done != null) {
                        IconButton(onClick = { completing = stage }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить этап")
                        }
                    }
                }
            }
        }
    }

    completing?.let { stage ->
        CompleteStageDialog(
            stage = stage,
            onDismiss = { completing = null },
            onSave = { doneAt, temperature, gravity, notes ->
                scope.launch {
                    warehouse.completeStage(stage.id, doneAt, temperature, gravity, notes)
                    completing = null
                }
            },
        )
    }
}

@Composable
private fun CompleteStageDialog(
    stage: BatchStage,
    onDismiss: () -> Unit,
    onSave: (doneAt: Long, temperatureC: Double?, gravity: Double?, notes: String) -> Unit,
) {
    var dateText by remember { mutableStateOf(formatDate(stage.doneAt ?: System.currentTimeMillis())) }
    var tempText by remember { mutableStateOf(stage.temperatureC?.toString() ?: "") }
    var gravityText by remember { mutableStateOf(stage.gravity?.toString() ?: "") }
    var notes by remember { mutableStateOf(stage.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text(if (stage.doneAt == null) stageTitle(stage.type) else "Изменить: ${stageTitle(stage.type)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Дата (дд.мм.гггг)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    label = { Text("Температура, °C (необязательно)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = gravityText,
                    onValueChange = { gravityText = it },
                    label = { Text("Плотность, % (необязательно)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val date = parseDate(dateText)
                if (date == null) {
                    error = "Дата в формате дд.мм.гггг"
                    return@Button
                }
                onSave(
                    date,
                    tempText.replace(',', '.').toDoubleOrNull(),
                    gravityText.replace(',', '.').toDoubleOrNull(),
                    notes.trim(),
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
