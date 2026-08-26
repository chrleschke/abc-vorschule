package app.abcvorschule.ui.shell

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * Die einzige Einstellungstür (§6), als schwebender Knopf über dem Inhalt — er
 * gehört keiner Bar an und wird deshalb rund gezeichnet, mit einem Schatten, der
 * ihn von der Landschaft dahinter abhebt.
 *
 * Das Zeichen ist das native Overflow-Icon (drei senkrechte Punkte,
 * `Icons.Rounded.MoreVert`) statt eines getippten ⋯: Erwachsene erkennen daran
 * ohne Text, dass hier ein Menü liegt, und ein Vektor wächst nicht mit der
 * Schriftskalierung aus dem 48-dp-Knopf heraus, wie es der Glyph tat.
 *
 * Nur auf dem Pfad-Screen. In der Lektion gibt es ihn nicht: dort führt der Weg
 * zu den Eltern-Einstellungen über das Verlassen der Lektion.
 */
@Composable
fun ParentGateButton(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    val gateDescription = stringResource(R.string.parent_gate_description)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = modifier
            .size(TopBarFloatingActionSize)
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
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                // Die Beschreibung sitzt auf dem Knopf selbst (oben), damit
                // TalkBack die Long-Click-Aktion mit ansagt.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
