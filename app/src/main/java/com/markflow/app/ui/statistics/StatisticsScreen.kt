package com.markflow.app.ui.statistics

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markflow.app.domain.model.Copy
import com.markflow.app.ui.components.StatCard
import com.markflow.app.util.toFormattedCount
import com.markflow.app.util.toMarksString

private data class GradeBarData(
    val label: String,
    val count: Int,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onCopyClick: (Long) -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val stats by viewModel.cohortStats.collectAsStateWithLifecycle()
    val cohortGrades by viewModel.cohortGrades.collectAsStateWithLifecycle()
    val cohortCopies by viewModel.cohortCopies.collectAsStateWithLifecycle()

    var dropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableIntStateOf(0) } // 0: Marks High-Low, 1: Marks Low-High, 2: Roll Number

    val selectedSessionName = sessions.find { it.id == selectedSessionId }?.name ?: "All Sessions (Aggregate)"

    val filteredCopies = remember(cohortCopies, searchQuery, sortOrder) {
        cohortCopies
            .filter { copy ->
                searchQuery.isEmpty() ||
                (copy.studentName?.contains(searchQuery, ignoreCase = true) == true) ||
                (copy.rollNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                ("Copy #${copy.copyNumber}".contains(searchQuery, ignoreCase = true))
            }
            .sortedWith { c1, c2 ->
                when (sortOrder) {
                    0 -> c2.calculatedTotal.compareTo(c1.calculatedTotal) // marks high to low
                    1 -> c1.calculatedTotal.compareTo(c2.calculatedTotal) // marks low to high
                    else -> (c1.rollNumber ?: "").compareTo(c2.rollNumber ?: "") // roll number
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cohort Statistics") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Cohort dropdown selection ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    onClick = { dropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Selected Cohort / Batch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            Text(selectedSessionName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Icon(Icons.Filled.ArrowDropDown, "Select Cohort", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Sessions (Aggregate)") },
                        onClick = {
                            viewModel.selectSession(null)
                            dropdownExpanded = false
                        }
                    )
                    sessions.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.name) },
                            onClick = {
                                viewModel.selectSession(session.id)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // ── Performance Overview ──
            item {
                Text(
                    text = "Performance Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Average Marks",
                        value = stats.averageMarks.toMarksString(),
                        icon = Icons.Outlined.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Highest",
                        value = stats.highestMarks.toMarksString(),
                        icon = Icons.Outlined.ArrowUpward,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Lowest",
                        value = stats.lowestMarks.toMarksString(),
                        icon = Icons.Outlined.ArrowDownward,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Pass %",
                        value = "${stats.passPercentage.toInt()}%",
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Median Marks",
                        value = stats.medianMarks.toMarksString(),
                        icon = Icons.Outlined.BarChart,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Std Deviation",
                        value = String.format("%.2f", stats.standardDeviation),
                        icon = Icons.Outlined.ShowChart,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Grade Distribution ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grade Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val total = stats.totalCopiesScanned.toFloat()
                        val listGrades = listOf(
                            GradeBarData("A (>=85)", cohortGrades.gradeA, Color(0xFF2E7D32)),
                            GradeBarData("B (70-84)", cohortGrades.gradeB, Color(0xFF00897B)),
                            GradeBarData("C (50-69)", cohortGrades.gradeC, Color(0xFFFFB300)),
                            GradeBarData("D (33-49)", cohortGrades.gradeD, Color(0xFFF4511E)),
                            GradeBarData("F (<33)", cohortGrades.gradeF, Color(0xFFE53935))
                        )

                        listGrades.forEach { grade ->
                            val pct = if (total > 0f) grade.count / total else 0f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = grade.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(80.dp),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = grade.color,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${grade.count} (${(pct * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(50.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Cohort details ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cohort Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        StatRow("Total Copies Evaluated", stats.totalCopiesScanned.toFormattedCount())
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        StatRow("Total Pages Scanned", stats.totalPagesScanned.toFormattedCount())
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        StatRow("Total Marks Detected", stats.totalMarksDetected.toFormattedCount())
                    }
                }
            }

            // ── Student evaluations list ──

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Evaluations (${filteredCopies.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(onClick = {
                        sortOrder = (sortOrder + 1) % 3
                    }) {
                        val icon = when (sortOrder) {
                            0 -> Icons.Filled.TrendingDown // descending
                            1 -> Icons.Filled.TrendingUp // ascending
                            else -> Icons.Filled.SortByAlpha // alphabetical/roll
                        }
                        Icon(icon, contentDescription = "Sort order")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, roll, section...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }

            if (filteredCopies.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No evaluations found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredCopies) { copy ->
                    CohortCopyItem(
                        copy = copy,
                        onClick = { onCopyClick(copy.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CohortCopyItem(
    copy: Copy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${copy.calculatedTotal.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = copy.studentName ?: "Student Copy #${copy.copyNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Roll: ${copy.rollNumber ?: "N/A"} | Sec: ${copy.section ?: "N/A"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (copy.isVerified) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = if (copy.isVerified) "Verified" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copy.isVerified) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
