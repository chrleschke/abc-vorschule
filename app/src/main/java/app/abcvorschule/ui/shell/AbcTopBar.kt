package app.abcvorschule.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.AbcStarCount
import app.abcvorschule.ui.components.IconArrowBack
import app.abcvorschule.R
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.WarmInk

/**
 * Höhe einer kleinen M3-Top-App-Bar (`TopAppBarSmallTokens.ContainerHeight`),
 * ohne den Status-Bar-Inset. Öffentlich, weil der Pfad-Screen die Leiste über
 * seinen Scroll-Inhalt legt und dafür wissen muss, wie viel Platz sie einnimmt.
 */
val TopBarHeight: Dp = 64.dp

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
 * Kein Titel — weder auf dem Pfad noch in der Lektion. Ein Lektionslabel wäre
 * Elterntext an der Stelle, an der das Kind zuerst hinsieht.
 *
 * @param points Punktestand in der Kopfzeile. `null` blendet ihn aus.
 * @param centerPoints Setzt den Punktestand auf die Bildschirmmitte statt an
 * das Titelende. Für die Lektion: dort steht der Stern auf der Achse, auf der
 * am Ende des Trainers auch der große Stern hochkommt.
 * @param onBack Zurück zum Pfad. `null` auf dem Pfad selbst — dort gibt es
 * nichts, wohin zurück.
 * @param starOutline Kontur des Sterns. Default passt auf Cream; über dem
 * Pfad-Himmel muss der Aufrufer sie überschreiben (siehe `PathScreen`).
 * @param endReserve Freiraum am Titelende für einen schwebenden Knopf darüber.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbcTopBar(
    modifier: Modifier = Modifier,
    points: Int? = null,
    centerPoints: Boolean = false,
    onBack: (() -> Unit)? = null,
    starOutline: Color = StarGoldDeep,
    endReserve: Dp = 0.dp,
) {
    // safeDrawing statt der Vorgabe (systemBars): im Vollbild ist der
    // Status-Bar-Inset null, ein Display-Ausschnitt bleibt aber bestehen —
    // und darunter darf der Titel nicht liegen.
    // Plus eine feste Handbreit oben: im Vollbild ist der Status-Bar-Inset
    // null, und ohne sie klebte die Titelzeile an der physischen Kante.
    // Ein Wert für Leiste und mittigen Punktestand: beide müssen oben denselben
    // Bereich freihalten, sonst sitzt der Stern nicht auf der Mittellinie.
    val barInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        .add(WindowInsets(top = 10.dp))

    Box(modifier = modifier) {
        TopAppBar(
            windowInsets = barInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = WarmInk,
                navigationIconContentColor = WarmInk,
            ),
            navigationIcon = {
                if (onBack != null) {
                    // Pfeil nach links, nicht X: die Lektion ist ein Ziel, das man
                    // verlässt, kein Dialog, den man schließt — und bewusst ohne
                    // Gehäuse, das Navigations-Icon einer M3-Bar ist ein nackter
                    // IconButton.
                    val description = stringResource(R.string.back_to_path)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = description },
                    ) {
                        IconArrowBack(tint = WarmInk, size = 24.dp)
                    }
                }
            },
            title = {
                if (points != null && !centerPoints) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = endReserve),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        AbcStarCount(points = points, outline = starOutline)
                    }
                }
            },
        )

        if (points != null && centerPoints) {
            // Bewusst nicht im Titel-Slot: der beginnt erst hinter dem
            // Zurück-Pfeil, mittig *darin* säße der Stern sichtbar rechts der
            // Bildschirmmitte. Als eigene Lage über der Leiste steht er genau
            // auf der Achse, auf der der große Stern hochkommt (`SuccessBurst`,
            // ebenfalls über die volle Breite zentriert) — deshalb hier nur der
            // obere Inset, kein horizontaler.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(barInsets.only(WindowInsetsSides.Top))
                    .height(TopBarHeight),
                contentAlignment = Alignment.Center,
            ) {
                AbcStarCount(points = points, outline = starOutline)
            }
        }
    }
}
