package sanex.assistant

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

/** Saves routines as JSON in the app's private storage. No extra dependencies. */
object RoutineStore {
    private const val FILE_NAME = "routines.json"

    fun load(context: Context): List<Routine> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { Routine.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e("Assistant", "Could not read routines: ${e.message}")
            emptyList()
        }
    }

    fun save(context: Context, routines: List<Routine>) {
        try {
            val arr = JSONArray().also { a -> routines.forEach { a.put(it.toJson()) } }
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(File(context.filesDir, FILE_NAME))   // write-then-rename so a crash can't corrupt the file
        } catch (e: Exception) {
            Log.e("Assistant", "Could not save routines: ${e.message}")
        }
    }
}
