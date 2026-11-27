package sanex.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

enum class MicChoice(val label: String) {
    AUTO("自動 / Auto: wired, then Bluetooth, then phone"),
    PHONE("本体マイク / Phone mic"),
    WIRED("有線ヘッドセット / Wired headset"),
    BLUETOOTH("Bluetooth ヘッドセット / Bluetooth headset");

    companion object {
        fun from(name: String?): MicChoice = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Where the audio will come from for one listening session. */
class MicRoute(
    val label: String,
    val inputDevice: AudioDeviceInfo?,        // null = let Android choose
    val bluetoothDevice: AudioDeviceInfo?,    // set when a Bluetooth call link must be opened first
    val audioSource: Int
)

/**
 * Chooses the microphone: the phone's own, a wired headset, or a Bluetooth headset.
 *
 * Wired headsets and USB headsets are selected directly.
 * Bluetooth headsets need a "call" audio link (SCO), which Android 12+ opens with setCommunicationDevice.
 * That link has lower sound quality than normal Bluetooth music, so it is only opened while listening
 * and released when the assistant closes.
 */
class AudioRoute(private val context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var linkOpen = false

    val isBluetoothLinkOpen: Boolean get() = linkOpen

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun inputs(): List<AudioDeviceInfo> = am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun wiredMic(): AudioDeviceInfo? = inputs().firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    private fun phoneMic(): AudioDeviceInfo? = inputs().firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    private fun isBluetoothType(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == TYPE_BLE_HEADSET

    /** A connected Bluetooth headset that can carry a microphone. Needs Android 12+ and the Nearby devices permission. */
    private fun bluetoothHeadset(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !hasBluetoothPermission()) return null
        return am.availableCommunicationDevices.firstOrNull { isBluetoothType(it.type) }
    }

    /** The Bluetooth input once the call link is up. */
    fun findBluetoothInput(): AudioDeviceInfo? = inputs().firstOrNull { isBluetoothType(it.type) }

    fun resolve(choice: MicChoice): MicRoute {
        val wired = wiredMic()
        val bluetooth = bluetoothHeadset()
        return when (choice) {
            MicChoice.PHONE -> phoneRoute()
            MicChoice.WIRED ->
                if (wired != null) wiredRoute(wired)
                else phoneRoute("有線が見つかりません: 本体マイク / Wired not found: phone mic")
            MicChoice.BLUETOOTH ->
                if (bluetooth != null) bluetoothRoute(bluetooth)
                else phoneRoute("Bluetooth が見つかりません: 本体マイク / Bluetooth not found: phone mic")
            MicChoice.AUTO -> when {
                wired != null -> wiredRoute(wired)
                bluetooth != null -> bluetoothRoute(bluetooth)
                else -> phoneRoute()
            }
        }
    }

    private fun phoneRoute(label: String = "本体マイク / Phone mic") =
        MicRoute(label, phoneMic(), null, MediaRecorder.AudioSource.VOICE_RECOGNITION)

    private fun wiredRoute(d: AudioDeviceInfo) =
        MicRoute("有線 / Wired: ${d.productName}", d, null, MediaRecorder.AudioSource.VOICE_RECOGNITION)

    private fun bluetoothRoute(d: AudioDeviceInfo) =
        MicRoute("Bluetooth: ${d.productName}", null, d, MediaRecorder.AudioSource.VOICE_COMMUNICATION)

    /** Opens the Bluetooth call link for this route. Returns false if it could not be opened. */
    fun acquire(route: MicRoute): Boolean {
        val device = route.bluetoothDevice ?: return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val ok = am.setCommunicationDevice(device)
            linkOpen = ok
            Log.d("Assistant", "setCommunicationDevice(${device.productName}) = $ok")
            ok
        } catch (e: Exception) {
            Log.w("Assistant", "Could not open the Bluetooth link: ${e.message}")
            false
        }
    }

    /** Closes the Bluetooth call link so music and speech go back to normal quality. */
    fun release() {
        if (linkOpen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                am.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.w("Assistant", "Could not close the Bluetooth link: ${e.message}")
            }
        }
        linkOpen = false
    }

    /** A short list of what is connected, for the settings screen. */
    fun describeDevices(): String {
        val wired = wiredMic()
        val bluetoothLine = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "Bluetooth: needs Android 12 or newer"
            !hasBluetoothPermission() -> "Bluetooth: permission needed (open the app to allow it)"
            else -> "Bluetooth: ${bluetoothHeadset()?.productName ?: "not connected"}"
        }
        return "有線 / Wired: ${wired?.productName ?: "not connected"}\n$bluetoothLine\n本体 / Phone mic: available"
    }

    private companion object {
        const val TYPE_BLE_HEADSET = 26      // AudioDeviceInfo.TYPE_BLE_HEADSET, added in Android 12
    }
}
