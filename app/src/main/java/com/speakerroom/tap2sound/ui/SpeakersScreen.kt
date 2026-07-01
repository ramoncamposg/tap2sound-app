package com.speakerroom.tap2sound.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakerroom.tap2sound.data.Speaker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakersScreen(
    userEmail: String?,
    speakers: List<Speaker>,
    tapState: TapState,
    isAdmin: Boolean,
    connectedMac: String?,
    onAddSpeaker: () -> Unit,
    onOpenAdmin: () -> Unit,
    onConfirmSpeaker: () -> Unit,
    onRejectSpeaker: () -> Unit,
    onReplayVerification: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Speakers") },
                actions = {
                    if (isAdmin) {
                        TextButton(onClick = onOpenAdmin) { Text("Admin") }
                    }
                    TextButton(onClick = onLogout) {
                        Text("Log out")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSpeaker,
                text = { Text("Add speaker") },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Banner de estado del tap
            TapStatusBanner(
                tapState = tapState,
                onConfirmSpeaker = onConfirmSpeaker,
                onRejectSpeaker = onRejectSpeaker,
                onReplayVerification = onReplayVerification
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (speakers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "📡",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No speakers linked yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Connect your phone to a Bluetooth speaker and tap its NFC",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(speakers) { speaker ->
                        SpeakerCard(
                            speaker = speaker,
                            isConnected = connectedMac != null &&
                                speaker.btMac.equals(connectedMac, ignoreCase = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TapStatusBanner(
    tapState: TapState,
    onConfirmSpeaker: () -> Unit,
    onRejectSpeaker: () -> Unit,
    onReplayVerification: () -> Unit
) {
    when (tapState) {
        is TapState.Idle -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    "Tap a speaker's NFC to connect",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        is TapState.Processing -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "Connecting to speaker... keep the phone on the tag",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        is TapState.Verifying -> {
            SpeakerVerification(
                onConfirm = onConfirmSpeaker,
                onReject = onRejectSpeaker,
                onReplay = onReplayVerification
            )
        }
        is TapState.Connected -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    "✅ ${tapState.speakerName}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        is TapState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "⚠️ ${tapState.message}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SpeakerCard(speaker: Speaker, isConnected: Boolean) {
    val cardColors = if (isConnected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(
            if (isConnected) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = MaterialTheme.shapes.medium
                )
            } else {
                Modifier
            }
        )
    Card(modifier = cardModifier, colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = speaker.name ?: "Speaker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isConnected) {
                    Text(
                        text = "🔊 Conectado",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "MAC: ${speaker.btMac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "NFC: ${speaker.nfcUid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
