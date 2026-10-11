package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.BatchSummary
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.Warehouse
import com.example.alcohol.data.YearTotal
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

private const val BEER_EMOJI = "🍺"
private const val CIDER_EMOJI = "🍷"

/** Итоги по годам: сколько сварено, сколько потрачено и средняя себестоимость литра. */
@Composable
fun TotalsScreen() {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val beer by warehouse.batches(DrinkType.BEER).collectAsState(initial = emptyList())
    val cider by warehouse.batches(DrinkType.CIDER).collectAsState(initial = emptyList())
    val custom by warehouse.yearTotals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var addingFor by remember { mutableStateOf<Int?>(null) }

    val years = (beer.map { yearOf(it.batch.startedAt) } +
        cider.map { yearOf(it.batch.startedAt) } +
        custom.map { it.year })
        .distinct()
        .sortedDescending()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (years.isEmpty()) {
            item {
                Text("Итогов пока нет. Появятся после первой варки или добавленного итога.")
                OutlinedButton(onClick = { addingFor = yearNow() }) { Text("Добавить итог") }
            }
        }
        years.forEach { year ->
            val beerYear = beer.filter { yearOf(it.batch.startedAt) == year }
            val ciderYear = cider.filter { yearOf(it.batch.startedAt) == year }
            val customYear = custom.filter { it.year == year }
            item(key = "year$year") {
                YearCard(
                    year = year,
                    beer = beerYear,
                    cider = ciderYear,
                    custom = customYear,
                    onAdd = { addingFor = year },
                    onDelete = { id -> scope.launch { warehouse.deleteYearTotal(id) } },
                )
            }
        }
    }

    addingFor?.let { year ->
        AddYearTotalDialog(
            warehouse = warehouse,
            year = year,
            onDismiss = { addingFor = null },
        )
    }
}

@Composable
private fun YearCard(
    year: Int,
    beer: List<BatchSummary>,
    cider: List<BatchSummary>,
    custom: List<YearTotal>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(year.toString(), style = MaterialTheme.typography.titleLarge)
            DrinkTotalRow(emoji = BEER_EMOJI, title = "Пиво", batches = beer)
            DrinkTotalRow(emoji = CIDER_EMOJI, title = "Сидр", batches = cider)
            custom.forEach { total ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(total.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${"%.1f".format(total.quantity)} ${total.unit} · ${"%.0f".format(total.totalCost)} ₽ · " +
                                "${"%.2f".format(total.totalCost / total.quantity)} ₽/${total.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IconButton(onClick = { onDelete(total.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить итог")
                    }
                }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Добавить итог") }
        }
    }
}

/** Одна строка по напитку: сколько литров, сколько всего потрачено, средняя себестоимость литра. */
@Composable
private fun DrinkTotalRow(emoji: String, title: String, batches: List<BatchSummary>) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(emoji, fontSize = 32.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (batches.isEmpty()) {
                Text("Варок не было", style = MaterialTheme.typography.bodyMedium)
            } else {
                val liters = batches.sumOf { it.batch.volumeL }
                val spent = batches.sumOf { it.total }
                val perLiter = if (liters > 0) "%.1f ₽/л".format(spent / liters) else "—"
                Text(
                    "${"%.1f".format(liters)} л · всего ${"%.0f".format(spent)} ₽ · в среднем $perLiter",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AddYearTotalDialog(warehouse: Warehouse, year: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("шт.") }
    var costText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text("Итог за $year") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Количество") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Единица (л, шт., кг…)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Себестоимость, ₽") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val quantity = quantityText.replace(',', '.').toDoubleOrNull()
                val cost = costText.replace(',', '.').toDoubleOrNull()
                when {
                    quantity == null || quantity <= 0 -> error = "Количество должно быть больше нуля"
                    cost == null || cost < 0 -> error = "Укажи себестоимость"
                    else -> {
                        error = null
                        scope.launch {
                            try {
                                warehouse.addYearTotal(year, name, quantity, unit, cost)
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

private fun yearOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).year

private fun yearNow(): Int = java.time.LocalDate.now().year
