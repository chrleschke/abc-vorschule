package app.abcvorschule.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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

/** Kantenlänge des schwebenden Drei-Punkte-Knopfs (Elterntür, siehe `ParentGateButton`). */
val TopBarFloatingActionSize: Dp = 48.dp

/**
 * Feste Handbreit über der Titelzeile. Im Vollbild ist der Status-Bar-Inset null;
 * ohne sie klebte die Zeile an der physischen Kante.
 */
private val TopBarExtraTop: Dp = 10.dp

/**
 * Abstand des schwebenden Knopfs zur Oberkante des Top-Insets. Ausgerechnet statt
 * geschätzt: so sitzt er auf derselben Mittelachse wie der Punktestand in der
 * Leiste — mit einer geratenen Zahl (8 dp) stand er im Vollbild 10 dp zu hoch,
 * der Stern links und die Punkte rechts lagen sichtbar nicht auf einer Linie.
 */
val TopBarFloatingActionTop: Dp =
    TopBarExtraTop + (TopBarHeight - TopBarFloatingActionSize) / 2

/**
 * Startabstand des Titel-Slots einer M3-Top-App-Bar ohne Navigations-Icon
 * (`TopAppBarTitleInset`, nicht öffentlich). Steht hier, damit der Punktestand
 * auf dieselbe Randachse gesetzt werden kann wie der schwebende Eltern-Knopf.
 */
private val TopBarTitleInset: Dp = 16.dp

/**
 * Die Kopfzeile der App: eine native M3-[TopAppBar], durchsichtig, damit
 * Landschaft bzw. Hintergrund unter ihr und unter der Status-Bar durchlaufen.
 *
 * Kein Titel — weder auf dem Pfad noch in der Lektion. Ein Lektionslabel wäre
 * Elterntext an der Stelle, an der das Kind zuerst hinsieht.
 *
 * @param points Punktestand in der Kopfzeile — links am Anfang des Titel-Slots.
 * `null` blendet ihn aus.
 * @param centerPoints Setzt den Punktestand stattdessen auf die Bildschirmmitte.
 * Für die Lektion: dort steht der Stern auf der Achse, auf der am Ende des
 * Trainers auch der große Stern hochkommt.
 * @param onBack Zurück zum Pfad. `null` auf dem Pfad selbst — dort gibt es
 * nichts, wohin zurück.
 * @param starOutline Kontur des Sterns. Default passt auf Cream; über dem
 * Pfad-Himmel muss der Aufrufer sie überschreiben (siehe `PathScreen`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbcTopBar(
    modifier: Modifier = Modifier,
    points: Int? = null,
    centerPoints: Boolean = false,
    onBack: (() -> Unit)? = null,
    starOutline: Color = StarGoldDeep,
) {
    // safeDrawing statt der Vorgabe (systemBars): im Vollbild ist der
    // Status-Bar-Inset null, ein Display-Ausschnitt bleibt aber bestehen —
    // und darunter darf der Titel nicht liegen.
    // Plus eine feste Handbreit oben: im Vollbild ist der Status-Bar-Inset
    // null, und ohne sie klebte die Titelzeile an der physischen Kante.
    // Ein Wert für die Leiste und den mittigen Punktestand darüber: beide müssen
    // oben denselben Bereich freihalten, sonst sitzt der Stern nicht auf der
    // Mittellinie der Leiste.
    val barInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        .add(WindowInsets(top = TopBarExtraTop))

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
                    // Linksbündig in der Ecke, nicht rechts: rechtsbündig wanderte der
                    // Stern bei jeder zusätzlichen Ziffer nach links — der Punktestand
                    // verschob sich beim Zählen. Links bleibt der Stern stehen und nur
                    // die Zahl wächst nach rechts, weg vom schwebenden Eltern-Knopf.
                    AbcStarCount(
                        points = points,
                        outline = starOutline,
                        // Setzt den Stern auf dieselbe Randachse wie den Eltern-Knopf
                        // am anderen Ende der Leiste — der Titel-Slot beginnt 16 dp
                        // von der Kante, der Knopf steht `screenHorizontal` davon weg.
                        modifier = Modifier.padding(
                            start = AbcDimens.screenHorizontal - TopBarTitleInset,
                        ),
                    )
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
