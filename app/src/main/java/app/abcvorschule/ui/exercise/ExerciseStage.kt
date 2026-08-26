package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import app.abcvorschule.ui.theme.AbcDimens

/** Wo der Antwortblock einer Übung sitzt. */
enum class AnswerAnchor {
    /** Am unteren Rand, mit Luft darunter — die Grundform (PRODUCT_PRINCIPLES §9). */
    Bottom,

    /**
     * Oberkante des Antwortblocks knapp unter der Bildschirmmitte. Für Übungen,
     * deren Aufgabenblock fast leer ist: der Satz-Versteher trägt dort nur den
     * Speaker (kein Titel, keine Kacheln, kein Wort — Ausnahme: ohne deutsches
     * TTS erscheint der Satz dort als Text, damit ein Erwachsener vorlesen kann,
     * siehe PRODUCT_PRINCIPLES §7), und am unteren Rand verdeckt die tippende
     * Hand dann die Bildkarten, die den ganzen Inhalt der Aufgabe ausmachen.
     */
    BelowCenter,
}

/**
 * Anteil der Bühnenhöhe, ab dem der Antwortblock im [AnswerAnchor.BelowCenter]-Modus
 * beginnt. Wichtig: der Bruch ist eine **Untergrenze für den Antwortblock**, keine
 * feste Höhe für den Aufgabenblock. Der Aufgabenblock behält in beiden Modi sein
 * `weight(1f)`, denn Compose misst die *ungewichteten* Kinder einer Column zuerst
 * gegen die volle Höhe — nur so kann der Antwortblock nie zusammengedrückt werden.
 * Die Untergrenze ist dann das, was seine Oberkante knapp unter die Mitte setzt:
 * passt der Inhalt in die verbleibenden 48 %, beginnt der Block exakt bei 52 %;
 * braucht er mehr (hohe Emoji-Karten, „Zeig mir", font_scale über 1.0, kurzes
 * Gerät), wächst er nach *oben* weiter statt seine letzten Kinder auf 0dp zu
 * quetschen.
 *
 * Vorher stand hier eine feste `height` für den Aufgabenblock, und genau das war
 * der Fehler: damit war der Aufgabenblock das ungewichtete Kind und der
 * Antwortblock bekam 48 % als *Maximum*. Auf 360×640dp reichte das nicht für
 * Karte + Lücke + `AbcResolveButton`, der Auflösen-Knopf wurde auf wenige dp
 * gemessen und war nach zwei Fehltipps nicht mehr tippbar.
 */
private const val PromptHeightFraction = 0.52f

/**
 * Bühne einer Übung: Speaker-Kopfzeile ganz oben, darunter der Aufgabenblock,
 * darunter der Antwortblock, alle drei auf 420dp Breite gedeckelt, damit nichts
 * am Bildschirmrand klebt.
 *
 * [AnswerAnchor.Bottom] (Vorbelegung, die Grundform aus PRODUCT_PRINCIPLES §9):
 * der Antwortblock sitzt am unteren Rand mit 8dp Luft darunter, der Aufgabenblock
 * füllt den Rest und zentriert seinen Inhalt darin.
 *
 * [AnswerAnchor.BelowCenter]: derselbe Aufbau, aber der Antwortblock bekommt
 * zusätzlich [PromptHeightFraction] der Bühnenhöhe als Untergrenze — seine
 * Oberkante liegt damit knapp unter der Bildschirmmitte, solange sein Inhalt in
 * den Rest passt.
 *
 * @param promptChrome Kopf des Aufgabenbereichs — in allen Trainern der Speaker
 * ([TaskPromptChrome]). Eigener Slot **über** dem Aufgabenblock statt dessen
 * erstes Kind: der Aufgabenblock ist zentriert, also wanderte der Speaker mit
 * der Höhe des jeweiligen Trainerinhalts mit (gemessen: 203dp in der Jagd,
 * 305dp im Spurensucher, 425dp im Silben-Verschmelzer). Als ungewichtetes
 * erstes Kind der Bühne sitzt er in jedem Trainer auf derselben Höhe, direkt
 * unter Fortschritt und Punktestand.
 */
@Composable
fun ExerciseStage(
    modifier: Modifier = Modifier,
    answerAnchor: AnswerAnchor = AnswerAnchor.Bottom,
    promptChrome: @Composable ColumnScope.() -> Unit = {},
    prompt: @Composable ColumnScope.() -> Unit,
    answers: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Versicherung, kein aktueller Fall: unter unbeschränkter Höhe ist maxHeight
        // Dp.Infinity, `fillMaxSize` wirkungslos und eine daraus berechnete
        // Mindesthöhe unendlich. Die Kette TaskShell → Box(weight(1f)) → TrainerHost
        // ist immer beschränkt; wer diese Bühne aber einmal in eine scrollbare Spalte
        // hängt, soll ein brauchbares Layout bekommen statt eines absurden.
        val answersMinHeight = if (maxHeight.isFinite) {
            maxHeight * (1f - PromptHeightFraction)
        } else {
            0.dp
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = promptChrome,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AbcDimens.blockGap),
                    content = prompt,
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .then(
                        when (answerAnchor) {
                            AnswerAnchor.Bottom -> Modifier
                            AnswerAnchor.BelowCenter ->
                                Modifier.heightIn(min = answersMinHeight)
                        },
                    )
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = answers,
            )
        }
    }
}
