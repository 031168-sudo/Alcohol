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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
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
    var usageOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }) { Text("Удалить партию") }
        }
        batch?.let { b ->
            item {
                // Нажми на название, чтобы увидеть и поправить всё, что взято на варку.
                // Карандаш справа открывает то же окно правки партии: дата, объём и списания.
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        b.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).clickable { usageOpen = true },
                    )
                    IconButton(onClick = { usageOpen = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Изменить партию")
                    }
                }
                Text("Поставлено ${formatDate(b.startedAt)} · ${b.volumeL} л", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Нажми на название, чтобы посмотреть и поправить списания",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { Text("Этапы", style = MaterialTheme.typography.titleMedium) }

        items(stages, key = { it.id }) { stage ->
            val done = stage.doneAt
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = done == null) { completing = stage },
                // Выполненные этапы подсвечены зелёным, невыполненные — нейтральным.
                colors = if (done != null) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(stage.displayTitle(), style = MaterialTheme.typography.titleSmall)
                        if (done != null) {
                            Text(
                            "✓ Выполнено ${formatDate(done)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
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

    if (confirmDelete) {
        FormDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить партию?") },
            text = {
                Text(
                    "Всё, что партия взяла со склада, вернётся на закупки. Этапы и списания будут удалены. " +
                        "Это нельзя отменить.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        warehouse.deleteBatch(batchId)
                        confirmDelete = false
                        onBack()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    if (usageOpen) {
        batch?.let { b ->
            BatchUsageDialog(warehouse = warehouse, batch = b, onDismiss = { usageOpen = false })
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
    var dateText by remember { mutableStateOf(formatDate(stage.doneAt ?: stage.plannedAt ?: System.currentTimeMillis())) }
    var tempText by remember { mutableStateOf(stage.temperatureC?.toString() ?: "") }
    var gravityText by remember { mutableStateOf(stage.gravity?.toString() ?: "") }
    var notes by remember { mutableStateOf(stage.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text(if (stage.doneAt == null) stage.displayTitle() else "Изменить: ${stage.displayTitle()}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField(
                    label = "Дата (дд.мм.гггг)",
                    value = dateText,
                    onValueChange = { dateText = it },
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

/**
 * Всё, что взято на варку: по каждому ингредиенту его количество и стоимость.
 * Количество можно поправить, очистить (списание снимется) или добавить новый ингредиент.
 */
@Composable
private fun BatchUsageDialog(warehouse: Warehouse, batch: com.example.alcohol.data.Batch, onDismiss: () -> Unit) {
    val batchId = batch.id
    var dateText by remember { mutableStateOf(formatDate(batch.startedAt)) }
    var volumeText by remember {
        mutableStateOf(batch.volumeL.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() })
    }
    val stock by warehouse.stock().collectAsState(initial = emptyList())
    val uses by warehouse.batchUses(batchId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val amounts = remember { mutableStateMapOf<Long, String>() }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Один раз заполняем поля текущими списаниями, дальше правит пользователь.
    LaunchedEffect(batchId) {
        if (!loaded) {
            warehouse.batchUsesOnce(batchId)
                .groupBy { it.ingredientId }
                .forEach { (id, rows) -> amounts[id] = rows.sumOf { it.amount }.toPlainString() }
            loaded = true
        }
    }

    val totalCost = uses.sumOf { it.cost }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text("Изменить партию") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField(label = "Дата (дд.мм.гггг)", value = dateText, onValueChange = { dateText = it })
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text("Объём, л") },
                    singleLine = true,
                )
                Text("На варку взято", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Всего на ${"%.2f".format(totalCost)} ₽. Очисти поле, чтобы снять списание. " +
                        "Закупки берутся только те, что сделаны не позже даты варки.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (stock.isEmpty()) Text("На складе пока пусто.")
                stock.forEach { row ->
                    val cost = uses.filter { it.ingredientId == row.ingredientId }.sumOf { it.cost }
                    OutlinedTextField(
                        value = amounts[row.ingredientId] ?: "",
                        onValueChange = { amounts[row.ingredientId] = it },
                        label = { Text("${row.name}, ${row.unit}") },
                        singleLine = true,
                    )
                    if (cost > 0) {
                        Text("Стоимость: ${"%.2f".format(cost)} ₽", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val targets = amounts
                    .mapNotNull { (id, text) ->
                        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it }
                    }
                    .toMap()
                val at = parseDate(dateText)
                val volume = volumeText.replace(',', '.').toDoubleOrNull()
                if (at == null) {
                    error = "Дата в формате дд.мм.гггг"
                    return@Button
                }
                if (volume == null || volume <= 0) {
                    error = "Укажи объём в литрах"
                    return@Button
                }
                error = null
                scope.launch {
                    // Сначала дата и объём (списания берут закупки не позже этой даты), потом списания.
                    // Дата в названии партии («Рецепт · дд.мм.гггг») меняется вместе с датой постановки.
                    val oldSuffix = " · ${formatDate(batch.startedAt)}"
                    val name = if (batch.name.endsWith(oldSuffix)) {
                        batch.name.removeSuffix(oldSuffix) + " · ${formatDate(at)}"
                    } else {
                        batch.name
                    }
                    val problem = warehouse.updateBatchBasics(batchId, name, at, volume)
                    if (problem != null) {
                        error = problem
                        return@launch
                    }
                    val shortfalls = warehouse.setBatchUsage(batchId, targets)
                    if (shortfalls.isEmpty()) {
                        onDismiss()
                    } else {
                        error = "Не хватает: " + shortfalls.entries.joinToString(", ") { (id, lack) ->
                            val row = stock.firstOrNull { it.ingredientId == id }
                            "${row?.name ?: "#$id"} — ${"%.1f".format(lack)} ${row?.unit ?: ""}"
                        }
                    }
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun Double.toPlainString(): String =
    if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()
