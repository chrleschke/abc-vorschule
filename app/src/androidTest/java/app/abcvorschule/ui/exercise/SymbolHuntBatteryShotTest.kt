package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
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
 * Ladezustände der Jagd-Batterie als Bild — dieselbe Bauart wie
 * [SymbolHuntMorphShotTest]: keine Assertion, sondern ein Beleg, dass Gehäuse,
 * Balkentöne und Vollzustand auf echtem Gerät so aussehen wie beschrieben
 * ([HuntBatteryDesign]). Bei font_scale 1.3 aufgenommen, weil das die Skalierung
 * des Testgeräts ist.
 */
@RunWith(AndroidJUnit4::class)
class SymbolHuntBatteryShotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun captureChargeStates() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                AbcTheme {
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .background(Cream)
                            .padding(vertical = 12.dp)
                            .testTag("battery_states"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        (0..5).forEach { collected ->
                            SymbolHuntBattery(
                                collected = collected,
                                total = 5,
                                celebrate = collected == 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        (0..3).forEach { collected ->
                            SymbolHuntBattery(
                                collected = collected,
                                total = 3,
                                celebrate = collected == 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(250)
        val dir = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: InstrumentationRegistry.getInstrumentation().targetContext
                    .getExternalFilesDir(null)?.path
                ?: error("Kein Ordner für die Aufnahme"),
            "huntbatteryshots",
        ).apply { require(mkdirs() || isDirectory) { "Kein Zielordner für die Aufnahme: $this" } }
        val bitmap = rule.onNodeWithTag("battery_states").captureToImage().asAndroidBitmap()
        File(dir, "charge-states.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
