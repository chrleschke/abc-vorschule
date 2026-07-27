package app.abcvorschule.ui.exercise

import org.junit.Assert.assertTrue
import org.junit.Test

class SyllableFrameSizingTest {
    @Test fun compoundSyllablesReceiveMoreRoomThanSingleLetters() {
        assertTrue(SyllableFrameSizing.widthDp("Schu") > SyllableFrameSizing.widthDp("u"))
    }
}
