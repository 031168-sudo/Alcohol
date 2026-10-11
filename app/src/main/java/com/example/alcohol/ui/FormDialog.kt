package com.example.alcohol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Открытая форма: токен нужен, чтобы заменять и убирать её, а content — что рисовать. */
class FormEntry(val token: Any, val content: @Composable () -> Unit)

/**
 * Формы рисуются на весь экран внутри окна приложения, а не отдельным окном.
 * Так клавиатура сжимает форму, поля и кнопки «Сохранить» остаются доступны.
 */
class FormHost {
    val entries = mutableStateListOf<FormEntry>()

    fun upsert(token: Any, content: @Composable () -> Unit) {
        val entry = FormEntry(token, content)
        val index = entries.indexOfFirst { it.token == token }
        if (index >= 0) entries[index] = entry else entries.add(entry)
    }

    fun remove(token: Any) {
        entries.removeAll { it.token == token }
    }
}

val LocalFormHost = staticCompositionLocalOf<FormHost?> { null }

/**
 * Форма с вводом: заголовок, содержимое и кнопки внизу.
 * Вызывается так же, как AlertDialog, но открывается поверх экрана.
 */
@Composable
fun FormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val host = LocalFormHost.current
    val token = remember { Any() }
    val content: @Composable () -> Unit = {
        FormPage(onDismissRequest, title, text, confirmButton, dismissButton)
    }
    if (host != null) {
        SideEffect { host.upsert(token, content) }
        DisposableEffect(token) {
            onDispose { host.remove(token) }
        }
    }
}

@Composable
private fun FormPage(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    BackHandler(onBack = onDismissRequest)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        ) {
            title()
            Spacer(Modifier.height(12.dp))
            // Занимает всё свободное место: прокрутка внутри text получает ограниченную высоту.
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                text()
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissButton != null) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                }
                confirmButton()
            }
        }
    }
}
