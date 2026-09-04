package app.abcvorschule.content

import kotlin.math.floor
import kotlin.random.Random

/**
 * Reine Ableitung des Laut-Fressers (design doc §3/§4): welche Lektion welches
 * Paar spielt und welche Karten sie zieht. Läuft über **alle** Lektionen in
 * Index-Reihenfolge, weil zwei Entscheidungen von der Vorgeschichte abhängen —
 * „am längsten nicht gespielt" und die Rotation durch den Wortvorrat. Nichts hier
 * ist zufällig außer der Kartenreihenfolge, und die ist aus der Lektions-ID gesät.
 */
object SoundFeederDerivation {
    const val Prompt = "Füttere die Laut-Fresser."

    /** L01 und L02 bleiben frei — dort lernt das Kind noch die App. */
    const val FirstLessonIndex = 3
    const val MaxCards = 7
    const val MinPerSide = 2
    const val MinSupply = 6
    const val MaxTwinPairs = 2

    data class Assignment(val pair: SoundPair, val replay: Boolean)

    /** Kartentaugliche Wörter je Seite, alphabetisch — die Reihenfolge, durch die rotiert wird. */
    data class Supply(val left: List<Atom>, val right: List<Atom>) {
        val total: Int get() = left.size + right.size
        val sufficient: Boolean
            get() = left.size >= MinPerSide && right.size >= MinPerSide && total >= MinSupply
    }

    fun supply(pack: ContentPack, pair: SoundPair): Supply {
        val worthy = pack.atoms.values.filter { SoundPairs.isCardWorthy(it) }
        fun side(own: String, other: String) = worthy
            .filter { atom ->
                val hit = if (pair.anywhere) SoundPairs.contains(atom.display, own) else SoundPairs.startsWith(atom.display, own)
                hit && !SoundPairs.contains(atom.display, other)
            }
            .sortedBy { it.display }
        return Supply(side(pair.left, pair.right), side(pair.right, pair.left))
    }

    fun assignments(pack: ContentPack): Map<String, Assignment> = walk(pack).mapValues { it.value.first }

    fun derive(pack: ContentPack): Map<String, SoundFeederRound> = walk(pack).mapValues { it.value.second }

    fun buildRounds(pack: ContentPack, lesson: Lesson): List<SoundFeederRound> =
        listOfNotNull(derive(pack)[lesson.id])

    /** Alle Atome, die irgendeine Lektion auf eine Karte legt — für „Kein Atom ohne Auftritt". */
    fun shownAtomIds(pack: ContentPack): Set<String> =
        derive(pack).values.flatMap { round -> round.cards.map { it.atomId } }.toSet()

    /**
     * Proportional zum Vorrat, aber jede Seite behält mindestens [MinPerSide] —
     * sonst könnte das Kind die letzten Karten abzählen statt hinzuhören.
     */
    internal fun splitCounts(total: Int, leftSupply: Int, rightSupply: Int): Pair<Int, Int> {
        val raw = floor(total * leftSupply.toFloat() / (leftSupply + rightSupply) + 0.5f).toInt()
        val left = raw.coerceIn(MinPerSide, total - MinPerSide)
        return left to (total - left)
    }

    private fun walk(pack: ContentPack): Map<String, Pair<Assignment, SoundFeederRound>> {
        val supplies = SoundPairs.table.associateWith { supply(pack, it) }
        val introduced = SoundPairs.table.flatMap { it.graphemes }.distinct()
            .associateWith { SoundPairs.introductionIndex(pack, it) }
        val lastPlayed = mutableMapOf<SoundPair, Int>()
        val sidePlays = mutableMapOf<String, Int>()
        val result = linkedMapOf<String, Pair<Assignment, SoundFeederRound>>()

        pack.lessons.sortedBy { it.index }.forEach { lesson ->
            if (lesson.index < FirstLessonIndex) return@forEach
            val focus = lesson.focusAtomIds.mapNotNull { pack.atoms[it]?.display }.toSet()
            fun eligible(pair: SoundPair) =
                pair.graphemes.all { g -> introduced[g]?.let { it <= lesson.index } == true } &&
                    supplies.getValue(pair).sufficient
            // Nie gespielt sortiert vor allem Gespielten (0 < jeder Lektionsindex),
            // dann das am längsten Zurückliegende, dann die Tabellenreihenfolge.
            val byRecency = compareBy<SoundPair>({ lastPlayed[it] ?: 0 }, { SoundPairs.table.indexOf(it) })

            var replay = false
            val pick = SoundPairTier.entries.firstNotNullOfOrNull { tier ->
                SoundPairs.table
                    .filter { it.tier == tier && it.graphemes.any { g -> g in focus } && eligible(it) }
                    .minWithOrNull(byRecency)
            } ?: run {
                replay = true
                SoundPairs.table
                    .filter { it.tier == SoundPairTier.Contrast && eligible(it) }
                    .minWithOrNull(byRecency)
            } ?: return@forEach

            lastPlayed[pick] = lesson.index
            val round = buildRound(pack, lesson, pick, supplies.getValue(pick), sidePlays)
            result[lesson.id] = Assignment(pick, replay) to round
        }
        return result
    }

    /**
     * Zwillinge zuerst, dann der Rest per Rotation: jede Seite beginnt dort im
     * alphabetischen Vorrat, wo dieser Laut zuletzt aufgehört hat — über alle Paare
     * hinweg, in denen er vorkommt. So kommt jedes T-Wort einmal dran, obwohl kein
     * einzelnes Paar den Vorrat ausschöpft (design doc §4).
     */
    private fun buildRound(
        pack: ContentPack,
        lesson: Lesson,
        pair: SoundPair,
        supply: Supply,
        sidePlays: MutableMap<String, Int>,
    ): SoundFeederRound {
        val total = minOf(MaxCards, supply.total)
        val (leftCount, rightCount) = splitCounts(total, supply.left.size, supply.right.size)
        val usedEmojis = mutableSetOf<String>()
        val usedIds = mutableSetOf<String>()
        val units = mutableListOf<List<SoundFeederCard>>()

        fun take(atom: Atom, side: FeederSide): SoundFeederCard? {
            if (atom.id in usedIds || atom.emoji in usedEmojis) return null
            usedIds += atom.id
            usedEmojis += atom.emoji
            return SoundFeederCard(atom.id, side)
        }

        if (!pair.anywhere) {
            val twins = supply.left.flatMap { l ->
                supply.right.filter { r -> SoundPairs.rest(l.display) == SoundPairs.rest(r.display) }.map { l to it }
            }
            for ((l, r) in twins.take(MaxTwinPairs)) {
                if (l.id in usedIds || r.id in usedIds || l.emoji in usedEmojis || r.emoji in usedEmojis) continue
                val a = take(l, FeederSide.left) ?: continue
                val b = take(r, FeederSide.right) ?: continue
                units += listOf(a, b)
            }
        }

        fun fill(words: List<Atom>, side: FeederSide, count: Int, grapheme: String) {
            var have = units.flatten().count { it.side == side }
            val plays = sidePlays[grapheme] ?: 0
            val offset = if (words.isEmpty()) 0 else (plays * count) % words.size
            var step = 0
            while (have < count && step < words.size) {
                val atom = words[(offset + step) % words.size]
                step += 1
                val card = take(atom, side) ?: continue
                units += listOf(card)
                have += 1
            }
            sidePlays[grapheme] = plays + 1
        }
        fill(supply.left, FeederSide.left, leftCount, pair.left)
        fill(supply.right, FeederSide.right, rightCount, pair.right)

        // Nur die Reihenfolge ist zufällig — und die ist aus der Lektions-ID gesät,
        // damit ein Kind, das wiederholt, dasselbe Spiel sieht.
        val rng = Random(lesson.id.hashCode())
        val cards = units.shuffled(rng).flatMap { unit ->
            if (unit.size == 2 && rng.nextBoolean()) unit.reversed() else unit
        }
        return SoundFeederRound(
            promptTts = Prompt,
            leftAtomId = SoundPairs.letterAtom(pack, pair.left)?.id ?: error("no letter atom for ${pair.left}"),
            rightAtomId = SoundPairs.letterAtom(pack, pair.right)?.id ?: error("no letter atom for ${pair.right}"),
            cards = cards,
            anywhere = pair.anywhere,
        )
    }
}
