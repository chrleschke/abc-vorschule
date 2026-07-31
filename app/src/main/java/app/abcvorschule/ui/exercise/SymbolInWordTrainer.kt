package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.SoftSand

/**
 * Wort-Detektiv: find the hunted letter/syllable inside a word the lesson just
 * built (design doc §5/§6).
 *
 * PLACEHOLDER BODY — the real screen (colours, placeholder strokes, fly and spin
 * animations) lands in a later task. Kept compilable so the sealed dispatch in
 * [TrainerHost] stays exhaustive while the pure logic is built underneath.
 */
@Composable
fun SymbolInWordTrainer(
    round: SymbolInWordRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Text(text = pack.atoms[round.targetAtomId]?.display.orEmpty(), fontSize = 54.sp, color = SoftSand)
            Row(horizontalArrangement = Arrangement.Center) {
                round.segments.forEach { segment ->
                    Text(text = segment, fontSize = 40.sp, color = SoftSand)
                }
            }
        },
        answers = {},
    )
}
