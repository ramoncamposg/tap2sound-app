package com.speakerroom.tap2sound.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "tap2sound_prefs")

object PreferencesKeys {
    val JWT_TOKEN = stringPreferencesKey("jwt_token")
    val USER_ID = stringPreferencesKey("user_id")
    val USER_EMAIL = stringPreferencesKey("user_email")
    val USER_PASSWORD = stringPreferencesKey("user_password")
    val IS_ADMIN = booleanPreferencesKey("is_admin")
    val USE_LIGHT_THEME = booleanPreferencesKey("use_light_theme")
    val SUCCESSFUL_CONNECTIONS = androidx.datastore.preferences.core.intPreferencesKey("successful_connections")
    val REVIEW_REQUESTED = booleanPreferencesKey("review_requested")
}

class UserPreferencesRepository(private val context: Context) {
    val jwtToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.JWT_TOKEN]
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_ID]
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_EMAIL]
    }

    val userPassword: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_PASSWORD]
    }

    val isAdmin: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_ADMIN] ?: false
    }

    // La app usa el tema oscuro/dorado de marca por defecto; el usuario puede
    // cambiar a un tema claro desde Ajustes si lo prefiere.
    val useLightTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_LIGHT_THEME] ?: false
    }

    val successfulConnections: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SUCCESSFUL_CONNECTIONS] ?: 0
    }

    val reviewRequested: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.REVIEW_REQUESTED] ?: false
    }

    suspend fun setUseLightTheme(useLight: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_LIGHT_THEME] = useLight
        }
    }

    /** Incrementa el contador de conexiones exitosas y devuelve el nuevo valor. */
    suspend fun incrementSuccessfulConnections(): Int {
        var newValue = 0
        context.dataStore.edit { preferences ->
            newValue = (preferences[PreferencesKeys.SUCCESSFUL_CONNECTIONS] ?: 0) + 1
            preferences[PreferencesKeys.SUCCESSFUL_CONNECTIONS] = newValue
        }
        return newValue
    }

    suspend fun markReviewRequested() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REVIEW_REQUESTED] = true
        }
    }

    suspend fun saveUserData(
        userId: String,
        email: String,
        token: String,
        isAdmin: Boolean,
        password: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = userId
            preferences[PreferencesKeys.USER_EMAIL] = email
            preferences[PreferencesKeys.JWT_TOKEN] = token
            preferences[PreferencesKeys.IS_ADMIN] = isAdmin
            preferences[PreferencesKeys.USER_PASSWORD] = password
        }
    }

    suspend fun clearUserData() {
        // Cerrar sesion: borrar token/sesion pero conservar email y
        // password para prerellenar el login la proxima vez.
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.JWT_TOKEN)
            preferences.remove(PreferencesKeys.USER_ID)
            preferences.remove(PreferencesKeys.IS_ADMIN)
        }
    }

    /** Borra también el email/password recordados (p. ej. tras eliminar la cuenta). */
    suspend fun clearSavedCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_EMAIL)
            preferences.remove(PreferencesKeys.USER_PASSWORD)
            preferences.remove(PreferencesKeys.SUCCESSFUL_CONNECTIONS)
            preferences.remove(PreferencesKeys.REVIEW_REQUESTED)
        }
    }
}
