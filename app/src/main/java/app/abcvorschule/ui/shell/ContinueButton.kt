package app.abcvorschule.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.abcvorschule.R
import app.abcvorschule.ui.components.AbcContinueButton

@Composable
fun ContinueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.continue_label),
) {
    AbcContinueButton(onClick = onClick, modifier = modifier, label = label)
}
