package app.abcvorschule.content

/**
 * Pure derivation for the Wort-Detektiv (design doc §4): which symbol each word
 * hunts, in which mode, where its hits sit, and how the target is labelled. No
 * JSON is read beyond what [ContentPack] already parsed, and nothing here is
 * random — the same lesson always yields the same rounds.
 */
object SymbolInWordDerivation {
    private const val PromptLetterOne = "Finde den Buchstaben - %s - im Wort - %s."
    private const val PromptLetterMany = "Finde alle Buchstaben - %s - im Wort - %s."
    private const val PromptSyllableOne = "Finde die Silbe - %s - im Wort - %s."
    private const val PromptSyllableMany = "Finde alle Silben - %s - im Wort - %s."

    /**
     * Below this the word *is* the answer: L22 builds "Ei", a single grapheme, and
     * a round with one segment cannot be tapped wrong.
     */
    private const val MinSegments = 2

    /**
     * How the hunted symbol is shown. [alternate] carries the other case form so a
     * letter round can display "P / p" — the child learns the pair in the moment it
     * needs it, instead of having to bring the equivalence along. Null for
     * syllables and for graphemes that only exist in one form (`ck`, `ß`).
     */
    data class TargetLabel(val primary: String, val alternate: String?)

    fun targetLabel(target: Atom, mode: SymbolInWordMode): TargetLabel = when (mode) {
        // An uppercase syllable exists only because it happens to start a word —
        // not a second learnable glyph, so it is never shown (design doc §2).
        SymbolInWordMode.syllable -> TargetLabel(target.display, null)
        SymbolInWordMode.letter -> {
            val lower = target.display.lowercase()
            TargetLabel(target.display, lower.takeIf { it != target.display })
        }
    }

    fun buildRounds(pack: ContentPack, lesson: Lesson): List<SymbolInWordRound> {
        val specs = lesson.taskIds.mapNotNull { pack.tasks[it] }
        val focusLetterAtomIds = specs.filterIsInstance<LetterTraceSpec>()
            .flatMap { spec -> spec.rounds.map { it.atomId } }
            .distinct()
        val focusSyllableAtomIds = specs.filterIsInstance<SyllableMergeSpec>()
            .flatMap { spec -> spec.rounds.map { it.resultAtomId } }
            .toSet()
        val words = specs.filterIsInstance<WordBuildSpec>()
            .flatMap { it.rounds }
            .distinctBy { it.targetAtomId }

        val rounds = mutableListOf<SymbolInWordRound>()
        var focusCursor = 0
        words.forEach { word ->
            val wordAtom = pack.atoms[word.targetAtomId] ?: return@forEach
            val graphemes = WordGraphemes.split(pack, lesson.index, wordAtom.display)
            if (graphemes.size < MinSegments) return@forEach

            // The index that drives alternation counts *produced* rounds, not words
            // looked at — a skipped word must not flip the next word's mode.
            val wantsSyllable = rounds.size % 2 == 1
            val syllable = if (wantsSyllable) {
                syllableRound(pack, wordAtom, word, focusSyllableAtomIds)?.failable()
            } else {
                null
            }
            val built = syllable
                ?: letterRound(pack, wordAtom, graphemes, focusLetterAtomIds, focusCursor)?.failable()
                ?: return@forEach

            rounds += built.round
            if (built.usedFocusIndex != null) {
                focusCursor = (built.usedFocusIndex + 1) % focusLetterAtomIds.size
            }
        }
        return rounds
    }

    private data class Built(val round: SymbolInWordRound, val usedFocusIndex: Int?)

    /**
     * Derselbe Defekt in anderer Verkleidung (design doc §4): sind *alle* Segmente
     * Treffer, kann das Kind nicht danebentippen — kein Fehltipp für die
     * Adaptivität, "Zeig mir" unerreichbar, und die Produktprinzipien-Prüfzeile
     * "Kann die Aufgabe überhaupt fehlschlagen?" ist verletzt. `Mimi` -> `Mi·mi`
     * mit dem Ziel `mi` ist genau dieser Fall.
     *
     * Eine so verworfene Silben-Runde fällt in den Buchstaben-Modus, wie die
     * Block-Display-Bedingung auch — und `M·i·m·i` mit dem Ziel `I` ist zwei
     * Treffer in vier Segmenten, am Fokus-Graphem der Lektion, und fehlschlagbar.
     */
    private fun Built.failable(): Built? =
        takeIf { round.targetIndices.size < round.segments.size }

    private fun letterRound(
        pack: ContentPack,
        wordAtom: Atom,
        graphemes: List<String>,
        focusLetterAtomIds: List<String>,
        focusCursor: Int,
    ): Built? {
        if (focusLetterAtomIds.isEmpty()) return null
        // Rotate so a lesson with two focus letters practices both instead of
        // hammering the first one in every word.
        val rotated = focusLetterAtomIds.indices.map { (focusCursor + it) % focusLetterAtomIds.size }
        val focusIndex = rotated.firstOrNull { index ->
            val display = pack.atoms[focusLetterAtomIds[index]]?.display
            display != null && graphemes.any { it.equals(display, ignoreCase = true) }
        } ?: return null

        val targetAtomId = focusLetterAtomIds[focusIndex]
        val display = pack.atom(targetAtomId).display
        val hits = graphemes.indices.filter { graphemes[it].equals(display, ignoreCase = true) }
        val template = if (hits.size > 1) PromptLetterMany else PromptLetterOne
        return Built(
            SymbolInWordRound(
                promptTts = template.format(display, wordAtom.display),
                wordAtomId = wordAtom.id,
                targetAtomId = targetAtomId,
                mode = SymbolInWordMode.letter,
                segments = graphemes,
                targetIndices = hits,
            ),
            usedFocusIndex = focusIndex,
        )
    }

    private fun syllableRound(
        pack: ContentPack,
        wordAtom: Atom,
        word: WordBuildRound,
        focusSyllableAtomIds: Set<String>,
    ): Built? {
        if (word.blocks.size < MinSegments) return null
        // word_build blocks are not reliable syllables ("Hä·u·s·e·r", "Ha·l·l·o"),
        // so only blocks backed by an actual syllable atom may be hunted — asking
        // for "die Silbe l im Wort Hallo" would be nonsense. A block also only
        // qualifies when its authored display agrees with the atom's own display
        // (case-insensitively): the label shown to the child comes from the atom
        // (§2), so a block whose text disagrees with its atom — l17 "Spinne"
        // groups "Spin" under the `spi` atom — cannot be labelled honestly. Rather
        // than show "spi" above a segment that reads "Spin", the round is not
        // asked at all, exactly like a word with no syllable block falls back to
        // letter mode.
        val syllableBlocks = word.blocks.filter { block ->
            val atom = pack.atoms[block.atomId]
            atom?.kind == AtomKind.syllable && block.display.equals(atom.display, ignoreCase = true)
        }
        if (syllableBlocks.isEmpty()) return null

        val targetBlock = syllableBlocks.firstOrNull { it.atomId in focusSyllableAtomIds }
            ?: syllableBlocks.first()
        val target = pack.atom(targetBlock.atomId)
        val segments = word.blocks.map { it.display }
        // Match by atomId, not by display text: a repeated syllable can appear as
        // two differently-cased blocks of the same atom ("Mi"/"mi" in "Mimi"), and
        // atomId is the authoritative link from a block to its atom.
        val hits = word.blocks.indices.filter { word.blocks[it].atomId == targetBlock.atomId }
        val template = if (hits.size > 1) PromptSyllableMany else PromptSyllableOne
        return Built(
            SymbolInWordRound(
                promptTts = template.format(target.display, wordAtom.display),
                wordAtomId = wordAtom.id,
                targetAtomId = targetBlock.atomId,
                mode = SymbolInWordMode.syllable,
                segments = segments,
                targetIndices = hits,
            ),
            usedFocusIndex = null,
        )
    }
}
