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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.IngredientKind
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

@Composable
fun StockScreen() {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    val stock by warehouse.stock().collectAsState(initial = emptyList())
    var showPurchase by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
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
                                "Остаток: %.1f %s".format(row.remaining, row.unit),
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
            onDismiss = { showPurchase = false },
            onSave = { name, kind, unit, quantity, price ->
                scope.launch {
                    warehouse.purchase(
                        name = name,
                        kind = kind,
                        unit = unit,
                        quantity = quantity,
                        totalPrice = price,
                        purchasedAt = System.currentTimeMillis(),
                    )
                    showPurchase = false
                }
            },
        )
    }
}

@Composable
private fun PurchaseDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, kind: IngredientKind, unit: String, quantity: Double, price: Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kindIndex by remember { mutableStateOf(0) }
    var unit by remember { mutableStateOf("г") }
    var quantityText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val kinds = IngredientKind.entries
    val kind = kinds[kindIndex]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая закупка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedButton(onClick = { kindIndex = (kindIndex + 1) % kinds.size }) {
                    Text("Вид: ${kind.name}")
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
                    unit.isBlank() -> error = "Укажи единицу измерения"
                    quantity == null || quantity <= 0 -> error = "Количество должно быть больше нуля"
                    price == null || price < 0 -> error = "Укажи сумму покупки"
                    else -> onSave(name.trim(), kind, unit.trim(), quantity, price)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
