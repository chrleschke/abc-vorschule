package app.abcvorschule.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LessonFinale
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.rewards.ConfettiGeometry
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import kotlinx.coroutines.delay

// Kosmetische Werte für den Hintergrundstern: anders als die Schriftgrößen entscheiden
// sie nicht über die Höhe der Spalte (der Stern trägt nicht zur gemessenen Größe seiner
// Box bei, siehe excludedFromMeasurement()) und bleiben deshalb bewusst hier, nicht in
// FinaleLayout.
// Bewusst größer als die Bildreihe, die er hinterlegt: der Stern soll als Bühne lesbar
// sein, nicht als Rahmen. Weil er aus der Messung fällt, kostet zusätzliche Größe keinen
// Platz und kann den Weiter- oder Speaker-Button nicht verdrängen.
private val BackgroundStarSize = 300.dp
private const val BackgroundStarAlpha = 0.15f

// Zusätzliches horizontales Polster für den Satz, oben auf das 24dp der Spalte drauf
// (macht 44dp insgesamt pro Seite): 20dp liegt in der von der Produktseite gewünschten
// Spanne von 16–24dp — die Mitte der Spanne, weil der Satz spürbar mehr Luft braucht,
// ohne zu einem schmalen Textband zu werden. Reine Optik, keine Font-Scale-Entscheidung,
// bleibt darum hier statt in FinaleLayout.
private val SentenceExtraHorizontalPadding = 20.dp

/**
 * Mindest-Trefferfläche eines antippbaren Finale-Bildes. Vorher war die Fläche die
 * Glyphenbox selbst — effektiv 64dp bei zwei oder drei Bildern, 52dp bei vier
 * ([FinaleLayout.pictureSizeSp] deckelt die gerenderte Größe, sie ist also auf dem
 * Testgerät bei font_scale 1.3 dieselbe wie bei 1.0). Beides liegt unter
 * [AbcDimens.kidTouch], und getippt wird hier vom Kind (§7: antippbare Items werden
 * vorgelesen).
 *
 * Die vollen 80dp bekommen nur zwei oder drei Bilder. Vier bräuchten
 * 4×80 + 3×[FinaleLayout.PictureRowGapDp] = 368dp, die schmalste unterstützte
 * Inhaltsbreite ist aber 272dp (320dp-Gerät minus 24dp Spaltenpolster je Seite,
 * festgehalten in FinaleLayoutTest). Für vier Bilder ist der Boden deshalb genau das,
 * was dort noch in *eine* Reihe passt — (272 − 3×16) / 4 = 56dp —, und das ist der
 * Hitbox-Boden, auf den §9 auch die Satz-Pegs stellt, wenn kidTouch physikalisch nicht
 * passt. Drei Bilder gehen mit 3×80 + 2×16 = 272dp genau auf.
 */
private fun pictureTouchSize(count: Int): Dp =
    if (count >= 4) 56.dp else AbcDimens.kidTouch

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
 * - [finale] null (Defensivpfad: Finale nicht auflösbar — Abbrüche führen direkt
 *   zum Pfad und erreichen diesen Screen nie, §5): nur Erfolgs-Header, derselbe Stern und
 *   Weiter. Ohne Bildreihe ist der Stern der einzige Inhalt des mittleren Blocks und
 *   sitzt darum exakt in dessen Zentrum — tiefer als beim Finale, wo der Stern hinter
 *   der Bildreihe sitzt, die selbst über der Mitte der (durch Satz und Speaker
 *   verlängerten) Spalte liegt. Er bleibt also in beiden Varianten sichtbar und an
 *   vorhersagbarer, wenn auch unterschiedlicher Stelle, statt zu verschwinden oder
 *   irgendwo beliebig zu landen.
 *
 * Header, mittlerer Block und Weiter-Button liegen in einer Spalte; der mittlere
 * Block trägt `weight(1f)` und zentriert seinen Inhalt vertikal darin. `weight(1f)`
 * (mit dem Standard `fill = true`) gibt diesem Block eine feste Höhe, unabhängig vom
 * Inhalt — der Weiter-Button rutscht dadurch nie vom Bildschirm, ganz gleich wie der
 * Inhalt innerhalb des Blocks ausgerichtet ist. Eine frühere Fassung richtete den
 * Inhalt oben statt zentriert aus; das ließ ihn am oberen Bildschirmviertel kleben,
 * statt wie eine zusammenhängende Komposition zu wirken — die Erfolgsmeldung selbst
 * bleibt trotzdem oben, das Zentrieren betrifft nur den mittleren Block. Der Block
 * schneidet überlaufenden Inhalt nicht ab (kein `clipToBounds`): eine dekorative
 * Fläche darf über ihre Kindgrenzen hinausragen, aber ein Bedienelement (der
 * Speaker-Button) darf nie unsichtbar abgeschnitten werden — deshalb bleibt der Stern
 * von der Höhenmessung ausgenommen, statt echten Inhalt wegzuschneiden, falls er zu
 * groß würde.
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
    val haptics = LocalAbcHaptics.current
    LaunchedEffect(Unit) {
        popped = true
        haptics.celebrate()
    }
    // Absichtlich nicht `by`: der Wert wird erst im graphicsLayer-Block von
    // [BackgroundStar] gelesen, also in der Zeichenphase. Hier oben gelesen hätte
    // er den ganzen End-Screen — Header, Bildreihe, Satz, Speaker, Weiter-Knopf —
    // 500ms lang jeden Frame rekomponiert. Dieselbe Regel wie bei den Pfad-
    // Animationen (PathSignNode, PathHereMarker, PathScreen).
    val starScale = animateFloatAsState(
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

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Ganz unten in der z-Reihenfolge: rein dekorativ, überlagert weder
        // Bildreihe/Satz/Buttons noch fängt es Toucheingaben (Canvas ist nicht
        // klickbar) — siehe ConfettiOverlay-Kommentar.
        ConfettiOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
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
                contentAlignment = Alignment.Center,
            ) {
                if (finale == null) {
                    // Ohne Bildreihe gibt es keine Größe, die der Stern aufblähen könnte, aber
                    // excludedFromMeasurement() bleibt trotzdem dran: sonst wäre die Aussage im
                    // Datei-Kommentar oben ("der Stern trägt nicht zur gemessenen Größe seiner
                    // Box bei") nur für die Finale-Variante wahr, nicht unbedingt.
                    BackgroundStar(
                        scale = starScale,
                        modifier = Modifier.excludedFromMeasurement(),
                    )
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
}

/**
 * Fallendes Konfetti hinter dem gesamten Inhalt des End-Screens: läuft einmalig
 * über [ConfettiDurationMillis] und zeichnet danach nichts mehr (deterministische
 * Geometrie aus [ConfettiGeometry], Seed fest, damit Recompositions stabil bleiben).
 * Reine Deko-Ebene — kein Klick-/Touch-Handling, liegt unterhalb des restlichen
 * Inhalts in der Box, verdeckt also weder Bildreihe noch Satz noch Buttons.
 */
private const val ConfettiCount = 40
private const val ConfettiSeed = 42L
private const val ConfettiDurationMillis = 2200

/**
 * Einmal alloziert statt je Rekomposition: die vier Rollenfarben sind konstant,
 * und die Liste wurde vorher in jedem Frame der 2200ms neu gebaut.
 */
private val ConfettiColors = listOf(StarGold, SunCoral, SkyBlue, LeafGreen)

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val pieces = remember { ConfettiGeometry.pieces(count = ConfettiCount, seed = ConfettiSeed) }
    var progress by remember { mutableStateOf(0f) }
    // Absichtlich nicht `by`, und das `if (animatedProgress < 1f)` ist bewusst in
    // die Zeichenphase gewandert: als Bedingung um das Canvas herum war der
    // Fortschritt ein Kompositions-Lesezugriff und rekomponierte diese Composable
    // — und mit ihr den ganzen End-Screen — 2200ms lang in jedem Frame. Jetzt
    // bleibt das Canvas stehen und zeichnet nach dem Lauf schlicht nichts mehr;
    // die Zusage aus dem Kommentar oben („zeichnet danach nichts mehr") gilt
    // unverändert.
    val animatedProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(ConfettiDurationMillis, easing = LinearEasing),
        label = "confetti-progress",
    )
    LaunchedEffect(Unit) {
        progress = 1f
    }

    Canvas(modifier = modifier) {
        val fallen = animatedProgress.value
        if (fallen >= 1f) return@Canvas
        val width = size.width
        val height = size.height
        pieces.forEach { piece ->
            val y = ConfettiGeometry.yFraction(piece, fallen)
            if (y in -0.1f..1.1f) {
                val x = (piece.xFraction + piece.drift * fallen).coerceIn(0f, 1f) * width
                val pieceSize = (10f * piece.sizeFraction)
                withTransform({
                    translate(left = x, top = y * height)
                    rotate(degrees = piece.drift * 360f * fallen, pivot = Offset.Zero)
                }) {
                    drawRoundRect(
                        color = ConfettiColors[piece.colorIndex],
                        topLeft = Offset(-pieceSize / 2f, -pieceSize / 2f),
                        size = Size(pieceSize, pieceSize),
                        cornerRadius = CornerRadius(pieceSize * 0.3f),
                    )
                }
            }
        }
    }
}

/**
 * Der gedämpfte Erfolgsstern: gleiche Instanz für die echte und die schlanke
 * Variante. [scale] kommt als `State` herein und wird im graphicsLayer-Block
 * gelesen, also in der Zeichenphase — ein Skalierungsframe zeichnet den Stern neu,
 * ohne irgendetwas zu rekomponieren.
 */
@Composable
private fun BackgroundStar(scale: State<Float>, modifier: Modifier = Modifier) {
    IconStar(
        tint = StarGold.copy(alpha = BackgroundStarAlpha),
        // Ohne Override würde die neue Standard-Kontur (StarGoldDeep, siehe AbcIcons.kt)
        // mit voller Deckkraft gezeichnet — ein scharfer Ring um einen sonst absichtlich
        // fast unsichtbaren Hintergrundstern. Die Kontur bleibt darum auf derselben
        // gedämpften Alpha wie die Füllung; Kontrast ist hier ohnehin irrelevant, weil
        // dieser Stern rein dekorativ ist, kein bedienbares Glyph.
        outline = StarGoldDeep.copy(alpha = BackgroundStarAlpha),
        size = BackgroundStarSize,
        modifier = modifier.graphicsLayer {
            val factor = scale.value
            scaleX = factor
            scaleY = factor
        },
    )
}

/**
 * Misst den Inhalt ungebunden (`maxWidth`/`maxHeight` = unendlich, damit die gemeldete
 * Größe garantiert der tatsächlich gezeichneten entspricht — dazu gleich mehr) und
 * meldet dem Eltern-Layout trotzdem eine Größe von 0×0 zurück, zentriert auf demselben
 * Punkt, an dem der Inhalt sonst gestanden hätte. Für rein dekorative Elemente, die
 * größer sein dürfen als das, was sie hinterlegen, ohne dessen Größe (und damit die
 * Höhe der ganzen Spalte) zu beeinflussen.
 *
 * `Modifier.wrapContentSize(unbounded = true)` allein reicht dafür nicht: eine `Box`
 * misst alle Kinder mit denselben eingehenden Constraints, die hier recht locker sind
 * (die Bildreihe braucht viel weniger als verfügbar ist) — der Stern würde also seine
 * volle gemessene Größe zurückmelden, solange sie unter diesen Constraints bleibt, egal
 * wie "unbounded" er gemessen wurde. Hier wird die gemeldete Größe stattdessen explizit
 * überschrieben, unabhängig von den eingehenden Constraints.
 *
 * Wichtig: mit den *eingehenden* Constraints zu messen wäre trotzdem falsch, selbst mit
 * der 0×0-Überschreibung — auf einem 320dp breiten Gerät ist die Inhaltsbreite z. B. nur
 * 272dp, `IconStar` zeichnet aber immer anhand seines deklarierten `size`-Parameters
 * (`AbcIcons.kt`: `val w = size.toPx()`), nicht anhand der gemessenen Breite. Mit engen
 * eingehenden Constraints würde `placeable.width` (und damit `-placeable.width / 2` bei
 * der Zentrierung) auf 272dp geklemmt, während der Stern weiterhin bei seiner vollen,
 * deklarierten Größe gezeichnet wird — die Zentrierung würde um die halbe Differenz
 * danebenliegen. Ungebundenes Messen hält gemeldete und gezeichnete Größe deckungsgleich.
 */
private fun Modifier.excludedFromMeasurement(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity),
    )
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
    starScale: State<Float>,
) {
    val pictures = FinaleLayout.picturesOf(pack, finale)
    val pictureSp = FinaleLayout.pictureSizeSp(pictures.size, fontScale).sp
    val pictureTouch = pictureTouchSize(pictures.size)
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
            // ist hier Pflicht: ohne sie wäre der Stern (300dp) höher als die Bildreihe
            // (~85dp) und würde die Box — und damit die ganze Spalte — entsprechend
            // aufblähen, bis am Ende der Speaker-Button keinen Platz mehr hätte.
            BackgroundStar(
                scale = starScale,
                modifier = Modifier.excludedFromMeasurement(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    FinaleLayout.PictureRowGapDp.dp,
                    Alignment.CenterHorizontally,
                ),
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
                        // Die Trefferfläche ist die Box, nicht das Glyph: ein Emoji
                        // ist kleiner als der Finger, der es trifft. Siehe
                        // [pictureTouchSize].
                        Box(
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = pictureTouch,
                                    minHeight = pictureTouch,
                                )
                                // Tippen liest das Wort vor (Prinzip 7).
                                .clickable(enabled = ttsAvailable) {
                                    onSpeak(picture.lemma)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = picture.emoji, fontSize = pictureSp)
                        }
                    }
                }
            }
        }

        Text(
            text = finale.text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            // Wrapping ist jetzt das erwartete Verhalten, kein Fehlerfall: die
            // Schriftgröße bleibt unverändert (siehe FinaleLayout.sentenceSizeSp), der
            // Satz darf dafür über mehr Zeilen laufen. 4 Zeilen sind eine sichere
            // Obergrenze für 4–7 Wörter in der jetzt schmaleren Textspalte;
            // TextOverflow.Ellipsis bleibt als letzte Absicherung, falls doch mehr
            // Zeilen nötig wären.
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            fontSize = sentenceSp.sp,
            lineHeight = sentenceLineHeightSp.sp,
            modifier = Modifier.padding(horizontal = SentenceExtraHorizontalPadding),
        )

        AbcSpeakerButton(
            enabled = ttsAvailable,
            speaking = speaking,
            onClick = { onSpeak(finale.tts) },
        )
    }
}
