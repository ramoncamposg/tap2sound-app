package com.speakerroom.tap2sound.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Gestiona la captura de la MAC del altavoz Bluetooth conectado y la
 * conexion/reconexion via el perfil A2DP (reflexion).
 *
 * El proxy A2DP se abre UNA SOLA VEZ (al construir esta clase, en el bloque init) y
 * se mantiene vivo durante toda la vida del objeto, reconectandose solo si el
 * propio sistema lo desconecta. Antes cada llamada abria y cerraba su propio
 * proxy, lo que anadia un round-trip de bind/unbind del servicio A2DP en cada
 * tap NFC: en el primer tap tras abrir la app el proxy todavia estaba "frio" y
 * la conexion fallaba, obligando a un segundo toque. Con el proxy persistente
 * el primer tap ya encuentra el perfil listo.
 */
@SuppressLint("MissingPermission")
class BtManager(private val context: Context) {

    private val adapter: BluetoothAdapter? by lazy {
        val systemManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager
        systemManager?.adapter
    }

    @Volatile
    private var a2dpProxy: BluetoothA2dp? = null

    init {
        connectProxy()
    }

    /** Abre el proxy A2DP y lo deja vivo en [a2dpProxy]. Se reintenta solo si el sistema lo cierra. */
    private fun connectProxy() {
        val bt = adapter ?: return
        bt.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy = proxy as BluetoothA2dp
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy = null
                    // El sistema cerro el proxy (p. ej. se apago el Bluetooth).
                    // Reintentamos para que el siguiente tap lo encuentre listo.
                    Handler(Looper.getMainLooper()).postDelayed({ connectProxy() }, 1000)
                }
            }
        }, BluetoothProfile.A2DP)
    }

    /**
     * Devuelve el proxy A2DP ya conectado (caso normal: instantaneo). Si aun no
     * esta listo (justo tras crear el BtManager) espera al bind, con un timeout
     * de seguridad de 2s.
     */
    private suspend fun ensureProxy(bt: BluetoothAdapter): BluetoothA2dp? {
        a2dpProxy?.let { return it }
        return suspendCancellableCoroutine { cont ->
            var resumed = false
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP && !resumed) {
                        resumed = true
                        val a2dp = proxy as BluetoothA2dp
                        a2dpProxy = a2dp
                        if (cont.isActive) cont.resume(a2dp)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dpProxy = null
                }
            }
            val started = bt.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            if (!started) {
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(null)
                }
                return@suspendCancellableCoroutine
            }
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(null)
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 2000)
            cont.invokeOnCancellation { timeoutHandler.removeCallbacks(timeoutRunnable) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getConnectedSpeakerMac(): String? {
        val bt = adapter ?: return null
        if (!bt.isEnabled) return null
        val a2dp = ensureProxy(bt) ?: return null
        return try {
            a2dp.connectedDevices.firstOrNull()?.address
        } catch (e: Exception) {
            Log.e(TAG, "Error reading connected speaker", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getActiveSpeakerMac(): String? {
        val bt = adapter ?: return null
        if (!bt.isEnabled) return null
        val a2dp = ensureProxy(bt) ?: return null
        return try {
            var mac: String? = null
            try {
                val m = BluetoothA2dp::class.java.getMethod("getActiveDevice")
                mac = (m.invoke(a2dp) as? BluetoothDevice)?.address
            } catch (_: Exception) {
            }
            if (mac == null) mac = a2dp.connectedDevices.firstOrNull()?.address
            mac
        } catch (e: Exception) {
            Log.e(TAG, "Error reading active speaker", e)
            null
        }
    }

    /**
     * Conecta al altavoz indicado: desconecta el altavoz anterior y conecta el
     * nuevo (via reflexion), reutilizando el proxy A2DP persistente en lugar de
     * abrir uno nuevo para esta llamada.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToSpeaker(macAddress: String): Boolean {
        val bt = adapter ?: return false
        if (!bt.isEnabled) return false
        val device: BluetoothDevice = try {
            bt.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val a2dp = ensureProxy(bt) ?: return false
        return try {
            // Desconectar el altavoz anterior (fuerza el cambio de ruta).
            a2dp.connectedDevices.forEach { other ->
                if (other.address != macAddress) {
                    try {
                        BluetoothA2dp::class.java
                            .getMethod("disconnect", BluetoothDevice::class.java)
                            .invoke(a2dp, other)
                    } catch (_: Exception) {
                    }
                }
            }
            // Si ya esta conectado al destino, listo.
            if (a2dp.connectedDevices.any { it.address == macAddress }) {
                return true
            }
            // Conectar el destino via reflexion.
            BluetoothA2dp::class.java
                .getMethod("connect", BluetoothDevice::class.java)
                .invoke(a2dp, device) as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting via reflection", e)
            false
        }
    }

    /**
     * Nombre "amigable" del dispositivo BT (el que se ve al elegirlo en los
     * ajustes de Bluetooth del telefono), p. ej. "JBL Flip 5".
     */
    @SuppressLint("MissingPermission")
    fun getDeviceName(macAddress: String): String? {
        return try {
            adapter?.getRemoteDevice(macAddress)?.name
        } catch (e: Exception) {
            null
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Envia una orden de PLAY a la app de musica activa para que la
     * reproduccion continue tras cambiar de altavoz.
     */
    fun resumePlayback() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
            am.dispatchMediaKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * Libera el proxy A2DP. Llamala cuando este BtManager ya no se vaya a usar
     * mas (p. ej. desde onCleared() del ViewModel que lo posee).
     */
    fun release() {
        val bt = adapter
        val proxy = a2dpProxy
        if (bt != null && proxy != null) {
            bt.closeProfileProxy(BluetoothProfile.A2DP, proxy)
        }
        a2dpProxy = null
    }

    companion object {
        private const val TAG = "BtManager"
    }
}
