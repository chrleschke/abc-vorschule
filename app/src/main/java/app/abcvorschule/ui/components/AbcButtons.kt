package app.abcvorschule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.SunCoral

/**
 * Primary action aligned to the trailing edge.
 * Icons are vector/ASCII only — never emoji.
 */
@Composable
fun AbcContinueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.continue_label),
    enabled: Boolean = true,
    centered: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.End,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            modifier = Modifier.defaultMinSize(minHeight = AbcDimens.kidTouch - 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SunCoral,
                contentColor = Cream,
            ),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            IconChevronRight(tint = Cream, size = 22.dp)
        }
    }
}

@Composable
fun AbcResolveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.resolve),
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
    ) {
        IconUnlock(tint = MaterialTheme.colorScheme.secondary, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun AbcSpeakerButton(
    enabled: Boolean,
    speaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.speaker)
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .semantics { contentDescription = desc },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        IconSpeaker(
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            },
            speaking = speaking,
            size = 26.dp,
        )
    }
}

@Composable
fun AbcNavChevron(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 52.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        val tint = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        }
        if (forward) {
            IconChevronRight(tint = tint)
        } else {
            IconChevronLeft(tint = tint)
        }
    }
}

@Composable
fun AbcCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.close_lesson),
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        IconClose(tint = MaterialTheme.colorScheme.onSurface, size = 22.dp)
    }
}

@Composable
fun AbcProgressBar(
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else ((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(450),
        label = "progress-fraction",
    )
    // Kurzer Gold-Puls an der Füllkante — Puls markiert das Ereignis „Trainer
    // geschafft", nicht den Zustand. Der Merker startet nur auf dem zuletzt
    // gesehenen Index (kein Puls bei Erstkomposition) und pulst nur, wenn der
    // neue Index tatsächlich größer ist — ein Rücksprung per Zurück-Chevron
    // (index sinkt) pulst also ebenfalls nicht.
    val pulse = remember { Animatable(0f) }
    var previousIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) {
        if (index > previousIndex) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, animationSpec = tween(500))
        }
        previousIndex = index
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AbcDimens.progressBarHeight)
            .padding(horizontal = 4.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = CreamElevated,
                cornerRadius = CornerRadius(size.height),
            )
            val fillWidth = size.width * animatedFraction
            drawRoundRect(
                color = SkyBlue,
                size = Size(fillWidth, size.height),
                cornerRadius = CornerRadius(size.height),
            )
            val pulseValue = pulse.value
            if (pulseValue > 0f) {
                drawCircle(
                    color = StarGold.copy(alpha = 0.6f * pulseValue),
                    radius = size.height * (0.8f + 0.6f * (1f - pulseValue)),
                    center = Offset(fillWidth, size.height / 2f),
                )
            }
        }
    }
}
