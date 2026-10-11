package com.example.alcohol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.DrinkType

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

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selected) {
                drinkTabs.forEachIndexed { index, (title, _) ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(title) },
                    )
                }
            }
            // Экраны рецептов, партий и себестоимости добавим на следующих шагах.
            val current = drinkTabs[selected].second
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Раздел: ${drinkTabs[selected].first} (${current.name})")
            }
        }
    }
}
