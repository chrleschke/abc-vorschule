package app.abcvorschule

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ohne ausdrücklichen `windowSoftInputMode` steht das Fenster auf
 * `SOFT_INPUT_ADJUST_UNSPECIFIED`, und dann darf das System selbst wählen — auf
 * einem Motorola edge 60 pro (Android 16) wählt es **Pan**: beim Aufklappen der
 * System-Zahlentastatur (§8, Rechnen ab Ergebnis 11) schiebt es das ganze Fenster
 * nach oben, bis das Eingabefeld frei liegt. Kopfzeile, Punktestand und
 * Fortschrittszeile wandern dabei komplett aus dem Bild — auf dem Emulator
 * (Android 17) fiel das nie auf, dort blieb das Fenster stehen.
 *
 * `adjustResize` nimmt dem System diese Wahl. Verschoben wird nichts mehr; die
 * Tastatur kommt nur noch als Inset an, und den verbraucht die Übungsfläche in
 * `TaskShell` selbst (`safeDrawing.only(Bottom)`) — die Kopfzeile bleibt stehen.
 *
 * Als Unit-Test statt Instrumented-Test: die Regel steht im Manifest, und der
 * geteilte Emulator ist für eine Manifest-Zeile die teuerste denkbare Prüfung.
 */
class ManifestSoftInputTest {

    @Test
    fun mainActivityResizesForTheKeyboardInsteadOfPanningTheWholeWindow() {
        val manifest = manifestFile().readText()
        assertTrue(
            "MainActivity braucht android:windowSoftInputMode=\"adjustResize\", sonst " +
                "pant Android die Kopfzeile aus dem Bild, wenn die Zahlentastatur aufklappt",
            manifest.contains("android:windowSoftInputMode=\"adjustResize\""),
        )
    }

    /**
     * Der Test läuft im Modulverzeichnis (`app/`), die IDE startet ihn aber auch
     * gern aus dem Repo-Wurzelverzeichnis. Also von hier aus nach oben suchen,
     * statt einen der beiden Pfade fest zu verdrahten.
     */
    private fun manifestFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val direct = File(dir, "src/main/AndroidManifest.xml")
            if (direct.isFile) return direct
            val fromRoot = File(dir, "app/src/main/AndroidManifest.xml")
            if (fromRoot.isFile) return fromRoot
            dir = dir.parentFile
        }
        throw AssertionError("AndroidManifest.xml nicht gefunden, gestartet in ${File("").absolutePath}")
    }
}
