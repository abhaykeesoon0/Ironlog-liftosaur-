package com.ironlog.app.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.Space

/**
 * What the app keeps, where, and what ever leaves the phone -- in the app,
 * where the person whose data it is can read it, rather than only in a
 * report they will never see.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Privacy & your data", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = Space.md)
        ) {
            Caption("Liftosaur has no account server and no analytics. This page is the whole story.")

            SectionHeader("What is stored", Modifier.padding(top = Space.lg))
            Bullets(
                "Your workouts, sets, records, routines, body measurements, cardio routes and food log.",
                "Your profile: name, email, password (as a salted PBKDF2 hash, never the password itself), units, goals.",
                "Progress photos, in the app's private storage, never in the shared gallery.",
                "Preferences: units, colours, reminders, the Home layout."
            )

            SectionHeader("Where, and how it is protected", Modifier.padding(top = Space.lg))
            Bullets(
                "Everything lives in the app's private storage on this phone. The database is encrypted at rest with " +
                    "SQLCipher; its key is generated here and held in the Android Keystore.",
                "Sign-in tokens, the API key you may add, and the database key sit in Keystore-encrypted preferences.",
                "Cloud backup and device-to-device transfer of the app's data are switched off in the manifest.",
                "Optional app lock with fingerprint, face or screen lock. Photo screens block screenshots.",
                "Five failed sign-ins start a growing wait before the next attempt."
            )

            SectionHeader("What leaves the phone", Modifier.padding(top = Space.lg))
            Bullets(
                "Nothing, unless you turn a feature on. Each one is opt-in and says so where you turn it on.",
                "Barcode lookup: the barcode digits go to Open Food Facts. No account, no key.",
                "Exercise browser: search terms go to wger. No account, no key.",
                "Coach web lookups: the topic of your question goes to DuckDuckGo or Wikipedia. Never your log.",
                "Cloud assistant: your question plus a few lines of your training figures go to Anthropic, with a key " +
                    "you supply. Off until you add one.",
                "Health Connect: sessions and meals are written to it only after you connect it, and you can revoke that.",
                "Every connection is HTTPS, with the certificate pinned to the service's root."
            )

            SectionHeader("What you can do", Modifier.padding(top = Space.lg))
            Bullets(
                "Export everything as CSV or as an encrypted .liftosaur backup, from Settings.",
                "Delete your account from Settings. It removes the user and every row that belongs to it.",
                "Uninstalling the app removes all of it; nothing is kept anywhere else."
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun Bullets(vararg lines: String) {
    IronCard {
        Column {
            lines.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(Space.sm))
                Text("•  $line", style = MaterialTheme.typography.bodyMedium, color = if (index == 0) Chalk else ChalkDim)
            }
        }
    }
}
