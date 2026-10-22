package com.markflow.app.ui.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import com.markflow.app.domain.model.IssueType
import com.markflow.app.domain.model.MarkStatus
import com.markflow.app.ui.components.*
import com.markflow.app.ui.theme.*
import com.markflow.app.util.toFormattedDateTime
import com.markflow.app.util.toMarksString
import com.markflow.app.util.toPercentageString
import com.markflow.app.util.toRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopySummaryScreen(
    copyId: Long,
    onBack: () -> Unit,
    onViewPages: () -> Unit,
    onNavigateToPage: (Long) -> Unit,
    viewModel: CopySummaryViewModel = hiltViewModel()
) {
    val copy by viewModel.copy.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val runningTotal by viewModel.runningTotal.collectAsStateWithLifecycle()
    val questionMarks by viewModel.questionMarks.collectAsStateWithLifecycle()
    val auditTrail by viewModel.auditTrail.collectAsStateWithLifecycle()
    val reportState by viewModel.reportState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showStudentDetailsDialog by remember { mutableStateOf(false) }
    var studentNameInput by remember { mutableStateOf("") }
    var rollNumberInput by remember { mutableStateOf("") }
    var regNumberInput by remember { mutableStateOf("") }
    var classNameInput by remember { mutableStateOf("") }
    var sectionInput by remember { mutableStateOf("") }

    LaunchedEffect(showStudentDetailsDialog) {
        if (showStudentDetailsDialog) {
            studentNameInput = copy?.studentName ?: ""
            rollNumberInput = copy?.rollNumber ?: ""
            regNumberInput = copy?.registrationNumber ?: ""
            classNameInput = copy?.className ?: ""
            sectionInput = copy?.section ?: ""
        }
    }

    LaunchedEffect(reportState) {
        when (val state = reportState) {
            is ReportState.Success -> {
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.markflow.app.fileprovider",
                    state.file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Evaluation Report"))
                viewModel.resetReportState()
            }
            is ReportState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetReportState()
            }
            else -> {}
        }
    }

    val showProgressDialog = reportState is ReportState.Loading || reportState is ReportState.LoadingProgress
    if (showProgressDialog) {
        val currentProgress = when (val state = reportState) {
            is ReportState.LoadingProgress -> state.progress
            else -> 0f
        }
        val currentPage = when (val state = reportState) {
            is ReportState.LoadingProgress -> state.currentPage
            else -> 0
        }
        val totalPages = when (val state = reportState) {
            is ReportState.LoadingProgress -> state.totalPages
            else -> pages.size
        }
        val estimatedTime = when (val state = reportState) {
            is ReportState.LoadingProgress -> state.estimatedTimeSeconds
            else -> -1
        }

        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Generating Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (currentPage > 0) "Building page $currentPage of $totalPages..." else "Preparing export...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(currentProgress * 100).toInt()}% complete",
                            style = TabularNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        if (estimatedTime >= 0) {
                            Text(
                                text = if (estimatedTime == 0) "Almost done..." else "Est: ${estimatedTime}s remaining",
                                style = TabularNumbers.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelExport() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStudentDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showStudentDetailsDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Confirm Student Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Verify or enter student details before generating the PDF report:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Input 1: Student Name
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Student Name",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = studentNameInput,
                            onValueChange = { studentNameInput = it },
                            placeholder = { Text("e.g. Eleanor Vance") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Input 2: Roll Number
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Roll Number",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = rollNumberInput,
                            onValueChange = { rollNumberInput = it },
                            placeholder = { Text("e.g. 2026-4402") },
                            textStyle = TabularNumbers.copy(fontSize = 16.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Input 3: Registration Number
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Registration Number",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = regNumberInput,
                            onValueChange = { regNumberInput = it },
                            placeholder = { Text("e.g. REG-889312") },
                            textStyle = TabularNumbers.copy(fontSize = 16.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Row for Class and Section
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Class",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = classNameInput,
                                onValueChange = { classNameInput = it },
                                placeholder = { Text("e.g. 10-A") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Section",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = sectionInput,
                                onValueChange = { sectionInput = it },
                                placeholder = { Text("e.g. A") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.generateReport(
                            studentName = studentNameInput,
                            rollNumber = rollNumberInput,
                            registrationNumber = regNumberInput,
                            className = classNameInput,
                            section = sectionInput
                        )
                        showStudentDetailsDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Generate PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStudentDetailsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copy Summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    val isApproved = copy?.isVerified ?: false
                    IconButton(
                        onClick = { showStudentDetailsDialog = true },
                        enabled = isApproved
                    ) {
                        Icon(Icons.Filled.Share, "Share Report")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Total Marks Display (Clickable Adjustment Dialog) ──
            item {
                var showAdjustDialog by remember { mutableStateOf(false) }
                var adjustTotalText by remember { mutableStateOf("") }
                var bonusText by remember { mutableStateOf("0") }
                var penaltyText by remember { mutableStateOf("0") }
                var reasonText by remember { mutableStateOf("") }

                if (showAdjustDialog) {
                    AlertDialog(
                        onDismissRequest = { showAdjustDialog = false },
                        shape = RoundedCornerShape(16.dp),
                        title = {
                            Text(
                                text = "Adjust Total Marks",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Override Total
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Override Total Marks",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = adjustTotalText,
                                        onValueChange = { adjustTotalText = it },
                                        placeholder = { Text("e.g. 75.0") },
                                        textStyle = TabularNumbers.copy(fontSize = 16.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                // Bonus Marks
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Bonus Marks",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = bonusText,
                                        onValueChange = { bonusText = it },
                                        placeholder = { Text("0") },
                                        textStyle = TabularNumbers.copy(fontSize = 16.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                // Penalty Marks
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Penalty Marks",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = penaltyText,
                                        onValueChange = { penaltyText = it },
                                        placeholder = { Text("0") },
                                        textStyle = TabularNumbers.copy(fontSize = 16.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                // Reason / Notes
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Reason / Notes",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = reasonText,
                                        onValueChange = { reasonText = it },
                                        placeholder = { Text("e.g. Attendance bonus or Grace marks") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val overrideTotal = adjustTotalText.toDoubleOrNull() ?: runningTotal
                                    val bonus = bonusText.toDoubleOrNull() ?: 0.0
                                    val penalty = penaltyText.toDoubleOrNull() ?: 0.0
                                    val finalAdjustedTotal = overrideTotal + bonus - penalty
                                    viewModel.adjustTotal(finalAdjustedTotal, bonus, penalty, reasonText)
                                    showAdjustDialog = false
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAdjustDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                val displayedTotal = copy?.writtenTotal ?: runningTotal

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        adjustTotalText = (copy?.writtenTotal ?: runningTotal).toMarksString()
                        bonusText = "0"
                        penaltyText = "0"
                        reasonText = ""
                        showAdjustDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Marks" + if (copy?.writtenTotal != null) " (Manual Override)" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (copy?.writtenTotal != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (copy?.writtenTotal != null) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = displayedTotal.toMarksString(),
                                style = TabularNumbers.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " /100",
                                style = TabularNumbers.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Pages Scanned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${pages.size}",
                                    style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Tag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Marks Detected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${marks.size}",
                                    style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Scan Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = copy?.createdAt?.toFormattedDateTime() ?: "",
                                    style = TabularNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // ── Student Details Card (Feature 1) ──
            copy?.let { c ->
                if (c.studentName != null || c.rollNumber != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Student Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                c.studentName?.let {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                c.rollNumber?.let {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Roll No", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = TabularNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                c.registrationNumber?.let {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Registration No", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = TabularNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                if (c.className != null || c.section != null) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Class / Section", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${c.className ?: ""} - ${c.section ?: ""}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Scan Quality Card (Feature 13) ──
            if (pages.isNotEmpty()) {
                item {
                    val avgScore = pages.map { it.scanQualityScore }.average().toInt()
                    val rating = when {
                        avgScore >= 85 -> "Excellent"
                        avgScore >= 70 -> "Good"
                        avgScore >= 50 -> "Fair"
                        else -> "Poor"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Average Scan Quality", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                val ratingColor = when(rating) {
                                    "Excellent" -> MaterialTheme.colorScheme.primary
                                    "Good" -> ConfidenceHigh
                                    "Fair" -> ConfidenceMedium
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ratingColor.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, ratingColor.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = rating,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ratingColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$avgScore%",
                                style = TabularNumbers.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ── Analysis Section ──
            item {
                Text(
                    text = "Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Issues
            if (issues.isEmpty()) {
                item {
                    IssueCard(
                        type = "success",
                        description = "No issues detected",
                        severity = "info",
                        isResolved = true
                    )
                }
            } else {
                items(issues) { issue ->
                    IssueCard(
                        type = issue.type.name,
                        description = issue.description,
                        severity = issue.severity.name,
                        isResolved = issue.isResolved,
                        onResolve = { viewModel.resolveIssue(issue.id) }
                    )
                }
            }

            // Verification status
            item {
                val confirmedCount = marks.count { it.status == MarkStatus.CONFIRMED || it.status == MarkStatus.EDITED }
                val reviewCount = marks.count { it.status == MarkStatus.NEEDS_REVIEW }

                IssueCard(
                    type = "verification",
                    description = if (reviewCount == 0)
                        "All marks are verified ($confirmedCount/$confirmedCount)"
                    else
                        "$reviewCount marks need review",
                    severity = if (reviewCount == 0) "info" else "warning",
                    isResolved = reviewCount == 0
                )
            }

            // ── Question-wise Marks Mapping (Feature 2) ──
            if (questionMarks.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Question-wise Marks Mapping",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(questionMarks) { qm ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                                        modifier = Modifier.widthIn(min = 60.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Q${qm.questionNumber}",
                                                style = TabularNumbers.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = qm.marksAwarded.toMarksString(),
                                                style = TabularNumbers.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Verification Summary (Feature 13) ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Verification Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val aiDetected = marks.count { !it.isManual && it.regionType == "awarded_mark" }
                        val manualAdded = marks.count { it.isManual && it.regionType == "awarded_mark" }
                        val corrected = marks.count { it.status == MarkStatus.EDITED && it.regionType == "awarded_mark" }
                        val rejected = marks.count { it.status == MarkStatus.REJECTED }
                        val duplicatePages = pages.count { it.isDuplicate }
                        val missingPages = issues.count { it.type == IssueType.MISSING_SCORE }

                        VerificationSummaryRow("AI Detected Marks", "$aiDetected")
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        VerificationSummaryRow("Manual Marks Added", "$manualAdded")
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        VerificationSummaryRow("Marks Corrected", "$corrected")
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        VerificationSummaryRow("Rejected Detections", "$rejected")
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        VerificationSummaryRow("Pages Scanned", "${pages.size}")
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        VerificationSummaryRow("Duplicate Pages Found", "$duplicatePages")
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        VerificationSummaryRow("Potential Missing Pages", "$missingPages")
                    }
                }
            }

            // ── Approve Evaluation switch (Feature 13) ──
            item {
                val isVerified = copy?.isVerified ?: false
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isVerified) MarkFlowGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isVerified) MarkFlowGreen else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verify and Approve Copy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Teacher approval is required before report export is enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isVerified,
                            onCheckedChange = { viewModel.setVerified(it) }
                        )
                    }
                }
            }

            // ── Action Buttons ──
            item {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onViewPages,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Visibility, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Pages")
                }

                Spacer(modifier = Modifier.height(8.dp))

                val isApproved = copy?.isVerified ?: false
                Button(
                    onClick = { showStudentDetailsDialog = true },
                    enabled = isApproved,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Report", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Detection Audit Trail (Feature 14) ──
            if (auditTrail.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Detection Audit Trail",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(auditTrail) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.action.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when(log.action.lowercase()) {
                                        "detected" -> MaterialTheme.colorScheme.secondary
                                        "approved" -> MaterialTheme.colorScheme.primary
                                        "corrected" -> MaterialTheme.colorScheme.tertiary
                                        "deleted" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = log.timestamp.toRelativeTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.userAction ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = TabularNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
