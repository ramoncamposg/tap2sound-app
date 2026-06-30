package com.speakerroom.tap2sound.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    writeMode: Boolean,
    writeState: WriteState,
    tagsWritten: Int,
    onToggleWriteMode: (Boolean) -> Unit,
    onResetCounter: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin · Write tags") },
                actions = {
                    TextButton(onClick = onBack) { Text("Close") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NFC tag setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Turn on write mode and hold each blank tag near the phone. " +
                    "The app redirect is written and the tag is registered as " +
                    "official (only registered tags can be paired).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Interruptor de modo escritura
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Write mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = writeMode,
                        onCheckedChange = onToggleWriteMode
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Contador
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$tagsWritten",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "tags written this session",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Estado de la última escritura
            if (writeMode) {
                val (bg, msg) = when (writeState) {
                    is WriteState.Idle ->
                        MaterialTheme.colorScheme.secondaryContainer to
                            "Hold a tag near the phone to write it..."
                    is WriteState.Writing ->
                        MaterialTheme.colorScheme.secondaryContainer to
                            "Writing tag..."
                    is WriteState.Success ->
                        MaterialTheme.colorScheme.tertiaryContainer to
                            "✅ Tag #${writeState.total} written and registered. Bring the next one."
                    is WriteState.Error ->
                        MaterialTheme.colorScheme.errorContainer to
                            "⚠️ ${writeState.message}"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bg)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onResetCounter) {
                Text("Reset counter")
            }
        }
    }
}
