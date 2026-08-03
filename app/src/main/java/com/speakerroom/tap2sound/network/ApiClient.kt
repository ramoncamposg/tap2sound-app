package com.speakerroom.tap2sound.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Resultado genérico de las llamadas a la API.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

class ApiClient(private val baseUrl: String) {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ---------- AUTH ----------

    suspend fun register(email: String, password: String): ApiResult<AuthResponse> =
        post("/auth/register", RegisterRequest(email, password), AuthResponse::class.java)

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
        post("/auth/login", LoginRequest(email, password), AuthResponse::class.java)

    /** Solicita el envío de un enlace/código de restablecimiento de contraseña por email. */
    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> =
        post("/auth/forgot-password", ForgotPasswordRequest(email), MessageResponse::class.java)

    /** Elimina la cuenta del usuario autenticado (irreversible). */
    suspend fun deleteAccount(token: String): ApiResult<DeleteAccountResponse> =
        delete("/auth/account", DeleteAccountResponse::class.java, token)

    // ---------- SPEAKERS ----------

    /**
     * CORE: Envía el NFC UID + MAC Bluetooth al backend para
     * crear/vincular el altavoz al usuario autenticado.
     */
    suspend fun autoPair(
        token: String,
        nfcUid: String,
        btMac: String,
        name: String?
    ): ApiResult<AutoPairResponse> =
        post(
            "/speakers/auto-pair",
            AutoPairRequest(nfcUid, btMac, name),
            AutoPairResponse::class.java,
            token
        )

    suspend fun getSpeakers(token: String): ApiResult<GetSpeakersResponse> =
        get("/speakers", GetSpeakersResponse::class.java, token)

    suspend fun tagStatus(token: String, nfcUid: String): ApiResult<TagStatusResponse> =
        get("/speakers/tag-status/$nfcUid", TagStatusResponse::class.java, token)

    suspend fun renameSpeaker(
        token: String,
        speakerId: String,
        name: String
    ): ApiResult<RenameSpeakerResponse> =
        put(
            "/speakers/rename",
            RenameSpeakerRequest(speakerId, name),
            RenameSpeakerResponse::class.java,
            token
        )

    /** Admin: pre-registra una etiqueta NFC en la lista blanca del backend. */
    suspend fun registerTag(
        token: String,
        nfcUid: String
    ): ApiResult<RegisterTagResponse> =
        post(
            "/admin/api/register-tag",
            RegisterTagRequest(nfcUid),
            RegisterTagResponse::class.java,
            token
        )

    // ---------- HTTP HELPERS ----------

    private suspend fun <T> post(
        path: String,
        body: Any,
        responseClass: Class<T>,
        token: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(body)
            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .post(json.toRequestBody(jsonMediaType))
            token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

            client.newCall(requestBuilder.build()).execute().use { response ->
                parseResponse(response, responseClass)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private suspend fun <T> put(
        path: String,
        body: Any,
        responseClass: Class<T>,
        token: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(body)
            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .put(json.toRequestBody(jsonMediaType))
            token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

            client.newCall(requestBuilder.build()).execute().use { response ->
                parseResponse(response, responseClass)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private suspend fun <T> delete(
        path: String,
        responseClass: Class<T>,
        token: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .delete()
            token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

            client.newCall(requestBuilder.build()).execute().use { response ->
                parseResponse(response, responseClass)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private suspend fun <T> get(
        path: String,
        responseClass: Class<T>,
        token: String? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$baseUrl$path")
                .get()
            token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

            client.newCall(requestBuilder.build()).execute().use { response ->
                parseResponse(response, responseClass)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun <T> parseResponse(
        response: okhttp3.Response,
        responseClass: Class<T>
    ): ApiResult<T> {
        val responseBody = response.body?.string() ?: ""
        return if (response.isSuccessful) {
            try {
                // Algunos endpoints (p. ej. DELETE) pueden devolver un cuerpo vacío
                // en caso de éxito; usamos "{}" para que Gson use los valores por
                // defecto del data class en vez de fallar al parsear.
                val bodyToParse = responseBody.ifBlank { "{}" }
                ApiResult.Success(gson.fromJson(bodyToParse, responseClass))
            } catch (e: Exception) {
                ApiResult.Error("Failed to parse response: ${e.message}")
            }
        } else {
            val errorMsg = try {
                gson.fromJson(responseBody, ErrorResponse::class.java).error
            } catch (e: Exception) {
                "Request failed (${response.code})"
            }
            ApiResult.Error(errorMsg)
        }
    }

    companion object {
        // Backend Prisma desplegado en Railway con dominio personalizado.
        const val BASE_URL = "https://api.tap2sound.com"
    }
}
