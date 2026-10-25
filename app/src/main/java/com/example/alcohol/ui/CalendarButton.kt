package com.example.alcohol.ui

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.alcohol.data.BatchSummary
import com.example.alcohol.data.DrinkType
import com.example.alcohol.data.Warehouse
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Календарь на телефоне, в который можно записать событие (в том числе Google-календари аккаунтов). */
data class DeviceCalendar(val id: Long, val name: String, val account: String)

/** Работа с календарём телефона через системный Calendar Provider. */
object DeviceCalendars {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Календари, в которые можно добавлять события. */
    fun writable(context: Context): List<DeviceCalendar> {
        val result = mutableListOf<DeviceCalendar>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                ),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                CalendarContract.Calendars.ACCOUNT_NAME,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += DeviceCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty())
                }
            }
        } catch (_: SecurityException) {
            // Нет разрешения: вернём пустой список.
        }
        return result
    }

    /**
     * Добавляет событие на указанный день: начало в 12:00 по времени телефона, длится 30 минут,
     * с напоминанием в момент начала. Возвращает id события или null, если не получилось.
     */
    fun addEvent(context: Context, calendarId: Long, title: String, dayMillis: Long): Long? {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(dayMillis).atZone(zone).toLocalDate()
        val start = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + EVENT_MINUTES * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        return try {
            val eventId = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?.let { ContentUris.parseId(it) } ?: return null
            // Напоминание в момент начала события.
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 0)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
            eventId
        } catch (_: SecurityException) {
            null
        }
    }

    private const val EVENT_MINUTES = 30
}

/** «🍺 Пиво, партия 13.10.2026: РОЗЛИВ». */
private fun eventTitle(drinkType: DrinkType, summary: BatchSummary): String {
    val icon = if (drinkType == DrinkType.CIDER) "🍷" else "🍺"
    val kind = if (drinkType == DrinkType.CIDER) "Сидр" else "Пиво"
    val action = (summary.nextTitle?.ifBlank { null } ?: stageTitle(summary.nextType!!)).uppercase(Locale("ru"))
    return "$icon $kind, партия ${formatDate(summary.batch.startedAt)}: $action"
}

/**
 * Иконка справа от строки «Дальше: …»: заносит следующий этап в календарь телефона.
 * Когда этап уже занесён, показывает календарь с зелёной галочкой.
 */
@Composable
fun StageCalendarButton(warehouse: Warehouse, drinkType: DrinkType, summary: BatchSummary) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) picking = true else message = "Без доступа к календарю событие не занести"
    }

    val stageId = summary.nextStageId
    val planned = summary.nextPlannedAt
    if (stageId == null || planned == null || summary.nextType == null) return

    if (summary.nextEventId != null) {
        Icon(
            CalendarCheckIcon,
            contentDescription = "Занесено в календарь",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp),
        )
    } else {
        IconButton(onClick = {
            if (DeviceCalendars.hasPermission(context)) {
                picking = true
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }
        }) {
            Icon(Icons.Default.DateRange, contentDescription = "Занести в календарь", tint = MaterialTheme.colorScheme.primary)
        }
    }

    if (picking) {
        val calendars = remember { DeviceCalendars.writable(context) }
        FormDialog(
            onDismissRequest = { picking = false },
            title = { Text("В какой календарь занести?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(eventTitle(drinkType, summary), style = MaterialTheme.typography.bodyMedium)
                    Text("${formatDate(planned)}, 12:00, 30 минут, напоминание в момент начала", style = MaterialTheme.typography.bodySmall)
                    if (calendars.isEmpty()) Text("На телефоне нет календарей, в которые можно писать.")
                    calendars.forEach { calendar ->
                        OutlinedButton(
                            onClick = {
                                picking = false
                                val id = DeviceCalendars.addEvent(context, calendar.id, eventTitle(drinkType, summary), planned)
                                if (id != null) {
                                    scope.launch { warehouse.setStageCalendarEvent(stageId, id) }
                                } else {
                                    message = "Не удалось занести событие"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (calendar.account.isBlank()) calendar.name else "${calendar.name} · ${calendar.account}")
                        }
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { picking = false }) { Text("Отмена") } },
        )
    }

    message?.let { text ->
        FormDialog(
            onDismissRequest = { message = null },
            title = { Text("Календарь") },
            text = { Text(text) },
            confirmButton = { Button(onClick = { message = null }) { Text("Ок") } },
        )
    }
}
