package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.Warehouse

/** Новый рецепт: название, объём и количество каждого ингредиента со склада на партию. */
@Composable
fun RecipeDialog(
    drinkType: DrinkType,
    onDismiss: () -> Unit,
    onSave: (name: String, volumeL: Double, items: Map<Long, Double>) -> Unit,
) {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val stock by warehouse.stock().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var volumeText by remember { mutableStateOf("") }
    val amounts = remember { mutableStateMapOf<Long, String>() }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый рецепт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text("Объём партии, л") },
                    singleLine = true,
                )
                Text("Ингредиенты на партию:", style = MaterialTheme.typography.titleSmall)
                if (stock.isEmpty()) {
                    Text("На складе пока пусто. Сначала добавь закупки.")
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stock.forEach { row ->
                        OutlinedTextField(
                            value = amounts[row.ingredientId] ?: "",
                            onValueChange = { amounts[row.ingredientId] = it },
                            label = { Text("${row.name}, ${row.unit}") },
                            singleLine = true,
                        )
                    }
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val volume = volumeText.replace(',', '.').toDoubleOrNull()
                val items = amounts
                    .mapNotNull { (id, text) ->
                        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it }
                    }
                    .toMap()
                when {
                    name.isBlank() -> error = "Укажи название"
                    volume == null || volume <= 0 -> error = "Укажи объём партии"
                    items.isEmpty() -> error = "Добавь хотя бы один ингредиент"
                    else -> onSave(name.trim(), volume, items)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
