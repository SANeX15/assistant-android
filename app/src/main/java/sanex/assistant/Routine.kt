package sanex.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One step inside a routine. */
sealed class RoutineAction {
    data class OpenApp(val packageName: String, val label: String) : RoutineAction()
    data class Speak(val text: String) : RoutineAction()
    data class Wait(val seconds: Int) : RoutineAction()

    fun toJson(): JSONObject = when (this) {
        is OpenApp -> JSONObject().put("type", "open_app").put("package", packageName).put("label", label)
        is Speak   -> JSONObject().put("type", "speak").put("text", text)
        is Wait    -> JSONObject().put("type", "wait").put("seconds", seconds)
    }

    companion object {
        fun fromJson(o: JSONObject): RoutineAction? = when (o.optString("type")) {
            "open_app" -> OpenApp(o.getString("package"), o.optString("label", o.getString("package")))
            "speak"    -> Speak(o.getString("text"))
            "wait"     -> Wait(o.optInt("seconds", 1))
            else       -> null
        }
    }
}

/**
 * A routine (or single task): spoken trigger phrases + an ordered list of actions.
 * A "task" is just a routine with one action.
 */
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggers: List<String>,
    val actions: List<RoutineAction>,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject {
        val trig = JSONArray().also { arr -> triggers.forEach { arr.put(it) } }
        val acts = JSONArray().also { arr -> actions.forEach { arr.put(it.toJson()) } }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("enabled", enabled)
            .put("triggers", trig)
            .put("actions", acts)
    }

    companion object {
        fun fromJson(o: JSONObject): Routine? = try {
            val trigArr = o.getJSONArray("triggers")
            val actArr  = o.getJSONArray("actions")
            Routine(
                id       = o.getString("id"),
                name     = o.getString("name"),
                enabled  = o.optBoolean("enabled", true),
                triggers = (0 until trigArr.length()).map { trigArr.getString(it) },
                actions  = (0 until actArr.length()).mapNotNull {
                    RoutineAction.fromJson(actArr.getJSONObject(it))
                }
            )
        } catch (e: Exception) {
            null
        }
    }
}
