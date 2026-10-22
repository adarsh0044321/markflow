package com.markflow.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import com.markflow.app.ui.theme.MarkFlowGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Observe DB states
    val autoCapture by viewModel.autoCapture.collectAsStateWithLifecycle()
    val highResCapture by viewModel.highResCapture.collectAsStateWithLifecycle()
    val autoCrop by viewModel.autoCrop.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val answerSheetOrientation by viewModel.answerSheetOrientation.collectAsStateWithLifecycle()
    val showAnnotations by viewModel.showAnnotations.collectAsStateWithLifecycle()

    // Observe local pending UI states
    val maxMarks by viewModel.uiMaxMarks.collectAsStateWithLifecycle()
    val passThreshold by viewModel.uiPassThreshold.collectAsStateWithLifecycle()
    val markSensitivity by viewModel.uiMarkSensitivity.collectAsStateWithLifecycle()
    val defaultQuestionMarks by viewModel.uiDefaultQuestionMarks.collectAsStateWithLifecycle()
    val limitMin by viewModel.uiMarkRecognitionLimitMin.collectAsStateWithLifecycle()
    val limitMax by viewModel.uiMarkRecognitionLimitMax.collectAsStateWithLifecycle()

    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()

    // Validation
    val maxMarksVal = maxMarks.toDoubleOrNull()
    val defaultQuestionMarksVal = defaultQuestionMarks.toDoubleOrNull()
    val maxMarksError = maxMarksVal == null || maxMarksVal <= 0 || maxMarksVal > 1000
    val defaultQuestionMarksError = defaultQuestionMarksVal == null || defaultQuestionMarksVal <= 0 || defaultQuestionMarksVal > 100 || (maxMarksVal != null && defaultQuestionMarksVal > maxMarksVal)
    val passThresholdError = passThreshold.toDoubleOrNull() == null || passThreshold.toDouble() < 0 || passThreshold.toDouble() > 100
    val markSensitivityError = markSensitivity.toDoubleOrNull() == null || markSensitivity.toDouble() < 0 || markSensitivity.toDouble() > 100
    
    val minL = limitMin.toDoubleOrNull()
    val maxL = limitMax.toDoubleOrNull()
    val limitsError = minL == null || maxL == null || minL < 0 || maxL > 100 || minL > maxL

    val isAnyError = maxMarksError || defaultQuestionMarksError || passThresholdError || markSensitivityError || limitsError
    val isSaveEnabled = hasUnsavedChanges && !isAnyError

    var showExitConfirmation by remember { mutableStateOf(false) }
    var showClearDataConfirmation by remember { mutableStateOf(false) }

    // Intercept Back Press
    BackHandler {
        if (hasUnsavedChanges) {
            showExitConfirmation = true
        } else {
            onBack()
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved settings. Do you want to save before leaving?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        viewModel.saveSettings {
                            Toast.makeText(context, "Preferences Updated", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    enabled = !isAnyError
                ) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitConfirmation = false
                        viewModel.discardChanges()
                        onBack()
                    }) {
                        Text("Discard Changes")
                    }
                    TextButton(onClick = { showExitConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showClearDataConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirmation = false },
            title = { Text("Clear All Data") },
            text = { Text("Are you sure you want to permanently delete all scanned sessions, student copies, evidence crops, and generated reports? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataConfirmation = false
                        viewModel.clearAllData {
                            Toast.makeText(context, "All data cleared successfully", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showExitConfirmation = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            viewModel.saveSettings {
                                Toast.makeText(context, "Settings Saved Successfully", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = isSaveEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaveEnabled) MarkFlowGreen else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Unsaved Changes Banner
            if (hasUnsavedChanges) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Unsaved",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unsaved Changes Pending",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── Scanning Settings ──
            item {
                Text(
                    text = "Scanning Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsSwitch(
                    icon = Icons.Outlined.AutoMode,
                    title = "Auto Page Capture",
                    subtitle = "Automatically detect and capture pages",
                    checked = autoCapture,
                    onCheckedChange = { viewModel.setAutoCapture(it) }
                )
            }

            item {
                SettingsSwitch(
                    icon = Icons.Outlined.Crop,
                    title = "Auto Crop & Edge Detection",
                    subtitle = "Crop scanned sheet borders automatically",
                    checked = autoCrop,
                    onCheckedChange = { viewModel.setAutoCrop(it) }
                )
            }

            item {
                SettingsSwitch(
                    icon = Icons.Outlined.HighQuality,
                    title = "High Resolution Capture",
                    subtitle = "Better quality but slower processing",
                    checked = highResCapture,
                    onCheckedChange = { viewModel.setHighResCapture(it) }
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (answerSheetOrientation == "landscape") Icons.Outlined.CropLandscape else Icons.Outlined.CropPortrait,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Answer Sheet Orientation", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (answerSheetOrientation == "landscape") "Landscape (CBSE-style)" else "Portrait (Normal)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(if (answerSheetOrientation == "landscape") "Landscape" else "Portrait")
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Portrait (Normal)") },
                                    onClick = {
                                        viewModel.setAnswerSheetOrientation("portrait")
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Landscape (CBSE-style)") },
                                    onClick = {
                                        viewModel.setAnswerSheetOrientation("landscape")
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.Tune,
                    title = "Mark Detection Sensitivity (%)",
                    value = markSensitivity,
                    onValueChange = { viewModel.updateMarkSensitivity(it) },
                    isError = markSensitivityError,
                    errorMessage = "Must be between 0 and 100"
                )
            }

            // ── Evaluation Settings ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Evaluation Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.Score,
                    title = "Maximum Marks",
                    value = maxMarks,
                    onValueChange = { viewModel.updateMaxMarks(it) },
                    isError = maxMarksError,
                    errorMessage = "Must be a positive number (max 1000)"
                )
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.AssignmentLate,
                    title = "Default Question Max Marks",
                    value = defaultQuestionMarks,
                    onValueChange = { viewModel.updateDefaultQuestionMarks(it) },
                    isError = defaultQuestionMarksError,
                    errorMessage = "Must be positive, max 100, and cannot exceed Maximum Marks"
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Mark Recognition Limits",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = limitMin,
                                onValueChange = { viewModel.updateMarkRecognitionLimitMin(it) },
                                label = { Text("Min Limit") },
                                isError = limitsError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = limitMax,
                                onValueChange = { viewModel.updateMarkRecognitionLimitMax(it) },
                                label = { Text("Max Limit") },
                                isError = limitsError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (limitsError) {
                            Text(
                                text = "Min must be <= Max, positive values only",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.Percent,
                    title = "Pass Threshold (%)",
                    value = passThreshold,
                    onValueChange = { viewModel.updatePassThreshold(it) },
                    isError = passThresholdError,
                    errorMessage = "Must be between 0 and 100"
                )
            }

            // ── Annotations Preferences ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Annotations",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsSwitch(
                    icon = Icons.Outlined.Gesture,
                    title = "Show Drawings & Stamps",
                    subtitle = "Display red ink markings on review screen",
                    checked = showAnnotations,
                    onCheckedChange = { viewModel.setShowAnnotations(it) }
                )
            }

            // ── Appearance ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsSwitch(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Use dark color scheme",
                    checked = darkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) }
                )
            }

            // ── Data Management ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear All Data",
                    subtitle = "Delete all scanned copies and reports",
                    onClick = { showClearDataConfirmation = true }
                )
            }

            // ── About ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "MarkFlow v1.0.0",
                    subtitle = "AI-Powered Answer Sheet Evaluation"
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsTextField(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String = ""
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = value,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                        onValueChange(filtered)
                    },
                    modifier = Modifier.width(90.dp),
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
            if (isError && errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp, start = 36.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
