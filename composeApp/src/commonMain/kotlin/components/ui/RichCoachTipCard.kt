package components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import service.CoachTip

/**
 * The coach's tip as a rich card: title and summary, time / difficulty / frequency chips, steps you
 * can tick off, an expandable "Why it works", an encouraging line, and actions. Shows the usual
 * empty / loading / error states (via [CoachTipCard]) while there's no tip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichCoachTipCard(
    tip: CoachTip?,
    isLoading: Boolean,
    error: String?,
    emptyText: String,
    modifier: Modifier = Modifier,
    /** Whether the tip's habit is already on the user's list (shows "In your habits"). */
    isInHabits: Boolean = false,
    onAddToHabits: (() -> Unit)? = null,
    onNewTip: (() -> Unit)? = null,
) {
    if (tip == null || isLoading || error != null) {
        CoachTipCard(tip = null, isLoading = isLoading, error = error, emptyText = emptyText, modifier = modifier)
        return
    }

    // Ticked steps reset whenever a new tip arrives
    val done = remember(tip) { mutableStateListOf<Int>() }
    var showWhy by remember(tip) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(
                    icon = Icons.Outlined.AutoAwesome,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 40.dp,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tip.title, style = MaterialTheme.typography.titleLarge)
                    if (tip.summary.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tip.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Chips
            val chips = listOfNotNull(
                tip.minutes?.let { Icons.Outlined.Timer to "$it min" },
                tip.difficulty.takeIf { it.isNotEmpty() }?.let { Icons.Outlined.Bolt to it },
                tip.frequency.takeIf { it.isNotEmpty() }?.let { Icons.Outlined.CalendarMonth to it },
            )
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { (icon, label) -> TipChip(icon, label) }
                }
            }

            // Steps
            Spacer(Modifier.height(16.dp))
            Text(
                if (done.size == tip.steps.size) "All steps done — nice!" else "Steps · ${done.size}/${tip.steps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            tip.steps.forEachIndexed { index, step ->
                StepRow(
                    number = index + 1,
                    title = step.title,
                    detail = step.detail,
                    checked = index in done,
                    onCheckedChange = { if (it) done.add(index) else done.remove(index) },
                )
            }

            // Why it works
            if (tip.why.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val chevron by animateFloatAsState(if (showWhy) 180f else 0f, label = "whyChevron")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .toggleable(value = showWhy, role = Role.Button, onValueChange = { showWhy = it })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Why it works", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.rotate(chevron))
                }
                AnimatedVisibility(visible = showWhy, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Text(
                        tip.why,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                    )
                }
            }

            if (tip.encouragement.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tip.encouragement,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Actions
            if (onAddToHabits != null || onNewTip != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onAddToHabits != null) {
                        FilledTonalButton(onClick = onAddToHabits, enabled = !isInHabits) {
                            Icon(
                                if (isInHabits) Icons.Outlined.TaskAlt else Icons.Outlined.AddTask,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isInHabits) "In your habits" else "Add to my habits")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onNewTip != null) {
                        TextButton(onClick = onNewTip) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New tip")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipChip(icon: ImageVector, label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val circle by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "stepCircle",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(shape = CircleShape, color = circle, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (checked) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                } else {
                    Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
