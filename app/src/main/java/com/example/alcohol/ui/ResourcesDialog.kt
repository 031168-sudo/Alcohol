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
import com.example.alcohol.data.Resource
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

/**
 * Ресурсы производства для пива или сидра: электричество, вода и т.п.
 * У каждого название, объём напитка в литрах и цена. Ресурс добавляет price / объём к стоимости каждого литра.
 */
@Composable
fun ResourcesDialog(warehouse: Warehouse, drinkType: DrinkType, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val resources by warehouse.resources(drinkType).collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Resource?>(null) }
    var deleting by remember { mutableStateOf<Resource?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ресурсы") },
        text = {
            // Список читается здесь, внутри содержимого окна, чтобы оно обновлялось при изменениях.
            val perLiterTotal = resources.sumOf { it.perLiter }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Название, объём напитка в литрах и цена. К каждому литру добавляется цена, делённая на объём.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (resources.isEmpty()) {
                    Text("Ресурсов пока нет")
                }
                resources.forEach { resource ->
                    Column {
                        Text(resource.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${resource.volumeL.plainNumber()} л · ${resource.price.plainNumber()} ₽ · " +
                                "+${"%.2f".format(resource.perLiter)} ₽ к литру",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { editing = resource }) {
                                Icon(Icons.Default.Edit, contentDescription = "Изменить ресурс")
                            }
                            IconButton(onClick = { deleting = resource }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить ресурс")
                            }
                        }
                    }
                }
                if (resources.isNotEmpty()) {
                    Text(
                        "Всего к литру: +${"%.2f".format(perLiterTotal)} ₽",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить ресурс")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } },
    )

    if (adding) {
        ResourceEditDialog(warehouse = warehouse, drinkType = drinkType, existing = null, onDismiss = { adding = false })
    }
    editing?.let { resource ->
        ResourceEditDialog(warehouse = warehouse, drinkType = drinkType, existing = resource, onDismiss = { editing = null })
    }
    deleting?.let { resource ->
        ConfirmDeleteDialog(
            text = "Ресурс «${resource.name}» будет удалён, стоимость литра уменьшится.",
            onConfirm = { scope.launch { warehouse.deleteResource(resource.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ResourceEditDialog(
    warehouse: Warehouse,
    drinkType: DrinkType,
    existing: Resource?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var volumeText by remember { mutableStateOf(existing?.volumeL?.plainNumber() ?: "") }
    var priceText by remember { mutableStateOf(existing?.price?.plainNumber() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    FormDialog(
        onDismissRequest = onDismiss,
        message = error,
        title = { Text(if (existing == null) "Новый ресурс" else "Изменить ресурс") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название, например «Электричество»") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text("Объём, л") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Цена, ₽") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val volume = volumeText.replace(',', '.').toDoubleOrNull()
                val price = priceText.replace(',', '.').toDoubleOrNull()
                when {
                    name.isBlank() -> error = "Укажи название"
                    volume == null || volume <= 0 -> error = "Укажи объём в литрах"
                    price == null || price < 0 -> error = "Укажи цену"
                    else -> {
                        error = null
                        scope.launch {
                            try {
                                if (existing == null) {
                                    warehouse.addResource(drinkType, name, volume, price)
                                } else {
                                    warehouse.updateResource(existing.id, drinkType, name, volume, price)
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

/** 20.0 → «20», 0.5 → «0.5». */
private fun Double.plainNumber(): String = if (this == toLong().toDouble()) toLong().toString() else toString()
