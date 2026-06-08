package com.markflow.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.markflow.app.domain.model.FlashMode
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markflow.app.cv.PageChangeDetector
import com.markflow.app.domain.model.MarkFeedItem
import com.markflow.app.ui.components.ConfidenceBadge
import com.markflow.app.ui.components.MarkChip
import com.markflow.app.ui.components.MarkChipSize
import com.markflow.app.ui.theme.*
import java.util.concurrent.Executors
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import android.graphics.PointF

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    sessionId: Long,
    copyId: Long,
    onFinish: (Long, Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val markFeed by viewModel.markFeed.collectAsStateWithLifecycle()
    val runningTotal by viewModel.runningTotal.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val statusMessages by viewModel.statusMessages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val pageDetectionState by viewModel.pageDetectionState.collectAsStateWithLifecycle()
    val lastScanQuality by viewModel.lastScanQuality.collectAsStateWithLifecycle()
    val showFinalizeProgress by viewModel.showFinalizeProgress.collectAsStateWithLifecycle()
    val finalizeProgressMessage by viewModel.finalizeProgressMessage.collectAsStateWithLifecycle()

    val duplicateDialogState by viewModel.duplicateDialog.collectAsStateWithLifecycle()
    val missingPageDialogState by viewModel.missingPageDialog.collectAsStateWithLifecycle()
    val cropState by viewModel.cropState.collectAsStateWithLifecycle()

    val liveCorners by viewModel.liveCorners.collectAsStateWithLifecycle()
    val liveCornersSize by viewModel.liveCornersSize.collectAsStateWithLifecycle()
    val activeReviewPageIndex by viewModel.activeReviewPageIndex.collectAsStateWithLifecycle()
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()

    var showDetectionPanel by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var isDark by remember { mutableStateOf(false) }
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    var tiltAngle by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    tiltX = x
                    tiltY = y
                    val g = Math.sqrt((x * x + y * y + z * z).toDouble())
                    if (g > 0.0) {
                        val angle = Math.toDegrees(Math.acos(Math.abs(z) / g)).toFloat()
                        tiltAngle = angle
                        viewModel.updateTiltAngle(angle)
                    }
                } else if (event.sensor.type == Sensor.TYPE_LIGHT) {
                    val lux = event.values[0]
                    if (lux < 15.0f) {
                        isDark = true
                    } else if (lux > 25.0f) {
                        isDark = false
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        if (lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(sessionId, copyId) {
        viewModel.initialize(sessionId, copyId)
    }

    // ── Duplicate Page Alert (Feature 6) ──
    duplicateDialogState?.let { data ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Possible Duplicate Page Detected") },
            text = {
                Column {
                    Text("This page matches a previously scanned page with high similarity (${(data.confidence * 100).toInt()}%).")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("What would you like to do?")
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    TextButton(onClick = { viewModel.keepDuplicatePage() }) {
                        Text("Keep Page")
                    }
                    TextButton(onClick = { viewModel.mergeDuplicatePage(data.pageId, data.duplicateOfPageId) }) {
                        Text("Merge With Existing")
                    }
                    TextButton(
                        onClick = { viewModel.ignoreDuplicatePage(data.pageId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ignore Page")
                    }
                }
            }
        )
    }

    // ── Missing Page Alert (Feature 7) ──
    missingPageDialogState?.let { data ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Possible Missing Page") },
            text = {
                Column {
                    Text("Expected Page ${data.expectedPageNumber}, but detected Page ${data.detectedPageNumber} in sequence.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to scan again or continue anyway?")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.continueMissingPage() }) {
                        Text("Continue Anyway")
                    }
                    TextButton(
                        onClick = { viewModel.scanAgainMissingPage(data.pageId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Scan Again")
                    }
                }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish Scanning") },
            text = {
                Column {
                    Text("Total marks detected: ${runningTotal.toInt()}")
                    Text("Pages scanned: $pageCount")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to finish this copy or scan another?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.finishScanning(onFinish)
                }) {
                    Text("Finish")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showFinishDialog = false
                        viewModel.startNextCopy { }
                    }) {
                        Text("Next Copy")
                    }
                    TextButton(onClick = { showFinishDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scanning Copy #${scanState.currentPageNumber.let { if (it > 0) "" else "" }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Page $pageCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Auto mode badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MarkFlowGreen.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "AUTO",
                            color = MarkFlowGreen,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    // Flash Mode Toggle Button
                    IconButton(onClick = {
                        flashMode = when (flashMode) {
                            FlashMode.OFF -> FlashMode.ON
                            FlashMode.ON -> FlashMode.AUTO
                            FlashMode.AUTO -> FlashMode.OFF
                        }
                    }) {
                        val icon = when (flashMode) {
                            FlashMode.OFF -> Icons.Filled.FlashOff
                            FlashMode.ON -> Icons.Filled.FlashOn
                            FlashMode.AUTO -> Icons.Filled.FlashAuto
                        }
                        val tint = if (flashMode == FlashMode.ON) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                        Icon(
                            imageVector = icon,
                            contentDescription = "Flash mode",
                            tint = tint
                        )
                    }
                    IconButton(onClick = { showDetectionPanel = !showDetectionPanel }) {
                        Icon(Icons.Filled.Tune, "Detection panel")
                    }
                }
            }

            // ── Running Total Bar ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Total",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${runningTotal.toInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " Marks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            // ── Camera Preview ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreviewView(
                    flashMode = flashMode,
                    isDark = isDark,
                    onFrameAvailable = { bitmap ->
                        viewModel.processFrame(bitmap)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ── Digital Spirit Level ──
                SpiritLevel(
                    tiltX = tiltX,
                    tiltY = tiltY,
                    tiltAngle = tiltAngle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )

                // ── Tilt Angle Warning Banner ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = tiltAngle > 10.0f,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3CD)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Hold phone flat to capture (Tilt: ${tiltAngle.toInt()}°)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF856404)
                            )
                        }
                    }
                }

                // ── Live Boundary Outline Overlay ──
                liveCorners?.let { corners ->
                    val size = liveCornersSize
                    if (size != null && size.width > 0 && size.height > 0) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val scaleX = this.size.width / size.width
                            val scaleY = this.size.height / size.height
                            
                            val tl = Offset(corners.topLeft.x * scaleX, corners.topLeft.y * scaleY)
                            val tr = Offset(corners.topRight.x * scaleX, corners.topRight.y * scaleY)
                            val bl = Offset(corners.bottomLeft.x * scaleX, corners.bottomLeft.y * scaleY)
                            val br = Offset(corners.bottomRight.x * scaleX, corners.bottomRight.y * scaleY)

                            val path = Path().apply {
                                moveTo(tl.x, tl.y)
                                lineTo(tr.x, tr.y)
                                lineTo(br.x, br.y)
                                lineTo(bl.x, bl.y)
                                close()
                            }

                            val strokeColor = if (corners.isHighConfidence) Color.Green else Color.Yellow
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = 3.dp.toPx())
                            )
                            
                            val indicatorRadius = 6.dp.toPx()
                            listOf(tl, tr, bl, br).forEach { center ->
                                drawCircle(
                                    color = strokeColor,
                                    radius = indicatorRadius,
                                    center = center
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = indicatorRadius / 2f,
                                    center = center
                                )
                            }
                        }
                    }
                }

                // ── Floating Stats Panel ──
                FloatingStatsPanel(
                    total = runningTotal.toInt(),
                    marks = markFeed.size,
                    pages = pageCount,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )

                // ── No Copy Detected Warning ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = pageCount == 0,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No copy detected yet. Align sheet or tap 'Capture Page'.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // ── Page Detection Status ──
                PageStatusIndicator(
                    state = pageDetectionState,
                    isProcessing = isProcessing,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )

                // ── Scan Quality Warning (Feature 13) ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = lastScanQuality == "Poor",
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Poor Scan Quality Detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Please realign the page or reduce shadows, and rescan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // ── Detection Panel (right side) ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDetectionPanel,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    DetectionSidePanel(
                        markFeed = markFeed,
                        runningTotal = runningTotal,
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                    )
                }
            }

            // ── Captured Pages Horizontal Thumbnails Strip ──
            if (capturedPages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(capturedPages) { index, page ->
                        val isSelected = activeReviewPageIndex == index
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                                .clickable(enabled = !page.isProcessing) { viewModel.selectReviewPage(index) }
                        ) {
                            val previewBmpState = produceState<Bitmap?>(initialValue = null, page.rawImagePath, page.rotationDegrees, page.filterMode, page.corners) {
                                value = viewModel.getProcessedPreview(page, targetWidth = 100)
                            }
                            
                            previewBmpState.value?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            if (page.isProcessing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            
                            val dotColor = when (page.quality.rating) {
                                "Excellent", "Good" -> MarkFlowGreen
                                "Fair" -> Color.Yellow
                                else -> MaterialTheme.colorScheme.error
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .align(Alignment.TopStart)
                                    .offset(4.dp, 4.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── Status Messages & Bottom Bar ──
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp)
                ) {
                    // Status messages
                    statusMessages.takeLast(3).forEach { message ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = ConfidenceHigh,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Buttons (Capture & Finish)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Capture Button
                        Button(
                            onClick = { viewModel.manualCapture() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture Page", fontWeight = FontWeight.SemiBold)
                        }

                        // Finish button
                        Button(
                            onClick = { showFinishDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusError
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Finish Scanning", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        activeReviewPageIndex?.let { index ->
            val page = capturedPages.getOrNull(index)
            if (page != null) {
                PageReviewDialog(
                    page = page,
                    pageIndex = index,
                    totalPages = capturedPages.size,
                    onRotate = { viewModel.rotateStagingPage(index) },
                    onRecrop = { viewModel.startCropAdjustment(index) },
                    onFilterChange = { filter -> viewModel.updateStagingPageFilter(index, filter) },
                    onDelete = { viewModel.deleteStagingPage(index) },
                    onRescan = { viewModel.retakeStagingPage(index) },
                    onClose = { viewModel.selectReviewPage(null) },
                    onPrev = { if (index > 0) viewModel.selectReviewPage(index - 1) },
                    onNext = { if (index < capturedPages.lastIndex) viewModel.selectReviewPage(index + 1) },
                    onSave = {
                        viewModel.selectReviewPage(null)
                        viewModel.finishScanning(onFinish)
                    },
                    getProcessedPreview = { p -> viewModel.getProcessedPreview(p) }
                )
            }
        }

        cropState?.let { state ->
            ManualCropOverlay(
                rawBitmap = state.rawBitmap,
                initialCorners = state.corners,
                onConfirm = { corners ->
                    viewModel.confirmCrop(corners)
                },
                onDiscard = {
                    viewModel.discardCrop()
                }
            )
        }

        if (showFinalizeProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .width(280.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Finalizing Copy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = finalizeProgressMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageReviewDialog(
    page: ScanViewModel.StagingPage,
    pageIndex: Int,
    totalPages: Int,
    onRotate: () -> Unit,
    onRecrop: () -> Unit,
    onFilterChange: (ScanViewModel.FilterMode) -> Unit,
    onDelete: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    getProcessedPreview: suspend (ScanViewModel.StagingPage) -> Bitmap
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = { Text("Review Staging Page ${pageIndex + 1} of $totalPages") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, "Rescan Page")
                    }
                    IconButton(onClick = onDelete, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, "Delete Page")
                    }
                }
            )
            
            // Image Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val previewState = produceState<Bitmap?>(initialValue = null, page.rawImagePath, page.rotationDegrees, page.filterMode, page.corners) {
                    value = getProcessedPreview(page)
                }
                
                if (previewState.value != null) {
                    Image(
                        bitmap = previewState.value!!.asImageBitmap(),
                        contentDescription = "Staging Page Preview",
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                // Navigation arrows overlay
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrev,
                        enabled = pageIndex > 0,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.ChevronLeft, "Previous")
                    }
                    if (pageIndex < totalPages - 1) {
                        IconButton(
                            onClick = onNext,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.ChevronRight, "Next")
                        }
                    } else {
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(containerColor = MarkFlowGreen, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quality & Metadata Display
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Scan Quality: ${page.quality.rating} (Score: ${page.quality.score}%)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (page.quality.rating) {
                            "Excellent", "Good" -> MarkFlowGreen
                            "Fair" -> Color.Yellow
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Skew: ${"%.1f".format(page.quality.skewAngle)}° | Shadow: ${"%.1f".format(page.quality.shadowCoverage * 100)}% | Blur: ${page.quality.blurScore}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions & Filter Panel
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Filter selection
                    Text(
                        text = "Select Document Filter",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanViewModel.FilterMode.values().forEach { mode ->
                            val label = when (mode) {
                                ScanViewModel.FilterMode.ORIGINAL -> "Original"
                                ScanViewModel.FilterMode.ENHANCED -> "Enhanced"
                                ScanViewModel.FilterMode.EXAM_MODE -> "Exam Mode"
                            }
                            FilterChip(
                                selected = page.filterMode == mode,
                                onClick = { onFilterChange(mode) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manipulation actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRotate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.RotateRight, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotate")
                        }

                        Button(
                            onClick = onRecrop,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Crop, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Crop")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewView(
    flashMode: FlashMode,
    isDark: Boolean,
    onFrameAvailable: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraReference by remember { mutableStateOf<Camera?>(null) }

    val shouldTorchBeOn = when (flashMode) {
        FlashMode.ON -> true
        FlashMode.OFF -> false
        FlashMode.AUTO -> isDark
    }

    LaunchedEffect(cameraReference, shouldTorchBeOn) {
        try {
            cameraReference?.cameraControl?.enableTorch(shouldTorchBeOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = modifier,
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { analysis ->
                            var lastAnalysisTime = 0L
                            analysis.setAnalyzer(executor) { imageProxy ->
                                val now = System.currentTimeMillis()
                                if (now - lastAnalysisTime >= 200) { // 5 fps for analysis
                                    lastAnalysisTime = now
                                    try {
                                        val rotation = imageProxy.imageInfo.rotationDegrees
                                        var bitmap = imageProxy.toBitmap()
                                        if (rotation != 0) {
                                            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                            bitmap.recycle()
                                            bitmap = rotated
                                        }
                                        onFrameAvailable(bitmap)
                                    } catch (e: Exception) {
                                        // Ignore frame processing errors
                                    }
                                }
                                imageProxy.close()
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    cameraReference = camera
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun FloatingStatsPanel(
    total: Int,
    marks: Int,
    pages: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.7f),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Total: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "Marks: $marks",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Pages: $pages",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PageStatusIndicator(
    state: PageChangeDetector.PageState,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val (text, color) = when {
        isProcessing -> "Processing..." to StatusWarning
        state == PageChangeDetector.PageState.READY_TO_CAPTURE -> "Auto Capturing..." to ConfidenceHigh
        state == PageChangeDetector.PageState.STABILIZING -> "Stabilizing..." to StatusWarning
        state == PageChangeDetector.PageState.CHANGING -> "Page turning..." to StatusWarning
        else -> "Stable" to ConfidenceHigh
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DetectionSidePanel(
    markFeed: List<MarkFeedItem>,
    runningTotal: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "DETECTED MARKS",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(markFeed.reversed()) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MarkChip(
                            value = item.displayValue,
                            size = MarkChipSize.SMALL
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Page ${item.pageNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Running Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "${runningTotal.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OverlayConfirmed
                )
            }
        }
    }
}

enum class HandleType {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

@Composable
fun ManualCropOverlay(
    rawBitmap: Bitmap,
    initialCorners: com.markflow.app.cv.ImageProcessor.CornerPoints,
    onConfirm: (com.markflow.app.cv.ImageProcessor.CornerPoints) -> Unit,
    onDiscard: () -> Unit
) {
    val bitmapWidth = rawBitmap.width.toFloat()
    val bitmapHeight = rawBitmap.height.toFloat()

    // Normalized points between 0.0f and 1.0f
    var normTL by remember { mutableStateOf(Offset(initialCorners.topLeft.x / bitmapWidth, initialCorners.topLeft.y / bitmapHeight)) }
    var normTR by remember { mutableStateOf(Offset(initialCorners.topRight.x / bitmapWidth, initialCorners.topRight.y / bitmapHeight)) }
    var normBL by remember { mutableStateOf(Offset(initialCorners.bottomLeft.x / bitmapWidth, initialCorners.bottomLeft.y / bitmapHeight)) }
    var normBR by remember { mutableStateOf(Offset(initialCorners.bottomRight.x / bitmapWidth, initialCorners.bottomRight.y / bitmapHeight)) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val scale = minOf(
            containerWidth.value / bitmapWidth,
            containerHeight.value / bitmapHeight
        )

        val canvasWidthDp = (bitmapWidth * scale).dp
        val canvasHeightDp = (bitmapHeight * scale).dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Title Bar
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adjust Page Boundaries",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Interactive crop canvas area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(canvasWidthDp, canvasHeightDp)
                ) {
                    Image(
                        bitmap = rawBitmap.asImageBitmap(),
                        contentDescription = "Document preview",
                        modifier = Modifier.fillMaxSize()
                    )

                    var activeHandle by remember { mutableStateOf<HandleType?>(null) }

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val tl = Offset(normTL.x * size.width, normTL.y * size.height)
                                        val tr = Offset(normTR.x * size.width, normTR.y * size.height)
                                        val bl = Offset(normBL.x * size.width, normBL.y * size.height)
                                        val br = Offset(normBR.x * size.width, normBR.y * size.height)

                                        val limit = 40.dp.toPx()

                                        val distTL = (offset - tl).getDistance()
                                        val distTR = (offset - tr).getDistance()
                                        val distBL = (offset - bl).getDistance()
                                        val distBR = (offset - br).getDistance()

                                        activeHandle = when {
                                            distTL < limit && distTL < distTR && distTL < distBL && distTL < distBR -> HandleType.TOP_LEFT
                                            distTR < limit && distTR < distBL && distTR < distBR -> HandleType.TOP_RIGHT
                                            distBL < limit && distBL < distBR -> HandleType.BOTTOM_LEFT
                                            distBR < limit -> HandleType.BOTTOM_RIGHT
                                            else -> null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val active = activeHandle ?: return@detectDragGestures
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()

                                        when (active) {
                                            HandleType.TOP_LEFT -> {
                                                normTL = Offset(
                                                    (normTL.x + dragAmount.x / w).coerceIn(0f, 1f),
                                                    (normTL.y + dragAmount.y / h).coerceIn(0f, 1f)
                                                )
                                            }
                                            HandleType.TOP_RIGHT -> {
                                                normTR = Offset(
                                                    (normTR.x + dragAmount.x / w).coerceIn(0f, 1f),
                                                    (normTR.y + dragAmount.y / h).coerceIn(0f, 1f)
                                                )
                                            }
                                            HandleType.BOTTOM_LEFT -> {
                                                normBL = Offset(
                                                    (normBL.x + dragAmount.x / w).coerceIn(0f, 1f),
                                                    (normBL.y + dragAmount.y / h).coerceIn(0f, 1f)
                                                )
                                            }
                                            HandleType.BOTTOM_RIGHT -> {
                                                normBR = Offset(
                                                    (normBR.x + dragAmount.x / w).coerceIn(0f, 1f),
                                                    (normBR.y + dragAmount.y / h).coerceIn(0f, 1f)
                                                )
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandle = null
                                    },
                                    onDragCancel = {
                                        activeHandle = null
                                    }
                                )
                            }
                    ) {
                        val tl = Offset(normTL.x * size.width, normTL.y * size.height)
                        val tr = Offset(normTR.x * size.width, normTR.y * size.height)
                        val bl = Offset(normBL.x * size.width, normBL.y * size.height)
                        val br = Offset(normBR.x * size.width, normBR.y * size.height)

                        val path = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }

                        // Draw dark translucent overlay outside the selected region
                        clipPath(path, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.6f))
                        }

                        // Draw lines connecting the handles
                        drawPath(
                            path = path,
                            color = Color.Green,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Draw the 4 interactive handles
                        val handleRadius = 16.dp.toPx()
                        listOf(tl, tr, bl, br).forEach { center ->
                            drawCircle(
                                color = Color.Green,
                                radius = handleRadius,
                                center = center
                            )
                            drawCircle(
                                color = Color.White,
                                radius = handleRadius / 2f,
                                center = center
                            )
                        }
                    }
                }
            }

            // Bottom Buttons
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Discard Scan", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val finalTL = PointF(normTL.x * bitmapWidth, normTL.y * bitmapHeight)
                            val finalTR = PointF(normTR.x * bitmapWidth, normTR.y * bitmapHeight)
                            val finalBL = PointF(normBL.x * bitmapWidth, normBL.y * bitmapHeight)
                            val finalBR = PointF(normBR.x * bitmapWidth, normBR.y * bitmapHeight)
                            onConfirm(
                                com.markflow.app.cv.ImageProcessor.CornerPoints(
                                    topLeft = finalTL,
                                    topRight = finalTR,
                                    bottomLeft = finalBL,
                                    bottomRight = finalBR,
                                    isHighConfidence = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Green,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm Crop", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SpiritLevel(
    tiltX: Float,
    tiltY: Float,
    tiltAngle: Float,
    modifier: Modifier = Modifier
) {
    val isFlat = tiltAngle <= 10.0f
    val color = if (isFlat) Color(0xFF4CAF50) else Color(0xFFF44336)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .border(1.5.dp, color.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(color.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.3f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
                
                drawCircle(color.copy(alpha = 0.2f), radius = 8.dp.toPx(), center = Offset(cx, cy))
                
                val maxDisp = 20.dp.toPx()
                val dispX = (tiltX / 9.8f * maxDisp).coerceIn(-maxDisp, maxDisp)
                val dispY = (-tiltY / 9.8f * maxDisp).coerceIn(-maxDisp, maxDisp)
                
                drawCircle(color, radius = 4.dp.toPx(), center = Offset(cx + dispX, cy + dispY))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${tiltAngle.toInt()}°",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}
