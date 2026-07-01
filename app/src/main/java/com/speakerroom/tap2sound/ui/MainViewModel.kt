package com.speakerroom.tap2sound.ui

import android.nfc.Tag
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakerroom.tap2sound.audio.AudioVerifier
import com.speakerroom.tap2sound.bluetooth.BtManager
import com.speakerroom.tap2sound.data.Speaker
import com.speakerroom.tap2sound.data.Tap2SoundRepository
import com.speakerroom.tap2sound.nfc.NfcHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estados de autenticación de la pantalla.
 */
sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    object LoggedIn : AuthState()
}

/**
 * Estado del flujo de tap NFC.
 */
sealed class TapState {
    object Idle : TapState()
    object Processing : TapState()
    // Verificando que el altavoz conectado es el correcto (suena el tono).
    data class Verifying(val mac: String) : TapState()
    data class Connected(val speakerName: String) : TapState()
    data class Error(val message: String) : TapState()
}

/**
 * Estado de la escritura de un tag NFC (modo admin).
 */
sealed class WriteState {
    object Idle : WriteState()
    object Writing : WriteState()
    data class Success(val total: Int) : WriteState()
    data class Error(val message: String) : WriteState()
}

class MainViewModel(
    private val repository: Tap2SoundRepository,
    private val btManager: BtManager,
    private val audioVerifier: AudioVerifier
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _tapState = MutableStateFlow<TapState>(TapState.Idle)
    val tapState: StateFlow<TapState> = _tapState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Onboarding guiado (paso 1: conectar BT, paso 2: tocar NFC)
    private val _onboardingActive = MutableStateFlow(false)
    val onboardingActive: StateFlow<Boolean> = _onboardingActive.asStateFlow()

    // Estado de conexión Bluetooth detectada (null = sin comprobar todavía)
    private val _btConnected = MutableStateFlow<Boolean?>(null)
    val btConnected: StateFlow<Boolean?> = _btConnected.asStateFlow()

    // MAC del altavoz actualmente conectado (el que suena). Se usa para
    // resaltarlo en la lista. null = ninguno conectado por la app todavía.
    private val _connectedMac = MutableStateFlow<String?>(null)
    val connectedMac: StateFlow<String?> = _connectedMac.asStateFlow()

    // ---- Admin ----
    // Pantalla de admin visible
    private val _adminScreenVisible = MutableStateFlow(false)
    val adminScreenVisible: StateFlow<Boolean> = _adminScreenVisible.asStateFlow()

    // Modo escritura: si está activo, un tap NFC escribe el tag en vez de emparejar
    private val _writeMode = MutableStateFlow(false)
    val writeMode: StateFlow<Boolean> = _writeMode.asStateFlow()

    private val _writeState = MutableStateFlow<WriteState>(WriteState.Idle)
    val writeState: StateFlow<WriteState> = _writeState.asStateFlow()

    private val _tagsWritten = MutableStateFlow(0)
    val tagsWritten: StateFlow<Int> = _tagsWritten.asStateFlow()

    val isAdmin: StateFlow<Boolean> = repository.isAdmin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Evento de un solo disparo: minimizar la app tras un tap ya emparejado.
    private val _minimizeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val minimizeEvent: SharedFlow<Unit> = _minimizeEvent.asSharedFlow()

    // UID de un tap recibido antes de que la sesión esté confirmada.
    private var pendingTapUid: String? = null

    // La app está "lista" cuando hay sesión iniciada Y la sincronización inicial
    // de altavoces ha terminado (caché UID->MAC disponible). Hasta entonces, un
    // tap NFC no se procesa: se guarda como pendiente y se reprocesa solo cuando
    // se alcanza este estado. Así una única lectura basta aunque el tag llegue
    // durante el arranque.
    @Volatile
    private var appReady = false

    // Temporizador para minimizar la app tras conectar. Se mantiene la app en
    // primer plano unos segundos (así el foreground dispatch de NFC capta cada
    // tag al instante, sin el bloqueo BAL de Android 15). Cada nuevo tap resetea
    // el temporizador; solo se minimiza tras unos segundos sin actividad.
    private var minimizeJob: Job? = null
    private val minimizeDelayMs = 6000L

    // Si es false, la app NUNCA se auto-minimiza tras conectar (solución fiable
    // al doble tap con Android 15: al quedarse en primer plano, el foreground
    // dispatch capta cada NFC sin el bloqueo BAL). Ponlo a true para recuperar
    // el comportamiento de auto-minimizar tras [minimizeDelayMs] ms.
    private val autoMinimizeEnabled = false

    // Datos a la espera de confirmación de altavoz (verificación por sonido).
    private var verifyUid: String? = null
    private var verifyMac: String? = null
    private var verifyName: String? = null

    val userEmail: StateFlow<String?> = repository.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savedPassword: StateFlow<String?> = repository.userPassword
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val speakers: StateFlow<List<Speaker>> = repository.cachedSpeakers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            _authState.value = if (repository.isLoggedIn()) {
                // Sincronizar en segundo plano al iniciar sesión
                repository.syncSpeakers()
                appReady = true
                Log.d("T2S_DEBUG", "checkLoginStatus: session ready (sync done), appReady=true")
                AuthState.LoggedIn.also { processPendingTapIfAny() }
            } else {
                AuthState.LoggedOut
            }
        }
    }

    /**
     * Punto de entrada desde MainActivity cuando se escanea un tag NFC.
     * Si la sesión ya está activa, procesa de inmediato; si no, lo guarda
     * para procesarlo en cuanto el usuario inicie sesión.
     */
    fun onNfcTagScanned(nfcUid: String) {
        // Guardar SIEMPRE el UID como operación pendiente y no perderlo.
        // Si la app ya está lista se procesa de inmediato; si no, quedará
        // pendiente y se reprocesará en cuanto login + sync + caché estén listos.
        Log.d("T2S_DEBUG", "onNfcTagScanned: uid=$nfcUid appReady=$appReady authState=${_authState.value}")
        pendingTapUid = nfcUid
        processPendingTapIfAny()
    }

    /**
     * Programa la minimización de la app tras [minimizeDelayMs] ms. Cada llamada
     * reinicia el temporizador; así, mientras haya taps seguidos, la app sigue en
     * primer plano y el foreground dispatch capta cada uno sin BAL de Android 15.
     */
    private fun scheduleMinimize() {
        minimizeJob?.cancel()
        minimizeJob = viewModelScope.launch {
            delay(minimizeDelayMs)
            _minimizeEvent.tryEmit(Unit)
        }
    }

    private fun processPendingTapIfAny() {
        // Todavía no está lista la sesión/caché: se reintentará al estarlo.
        if (!appReady) {
            Log.d("T2S_DEBUG", "processPendingTapIfAny: appReady=false, keeping pending=$pendingTapUid")
            return
        }
        val pending = pendingTapUid ?: run {
            Log.d("T2S_DEBUG", "processPendingTapIfAny: no pending tap")
            return
        }
        Log.d("T2S_DEBUG", "processPendingTapIfAny: processing queued uid=$pending")
        pendingTapUid = null
        handleNfcTap(pending)
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            _authState.value = AuthState.Loading
            repository.register(email, password)
                .onSuccess {
                    appReady = true
                    _authState.value = AuthState.LoggedIn
                    if (pendingTapUid != null) {
                        processPendingTapIfAny()
                    } else {
                        // Usuario nuevo: guiarlo con el onboarding
                        _onboardingActive.value = true
                    }
                }
                .onFailure {
                    _authError.value = it.message
                    _authState.value = AuthState.LoggedOut
                }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            _authState.value = AuthState.Loading
            repository.login(email, password)
                .onSuccess {
                    repository.syncSpeakers()
                    appReady = true
                    _authState.value = AuthState.LoggedIn
                    if (pendingTapUid != null) {
                        processPendingTapIfAny()
                    } else if (!repository.hasSpeakers()) {
                        // Sin altavoces vinculados: guiar con el onboarding
                        _onboardingActive.value = true
                    }
                }
                .onFailure {
                    _authError.value = it.message
                    _authState.value = AuthState.LoggedOut
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            appReady = false
            pendingTapUid = null
            minimizeJob?.cancel()
            _connectedMac.value = null
            _adminScreenVisible.value = false
            _writeMode.value = false
            _onboardingActive.value = false
            _tapState.value = TapState.Idle
            _authState.value = AuthState.LoggedOut
        }
    }

    /**
     * CORE: Maneja un tap NFC.
     *
     * 1. Si el UID ya está cacheado localmente -> conectar directamente (uso diario).
     * 2. Si NO está cacheado -> capturar MAC del BT conectado, hacer auto-pair
     *    en el backend, y conectar.
     */
    fun handleNfcTap(nfcUid: String) {
        // Un nuevo tap cancela cualquier minimizado pendiente: la app se queda
        // en primer plano para captar el siguiente tag sin BAL.
        minimizeJob?.cancel()
        viewModelScope.launch {
            _tapState.value = TapState.Processing

            // 1. ¿Ya conocemos este altavoz localmente? (uso diario)
            // Si la caché aún no tiene el UID (p. ej. justo tras arrancar, antes
            // de que termine el primer sync), reintentamos una vez tras sincronizar
            // para no tratar por error un altavoz ya emparejado como si fuera nuevo.
            var cachedMac = repository.getCachedMacByUid(nfcUid)
            Log.d("T2S_DEBUG", "handleNfcTap: uid=$nfcUid cachedMac(1st)=$cachedMac")
            if (cachedMac == null) {
                repository.syncSpeakers()
                cachedMac = repository.getCachedMacByUid(nfcUid)
                Log.d("T2S_DEBUG", "handleNfcTap: after resync cachedMac=$cachedMac")
            }
            if (cachedMac != null) {
                val connected = btManager.connectToSpeaker(cachedMac)
                if (connected) {
                    // Breve espera para que se asiente la ruta A2DP, reanudar la
                    // musica y mostrar Connected. La app se mantiene en primer
                    // plano unos segundos (foreground dispatch capta taps sin BAL)
                    // y se minimiza tras ese margen si no hay más actividad.
                    kotlinx.coroutines.delay(800)
                    btManager.resumePlayback()
                    _connectedMac.value = cachedMac
                    _tapState.value = TapState.Connected("Speaker connected")
                    _onboardingActive.value = false
                    // NO auto-minimizamos: si la app se va a segundo plano, el
                    // siguiente tap NFC choca con el bloqueo BAL de Android 15 y
                    // exige un segundo toque. Quedándonos en primer plano, el
                    // foreground dispatch capta cada tag al instante (un solo toque).
                    // Para volver a la música, el usuario pulsa Inicio.
                    if (autoMinimizeEnabled) scheduleMinimize()
                } else {
                    _tapState.value = TapState.Error("Couldn't connect to the speaker")
                }
                return@launch
            }

            // 1b. ¿El tag está registrado en el sistema (lista blanca)?
            val registered = repository.isTagRegistered(nfcUid)
            if (registered == false) {
                _tapState.value = TapState.Error(
                    "This NFC tag is not registered in the system. Ask the admin to register it."
                )
                return@launch
            }

            // 2. Primer tap de este altavoz: capturar MAC del BT conectado
            if (!btManager.isBluetoothEnabled()) {
                _tapState.value = TapState.Error("Turn on Bluetooth first")
                return@launch
            }

            val connectedMac = btManager.getActiveSpeakerMac()
            if (connectedMac == null) {
                _tapState.value = TapState.Error(
                    "Connect the phone to the Bluetooth speaker before tapping the NFC"
                )
                return@launch
            }

            // 3. Verificar por sonido que es el altavoz correcto ANTES de fijar.
            verifyUid = nfcUid
            verifyMac = connectedMac
            verifyName = btManager.getDeviceName(connectedMac)
            _tapState.value = TapState.Verifying(connectedMac)
            audioVerifier.playVerification()
        }
    }

    /** El usuario confirma que el sonido salió del altavoz correcto. */
    fun confirmSpeaker() {
        val uid = verifyUid
        val mac = verifyMac
        audioVerifier.stop()
        if (uid == null || mac == null) {
            _tapState.value = TapState.Idle
            return
        }
        viewModelScope.launch {
            _tapState.value = TapState.Processing
            repository.autoPair(uid, mac, verifyName)
                .onSuccess { speaker ->
                    _connectedMac.value = mac
                    _tapState.value = TapState.Connected(speaker.name ?: "Speaker")
                    _onboardingActive.value = false
                }
                .onFailure {
                    _tapState.value = TapState.Error(it.message ?: "Pairing error")
                }
            verifyUid = null
            verifyMac = null
            verifyName = null
        }
    }

    /** El usuario indica que NO sonó en el altavoz correcto. */
    fun rejectSpeaker() {
        audioVerifier.stop()
        verifyUid = null
        verifyMac = null
        verifyName = null
        _tapState.value = TapState.Error(
            "Make sure the speaker you want to pair is the one selected in your " +
                "phone's Bluetooth. Select it there first, then tap the NFC again."
        )
    }

    /** Repite el sonido de verificación. */
    fun replayVerification() {
        if (verifyMac != null) {
            audioVerifier.playVerification()
        }
    }

    fun resetTapState() {
        _tapState.value = TapState.Idle
    }

    // ---------- ONBOARDING ----------

    /** Abre el onboarding manualmente (botón "Add speaker"). */
    fun startOnboarding() {
        _btConnected.value = null
        _tapState.value = TapState.Idle
        _onboardingActive.value = true
    }

    /** Cierra el onboarding (al completarse o al saltarlo). */
    fun finishOnboarding() {
        _onboardingActive.value = false
        _btConnected.value = null
    }

    /** Comprueba si hay un altavoz Bluetooth A2DP conectado (paso 1). */
    fun refreshBluetoothConnection() {
        viewModelScope.launch {
            if (!btManager.isBluetoothEnabled()) {
                _btConnected.value = false
                return@launch
            }
            _btConnected.value = btManager.getConnectedSpeakerMac() != null
        }
    }

    // ---------- ADMIN ----------

    fun showAdminScreen() {
        _adminScreenVisible.value = true
    }

    fun hideAdminScreen() {
        _writeMode.value = false
        _writeState.value = WriteState.Idle
        _adminScreenVisible.value = false
    }

    /** Activa/desactiva el modo escritura de tags. */
    fun setWriteMode(enabled: Boolean) {
        _writeMode.value = enabled
        _writeState.value = WriteState.Idle
    }

    /** Reinicia el contador de tags escritos en esta sesión. */
    fun resetTagCounter() {
        _tagsWritten.value = 0
    }

    /**
     * Punto de entrada único para un tag NFC detectado.
     * Si el modo escritura (admin) está activo, escribe el NDEF;
     * en caso contrario, sigue el flujo normal de emparejar/conectar.
     */
    fun onNfcTag(tag: Tag) {
        if (_writeMode.value) {
            writeTag(tag)
        } else {
            onNfcTagScanned(NfcHelper.getTagUid(tag))
        }
    }

    /**
     * Prepara un tag: escribe el NDEF (AAR + URI a Play Store) y lo
     * pre-registra en la lista blanca del backend. Solo cuenta como
     * escrito si ambas cosas tienen éxito.
     */
    private fun writeTag(tag: Tag) {
        viewModelScope.launch {
            _writeState.value = WriteState.Writing
            val uid = NfcHelper.getTagUid(tag)

            // 1. Escribir NDEF en el tag (local)
            val wrote = withContext(Dispatchers.IO) {
                NfcHelper.writeNdefToTag(tag, uid)
            }
            if (!wrote) {
                _writeState.value = WriteState.Error(
                    "Couldn't write the tag. Is it rewritable and NDEF-compatible?"
                )
                return@launch
            }

            // 2. Registrar el UID en la lista blanca (backend)
            repository.registerTag(uid)
                .onSuccess {
                    _tagsWritten.value += 1
                    _writeState.value = WriteState.Success(_tagsWritten.value)
                }
                .onFailure {
                    _writeState.value = WriteState.Error(
                        "Tag written, but couldn't register it: ${it.message}. " +
                            "Hold it near again to retry."
                    )
                }
        }
    }

    fun renameSpeaker(speakerId: String, name: String) {
        viewModelScope.launch {
            repository.renameSpeaker(speakerId, name)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioVerifier.release()
        btManager.release()
    }
}
