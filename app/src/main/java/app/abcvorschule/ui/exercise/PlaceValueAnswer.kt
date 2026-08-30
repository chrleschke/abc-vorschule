package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.IconChevronRight
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk

/** Spalte des Rechenzeichens — links vor den Ziffern, in jeder Zeile gleich breit. */
private val OperatorSlot = 32.dp

/** Abstand zwischen zwei Spalten. */
private val ColumnGap = 8.dp

/** Breite des Absenden-Pfeils, als Platzhalter auch in den Ziffernzeilen — sonst
 * stünden die Kästen nicht unter den Ziffern, sondern neben ihnen. */
private val ArrowSlot = AbcDimens.kidTouch - 8.dp

/**
 * Die Antwort in Stellenwert-Schreibweise: die Aufgabe untereinander, darunter
 * ein Kasten für die Zehner und einer für die Einer — in denselben Spalten wie
 * die Ziffern darüber (design doc 2026-08-30-zehnerfeld-addition).
 *
 * Keine „Z/E"-Kopfzeile: die Ausrichtung ist die Erklärung, und eine
 * Beschriftung, die ein Vorschulkind nicht liest, ist Dekoration.
 *
 * Zwei Dinge, die [NumberPad] anders macht und dort auch richtig bleiben:
 *
 * - **Kein Fokus beim Aufbau.** Die System-Tastatur würde genau das Zehnerfeld
 *   verdecken, an dem das Kind gerade rechnet. Sie kommt, wenn das Kind einen
 *   Kasten antippt — dieselbe Regel, die dort für die offene Zähl-Hilfe gilt.
 * - **Nichts wird gespiegelt.** Der Stand aus dem Bild landet nicht im Feld;
 *   die Zahl schreibt das Kind selbst. Genau dieser Schritt vom Bild zur Ziffer
 *   ist der geübte.
 */
@Composable
fun PlaceValueAnswer(
    left: Int,
    right: Int,
    answer: Int,
    resetToken: String,
    onSubmit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** True, sobald die getippte Zahl die Antwort war — die Kästen bestätigen grün. */
    solved: Boolean = false,
    /**
     * True, sobald die Runde entschieden ist — richtig geraten **oder** aufgelöst.
     * Sperrt die Eingabe, ohne sie einzufärben: über [enabled] gesperrt zeichnete
     * `OutlinedTextField` seinen Disabled-Zweig und überschriebe damit genau die
     * grüne Bestätigung, die §8 für die richtige Antwort verlangt. Auflösen bleibt
     * dabei farblos — grün ist [solved] allein.
     */
    locked: Boolean = false,
    /** False während des Audio-Locks: Kästen und Pfeil sind blass und stumm. */
    enabled: Boolean = true,
) {
    val fields = PlaceValueInput.fieldCount(answer)
    var tens by remember(resetToken) { mutableStateOf(TextFieldValue("")) }
    var ones by remember(resetToken) { mutableStateOf(TextFieldValue("")) }
    val tensFocus = remember { FocusRequester() }
    val onesFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else StageLockedAlpha,
        animationSpec = tween(durationMillis = StageLockFadeMillis),
        label = "place_value_lock_opacity",
    )
    val complete = PlaceValueInput.isComplete(tens.text, ones.text, fields)

    fun submit() {
        // Einmal abgeschickt ist abgeschickt: der Pfeil ist danach zwar aus, das
        // Einerfeld nimmt aber weiter Fokus und IME-Aktionen an — ein zweites
        // „Fertig" auf der Tastatur käme sonst als zweiter Versuch an.
        if (solved || locked || !complete) return
        PlaceValueInput.combine(tens.text, ones.text, fields)?.let(onSubmit)
    }

    LaunchedEffect(solved) {
        // Ohne das Einklappen sitzt die grüne Bestätigung hinter der Tastatur —
        // genau das, was sie zeigen soll.
        if (solved) keyboard?.hide()
    }

    val slot = PlaceValueInput.slotWidthDp(
        textSp = MaterialTheme.typography.displayLarge.fontSize.value,
        fontScale = LocalDensity.current.fontScale,
    ).dp

    Column(
        modifier = modifier.alpha(opacity),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Die Aufgabe untereinander: Zehner über Zehner, Einer über Einer. Das ist
        // der ganze Zweck dieser Zeilen — und der Grund, warum alle drei Zeilen
        // dieselbe Spaltenfolge haben.
        PlaceValueDigits(operator = "", value = left, slot = slot)
        PlaceValueDigits(operator = MathOperation.Add.symbol, value = right, slot = slot)
        Row(horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
            Spacer(Modifier.width(OperatorSlot))
            Box(
                modifier = Modifier
                    .width(slot * 2 + ColumnGap)
                    .height(3.dp)
                    .background(WarmInk, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(ArrowSlot))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(OperatorSlot))
            if (fields == 2) {
                DigitField(
                    value = tens,
                    onValueChange = { raw ->
                        val digit = PlaceValueInput.lastDigit(raw.text)
                        tens = TextFieldValue(digit, TextRange(digit.length))
                        // Der Sprung ist die Antwort auf „welche Ziffer zuerst?":
                        // erst der Zehner, dann wandert der Fokus von selbst weiter.
                        if (digit.isNotEmpty()) onesFocus.requestFocus()
                    },
                    onSelectAll = { tens = tens.copy(selection = TextRange(0, tens.text.length)) },
                    focusRequester = tensFocus,
                    slot = slot,
                    solved = solved,
                    locked = locked,
                    enabled = enabled,
                    imeAction = ImeAction.Next,
                    onImeAction = { onesFocus.requestFocus() },
                    tag = "place_value_tens",
                )
            } else {
                Spacer(Modifier.width(slot))
            }
            DigitField(
                value = ones,
                onValueChange = { raw ->
                    val digit = PlaceValueInput.lastDigit(raw.text)
                    ones = TextFieldValue(digit, TextRange(digit.length))
                },
                onSelectAll = { ones = ones.copy(selection = TextRange(0, ones.text.length)) },
                // „Fokus startet im Zehnerfeld": bekommt das Einerfeld den Fokus,
                // während der Zehner noch leer ist, wandert er dorthin zurück. Nur
                // auf Fokus*gewinn* — beim Rundenaufbau fordert niemand Fokus an,
                // und genau das muss so bleiben, sonst klappt die System-Tastatur
                // über das Zehnerfeld, an dem das Kind rechnet.
                //
                // Keine Fokus-Schaukel: der Vorwärtssprung aus dem Zehnerfeld
                // passiert erst, nachdem dort eine Ziffer steht — dann ist
                // `tens.text` nicht mehr leer und das Einerfeld behält den Fokus.
                onFocusGained = {
                    val toTens = fields == 2 && tens.text.isEmpty()
                    if (toTens) tensFocus.requestFocus()
                    toTens
                },
                focusRequester = onesFocus,
                slot = slot,
                solved = solved,
                locked = locked,
                enabled = enabled,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                // Rücktaste im leeren Einerfeld springt zurück in den Zehner —
                // sonst wäre die Zehnerziffer nur über einen genauen Tipp auf einen
                // kleinen Kasten erreichbar.
                onBackspaceWhenEmpty = { if (fields == 2) tensFocus.requestFocus() },
                tag = "place_value_ones",
            )
            Surface(
                onClick = { submit() },
                enabled = enabled && complete && !solved && !locked,
                shape = RoundedCornerShape(20.dp),
                color = SunCoral,
                modifier = Modifier
                    .size(ArrowSlot)
                    // Blass, solange eine Ziffer fehlt: der Pfeil zeigt, dass die
                    // Antwort noch nicht vollständig ist, statt eine halbe Zahl
                    // abzuschicken.
                    .alpha(if (complete) 1f else 0.4f)
                    .testTag("place_value_submit"),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    IconChevronRight(tint = Cream, size = 28.dp)
                }
            }
        }
    }
}

/** Eine Ziffernzeile der Aufgabe, in derselben Spaltenfolge wie die Eingabe. */
@Composable
private fun PlaceValueDigits(operator: String, value: Int, slot: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Digit(operator, OperatorSlot)
        Digit(if (value >= PlaceValueInput.TensFrom) (value / 10).toString() else "", slot)
        Digit((value % 10).toString(), slot)
        Spacer(Modifier.width(ArrowSlot))
    }
}

@Composable
private fun Digit(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        color = WarmInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width),
    )
}

/** Ein Ziffernkasten. Zustandslos — sein Wert liegt in [PlaceValueAnswer]. */
@Composable
private fun DigitField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSelectAll: () -> Unit,
    focusRequester: FocusRequester,
    /** Läuft bei Fokusgewinn; `true` heißt „der Fokus ist weitergereicht worden". */
    onFocusGained: () -> Boolean = { false },
    slot: Dp,
    solved: Boolean,
    locked: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    tag: String,
    onBackspaceWhenEmpty: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(slot)
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                if (!focus.isFocused) return@onFocusChanged
                if (onFocusGained()) return@onFocusChanged
                // Beim Antippen wird der Inhalt markiert, damit die nächste Ziffer
                // ihn ersetzt — ein Kind soll nicht erst löschen müssen.
                if (value.text.isNotEmpty()) onSelectAll()
            }
            .onPreviewKeyEvent { event ->
                if (
                    onBackspaceWhenEmpty != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.Backspace &&
                    value.text.isEmpty()
                ) {
                    onBackspaceWhenEmpty()
                    true
                } else {
                    false
                }
            }
            .testTag(tag),
        textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center),
        singleLine = true,
        enabled = enabled,
        // Gesperrt wird über `readOnly`, nicht über `enabled`: der Disabled-Zweig
        // von M3 zöge `disabledBorderColor` und färbte die richtige Antwort grau
        // statt grün.
        readOnly = solved || locked,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            // Neutral beim Tippen, damit Grün genau eines heißt: richtig.
            focusedBorderColor = if (solved) LeafGreen else SkyBlue,
            unfocusedBorderColor = if (solved) LeafGreen else SkyBlue.copy(alpha = 0.5f),
            focusedTextColor = WarmInk,
            unfocusedTextColor = WarmInk,
            disabledBorderColor = SkyBlue.copy(alpha = 0.5f),
            disabledTextColor = WarmInk,
        ),
    )
}
