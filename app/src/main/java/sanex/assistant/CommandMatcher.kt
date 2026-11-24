package sanex.assistant

import java.text.Normalizer
import kotlin.math.max
import kotlin.text.iterator

data class MatchResult(val routine: Routine, val trigger: String, val score: Double)

/**
 * Matches what the recognizer heard against routine trigger phrases.
 * Runs in a few milliseconds; no model needed.
 *
 * Triggers are recorded by voice, so they are stored in the same spelling the
 * recognizer produces. That avoids needing kanji readings for matching.
 */
object CommandMatcher {

    /** At or above this score, the routine runs immediately. */
    const val RUN_THRESHOLD = 0.80

    /** Between this and RUN_THRESHOLD, show "did you mean…" buttons. */
    const val SUGGEST_THRESHOLD = 0.55

    /** Remove spaces and punctuation, unify width, and turn katakana into hiragana. */
    fun normalize(s: String): String {
        val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder()
        for (ch in nfkc) {
            if (!ch.isLetterOrDigit()) continue          // drops spaces, 、。！？ etc.
            sb.append(if (ch in '\u30A1'..'\u30F6') (ch.code - 0x60).toChar() else ch)
        }
        return sb.toString()
    }

    /** Spoken forms of "open" that can end a command: 開いて, 開けて, ひらいて, 起動して ... */
    val OPEN_VERB = Regex(
        "(開いてください|ひらいてください|開いてくれ|ひらいてくれ|開いて|ひらいて|開けて|あけて|" +
            "開きます|ひらきます|開く|ひらく|開け|ひらけ|起動して|きどうして|起動|立ち上げて|たちあげて)$"
    )

    /** Treat all of those endings as one word, so "カメラを開けて" matches "カメラを開いて". */
    private fun canonical(s: String): String = s.replace(OPEN_VERB, "<open>")

    /** 1.0 = identical, 0.0 = nothing in common (edit distance relative to the longer string). */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
    }

    fun score(utterance: String, trigger: String): Double {
        val u = canonical(normalize(utterance))
        val t = canonical(normalize(trigger))
        if (u.isEmpty() || t.isEmpty()) return 0.0
        if (u == t) return 1.0

        var best = similarity(u, t)

        // "えっとカメラを開いて" still matches the trigger "カメラを開いて"
        if (t.length >= 3 && u.contains(t)) {
            best = max(best, 0.8 + 0.2 * t.length / u.length)
        }
        return best
    }

    /** Best-first list of enabled routines scoring at least SUGGEST_THRESHOLD. */
    fun match(utterance: String, routines: List<Routine>): List<MatchResult> =
        routines
            .filter { it.enabled }
            .mapNotNull { r ->
                r.triggers
                    .map { it to score(utterance, it) }
                    .maxByOrNull { it.second }
                    ?.let { (trigger, s) -> MatchResult(r, trigger, s) }
            }
            .filter { it.score >= SUGGEST_THRESHOLD }
            .sortedByDescending { it.score }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
