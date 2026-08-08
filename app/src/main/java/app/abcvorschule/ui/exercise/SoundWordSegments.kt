package app.abcvorschule.ui.exercise

/** Small German grapheme groups make the written picture-word match the train's three colours. */
object SoundWordSegments {
    private val multiLetterGraphemes =
        listOf("sch", "ch", "ei", "ie", "eu", "au", "äu", "ck", "tz", "ng", "sp", "st", "pf", "qu")

    fun split(word: String): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < word.length) {
            val next = multiLetterGraphemes.firstOrNull { grapheme ->
                word.regionMatches(index, grapheme, 0, grapheme.length, ignoreCase = true)
            }
            if (next == null) {
                result += word[index].toString()
                index += 1
            } else {
                result += word.substring(index, index + next.length)
                index += next.length
            }
        }
        return result
    }
}
