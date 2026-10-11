package com.example.alcohol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.alcohol.data.DrinkType
import com.example.alcohol.ui.RecipeCostScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DrinkTabs()
                }
            }
        }
    }
}

private val drinkTabs = listOf(
    "Пиво" to DrinkType.BEER,
    "Сидр" to DrinkType.CIDER,
)

@Composable
fun DrinkTabs() {
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            drinkTabs.forEachIndexed { index, (title, _) ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }
        // Пока показываем только себестоимость. Экраны партий и напоминаний добавим позже.
        RecipeCostScreen(drinkType = drinkTabs[selected].second)
    }
}
