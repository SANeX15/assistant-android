package sanex.assistant

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Manages Vosk model downloads and storage.
 * Models are stored in filesDir/models/<model-name>/
 */
object ModelManager {

    data class VoskModel(
        val id: String,           // used as folder name
        val displayName: String,
        val description: String,
        val sizeMb: Int,
        val url: String,
        val accurate: Boolean     // true = large/accurate, false = small/fast
    )

    val availableModels = listOf(
        VoskModel(
            id = "vosk-model-small-ja-0.22",
            displayName = "Small (速い / Hayai)",
            description = "50 MB · Fast, lower accuracy\n速くて軽い、精度は低め",
            sizeMb = 50,
            url = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            accurate = false
        ),
        VoskModel(
            id = "vosk-model-ja-0.22",
            displayName = "Large (正確 / Seikaku)",
            description = "1 GB · Slower download, much better accuracy\n精度が高い、ダウンロードに時間がかかる",
            sizeMb = 1000,
            url = "https://alphacephei.com/vosk/models/vosk-model-ja-0.22.zip",
            accurate = true
        )
    )

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").also { it.mkdirs() }

    fun modelDir(context: Context, modelId: String): File =
        File(modelsDir(context), modelId)

    /** Returns true if the model folder exists and contains the uuid file */
    fun isModelReady(context: Context, modelId: String): Boolean {
        val dir = modelDir(context, modelId)
        return dir.exists() && File(dir, "uuid").exists()
    }

    /** Returns the first ready model id, or null if none downloaded */
    fun firstReadyModel(context: Context): String? =
        availableModels.firstOrNull { isModelReady(context, it.id) }?.id

    /** Delete a downloaded model */
    fun deleteModel(context: Context, modelId: String) {
        modelDir(context, modelId).deleteRecursively()
    }

    /**
     * Download and unzip a model.
     * Calls onProgress(0..100), onProgress(-2) while extracting, onComplete, or onError.
     * Must be called from a coroutine.
     */
    suspend fun downloadModel(
        context: Context,
        model: VoskModel,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val partFile = File(context.cacheDir, "${model.id}.zip.part")
        val zipFile  = File(context.cacheDir, "${model.id}.zip")
        val destDir  = modelDir(context, model.id)
        var connection: HttpURLConnection? = null

        try {
            if (partFile.exists()) partFile.delete()
            if (zipFile.exists()) zipFile.delete()

            // ── 1. Download to .part file ────────────────────────────────
            Log.d("Assistant", "Downloading ${model.url}")
            connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 60_000
                connect()
            }

            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastReportedProgress = -1

            partFile.outputStream().buffered(65536).use { out ->
                connection.inputStream.buffered(65536).use { input ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastReportedProgress) {
                                lastReportedProgress = progress
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                    out.flush()
                }
            }

            if (totalBytes > 0 && downloadedBytes != totalBytes) {
                throw IOException("Incomplete download: $downloadedBytes / $totalBytes bytes")
            }
            if (!partFile.exists() || partFile.length() == 0L) {
                throw IOException("Downloaded file is empty")
            }
            if (!partFile.renameTo(zipFile)) {
                throw IOException("Failed to finalize zip file")
            }

            Log.d("Assistant", "Download complete: ${zipFile.length()} bytes")

            // ── 2. Unzip ─────────────────────────────────────────────────
            withContext(Dispatchers.Main) { onProgress(-2) } // matches isExtracting == (progress == -2)
            destDir.deleteRecursively()
            destDir.mkdirs()
            unzip(zipFile, destDir, model.id)

            // ── 3. Generate uuid if missing ──────────────────────────────
            val uuidFile = File(destDir, "uuid")
            if (!uuidFile.exists()) {
                uuidFile.writeText(UUID.randomUUID().toString())
            }

            zipFile.delete()
            Log.d("Assistant", "Model ready at ${destDir.absolutePath}")
            withContext(Dispatchers.Main) { onComplete() }

        } catch (e: Exception) {
            Log.e("Assistant", "Download failed: ${e.message}", e)
            partFile.delete()
            zipFile.delete()
            destDir.deleteRecursively()
            withContext(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Unzip a file into destDir.
     * Vosk zips contain a top-level folder matching the model id;
     * we strip that so the contents land directly in destDir.
     */
    private fun unzip(zipFile: File, destDir: File, modelId: String) {
        if (!zipFile.exists() || zipFile.length() == 0L) {
            throw IOException("Zip file missing or empty: ${zipFile.absolutePath}")
        }

        val canonicalDest = destDir.canonicalPath
        val prefixWithSep = canonicalDest + File.separator

        ZipInputStream(zipFile.inputStream().buffered(65536)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val relativePath = entry.name
                    .removePrefix("$modelId/")
                    .removePrefix("/")

                if (relativePath.isEmpty()) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                val outFile = File(destDir, relativePath)
                if (!outFile.canonicalPath.startsWith(prefixWithSep)) {
                    throw SecurityException("Blocked zip entry outside destination: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().buffered(65536).use { out ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}