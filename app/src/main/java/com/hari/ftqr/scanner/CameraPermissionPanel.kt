package com.hari.ftqr.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hari.ftqr.R

@Composable
fun CameraPermissionPanel(
    hasCameraHardware: Boolean,
    permanentlyDenied: Boolean,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PanelCard(
                hasCameraHardware = hasCameraHardware,
                permanentlyDenied = permanentlyDenied,
                onGrantPermission = onGrantPermission,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun PanelCard(
    hasCameraHardware: Boolean,
    permanentlyDenied: Boolean,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasCameraHardware) {
                Image(
                    painter = painterResource(R.drawable.illus_camera_access),
                    contentDescription = null,
                    modifier = Modifier.size(160.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.NoPhotography,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = if (hasCameraHardware) "Camera access needed" else "No camera available",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (hasCameraHardware) {
                    "FTQR uses the camera only to read QR codes and barcodes live. Nothing is recorded or uploaded. You can still scan codes from images in your gallery without it."
                } else {
                    "This device has no camera, but you can still scan QR codes and barcodes from images in your gallery."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (hasCameraHardware && permanentlyDenied) {
                Text(
                    text = "Camera access is turned off. Enable it in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (hasCameraHardware) {
                Spacer(modifier = Modifier.height(4.dp))
                if (permanentlyDenied) {
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open settings")
                    }
                } else {
                    Button(onClick = onGrantPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant permission")
                    }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
}
