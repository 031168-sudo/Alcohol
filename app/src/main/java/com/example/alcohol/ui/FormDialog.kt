package com.example.alcohol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Замена AlertDialog для форм с вводом. Окно сжимается над клавиатурой,
 * а содержимое прокручивается, поэтому поля и кнопки «Сохранить» не перекрываются.
 */
@Composable
fun FormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Высота окна диалога не ограничена, поэтому максимум берём от высоты экрана.
        val textMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.55f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    title()
                    Spacer(Modifier.height(16.dp))
                    // Прокрутка внутри text получает явную высоту: без неё окно диалога измеряется без границ и падает.
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = textMaxHeight)) {
                        text()
                    }
                    Spacer(Modifier.height(24.dp))
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
    }
}
