package sanex.assistant

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay

data class AppInfo(val packageName: String, val label: String)

/** Apps that have a launcher icon. Needs the <queries> block in AndroidManifest.xml. */
fun loadLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Runs a routine's actions in order. Call from a coroutine on the main thread. */
class RoutineRunner(
    private val context: Context,
    private val speak: (String) -> Unit,
    private val isSpeaking: () -> Boolean,
    private val ensureUnlocked: suspend () -> Boolean = { true }
) {
    data class Result(val ok: Boolean, val message: String)

    suspend fun run(routine: Routine, onStep: (step: Int, total: Int) -> Unit = { _, _ -> }): Result {
        routine.actions.forEachIndexed { index, action ->
            onStep(index + 1, routine.actions.size)
            when (action) {
                is RoutineAction.Speak -> {
                    speak(action.text)
                    delay(400)                                   // give the engine time to start
                    var waited = 0
                    while (isSpeaking() && waited < 15_000) { delay(100); waited += 100 }
                }
                is RoutineAction.Wait -> delay(action.seconds * 1000L)
                is RoutineAction.OpenApp -> {
                    if (!ensureUnlocked()) return Result(false, "The phone is locked")
                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                        ?: return Result(false, "App not found: ${action.label}")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        return Result(false, "Could not open ${action.label}: ${e.message}")
                    }
                    delay(600)
                }
            }
        }
        return Result(true, "Done")
    }
}