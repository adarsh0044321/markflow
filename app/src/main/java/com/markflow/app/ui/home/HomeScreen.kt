package com.markflow.app.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import com.markflow.app.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markflow.app.domain.model.Copy
import com.markflow.app.domain.model.DashboardStats
import com.markflow.app.domain.model.ScanSession
import com.markflow.app.ui.components.*
import com.markflow.app.ui.theme.*
import com.markflow.app.ui.summary.ReportState
import com.markflow.app.util.FileUtils
import com.markflow.app.util.toFormattedCount
import com.markflow.app.util.toMarksString
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartScan: (sessionId: Long, copyId: Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCopy: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val recentCopies by viewModel.recentCopies.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val sessionCopies by viewModel.sessionCopies.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreatingScan.collectAsStateWithLifecycle()
    val reportState by viewModel.reportState.collectAsStateWithLifecycle()
    val isOrientationSet by viewModel.isOrientationSet.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showScanDetailsDialog by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<ScanSession?>(null) }
    var copyToEdit by remember { mutableStateOf<Copy?>(null) }
    var showOrientationOnboardingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isOrientationSet) {
        if (!isOrientationSet) {
            showOrientationOnboardingDialog = true
        }
    }

    if (showOrientationOnboardingDialog) {
        OrientationOnboardingDialog(
            onConfirm = { orientation ->
                viewModel.setAnswerSheetOrientation(orientation)
                showOrientationOnboardingDialog = false
            }
        )
    }

    LaunchedEffect(reportState) {
        when (val state = reportState) {
            is ReportState.Success -> {
                Toast.makeText(context, "Report generated successfully!", Toast.LENGTH_SHORT).show()
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "com.markflow.app.fileprovider",
                    state.file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Report"))
                viewModel.resetReportState()
            }
            is ReportState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetReportState()
            }
            else -> {}
        }
    }

    if (reportState is ReportState.Loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Generating Report") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Compiling summary and statistics...")
                }
            },
            confirmButton = {}
        )
    }

    // ── Create Folder Dialog ──
    ClassDetailsDialog(
        show = showCreateFolderDialog,
        onDismiss = { showCreateFolderDialog = false },
        onConfirm = { name, maxMarks, passThreshold ->
            viewModel.createFolder(name, maxMarks, passThreshold)
            showCreateFolderDialog = false
            Toast.makeText(context, "Class Folder created successfully!", Toast.LENGTH_SHORT).show()
        }
    )

    // ── Scan Details Dialog (Folder details for Start Scan) ──
    ClassDetailsDialog(
        show = showScanDetailsDialog,
        onDismiss = { showScanDetailsDialog = false },
        onConfirm = { name, maxMarks, passThreshold ->
            showScanDetailsDialog = false
            viewModel.startNewScanWithDetails(name, maxMarks, passThreshold) { sessionId, copyId ->
                onStartScan(sessionId, copyId)
            }
        }
    )

    // ── Folder Detailed copies list dialog ──
    selectedFolder?.let { folder ->
        FolderDetailDialog(
            session = folder,
            copies = sessionCopies,
            onDismiss = {
                selectedFolder = null
                viewModel.selectSession(null)
            },
            onAddCopy = {
                selectedFolder = null
                viewModel.selectSession(null)
                viewModel.startScanInFolder(folder.id) { sessionId, copyId ->
                    onStartScan(sessionId, copyId)
                }
            },
            onEditCopy = { copy ->
                copyToEdit = copy
            },
            onDeleteCopy = { copyId ->
                viewModel.deleteCopy(copyId)
            },
            onGenerateReport = {
                viewModel.generateFolderReport(folder.id)
            }
        )
    }

    // ── Edit Student Details Dialog ──
    copyToEdit?.let { copy ->
        EditCopyDetailsDialog(
            copy = copy,
            onDismiss = { copyToEdit = null },
            onConfirm = { name, roll ->
                viewModel.updateCopyDetails(copy.id, name, roll)
                copyToEdit = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.markflow_icon),
                            contentDescription = "MarkFlow Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "MarkFlow",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Create Folder")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Start New Scan Button ──
            item {
                StartScanCard(
                    isLoading = isCreating,
                    onClick = { showScanDetailsDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── Overview Stats ──
            item {
                OverviewSection(
                    stats = stats,
                    onGenerateAllClassesReport = { viewModel.generateAllClassesReport() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── Class Folders / Sessions Section ──
            item {
                SectionHeader(
                    title = "Class Folders / Sessions",
                    action = "Create Folder",
                    onAction = { showCreateFolderDialog = true }
                )
            }

            if (folders.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Folder,
                        title = "No class folders yet",
                        subtitle = "Create a folder or start a scan to begin organizing copies",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(folders) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = {
                            selectedFolder = folder
                            viewModel.selectSession(folder.id)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Navigation Items ──
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    NavigationItem(
                        icon = Icons.Outlined.History,
                        title = "Scan History",
                        subtitle = "View all scanned copies",
                        onClick = onNavigateToHistory
                    )
                    NavigationItem(
                        icon = Icons.Outlined.Assessment,
                        title = "Reports",
                        subtitle = "View generated batch reports",
                        onClick = onNavigateToReports
                    )
                    NavigationItem(
                        icon = Icons.Outlined.BarChart,
                        title = "Statistics",
                        subtitle = "Analytics and insights",
                        onClick = onNavigateToStatistics
                    )
                }
            }

            // ── Recent Copies ──
            if (recentCopies.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recent Copies",
                        action = "View All",
                        onAction = onNavigateToHistory
                    )
                }

                items(recentCopies.take(5)) { copy ->
                    RecentCopyItem(
                        copy = copy,
                        onClick = { onNavigateToCopy(copy.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: ScanSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${folder.copyCount} copies",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Avg: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", folder.averageMarks),
                            style = TabularNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ClassDetailsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, maxMarks: Double, passThreshold: Double) -> Unit
) {
    if (!show) return

    var name by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("100") }
    var passThreshold by remember { mutableStateOf("33") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Class Folder Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                // Input 1: Class Name
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Class / Folder Name",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Mathematics 10-A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Input 2: Max Marks
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Maximum Marks",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = maxMarks,
                        onValueChange = { newValue ->
                            maxMarks = newValue.filter { it.isDigit() }
                        },
                        placeholder = { Text("e.g. 100") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Input 3: Passing Threshold
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Passing Threshold (%)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = passThreshold,
                        onValueChange = { newValue ->
                            passThreshold = newValue.filter { it.isDigit() }
                        },
                        placeholder = { Text("e.g. 33") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val maxM = maxMarks.toDoubleOrNull() ?: 100.0
                    val passT = passThreshold.toDoubleOrNull() ?: 33.0
                    if (name.isNotBlank()) {
                        onConfirm(name, maxM, passT)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FolderDetailDialog(
    session: ScanSession,
    copies: List<Copy>,
    onDismiss: () -> Unit,
    onAddCopy: () -> Unit,
    onEditCopy: (Copy) -> Unit,
    onDeleteCopy: (Long) -> Unit,
    onGenerateReport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddCopy) {
                    Icon(Icons.Filled.Add, "Add Copy", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                // Folder Stats Overview Card - Flat Border and Clean Monospace Numbers
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Copies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${copies.size}", style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Class Avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%.1f", session.averageMarks), style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        val passCount = copies.count { it.calculatedTotal >= session.maxMarks * (session.passThreshold / 100.0) }
                        val passPct = if (copies.isNotEmpty()) (passCount.toDouble() / copies.size) * 100.0 else 0.0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Pass Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%.0f%%", passPct), style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Text("Copies Directory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                if (copies.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No copies scanned in this folder yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    ) {
                        items(copies) { copy ->
                            val isPassed = copy.calculatedTotal >= session.maxMarks * (session.passThreshold / 100.0)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = copy.studentName ?: "Copy #${copy.copyNumber}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Roll: ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = copy.rollNumber ?: "N/A",
                                                style = TabularNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = " | Marks: ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${copy.calculatedTotal.toMarksString()}/${session.maxMarks.toMarksString()}",
                                                style = TabularNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // Pass / Fail Badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isPassed) MarkFlowGreenSurface else StatusError.copy(alpha = 0.08f),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isPassed) "PASS" else "FAIL",
                                            color = if (isPassed) MarkFlowGreen else StatusError,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    IconButton(onClick = { onEditCopy(copy) }) {
                                        Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDeleteCopy(copy.id) }) {
                                        Icon(Icons.Filled.Delete, "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onGenerateReport,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Class Report", fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun EditCopyDetailsDialog(
    copy: Copy,
    onDismiss: () -> Unit,
    onConfirm: (name: String, roll: String) -> Unit
) {
    var name by remember { mutableStateOf(copy.studentName ?: "") }
    var roll by remember { mutableStateOf(copy.rollNumber ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Student Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                // Input 1: Student Name
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Student Name",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
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
                        value = roll,
                        onValueChange = { roll = it },
                        placeholder = { Text("e.g. 2026-4402") },
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
                    onConfirm(name, roll)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StartScanCard(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { if (!isLoading) onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "New Scanning Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Scan answer copies and calculate marks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Start",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OverviewSection(
    stats: DashboardStats,
    onGenerateAllClassesReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onGenerateAllClassesReport) {
                    Icon(
                        imageVector = Icons.Filled.Assessment,
                        contentDescription = "All-Classes Report",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("All-Classes Report", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Copies Scanned",
                value = stats.totalCopiesScanned.toFormattedCount(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Pages Scanned",
                value = stats.totalPagesScanned.toFormattedCount(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Total Marks",
                value = stats.totalMarksDetected.toFormattedCount(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

@Composable
private fun RecentCopyItem(
    copy: Copy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mark value circle
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = copy.calculatedTotal.toMarksString(),
                        style = TabularNumbers.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = copy.studentName ?: "Copy #${copy.copyNumber}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Roll: ${copy.rollNumber ?: "N/A"} | ${copy.pageCount} Pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Total Marks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = copy.calculatedTotal.toMarksString(),
                        style = TabularNumbers.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "/100",
                        style = TabularNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            if (copy.hasIssues) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Issues",
                    tint = StatusWarning,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun OrientationOnboardingDialog(
    onConfirm: (String) -> Unit
) {
    var selectedOrientation by remember { mutableStateOf("portrait") }

    AlertDialog(
        onDismissRequest = { /* Force selection on first launch */ },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Answer Sheet Orientation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Welcome to MarkFlow! Please select the default orientation of your exam answer sheets. This helps optimize the auto-crop and layout detection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Portrait Selection Card
                Card(
                    onClick = { selectedOrientation = "portrait" },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedOrientation == "portrait") {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (selectedOrientation == "portrait") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CropPortrait,
                            contentDescription = null,
                            tint = if (selectedOrientation == "portrait") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Portrait Orientation",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Standard school/college answer sheets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Landscape Selection Card
                Card(
                    onClick = { selectedOrientation = "landscape" },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedOrientation == "landscape") {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (selectedOrientation == "landscape") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CropLandscape,
                            contentDescription = null,
                            tint = if (selectedOrientation == "landscape") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Landscape Orientation",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "CBSE-style or wide answer sheets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedOrientation) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Get Started", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
