package app.abcvorschule.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.WarmInk

/** Kantenlänge des Sterns neben der Zahl. */
private val StarGlyphSize = 22.dp

/**
 * Natürliche Zeilenhöhe der Sans, wenn der Textstil keine eigene `lineHeight`
 * mitbringt (`titleLarge` tut das nicht).
 */
private const val NaturalLineHeightFactor = 1.25f

/**
 * Höhe, die eine [AbcStarCount]-Zeile im Layout einnimmt — der Stern oder die
 * Zahl daneben, je nachdem, was höher ist. Ausgerechnet statt als feste Zahl
 * hinterlegt: die Zahl wächst mit der Systemschriftgröße, und wer den Platz
 * nachrechnet, den der Punktestand freigibt (siehe `TaskShell`), verschätzt
 * sich mit einer festen Zahl genau dort um mehrere dp.
 */
@Composable
fun abcStarCountHeight(): Dp {
    val style = MaterialTheme.typography.titleLarge
    val textHeight = with(LocalDensity.current) {
        when {
            style.lineHeight.isSp -> style.lineHeight.toDp()
            style.fontSize.isSp -> style.fontSize.toDp() * NaturalLineHeightFactor
            else -> StarGlyphSize
        }
    }
    return maxOf(StarGlyphSize, textHeight)
}

/**
 * Der Punktestand: ein Stern und eine Zahl, nichts weiter. Ein Element für beide
 * Orte, an denen er steht — rechts in der Pfad-Kopfzeile und mittig in der
 * Kopfzeile der Lektion —, damit er nicht an einem Ort mitwächst und am
 * anderen nicht.
 *
 * @param outline Kontur des Sterns. Default passt auf Cream; über dem Pfad-Himmel
 * muss der Aufrufer sie überschreiben (siehe `PathScreen`).
 */
@Composable
fun AbcStarCount(
    points: Int,
    modifier: Modifier = Modifier,
    outline: Color = StarGoldDeep,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconStar(tint = StarGold, outline = outline, size = StarGlyphSize)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$points",
            style = MaterialTheme.typography.titleLarge,
            color = WarmInk,
        )
    }
}
