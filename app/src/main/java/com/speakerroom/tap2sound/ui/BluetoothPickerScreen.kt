package com.speakerroom.tap2sound.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakerroom.tap2sound.bluetooth.BtDevice

/**
 * Selector de Bluetooth propio de la app: muestra los dispositivos ya
 * emparejados con el teléfono (como el menú de Bluetooth del sistema) y permite
 * elegir uno A MANO para conectarlo, sin necesidad de tocar la etiqueta NFC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPickerScreen(
    devices: List<BtDevice>,
    connectedMac: String?,
    tapState: TapState,
    onSelectDevice: (BtDevice) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Bluetooth device") },
                actions = {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Pick a paired device to connect, just like from the phone's " +
                    "Bluetooth menu — no NFC needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Banner de estado de la conexión en curso.
            when (tapState) {
                is TapState.Processing -> StatusBanner(
                    MaterialTheme.colorScheme.secondaryContainer,
                    "Connecting…"
                )
                is TapState.Connected -> StatusBanner(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    "✅ ${tapState.speakerName}"
                )
                is TapState.Error -> StatusBanner(
                    MaterialTheme.colorScheme.errorContainer,
                    "⚠️ ${tapState.message}"
                )
                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No paired devices found",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Turn on Bluetooth and pair your speaker first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            isConnected = connectedMac != null &&
                                device.mac.equals(connectedMac, ignoreCase = true),
                            onClick = { onSelectDevice(device) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pair a new device (system settings)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun DeviceCard(device: BtDevice, isConnected: Boolean, onClick: () -> Unit) {
    val cardColors = if (isConnected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }
    val modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
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
    Card(modifier = modifier, colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
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
                text = "MAC: ${device.mac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBanner(bgColor: androidx.compose.ui.graphics.Color, text: String) {
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
