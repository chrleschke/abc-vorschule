package app.abcvorschule.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Titelgrößen des Aufgaben-Chromes, compose-frei und damit testbar.
 *
 * Der Titel ist Anweisungschrome für den danebensitzenden Erwachsenen — das Kind
 * liest ihn nicht (§2), die Aufgabe trägt das Audio. Er darf also seine
 * fontScale-Vergrößerung als Erstes wieder abgeben (dasselbe Argument wie die
 * Emojis in `FinaleLayout`): ungedeckelt rendert der 34sp-Titel bei font_scale
 * 2.0 als 68dp-Zeile in einem Aufgabenblock, der weder scrollt noch clippt
 * (`ExerciseStage`), und schiebt die eigentliche Aufgabe zusammen.
 */
object TaskPromptSizing {
    const val TitleSp = 34
    const val MutedTitleSp = 28

    /** Basis-Zeilenhöhe: `headlineMedium` aus `ui/theme/Theme.kt`, dessen Stil der
     * Titel trägt. Eigenständig gedeckelt, weil der Stil sonst weiterhin feste
     * 34sp vorgibt, die ungebremst mit fontScale wachsen — dieselbe Begründung
     * wie `FinaleLayout.sentenceLineHeightSp`. */
    const val TitleLineHeightSp = 34

    /** Das Aufgabenbild über Wort bzw. Satz — die bisherige feste Größe im
     * Wort-Bauer und im Satz-Architekten. */
    const val PictureSp = 84

    /**
     * Größe des Aufgabenbildes, mit demselben Deckel wie der Titel: ein Emoji ist
     * ein Bild, keine Prosa (Muster `SentencePictureCardSizing.emojiSp` und
     * `WordFrameSizing.targetLabelSp`), und ungedeckelt rendern die 84sp bei
     * font_scale 2.0 als ~168dp — der Aufgabenblock von [ExerciseStage] scrollt
     * nicht und clippt nicht, das Bild schöbe also Wort und Rahmen aus dem Bild.
     * Bei font_scale 1.0 kommt unverändert [PictureSp] heraus.
     */
    fun pictureSp(fontScale: Float): Int = capEffectiveSize(PictureSp, fontScale)

    fun titleSp(muted: Boolean, fontScale: Float): Int =
        capEffectiveSize(if (muted) MutedTitleSp else TitleSp, fontScale)

    fun titleLineHeightSp(fontScale: Float): Int =
        capEffectiveSize(TitleLineHeightSp, fontScale)

    /**
     * Deckelt die *effektiv gerenderte* Größe (= Rückgabe × `fontScale`) auf
     * [baseSp] — Muster `FinaleLayout.capEffectiveSize` (dort private, und ein
     * Import aus `ui/shell` in eine Übung wäre die falsche Richtung): bis 1.0
     * unverändert, darüber schrumpft der sp-Wert so, dass das Produkt konstant
     * bleibt. Ganzzahl-Kürzung rundet bewusst ab, nie auf.
     */
    private fun capEffectiveSize(baseSp: Int, fontScale: Float): Int =
        if (fontScale <= 1f) baseSp else (baseSp / fontScale).toInt().coerceAtLeast(1)
}

/**
 * Speaker centered above the task title inside the exercise prompt area.
 */
@Composable
fun TaskPromptChrome(
    title: String?,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    mutedTitle: Boolean = false,
    onTitleSpeak: ((String) -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AbcSpeakerButton(
            enabled = ttsAvailable,
            speaking = speaking,
            onClick = onSpeakPrompt,
        )
        if (!title.isNullOrBlank()) {
            val fontScale = LocalDensity.current.fontScale
            Text(
                text = title,
                fontSize = TaskPromptSizing.titleSp(mutedTitle, fontScale).sp,
                lineHeight = TaskPromptSizing.titleLineHeightSp(fontScale).sp,
                color = if (mutedTitle) WarmMuted else WarmInk,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clickable {
                    if (onTitleSpeak != null) onTitleSpeak(title) else onSpeakPrompt()
                },
            )
        }
    }
}
