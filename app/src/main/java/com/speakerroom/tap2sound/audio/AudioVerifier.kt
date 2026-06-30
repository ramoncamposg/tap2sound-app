package com.speakerroom.tap2sound.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.speakerroom.tap2sound.R

/**
 * Reproduce el sonido de verificacion desde res/raw/bosco.mp3, que contiene
 * la melodia de El Bosco seguida del mensaje hablado en espanol e ingles
 * ("Este es el altavoz Bluetooth seleccionado? / Is this the selected
 * Bluetooth speaker?").
 *
 * Se reproduce por el canal multimedia (USAGE_MEDIA), que se enruta al
 * altavoz Bluetooth A2DP activo. El usuario confirma con los botones Si/No
 * si lo ha oido en el altavoz correcto antes de fijar la asociacion NFC+MAC.
 *
 * Para cambiar el sonido basta con sustituir el fichero res/raw/bosco.mp3.
 */
class AudioVerifier(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    private fun mediaAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** Reproduce desde el principio la melodia + el mensaje de verificacion. */
    fun playVerification() {
        stop()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(mediaAttributes())
            val afd = appContext.resources.openRawResourceFd(R.raw.bosco) ?: return
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
            afd.close()
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            mp.setOnErrorListener { p, _, _ ->
                p.release()
                if (player === p) player = null
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Detiene el sonido en curso. */
    fun stop() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
    }

    /** Libera recursos (al destruir el ViewModel). */
    fun release() {
        stop()
    }
}
