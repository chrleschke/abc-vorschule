package app.abcvorschule.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LessonFinale
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.components.IconStar
import kotlinx.coroutines.delay

private val BackgroundStarSize = 280.dp
private const val BackgroundStarAlpha = 0.12f

// Material3s eigenes Verhältnis für headlineSmall (32sp Zeilenhöhe / 24sp Schriftgröße),
// hier reproduziert: sobald wir fontSize überschreiben, hängt lineHeight sonst weiter an
// headlineSmalls festen 32sp und wächst mit der System-Schriftskalierung ungebremst
// weiter — die gedeckelte Schriftgröße allein würde die Zeile dann nicht klein halten.
private const val SentenceLineHeightRatio = 32f / 24f

/**
 * Der End-Screen einer Lektion, in zwei Varianten:
 *
 * - [finale] gesetzt (echter Abschluss): Bildreihe, Satz und Speaker über einem
 *   gedämpften Hintergrundstern. Der Satztext richtet sich an den mitlesenden
 *   Erwachsenen — die einzige bewusste Ausnahme von „das Kind kann nicht lesen"
 *   (PRODUCT_PRINCIPLES.md Abschnitt 12), weil keine Handlung am Text hängt.
 * - [finale] null (Abbruch mit Punkten): nur Erfolgs-Header, Stern und Weiter.
 *
 * Zeigt bewusst **keine** Punktezahl: die steht im Übungs-Chrome und auf dem Pfad.
 */
@Composable
fun RewardSummaryScreen(
    finale: LessonFinale?,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popped = true }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.7f,
        animationSpec = tween(500),
        label = "reward-scale",
    )

    // Den Satz einmal beim Erscheinen sprechen, wie die Prompt-Ansage in der Übung.
    LaunchedEffect(finale?.id, ttsAvailable) {
        val text = finale?.tts ?: return@LaunchedEffect
        if (ttsAvailable) onSpeak(text)
    }

    Box(modifier = modifier.fillMaxSize()) {
        IconStar(
            tint = MaterialTheme.colorScheme.primary.copy(alpha = BackgroundStarAlpha),
            size = BackgroundStarSize,
            modifier = Modifier
                .align(Alignment.Center)
                .scale(scale),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.reward_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (finale == null) {
                Spacer(Modifier.height(1.dp))
            } else {
                FinaleBody(
                    finale = finale,
                    pack = pack,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeak = onSpeak,
                )
            }

            AbcContinueButton(
                onClick = onContinue,
                centered = true,
            )
        }
    }
}

@Composable
private fun FinaleBody(
    finale: LessonFinale,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val pictures = FinaleLayout.picturesOf(pack, finale)
    val pictureSp = FinaleLayout.pictureSizeSp(pictures.size, fontScale).sp
    val sentenceSp = FinaleLayout.sentenceSizeSp(fontScale)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pictures.forEachIndexed { index, picture ->
                var shown by remember(finale.id, picture.atomId) { mutableStateOf(false) }
                LaunchedEffect(finale.id, picture.atomId) {
                    delay(FinaleLayout.revealDelayMillis(index))
                    shown = true
                }
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.6f),
                ) {
                    Text(
                        text = picture.emoji,
                        fontSize = pictureSp,
                        // Tippen liest das Wort vor (Prinzip 7).
                        modifier = Modifier.clickable(enabled = ttsAvailable) {
                            onSpeak(picture.lemma)
                        },
                    )
                }
            }
        }

        Text(
            text = finale.text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            fontSize = sentenceSp.sp,
            lineHeight = (sentenceSp * SentenceLineHeightRatio).sp,
        )

        AbcSpeakerButton(
            enabled = ttsAvailable,
            speaking = speaking,
            onClick = { onSpeak(finale.tts) },
        )
    }
}
