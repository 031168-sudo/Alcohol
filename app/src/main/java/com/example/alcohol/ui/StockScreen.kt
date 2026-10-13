package com.example.alcohol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.alcohol.data.Purchase
import com.example.alcohol.data.StockRow
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun StockScreen() {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val stock by warehouse.stock().collectAsState(initial = emptyList())
    var showPurchase by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var openedStock by remember { mutableStateOf<StockRow?>(null) }

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
            Text(
                "Нажми на позицию, чтобы посмотреть, изменить или удалить её закупки.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stock, key = { it.ingredientId }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openedStock = row },
                    ) {
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

    openedStock?.let { row ->
        PurchasesDialog(
            warehouse = warehouse,
            row = row,
            onDismiss = { openedStock = null },
        )
    }
}

/** Позиция склада: название, категория и единица. Закупки при этом не меняются. */
@Composable
private fun IngredientDialog(
    warehouse: Warehouse,
    row: StockRow,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val categories by warehouse.categories().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(row.name) }
    var categoryId by remember { mutableStateOf(row.categoryId) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(row.unit) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val category = categories.firstOrNull { it.id == categoryId }

    FormDialog(
        onDismissRequest = onDismiss,
        title = { Text("Позиция склада") },
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
                    OutlinedButton(onClick = { categoryMenuOpen = true }) {
                        Text("Категория: ${category?.name ?: "нет категорий"}")
                    }
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    categoryId = c.id
                                    categoryMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Column {
                    OutlinedButton(onClick = { unitMenuOpen = true }) { Text("Единица: $unit") }
                    DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                        stockUnits.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    unit = u
                                    unitMenuOpen = false
                                },
                            )
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    try {
                        warehouse.updateIngredient(row.ingredientId, name, categoryId, unit)
                        onSaved()
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Единицы измерения для склада. Выбираются из списка. */
private val stockUnits = listOf("г", "кг", "мл", "л", "шт.")

/** 1000.0 → «1000», 2.5 → «2.5». */
private fun Double.plain(): String = if (this == toLong().toDouble()) toLong().toString() else toString()

/** Одно ручное списание: все его строки по закупкам вместе. */
private data class WriteOffGroup(
    val at: Long,
    val reason: String,
    val amount: Double,
    val cost: Double,
    val ids: List<Long>,
)

/** Строка списка на карточке позиции: закупка или ручное списание. */
private sealed interface LedgerEntry {
    val at: Long

    data class Bought(val purchase: Purchase) : LedgerEntry {
        override val at: Long get() = purchase.purchasedAt
    }

    data class WrittenOff(val group: WriteOffGroup) : LedgerEntry {
        override val at: Long get() = group.at
    }
}

/** «дата · −25 г · −12 ₽»: списание со знаком минус, сумма в рублях красным. */
private fun writeOffLine(date: String, amount: Double, unit: String, cost: Double): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append("$date · −${amount.plain()} $unit · ")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFB3261E)))
        append("−${"%.0f".format(cost)} ₽")
        pop()
    }

/** Закупки одной позиции склада: можно изменить или удалить каждую. */
@Composable
private fun PurchasesDialog(warehouse: Warehouse, row: StockRow, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Каждый список грузится отдельно; если загрузка падает, причина видна на экране, а не прячется пустым списком.
    var loadError by remember { mutableStateOf<String?>(null) }
    val purchases by androidx.compose.runtime.produceState(
        initialValue = emptyList<Purchase>(),
        key1 = row.ingredientId,
    ) {
        try {
            value = warehouse.purchasesListOf(row.ingredientId)
            warehouse.purchasesOf(row.ingredientId).collect { value = it }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            loadError = "Закупки не загрузились: $e"
        }
    }
    val writeOffs by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.example.alcohol.data.WriteOff>(),
        key1 = row.ingredientId,
    ) {
        try {
            value = warehouse.writeOffsListOf(row.ingredientId)
            warehouse.writeOffsOf(row.ingredientId).collect { value = it }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            loadError = "Списания не загрузились: $e"
        }
    }
    var editing by remember { mutableStateOf<Purchase?>(null) }
    var editingIngredient by remember { mutableStateOf(false) }
    var writingOff by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.name) },
        text = {
        // Ручные списания: строки одного списания (одна дата и причина) показываются одной строкой.
        val writeOffGroups = writeOffs
            .groupBy { it.writtenOffAt to it.reason }
            .map { (key, rows) ->
                WriteOffGroup(
                    at = key.first,
                    reason = key.second,
                    amount = rows.sumOf { it.amount },
                    cost = rows.sumOf { it.cost },
                    ids = rows.map { it.id },
                )
            }
        // Закупки и списания одним списком по дате, новые сверху.
        val ledger: List<LedgerEntry> = (
            purchases.map { LedgerEntry.Bought(it) } + writeOffGroups.map { LedgerEntry.WrittenOff(it) }
            ).sortedByDescending { it.at }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "${row.category} · ${row.unit}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { editingIngredient = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Изменить позицию")
                    }
                }
                OutlinedButton(onClick = { writingOff = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Списать")
                }
                loadError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (ledger.isEmpty() && loadError == null) {
                    Text("Закупок пока нет")
                }
                ledger.forEach { entry ->
                    when (entry) {
                        is LedgerEntry.Bought -> {
                            val purchase = entry.purchase
                            Column {
                                Text(
                                    "${formatDate(purchase.purchasedAt)} · %.1f %s · %.0f ₽".format(
                                        purchase.quantity, row.unit, purchase.totalPrice,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "Осталось %.1f %s".format(purchase.remaining, row.unit),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = { editing = purchase }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Изменить закупку")
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            message = if (warehouse.deletePurchase(purchase.id)) {
                                                null
                                            } else {
                                                "Из этой закупки уже списано в варку, удалить нельзя"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить закупку")
                                    }
                                }
                            }
                        }
                        is LedgerEntry.WrittenOff -> {
                            val group = entry.group
                            Column {
                                Text(
                                    writeOffLine(formatDate(group.at), group.amount, row.unit, group.cost),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    if (group.reason.isBlank()) "Списание" else "Списание: ${group.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = {
                                        scope.launch { group.ids.forEach { warehouse.deleteWriteOff(it) } }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить списание")
                                    }
                                }
                            }
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } },
    )

    if (writingOff) {
        WriteOffDialog(warehouse = warehouse, row = row, onDismiss = { writingOff = false })
    }

    editing?.let { purchase ->
        PurchaseDialog(
            warehouse = warehouse,
            existing = purchase,
            onDismiss = { editing = null },
        )
    }

    if (editingIngredient) {
        IngredientDialog(
            warehouse = warehouse,
            row = row,
            onDismiss = { editingIngredient = false },
            // Позиция могла слиться с другой и исчезнуть, поэтому закрываем и этот список.
            onSaved = onDismiss,
        )
    }
}

/** Новая закупка, или изменение существующей (тогда название и категория не меняются). */
@Composable
private fun PurchaseDialog(warehouse: Warehouse, existing: Purchase? = null, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val categories by warehouse.categories().collectAsState(initial = emptyList())
    val stock by warehouse.stock().collectAsState(initial = emptyList())
    val purchases by warehouse.allPurchases().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf("г") }
    var quantityText by remember { mutableStateOf(existing?.quantity?.toString() ?: "") }
    var priceText by remember { mutableStateOf(existing?.totalPrice?.toString() ?: "") }
    var dateText by remember {
        mutableStateOf(formatDate(existing?.purchasedAt ?: System.currentTimeMillis()))
    }
    var error by remember { mutableStateOf<String?>(null) }

    val selected: Category? = categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull()

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text(if (existing == null) "Новая закупка" else "Изменить закупку") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (existing == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true,
                    )
                    // Подсказки по первым трём буквам: выбор подставляет категорию, единицу и последнее количество.
                    val query = name.trim()
                    val suggestions = if (query.length >= 3) {
                        stock.filter { row ->
                            row.name.startsWith(query, ignoreCase = true) &&
                                !(row.name == query && row.categoryId == categoryId)
                        }.take(5)
                    } else {
                        emptyList()
                    }
                    suggestions.forEach { row ->
                        OutlinedButton(
                            onClick = {
                                name = row.name
                                categoryId = row.categoryId
                                unit = row.unit
                                purchases.firstOrNull { it.ingredientId == row.ingredientId }
                                    ?.let { quantityText = it.quantity.plain() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${row.name} · ${row.category} · ${row.unit}")
                        }
                    }
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
                    Column {
                        OutlinedButton(onClick = { unitMenuOpen = true }) { Text("Единица: $unit") }
                        DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                            stockUnits.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        unit = u
                                        unitMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
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
                DateField(
                    label = "Дата закупки (дд.мм.гггг)",
                    value = dateText,
                    onValueChange = { dateText = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val quantity = quantityText.replace(',', '.').toDoubleOrNull()
                val price = priceText.replace(',', '.').toDoubleOrNull()
                val purchasedAt = parseDate(dateText)
                error = null
                when {
                    existing == null && name.isBlank() -> error = "Укажи название"
                    existing == null && unit.isBlank() -> error = "Укажи единицу измерения"
                    quantity == null || quantity <= 0 -> error = "Количество должно быть больше нуля"
                    price == null || price < 0 -> error = "Укажи сумму покупки"
                    purchasedAt == null -> error = "Дата в формате дд.мм.гггг"
                    else -> scope.launch {
                        try {
                            if (existing != null) {
                                warehouse.updatePurchase(existing.id, purchasedAt, quantity, price)
                            } else {
                                // Категорию читаем заново в момент сохранения, а не из снимка экрана.
                                val all = warehouse.categories().first()
                                val chosen = all.firstOrNull { it.id == categoryId } ?: all.firstOrNull()
                                if (chosen == null) {
                                    error = "Сначала создай категорию"
                                    return@launch
                                }
                                warehouse.purchase(
                                    name = name,
                                    categoryId = chosen.id,
                                    unit = unit,
                                    quantity = quantity,
                                    totalPrice = price,
                                    purchasedAt = purchasedAt,
                                )
                            }
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message ?: "Не удалось сохранить: ${e::class.java.simpleName}"
                        }
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

    FormDialog(
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
                            IconButton(onClick = {
                                editingId = category.id
                                editText = category.name
                            }) { Icon(Icons.Default.Edit, contentDescription = "Переименовать") }
                            IconButton(onClick = {
                                scope.launch {
                                    val ok = warehouse.deleteCategory(category.id)
                                    message = if (ok) null
                                    else "В «${category.name}» есть ингредиенты. Сначала перенеси их."
                                }
                            }) { Icon(Icons.Default.Delete, contentDescription = "Удалить категорию") }
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

/** Ручное списание: количество из позиции, дата не раньше закупок, которые покрывают его. */
@Composable
private fun WriteOffDialog(warehouse: Warehouse, row: StockRow, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dateText by remember { mutableStateOf(formatDate(System.currentTimeMillis())) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text("Списать: ${row.name}") },
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
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сколько, ${row.unit}") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Причина (необязательно)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val at = parseDate(dateText)
                val amount = amountText.replace(',', '.').toDoubleOrNull()
                when {
                    at == null -> error = "Дата в формате дд.мм.гггг"
                    amount == null || amount <= 0 -> error = "Укажи количество"
                    else -> {
                        error = null
                        scope.launch {
                            try {
                                val shortfall = warehouse.writeOff(row.ingredientId, at, amount, reason)
                                if (shortfall > 0) {
                                    error = "Не хватает ${"%.1f".format(shortfall)} ${row.unit}"
                                } else {
                                    onDismiss()
                                }
                            } catch (e: IllegalArgumentException) {
                                error = e.message ?: "Не удалось списать"
                            }
                        }
                    }
                }
            }) { Text("Списать") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
