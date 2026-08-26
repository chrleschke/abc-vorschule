package app.abcvorschule.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.IconClose
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.R
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.WarmInk

/** Kantenlänge des schwebenden ⋯-Knopfs. */
val TopBarFloatingActionSize: Dp = 48.dp

/**
 * Platz, den der schwebende ⋯-Knopf im Titel-Slot freihalten muss: seine Breite,
 * sein Abstand zur Bildschirmkante und eine Handbreit Luft. Ausgerechnet statt
 * geschätzt — mit einer geratenen Zahl lag die Punktezahl hinter dem Knopf.
 */
val TopBarFloatingActionReserve: Dp =
    TopBarFloatingActionSize + AbcDimens.screenHorizontal + 8.dp

/**
 * Die Kopfzeile der App: eine native M3-[TopAppBar], durchsichtig, damit
 * Landschaft bzw. Hintergrund unter ihr und unter der Status-Bar durchlaufen.
 *
 * Die Punkte stehen im Titel-Slot, nicht in `actions` — dort sitzt auf dem Pfad
 * der schwebende ⋯-Knopf, der kein Bar-Element ist. Damit steht der Stern auf
 * beiden Screens an derselben Stelle.
 *
 * @param title Elternseitiges Lektions-Label (z. B. „M & A"), auf dem Pfad null.
 * Es ist keine Anweisung ans Kind und wird nicht vorgesprochen.
 * @param starOutline Kontur des Sterns. Default passt auf Cream; über dem
 * Pfad-Himmel muss der Aufrufer sie überschreiben (siehe `PathScreen`).
 * @param endReserve Freiraum am Titelende für einen schwebenden Knopf darüber.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbcTopBar(
    points: Int,
    modifier: Modifier = Modifier,
    title: String? = null,
    onClose: (() -> Unit)? = null,
    starOutline: Color = StarGoldDeep,
    endReserve: Dp = 0.dp,
) {
    TopAppBar(
        modifier = modifier,
        // safeDrawing statt der Vorgabe (systemBars): im Vollbild ist der
        // Status-Bar-Inset null, ein Display-Ausschnitt bleibt aber bestehen —
        // und darunter darf der Titel nicht liegen.
        // Plus eine feste Handbreit oben: im Vollbild ist der Status-Bar-Inset
        // null, und ohne sie klebte die Titelzeile an der physischen Kante.
        windowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            .add(WindowInsets(top = 10.dp)),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = WarmInk,
            navigationIconContentColor = WarmInk,
        ),
        navigationIcon = {
            if (onClose != null) {
                // Bewusst ohne Gehäuse: das Navigations-Icon einer M3-Bar ist ein
                // nackter IconButton. Ein gefüllter Kreis wäre der einzige
                // Knopf-Kasten in einer sonst durchsichtigen Leiste.
                val description = stringResource(R.string.close_lesson)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    IconClose(tint = WarmInk, size = 24.dp)
                }
            }
        },
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = endReserve),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Der Titel nimmt den ganzen Rest ein und kürzt sich notfalls
                // selbst; ohne Titel schiebt der Freiraum die Punkte trotzdem an
                // dieselbe rechte Kante wie in der Lektion.
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = WarmInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconStar(tint = StarGold, outline = starOutline, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$points",
                    style = MaterialTheme.typography.titleLarge,
                    color = WarmInk,
                )
            }
        },
    )
}
