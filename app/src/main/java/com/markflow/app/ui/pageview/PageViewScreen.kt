package com.markflow.app.ui.pageview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.markflow.app.domain.model.DetectedMark
import com.markflow.app.domain.model.MarkStatus
import com.markflow.app.ui.components.ConfidenceBadge
import com.markflow.app.ui.components.MarkChip
import com.markflow.app.ui.components.MarkChipSize
import com.markflow.app.util.toMarksString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewScreen(
    pageId: Long,
    onBack: () -> Unit,
    viewModel: PageViewViewModel = hiltViewModel()
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()

    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var isDrawMode by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    val currentStroke = remember { mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
    var imageVersion by remember { mutableStateOf(0) }

    // Dialog state for adding manual marks
    var showAddDialog by remember { mutableStateOf(false) }
    var addMarkCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var addValueText by remember { mutableStateOf("") }

    // Dialog state for editing marks
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedMark by remember { mutableStateOf<DetectedMark?>(null) }
    var editValueText by remember { mutableStateOf("") }

    // ── Add Manual Mark Dialog ──
    if (showAddDialog && addMarkCoords != null) {
        val coords = addMarkCoords!!
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Manual Mark") },
            text = {
                Column {
                    Text("Location: (${coords.first}, ${coords.second})", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addValueText,
                        onValueChange = { addValueText = it },
                        label = { Text("Enter Value (e.g. 5, 4.5, 3/5)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = addValueText.toDoubleOrNull()
                    if (parsed != null) {
                        viewModel.addManualMark(parsed, addValueText, coords.first, coords.second)
                        showAddDialog = false
                        addValueText = ""
                    } else {
                        val fractionMatch = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""").find(addValueText)
                        if (fractionMatch != null) {
                            val num = fractionMatch.groupValues[1].toDoubleOrNull()
                            if (num != null) {
                                viewModel.addManualMark(num, addValueText, coords.first, coords.second)
                                showAddDialog = false
                                addValueText = ""
                            }
                        }
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Edit Mark Dialog ──
    if (showEditDialog && selectedMark != null) {
        val mark = selectedMark!!
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Mark") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (mark.candidates.isNotEmpty()) {
                        Text("Alternative Detections:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            mark.candidates.forEach { cand ->
                                InputChip(
                                    selected = editValueText == cand,
                                    onClick = { editValueText = cand },
                                    label = { Text(cand) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = editValueText,
                        onValueChange = { editValueText = it },
                        label = { Text("Value") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Nudge Position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.moveMark(mark.id, mark.boundingBox.x - 20, mark.boundingBox.y) }) {
                            Icon(Icons.Filled.ArrowBack, "Left")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { viewModel.moveMark(mark.id, mark.boundingBox.x, mark.boundingBox.y - 20) }) {
                                Icon(Icons.Filled.ArrowUpward, "Up")
                            }
                            Text("Nudge", style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { viewModel.moveMark(mark.id, mark.boundingBox.x, mark.boundingBox.y + 20) }) {
                                Icon(Icons.Filled.ArrowDownward, "Down")
                            }
                        }
                        IconButton(onClick = { viewModel.moveMark(mark.id, mark.boundingBox.x + 20, mark.boundingBox.y) }) {
                            Icon(Icons.Filled.ArrowForward, "Right")
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.approveMark(mark.id)
                        showEditDialog = false
                    }) {
                        Text("Confirm")
                    }
                    TextButton(onClick = {
                        val parsed = editValueText.toDoubleOrNull()
                        if (parsed != null) {
                            viewModel.addManualMark(parsed, editValueText, mark.boundingBox.x, mark.boundingBox.y)
                            viewModel.deleteMark(mark.id)
                            showEditDialog = false
                        }
                    }) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteMark(mark.id)
                    showEditDialog = false
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            }
        )
    }

    val pagePositionText by viewModel.pagePositionText.collectAsStateWithLifecycle()
    var showDiagnostics by remember { mutableStateOf(false) }
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    LaunchedEffect(showDiagnostics, page?.imagePath) {
        if (showDiagnostics) {
            page?.imagePath?.let { path ->
                viewModel.calculateDiagnostics(path)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(pagePositionText)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isDrawMode = !isDrawMode
                        if (!isDrawMode) {
                            strokes.clear()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Draw Mode",
                            tint = if (isDrawMode) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDiagnostics = !showDiagnostics }) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = "Diagnostics",
                            tint = if (showDiagnostics) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            val hasPrev by viewModel.hasPreviousPage.collectAsStateWithLifecycle()
            val hasNext by viewModel.hasNextPage.collectAsStateWithLifecycle()

            BottomAppBar(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(72.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateToPreviousPage() },
                        enabled = hasPrev
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous Page")
                    }

                    Button(
                        onClick = {
                            viewModel.deleteCurrentPage(onBack)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Delete, "Delete Page")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }

                    IconButton(
                        onClick = { viewModel.navigateToNextPage() },
                        enabled = hasNext
                    ) {
                        Icon(Icons.Filled.ArrowForward, "Next Page")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showDiagnostics && diagnostics != null) {
                item {
                    val diag = diagnostics!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Developer Diagnostics",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Debug Mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Classification Reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text(diag.classificationReason, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Blank Score", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text("${diag.blankScore.toInt()} / 100", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Edge Density", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text(String.format(java.util.Locale.US, "%.3f %%", diag.edgeDensity), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ink Coverage %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text(String.format(java.util.Locale.US, "%.2f %%", diag.inkCoveragePct), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Red Ink Coverage %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text(String.format(java.util.Locale.US, "%.2f %%", diag.redInkPct), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Handwriting Density", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text(String.format(java.util.Locale.US, "%.1f %%", diag.handwritingDensity), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("OCR Characters Found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text("${diag.ocrCharacters}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("OCR Words Found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text("${diag.ocrWords}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Text Regions Found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                Text("${diag.textRegions}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
            }

            // Full page image with overlay
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                imageSize = coordinates.size
                            }
                            .pointerInput(isDrawMode) {
                                if (!isDrawMode) {
                                    detectTapGestures { offset ->
                                        if (imageSize.width > 0 && imageSize.height > 0) {
                                            val scale = 1200f / imageSize.width
                                            val origX = (offset.x * scale).toInt()
                                            val origY = (offset.y * scale).toInt()

                                            addMarkCoords = origX to origY
                                            addValueText = ""
                                            showAddDialog = true
                                        }
                                    }
                                }
                            }
                    ) {
                        // Base full sheet image
                        page?.imagePath?.let { path ->
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(java.io.File(path))
                                    .memoryCacheKey(path + "_" + imageVersion)
                                    .build(),
                                contentDescription = "Page image",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }

                        // Drawing box overlays (Feature 2)
                        if (imageSize.width > 0 && imageSize.height > 0 && !isDrawMode) {
                            val scale = 1200f / imageSize.width
                            val density = LocalDensity.current

                            marks.forEach { mark ->
                                val box = mark.boundingBox
                                val left = box.x / scale
                                val top = box.y / scale
                                val width = box.width / scale
                                val height = box.height / scale

                                // Color coding (Issue 10)
                                val color = when {
                                    mark.isManual -> Color(0xFF9C27B0) // Purple: manually added
                                    mark.status == MarkStatus.CONFIRMED || mark.status == MarkStatus.EDITED -> Color(0xFF4CAF50) // Green: confirmed
                                    mark.value <= 0 || mark.displayValue == "?" || mark.displayValue == "Unreadable" -> Color(0xFF2196F3) // Blue: Candidate
                                    mark.confidence < 0.3 -> Color(0xFFF44336) // Red: Detection Error
                                    else -> Color(0xFFFFEB3B) // Yellow: Needs review
                                }

                                var dragOffsetX by remember(mark.id) { mutableStateOf(0f) }
                                var dragOffsetY by remember(mark.id) { mutableStateOf(0f) }

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { (left + dragOffsetX).toDp() },
                                            y = with(density) { (top + dragOffsetY).toDp() }
                                        )
                                        .size(
                                            width = with(density) { width.toDp() },
                                            height = with(density) { height.toDp() }
                                        )
                                        .border(2.dp, color, RoundedCornerShape(4.dp))
                                        .background(color.copy(alpha = 0.15f))
                                        .pointerInput(mark.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragEnd = {
                                                    val finalX = ((left + dragOffsetX) * scale).toInt()
                                                    val finalY = ((top + dragOffsetY) * scale).toInt()
                                                    viewModel.moveMark(mark.id, finalX, finalY)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                }
                                            )
                                        }
                                        .clickable {
                                            selectedMark = mark
                                            editValueText = mark.displayValue
                                            showEditDialog = true
                                        }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(2.dp),
                                        color = color.copy(alpha = 0.85f),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "${mark.displayValue} (${(mark.confidence * 100).toInt()}%)",
                                            color = if (color == Color(0xFFFFEB3B)) Color.Black else Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // ── Drawing overlay (Draw Mode) ──
                        if (isDrawMode && imageSize.width > 0 && imageSize.height > 0) {
                            val density = LocalDensity.current
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(with(density) { imageSize.height.toDp() })
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentStroke.clear()
                                                currentStroke.add(offset)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                currentStroke.add(change.position)
                                            },
                                            onDragEnd = {
                                                strokes.add(currentStroke.toList())
                                                currentStroke.clear()
                                            }
                                        )
                                    }
                            ) {
                                strokes.forEach { stroke ->
                                    if (stroke.size > 1) {
                                        val path = Path()
                                        path.moveTo(stroke[0].x, stroke[0].y)
                                        for (i in 1 until stroke.size) {
                                            path.lineTo(stroke[i].x, stroke[i].y)
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color.Red,
                                            style = Stroke(
                                                width = 4.dp.toPx(),
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                            )
                                        )
                                    } else if (stroke.size == 1) {
                                        drawCircle(Color.Red, radius = 2.dp.toPx(), center = stroke[0])
                                    }
                                }

                                if (currentStroke.size > 1) {
                                    val path = Path()
                                    path.moveTo(currentStroke[0].x, currentStroke[0].y)
                                    for (i in 1 until currentStroke.size) {
                                        path.lineTo(currentStroke[i].x, currentStroke[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.Red,
                                        style = Stroke(
                                            width = 4.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                } else if (currentStroke.size == 1) {
                                    drawCircle(Color.Red, radius = 2.dp.toPx(), center = currentStroke[0])
                                }
                            }
                        }

                        // ── Drawing Control Floating Actions ──
                        if (isDrawMode) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { strokes.clear() }
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", color = Color.White)
                                }
                                TextButton(
                                    onClick = {
                                        page?.imagePath?.let { path ->
                                            viewModel.saveDrawing(
                                                imagePath = path,
                                                strokes = strokes.toList(),
                                                composeWidth = imageSize.width.toFloat(),
                                                composeHeight = imageSize.height.toFloat()
                                            )
                                            imageVersion++
                                            isDrawMode = false
                                            strokes.clear()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null, tint = Color.Green)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Red Ink", color = Color.Green)
                                }
                            }
                        }
                    }
                }
            }

            // Detected marks list below image
            if (marks.isNotEmpty()) {
                item {
                    Text(
                        text = "Detected Marks (Tap boxes above or list items to modify)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(marks) { mark ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMark = mark
                                editValueText = mark.displayValue
                                showEditDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            mark.evidenceImagePath?.let { path ->
                                AsyncImage(
                                    model = path,
                                    contentDescription = "Mark evidence",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            MarkChip(
                                value = mark.displayValue,
                                size = MarkChipSize.MEDIUM
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mark: ${mark.value.toMarksString()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (mark.detectionReason.isNotEmpty()) {
                                    Text(
                                        text = mark.detectionReason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                    )
                                }
                                ConfidenceBadge(confidence = mark.confidence)
                            }
                        }
                    }
                }
            }
        }
    }
}
