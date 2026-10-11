package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.BrewResult
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.Recipe
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

/**
 * Варка по рецепту: количества берутся из рецепта, их можно поправить.
 * Списывается то, что реально есть на складе, по FIFO.
 */
@Composable
fun BrewDialog(
    recipe: Recipe,
    drinkType: DrinkType,
    onDismiss: () -> Unit,
    onBrewed: (batchId: Long) -> Unit,
) {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val stock by warehouse.stock().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val amounts = remember { mutableStateMapOf<Long, String>() }
    var volumeText by remember { mutableStateOf(recipe.batchVolumeL.toString()) }
    var dateText by remember { mutableStateOf(formatDate(System.currentTimeMillis())) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recipe.id) {
        val items = warehouse.recipeItems(recipe.id)
        items.forEach { amounts[it.ingredientId] = it.amount.toString() }
    }

    val names = stock.associate { it.ingredientId to "${it.name} (${it.unit})" }
    val units = stock.associate { it.ingredientId to it.unit }

    FormDialog(
        onDismissRequest = onDismiss,
        title = { Text("Варим: ${recipe.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateField(
                    label = "Дата варки (дд.мм.гггг)",
                    value = dateText,
                    onValueChange = { dateText = it },
                )
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text("Объём, л") },
                    singleLine = true,
                )
                Text("Что списываем со склада:", style = MaterialTheme.typography.titleSmall)
                amounts.keys.toList().forEach { id ->
                    val label = names[id] ?: "Ингредиент #$id"
                    OutlinedTextField(
                        value = amounts[id] ?: "",
                        onValueChange = { amounts[id] = it },
                        label = { Text(label) },
                        singleLine = true,
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val volume = volumeText.replace(',', '.').toDoubleOrNull()
                val brewedAt = parseDate(dateText)
                val needs = amounts
                    .mapNotNull { (id, text) ->
                        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it }
                    }
                    .toMap()

                if (brewedAt == null) {
                    error = "Дата варки в формате дд.мм.гггг"
                    return@Button
                }
                if (volume == null || volume <= 0) {
                    error = "Укажи объём"
                    return@Button
                }
                if (needs.isEmpty()) {
                    error = "Нет ингредиентов для варки"
                    return@Button
                }

                scope.launch {
                    when (val result = warehouse.brew(
                        name = "${recipe.name} · ${formatDate(brewedAt)}",
                        drinkType = drinkType,
                        recipeId = recipe.id,
                        volumeL = volume,
                        startedAt = brewedAt,
                        needs = needs,
                    )) {
                        is BrewResult.Brewed -> onBrewed(result.batchId)
                        is BrewResult.NotEnoughStock -> {
                            error = "Не хватает: " + result.shortfalls.entries.joinToString(", ") { (id, lack) ->
                                "${names[id] ?: "#$id"} — ${"%.1f".format(lack)} ${units[id] ?: ""}"
                            }
                        }
                    }
                }
            }) { Text("Сварить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
