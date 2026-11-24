package sanex.assistant

/** Understands spoken answers to "…ですか？" questions: はい, いいえ and their everyday variants. */
object YesNo {

    // Written the way Vosk is likely to write them. Everything is normalised the same way as heard text.
    private val YES = listOf(
        "はい", "はーい", "はいはい", "ええ", "うん", "うんうん", "そう", "そうです", "そうですね", "そうだよ",
        "おねがい", "お願い", "おねがいします", "お願いします", "おーけー", "おっけー", "オッケー", "オーケー",
        "いいよ", "いいですよ", "ひらいて", "開いて", "じっこう", "実行"
    ).map { CommandMatcher.normalize(it) }

    private val NO = listOf(
        "いいえ", "いえ", "いや", "いやだ", "ちがう", "違う", "ちがいます", "違います",
        "やめて", "やめる", "やめろ", "キャンセル", "ストップ", "だめ", "駄目", "いらない",
        "けっこう", "結構", "そうじゃない", "そうではない"
    ).map { CommandMatcher.normalize(it) }

    // "um…" is not a yes
    private val UNSURE = listOf("ええと", "えっと", "えーと").map { CommandMatcher.normalize(it) }

    /** true = yes, false = no, null = couldn't tell. */
    fun parse(text: String): Boolean? {
        val n = CommandMatcher.normalize(text)
        if (n.isEmpty() || n in UNSURE) return null
        if (NO.any { n == it || n.startsWith(it) }) return false
        if (YES.any { n == it || n.startsWith(it) }) return true
        return null
    }
}
