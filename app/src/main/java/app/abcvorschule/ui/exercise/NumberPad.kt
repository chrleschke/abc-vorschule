package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.IconChevronRight
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk

/**
 * Numeric answer field backed by the device's own keyboard (number mode) —
 * more reliable for kids than a custom on-screen keypad, and it just works
 * with whatever input method/accessibility tooling is installed.
 */
@Composable
fun NumberPad(
    onSubmit: (Int) -> Unit,
    /** Changing this clears the field — a new round, or another wrong try. */
    resetToken: String,
    modifier: Modifier = Modifier,
    /** True once the typed number turned out to be the answer — the field confirms in green. */
    solved: Boolean = false,
    /** False during the audio lock — field and submit button are non-interactive
     * and dimmed, and focus/keyboard are deferred until this turns true. */
    enabled: Boolean = true,
) {
    var value by remember(resetToken) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200),
        label = "number_pad_lock_opacity",
    )

    fun submit() {
        value.toIntOrNull()?.let(onSubmit)
    }

    LaunchedEffect(enabled) {
        // Deferred rather than Unit-keyed: while locked the keyboard must not pop
        // up before the child is allowed to type (design doc).
        if (enabled) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(solved) {
        // Without closing the IME the green confirmation sits behind the keyboard —
        // exactly the thing it is supposed to show.
        if (solved) keyboardController?.hide()
    }

    Row(
        modifier = modifier.fillMaxWidth().alpha(opacity),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> value = NumberPadInput.sanitize(input) },
            modifier = Modifier
                .width(140.dp)
                .focusRequester(focusRequester)
                .testTag("number_input"),
            textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center),
            singleLine = true,
            enabled = enabled,
            readOnly = solved,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            // Neutral while typing so that green means one thing only: correct.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (solved) LeafGreen else SkyBlue,
                unfocusedBorderColor = if (solved) LeafGreen else SkyBlue.copy(alpha = 0.5f),
                focusedTextColor = WarmInk,
                unfocusedTextColor = WarmInk,
            ),
        )
        Spacer(Modifier.width(16.dp))
        Surface(
            onClick = { submit() },
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            color = SunCoral,
            modifier = Modifier
                .size(AbcDimens.kidTouch - 8.dp)
                .testTag("number_submit"),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IconChevronRight(tint = Cream, size = 28.dp)
            }
        }
    }
}
