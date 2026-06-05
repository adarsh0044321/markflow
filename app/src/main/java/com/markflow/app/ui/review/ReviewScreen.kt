package com.markflow.app.ui.review

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.markflow.app.domain.model.Page
import com.markflow.app.domain.model.DetectedMark
import com.markflow.app.domain.model.MarkStatus
import com.markflow.app.ui.components.*
import com.markflow.app.ui.theme.*
import com.markflow.app.util.toMarksString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    copyId: Long,
    onBack: () -> Unit,
    onNavigateToPage: (Long) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val allMarks by viewModel.allMarks.collectAsStateWithLifecycle()
    val reviewMarks by viewModel.reviewMarks.collectAsStateWithLifecycle()
    val runningTotal by viewModel.runningTotal.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "All Marks (${allMarks.size})",
        "Review (${reviewMarks.size})",
        "Ignored (${allMarks.count { it.status == MarkStatus.IGNORED || it.status == MarkStatus.REJECTED }})"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detected Marks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Running Total ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Running Total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = runningTotal.toInt().toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Text(
                        text = "/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ── Developer Debug Panel (Issue 12) ──
            var showDebugPanel by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showDebugPanel = !showDebugPanel },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, "Developer Debug Panel", tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Developer Debug Panel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Icon(if (showDebugPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle")
                    }

                    if (showDebugPanel) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val candidateCount = allMarks.count { it.status == MarkStatus.NEEDS_REVIEW }
                        val confirmedCount = allMarks.count { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED }
                        val rejectedCount = allMarks.count { it.status == MarkStatus.REJECTED || it.status == MarkStatus.IGNORED }
                        val manualCount = allMarks.count { it.isManual }
                        val ocrMatchCount = allMarks.count { it.ocrResult?.detectedValue != null }
                        val redRegionsCount = allMarks.size

                        Text("• Red Regions Found: $redRegionsCount", style = MaterialTheme.typography.bodySmall)
                        Text("• OCR Matches: $ocrMatchCount", style = MaterialTheme.typography.bodySmall)
                        Text("• Candidate Count: $candidateCount", style = MaterialTheme.typography.bodySmall)
                        Text("• Confirmed Marks: $confirmedCount", style = MaterialTheme.typography.bodySmall)
                        Text("• Rejected/Ignored Marks: $rejectedCount", style = MaterialTheme.typography.bodySmall)
                        Text("• Manual Marks: $manualCount", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Tabs ──
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // ── Edit Mark Dialog ──
            var markToEdit by remember { mutableStateOf<DetectedMark?>(null) }
            var editValueText by remember { mutableStateOf("") }

            if (markToEdit != null) {
                val mark = markToEdit!!
                AlertDialog(
                    onDismissRequest = { markToEdit = null },
                    title = { Text("Edit Mark Value") },
                    text = {
                        OutlinedTextField(
                            value = editValueText,
                            onValueChange = { editValueText = it },
                            label = { Text("Enter Value (e.g. 5, 4.5, 3/5)") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val parsed = editValueText.toDoubleOrNull()
                            if (parsed != null) {
                                viewModel.editMark(mark.id, parsed, editValueText)
                            } else {
                                val fractionMatch = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""").find(editValueText)
                                if (fractionMatch != null) {
                                    val num = fractionMatch.groupValues[1].toDoubleOrNull()
                                    if (num != null) {
                                        viewModel.editMark(mark.id, num, editValueText)
                                    }
                                }
                            }
                            markToEdit = null
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { markToEdit = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (selectedTabIndex == 0) {
                if (pages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.ContentPasteOff,
                            title = "No pages scanned yet",
                            subtitle = "Please scan pages to start evaluation."
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pages.size) { index ->
                            val pageItem = pages[index]
                            val pageMarks = allMarks.filter { it.pageId == pageItem.id }
                            PageThumbnailItem(
                                page = pageItem,
                                marks = pageMarks,
                                onClick = { onNavigateToPage(pageItem.id) }
                            )
                        }
                    }
                }
            } else {
                val displayMarks = if (selectedTabIndex == 1) {
                    reviewMarks
                } else {
                    allMarks.filter { it.status == MarkStatus.IGNORED || it.status == MarkStatus.REJECTED }
                }

                if (displayMarks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = if (selectedTabIndex == 1) Icons.Filled.CheckCircle else Icons.Outlined.ContentPasteOff,
                            title = if (selectedTabIndex == 1) "All done reviewing!" else "No marks found",
                            subtitle = if (selectedTabIndex == 1)
                                "You reviewed all detections." else "No marks in this category."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayMarks) { mark ->
                            ReviewMarkCard(
                                mark = mark,
                                onApprove = { viewModel.approveMark(mark.id) },
                                onEdit = {
                                    markToEdit = mark
                                    editValueText = mark.displayValue
                                },
                                onIgnore = { viewModel.ignoreMark(mark.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageMarksSummaryCard(
    pageId: Long,
    marks: List<DetectedMark>,
    onPageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalForPage = marks.filter { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED }
        .sumOf { it.value }
    val avgConfidence = marks.map { it.confidence }.average()

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onPageClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mark value badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = totalForPage.toMarksString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${marks.size} Marks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ConfidenceBadge(confidence = avgConfidence)

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (marks.all { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED })
                    Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (marks.all { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED })
                    ConfidenceHigh else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ReviewMarkCard(
    mark: DetectedMark,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Evidence image
                if (mark.evidenceImagePath != null) {
                    AsyncImage(
                        model = mark.evidenceImagePath,
                        contentDescription = "Mark evidence",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Detected: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = mark.displayValue,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (mark.detectionReason.isNotEmpty()) {
                        Text(
                            text = mark.detectionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    ConfidenceBadge(confidence = mark.confidence)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ConfidenceHigh
                    )
                ) {
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ConfidenceMedium
                    )
                ) {
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Ignore")
                }
            }
        }
    }
}

@Composable
fun PageThumbnailItem(
    page: Page,
    marks: List<DetectedMark>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detectedCount = marks.count { !it.isManual && it.status != MarkStatus.REJECTED && it.status != MarkStatus.IGNORED }
    val manualCount = marks.count { it.isManual && it.status != MarkStatus.REJECTED && it.status != MarkStatus.IGNORED }

    val needsReview = marks.any { it.status == MarkStatus.NEEDS_REVIEW }
    val isVerified = marks.isNotEmpty() && marks.all { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED }
    val hasLowConfidence = marks.any { it.status == MarkStatus.NEEDS_REVIEW || it.confidence < 0.7 }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.7f),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imageModel = page.thumbnailPath ?: page.imagePath
            AsyncImage(
                model = imageModel,
                contentDescription = "Page ${page.pageNumber} Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Page ${page.pageNumber}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isVerified) ConfidenceHigh.copy(alpha = 0.85f) else if (needsReview) ConfidenceMedium.copy(alpha = 0.85f) else Color.Gray.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = if (isVerified) "Verified" else if (needsReview) "Needs Review" else "Empty",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Detected: $detectedCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Manual: $manualCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (page.isDuplicate) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CopyAll,
                                contentDescription = "Duplicate Page",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(12.dp)
                            )
                        }
                    }
                    if (page.hasUncheckedAnswers) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Unchecked Answers",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(12.dp)
                            )
                        }
                    }
                    if (hasLowConfidence) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Low Confidence Marks",
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(12.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    val pageTotal = marks.filter { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED }
                        .sumOf { it.value }
                    Text(
                        text = pageTotal.toMarksString() + " M",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
