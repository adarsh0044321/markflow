package com.markflow.app.ui.pageview

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import com.markflow.app.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewScreen(
    pageId: Long,
    onBack: () -> Unit,
    viewModel: PageViewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val page by viewModel.page.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val maxQuestionMarks by viewModel.maxQuestionMarks.collectAsStateWithLifecycle()

    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var imageVersion by remember { mutableStateOf(0) }

    // ── Annotation Toolbox State ──
    var activeTool by remember { mutableStateOf(ToolType.NONE) }
    val annotations = remember { mutableStateListOf<AnnotationAction>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var selectedSize by remember { mutableFloatStateOf(48f) }
    var currentQuestionNumber by remember { mutableIntStateOf(1) }

    // ── Sequential Dialog Flow State ──
    var showEvaluationDialog by remember { mutableStateOf(false) }
    var dialogStepIndex by remember { mutableIntStateOf(0) } // 0 = Steps, 1 = Final Marks
    var targetMarkCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingMarkId by remember { mutableStateOf<Long?>(null) }

    // Dialog Data fields
    var questionNumText by remember { mutableStateOf("1") }
    val stepsList = remember { mutableStateListOf<Pair<String, String>>() } // (value string, comment string)
    var finalAwardedText by remember { mutableStateOf("") }
    var isOverrideActive by remember { mutableStateOf(false) }

    // Auto-update default values when dialog opens
    LaunchedEffect(showEvaluationDialog) {
        if (!showEvaluationDialog) {
            editingMarkId = null
            targetMarkCoords = null
        }
    }

    // Helper to filter mark input on-the-fly (Numeric keyboard input verification)
    fun filterMarkInput(input: String, maxVal: Double): String {
        if (input.isEmpty()) return ""
        // Allow only digits and at most one dot
        var hasDot = false
        val clean = input.filter { char ->
            if (char == '.') {
                if (!hasDot) {
                    hasDot = true
                    true
                } else {
                    false
                }
            } else {
                char.isDigit()
            }
        }
        
        val dotIndex = clean.indexOf('.')
        val finalStr = if (dotIndex != -1) {
            val beforeDot = clean.substring(0, dotIndex)
            val afterDot = clean.substring(dotIndex + 1)
            val limitedAfterDot = if (afterDot.length > 1) afterDot.substring(0, 1) else afterDot
            "$beforeDot.$limitedAfterDot"
        } else {
            clean
        }
        
        val num = finalStr.toDoubleOrNull()
        if (num != null && num > maxVal) {
            return maxVal.toMarksString()
        }
        return finalStr
    }

    // Swipe navigation helpers
    val activeMarks = remember(marks) { marks.filter { it.regionType == "awarded_mark" } }
    
    fun selectMarkForEditing(mark: DetectedMark) {
        editingMarkId = mark.id
        targetMarkCoords = mark.boundingBox.x to mark.boundingBox.y
        questionNumText = mark.displayValue.removePrefix("Q")
        stepsList.clear()
        if (mark.detectionReason.contains("(")) {
            val splitSteps = mark.detectionReason.split("; ")
            splitSteps.forEach { stepStr ->
                val match = Regex("""([\d.]+)\s*\((.*?)\)""").find(stepStr)
                if (match != null) {
                    stepsList.add(match.groupValues[1] to match.groupValues[2])
                }
            }
        }
        isOverrideActive = true
        finalAwardedText = mark.displayValue
        dialogStepIndex = 0
        showEvaluationDialog = true
    }

    var autoSelectFirstMarkOnLoad by remember { mutableStateOf(false) }
    var autoSelectLastMarkOnLoad by remember { mutableStateOf(false) }

    fun navigateToNextQuestion() {
        val currentIndex = activeMarks.indexOfFirst { it.id == editingMarkId }
        if (currentIndex != -1 && currentIndex < activeMarks.size - 1) {
            selectMarkForEditing(activeMarks[currentIndex + 1])
        } else {
            val currentList = viewModel.siblingPages.value
            val currentP = viewModel.page.value
            if (currentP != null) {
                val idx = currentList.indexOfFirst { it.id == currentP.id }
                if (idx >= 0 && idx < currentList.size - 1) {
                    autoSelectFirstMarkOnLoad = true
                    viewModel.navigateToNextPage()
                }
            }
        }
    }

    fun navigateToPreviousQuestion() {
        val currentIndex = activeMarks.indexOfFirst { it.id == editingMarkId }
        if (currentIndex > 0) {
            selectMarkForEditing(activeMarks[currentIndex - 1])
        } else {
            val currentList = viewModel.siblingPages.value
            val currentP = viewModel.page.value
            if (currentP != null) {
                val idx = currentList.indexOfFirst { it.id == currentP.id }
                if (idx > 0) {
                    autoSelectLastMarkOnLoad = true
                    viewModel.navigateToPreviousPage()
                }
            }
        }
    }

    // Initialize/reset next question number automatically
    LaunchedEffect(marks) {
        val maxQ = marks.filter { it.regionType == "question_number" }
            .mapNotNull { it.displayValue.removePrefix("Q").toIntOrNull() }
            .maxOrNull() ?: 0
        currentQuestionNumber = maxQ + 1
        questionNumText = currentQuestionNumber.toString()

        val active = marks.filter { it.regionType == "awarded_mark" }
        if (autoSelectFirstMarkOnLoad) {
            autoSelectFirstMarkOnLoad = false
            if (active.isNotEmpty()) {
                selectMarkForEditing(active.first())
            }
        } else if (autoSelectLastMarkOnLoad) {
            autoSelectLastMarkOnLoad = false
            if (active.isNotEmpty()) {
                selectMarkForEditing(active.last())
            }
        }
    }

    // Focus Requester logic for keyboard auto-opening
    val focusRequester = remember { FocusRequester() }
    val finalFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(showEvaluationDialog, dialogStepIndex) {
        if (showEvaluationDialog && dialogStepIndex == 0) {
            kotlinx.coroutines.delay(150)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(showEvaluationDialog, dialogStepIndex, isOverrideActive) {
        if (showEvaluationDialog && dialogStepIndex == 1 && isOverrideActive) {
            kotlinx.coroutines.delay(150)
            try {
                finalFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    // ── Sequential Step Marks + Final Question Marks Dialog ──
    if (showEvaluationDialog) {
        if (dialogStepIndex == 0) {
            // STEP MARKS DIALOG
            val areStepsValid = stepsList.all { step ->
                val stepVal = step.first.toDoubleOrNull()
                stepVal != null && stepVal >= 0.0 && stepVal <= maxQuestionMarks && Math.abs(Math.round(stepVal * 2.0) - (stepVal * 2.0)) < 1e-9 && !step.first.endsWith(".")
            }

            AlertDialog(
                onDismissRequest = { showEvaluationDialog = false },
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Step Marks - Question $questionNumText",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onDragEnd = {
                                        if (totalDrag < -150f) {
                                            navigateToNextQuestion()
                                        } else if (totalDrag > 150f) {
                                            navigateToPreviousQuestion()
                                        }
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDrag += dragAmount.x
                                    }
                                )
                            }
                    ) {
                        // Input: Question Number
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Question Number",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = questionNumText,
                                onValueChange = { questionNumText = it.filter { c -> c.isDigit() } },
                                placeholder = { Text("e.g. 1") },
                                textStyle = TabularNumbers.copy(fontSize = 16.sp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Steps Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(stepsList.size) { index ->
                                val step = stepsList[index]
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Step ${index + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = { stepsList.removeAt(index) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete Step",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(0.35f),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Mark",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedTextField(
                                                    value = step.first,
                                                    onValueChange = { newVal ->
                                                        stepsList[index] = filterMarkInput(newVal, maxQuestionMarks) to step.second
                                                    },
                                                    placeholder = { Text("0.0") },
                                                    textStyle = TabularNumbers.copy(fontSize = 15.sp),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Decimal,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onNext = {
                                                            focusManager.moveFocus(FocusDirection.Right)
                                                        }
                                                    ),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = if (index == 0) {
                                                        Modifier.fillMaxWidth().focusRequester(focusRequester)
                                                    } else {
                                                        Modifier.fillMaxWidth()
                                                    }
                                                )
                                            }

                                            Column(
                                                modifier = Modifier.weight(0.65f),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Comment",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedTextField(
                                                    value = step.second,
                                                    onValueChange = { newVal ->
                                                        stepsList[index] = step.first to newVal
                                                    },
                                                    placeholder = { Text("e.g. Correct formula") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        imeAction = if (index == stepsList.size - 1) ImeAction.Done else ImeAction.Next
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onNext = {
                                                            focusManager.moveFocus(FocusDirection.Down)
                                                        },
                                                        onDone = {
                                                            focusManager.clearFocus()
                                                        }
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    stepsList.add("" to "")
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Step")
                            }

                            val stepTotal = stepsList.mapNotNull { it.first.toDoubleOrNull() }.sum()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Subtotal: ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${stepTotal.toMarksString()} M",
                                    style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val stepTotal = stepsList.mapNotNull { it.first.toDoubleOrNull() }.sum()
                            if (!isOverrideActive || finalAwardedText.isEmpty()) {
                                finalAwardedText = stepTotal.toMarksString()
                            }
                            dialogStepIndex = 1
                        },
                        enabled = areStepsValid,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Next: Final Marks")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEvaluationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        } else if (dialogStepIndex == 1) {
            // FINAL QUESTION MARKS DIALOG
            val stepTotal = stepsList.mapNotNull { it.first.toDoubleOrNull() }.sum()
            val finalVal = finalAwardedText.toDoubleOrNull() ?: 0.0

            // Strict Validation Rules: bounds check, 0.5 increments check, decimal places check
            val hasAtMostOneDecimal = !finalAwardedText.contains('.') || finalAwardedText.substringAfter('.', "").length <= 1
            val isWithinBounds = finalVal >= 0.0 && finalVal <= maxQuestionMarks
            val isHalfIncrement = Math.abs(Math.round(finalVal * 2.0) - (finalVal * 2.0)) < 1e-9
            val isFinalValid = isWithinBounds && isHalfIncrement && hasAtMostOneDecimal && finalAwardedText.isNotEmpty() && !finalAwardedText.endsWith(".")

            val errorMessage = when {
                !isWithinBounds -> "Must be between 0.0 and Question Max ($maxQuestionMarks)"
                !isHalfIncrement -> "Must be in 0.5 increments only (e.g. 1.0, 1.5, 2.0)"
                !hasAtMostOneDecimal -> "More than one decimal place is rejected"
                finalAwardedText.endsWith(".") -> "Incomplete decimal input"
                else -> ""
            }

            AlertDialog(
                onDismissRequest = { showEvaluationDialog = false },
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Final Question Marks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onDragEnd = {
                                        if (totalDrag < -150f) {
                                            navigateToNextQuestion()
                                        } else if (totalDrag > 150f) {
                                            navigateToPreviousQuestion()
                                        }
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDrag += dragAmount.x
                                    }
                                )
                            }
                    ) {
                        // Overview Card of Question Info
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Question Index", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "Q$questionNumText",
                                        style = TabularNumbers.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Maximum Marks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "$maxQuestionMarks M",
                                        style = TabularNumbers.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Step Marks Total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${stepTotal.toMarksString()} M",
                                        style = TabularNumbers.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Override Auto Total",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = isOverrideActive,
                                onCheckedChange = {
                                    isOverrideActive = it
                                    if (!it) {
                                        finalAwardedText = stepTotal.toMarksString()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Input field: Final Awarded Marks
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Final Awarded Marks",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = finalAwardedText,
                                onValueChange = { newVal ->
                                    finalAwardedText = filterMarkInput(newVal, maxQuestionMarks)
                                },
                                enabled = isOverrideActive,
                                isError = !isFinalValid && finalAwardedText.isNotEmpty(),
                                placeholder = { Text(stepTotal.toMarksString()) },
                                textStyle = TabularNumbers.copy(fontSize = 16.sp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().focusRequester(finalFocusRequester),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        if (!isFinalValid && finalAwardedText.isNotEmpty()) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val displayStr = finalAwardedText
                            val finalScore = finalVal
                            val x = targetMarkCoords?.first ?: 400
                            val y = targetMarkCoords?.second ?: 400
                            
                            val commentStr = if (stepsList.isNotEmpty()) {
                                stepsList.joinToString("; ") { "${it.first} (${it.second})" }
                            } else ""

                            if (editingMarkId != null) {
                                viewModel.deleteMark(editingMarkId!!)
                            }
                            
                            viewModel.addManualMark(finalScore, displayStr, x, y)
                            showEvaluationDialog = false
                        },
                        enabled = isFinalValid,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Exit")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { dialogStepIndex = 0 },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }
                }
            )
        }
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

                    if (hasNext) {
                        IconButton(
                            onClick = { viewModel.navigateToNextPage() }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next Page")
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.verifyAndSaveCopy(onBack)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MarkFlowGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
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
            // ── Floating Evaluation Toolbox UI ──
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Evaluation Toolbox", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (activeTool != ToolType.NONE) {
                                val annotationsList = annotations
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        if (annotationsList.isNotEmpty()) {
                                            annotationsList.removeAt(annotationsList.size - 1)
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Undo", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = {
                                        annotations.clear()
                                    }) {
                                        Icon(Icons.Filled.Clear, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.NONE,
                                    onClick = { activeTool = ToolType.NONE },
                                    label = "Select",
                                    icon = Icons.Filled.TouchApp
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.TICK,
                                    onClick = { activeTool = ToolType.TICK },
                                    label = "Tick (✓)",
                                    icon = Icons.Filled.Check
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.CROSS,
                                    onClick = { activeTool = ToolType.CROSS },
                                    label = "Cross (✗)",
                                    icon = Icons.Filled.Close
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.PAGE_SEEN,
                                    onClick = { activeTool = ToolType.PAGE_SEEN },
                                    label = "Double Tick",
                                    icon = Icons.Filled.DoneAll
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.BLANK_PAGE,
                                    onClick = { activeTool = ToolType.BLANK_PAGE },
                                    label = "Blank Stamp",
                                    icon = Icons.Filled.LayersClear
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.UNDERLINE,
                                    onClick = { activeTool = ToolType.UNDERLINE },
                                    label = "Underline",
                                    icon = Icons.Filled.HorizontalRule
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.CIRCLE,
                                    onClick = { activeTool = ToolType.CIRCLE },
                                    label = "Circle",
                                    icon = Icons.Filled.RadioButtonUnchecked
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.FREE_PEN,
                                    onClick = { activeTool = ToolType.FREE_PEN },
                                    label = "Pen",
                                    icon = Icons.Filled.Edit
                                )
                            }
                            item {
                                ToolboxChip(
                                    selected = activeTool == ToolType.QUESTION_NUMBER,
                                    onClick = { activeTool = ToolType.QUESTION_NUMBER },
                                    label = "Q No (Q$currentQuestionNumber)",
                                    icon = Icons.Filled.Tag
                                )
                            }
                        }
                        if (activeTool != ToolType.NONE && activeTool != ToolType.FREE_PEN) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Stamp Size: ${selectedSize.toInt()}px", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
                                Slider(
                                    value = selectedSize,
                                    onValueChange = { selectedSize = it },
                                    valueRange = 24f..96f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

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
                            .pointerInput(activeTool) {
                                if (activeTool == ToolType.NONE) {
                                    detectTapGestures { offset ->
                                        if (imageSize.width > 0 && imageSize.height > 0) {
                                            val scale = 1200f / imageSize.width
                                            val origX = (offset.x * scale).toInt()
                                            val origY = (offset.y * scale).toInt()

                                            targetMarkCoords = origX to origY
                                            editingMarkId = null
                                            questionNumText = currentQuestionNumber.toString()
                                            stepsList.clear()
                                            isOverrideActive = false
                                            finalAwardedText = ""
                                            dialogStepIndex = 0
                                            showEvaluationDialog = true
                                        }
                                    }
                                } else if (activeTool == ToolType.TICK || activeTool == ToolType.CROSS ||
                                    activeTool == ToolType.PAGE_SEEN || activeTool == ToolType.BLANK_PAGE) {
                                    detectTapGestures { offset ->
                                        annotations.add(
                                            AnnotationAction(
                                                type = activeTool,
                                                points = listOf(offset),
                                                size = selectedSize
                                            )
                                        )
                                    }
                                } else if (activeTool == ToolType.QUESTION_NUMBER) {
                                    detectTapGestures { offset ->
                                        if (imageSize.width > 0 && imageSize.height > 0) {
                                            val scale = 1200f / imageSize.width
                                            val origX = (offset.x * scale).toInt()
                                            val origY = (offset.y * scale).toInt()
                                            
                                            // Place Question Number Stamp
                                            annotations.add(
                                                AnnotationAction(
                                                    type = ToolType.QUESTION_NUMBER,
                                                    points = listOf(offset),
                                                    size = selectedSize,
                                                    text = "Q$currentQuestionNumber"
                                                )
                                            )
                                            viewModel.addManualMark(0.0, "Q$currentQuestionNumber", origX, origY, isQuestionNumber = true)
                                            currentQuestionNumber++
                                            questionNumText = currentQuestionNumber.toString()
                                        }
                                    }
                                } else if (activeTool == ToolType.FREE_PEN || activeTool == ToolType.UNDERLINE || activeTool == ToolType.CIRCLE) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStroke.clear()
                                            currentStroke.add(offset)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (activeTool == ToolType.FREE_PEN) {
                                                currentStroke.add(change.position)
                                            } else {
                                                if (currentStroke.size > 1) {
                                                    currentStroke[1] = change.position
                                                } else {
                                                    currentStroke.add(change.position)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            annotations.add(
                                                AnnotationAction(
                                                    type = activeTool,
                                                    points = currentStroke.toList(),
                                                    size = selectedSize
                                                )
                                            )
                                            currentStroke.clear()
                                        }
                                    )
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

                        // Drawing box overlays (Select Mode)
                        if (imageSize.width > 0 && imageSize.height > 0 && activeTool == ToolType.NONE) {
                            val scale = 1200f / imageSize.width
                            val density = LocalDensity.current

                            marks.forEach { mark ->
                                val box = mark.boundingBox
                                val left = box.x / scale
                                val top = box.y / scale
                                val width = box.width / scale
                                val height = box.height / scale

                                val color = when {
                                    mark.regionType == "question_number" -> Color.LightGray
                                    mark.isManual -> Color(0xFF9C27B0) // Purple
                                    mark.status == MarkStatus.CONFIRMED || mark.status == MarkStatus.EDITED -> Color(0xFF4CAF50) // Green
                                    mark.value <= 0 || mark.displayValue == "?" || mark.displayValue == "Unreadable" -> Color(0xFF2196F3) // Blue
                                    mark.confidence < 0.3 -> Color(0xFFF44336) // Red
                                    else -> Color(0xFFFFEB3B) // Yellow
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
                                            editingMarkId = mark.id
                                            targetMarkCoords = mark.boundingBox.x to mark.boundingBox.y
                                            
                                            // Check if it is a question number annotation
                                            if (mark.regionType == "question_number") {
                                                questionNumText = mark.displayValue.removePrefix("Q")
                                                viewModel.deleteMark(mark.id)
                                                Toast.makeText(context, "Deleted Q$questionNumText", Toast.LENGTH_SHORT).show()
                                            } else {
                                                questionNumText = currentQuestionNumber.toString()
                                                stepsList.clear()
                                                
                                                // Prepopulate step list from reasons if format matches
                                                if (mark.detectionReason.contains("(")) {
                                                    // Parse existing step details e.g., "1 (Step 1); 1.5 (Step 2)"
                                                    val splitSteps = mark.detectionReason.split("; ")
                                                    splitSteps.forEach { stepStr ->
                                                        val match = Regex("""([\d.]+)\s*\((.*?)\)""").find(stepStr)
                                                        if (match != null) {
                                                            stepsList.add(match.groupValues[1] to match.groupValues[2])
                                                        }
                                                    }
                                                }
                                                isOverrideActive = true
                                                finalAwardedText = mark.displayValue
                                                dialogStepIndex = 0
                                                showEvaluationDialog = true
                                            }
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

                        // ── Drawing overlay (Canvas) ──
                        if (imageSize.width > 0 && imageSize.height > 0) {
                            val density = LocalDensity.current
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(with(density) { imageSize.height.toDp() })
                            ) {
                                annotations.forEach { action ->
                                    drawAnnotation(action, Color.Red, 4.dp.toPx())
                                }

                                if (currentStroke.isNotEmpty()) {
                                    if (activeTool == ToolType.FREE_PEN && currentStroke.size > 1) {
                                        val path = Path()
                                        path.moveTo(currentStroke[0].x, currentStroke[0].y)
                                        for (i in 1 until currentStroke.size) {
                                            path.lineTo(currentStroke[i].x, currentStroke[i].y)
                                        }
                                        drawPath(path, Color.Red, style = Stroke(width = 4.dp.toPx()))
                                    } else if (activeTool == ToolType.UNDERLINE && currentStroke.size >= 2) {
                                        drawLine(Color.Red, currentStroke[0], Offset(currentStroke[1].x, currentStroke[0].y), strokeWidth = 4.dp.toPx())
                                    } else if (activeTool == ToolType.CIRCLE && currentStroke.size >= 2) {
                                        val start = currentStroke[0]
                                        val end = currentStroke[1]
                                        val left = minOf(start.x, end.x)
                                        val top = minOf(start.y, end.y)
                                        val w = Math.abs(start.x - end.x)
                                        val h = Math.abs(start.y - end.y)
                                        drawOval(Color.Red, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = 4.dp.toPx()))
                                    }
                                }
                            }
                        }

                        // ── Drawing Control Floating Actions ──
                        if (activeTool != ToolType.NONE) {
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
                                    onClick = {
                                        annotations.clear()
                                        currentStroke.clear()
                                    }
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset", color = Color.White)
                                }
                                TextButton(
                                    onClick = {
                                        page?.imagePath?.let { path ->
                                            viewModel.saveDrawing(
                                                imagePath = path,
                                                annotations = annotations.toList(),
                                                composeWidth = imageSize.width.toFloat(),
                                                composeHeight = imageSize.height.toFloat()
                                            )
                                            imageVersion++
                                            activeTool = ToolType.NONE
                                            annotations.clear()
                                            Toast.makeText(context, "Markings Saved Successfully", Toast.LENGTH_SHORT).show()
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
                                editingMarkId = mark.id
                                targetMarkCoords = mark.boundingBox.x to mark.boundingBox.y
                                
                                if (mark.regionType == "question_number") {
                                    questionNumText = mark.displayValue.removePrefix("Q")
                                    viewModel.deleteMark(mark.id)
                                    Toast.makeText(context, "Deleted Q$questionNumText", Toast.LENGTH_SHORT).show()
                                } else {
                                    questionNumText = currentQuestionNumber.toString()
                                    stepsList.clear()
                                    
                                    if (mark.detectionReason.contains("(")) {
                                        val splitSteps = mark.detectionReason.split("; ")
                                        splitSteps.forEach { stepStr ->
                                            val match = Regex("""([\d.]+)\s*\((.*?)\)""").find(stepStr)
                                            if (match != null) {
                                                stepsList.add(match.groupValues[1] to match.groupValues[2])
                                            }
                                        }
                                    }
                                    isOverrideActive = true
                                    finalAwardedText = mark.displayValue
                                    dialogStepIndex = 0
                                    showEvaluationDialog = true
                                }
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
                                    text = if (mark.regionType == "question_number") "Question Number Annotation" else "Mark: ${mark.value.toMarksString()}",
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

private fun DrawScope.drawAnnotation(action: AnnotationAction, color: Color, strokeWidth: Float) {
    val pts = action.points
    if (pts.isEmpty()) return
    val s = action.size
    val p0 = pts[0]
    when (action.type) {
        ToolType.FREE_PEN -> {
            if (pts.size > 1) {
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    path.lineTo(pts[i].x, pts[i].y)
                }
                drawPath(path, color, style = Stroke(width = strokeWidth))
            } else if (pts.size == 1) {
                drawCircle(color, radius = strokeWidth / 2f, center = p0)
            }
        }
        ToolType.TICK -> {
            val x = p0.x
            val y = p0.y
            drawLine(color, Offset(x - s/2, y), Offset(x - s/6, y + s/3), strokeWidth = strokeWidth)
            drawLine(color, Offset(x - s/6, y + s/3), Offset(x + s/2, y - s/2), strokeWidth = strokeWidth)
        }
        ToolType.CROSS -> {
            val x = p0.x
            val y = p0.y
            drawLine(color, Offset(x - s/2, y - s/2), Offset(x + s/2, y + s/2), strokeWidth = strokeWidth)
            drawLine(color, Offset(x - s/2, y + s/2), Offset(x + s/2, y - s/2), strokeWidth = strokeWidth)
        }
        ToolType.PAGE_SEEN -> {
            val x = p0.x
            val y = p0.y
            val dx1 = -s/4
            drawLine(color, Offset(x + dx1 - s/3, y), Offset(x + dx1 - s/12, y + s/5), strokeWidth = strokeWidth)
            drawLine(color, Offset(x + dx1 - s/12, y + s/5), Offset(x + dx1 + s/3, y - s/3), strokeWidth = strokeWidth)
            val dx2 = s/4
            drawLine(color, Offset(x + dx2 - s/3, y), Offset(x + dx2 - s/12, y + s/5), strokeWidth = strokeWidth)
            drawLine(color, Offset(x + dx2 - s/12, y + s/5), Offset(x + dx2 + s/3, y - s/3), strokeWidth = strokeWidth)
        }
        ToolType.BLANK_PAGE -> {
            val x = p0.x
            val y = p0.y
            drawRect(color = color, topLeft = Offset(x - s, y - s/2), size = Size(s*2, s), style = Stroke(width = strokeWidth))
            drawLine(color, Offset(x - s, y - s/2), Offset(x + s, y + s/2), strokeWidth = strokeWidth)
        }
        ToolType.UNDERLINE -> {
            if (pts.size >= 2) {
                drawLine(color, p0, Offset(pts[1].x, p0.y), strokeWidth = strokeWidth)
            }
        }
        ToolType.CIRCLE -> {
            if (pts.size >= 2) {
                val start = p0
                val end = pts[1]
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val w = Math.abs(start.x - end.x)
                val h = Math.abs(start.y - end.y)
                drawOval(color = color, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(width = strokeWidth))
            }
        }
        ToolType.QUESTION_NUMBER -> {
            val x = p0.x
            val y = p0.y
            val text = action.text ?: "Q"
            // Render a small border circle for question numbers
            drawCircle(color = color, radius = s * 0.5f, center = p0, style = Stroke(width = strokeWidth))
        }
        else -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolboxChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderWidth = 1.dp,
            borderWidth = 1.dp
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    )
}
