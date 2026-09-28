package app.abcvorschule.ui.shell

import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LessonFinale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Das Sternbild des End-Screens (PRODUCT_PRINCIPLES §10, „Lektions-Ende"): die Sterne der
 * Lektion setzen sich am Abendhimmel zu einem Buchstaben zusammen. Compose-frei.
 *
 * **Welcher Buchstabe** ([letterFor], Nutzerentscheidung): er soll zum Finale-Satz passen
 * **und** in der Lektion geübt worden sein. Also zuerst der Anfangsbuchstabe des ersten
 * Satzworts, der ein geübter Buchstabe ist („**M**ama Maus mampft …" in einer M-Lektion),
 * dann ein geübter Buchstabe, der sonst im Satz steht; erst wenn keiner passt, der
 * Anfangsbuchstabe des ersten Bild-Nomens. Nur Einzelbuchstaben mit Spurdaten — „Sch"
 * oder „Ei" als Sternbild wären kein Buchstabe mehr, den das Kind am Himmel erkennt.
 *
 * **Wie die Sterne liegen** ([of]): aus den Spurdaten des Buchstabens (`Atom.strokes`,
 * dieselben Wege wie im Spurensucher). Jeder Strich wird vereinfacht (Ecken bleiben, eine
 * Rundung wird zu wenigen Punkten), lange Stücke bekommen Zwischensterne, und Sterne,
 * an denen sich zwei Striche treffen, werden einer.
 */
object FinaleConstellation {
    /** Toleranz der Vereinfachung, im Einheitsquadrat des Buchstabens. */
    const val SimplifyTolerance = 0.03f

    /** Längstes Stück zwischen zwei Sternen, bevor ein Zwischenstern kommt. */
    const val MaxGap = 0.3f

    /** Näher als das beieinander, und zwei Sterne sind einer. */
    const val MergeDistance = 0.07f

    data class Constellation(
        val letter: String,
        /** Sterne im Einheitsquadrat (0…1, y nach unten), in Zeichenreihenfolge. */
        val stars: List<Pair<Float, Float>>,
        /** Linien als Paare von Stern-Indizes, in Zeichenreihenfolge. */
        val lines: List<Pair<Int, Int>>,
    )

    fun letterFor(lesson: Lesson?, finale: LessonFinale?, pack: ContentPack): Atom? {
        val practiced = lesson?.focusAtomIds.orEmpty()
            .mapNotNull { pack.atoms[it] }
            .filter { it.usable() }
        val byLetter = practiced.associateBy { it.display.uppercase() }
        val text = finale?.text.orEmpty()
        val words = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        words.firstNotNullOfOrNull { byLetter[it.first().uppercase()] }?.let { return it }
        practiced.firstOrNull { text.contains(it.display, ignoreCase = true) }?.let { return it }
        val noun = finale?.pictureAtomIds?.firstNotNullOfOrNull { pack.atoms[it] }
        val initial = noun?.display?.firstOrNull()?.uppercase()
        pack.atoms.values.firstOrNull { it.usable() && it.display.uppercase() == initial }?.let { return it }
        return practiced.firstOrNull()
    }

    private fun Atom.usable(): Boolean =
        kind == AtomKind.letter && display.length == 1 && display != "ß" && strokes.isNotEmpty()

    fun of(atom: Atom): Constellation {
        val stars = mutableListOf<Pair<Float, Float>>()
        val lines = mutableListOf<Pair<Int, Int>>()
        fun indexOf(p: Pair<Float, Float>): Int {
            val hit = stars.indexOfFirst { hypot(it.first - p.first, it.second - p.second) < MergeDistance }
            if (hit >= 0) return hit
            stars += p
            return stars.lastIndex
        }
        atom.strokes.forEach { stroke ->
            val raw = stroke.points.filter { it.size >= 2 }.map { it[0].toFloat() to it[1].toFloat() }
            if (raw.isEmpty()) return@forEach
            val simple = simplify(raw, SimplifyTolerance)
            var previous = indexOf(simple.first())
            for (i in 1 until simple.size) {
                val a = simple[i - 1]
                val b = simple[i]
                val pieces = ceil(hypot(b.first - a.first, b.second - a.second) / MaxGap).toInt().coerceAtLeast(1)
                for (k in 1..pieces) {
                    val t = k / pieces.toFloat()
                    val next = indexOf((a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t))
                    if (next != previous && lines.none { (x, y) -> (x == previous && y == next) || (x == next && y == previous) }) {
                        lines += previous to next
                    }
                    previous = next
                }
            }
        }
        return Constellation(atom.display.uppercase(), stars, lines)
    }

    /** Ramer–Douglas–Peucker: behält Ecken, macht aus einer Rundung wenige Punkte. */
    internal fun simplify(points: List<Pair<Float, Float>>, tolerance: Float): List<Pair<Float, Float>> {
        if (points.size < 3) return points
        val first = points.first()
        val last = points.last()
        var worst = 0f
        var index = 0
        for (i in 1 until points.lastIndex) {
            val d = distanceToSegment(points[i], first, last)
            if (d > worst) {
                worst = d
                index = i
            }
        }
        if (worst <= tolerance) {
            // Ein geschlossener Strich (O): Anfang und Ende fallen zusammen, die Strecke
            // dazwischen ist null lang — dann trägt der fernste Punkt die Form.
            return if (hypot(first.first - last.first, first.second - last.second) < tolerance && points.size > 2) {
                simplify(points.subList(0, points.size / 2 + 1), tolerance).dropLast(1) +
                    simplify(points.subList(points.size / 2, points.size), tolerance)
            } else {
                listOf(first, last)
            }
        }
        return simplify(points.subList(0, index + 1), tolerance).dropLast(1) +
            simplify(points.subList(index, points.size), tolerance)
    }

    private fun distanceToSegment(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len = dx * dx + dy * dy
        if (len < 1e-9f) return hypot(p.first - a.first, p.second - a.second)
        val t = (((p.first - a.first) * dx + (p.second - a.second) * dy) / len).coerceIn(0f, 1f)
        return abs(hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy)))
    }
}
