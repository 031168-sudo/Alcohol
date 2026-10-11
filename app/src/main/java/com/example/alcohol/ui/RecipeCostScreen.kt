package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.DrinkType
import com.example.alcohol.domain.CostCalculator

@Composable
fun RecipeCostScreen(drinkType: DrinkType) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).recipeDao() }
    val recipes by dao.recipes(drinkType).collectAsState(initial = emptyList())
    val lines by dao.costLines().collectAsState(initial = emptyList())

    if (recipes.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Рецептов пока нет", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recipes, key = { it.id }) { recipe ->
            val recipeLines = lines.filter { it.recipeId == recipe.id }
            val cost = CostCalculator.calculate(recipeLines, recipe.batchVolumeL)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Партия ${recipe.batchVolumeL} л",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Себестоимость: %.0f ₽ · %.1f ₽/л".format(cost.total, cost.perLiter),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
