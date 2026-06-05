package com.markflow.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markflow.app.ui.theme.*

/**
 * Confidence badge showing a percentage with color coding.
 */
@Composable
fun ConfidenceBadge(
    confidence: Double,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    val color = when {
        confidence >= 0.85 -> ConfidenceHigh
        confidence >= 0.50 -> ConfidenceMedium
        else -> ConfidenceLow
    }
    val percentage = (confidence * 100).toInt()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = if (showPercentage) "$percentage%" else when {
                confidence >= 0.85 -> "High"
                confidence >= 0.50 -> "Medium"
                else -> "Low"
            },
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Chip displaying a detected mark value.
 */
@Composable
fun MarkChip(
    value: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean = true,
    size: MarkChipSize = MarkChipSize.MEDIUM
) {
    val bgColor = if (isPositive) MarkFlowGreenSurface else StatusError.copy(alpha = 0.1f)
    val textColor = if (isPositive) MarkFlowGreenOnSurface else StatusError

    val (textSize, padding) = when (size) {
        MarkChipSize.SMALL -> 12.sp to PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        MarkChipSize.MEDIUM -> 16.sp to PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        MarkChipSize.LARGE -> 22.sp to PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = if (isPositive) "+$value" else value,
            color = textColor,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(padding)
        )
    }
}

enum class MarkChipSize { SMALL, MEDIUM, LARGE }

/**
 * Stats card for dashboard displays.
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Issue card for displaying detected problems.
 */
@Composable
fun IssueCard(
    type: String,
    description: String,
    severity: String,
    modifier: Modifier = Modifier,
    isResolved: Boolean = false,
    onResolve: (() -> Unit)? = null
) {
    val (icon, color) = when (severity.lowercase()) {
        "error" -> Icons.Filled.Error to StatusError
        "warning" -> Icons.Filled.Warning to StatusWarning
        "info" -> Icons.Filled.Info to StatusInfo
        else -> Icons.Filled.Info to StatusInfo
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isResolved) MaterialTheme.colorScheme.surfaceVariant
                else color.copy(alpha = 0.08f),
        border = if (!isResolved) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
                 else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isResolved) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                tint = if (isResolved) ConfidenceHigh else color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onResolve != null && !isResolved) {
                IconButton(onClick = onResolve) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Resolve",
                        tint = ConfidenceHigh
                    )
                }
            }
        }
    }
}

/**
 * Empty state placeholder with icon and message.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(16.dp))
            action()
        }
    }
}

/**
 * Shimmer loading placeholder.
 */
@Composable
fun ShimmerLoadingItem(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnim.value - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim.value, 0f)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )
    }
}

/**
 * Animated counter for marks total display.
 */
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium
) {
    var previousCount by remember { mutableIntStateOf(count) }
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = tween(durationMillis = 500),
        label = "counter_animation"
    )

    LaunchedEffect(count) {
        previousCount = count
    }

    Text(
        text = animatedCount.toString(),
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Section header with optional action.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
