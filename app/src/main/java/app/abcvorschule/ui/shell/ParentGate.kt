package app.abcvorschule.ui.shell

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val ParentGateMs = 1500L

@Composable
fun ParentGateButton(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    val gateDescription = stringResource(R.string.parent_gate_description)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .size(48.dp)
            .testTag("parent_gate")
            // Der rohe pointerInput ist für TalkBack unsichtbar; die Semantik macht
            // die einzige Einstellungstür (§6) als Button mit Long-Click-Aktion
            // bedienbar, ohne die Kindersicherung (langer Druck) aufzuweichen.
            .semantics {
                role = Role.Button
                contentDescription = gateDescription
                onLongClick {
                    haptics.tick()
                    onUnlocked()
                    true
                }
            }
            .pointerInput(onUnlocked) {
                detectTapGestures(
                    onPress = {
                        try {
                            withTimeout(ParentGateMs) {
                                tryAwaitRelease()
                            }
                        } catch (_: TimeoutCancellationException) {
                            // Long-press threshold reached: confirm the gesture landed
                            // before the gate unlocks. tick = Einrasten (§10), keine
                            // Korrektur — nudge wäre das falsche Vokabular.
                            haptics.tick()
                            onUnlocked()
                            tryAwaitRelease()
                        }
                    },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "⋯",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
