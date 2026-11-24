package sanex.assistant

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** An installed app, the spoken name that matched it, and how well it matched (0..1). */
data class AppMatch(val app: AppInfo, val alias: String, val score: Double)

/**
 * Spoken names ("aliases") for installed apps, saved as JSON.
 * An alias is either romaji you typed ("yu-chu-bu") or text the recognizer heard when you said the name.
 */
object AppAliasStore {
    private const val FILE_NAME = "app_aliases.json"

    fun load(context: Context): Map<String, List<String>> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyMap()
        return try {
            val obj = JSONObject(file.readText())
            val result = mutableMapOf<String, List<String>>()
            for (pkg in obj.keys()) {
                val arr = obj.getJSONArray(pkg)
                result[pkg] = (0 until arr.length()).map { arr.getString(it) }
            }
            result
        } catch (e: Exception) {
            Log.e("Assistant", "Could not read app names: ${e.message}")
            emptyMap()
        }
    }

    fun save(context: Context, aliases: Map<String, List<String>>) {
        try {
            val obj = JSONObject()
            for ((pkg, list) in aliases) {
                obj.put(pkg, JSONArray().also { arr -> list.forEach { arr.put(it) } })
            }
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(obj.toString())
            tmp.renameTo(File(context.filesDir, FILE_NAME))
        } catch (e: Exception) {
            Log.e("Assistant", "Could not save app names: ${e.message}")
        }
    }
}

/**
 * Starting names for popular apps, typed the way you would type romaji (hyphen = long vowel).
 * These are the katakana loanwords the recognizer writes in kana. Native Japanese names
 * (設定, 電話, 時計...) come out as kanji, so record those by voice in the app-names screen.
 */
object DefaultAppNames {
    private val table: List<Pair<String, List<String>>> = listOf(
        "camera" to listOf("kamera"),
        "youtube" to listOf("yu-chu-bu"),
        "chrome" to listOf("ku-ro-mu"),
        "gmail" to listOf("ji-me-ru"),
        "maps" to listOf("mappu"),
        "photos" to listOf("fo-to"),
        "gallery" to listOf("gyarari-"),
        "calendar" to listOf("karenda-"),
        "clock" to listOf("kurokku"),
        "files" to listOf("fairu"),
        "drive" to listOf("doraibu"),
        "music" to listOf("myu-jikku"),
        "play store" to listOf("purei suto-"),
        "whatsapp" to listOf("wattsuappu"),
        "instagram" to listOf("insutaguramu"),
        "facebook" to listOf("feisubukku"),
        "messenger" to listOf("messenja-"),
        "telegram" to listOf("tereguramu"),
        "spotify" to listOf("supotifai"),
        "netflix" to listOf("nettofurikkusu"),
        "zoom" to listOf("zu-mu")
    )

    fun forLabel(label: String): List<String> {
        val l = label.lowercase()
        return table.firstOrNull { l.contains(it.first) }?.second ?: emptyList()
    }
}

/** Understands "<app name> を開いて" and finds the installed app it refers to. */
object AppCommandMatcher {

    /** At or above this the app opens immediately; below it the app is only offered as a guess. */
    const val RUN_THRESHOLD = 0.80
    private const val GUESS_THRESHOLD = 0.55

    /** "ユーチューブを開いて" gives "ゆーちゅーぶ" (normalised); null if the sentence isn't an open command. */
    fun extractName(utterance: String): String? {
        val n = CommandMatcher.normalize(utterance)
        val m = CommandMatcher.OPEN_VERB.find(n) ?: return null
        val name = n.substring(0, m.range.first).removeSuffix("を")
        return name.ifEmpty { null }
    }

    fun match(
        utterance: String,
        apps: List<AppInfo>,
        aliasesFor: (AppInfo) -> List<String>
    ): AppMatch? {
        val name = extractName(utterance) ?: return null
        var best: AppMatch? = null
        for (app in apps) {
            for (alias in aliasesFor(app)) {
                val s = nameScore(name, alias)
                val current = best
                if (current == null || s > current.score) best = AppMatch(app, alias, s)
            }
        }
        return best?.takeIf { it.score >= GUESS_THRESHOLD }
    }

    private fun nameScore(name: String, alias: String): Double {
        // Spelling: works for aliases recorded by voice (kana or kanji).
        var best = CommandMatcher.score(name, alias)
        // Sound: lets typed "yu-chu-bu" match the heard ユーチューブ.
        val a = RomajiConverter.phoneticKey(name)
        val b = RomajiConverter.phoneticKey(alias)
        if (a != null && b != null) best = max(best, CommandMatcher.similarity(a, b))
        return best
    }
}
