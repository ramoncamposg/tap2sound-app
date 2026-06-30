package com.speakerroom.tap2sound.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Gestiona la captura de la MAC del altavoz Bluetooth conectado y la
 * conexion/reconexion via el perfil A2DP (reflexion).
 */
@SuppressLint("MissingPermission")
class BtManager(private val context: Context) {

    private val adapter: BluetoothAdapter? by lazy {
        val systemManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager
        systemManager?.adapter
    }

    @SuppressLint("MissingPermission")
    suspend fun getConnectedSpeakerMac(): String? = suspendCancellableCoroutine { cont ->
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            cont.resume(null); return@suspendCancellableCoroutine
        }
        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as BluetoothA2dp
                    try {
                        val mac = a2dp.connectedDevices.firstOrNull()?.address
                        if (cont.isActive) cont.resume(mac)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    } finally {
                        bt.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        val started = bt.getProfileProxy(context, serviceListener, BluetoothProfile.A2DP)
        if (!started && cont.isActive) cont.resume(null)
    }

    @SuppressLint("MissingPermission")
    suspend fun getActiveSpeakerMac(): String? = suspendCancellableCoroutine { cont ->
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            cont.resume(null); return@suspendCancellableCoroutine
        }
        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as BluetoothA2dp
                    try {
                        var mac: String? = null
                        try {
                            val m = BluetoothA2dp::class.java.getMethod("getActiveDevice")
                            mac = (m.invoke(a2dp) as? BluetoothDevice)?.address
                        } catch (_: Exception) {}
                        if (mac == null) mac = a2dp.connectedDevices.firstOrNull()?.address
                        if (cont.isActive) cont.resume(mac)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    } finally {
                        bt.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        val started = bt.getProfileProxy(context, serviceListener, BluetoothProfile.A2DP)
        if (!started && cont.isActive) cont.resume(null)
    }

    /**
     * Conecta al altavoz indicado: desconecta el altavoz anterior y conecta el
     * nuevo (via reflexion). Vincula el proxy A2DP en cada llamada.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToSpeaker(macAddress: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val bt = adapter
            if (bt == null || !bt.isEnabled) {
                cont.resume(false); return@suspendCancellableCoroutine
            }
            val device: BluetoothDevice = try {
                bt.getRemoteDevice(macAddress)
            } catch (e: IllegalArgumentException) {
                cont.resume(false); return@suspendCancellableCoroutine
            }
            val serviceListener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP) {
                        val a2dp = proxy as BluetoothA2dp
                        try {
                            // Desconectar el altavoz anterior (fuerza el cambio de ruta).
                            a2dp.connectedDevices.forEach { other ->
                                if (other.address != macAddress) {
                                    try {
                                        BluetoothA2dp::class.java
                                            .getMethod("disconnect", BluetoothDevice::class.java)
                                            .invoke(a2dp, other)
                                    } catch (_: Exception) {}
                                }
                            }
                            // Si ya esta conectado al destino, listo.
                            if (a2dp.connectedDevices.any { it.address == macAddress }) {
                                if (cont.isActive) cont.resume(true)
                                return
                            }
                            // Conectar el destino via reflexion.
                            val result = BluetoothA2dp::class.java
                                .getMethod("connect", BluetoothDevice::class.java)
                                .invoke(a2dp, device) as? Boolean ?: false
                            if (cont.isActive) cont.resume(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error connecting via reflection", e)
                            if (cont.isActive) cont.resume(false)
                        } finally {
                            bt.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        }
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val started = bt.getProfileProxy(context, serviceListener, BluetoothProfile.A2DP)
            if (!started && cont.isActive) cont.resume(false)
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

    companion object {
        private const val TAG = "BtManager"
    }
}
