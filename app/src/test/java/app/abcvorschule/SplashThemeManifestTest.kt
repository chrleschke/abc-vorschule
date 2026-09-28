package app.abcvorschule

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `android:theme="@style/Theme.AbcVorschule.Splash"` an der Launcher-Activity ist
 * die einzige Stelle, die den Splash-Screen tatsächlich einschaltet —
 * `installSplashScreen()` in `MainActivity` bleibt zur Laufzeit wirkungslos, wenn
 * das Manifest stattdessen das normale `Theme.AbcVorschule` trägt. Kotlin-Code,
 * Themes und Tests könnten dann alle unverändert grün sein, während am Gerät der
 * alte Kaltstart (schwarzer Screen bzw. „Silbo …") wieder auftaucht — diese eine
 * Manifest-Zeile fällt sonst durch kein Review und keinen anderen Test auf.
 *
 * Als Unit-Test statt Instrumented-Test: die Regel steht im Manifest, und der
 * geteilte Emulator ist für eine Manifest-Zeile die teuerste denkbare Prüfung
 * (siehe `ManifestSoftInputTest`, gleiches Muster).
 */
class SplashThemeManifestTest {

    @Test
    fun mainActivityStartsWithTheSplashTheme() {
        val manifest = manifestFile().readText()
        assertTrue(
            "MainActivity braucht android:theme=\"@style/Theme.AbcVorschule.Splash\", sonst " +
                "bleibt installSplashScreen() wirkungslos und der alte Kaltstart kommt zurück",
            manifest.contains("android:theme=\"@style/Theme.AbcVorschule.Splash\""),
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
