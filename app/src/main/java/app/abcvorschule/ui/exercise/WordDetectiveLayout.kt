package app.abcvorschule.ui.exercise

import kotlin.math.max
import kotlin.math.min

/**
 * Layout des Worts im Wort-Detektiv unter dem Sternenhimmel (Spec
 * `2026-10-02-wort-detektiv-sternenhimmel-design.md`) — Compose-frei wie die anderen
 * `*Sizing.kt`, alles in dp.
 *
 * Bis Oktober 2026 war jedes Segment eine eigene ≥ 56 dp **breite** Tipp-Fläche mit
 * Lücke, und lange Wörter brachen nach Segmentanzahl um (`WordFrameSizing.segmentsPerRow`)
 * — „Häuser" las sich als Buchstabensalat, „Schneemann" brach mitten in der Silbe. Jetzt
 * gilt:
 *
 * 1. **Das Wort steht als ein Wort.** Die Glyphen liegen in ihrer natürlichen Laufweite
 *    nebeneinander ([SegmentBox.glyphX] lückenlos); die Tipp-Fläche wächst in die Höhe
 *    ([MinHitHeightDp]), nicht in die Breite.
 * 2. **Schmale Segmente** („i", „l") bekommen unsichtbaren Zusatzrand bis
 *    [MinHitWidthDp], genommen von den Nachbarn bzw. vom Rand der Bühne — die sichtbare
 *    Laufweite ändert das nicht.
 * 3. **Kein Umbruch nach Segmentanzahl.** Die Schriftgröße passt das Wort in eine Zeile.
 *    Erst wenn das bei [MinGlyphDp] nicht mehr geht (oder die Tipp-Flächen nicht in die
 *    Zeile passen), wird **an einer Silbengrenze** getrennt, mit Trennstrich. Ohne
 *    verlässliche Silbengrenze wird kleiner gesetzt statt falsch getrennt, bis
 *    [FloorGlyphDp].
 *
 * Eingabe sind die gemessenen Vorschübe je Segment in em (Breite bei 1 dp
 * Schriftgröße), nicht geschätzte Zeichenzahlen: Silbo Fibel ist proportional, ein „m"
 * ist fast dreimal so breit wie ein „i", und genau darauf kommt es hier an.
 */
object WordDetectiveLayout {
    /** Größte Wortgröße (gerenderte dp) — kurze Wörter wie „Tomate" im Mockup. */
    const val MaxGlyphDp = 72f

    /**
     * Darunter wird an einer Silbengrenze getrennt, wenn es eine gibt. Ein langes Wort
     * auf zwei Zeilen liest sich für ein Vorschulkind leichter als ein winziges in einer.
     */
    const val MinGlyphDp = 40f

    /** Letzte Grenze, nur ohne verlässliche Silbengrenze: lieber klein als falsch getrennt. */
    const val FloorGlyphDp = 28f

    /**
     * Mindestbreite einer Tipp-Fläche. Die Spec nennt ≥ 32 dp als Richtwert; 36 dp ist die
     * mittlere Segmentbreite des längsten Worts im Pack („Taschenlampe", 10 Segmente bei
     * ~53 dp auf dem Moto, 370 dp Bühne) — das Wort selbst hat also so viel Platz je
     * Segment, und nur „i"/„l"/„t" müssen von ihren Nachbarn leihen. Die Höhe trägt den
     * Rest der Treffsicherheit ([MinHitHeightDp]).
     */
    const val MinHitWidthDp = 36f

    /** Der Tipp-Boden der App (Prinzip 7) — hier in der Höhe, nicht in der Breite. */
    const val MinHitHeightDp = 56f

    /** Ascent + Descent von Silbo Fibel Bold ((2080 + 515) / 2048 em): die Höhe der Textzeile. */
    const val LineHeightFactor = 1.267f

    /** Grundlinie unter der Oberkante der Textzeile (Ascent, 2080 / 2048 em). */
    const val AscentFactor = 1.016f

    /** Versalhöhe (OS/2 `sCapHeight`), für die Lage der Rahmen-Sterne. */
    const val CapHeightFactor = 0.725f

    /** x-Höhe, Spiegelachse der Rahmen-Sterne auf der oberen von zwei Zeilen. */
    const val XHeightFactor = 0.508f

    /**
     * Das gesuchte Symbol über dem Wort, fest in dp: kleiner als das Wort an seiner
     * kleinsten Trennschwelle ([MinGlyphDp]), damit die Frage immer kleiner ist als die
     * Arbeit — bei jeder Systemschriftgröße, weil beides in dp gerechnet wird.
     */
    const val TargetLabelDp = 38f

    /** Ein Segment: wo seine Glyphe steht und wo seine (breitere) Tipp-Fläche liegt. */
    data class SegmentBox(
        val index: Int,
        val glyphX: Float,
        val glyphWidth: Float,
        val hitX: Float,
        val hitWidth: Float,
    ) {
        val glyphCenterX: Float get() = glyphX + glyphWidth / 2f
    }

    /** Eine Zeile; [hyphenX] ist gesetzt, wenn sie mit einem Trennstrich endet. */
    data class Line(val segments: List<SegmentBox>, val hyphenX: Float?)

    data class Layout(
        /** Gerenderte Glyphgröße in dp (sp = dp / fontScale). */
        val glyphDp: Float,
        val lines: List<Line>,
        /** Breite der Bühne, auf die alle x-Werte bezogen sind. */
        val widthDp: Float,
    ) {
        val lineHeightDp: Float get() = glyphDp * LineHeightFactor

        /** Höhe einer Zeile samt Tipp-Fläche: mindestens [MinHitHeightDp]. */
        val rowHeightDp: Float get() = max(lineHeightDp, MinHitHeightDp)

        /** Grundlinie unter der Oberkante einer Zeile — der Text steht senkrecht mittig. */
        val baselineDp: Float get() = (rowHeightDp - lineHeightDp) / 2f + glyphDp * AscentFactor

        val breakBefore: Int? get() = lines.getOrNull(1)?.segments?.firstOrNull()?.index
    }

    /**
     * @param advances Vorschub je Segment in em, in Lesereihenfolge.
     * @param hyphenAdvance Vorschub des Trennstrichs in em.
     * @param breakBefore Segment-Indizes, vor denen getrennt werden *darf* — nur
     *   verlässliche Silbengrenzen ([app.abcvorschule.content.SymbolInWordRound.breakBefore]).
     * @param availableDp nutzbare Breite der Bühne.
     */
    fun layout(
        advances: List<Float>,
        hyphenAdvance: Float,
        breakBefore: Collection<Int>,
        availableDp: Float,
        /** Deckel aus der Höhe ([verticalFit]); die Breite allein ließe bis [MaxGlyphDp] zu. */
        maxGlyphDp: Float = MaxGlyphDp,
    ): Layout {
        val available = availableDp.coerceAtLeast(1f)
        val n = advances.size
        if (n == 0) return Layout(maxGlyphDp.coerceIn(FloorGlyphDp, MaxGlyphDp), emptyList(), available)

        fun em(range: IntRange, hyphen: Boolean): Float =
            range.fold(0f) { sum, i -> sum + advances[i] } + if (hyphen) hyphenAdvance else 0f

        val cap = maxGlyphDp.coerceIn(FloorGlyphDp, MaxGlyphDp)
        fun sizeFor(widthEm: Float): Float = if (widthEm <= 0f) cap else min(cap, available / widthEm)

        fun hitsFit(count: Int): Boolean = count * MinHitWidthDp <= available

        data class Option(val split: Int?, val size: Float, val hitsOk: Boolean)

        val oneLine = Option(null, sizeFor(em(0 until n, hyphen = false)), hitsFit(n))
        // Lexikografisch: erst ob die Tipp-Flächen passen, dann ob die Schrift über der
        // Trennschwelle bleibt, dann die Größe selbst. Eine Trennung muss die eine Zeile
        // echt schlagen — passt das Wort bei [MinGlyphDp] in eine Zeile, bleibt es dort,
        // auch wenn es getrennt größer würde.
        val better = compareBy<Option>({ it.hitsOk }, { it.size >= MinGlyphDp }, { it.size })
        var chosen = oneLine
        if (!(oneLine.hitsOk && oneLine.size >= MinGlyphDp)) {
            breakBefore.filter { it in 1 until n }.distinct().sorted().forEach { k ->
                val widest = max(em(0 until k, hyphen = true), em(k until n, hyphen = false))
                val option = Option(k, sizeFor(widest), hitsFit(k) && hitsFit(n - k))
                if (better.compare(option, chosen) > 0) chosen = option
            }
        }
        val glyph = max(chosen.size, FloorGlyphDp)

        val ranges = chosen.split?.let { listOf(0 until it, it until n) } ?: listOf(0 until n)
        val lines = ranges.mapIndexed { lineIndex, range ->
            val hyphen = lineIndex < ranges.lastIndex
            val visible = em(range, hyphen) * glyph
            // Das sichtbare Wort steht mittig, nicht die Summe der Tipp-Flächen: ein
            // schmales „i" am Rand darf seine Fläche nach außen ziehen, ohne das Wort
            // zu verschieben.
            val start = (available - visible) / 2f
            val edges = FloatArray(range.count() + 1)
            edges[0] = start
            range.forEachIndexed { k, i -> edges[k + 1] = edges[k] + advances[i] * glyph }
            val minHit = min(MinHitWidthDp, available / range.count())
            val hits = keepCentresInside(spreadHits(edges, minHit, lo = 0f, hi = available), edges)
            Line(
                segments = range.mapIndexed { k, i ->
                    SegmentBox(
                        index = i,
                        glyphX = edges[k],
                        glyphWidth = edges[k + 1] - edges[k],
                        hitX = hits[k],
                        hitWidth = hits[k + 1] - hits[k],
                    )
                },
                hyphenX = if (hyphen) edges.last() else null,
            )
        }
        return Layout(glyph, lines, available)
    }

    /**
     * Grenzen der Tipp-Flächen: so nah wie möglich an den Glyphgrenzen [edges], aber
     * jede Fläche mindestens [minWidth] breit und alles in [lo]…[hi].
     *
     * Mit `y_k = b_k − k·minWidth` wird „jede Fläche ≥ minWidth" zu „y steigt monoton",
     * und die kleinste Verschiebung (Quadratsumme) ist eine isotone Regression — Pool
     * Adjacent Violators. Ein schmales „i" verteilt sein Defizit damit auf beide Nachbarn
     * und, am Wortrand, auf den freien Platz daneben; wer breit genug ist und keinen
     * schmalen Nachbarn hat, behält genau seine Glyphbreite.
     */
    internal fun spreadHits(edges: FloatArray, minWidth: Float, lo: Float, hi: Float): FloatArray {
        val count = edges.size - 1
        val targets = FloatArray(edges.size) { edges[it] - it * minWidth }
        val means = ArrayList<Float>()
        val sizes = ArrayList<Int>()
        targets.forEach { t ->
            means += t
            sizes += 1
            while (means.size > 1 && means[means.size - 2] > means.last()) {
                val w1 = sizes[sizes.size - 2]
                val w2 = sizes.last()
                val merged = (means[means.size - 2] * w1 + means.last() * w2) / (w1 + w2)
                means.removeAt(means.lastIndex)
                sizes.removeAt(sizes.lastIndex)
                means[means.lastIndex] = merged
                sizes[sizes.lastIndex] = w1 + w2
            }
        }
        val yLo = lo
        val yHi = max(lo, hi - count * minWidth)
        val out = FloatArray(edges.size)
        var k = 0
        means.forEachIndexed { block, mean ->
            repeat(sizes[block]) {
                // Das Klemmen einer monotonen Folge bleibt monoton, und für eine isotone
                // Regression mit einheitlichen Schranken ist es die Lösung mit Schranken.
                out[k] = mean.coerceIn(yLo, yHi) + k * minWidth
                k++
            }
        }
        return out
    }

    /**
     * Jede Tipp-Fläche enthält die Mitte ihrer eigenen Glyphe — das geht vor der
     * Mindestbreite. Wird es eng (zehn Segmente auf fast der ganzen Bühne), verteilt
     * [spreadHits] sonst beinahe gleich breite Flächen über ungleich breite Glyphen, und
     * ein Tipp mitten aufs „e" in „Taschenlampe" träfe das „sch" daneben. Eine Grenze
     * liegt deshalb immer zwischen den Mitten ihrer beiden Nachbarn; das Klemmen in diese
     * geordneten Intervalle hält die Folge monoton.
     */
    internal fun keepCentresInside(bounds: FloatArray, edges: FloatArray): FloatArray {
        val last = edges.size - 1
        fun centre(k: Int) = (edges[k] + edges[k + 1]) / 2f
        return FloatArray(bounds.size) { k ->
            when (k) {
                0 -> min(bounds[0], centre(0))
                last -> max(bounds[last], centre(last - 1))
                else -> bounds[k].coerceIn(centre(k - 1), centre(k))
            }
        }
    }

    /** Ein Rahmen-Stern, in dp relativ zur Oberkante der Zeile. */
    data class StarPoint(val x: Float, val y: Float)

    /**
     * Wo die Sterne eines gefundenen Segments stehen (Mockups „Sch" und „Tomate"):
     * ein Buchstabe bekommt **zwei** (oben links, unten rechts, diagonal), ein
     * Mehrzeichen-Graphem oder eine Silbe **drei** (oben links, oben rechts, unten
     * mittig — ein kleines Dreieck). Die Werte sind die Mockup-Lagen in em, bezogen auf
     * Glyphkante und Grundlinie.
     *
     * Auf der oberen von zwei Zeilen wird das Muster an der Mitte der x-Höhe gespiegelt:
     * sonst fiele der untere Stern in die Versalien der zweiten Zeile.
     */
    fun frameStars(box: SegmentBox, chars: Int, layout: Layout, upperOfTwo: Boolean): List<StarPoint> {
        val g = layout.glyphDp
        val base = layout.baselineDp
        val left = box.glyphX
        val right = box.glyphX + box.glyphWidth
        val points = if (chars <= 1) {
            listOf(
                StarPoint(left - 0.14f * g, base - 1.02f * g),
                StarPoint(right + 0.07f * g, base + 0.17f * g),
            )
        } else {
            listOf(
                StarPoint(left - 0.2f * g, base - 1.12f * g),
                StarPoint(right + 0.06f * g, base - 1.0f * g),
                StarPoint(left + 0.45f * box.glyphWidth, base + 0.5f * g),
            )
        }
        if (!upperOfTwo) return points
        val axis = base - XHeightFactor * g / 2f
        return points.map { it.copy(y = 2f * axis - it.y) }
    }

    /** Größe eines Rahmen-Sterns (Spitze zu Mitte), mit der Schrift skaliert, aber gedeckelt. */
    fun frameStarRadiusDp(glyphDp: Float): Float = (glyphDp * 0.17f).coerceIn(8f, 12f)

    // --- Höhe: Symbol, Wort und Silhouetten als eine Gruppe ----------------------

    /** Natürliche Luft Symbol → Wort und Wort → Silhouetten (Mockup), und wie weit sie schrumpfen darf. */
    const val LabelToWordDp = 58f
    const val MinLabelToWordDp = 16f
    const val WordToStarsDp = 92f
    const val MinWordToStarsDp = 20f

    /** Kleinste Silhouette, bevor stattdessen das Wort kleiner wird. */
    const val CompactSilhouetteDp = 36f

    data class VerticalFit(
        val labelToWordDp: Float,
        val wordToStarsDp: Float,
        val silhouetteCapDp: Float,
        val glyphCapDp: Float,
    )

    /**
     * Wie die Gruppe in [availableHeightDp] passt. `ExerciseStage` scrollt nicht und
     * clippt nicht — zu hoch heißt: Compose quetscht das letzte Kind (bis Oktober 2026
     * die zweite Wortreihe auf der 640-dp-Höhenklasse, Residual P1). Deshalb in dieser
     * Reihenfolge nachgeben: erst die Luft (anteilig bis zu ihren Mindestwerten), dann
     * die Silhouetten bis [CompactSilhouetteDp], zuletzt die Wortgröße — das Wort ist die
     * Arbeit und gibt als Letztes nach, die Tipp-Höhe von [MinHitHeightDp] nie.
     */
    fun verticalFit(availableHeightDp: Float, labelHeightDp: Float, lineCount: Int): VerticalFit {
        val lines = lineCount.coerceAtLeast(1)
        fun row(glyph: Float) = max(glyph * LineHeightFactor, MinHitHeightDp)
        val natural = labelHeightDp + LabelToWordDp + lines * row(MaxGlyphDp) + WordToStarsDp + SilhouetteDp
        var slack = natural - availableHeightDp
        if (slack <= 0f) return VerticalFit(LabelToWordDp, WordToStarsDp, SilhouetteDp, MaxGlyphDp)
        val gapRoomA = LabelToWordDp - MinLabelToWordDp
        val gapRoomB = WordToStarsDp - MinWordToStarsDp
        val gapCut = min(slack, gapRoomA + gapRoomB)
        val a = LabelToWordDp - gapCut * gapRoomA / (gapRoomA + gapRoomB)
        val b = WordToStarsDp - gapCut * gapRoomB / (gapRoomA + gapRoomB)
        slack -= gapCut
        val starCut = min(slack.coerceAtLeast(0f), SilhouetteDp - CompactSilhouetteDp)
        val stars = SilhouetteDp - starCut
        slack -= starCut
        if (slack <= 0f) return VerticalFit(a, b, stars, MaxGlyphDp)
        val perLine = (availableHeightDp - labelHeightDp - a - b - stars) / lines
        val glyph = (perLine / LineHeightFactor).coerceIn(FloorGlyphDp, MaxGlyphDp)
        return VerticalFit(a, b, stars, glyph)
    }

    // --- Stern-Silhouetten unter dem Wort --------------------------------------

    /** Kantenlänge einer Silhouette, wie im Mockup. */
    const val SilhouetteDp = 54f

    /** Kleinste Silhouette; erst darunter dürfte die Reihe den Rand berühren. */
    const val MinSilhouetteDp = 20f

    const val SilhouetteGapDp = 18f

    /** Größe einer Silhouette und Abstand, wenn [count] davon zwischen die Ränder passen sollen. */
    data class SilhouetteRow(val sizeDp: Float, val gapDp: Float)

    /**
     * Die Reihe steht mittig, darf aber nicht in [keepOutDp] links und rechts ragen —
     * links steht das Teleskop, und Sterne dürfen es nie berühren
     * (`StarsScene.TelescopeZoneWidthDp`); symmetrisch, damit die Reihe mittig bleibt.
     * Passt sie nicht, schrumpfen Silhouetten und Abstand gemeinsam, bis
     * [MinSilhouetteDp]; der aktuelle Content hat höchstens zwei Treffer, die passen auch
     * auf 320 dp in voller Größe.
     */
    fun silhouetteRow(count: Int, widthDp: Float, keepOutDp: Float, maxSizeDp: Float = SilhouetteDp): SilhouetteRow {
        val size = maxSizeDp.coerceIn(MinSilhouetteDp, SilhouetteDp)
        val gap = SilhouetteGapDp * size / SilhouetteDp
        if (count <= 0) return SilhouetteRow(size, gap)
        val usable = (widthDp - 2f * keepOutDp).coerceAtLeast(0f)
        val full = count * size + (count - 1) * gap
        if (full <= usable) return SilhouetteRow(size, gap)
        val scale = (usable / full).coerceAtLeast(MinSilhouetteDp / size)
        return SilhouetteRow(size * scale, gap * scale)
    }
}
