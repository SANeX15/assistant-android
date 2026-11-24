package sanex.assistant

import dev.esnault.wanakana.core.Wanakana

/**
 * Kana and romaji helpers built on wanakana-kt.
 *
 * WanaKana converts between hiragana, katakana and romaji and can detect kanji,
 * but it does not read kanji: kanji pass through unchanged (開いて becomes "開ite").
 */
object RomajiConverter {

    /** Romaji for display. Kana become Hepburn romaji; kanji stay as they are. */
    fun convert(input: String): String {
        if (input.isBlank()) return ""
        return Wanakana.toRomaji(input).replace(Regex("\\s+"), " ").trim()
    }

    fun toKatakana(input: String): String = Wanakana.toKatakana(input)

    fun toHiragana(input: String): String = Wanakana.toHiragana(input)

    fun hasKanji(text: String): Boolean = text.any { Wanakana.isKanji(it) }

    fun isAscii(text: String): Boolean = text.all { it.code < 128 }

    /**
     * A loose "how it sounds" key, so that typed "yu-chu-bu" and heard ユーチューブ compare equal.
     * Long vowels are collapsed and punctuation is dropped.
     * Returns null when the text contains kanji, because a reading would be needed.
     */
    fun phoneticKey(text: String): String? {
        if (text.isBlank() || hasKanji(text)) return null
        // Going through hiragana first makes "tu"/"tsu" style spelling differences disappear.
        val romaji = Wanakana.toRomaji(Wanakana.toHiragana(text))
        val key = collapse(romaji)
        return key.ifEmpty { null }
    }

    private fun collapse(romaji: String): String {
        var s = romaji.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        for ((from, to) in LONG_VOWELS) s = s.replace(from, to)
        return s
    }

    private val LONG_VOWELS = listOf(
        "ou" to "o", "oo" to "o", "uu" to "u", "aa" to "a", "ii" to "i", "ee" to "e", "ei" to "e"
    )
}

/** Short alias used by the screens. */
fun toRomaji(text: String): String = RomajiConverter.convert(text)
