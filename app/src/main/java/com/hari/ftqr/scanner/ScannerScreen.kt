package com.hari.ftqr.scanner

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
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

private const val READING_IMAGE = "Reading image"

@Composable
fun ScannerScreen(
    onScanned: (rawValue: String, format: Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGenerator: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberScanHaptics()
    val currentOnScanned by rememberUpdatedState(onScanned)

    val hasCameraHardware = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var hasCameraPermission by remember { mutableStateOf(context.isCameraPermissionGranted()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val cameraActive = hasCameraHardware && hasCameraPermission

    val screenDisposed = remember { AtomicBoolean(false) }
    val scanHandled = remember { AtomicBoolean(false) }
    val torchSynced = remember { AtomicBoolean(false) }
    val deliverScan: (String, Int) -> Unit = remember(haptics) {
        { rawValue: String, format: Int ->
            haptics.perform()
            currentOnScanned(rawValue, format)
        }
    }

    DisposableEffect(Unit) {
        onDispose { screenDisposed.set(true) }
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

    var desiredTorchOn by rememberSaveable { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var isDecodingImage by remember { mutableStateOf(false) }
    var showNoCodeFound by remember { mutableStateOf(false) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var topBarBottom by remember { mutableFloatStateOf(0f) }
    var controlsBounds by remember { mutableStateOf(Rect.Zero) }

    val safeLeft = WindowInsets.safeDrawing.getLeft(density, layoutDirection).toFloat()
    val safeRight = WindowInsets.safeDrawing.getRight(density, layoutDirection).toFloat()
    val safeBottom = WindowInsets.safeDrawing.getBottom(density).toFloat()

    val freeArea = remember(containerSize, topBarBottom, controlsBounds, safeLeft, safeRight, safeBottom) {
        computeFreeArea(containerSize, topBarBottom, controlsBounds, safeLeft, safeRight, safeBottom)
    }
    val viewfinderLayout = remember(freeArea, density) {
        freeArea?.let { with(density) { computeViewfinderLayout(it) } }
    }
    val latestViewfinderLayout by rememberUpdatedState(viewfinderLayout)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        hasCameraPermission = granted
        val activity = context.findActivity()
        permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && !screenDisposed.get()) {
            isDecodingImage = true
            scope.launch {
                val result = try {
                    decodeBarcodeFromImage(context, uri, scanner)
                } finally {
                    isDecodingImage = false
                }
                if (screenDisposed.get()) return@launch
                when (result) {
                    is GalleryScanResult.Found -> {
                        if (scanHandled.compareAndSet(false, true)) {
                            deliverScan(result.rawValue, result.format)
                        }
                    }
                    GalleryScanResult.NotFound -> showNoCodeFound = true
                    is GalleryScanResult.Failed -> snackbarHostState.showSnackbar(result.message)
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
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val granted = context.isCameraPermissionGranted()
                    hasCameraPermission = granted
                    if (granted) permanentlyDenied = false
                }
                Lifecycle.Event.ON_STOP -> torchSynced.set(false)
                else -> Unit
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
        val analyzerActive = AtomicBoolean(cameraActive)
        if (cameraActive) {
            torchSynced.set(false)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            fun onAnalyzerResult(result: MlKitAnalyzer.Result) {
                if (!analyzerActive.get() || screenDisposed.get()) return
                if (torchSynced.compareAndSet(false, true)) {
                    val flash = cameraController.cameraInfo?.hasFlashUnit() == true
                    hasFlashUnit = flash
                    if (flash && desiredTorchOn) cameraController.enableTorch(true)
                }
                val cutout = latestViewfinderLayout?.cutout ?: return
                val barcode = pickBarcodeInCutout(result.getValue(scanner).orEmpty(), cutout) ?: return
                val rawValue = barcode.rawValue ?: return
                if (scanHandled.compareAndSet(false, true)) {
                    deliverScan(rawValue, barcode.format)
                }
            }
            cameraController.setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(
                    listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    mainExecutor,
                ) { result -> onAnalyzerResult(result) },
            )
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            analyzerActive.set(false)
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it },
    ) {
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp

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
            ViewfinderOverlay(layout = viewfinderLayout, modifier = Modifier.fillMaxSize())
        } else {
            val panelArea = freeArea
            val panelModifier = if (panelArea != null && containerSize != IntSize.Zero) {
                with(density) {
                    Modifier.absolutePadding(
                        left = panelArea.left.toDp(),
                        top = panelArea.top.toDp(),
                        right = (containerSize.width - panelArea.right).coerceAtLeast(0f).toDp(),
                        bottom = (containerSize.height - panelArea.bottom).coerceAtLeast(0f).toDp(),
                    )
                }
            } else {
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            }
            CameraPermissionPanel(
                hasCameraHardware = hasCameraHardware,
                permanentlyDenied = permanentlyDenied,
                onGrantPermission = {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = { context.openAppSettings() },
                modifier = Modifier
                    .fillMaxSize()
                    .then(panelModifier),
            )
        }

        ScannerTopBar(
            showTorch = cameraActive && hasFlashUnit,
            torchOn = torchOn,
            onToggleTorch = {
                desiredTorchOn = !torchOn
                cameraController.enableTorch(desiredTorchOn)
            },
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    topBarBottom = coordinates.boundsInParent().bottom
                }
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    controlsBounds = coordinates.boundsInParent()
                }
                .align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .then(if (landscape) Modifier.width(320.dp) else Modifier.fillMaxWidth())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

        val snackbarBottom = with(density) {
            if (containerSize != IntSize.Zero && controlsBounds != Rect.Zero) {
                (containerSize.height - controlsBounds.top).coerceAtLeast(0f).toDp()
            } else {
                0.dp
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottom)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        )

        if (showNoCodeFound) {
            NoCodeFoundDialog(
                onTryAnother = {
                    showNoCodeFound = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDismiss = { showNoCodeFound = false },
            )
        }
    }
}

private fun computeFreeArea(
    containerSize: IntSize,
    topBarBottom: Float,
    controlsBounds: Rect,
    safeLeft: Float,
    safeRight: Float,
    safeBottom: Float,
): Rect? {
    if (containerSize == IntSize.Zero || controlsBounds == Rect.Zero) return null
    val width = containerSize.width.toFloat()
    val height = containerSize.height.toFloat()
    val controlsSpanWidth = controlsBounds.width >= width * 0.9f
    val area = when {
        controlsSpanWidth -> Rect(safeLeft, topBarBottom, width - safeRight, controlsBounds.top)
        controlsBounds.center.x >= width / 2f -> Rect(safeLeft, topBarBottom, controlsBounds.left, height - safeBottom)
        else -> Rect(controlsBounds.right, topBarBottom, width - safeRight, height - safeBottom)
    }
    return area.takeIf { it.width > 0f && it.height > 0f }
}

private fun pickBarcodeInCutout(barcodes: List<Barcode>, cutout: Rect): Barcode? {
    val cutoutCenter = cutout.center
    var best: Barcode? = null
    var bestDistance = Float.MAX_VALUE
    for (barcode in barcodes) {
        if (barcode.rawValue.isNullOrEmpty()) continue
        val box = barcode.boundingBox ?: continue
        val center = Offset(box.exactCenterX(), box.exactCenterY())
        if (!cutout.contains(center)) continue
        val distance = (center - cutoutCenter).getDistanceSquared()
        if (distance < bestDistance) {
            bestDistance = distance
            best = barcode
        }
    }
    return best
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
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = "Zoom" },
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
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = !busy,
            role = Role.Button,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .indication(interactionSource, ripple(color = Color.White)),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .clearAndSetSemantics { stateDescription = READING_IMAGE },
                )
            } else {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White)
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
