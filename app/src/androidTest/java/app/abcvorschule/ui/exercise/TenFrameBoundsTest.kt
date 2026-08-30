package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zehn Objekte nebeneinander sind der enge Fall des Zehnerfeldes. Gemessen wird
 * gegen das schmale Telefon bei font_scale 1.3 — das Testgerät —, denn dort
 * entscheidet sich, ob das zehnte Objekt noch im Feld steht.
 */
@RunWith(AndroidJUnit4::class)
class TenFrameBoundsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun stage(left: Int, right: Int, onTapped: (TenFrameState) -> Unit = {}) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                AbcTheme {
                    var state by remember { mutableStateOf(TenFrameState.forRound(left, right)) }
                    Box(Modifier.size(width = 328.dp, height = 400.dp)) {
                        TenFrameBoard(
                            emoji = "🍎",
                            state = state,
                            onTap = { index ->
                                state = state.tap(index)
                                onTapped(state)
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theTenthObjectOfARowStaysInsideTheField() {
        stage(left = 16, right = 8)
        val field = rule.onNodeWithTag("ten_frame").getUnclippedBoundsInRoot()
        val tenth = rule.onNodeWithTag("ten_frame_cell_9").getUnclippedBoundsInRoot()
        assertTrue("$tenth vs $field", tenth.right <= field.right)
        assertTrue("$tenth vs $field", tenth.left >= field.left)
    }

    @Test
    fun onlyTheNextPlaceholderReactsAndTheFrameGrowsToTwenty() {
        // `state` selbst ist die Quelle der Wahrheit für das, was ein Tipp bewirkt
        // — `latest` ist nur ihr Fenster nach außen. Der Schreibzugriff im
        // onTap-Callback läuft synchron im selben Snapshot wie der anschließende
        // Lesezugriff hier, also ist kein Recompose zwischen Tipp und Prüfung
        // nötig; `waitForIdle()` bleibt trotzdem als Netz für die Gesten-Erkennung
        // von `clickable`, die intern eine Coroutine startet.
        var latest: TenFrameState? = null
        stage(left = 16, right = 8) { latest = it }
        // Ein Tipp auf ein gesetztes Objekt tut nichts. Die Zelle trägt dafür gar
        // keinen Klick-Handler (siehe TenFrameBoard.TenFrameCell) — `performClick`
        // simuliert einen echten Tipp auf die Bildschirmmitte dieser Zelle und
        // findet dort niemanden, der reagiert.
        rule.onNodeWithTag("ten_frame_cell_0").performClick()
        rule.waitForIdle()
        assertTrue(latest == null || latest!!.filled == 0)
        // Vier Tipps auf den jeweils nächsten Platzhalter machen den Zehner voll.
        // Dass die Trefferfläche der aktiven Zelle über ihre Nachbarn hinausragt
        // (TenFrame.hitTargetDp), ändert daran nichts: `performClick` trifft die
        // Mitte genau der per Test-Tag gefundenen Zelle, und zu jedem Zeitpunkt
        // trägt nur diese eine Zelle überhaupt einen Klick-Handler — Nachbarn
        // haben in der Überlappung keinen, mit dem sie konkurrieren könnten.
        (16..19).forEach { index ->
            rule.onNodeWithTag("ten_frame_cell_$index").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("ten_frame_total").assertExists()
        assertTrue("filled=${latest?.filled}", latest?.filled == 4)
        assertTrue("tens=${latest?.fullTens}", latest?.fullTens == 20)
    }
}
