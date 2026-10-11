package com.example.alcohol.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Запоминает текст последнего необработанного сбоя в файл.
 * При следующем запуске приложение показывает его, чтобы текст можно было скопировать.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                app.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(error.stackTraceToString().take(20_000).toByteArray())
                }
            } catch (_: Exception) {
                // Не получилось записать — падаем как обычно.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = try {
        context.openFileInput(FILE).use { String(it.readBytes()) }
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        context.deleteFile(FILE)
    }
}

/** Показывает текст сбоя из прошлого запуска, если он был. */
@Composable
fun CrashDialog() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(CrashReport.read(context)) }

    report?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("В прошлый раз приложение упало") },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                    Text("Скопировать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashReport.clear(context)
                    report = null
                }) { Text("Закрыть") }
            },
        )
    }
}
