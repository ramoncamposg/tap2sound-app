package com.speakerroom.tap2sound.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.util.Log

/**
 * Helper para operaciones NFC: leer UID de tags y escribir registros NDEF.
 *
 * El registro NDEF escrito contiene:
 *  1. Un AAR (Android Application Record) que abre la app si está instalada.
 *  2. Un registro URI que redirige a Play Store si NO está instalada.
 */
object NfcHelper {

    private const val TAG = "NfcHelper"
    const val PACKAGE_NAME = "com.speakerroom.tap2sound"
    const val MIME_TYPE = "application/com.speakerroom.tap2sound"
    private const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"

    /**
     * Extrae el UID único del tag NFC en formato hexadecimal.
     */
    fun getTagUid(tag: Tag): String {
        return tag.id.joinToString("") { "%02X".format(it) }
    }

    /**
     * Habilita el foreground dispatch para capturar tags mientras
     * la app está en primer plano.
     */
    fun enableForegroundDispatch(activity: Activity, adapter: NfcAdapter?) {
        adapter ?: return
        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)
        try {
            adapter.enableForegroundDispatch(activity, pendingIntent, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling foreground dispatch", e)
        }
    }

    fun disableForegroundDispatch(activity: Activity, adapter: NfcAdapter?) {
        adapter ?: return
        try {
            adapter.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling foreground dispatch", e)
        }
    }

    /**
     * Escribe en el tag un mensaje NDEF que:
     *  - Lanza la app vía AAR si está instalada.
     *  - Incluye el UID como parámetro embebido en un registro de mime propio.
     *
     * @return true si la escritura fue exitosa.
     */
    fun writeNdefToTag(tag: Tag, nfcUid: String): Boolean {
        val message = buildNdefMessage(nfcUid)

        // Intentar con Ndef (tag ya formateado)
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    Log.e(TAG, "Tag is not writable")
                    return false
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    Log.e(TAG, "Message too large for tag")
                    return false
                }
                ndef.writeNdefMessage(message)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing NDEF", e)
                false
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        // Intentar formatear si el tag no estaba formateado
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error formatting tag", e)
                false
            } finally {
                try {
                    formatable.close()
                } catch (_: Exception) {
                }
            }
        }

        Log.e(TAG, "Tag does not support NDEF")
        return false
    }

    /**
     * Construye el mensaje NDEF:
     *  - Registro MIME propio con el UID (lo lee la app).
     *  - Registro URI a Play Store (fallback si no hay app).
     *  - AAR para forzar la apertura de la app.
     */
    private fun buildNdefMessage(nfcUid: String): NdefMessage {
        val mimeRecord = NdefRecord.createMime(
            MIME_TYPE,
            nfcUid.toByteArray(Charsets.UTF_8)
        )


        val aarRecord = NdefRecord.createApplicationRecord(PACKAGE_NAME)

        return NdefMessage(arrayOf(mimeRecord, aarRecord))
    }

    /**
     * Extrae el UID embebido en un mensaje NDEF recibido (registro MIME).
     * Si no hay registro MIME, devuelve null para que se use el UID del tag.
     */
    fun extractUidFromNdefMessage(messages: Array<NdefMessage>?): String? {
        messages ?: return null
        for (message in messages) {
            for (record in message.records) {
                if (record.toMimeType() == MIME_TYPE) {
                    return String(record.payload, Charsets.UTF_8)
                }
            }
        }
        return null
    }
}
