package app.abcvorschule.ui.exercise

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
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

/**
 * Numeric answer field backed by the device's own keyboard (number mode) —
 * more reliable for kids than a custom on-screen keypad, and it just works
 * with whatever input method/accessibility tooling is installed.
 */
@Composable
fun NumberPad(
    onSubmit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** True once the typed number turned out to be the answer — the field confirms in green. */
    solved: Boolean = false,
) {
    var value by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit() {
        value.toIntOrNull()?.let(onSubmit)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(solved) {
        // Without closing the IME the green confirmation sits behind the keyboard —
        // exactly the thing it is supposed to show.
        if (solved) keyboardController?.hide()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> value = input.filter(Char::isDigit).take(3) },
            modifier = Modifier
                .width(140.dp)
                .focusRequester(focusRequester)
                .testTag("number_input"),
            textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center),
            singleLine = true,
            readOnly = solved,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            // Neutral while typing so that green means one thing only: correct.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (solved) SoftMint else SoftSky,
                unfocusedBorderColor = if (solved) SoftMint else SoftSky.copy(alpha = 0.5f),
                focusedTextColor = if (solved) SoftMint else SoftSand,
                unfocusedTextColor = if (solved) SoftMint else SoftSand,
            ),
        )
        Spacer(Modifier.width(16.dp))
        Surface(
            onClick = { submit() },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(AbcDimens.kidTouch - 8.dp)
                .testTag("number_submit"),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IconChevronRight(tint = MaterialTheme.colorScheme.onPrimary, size = 28.dp)
            }
        }
    }
}
