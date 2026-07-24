package com.speakerroom.tap2sound.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.speakerroom.tap2sound.R
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    btConnected: Boolean?,
    tapState: TapState,
    onRefreshBt: () -> Unit,
    onConfirmSpeaker: () -> Unit,
    onRejectSpeaker: () -> Unit,
    onReplayVerification: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }

    // Paso 1: sondear la conexión Bluetooth cada 2,5 s.
    LaunchedEffect(step) {
        while (step == 1) {
            onRefreshBt()
            delay(2500)
        }
    }

    // Auto-avanzar al paso 2 cuando se detecta el altavoz.
    LaunchedEffect(btConnected, step) {
        if (step == 1 && btConnected == true) {
            delay(800)
            step = 2
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Step $step of 2",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (step == 1) {
            StepConnectBluetooth(
                btConnected = btConnected,
                onOpenBtSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                onContinue = { step = 2 }
            )
        } else {
            StepTapNfc(
                tapState = tapState,
                onConfirmSpeaker = onConfirmSpeaker,
                onRejectSpeaker = onRejectSpeaker,
                onReplayVerification = onReplayVerification,
                onBack = { step = 1 }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun StepConnectBluetooth(
    btConnected: Boolean?,
    onOpenBtSettings: () -> Unit,
    onContinue: () -> Unit
) {
    Text(
        text = "🔊",
        style = MaterialTheme.typography.displayLarge
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Connect your phone to the speaker",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Turn on the speaker and pair your phone with it from Bluetooth settings, like any speaker.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    // Indicador de estado de conexión
    val (bgColor, statusText) = when (btConnected) {
        true -> MaterialTheme.colorScheme.tertiaryContainer to "✅ Speaker detected"
        false -> MaterialTheme.colorScheme.errorContainer to "No connected speaker detected yet"
        null -> MaterialTheme.colorScheme.secondaryContainer to "Checking connection..."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Text(
            text = statusText,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = onOpenBtSettings,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open Bluetooth settings")
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (btConnected == true) "Continue" else "Already connected, continue")
    }
}

@Composable
private fun StepTapNfc(
    tapState: TapState,
    onConfirmSpeaker: () -> Unit,
    onRejectSpeaker: () -> Unit,
    onReplayVerification: () -> Unit,
    onBack: () -> Unit
) {
    Box(
                modifier = Modifier
                    .size(96.dp)
                                .background(color = T2SInk, shape = CircleShape)
                                            .border(width = 2.dp, color = T2SGold, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                                painter = painterResource(id = R.drawable.ic_tap_nfc),
                                contentDescription = "Tap2Sound NFC icon",
                                modifier = Modifier
                                    .size(68.dp)
                                                    .clip(CircleShape)
                                                            )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Hold the phone to the speaker's NFC",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Tap the speaker's NFC tag with the back of the phone. We'll save the association automatically.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    when (tapState) {
        is TapState.Processing -> StatusCard(
            MaterialTheme.colorScheme.secondaryContainer,
            "Registering speaker..."
        )
        is TapState.Verifying -> {
            SpeakerVerification(
                onConfirm = onConfirmSpeaker,
                onReject = onRejectSpeaker,
                onReplay = onReplayVerification
            )
        }
        is TapState.Error -> StatusCard(
            MaterialTheme.colorScheme.errorContainer,
            "⚠️ ${tapState.message}"
        )
        is TapState.Connected -> StatusCard(
            MaterialTheme.colorScheme.tertiaryContainer,
            "✅ ${tapState.speakerName}"
        )
        is TapState.Idle -> StatusCard(
            MaterialTheme.colorScheme.primaryContainer,
            "Waiting for the NFC tap..."
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onBack) {
        Text("Back")
    }
}

/**
 * Tarjeta de verificación: suena un tono en el altavoz y el usuario
 * confirma si lo ha oído en el altavoz correcto.
 */
@Composable
fun SpeakerVerification(
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onReplay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔊 A melody and a voice message will play",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Did it play on the Bluetooth speaker you want to connect?",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) { Text("Yes, this one") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) { Text("No") }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onReplay) {
                Text("Play again")
            }
        }
    }
}

@Composable
private fun StatusCard(bgColor: androidx.compose.ui.graphics.Color, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
