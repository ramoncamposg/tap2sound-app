package com.speakerroom.tap2sound.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("Tap2Sound Privacy Policy")
            BodyText("Last updated: 2026")

            SectionHeading("Information We Collect")
            BodyText(
                "When you create a Tap2Sound account, we collect your email address and " +
                    "a password (stored securely and never in plain text). To provide the " +
                    "core tap-to-connect experience, we also store the Bluetooth MAC " +
                    "address of speakers you pair and the identifier of the NFC tags you " +
                    "use to trigger those connections."
            )

            SectionHeading("How We Use Your Information")
            BodyText(
                "Your email and password are used solely to authenticate you and keep " +
                    "your account secure. Speaker and NFC tag identifiers are used to " +
                    "remember which speaker to connect to when you tap a tag, and are " +
                    "linked only to your account."
            )

            SectionHeading("Data Sharing")
            BodyText(
                "We do not sell or share your personal information with third parties. " +
                    "Data is stored on our secured backend infrastructure solely to " +
                    "operate the Tap2Sound service."
            )

            SectionHeading("Data Retention & Account Deletion")
            BodyText(
                "You can permanently delete your account and all associated data at any " +
                    "time from Settings > Delete account. This action removes your user " +
                    "record, your linked speakers, and cannot be undone."
            )

            SectionHeading("Permissions")
            BodyText(
                "Tap2Sound requests Bluetooth and NFC permissions strictly to discover, " +
                    "pair, and connect to your speakers. These permissions are not used " +
                    "to track your location or share data with advertisers."
            )

            SectionHeading("Contact")
            BodyText(
                "If you have questions about this policy or your data, contact us at " +
                    "support@tap2sound.com."
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
