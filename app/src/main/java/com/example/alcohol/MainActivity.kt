package com.example.alcohol

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.alcohol.data.DrinkType
import com.example.alcohol.notify.ensureChannel
import com.example.alcohol.notify.scheduleStageReminders
import com.example.alcohol.ui.DrinkScreen
import com.example.alcohol.ui.StockScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureChannel(this)
        scheduleStageReminders(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotificationPermission()
                    MainTabs()
                }
            }
        }
    }
}

/** Android 13+ требует разрешение на уведомления. Просим один раз при запуске. */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private val tabs = listOf("Пиво", "Сидр", "Склад")

@Composable
fun MainTabs() {
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }
        when (selected) {
            0 -> DrinkScreen(drinkType = DrinkType.BEER)
            1 -> DrinkScreen(drinkType = DrinkType.CIDER)
            else -> StockScreen()
        }
    }
}
