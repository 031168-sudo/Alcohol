package com.example.alcohol.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.Backup
import com.example.alcohol.data.BackupCounts
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PendingImport(val json: String, val counts: BackupCounts)

private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

/** Подписи таблиц для счётчиков на экране копии. Порядок — как в [Backup.TABLES]. */
private val TABLE_LABELS = linkedMapOf(
    "categories" to "категорий",
    "ingredients" to "позиций склада",
    "purchases" to "закупок",
    "recipes" to "рецептов",
    "recipe_items" to "строк рецептов",
    "batches" to "партий",
    "batch_ingredients" to "списаний в варки",
    "batch_stages" to "этапов",
    "stage_templates" to "шагов процесса",
    "resources" to "ресурсов",
    "year_totals" to "итогов",
    "write_offs" to "списаний",
)

/**
 * Резервная копия данных: сохранить всё в файл и загрузить обратно.
 * Данные лежат только на телефоне, поэтому без копии при удалении приложения они пропадут.
 */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf<BackupCounts?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingImport?>(null) }

    LaunchedEffect(Unit) { current = Backup.counts(db) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            status = null
            try {
                val json = Backup.export(db)
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Не удалось открыть файл для записи")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                status = "Копия сохранена: ${Backup.countsOf(json).total} записей."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось сохранить копию: ${messageOf(e)}"
            } finally {
                busy = false
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            status = null
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Не удалось открыть файл")
                    val json = input.use { it.readBytes().toString(Charsets.UTF_8) }
                    PendingImport(json, Backup.countsOf(json))
                }
                pending = parsed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось прочитать копию: ${messageOf(e)}"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Резервная копия", style = MaterialTheme.typography.titleLarge)
        Text(
            "Все данные хранятся только на этом телефоне: сами они никуда не отправляются. " +
                "Удалишь приложение или очистишь его данные — записи пропадут.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Сохраняй файл копии в облако, на компьютер или в почту. Из него можно вернуть всё " +
                "на этом телефоне или перенести записи на новый.",
            style = MaterialTheme.typography.bodyMedium,
        )

        current?.let { counts ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Сейчас в приложении", style = MaterialTheme.typography.titleSmall)
                    Text("${counts.total} записей", style = MaterialTheme.typography.bodyMedium)
                    Text(describe(counts), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(
            onClick = { saveLauncher.launch("moi-napitki-${LocalDateTime.now().format(FILE_STAMP)}.json") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить копию") }

        OutlinedButton(
            onClick = { openLauncher.launch(arrayOf("*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Загрузить из копии") }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    pending?.let { importData ->
        FormDialog(
            onDismissRequest = { pending = null },
            title = { Text("Заменить все данные?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Сейчас в приложении: ${current?.total ?: 0} записей.")
                    Text("В копии: ${importData.counts.total} записей.")
                    Text(describe(importData.counts), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Текущие записи будут удалены и заменены данными из файла. Отменить это нельзя.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    scope.launch {
                        busy = true
                        error = null
                        status = null
                        try {
                            val restored = Backup.restore(db, importData.json)
                            current = Backup.counts(db)
                            status = "Данные загружены: ${restored.total} записей."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Не удалось загрузить копию: ${messageOf(e)}"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Заменить") }
            },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Отмена") } },
        )
    }
}

private fun describe(counts: BackupCounts): String =
    TABLE_LABELS.mapNotNull { (table, label) ->
        val n = counts.rows[table] ?: 0
        if (n > 0) "$label — $n" else null
    }.joinToString(" · ").ifEmpty { "данных нет" }

private fun messageOf(e: Exception): String =
    e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
