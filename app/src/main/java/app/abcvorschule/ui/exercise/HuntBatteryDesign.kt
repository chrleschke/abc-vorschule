package app.abcvorschule.ui.exercise

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.ChargeHigh
import app.abcvorschule.ui.theme.ChargeLow
import app.abcvorschule.ui.theme.ChargeMid

/**
 * Maße und Töne der Jagd-Batterie (PRODUCT_PRINCIPLES §10). Reine Werte, damit
 * das Gehäuse an einer Stelle beschrieben ist und ein Test die Zusagen halten
 * kann — gezeichnet wird in [SymbolHuntBattery].
 *
 * Die Batterie war vorher eine Reihe gleich großer Rechtecke ohne Gehäuse: drei
 * bzw. fünf Kacheln, grün wenn gefüllt, gold wenn voll. Sie sah damit aus wie
 * ein Streifen Kacheln, nicht wie eine Batterie, und der volle Zustand fiel aus
 * der Farblogik der App heraus (Gold = Stern/Belohnung, nicht Ladezustand).
 * Jetzt: ein Gehäuse mit Pluspol, darin eine dunkle Wanne, darin die Balken —
 * jeder Balken in seinem eigenen, nach rechts heller werdenden Grün
 * ([shadeFor]), voll heißt hell und einheitlich [ChargeHigh] plus Blitz.
 */
object HuntBatteryDesign {
    /** Ein Balken. Breiter als hoch wäre eine Kachel — hoch und schlank liest sich als Zelle. */
    val CellWidth: Dp = 22.dp
    val CellHeight: Dp = 34.dp
    val CellGap: Dp = 5.dp
    val CellCorner: Dp = 4.dp

    /** Wandstärke des Gehäuses und Luft zwischen Wanne und Balken. */
    val CasingThickness: Dp = 5.dp
    val WellPadding: Dp = 3.dp
    val BodyCorner: Dp = 11.dp
    val WellCorner: Dp = 8.dp

    /** Pluspol rechts — die eine Form, an der eine Batterie ohne Text erkennbar ist. */
    val NubWidth: Dp = 7.dp
    val NubHeight: Dp = 16.dp
    val NubCorner: Dp = 3.dp

    /**
     * Wie weit ein gefüllter Balken in die Wanne hinein leuchtet. Darf
     * [WellPadding] nie überschreiten, sonst leuchtet Grün auf das Gehäuse statt
     * in die Wanne — [HuntBatteryDesignTest] hält das fest.
     */
    val GlowBleed: Dp = 3.dp

    /** Rand eines leeren Platzes; er trägt die 3:1-Grenze (siehe [SymbolHuntBattery]). */
    val EmptyCellStroke: Dp = 2.dp

    /** Ring um das Gehäuse, wenn die Batterie voll ist. */
    val HaloWidth: Dp = 7.dp

    /** Gehäusewand plus Luft — der Rand zwischen Balkenfeld und Außenkante. */
    val Rim: Dp = CasingThickness + WellPadding

    fun bodyWidth(total: Int): Dp =
        CellWidth * total + CellGap * (total - 1).coerceAtLeast(0) + Rim * 2

    val BodyHeight: Dp = CellHeight + Rim * 2

    /** Gesamtbreite inklusive Pluspol. */
    fun totalWidth(total: Int): Dp = bodyWidth(total) + NubWidth

    /**
     * Ton des Balkens an Position [index] von [total]. Zwei Strecken über drei
     * Stützstellen statt einer geraden Interpolation von unten nach oben: eine
     * einzige Strecke durch den Mittelton würde bei fünf Balken zwei fast
     * gleiche Nachbarn erzeugen, weil ChargeMid nicht in der Mitte zwischen den
     * Enden liegt.
     */
    fun shadeFor(index: Int, total: Int): Color {
        if (total <= 1) return ChargeHigh
        val f = index.coerceIn(0, total - 1).toFloat() / (total - 1)
        return if (f <= 0.5f) {
            lerp(ChargeLow, ChargeMid, f / 0.5f)
        } else {
            lerp(ChargeMid, ChargeHigh, (f - 0.5f) / 0.5f)
        }
    }

    /**
     * Oberkante eines Balkens: derselbe Ton, aufgehellt. Nur nach oben — die
     * flache Farbe bleibt die dunkelste Stelle des Balkens und damit die
     * Untergrenze der in Color.kt gemessenen Kontraste.
     */
    fun cellHighlight(shade: Color): Color = lerp(shade, Color.White, 0.28f)

    /**
     * Blitz für den vollen Zustand, in Anteilen der Blitzfläche (0..1, y nach
     * unten). Ein Kind kann „voll" nicht lesen — der Blitz ist das Wort dafür.
     */
    val BoltPath: List<Pair<Float, Float>> = listOf(
        0.62f to 0.00f,
        0.10f to 0.56f,
        0.42f to 0.56f,
        0.34f to 1.00f,
        0.90f to 0.40f,
        0.56f to 0.40f,
    )
}
