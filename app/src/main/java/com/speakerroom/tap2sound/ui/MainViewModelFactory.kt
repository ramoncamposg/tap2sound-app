package com.speakerroom.tap2sound.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.speakerroom.tap2sound.audio.AudioVerifier
import com.speakerroom.tap2sound.bluetooth.BtManager
import com.speakerroom.tap2sound.data.Tap2SoundDatabase
import com.speakerroom.tap2sound.data.Tap2SoundRepository
import com.speakerroom.tap2sound.data.UserPreferencesRepository
import com.speakerroom.tap2sound.network.ApiClient

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = Tap2SoundDatabase.getInstance(context)
        val apiClient = ApiClient(ApiClient.BASE_URL)
        val userPrefs = UserPreferencesRepository(context)
        val repository = Tap2SoundRepository(
            apiClient = apiClient,
            speakerDao = database.speakerDao(),
            userPrefs = userPrefs
        )
        val btManager = BtManager(context)
        val audioVerifier = AudioVerifier(context)

        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, btManager, audioVerifier) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
