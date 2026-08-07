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
        // − 14dp Kartenabstand, / 2 Karten = 133dp Karte, − 2 × 10dp Karteninnen-
        // abstand = 113dp Inhaltsbreite. Der Validator erlaubt 1..3 Atome je Karte;
        // ein Test, der nur 2 prüft, würde die 3-Atom-Karten übersehen — genau die,
        // bei denen vorher das letzte Emoji verschwand.
        val narrowestCardContentWidthDp = 113f
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
    fun emojiSizeIsUnchangedAtFontScaleOneWhenThereIsRoom() {
        // Kein-Regressions-Anker: auf einer breiten Karte bei fontScale 1.0 bleiben
        // die ursprünglichen 72/56/44sp stehen.
        val wide = 400f
        assertEquals(72f, SentencePictureCardSizing.emojiSp(1, wide, 1f), 0f)
        assertEquals(56f, SentencePictureCardSizing.emojiSp(2, wide, 1f), 0f)
        assertEquals(44f, SentencePictureCardSizing.emojiSp(3, wide, 1f), 0f)
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
}
