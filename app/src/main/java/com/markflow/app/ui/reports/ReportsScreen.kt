package com.markflow.app.ui.reports

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markflow.app.ui.components.EmptyState
import com.markflow.app.ui.summary.ReportState
import com.markflow.app.ui.theme.StatusError
import com.markflow.app.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val reportFiles by viewModel.reportFiles.collectAsStateWithLifecycle()
    val reportState by viewModel.reportState.collectAsStateWithLifecycle()

    var showNewReportDialog by remember { mutableStateOf(false) }
    var selectedSessionId by remember { mutableStateOf<Long?>(null) }
    var showFormatDialog by remember { mutableStateOf(false) }

    val fileDateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)

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
                    type = if (state.file.name.endsWith(".xlsx")) {
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    } else {
                        "application/pdf"
                    }
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
                    Text("Compiling batch results and statistics...")
                }
            },
            confirmButton = {}
        )
    }

    // ── Session Selection Dialog ──
    if (showNewReportDialog) {
        AlertDialog(
            onDismissRequest = { showNewReportDialog = false },
            title = { Text("Select Session for Batch Report") },
            text = {
                if (sessions.isEmpty()) {
                    Text("No sessions available. Please scan some copies first.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(sessions) { session ->
                            Card(
                                onClick = {
                                    selectedSessionId = session.id
                                    showFormatDialog = true
                                    showNewReportDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(session.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text("Copies: ${session.copyCount} | Average: ${String.format(Locale.US, "%.1f", session.averageMarks)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Format Selection Dialog ──
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Select Report Format") },
            text = {
                Text("Select the file format for the batch summary report.")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            selectedSessionId?.let { viewModel.generateBatchPdf(it) }
                            showFormatDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                    Button(
                        onClick = {
                            selectedSessionId?.let { viewModel.generateBatchExcel(it) }
                            showFormatDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.TableChart, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evaluation Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewReportDialog = true },
                icon = { Icon(Icons.Filled.Add, "Generate") },
                text = { Text("New Batch Report") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header stats or filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { showNewReportDialog = true },
                    label = { Text("Generate Batch PDF") },
                    selected = false,
                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    onClick = { showNewReportDialog = true },
                    label = { Text("Generate Batch Excel") },
                    selected = false,
                    leadingIcon = { Icon(Icons.Filled.TableChart, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp)) }
                )
            }

            if (reportFiles.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Assessment,
                    title = "No reports generated yet",
                    subtitle = "Tap 'New Batch Report' or generate a copy summary to view reports here"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(reportFiles) { file ->
                        ReportFileCard(
                            file = file,
                            formattedSize = FileUtils.formatFileSize(file.length()),
                            formattedDate = fileDateFormat.format(Date(file.lastModified())),
                            onShare = {
                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    "com.markflow.app.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (file.name.endsWith(".xlsx")) {
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    } else {
                                        "application/pdf"
                                    }
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Report"))
                            },
                            onDelete = { viewModel.deleteReportFile(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportFileCard(
    file: File,
    formattedSize: String,
    formattedDate: String,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isExcel = file.name.endsWith(".xlsx")
    val fileIcon = if (isExcel) Icons.Filled.TableChart else Icons.Filled.PictureAsPdf
    val iconColor = if (isExcel) Color(0xFF2E7D32) else Color(0xFFD32F2F)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = fileIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedSize | $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = StatusError)
            }
        }
    }
}
