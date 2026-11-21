package com.hari.ftqr.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hari.ftqr.R

@Composable
fun NoCodeFoundDialog(onTryAnother: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = painterResource(R.drawable.illus_no_code_found),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
        },
        title = { Text("No code found", textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "We couldn't find a QR code or barcode in that image. Try a sharper picture, or crop closer to the code.",
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = onTryAnother) { Text("Pick another") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
