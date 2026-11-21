package com.hari.ftqr.scanner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(
    onScanned: (rawValue: String, format: Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGenerator: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberScanHaptics()
    val currentOnScanned by rememberUpdatedState(onScanned)

    val hasCameraHardware = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var hasCameraPermission by remember { mutableStateOf(context.isCameraPermissionGranted()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val cameraActive = hasCameraHardware && hasCameraPermission

    val scanHandled = remember { AtomicBoolean(false) }
    val deliverScan: (String, Int) -> Unit = remember(haptics) {
        { rawValue: String, format: Int ->
            haptics.perform()
            currentOnScanned(rawValue, format)
        }
    }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build(),
        )
    }
    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            isPinchToZoomEnabled = true
            isTapToFocusEnabled = true
        }
    }

    var torchOn by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var isDecodingImage by remember { mutableStateOf(false) }
    var showNoCodeFound by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isDecodingImage = true
            scope.launch {
                val result = decodeBarcodeFromImage(context, uri, scanner)
                isDecodingImage = false
                when (result) {
                    is GalleryScanResult.Found -> {
                        scanHandled.set(true)
                        deliverScan(result.rawValue, result.format)
                    }
                    GalleryScanResult.NotFound -> showNoCodeFound = true
                    is GalleryScanResult.Failed ->
                        snackbarHostState.showSnackbar("Couldn't read that image")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasCameraHardware && !hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = context.isCameraPermissionGranted()
                scanHandled.set(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(cameraController, lifecycleOwner) {
        val torchObserver = Observer<Int> { state ->
            torchOn = state == TorchState.ON
            hasFlashUnit = cameraController.cameraInfo?.hasFlashUnit() == true
        }
        val zoomObserver = Observer<ZoomState> { state ->
            linearZoom = state.linearZoom
            hasFlashUnit = cameraController.cameraInfo?.hasFlashUnit() == true
        }
        cameraController.torchState.observe(lifecycleOwner, torchObserver)
        cameraController.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            cameraController.torchState.removeObserver(torchObserver)
            cameraController.zoomState.removeObserver(zoomObserver)
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, cameraActive, scanner) {
        if (cameraActive) {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            cameraController.setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(
                    listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    mainExecutor,
                ) { result ->
                    val barcode = result.getValue(scanner)?.firstOrNull { !it.rawValue.isNullOrEmpty() }
                    val rawValue = barcode?.rawValue
                    if (barcode != null && rawValue != null && scanHandled.compareAndSet(false, true)) {
                        deliverScan(rawValue, barcode.format)
                    }
                },
            )
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraActive) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cameraController
                    }
                },
                onRelease = { previewView -> previewView.controller = null },
                modifier = Modifier.fillMaxSize(),
            )
            ViewfinderOverlay(modifier = Modifier.fillMaxSize())
        } else {
            CameraPermissionPanel(
                hasCameraHardware = hasCameraHardware,
                onGrantPermission = {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = { context.openAppSettings() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
            )
        }

        ScannerTopBar(
            showTorch = cameraActive && hasFlashUnit,
            torchOn = torchOn,
            onToggleTorch = { cameraController.enableTorch(!torchOn) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SnackbarHost(hostState = snackbarHostState)
            if (showNoCodeFound) {
                NoCodeFoundDialog(
                    onTryAnother = {
                        showNoCodeFound = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDismiss = { showNoCodeFound = false },
                )
            }
            if (cameraActive) {
                ZoomControl(
                    value = linearZoom,
                    onValueChange = { zoom ->
                        linearZoom = zoom
                        cameraController.setLinearZoom(zoom)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                ScannerControlButton(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "Gallery",
                    busy = isDecodingImage,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                .build(),
                        )
                    },
                )
                ScannerControlButton(
                    icon = Icons.Outlined.QrCode2,
                    label = "Create QR",
                    onClick = onOpenGenerator,
                )
                ScannerControlButton(
                    icon = Icons.Outlined.History,
                    label = "History",
                    onClick = onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun ScannerTopBar(
    showTorch: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FTQR",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showTorch) {
            IconButton(
                onClick = onToggleTorch,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (torchOn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                    contentColor = if (torchOn) MaterialTheme.colorScheme.onPrimary else Color.White,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                )
            }
        }
    }
}

@Composable
private fun ZoomControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ZoomOut,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ZoomIn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ScannerControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            enabled = !busy,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(imageVector = icon, contentDescription = label)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

private fun Context.isCameraPermissionGranted(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (notFound: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
