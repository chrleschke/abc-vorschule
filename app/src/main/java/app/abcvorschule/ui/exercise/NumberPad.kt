package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.speech.GermanNumberWord
import app.abcvorschule.ui.components.IconBackspace
import app.abcvorschule.ui.components.IconChevronRight
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Der Kinder-Ziffernblock (PRODUCT_PRINCIPLES §8). Bis September 2026 tippte das Kind
 * seine Rechenantwort in die System-Zahlentastatur — Erwachsenen-UI mit Komma, Minus,
 * Leertaste und „Fertig", die von selbst aufklappte, die Aufgabe verdeckte und auf
 * jedem Gerät anders aussah. Sie kam in fast der Hälfte aller Rechenrunden (Ergebnis
 * ab 11).
 *
 * Jetzt gehört der Block der App: oben das Antwortfeld zwischen Löschen und Absenden,
 * darunter zwei Fünferreihen Ziffern (1–5, 6–0) — dieselbe Fünfer-Gliederung wie das
 * Zählfeld und zwei Hände. Jede Taste federt beim Drücken ein, gibt `tick`-Haptik und
 * **spricht die Zahl, die jetzt im Feld steht** („eins", dann „zwölf"): ein Kind, das
 * zweistellige Ziffern noch nicht liest, hört so, was es getippt hat.
 *
 * Solange die Zähl-Hilfe offen ist, klappen die Ziffernreihen ein — das Kind zählt
 * dann mit dem Finger im Aufgabenblock, und das Feld spiegelt den Zähler. Ein Tipp
 * aufs Feld holt die Ziffern zurück.
 */
@Composable
fun NumberPad(
    onSubmit: (Int) -> Unit,
    /** Changing this clears the field — a new round, or another wrong try. */
    resetToken: String,
    modifier: Modifier = Modifier,
    /** True once the typed number turned out to be the answer — the field confirms in green. */
    solved: Boolean = false,
    /** False during the audio lock — the whole block is non-interactive and dimmed. */
    enabled: Boolean = true,
    /** Von der Zähl-Hilfe hochgezählter Wert; `null` heißt „nichts (mehr)
     * angetippt". Wirkt nur bei [countingOpen]. */
    countedValue: Int? = null,
    /** True, solange die Zähl-Hilfe offen ist: Ziffernreihen eingeklappt, das Feld
     * spiegelt, was die Hilfe zählt. */
    countingOpen: Boolean = false,
    /** Spricht die Zahl im Feld nach jedem Tastendruck (Zählkanal). */
    onSpeakValue: (String) -> Unit = {},
) {
    var value by remember(resetToken) { mutableStateOf("") }
    var keysOpen by remember(resetToken, countingOpen) { mutableStateOf(!countingOpen) }
    val haptics = LocalAbcHaptics.current
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(durationMillis = AbcMotion.QuickMs),
        label = "number_pad_lock_opacity",
    )
    val interactive = enabled && !solved

    fun submit() {
        value.toIntOrNull()?.let(onSubmit)
    }

    fun type(digit: Int) {
        val next = NumberPadInput.append(value, digit)
        if (next == value) {
            // Schon drei Ziffern: spürbar „voll", kein Fehler.
            haptics.nudge()
            return
        }
        value = next
        haptics.tick()
        next.toIntOrNull()?.let { onSpeakValue(GermanNumberWord.of(it)) }
    }

    LaunchedEffect(countedValue, resetToken, countingOpen) {
        // Auch auf resetToken gekeyed: der Token wechselt bei jedem Fehlversuch und
        // leert das Feld. Ohne dieses Re-Spiegeln stünde das Feld nach einem Miss
        // leer da, während die Haken in der Zähl-Hilfe noch gesetzt sind.
        //
        // Gespiegelt wird auch der LEERE Stand: nimmt das Kind alle Tipps wieder
        // zurück, steht die Hilfe wieder bei null, und die zuletzt gespiegelte
        // Zahl im Feld wäre eine Antwort, die niemand mehr gezählt hat — samt der
        // Möglichkeit, sie abzusenden. Nur solange die Hilfe offen ist: sonst
        // löschte dieser Effect die von Hand getippte Zahl.
        if (!countingOpen) return@LaunchedEffect
        value = NumberPadInput.mirroredValue(countedValue)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().alpha(opacity), contentAlignment = Alignment.Center) {
        val key = NumberPadInput.keySizeDp(maxWidth.value).dp
        val gap = NumberPadInput.KeyGapDp.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap + 4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PadKey(
                    size = key,
                    enabled = interactive && value.isNotEmpty(),
                    color = CreamElevated,
                    onClick = {
                        haptics.tick()
                        value = NumberPadInput.backspace(value)
                    },
                    modifier = Modifier.testTag("number_erase").semantics { contentDescription = "Löschen" },
                ) {
                    IconBackspace(tint = WarmMuted, size = key * 0.5f)
                }
                AnswerField(
                    value = value,
                    solved = solved,
                    height = key,
                    onTap = { if (interactive) keysOpen = true },
                )
                PadKey(
                    size = key,
                    enabled = interactive && value.isNotEmpty(),
                    color = SunCoral,
                    onClick = ::submit,
                    modifier = Modifier.testTag("number_submit"),
                ) {
                    IconChevronRight(tint = Cream, size = key * 0.45f)
                }
            }
            if (keysOpen) {
                NumberPadInput.KeyRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { digit ->
                            PadKey(
                                size = key,
                                enabled = interactive,
                                color = CreamElevated,
                                onClick = { type(digit) },
                                modifier = Modifier.testTag("number_key_$digit"),
                            ) {
                                Text(
                                    text = "$digit",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = WarmInk,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Das Antwortfeld: zeigt, was getippt ist, oder ein blasses „?" — die Aufgabe fragt ja. */
@Composable
private fun AnswerField(
    value: String,
    solved: Boolean,
    height: Dp,
    onTap: () -> Unit,
) {
    // Breite aus der effektiven Textgröße statt fest: bei großer System-Schrift
    // passte die Antwort sonst nicht mehr ins Feld (NumberPadInput.fieldWidthDp).
    val width = NumberPadInput.fieldWidthDp(
        textSp = MaterialTheme.typography.displayLarge.fontSize.value,
        fontScale = LocalDensity.current.fontScale,
    ).dp
    // Grün heißt nur eins: richtig (§8). Während des Tippens neutral im Aktiv-Blau.
    val borderColor = if (solved) LeafGreen else SkyBlue
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Cream,
        border = BorderStroke(3.dp, borderColor),
        modifier = Modifier
            .width(width)
            .height(height)
            .clickable(onClick = onTap)
            .testTag("number_input"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.ifEmpty { "?" },
                style = MaterialTheme.typography.displayLarge,
                color = if (value.isEmpty()) WarmMuted.copy(alpha = 0.5f) else WarmInk,
            )
        }
    }
}

/**
 * Eine Taste. Federt beim Drücken ein und wieder heraus (Squash wie die Jagd-Kugeln,
 * Feder `Bouncy`) — eine Taste, die sich nicht bewegt, fühlt sich für Kinder kaputt an.
 */
@Composable
private fun PadKey(
    size: Dp,
    enabled: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) KeyPressedScale else 1f,
        animationSpec = AbcMotion.Bouncy.spec(),
        label = "pad_key_squash",
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = color,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
                alpha = if (enabled) 1f else DisabledKeyAlpha
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private const val KeyPressedScale = 0.88f
private const val DisabledKeyAlpha = 0.4f
