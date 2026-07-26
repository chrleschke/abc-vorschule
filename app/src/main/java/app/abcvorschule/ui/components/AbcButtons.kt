package app.abcvorschule.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.ui.theme.AbcDimens

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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            IconChevronRight(tint = MaterialTheme.colorScheme.onPrimary, size = 22.dp)
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
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
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
fun AbcProgressBar(
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else ((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AbcDimens.progressBarHeight)
            .padding(horizontal = 4.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF2A3A4F),
                cornerRadius = CornerRadius(size.height),
            )
            drawRoundRect(
                color = Color(0xFF7EC8A3),
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(size.height),
            )
        }
    }
}
