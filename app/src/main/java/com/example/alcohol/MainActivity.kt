package com.example.alcohol

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.alcohol.backup.AutoBackupSettings
import com.example.alcohol.backup.scheduleAutoBackup
import com.example.alcohol.data.DrinkType
import com.example.alcohol.notify.ensureChannel
import com.example.alcohol.notify.scheduleStageReminders
import com.example.alcohol.ui.AlcoholTheme
import com.example.alcohol.ui.BackupScreen
import com.example.alcohol.ui.CrashDialog
import com.example.alcohol.ui.CrashReport
import com.example.alcohol.ui.DrinkScreen
import com.example.alcohol.ui.FormHost
import com.example.alcohol.ui.LocalFormHost
import com.example.alcohol.ui.StockScreen
import com.example.alcohol.ui.TotalsChartIcon
import com.example.alcohol.ui.TotalsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReport.install(this)
        // Контент рисуется под системными панелями; отступы задаёт Scaffold через safeDrawing.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ensureChannel(this)
        scheduleStageReminders(this)
        // Автосохранение могло быть включено раньше: восстанавливаем ежедневную задачу.
        if (AutoBackupSettings.isEnabled(this)) scheduleAutoBackup(this)
        setContent {
            // Пастельная зелёная палитра; светлая или тёмная по системной теме.
            AlcoholTheme {
                val formHost = remember { FormHost() }
                CompositionLocalProvider(LocalFormHost provides formHost) {
                    NotificationPermission()
                    CrashDialog()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            contentWindowInsets = WindowInsets.safeDrawing,
                        ) { innerPadding ->
                            MainTabs(modifier = Modifier.padding(innerPadding))
                        }
                        // Открытые формы поверх всего экрана, последняя — сверху.
                        formHost.entries.forEach { it.content() }
                    }
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
private const val TOTALS_TAB = 3
private const val DATA_TAB = 4

@Composable
fun MainTabs(modifier: Modifier = Modifier) {
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        MainTabRow(selected = selected, onSelect = { selected = it })
        when (selected) {
            0 -> DrinkScreen(drinkType = DrinkType.BEER)
            1 -> DrinkScreen(drinkType = DrinkType.CIDER)
            2 -> StockScreen()
            TOTALS_TAB -> TotalsScreen()
            else -> BackupScreen()
        }
    }
}

/**
 * Своя строка вкладок вместо TabRow: у Tab жёсткая минимальная ширина,
 * из-за чего при крупном системном шрифте подписи переносились на две строки.
 * Здесь пять равных ячеек, подпись всегда в одну строку, иконки не сдвигаются.
 */
@Composable
private fun MainTabRow(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .selectableGroup(),
        ) {
            tabs.forEachIndexed { index, title ->
                TabCell(
                    selected = selected == index,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) },
                ) {
                    Text(
                        title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            TabCell(
                selected = selected == TOTALS_TAB,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(TOTALS_TAB) },
            ) {
                Icon(TotalsChartIcon, contentDescription = "Итоги")
            }
            TabCell(
                selected = selected == DATA_TAB,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(DATA_TAB) },
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Данные")
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TabCell(
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides color) { content() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
