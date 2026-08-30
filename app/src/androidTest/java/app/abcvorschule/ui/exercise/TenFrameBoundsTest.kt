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
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
        // Einmal synchronisieren: auf dem Testgerät kehrt `setContent` gelegentlich
        // zurück, bevor das Fenster der Testaktivität angehängt ist, und die erste
        // Messung fände dann keine Compose-Hierarchie.
        rule.waitForIdle()
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
    fun theActiveCellIsBigEnoughForAChildsFingerOnTheNarrowPhone() {
        // Die JVM-Rechnung (TenFrameTest) prüft nur, dass `hitTargetDp` groß genug
        // *rechnet*. Sie lief an dem Fehler vorbei, der hier saß: die klickbare Box
        // steckte in einer `size(cell)`-Box und wurde von deren Constraints wieder
        // auf Zellgröße geklemmt — ~27dp statt 40dp, auf jedem Gerät. Nur die
        // gemessenen Bounds des gerenderten Knotens zeigen das.
        stage(left = 16, right = 8)
        val active = rule.onNodeWithTag("ten_frame_cell_16").getUnclippedBoundsInRoot()
        assertTrue(
            "aktive Zelle $active ist schmaler als ${TenFrame.MinHitTargetDp}dp",
            active.width.value >= TenFrame.MinHitTargetDp,
        )
        assertTrue(
            "aktive Zelle $active ist flacher als ${TenFrame.MinHitTargetDp}dp",
            active.height.value >= TenFrame.MinHitTargetDp,
        )

        // Gegenprobe: eine *nicht* aktive Zelle bleibt zellgroß. Ohne sie wäre oben
        // auch ein Feld grün, das schlicht alle Zellen auf 40dp aufbläst — und das
        // wäre ein anderes, breiteres Feld als das gerechnete.
        val quiet = rule.onNodeWithTag("ten_frame_cell_17").getUnclippedBoundsInRoot()
        assertTrue(
            "Nachbarzelle $quiet ist so groß wie die aktive",
            quiet.width.value < active.width.value,
        )
    }

    @Test
    fun onlyTheNextPlaceholderReactsAndTheFrameGrowsToTwenty() {
        // Alle Callback-Aufrufe sammeln statt nur den letzten Zustand zu halten:
        // ein Callback, der nie aufgerufen wird, sähe sonst genauso aus wie einer,
        // der korrekt mit unverändertem Zustand aufgerufen wird — die Liste macht
        // "nichts passiert" von "nichts wurde geprüft" unterscheidbar.
        val taps = mutableListOf<TenFrameState>()
        stage(left = 16, right = 8) { taps.add(it) }

        // Ein Tipp auf ein gesetztes Objekt tut nichts. Die Zelle trägt dafür gar
        // keinen Klick-Handler (siehe TenFrameBoard.TenFrameCell) — `performClick`
        // simuliert einen echten Tipp auf die Bildschirmmitte dieser Zelle und
        // findet dort niemanden, der reagiert. Das prüft nur die Widerrufs-Regel
        // (§ TenFrameState: "kein Widerruf"), nicht die Reihenfolge.
        rule.onNodeWithTag("ten_frame_cell_0").performClick()
        rule.waitForIdle()
        assertTrue("taps=$taps", taps.isEmpty())

        // Der Fall, um den es bei "nur der nächste Platzhalter reagiert" eigentlich
        // geht: Zelle 20 ist ein Platzhalter (noch nicht real), aber nicht der
        // nächste — nextIndex ist 16, in der zweiten Zeile, Zelle 20 liegt bereits
        // in der dritten. Eine kaputte Reihenfolge-Prüfung (z. B. "irgendein noch
        // offener Platzhalter reagiert") würde genau hier zuschlagen; der Test auf
        // Zelle 0 oben kann das nicht zeigen, weil der dort schon aus einem
        // anderen Grund (kein Handler) nichts tut.
        rule.onNodeWithTag("ten_frame_cell_20").performClick()
        rule.waitForIdle()
        assertTrue("taps=$taps", taps.isEmpty())

        // Positive Kontrolle: erst dieser Tipp beweist, dass der Callback in
        // dieser Anordnung überhaupt feuern kann. Ohne ihn wären die beiden
        // leeren Erwartungen oben tautologisch — sie würden genauso aussehen,
        // wenn `onTap` nie verdrahtet worden wäre.
        rule.onNodeWithTag("ten_frame_cell_16").performClick()
        rule.waitForIdle()
        assertTrue("taps=$taps", taps.size == 1)
        assertTrue("filled=${taps.single().filled}", taps.single().filled == 1)

        // Drei weitere Tipps auf den jeweils nächsten Platzhalter machen den
        // zweiten Zehner voll.
        (17..19).forEach { index ->
            rule.onNodeWithTag("ten_frame_cell_$index").performClick()
            rule.waitForIdle()
        }
        // Die Marke der *zweiten* vollen Zeile — „20", die Zahl, die beim
        // Einrasten gesprochen wird. Die Marke der ersten Zeile („10") steht seit
        // dem Rundenstart da, sie bewiese nichts.
        rule.onNodeWithTag("ten_frame_total_1").assertExists()
        val last = taps.last()
        assertTrue("filled=${last.filled}", last.filled == 4)
        assertTrue("tens=${last.fullTens}", last.fullTens == 20)
    }
}
