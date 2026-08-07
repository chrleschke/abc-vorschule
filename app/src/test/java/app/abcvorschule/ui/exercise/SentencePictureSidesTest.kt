package app.abcvorschule.ui.exercise

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SentencePictureSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencePictureSidesTest {

    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun sideIsDeterministicPerSeed() {
        (-5..5).forEach { seed ->
            assertEquals(
                SentencePictureSides.correctOnLeft(seed),
                SentencePictureSides.correctOnLeft(seed),
            )
        }
    }

    @Test
    fun sidesAreRoughlyBalancedOverManySeeds() {
        // Sätze liefern beliebige String-Hashes; über viele Seeds darf keine
        // Seite dominieren, sonst lernt das Kind "immer links tippen".
        val left = (0 until 1000).count { SentencePictureSides.correctOnLeft("Satz $it".hashCode()) }
        assertTrue("left=$left of 1000", left in 300..700)
    }

    @Test
    fun shippedSentencesDoNotFavourOneSideOverall() {
        // Der Hash ist über beliebige Seeds ausgeglichen (siehe oben) — das sagt
        // aber nichts über die 72 tatsächlich ausgelieferten Sätze. Erst dieser
        // Test prüft den Content selbst.
        val sides = shippedSides()
        val left = sides.count { it }
        assertTrue(
            "no side may take more than 70% of the shipped rounds: left=$left of ${sides.size}",
            left <= sides.size * 70 / 100 && (sides.size - left) <= sides.size * 70 / 100,
        )
    }

    @Test
    fun noShippedTaskPutsEveryRoundOnTheSameSide() {
        // Vier Runden hintereinander auf derselben Seite bringen genau das bei,
        // was die Seitenwahl verhindern soll: "immer links tippen". Die Seite hängt
        // am Satz-Hash, also ist die Gegenmaßnahme, einen Satz umzuformulieren.
        pack.tasks.values.filterIsInstance<SentencePictureSpec>().forEach { spec ->
            val sides = spec.rounds.map {
                SentencePictureSides.correctOnLeft(it.promptTts.hashCode())
            }
            assertTrue(
                "task ${spec.id} puts all ${sides.size} rounds on the " +
                    "${if (sides.first()) "left" else "right"} side",
                sides.toSet().size > 1,
            )
        }
    }

    private fun shippedSides(): List<Boolean> =
        pack.tasks.values.filterIsInstance<SentencePictureSpec>()
            .sortedBy { it.id }
            .flatMap { spec ->
                spec.rounds.map { SentencePictureSides.correctOnLeft(it.promptTts.hashCode()) }
            }

    @Test
    fun emojiShrinksWithMoreAtoms() {
        // Breite Karte, damit hier wirklich die Staffelung nach Atomzahl greift
        // und nicht der Breitendeckel.
        val wide = 400f
        assertTrue(
            SentencePictureCardSizing.emojiSp(1, wide, 1f) >
                SentencePictureCardSizing.emojiSp(2, wide, 1f) &&
                SentencePictureCardSizing.emojiSp(2, wide, 1f) >
                SentencePictureCardSizing.emojiSp(3, wide, 1f),
        )
    }

    @Test
    fun emojiRowFitsTheNarrowestSupportedCardForEveryValidatorPermittedCount() {
        // 320dp Gerät − 2 × 20dp AbcDimens.screenHorizontal = 280dp für die Reihe,
        // − 8dp Kartenabstand, / 2 Karten = 136dp Karte, − 2 × 4dp Karteninnen-
        // abstand = 128dp Inhaltsbreite. Der Validator erlaubt 1..3 Atome je Karte;
        // ein Test, der nur 2 prüft, würde die 3-Atom-Karten übersehen — genau die,
        // bei denen vorher das letzte Emoji verschwand.
        val narrowestCardContentWidthDp = 128f
        (1..3).forEach { count ->
            listOf(1f, 1.3f, 2f).forEach { fontScale ->
                val sp = SentencePictureCardSizing.emojiSp(count, narrowestCardContentWidthDp, fontScale)
                val renderedDp = count * sp * SentencePictureCardSizing.EmojiAdvanceEm * fontScale
                assertTrue(
                    "row of $count emojis renders ${renderedDp}dp wide at fontScale " +
                        "$fontScale, card content is only ${narrowestCardContentWidthDp}dp",
                    renderedDp <= narrowestCardContentWidthDp,
                )
            }
        }
    }

    @Test
    fun emojiSizeIsTheFullBaseAtFontScaleOneWhenThereIsRoom() {
        // Kein-Regressions-Anker für die *Staffelung*, nicht für die Zahlen von
        // gestern: auf einer Karte, die breit genug ist, greift die Basis. 400dp
        // reicht für alle drei Stufen (bei 3 Emojis liegt der Deckel bei
        // 400 / 3.6 = 111sp, also über der Basis 56). Alle drei Werte müssen hier
        // stehen — ohne den 3er-Wert ist die Basis 56 von keinem Test festgehalten,
        // weil emojiShrinksWithMoreAtoms nur die Monotonie prüft.
        val wide = 400f
        assertEquals(110f, SentencePictureCardSizing.emojiSp(1, wide, 1f), 0f)
        assertEquals(76f, SentencePictureCardSizing.emojiSp(2, wide, 1f), 0f)
        assertEquals(56f, SentencePictureCardSizing.emojiSp(3, wide, 1f), 0f)
    }

    @Test
    fun theFloorBeatsTheWidthBudgetForDegenerateWidths() {
        // Die eine benannte Ausnahme vom Breitenbudget: sobald weniger als
        // 14.4dp × Atomzahl × fontScale übrig sind, gewinnt MinEmojiSp und die
        // gerenderte Reihe wird breiter als die Karte. Das ist Absicht — der Boden
        // verhindert 0sp (oder Negatives), wenn die Breite vor der ersten Messung
        // noch 0 ist. Harmlos, weil im laufenden Layout nur ein Aufrufer in diese
        // Zone kommt: die Verliererkarte der Erfolgsanimation, die auf weight 0.001f
        // schrumpft. Die verblasst dabei ohnehin (Alpha ≤ 0.5), und softWrap = false
        // schneidet die Reihe an statt sie umzubrechen.
        val degenerateWidthDp = 20f
        val fontScale = 1.3f
        val sp = SentencePictureCardSizing.emojiSp(3, degenerateWidthDp, fontScale)
        assertEquals(SentencePictureCardSizing.MinEmojiSp, sp, 0f)
        val renderedDp = 3 * sp * SentencePictureCardSizing.EmojiAdvanceEm * fontScale
        assertTrue(
            "row renders ${renderedDp}dp into a ${degenerateWidthDp}dp card — that is " +
                "the documented exception from the width budget, not a regression",
            renderedDp > degenerateWidthDp,
        )
    }

    @Test
    fun emojiSizeStaysPositiveForDegenerateInput() {
        // Vor der ersten Messung kann die Breite 0 sein, und atomCount 0 darf keine
        // Division durch 0 auslösen.
        assertTrue(SentencePictureCardSizing.emojiSp(0, 0f, 1f) > 0f)
        assertTrue(SentencePictureCardSizing.emojiSp(3, -10f, 0f) > 0f)
    }

    @Test
    fun emojiSizeNeverGrowsWithFontScale() {
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.8f, 2f, 3f)
        (1..3).forEach { count ->
            val sizes = scales.map { SentencePictureCardSizing.emojiSp(count, 113f, it) }
            for (i in 1 until sizes.size) {
                assertTrue(
                    "emoji size grew from ${sizes[i - 1]} to ${sizes[i]} between " +
                        "scale ${scales[i - 1]} and ${scales[i]} at count $count",
                    sizes[i] <= sizes[i - 1],
                )
            }
        }
    }

    @Test
    fun baseScaleRaisesTheBaseButNotAboveTheWidthCap() {
        // Die Erfolgsanimation zieht die Karte fast bühnenbreit; dort bindet
        // weiter die Basisgröße, nicht der Deckel — ohne baseScale würde das
        // Emoji also gar nicht wachsen.
        val wide = 400f
        assertEquals(76f * 1.6f, SentencePictureCardSizing.emojiSp(2, wide, 1f, 1.6f), 1f)

        // Auf einer schmalen Karte bleibt der Deckel die Obergrenze: baseScale
        // darf ihn nicht aushebeln, sonst kehrt der Überlauf-Bug zurück.
        val narrow = 128f
        val capped = SentencePictureCardSizing.emojiSp(3, narrow, 1f, 1.6f)
        val renderedDp = 3 * capped * SentencePictureCardSizing.EmojiAdvanceEm
        assertTrue("row renders ${renderedDp}dp into a ${narrow}dp card", renderedDp <= narrow)
    }

    @Test
    fun baseScaleOfOneMatchesTheCallWithoutIt() {
        // Der Normalpfad darf sich durch den neuen Parameter nicht verschieben.
        listOf(1, 2, 3).forEach { count ->
            listOf(128f, 173f, 400f).forEach { width ->
                assertEquals(
                    SentencePictureCardSizing.emojiSp(count, width, 1f),
                    SentencePictureCardSizing.emojiSp(count, width, 1f, 1f),
                    0f,
                )
            }
        }
    }

    @Test
    fun shakeStartsAndEndsAtRest() {
        // Der Versatz wird über graphicsLayer gezeichnet: bleibt am Ende etwas
        // stehen, sitzt die Karte für den Rest der Runde schief.
        assertEquals(0f, SentencePictureCardShake.offsetDp(0f), 0.001f)
        assertEquals(0f, SentencePictureCardShake.offsetDp(1f), 0.001f)
    }

    @Test
    fun shakeNeverLeavesItsAmplitude() {
        // 12dp ist der Abstand, den die Karte zur Nachbarkarte hat (8dp Lücke,
        // graphicsLayer clippt nicht) — mehr würde sichtbar überlappen.
        (0..100).forEach { i ->
            val p = i / 100f
            val offset = SentencePictureCardShake.offsetDp(p)
            assertTrue(
                "offset $offset at progress $p exceeds ${SentencePictureCardShake.AmplitudeDp}dp",
                kotlin.math.abs(offset) <= SentencePictureCardShake.AmplitudeDp + 0.001f,
            )
        }
    }

    @Test
    fun shakeOscillatesAsOftenAsCyclesPromises() {
        // 2.5 Zyklen einer Sinuskurve haben 5 Halbwellen, also 4 Nulldurchgänge
        // im offenen Intervall. Weniger wäre ein Ausschlag statt eines Wackelns.
        val samples = (1..999).map { SentencePictureCardShake.offsetDp(it / 1000f) }
        val signChanges = (1 until samples.size).count { i ->
            samples[i - 1] < 0f && samples[i] > 0f || samples[i - 1] > 0f && samples[i] < 0f
        }
        assertEquals(4, signChanges)
    }

    @Test
    fun shakeAmplitudeDecaysSoTheCardComesToRest() {
        // Ohne abklingende Hüllkurve stoppt die Karte mitten im vollen Ausschlag
        // — das liest sich wie ein Ruck, nicht wie ein Auslaufen. Verglichen
        // werden die Extrema der ersten und der letzten Halbwelle.
        fun peakBetween(from: Float, to: Float): Float =
            (0..200).map { from + (to - from) * it / 200f }
                .maxOf { kotlin.math.abs(SentencePictureCardShake.offsetDp(it)) }

        val firstHalfWave = peakBetween(0f, 0.2f)
        val lastHalfWave = peakBetween(0.8f, 1f)
        assertTrue(
            "first peak $firstHalfWave should be clearly larger than last $lastHalfWave",
            firstHalfWave > lastHalfWave * 1.5f,
        )
    }

    @Test
    fun shakeClampsProgressOutsideTheUnitInterval() {
        // animateFloatAsState kann bei Spring-Overshoot über 1f laufen; ein
        // Aufruf mit 1.05f darf keinen Sprung erzeugen.
        assertEquals(0f, SentencePictureCardShake.offsetDp(-0.5f), 0.001f)
        assertEquals(0f, SentencePictureCardShake.offsetDp(1.5f), 0.001f)
    }
}
