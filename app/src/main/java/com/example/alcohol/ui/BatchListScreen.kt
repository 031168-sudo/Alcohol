package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.Warehouse
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BatchListScreen(drinkType: DrinkType) {
    val context = LocalContext.current
    val warehouse = remember { Warehouse(AppDatabase.get(context)) }
    val batches by warehouse.batches(drinkType).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale("ru")) }

    if (batches.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Партий пока нет", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(batches, key = { it.batch.id }) { summary ->
            val perLiter = summary.total / summary.batch.volumeL
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(summary.batch.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${dateFormat.format(summary.batch.startedAt)} · ${summary.batch.volumeL} л",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Себестоимость: %.0f ₽ · %.1f ₽/л".format(summary.total, perLiter),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
