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
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.WarmInk

/**
 * Der Punktestand: ein Stern und eine Zahl, nichts weiter. Ein Element für beide
 * Orte, an denen er steht — rechts in der Pfad-Kopfzeile und mittig unter dem
 * Fortschritt in der Lektion —, damit er nicht an einem Ort mitwächst und am
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
        IconStar(tint = StarGold, outline = outline, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$points",
            style = MaterialTheme.typography.titleLarge,
            color = WarmInk,
        )
    }
}
