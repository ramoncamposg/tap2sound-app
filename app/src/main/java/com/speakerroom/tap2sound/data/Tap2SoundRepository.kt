package com.speakerroom.tap2sound.data

import com.speakerroom.tap2sound.network.ApiClient
import com.speakerroom.tap2sound.network.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository central: coordina las llamadas de red (ApiClient),
 * el cacheo local (Room) y la sesión de usuario (DataStore).
 */
class Tap2SoundRepository(
    private val apiClient: ApiClient,
    private val speakerDao: SpeakerDao,
    private val userPrefs: UserPreferencesRepository
) {

    // Flujo de altavoces cacheados localmente
    val cachedSpeakers: Flow<List<Speaker>> = speakerDao.getAllSpeakers()

    val jwtToken: Flow<String?> = userPrefs.jwtToken
    val userEmail: Flow<String?> = userPrefs.userEmail
    val userPassword: Flow<String?> = userPrefs.userPassword
    val isAdmin: Flow<Boolean> = userPrefs.isAdmin
    val useLightTheme: Flow<Boolean> = userPrefs.useLightTheme
    val reviewRequested: Flow<Boolean> = userPrefs.reviewRequested

    suspend fun setUseLightTheme(useLight: Boolean) = userPrefs.setUseLightTheme(useLight)

    /** Registra una conexión exitosa; devuelve true si es momento de pedir review. */
    suspend fun registerSuccessfulConnectionAndCheckReview(): Boolean {
        val count = userPrefs.incrementSuccessfulConnections()
        val alreadyRequested = userPrefs.reviewRequested.first()
        return if (!alreadyRequested && count >= 3) {
            userPrefs.markReviewRequested()
            true
        } else {
            false
        }
    }

    // ---------- AUTH ----------

    suspend fun register(email: String, password: String): Result<Unit> {
        return when (val result = apiClient.register(email, password)) {
            is ApiResult.Success -> {
                userPrefs.saveUserData(
                    result.data.user.id,
                    result.data.user.email,
                    result.data.token,
                    result.data.user.isAdmin,
                    password
                )
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return when (val result = apiClient.login(email, password)) {
            is ApiResult.Success -> {
                userPrefs.saveUserData(
                    result.data.user.id,
                    result.data.user.email,
                    result.data.token,
                    result.data.user.isAdmin,
                    password
                )
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    suspend fun logout() {
        userPrefs.clearUserData()
        speakerDao.deleteAll()
    }

    suspend fun isLoggedIn(): Boolean = userPrefs.jwtToken.first() != null

    /** Solicita el email de restablecimiento de contraseña. */
    suspend fun forgotPassword(email: String): Result<String> {
        return when (val result = apiClient.forgotPassword(email)) {
            is ApiResult.Success -> Result.success(
                result.data.message
                    ?: "If that email is registered, you'll receive reset instructions shortly."
            )
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    /** Elimina la cuenta del usuario autenticado y limpia todos los datos locales. */
    suspend fun deleteAccount(): Result<Unit> {
        val token = userPrefs.jwtToken.first()
            ?: return Result.failure(Exception("Not authenticated"))

        return when (val result = apiClient.deleteAccount(token)) {
            is ApiResult.Success -> {
                userPrefs.clearUserData()
                userPrefs.clearSavedCredentials()
                speakerDao.deleteAll()
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    /** true/false si el tag esta en la lista blanca; null si no se pudo comprobar. */
    suspend fun isTagRegistered(nfcUid: String): Boolean? {
        val token = userPrefs.jwtToken.first() ?: return null
        return when (val result = apiClient.tagStatus(token, nfcUid)) {
            is ApiResult.Success -> result.data.registered
            is ApiResult.Error -> null
        }
    }

    suspend fun hasSpeakers(): Boolean = speakerDao.count() > 0

    // ---------- AUTO-PAIR (CORE) ----------

    /**
     * Envía NFC UID + MAC Bluetooth al backend. Si tiene éxito,
     * cachea el altavoz localmente y devuelve la MAC para conectar.
     */
    suspend fun autoPair(nfcUid: String, btMac: String, name: String?): Result<Speaker> {
        val token = userPrefs.jwtToken.first()
            ?: return Result.failure(Exception("Not authenticated"))

        return when (val result = apiClient.autoPair(token, nfcUid, btMac, name)) {
            is ApiResult.Success -> {
                val s = result.data.speaker
                val speaker = Speaker(
                    id = s.id,
                    nfcUid = s.nfcUid,
                    btMac = s.btMac,
                    name = s.name
                )
                // Cachear localmente (upsert manual)
                val existing = speakerDao.getSpeakerById(speaker.id)
                if (existing == null) {
                    speakerDao.insert(speaker)
                } else {
                    speakerDao.update(speaker)
                }
                Result.success(speaker)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Busca primero en cache local (para uso diario sin red).
     * Devuelve la MAC asociada al UID si existe.
     */
    suspend fun getCachedMacByUid(nfcUid: String): String? {
        return speakerDao.getSpeakerByNfcUid(nfcUid)?.btMac
    }

    // ---------- SYNC ----------

    /**
     * Sincroniza los altavoces del servidor a la cache local.
     * Útil tras reinstalar la app.
     */
    suspend fun syncSpeakers(): Result<Unit> {
        val token = userPrefs.jwtToken.first()
            ?: return Result.failure(Exception("Not authenticated"))

        return when (val result = apiClient.getSpeakers(token)) {
            is ApiResult.Success -> {
                speakerDao.deleteAll()
                result.data.speakers.forEach { s ->
                    speakerDao.insert(
                        Speaker(
                            id = s.id,
                            nfcUid = s.nfcUid,
                            btMac = s.btMac,
                            name = s.name
                        )
                    )
                }
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    suspend fun renameSpeaker(speakerId: String, name: String): Result<Unit> {
        val token = userPrefs.jwtToken.first()
            ?: return Result.failure(Exception("Not authenticated"))

        return when (val result = apiClient.renameSpeaker(token, speakerId, name)) {
            is ApiResult.Success -> {
                speakerDao.getSpeakerById(speakerId)?.let {
                    speakerDao.update(it.copy(name = name))
                }
                Result.success(Unit)
            }
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Admin: pre-registra una etiqueta NFC. Devuelve true si ya estaba
     * registrada (idempotente).
     */
    suspend fun registerTag(nfcUid: String): Result<Boolean> {
        val token = userPrefs.jwtToken.first()
            ?: return Result.failure(Exception("Not authenticated"))

        return when (val result = apiClient.registerTag(token, nfcUid)) {
            is ApiResult.Success -> Result.success(result.data.alreadyRegistered)
            is ApiResult.Error -> Result.failure(Exception(result.message))
        }
    }
}
