package com.example.alcohol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.Recipe
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch

/** Вкладка «Пиво» или «Сидр»: рецепты для варки, список партий и открытая партия. */
@Composable
fun DrinkScreen(drinkType: DrinkType) {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val recipes by warehouse.recipes(drinkType).collectAsState(initial = emptyList())
    val batches by warehouse.batches(drinkType).collectAsState(initial = emptyList())

    var openedBatchId by remember { mutableStateOf<Long?>(null) }
    var brewing by remember { mutableStateOf<Recipe?>(null) }
    var showRecipeDialog by remember { mutableStateOf(false) }
    var showResources by remember { mutableStateOf(false) }
    var showStages by remember { mutableStateOf(false) }
    /** Рецепт, который сейчас редактируем. null — создаём новый. */
    var editingRecipe by remember { mutableStateOf<Recipe?>(null) }
    var deletingRecipe by remember { mutableStateOf<Recipe?>(null) }
    val scope = rememberCoroutineScope()

    openedBatchId?.let { id ->
        BatchDetailScreen(batchId = id, onBack = { openedBatchId = null })
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .drinkBackdrop(drinkType),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    editingRecipe = null
                    showRecipeDialog = true
                }) { Text("Новый рецепт") }
                OutlinedButton(
                    onClick = { showResources = true },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("Ресурс") }
                OutlinedButton(
                    onClick = { showStages = true },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("Этапы") }
            }
        }

        item { Text("Рецепты", style = MaterialTheme.typography.titleMedium) }
        if (recipes.isEmpty()) {
            item { Text("Рецептов пока нет") }
        }
        items(recipes, key = { "r${it.id}" }) { recipe ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleSmall)
                    Text("Партия ${recipe.batchVolumeL} л", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { brewing = recipe }) {
                            Text(if (drinkType == DrinkType.CIDER) "Сделать" else "Сварить")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            editingRecipe = recipe
                            showRecipeDialog = true
                        }) { Icon(Icons.Default.Edit, contentDescription = "Изменить рецепт") }
                        IconButton(onClick = { deletingRecipe = recipe }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить рецепт")
                        }
                    }
                }
            }
        }

        item { Text("Партии", style = MaterialTheme.typography.titleMedium) }
        if (batches.isEmpty()) {
            item { Text("Партий пока нет") }
        }
        items(batches, key = { "b${it.batch.id}" }) { summary ->
            val perLiter = summary.total / summary.batch.volumeL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openedBatchId = summary.batch.id },
                // Разлитые партии подсвечены зелёным, как выполненные этапы.
                colors = if (summary.bottled) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (summary.bottled) "✓ ${summary.batch.name}" else summary.batch.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${formatDate(summary.batch.startedAt)} · ${summary.batch.volumeL} л",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Себестоимость: %.0f ₽ · %.1f ₽/л".format(summary.total, perLiter),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // Нижняя строка: что дальше и когда. Зелёным, а красным, если срок уже прошёл.
                    val next = summary.nextType
                    if (next != null) {
                        val planned = summary.nextPlannedAt
                        val overdue = planned != null &&
                            Instant.ofEpochMilli(planned).atZone(ZoneId.systemDefault()).toLocalDate()
                                .isBefore(LocalDate.now())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Дальше: ${stageLabel(summary.nextIcon, summary.nextTitle?.ifBlank { null } ?: stageTitle(next))} · ${planned?.let { formatDate(it) } ?: "не запланировано"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (overdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.weight(1f),
                            )
                            StageCalendarButton(warehouse = warehouse, drinkType = drinkType, summary = summary)
                        }
                    }
                }
            }
        }
    }

    if (showStages) {
        StagesDialog(warehouse = warehouse, drinkType = drinkType, onDismiss = { showStages = false })
    }

    if (showResources) {
        ResourcesDialog(warehouse = warehouse, drinkType = drinkType, onDismiss = { showResources = false })
    }

    if (showRecipeDialog) {
        RecipeDialog(
            drinkType = drinkType,
            existing = editingRecipe,
            onDismiss = { showRecipeDialog = false },
            onSave = { name, volume, items ->
                scope.launch {
                    val target = editingRecipe
                    if (target == null) {
                        warehouse.createRecipe(name, drinkType, volume, items)
                    } else {
                        warehouse.updateRecipe(target.copy(name = name, batchVolumeL = volume), items)
                    }
                    showRecipeDialog = false
                }
            },
        )
    }

    deletingRecipe?.let { recipe ->
        FormDialog(
            onDismissRequest = { deletingRecipe = null },
            title = { Text("Удалить рецепт?") },
            text = { Text("«${recipe.name}» пропадёт из списка. Уже сваренные партии останутся.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { warehouse.deleteRecipe(recipe.id) }
                    deletingRecipe = null
                }) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { deletingRecipe = null }) { Text("Отмена") } },
        )
    }

    brewing?.let { recipe ->
        BrewDialog(
            recipe = recipe,
            drinkType = drinkType,
            onDismiss = { brewing = null },
            onBrewed = { batchId ->
                brewing = null
                openedBatchId = batchId
            },
        )
    }
}
