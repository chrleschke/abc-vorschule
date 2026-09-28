package app.abcvorschule.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.abcvorschule.R

private val LightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Cream,
    secondary = SkyBlue,
    onSecondary = Cream,
    tertiary = SunCoral,
    onTertiary = Cream,
    error = ClayRed,
    onError = Cream,
    background = Cream,
    onBackground = WarmInk,
    surface = CreamPanel,
    onSurface = WarmInk,
    surfaceVariant = CreamElevated,
    onSurfaceVariant = WarmMuted,
    outline = WarmMuted,
    scrim = Color(0x66000000),
)

/**
 * Die Lernschrift (PRODUCT_PRINCIPLES §10, „Schrift"): Andika, gekürzt und umbenannt,
 * mit schlichtem großem I (`tools/fonts/build_fonts.py`). Einstöckiges a und g wie in
 * der Schule, l mit Bogen am Fuß — so sehen Buchstaben, Silben, Wörter und Ziffern in
 * jedem Trainer aus wie in der Fibel, und „I o" liest sich nicht mehr als „lo".
 */
val SilboFibel = FontFamily(
    Font(R.font.silbo_fibel_regular, FontWeight.Normal),
    Font(R.font.silbo_fibel_bold, FontWeight.Bold),
)

/**
 * Die UI-Schrift: Baloo 2, dieselbe runde Schrift wie Store-Grafik und Launcher-Icon.
 * Nur für Beschriftungen, die kein Lerninhalt sind — Punktestand, Knöpfe,
 * Eltern-Bereich, der Jubel-Titel des End-Screens.
 */
@OptIn(ExperimentalTextApi::class)
val SilboUi = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.baloo2,
            weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * Rollen: `display*`, `headline*` und `body*` tragen Lerninhalt und nutzen [SilboFibel];
 * `title*` und `label*` sind UI und nutzen [SilboUi]. Wer Lerninhalt in einer Titel-Rolle
 * setzt (Pfad-Schilder), überschreibt die Familie dort ausdrücklich.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = TextStyle(
            fontFamily = SilboFibel,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 44.sp,
        ),
        displayMedium = base.displayMedium.copy(fontFamily = SilboFibel),
        displaySmall = base.displaySmall.copy(fontFamily = SilboFibel),
        headlineLarge = base.headlineLarge.copy(fontFamily = SilboFibel),
        headlineMedium = TextStyle(
            fontFamily = SilboFibel,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontFamily = SilboFibel),
        titleLarge = TextStyle(
            fontFamily = SilboUi,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        ),
        titleMedium = base.titleMedium.copy(fontFamily = SilboUi),
        titleSmall = base.titleSmall.copy(fontFamily = SilboUi),
        bodyLarge = TextStyle(
            fontFamily = SilboFibel,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        bodyMedium = base.bodyMedium.copy(fontFamily = SilboFibel),
        bodySmall = base.bodySmall.copy(fontFamily = SilboFibel),
        labelLarge = TextStyle(
            fontFamily = SilboUi,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        ),
        labelMedium = base.labelMedium.copy(fontFamily = SilboUi),
        labelSmall = base.labelSmall.copy(fontFamily = SilboUi),
    )
}

@Composable
fun AbcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
