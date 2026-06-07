package com.markflow.app.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val maxMarks by viewModel.maxMarks.collectAsStateWithLifecycle()
    val passThreshold by viewModel.passThreshold.collectAsStateWithLifecycle()
    val autoCapture by viewModel.autoCapture.collectAsStateWithLifecycle()
    val highResCapture by viewModel.highResCapture.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val markSensitivity by viewModel.markSensitivity.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Scanning Settings ──
            item {
                Text(
                    text = "Scanning",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
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
                    icon = Icons.Outlined.HighQuality,
                    title = "High Resolution Capture",
                    subtitle = "Better quality but slower processing",
                    checked = highResCapture,
                    onCheckedChange = { viewModel.setHighResCapture(it) }
                )
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.Tune,
                    title = "Mark Detection Sensitivity (%)",
                    value = markSensitivity,
                    onValueChange = { viewModel.setMarkSensitivity(it) }
                )
            }

            // ── Evaluation Settings ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Evaluation",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.Score,
                    title = "Maximum Marks",
                    value = maxMarks,
                    onValueChange = { viewModel.setMaxMarks(it) }
                )
            }

            item {
                SettingsTextField(
                    icon = Icons.Outlined.Percent,
                    title = "Pass Threshold (%)",
                    value = passThreshold,
                    onValueChange = { viewModel.setPassThreshold(it) }
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
                    modifier = Modifier.padding(vertical = 8.dp)
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
                    text = "Data",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear All Data",
                    subtitle = "Delete all scanned copies and reports"
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
                    modifier = Modifier.padding(vertical = 8.dp)
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
    onValueChange: (String) -> Unit
) {
    var localText by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isFocused && localText != value) {
            localText = value
        }
    }

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
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = localText,
                onValueChange = { newValue ->
                    val filteredValue = newValue.filter { it.isDigit() }
                    localText = filteredValue
                    onValueChange(filteredValue)
                },
                modifier = Modifier
                    .width(80.dp)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            if (localText.isEmpty()) {
                                val fallback = when {
                                    title.contains("Sensitivity", ignoreCase = true) -> "50"
                                    title.contains("Pass", ignoreCase = true) -> "33"
                                    else -> "100"
                                }
                                localText = fallback
                                onValueChange(fallback)
                            }
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String
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
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
