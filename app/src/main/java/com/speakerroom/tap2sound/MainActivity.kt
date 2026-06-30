package com.speakerroom.tap2sound

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.speakerroom.tap2sound.ui.TapState
import kotlinx.coroutines.delay
import com.speakerroom.tap2sound.nfc.NfcHelper
import com.speakerroom.tap2sound.ui.AuthScreen
import com.speakerroom.tap2sound.ui.OnboardingScreen
import com.speakerroom.tap2sound.ui.AdminScreen
import com.speakerroom.tap2sound.ui.AuthState
import com.speakerroom.tap2sound.ui.MainViewModel
import com.speakerroom.tap2sound.ui.MainViewModelFactory
import com.speakerroom.tap2sound.ui.SpeakersScreen
import com.speakerroom.tap2sound.ui.Tap2SoundTheme

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    // Launcher para solicitar permisos Bluetooth en runtime
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* el usuario decide; el flujo se valida en tiempo de uso */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        requestBluetoothPermissions()

        setContent {
            Tap2SoundTheme {
                AppRoot(viewModel, onMinimize = { moveTaskToBack(true) })
            }
        }

        // Procesar intent NFC si la app se lanzó al tocar un tag
        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        NfcHelper.enableForegroundDispatch(this, nfcAdapter)
    }

    override fun onPause() {
        super.onPause()
        NfcHelper.disableForegroundDispatch(this, nfcAdapter)
    }

    /**
     * Extrae el UID del intent NFC y lo entrega al companion object,
     * que el Composable observa.
     */
    private fun handleNfcIntent(intent: Intent?) {
        intent ?: return
        val action = intent.action
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            tag?.let {
                viewModel.onNfcTag(it)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel, onMinimize: () -> Unit) {
    val authState by viewModel.authState.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val savedEmail by viewModel.userEmail.collectAsState()
    val savedPassword by viewModel.savedPassword.collectAsState()
    val tapState by viewModel.tapState.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()

    // Tras un tap ya emparejado: mostrar unos segundos y minimizar (como antes).
    LaunchedEffect(Unit) {
        viewModel.minimizeEvent.collect {
            delay(1200)
            onMinimize()
        }
    }
    val speakers by viewModel.speakers.collectAsState()
    val onboardingActive by viewModel.onboardingActive.collectAsState()
    val btConnected by viewModel.btConnected.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val adminScreenVisible by viewModel.adminScreenVisible.collectAsState()
    val writeMode by viewModel.writeMode.collectAsState()
    val writeState by viewModel.writeState.collectAsState()
    val tagsWritten by viewModel.tagsWritten.collectAsState()

    when (authState) {
        is AuthState.Loading,
        is AuthState.LoggedOut -> {
            AuthScreen(
                isLoading = authState is AuthState.Loading,
                errorMessage = authError,
                initialEmail = savedEmail ?: "",
                initialPassword = savedPassword ?: "",
                onLogin = viewModel::login,
                onRegister = viewModel::register
            )
        }
        is AuthState.LoggedIn -> {
            when {
                adminScreenVisible -> {
                    AdminScreen(
                        writeMode = writeMode,
                        writeState = writeState,
                        tagsWritten = tagsWritten,
                        onToggleWriteMode = viewModel::setWriteMode,
                        onResetCounter = viewModel::resetTagCounter,
                        onBack = viewModel::hideAdminScreen
                    )
                }
                onboardingActive -> {
                    OnboardingScreen(
                        btConnected = btConnected,
                        tapState = tapState,
                        onRefreshBt = viewModel::refreshBluetoothConnection,
                        onConfirmSpeaker = viewModel::confirmSpeaker,
                        onRejectSpeaker = viewModel::rejectSpeaker,
                        onReplayVerification = viewModel::replayVerification,
                        onSkip = viewModel::finishOnboarding
                    )
                }
                else -> {
                    SpeakersScreen(
                        userEmail = userEmail,
                        speakers = speakers,
                        tapState = tapState,
                        isAdmin = isAdmin,
                        onAddSpeaker = viewModel::startOnboarding,
                        onOpenAdmin = viewModel::showAdminScreen,
                        onConfirmSpeaker = viewModel::confirmSpeaker,
                        onRejectSpeaker = viewModel::rejectSpeaker,
                        onReplayVerification = viewModel::replayVerification,
                        onLogout = viewModel::logout
                    )
                }
            }
        }
    }

    // Pop-up grande visible nada mas leer el NFC: recuerda mantener el
    // telefono sobre el tag mientras se conecta el altavoz.
    if (tapState is TapState.Processing && !onboardingActive) {
        ConnectingDialog()
    }
}

@Composable
private fun ConnectingDialog() {
    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\uD83D\uDCF2", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Keep your phone on the tag",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Connecting to the speaker\u2026 don't move the phone " +
                        "away from the NFC tag until it finishes.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                CircularProgressIndicator()
            }
        }
    }
}
