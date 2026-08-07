package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.AbcDimens

/** Wo der Antwortblock einer Übung sitzt. */
enum class AnswerAnchor {
    /** Am unteren Rand, mit Luft darunter — die Grundform (PRODUCT_PRINCIPLES §9). */
    Bottom,

    /**
     * Oberkante des Antwortblocks knapp unter der Bildschirmmitte. Für Übungen,
     * deren Aufgabenblock fast leer ist: der Satz-Versteher trägt dort nur den
     * Speaker (kein Titel, keine Kacheln, kein Wort), und am unteren Rand
     * verdeckt die tippende Hand dann die Bildkarten, die den ganzen Inhalt der
     * Aufgabe ausmachen.
     */
    BelowCenter,
}

/**
 * Anteil der Bühnenhöhe, den der Aufgabenblock im [AnswerAnchor.BelowCenter]-Modus
 * bekommt. Eine feste Bruchhöhe und *kein* zweites `weight`: Gewichte teilen den
 * Restraum nach Abzug der Antworten auf, und die Kartenhöhe des Satz-Verstehers
 * wächst mit der Emoji-Größe — die Oberkante würde also mit jeder Größenänderung
 * wandern, bei hohen Karten sogar über die Mitte hinaus, also in die
 * Gegenrichtung. 0.52 ist von der Antworthöhe unabhängig und hält die Zusage
 * „knapp unterhalb der Mitte" wörtlich.
 */
private const val PromptHeightFraction = 0.52f

/**
 * Prompt/task block in the upper area; answers anchored near the bottom with breathing room.
 * Content is width-capped so nothing hugs the screen edges.
 */
@Composable
fun ExerciseStage(
    modifier: Modifier = Modifier,
    answerAnchor: AnswerAnchor = AnswerAnchor.Bottom,
    prompt: @Composable ColumnScope.() -> Unit,
    answers: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val promptHeight = maxHeight * PromptHeightFraction
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = when (answerAnchor) {
                    AnswerAnchor.Bottom -> Modifier.weight(1f)
                    AnswerAnchor.BelowCenter -> Modifier.height(promptHeight)
                }.fillMaxWidth(),
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
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = answers,
            )
        }
    }
}
