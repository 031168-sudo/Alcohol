package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.Category
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

@Composable
fun StockScreen() {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val stock by warehouse.stock().collectAsState(initial = emptyList())
    var showPurchase by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showCategories = true }) { Text("Категории") }
            Button(onClick = { showPurchase = true }) { Text("Закупка") }
        }

        if (stock.isEmpty()) {
            Text(
                "На складе пусто. Добавь первую закупку.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stock, key = { it.ingredientId }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(row.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${row.category} · остаток %.1f %s".format(row.remaining, row.unit),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPurchase) {
        PurchaseDialog(
            warehouse = warehouse,
            onDismiss = { showPurchase = false },
        )
    }

    if (showCategories) {
        CategoriesDialog(
            warehouse = warehouse,
            onDismiss = { showCategories = false },
        )
    }
}

@Composable
private fun PurchaseDialog(warehouse: Warehouse, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val categories by warehouse.categories().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf("г") }
    var quantityText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val selected: Category? = categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая закупка") },
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
                Column {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Text("Категория: ${selected?.name ?: "нет категорий"}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Единица (г, мл, шт.)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Количество") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Сумма покупки, ₽") },
                    singleLine = true,
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val quantity = quantityText.replace(',', '.').toDoubleOrNull()
                val price = priceText.replace(',', '.').toDoubleOrNull()
                when {
                    name.isBlank() -> error = "Укажи название"
                    selected == null -> error = "Сначала создай категорию"
                    unit.isBlank() -> error = "Укажи единицу измерения"
                    quantity == null || quantity <= 0 -> error = "Количество должно быть больше нуля"
                    price == null || price < 0 -> error = "Укажи сумму покупки"
                    else -> scope.launch {
                        warehouse.purchase(
                            name = name,
                            categoryId = selected.id,
                            unit = unit,
                            quantity = quantity,
                            totalPrice = price,
                            purchasedAt = System.currentTimeMillis(),
                        )
                        onDismiss()
                    }
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Список категорий: добавить, переименовать, удалить. Удалить можно только пустую категорию. */
@Composable
private fun CategoriesDialog(warehouse: Warehouse, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val categories by warehouse.categories().collectAsState(initial = emptyList())

    var newName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категории") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    if (editingId == category.id) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            label = { Text("Новое название") },
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val ok = warehouse.renameCategory(category.id, editText)
                                    if (ok) {
                                        editingId = null
                                        message = null
                                    } else {
                                        message = "Такая категория уже есть"
                                    }
                                }
                            }) { Text("Сохранить") }
                            OutlinedButton(onClick = { editingId = null }) { Text("Отмена") }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(category.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                editingId = category.id
                                editText = category.name
                            }) { Text("Изменить") }
                            TextButton(onClick = {
                                scope.launch {
                                    val ok = warehouse.deleteCategory(category.id)
                                    message = if (ok) null
                                    else "В «${category.name}» есть ингредиенты. Сначала перенеси их."
                                }
                            }) { Text("Удалить") }
                        }
                    }
                }

                Text("Новая категория", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                Button(onClick = {
                    scope.launch {
                        if (newName.isBlank()) {
                            message = "Укажи название"
                        } else {
                            val ok = warehouse.addCategory(newName)
                            if (ok) {
                                newName = ""
                                message = null
                            } else {
                                message = "Такая категория уже есть"
                            }
                        }
                    }
                }) { Text("Добавить") }

                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } },
    )
}
