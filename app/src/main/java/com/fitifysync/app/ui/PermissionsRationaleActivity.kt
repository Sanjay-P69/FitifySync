package com.fitifysync.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Fitify Sync Privacy & Permissions",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fitify Sync is an open-source, local utility designed to read workout sessions from your locally installed Fitify app using root permissions, and bridge them into Android's Health Connect.\n\n" +
                                    "• 100% Local: No data is ever transmitted to any external server or third party.\n" +
                                    "• Health Connect Writes: Permissions are strictly used to store Exercise and Active Calorie records so that Samsung Health and Google Fit can perform detailed fitness analysis.\n" +
                                    "• Revocable: You can revoke permissions or uninstall this app at any time in Health Connect settings.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { finish() }) {
                            Text("Back to App")
                        }
                    }
                }
            }
        }
    }
}
