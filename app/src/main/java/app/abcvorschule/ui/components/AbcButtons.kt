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
            // Voller kidTouch statt kidTouch - 8dp: das Kind tippt diesen Knopf, und
            // 72dp waren eine Ausnahme ohne Begründung — §9 nimmt nur die Chevrons
            // (48dp) aus. Der einzige Aufrufer ist der End-Screen, dessen mittlerer
            // Block `weight(1f)` trägt: die 8dp gehen dort von einem Block ab, der
            // bei font_scale 1.3 selbst im ungünstigsten Fall (360×640dp, vier
            // Bilder, vierzeiliger Satz) über 100dp Luft behält.
            modifier = Modifier.defaultMinSize(minHeight = AbcDimens.kidTouch),
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
    //
    // 56dp und nicht AbcDimens.kidTouch: 56dp ist der Hitbox-Boden des Design-Systems
    // (WordFrameSizing.MinFrameDp, SentencePegSizing.MinPegWidthDp, §9 „jeder Peg
    // bleibt tippbar (56dp)"), kidTouch der bequeme Zielwert dort, wo Platz ist. Hier
    // ist keiner: der Knopf erscheint nach zwei Fehlversuchen *zusätzlich* im
    // Antwortblock von acht Trainern, und der steht in einer Bühne, die weder scrollt
    // noch beschneidet (ExerciseStage). Genau dieser Knopf wurde dort schon einmal
    // auf wenige dp zusammengedrückt (siehe ExerciseStage-Kommentar zu
    // PromptHeightFraction); 24dp mehr je Trainer wären derselbe Fehler noch einmal.
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
        // Der Glyph trägt die 3:1-Grenze für UI-Bauteile allein: ohne Gehäuse gibt
        // es keine zweite Fläche, an der man den Knopf erkennen könnte. 0.55 lag
        // über Cream bei #B5AA98 = 2.08:1 — zu wenig für den einzigen Weg zurück in
        // eine Runde. 0.8 komponiert zu #958976 = 3.11:1 und bleibt sichtbar unter
        // den 4.45:1 des vollen WarmMuted: gedämpft wie in §9 gewollt, nur eben
        // nicht unsichtbar.
        // Der Aus-Zustand bleibt bei 0.2 (#E2D9C8, 1.27:1): deaktivierte Bauteile
        // nimmt WCAG 1.4.11 ausdrücklich aus, und ein Rückfallweg, der gerade
        // nirgendwohin führt, soll auch nicht danach aussehen.
        val tint = WarmMuted.copy(alpha = if (enabled) 0.8f else 0.2f)
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
