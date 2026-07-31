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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.layout
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

// Kosmetische Werte für den Hintergrundstern: anders als die Schriftgrößen entscheiden
// sie nicht über die Höhe der Spalte (der Stern trägt nicht zur gemessenen Größe seiner
// Box bei, siehe excludedFromMeasurement()) und bleiben deshalb bewusst hier, nicht in
// FinaleLayout.
private val BackgroundStarSize = 180.dp
private const val BackgroundStarAlpha = 0.12f

/**
 * Der End-Screen einer Lektion, in zwei Varianten:
 *
 * - [finale] gesetzt (echter Abschluss): Bildreihe, Satz und Speaker über einem
 *   gedämpften Hintergrundstern, der hinter der Bildreihe sitzt (nicht hinter dem
 *   ganzen Bildschirm) — er rahmt die Bilder, statt über den Satz zu laufen. Der
 *   Stern ist rein dekorativ und darf größer sein als die Bildreihe, ohne deren
 *   Höhe (und damit die Höhe der ganzen Spalte) zu beeinflussen — siehe
 *   [excludedFromMeasurement]. Der Satztext richtet sich an den mitlesenden
 *   Erwachsenen — die einzige bewusste Ausnahme von „das Kind kann nicht lesen"
 *   (PRODUCT_PRINCIPLES.md Abschnitt 12), weil keine Handlung am Text hängt.
 * - [finale] null (Abbruch mit Punkten): nur Erfolgs-Header, derselbe Stern und
 *   Weiter. Ohne Bildreihe sitzt der Stern an derselben Stelle, an der die Bildreihe
 *   sonst stünde (oben im mittleren Block) — er bleibt also sichtbar und an
 *   vorhersagbarer Stelle, statt zu verschwinden oder irgendwo zu landen.
 *
 * Header, mittlerer Block und Weiter-Button liegen in einer Spalte; der mittlere
 * Block trägt `weight(1f)` und richtet seinen Inhalt oben aus, sodass Header und
 * Inhalt als eine zusammenhängende Einheit wirken und der Weiter-Button unten
 * andockt. `weight(1f)` (mit dem Standard `fill = true`) gibt diesem Block eine
 * feste Höhe, unabhängig vom Inhalt — der Weiter-Button rutscht dadurch nie vom
 * Bildschirm. Der Block schneidet überlaufenden Inhalt nicht ab (kein
 * `clipToBounds`): eine dekorative Fläche darf über ihre Kindgrenzen hinausragen,
 * aber ein Bedienelement (der Speaker-Button) darf nie unsichtbar abgeschnitten
 * werden — deshalb bleibt der Stern von der Höhenmessung ausgenommen, statt echten
 * Inhalt wegzuschneiden, falls er zu groß würde.
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
    val starScale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.7f,
        animationSpec = tween(500),
        label = "reward-scale",
    )
    val fontScale = LocalDensity.current.fontScale

    // Den Satz einmal beim Erscheinen sprechen, wie die Prompt-Ansage in der Übung.
    LaunchedEffect(finale?.id, ttsAvailable) {
        val text = finale?.tts ?: return@LaunchedEffect
        if (ttsAvailable) onSpeak(text)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.reward_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            // Ungedeckelt würde der Header bei großer Schriftskalierung nicht nur
            // wachsen, sondern (ohne maxLines) auf zwei Zeilen umbrechen und den
            // Puffer auffressen, den die gedeckelten Bilder und der gedeckelte Satz
            // freihalten. Siehe FinaleLayout.headerSizeSp/-headerLineHeightSp.
            fontSize = FinaleLayout.headerSizeSp(fontScale).sp,
            lineHeight = FinaleLayout.headerLineHeightSp(fontScale).sp,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (finale == null) {
                BackgroundStar(scale = starScale)
            } else {
                FinaleBody(
                    finale = finale,
                    pack = pack,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeak = onSpeak,
                    fontScale = fontScale,
                    starScale = starScale,
                )
            }
        }

        AbcContinueButton(
            onClick = onContinue,
            centered = true,
        )
    }
}

/** Der gedämpfte Erfolgsstern: gleiche Instanz für die echte und die schlanke Variante. */
@Composable
private fun BackgroundStar(scale: Float, modifier: Modifier = Modifier) {
    IconStar(
        tint = MaterialTheme.colorScheme.primary.copy(alpha = BackgroundStarAlpha),
        size = BackgroundStarSize,
        modifier = modifier.scale(scale),
    )
}

/**
 * Misst den Inhalt normal (in voller Größe), meldet dem Eltern-Layout aber eine Größe
 * von 0×0 zurück — zentriert auf demselben Punkt, an dem der Inhalt sonst gestanden
 * hätte. Für rein dekorative Elemente, die größer sein dürfen als das, was sie
 * hinterlegen, ohne dessen Größe (und damit die Höhe der ganzen Spalte) zu
 * beeinflussen.
 *
 * `Modifier.wrapContentSize(unbounded = true)` allein reicht dafür nicht: eine `Box`
 * misst alle Kinder mit denselben eingehenden Constraints, die hier recht locker sind
 * (die Bildreihe braucht viel weniger als verfügbar ist) — der Stern würde also seine
 * volle gemessene Größe zurückmelden, solange sie unter diesen Constraints bleibt, egal
 * wie "unbounded" er gemessen wurde. Hier wird die gemeldete Größe stattdessen explizit
 * überschrieben, unabhängig von den eingehenden Constraints.
 */
private fun Modifier.excludedFromMeasurement(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, 0) {
        placeable.place(-placeable.width / 2, -placeable.height / 2)
    }
}

@Composable
private fun FinaleBody(
    finale: LessonFinale,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    fontScale: Float,
    starScale: Float,
) {
    val pictures = FinaleLayout.picturesOf(pack, finale)
    val pictureSp = FinaleLayout.pictureSizeSp(pictures.size, fontScale).sp
    val sentenceSp = FinaleLayout.sentenceSizeSp(fontScale)
    val sentenceLineHeightSp = FinaleLayout.sentenceLineHeightSp(fontScale)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Hinter der Bildreihe statt hinter der ganzen Spalte. excludedFromMeasurement()
            // ist hier Pflicht: ohne sie wäre der Stern (180dp) höher als die Bildreihe
            // (~85dp) und würde die Box — und damit die ganze Spalte — entsprechend
            // aufblähen, bis am Ende der Speaker-Button keinen Platz mehr hätte.
            BackgroundStar(
                scale = starScale,
                modifier = Modifier.excludedFromMeasurement(),
            )
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
        }

        Text(
            text = finale.text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            fontSize = sentenceSp.sp,
            lineHeight = sentenceLineHeightSp.sp,
        )

        AbcSpeakerButton(
            enabled = ttsAvailable,
            speaking = speaking,
            onClick = { onSpeak(finale.tts) },
        )
    }
}
