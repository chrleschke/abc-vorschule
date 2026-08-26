package app.abcvorschule.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmMuted

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
    // Ruhige Sekundäraktion in einer Rolle: WarmMuted (4.45:1 auf Cream) für Glyph
    // und Label. Vorher erbte das Label primary (= LeafGreen, „richtig/erledigt")
    // und das Icon secondary (= SkyBlue, „Fortschritt") — zwei Bedeutungsfarben
    // auf einem Aufgeben-Weg, und Auflösen darf nicht grün sein (§8, §10).
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = WarmMuted),
    ) {
        IconUnlock(tint = WarmMuted, size = 22.dp)
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
    // Ohne Gehäuse: Vor/Zurück ist ein Rückfallweg für Erwachsene, kein Angebot
    // ans Kind — vorwärts kommt es durch Lösen. Die Trefferfläche bleibt trotzdem
    // bei 48 dp, sonst wäre der Rückfallweg auf dem Gerät nicht bedienbar.
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = WarmMuted.copy(alpha = if (enabled) 0.55f else 0.2f)
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
