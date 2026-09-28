package app.abcvorschule.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Übergabe vom Splash an die App. Der Splash liegt über allem, bis diese
 * Bedingung kippt — sie ist damit die einzige Stelle, an der ein Startbild für
 * immer stehenbleiben könnte.
 */
class SplashHandoffTest {

    @Test
    fun frischerStartHaeltDenSplash() {
        assertFalse(SessionUiState().hasShowableContent(packLoaded = false))
    }

    @Test
    fun readyOhnePackHaeltDenSplash() {
        assertFalse(SessionUiState(ready = true).hasShowableContent(packLoaded = false))
    }

    @Test
    fun packOhneReadyHaeltDenSplash() {
        assertFalse(SessionUiState(ready = false).hasShowableContent(packLoaded = true))
    }

    @Test
    fun readyMitPackGibtFrei() {
        assertTrue(SessionUiState(ready = true).hasShowableContent(packLoaded = true))
    }

    /**
     * Der Grund, warum der Fehlerzweig überhaupt in die Bedingung gehört: hier
     * bleibt `ready` dauerhaft false, und ohne diesen Fall sähe das Kind nie die
     * Meldung, die TaskShell dafür bereithält — nur den Splash.
     */
    @Test
    fun ladefehlerGibtFreiObwohlReadyFalseBleibt() {
        val state = SessionUiState(ready = false, error = "Inhalt konnte nicht geladen werden")
        assertTrue(state.hasShowableContent(packLoaded = false))
    }
}
