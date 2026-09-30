package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.theme.Cream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beleg statt Rechnung (gleiche Bauart wie [SymbolHuntMorphShotTest]): die
 * Mengenwahl auf echten Bühnengrößen. Bühne = Bildschirmbreite minus
 * `AbcDimens.screenHorizontal` je Seite, ein 360dp-Telefon hat also 280dp. Keine
 * Assertion — die prüfen [MathBoardSizingTest] und [MathBoardBoundsTest]; hier
 * geht es darum, dass die Aufgabe über den Antworten auch wirklich zu lesen ist.
 */
@RunWith(AndroidJUnit4::class)
class MathBoardShotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun captureFirstLessonRounds() {
        val dir = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: InstrumentationRegistry.getInstrumentation().targetContext
                    .getExternalFilesDir(null)?.path
                ?: error("Kein Ordner für die Aufnahme"),
            "mathboardshots",
        ).apply { require(mkdirs() || isDirectory) { "Kein Zielordner für die Aufnahme: $this" } }

        // Bühnenbreite = Bildschirmbreite - 2 * AbcDimens.screenHorizontal.
        // 320dp entspricht einem 360dp-Telefon.
        var case by mutableStateOf(cases.first())

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = case.fontScale),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = case.width, height = case.height)
                            .background(Cream)
                            .testTag("math_stage"),
                    ) {
                        VisualQuantityBoard(
                            left = case.left,
                            right = case.right,
                            operation = MathOperation.Add,
                            choices = case.choices,
                            onChoose = {},
                            ttsAvailable = true,
                        )
                    }
                }
            }
        }

        cases.forEach { current ->
            case = current
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag("math_stage").captureToImage().asAndroidBitmap()
            File(dir, "${current.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private data class Case(
        val name: String,
        val left: Int,
        val right: Int,
        val choices: List<Int>,
        val width: Dp,
        val height: Dp,
        val fontScale: Float,
    )

    private val cases = listOf(
        // Die beiden Rechenrunden der ersten Lektion auf einem 360dp-Telefon
        // (280dp Bühne) und einem 412dp-Telefon (372dp), bei der Schriftgröße des
        // Testgeräts und bei 1.0.
        Case("l01-t9-3plus2-280", 3, 2, listOf(4, 5, 6), 280.dp, 600.dp, 1.3f),
        Case("l01-t10-4plus3-280", 4, 3, listOf(6, 7, 8), 280.dp, 600.dp, 1.3f),
        Case("l01-t10-4plus3-280-fs1", 4, 3, listOf(6, 7, 8), 280.dp, 600.dp, 1.0f),
        Case("l01-t10-4plus3-372", 4, 3, listOf(6, 7, 8), 372.dp, 780.dp, 1.3f),
        // Der höchste Kachelstapel, den eine Runde mit Bildwort haben kann.
        Case("tallest-5plus5-280", 5, 5, listOf(8, 9, 10), 280.dp, 600.dp, 1.3f),
        // Kurzes Gerät bei großer Systemschrift: hier fällt die Runde auf das Symbol.
        Case("tight-5plus5-280-fs2", 5, 5, listOf(8, 9, 10), 280.dp, 480.dp, 2.0f),
    )
}
