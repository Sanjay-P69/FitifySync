package com.fitifysync.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fitifysync.app.ui.state.UiEvent
import com.fitifysync.app.ui.theme.FitifySyncTheme
import com.fitifysync.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var healthPermissionsLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindowFlags()
        setupPermissionLauncher()
        observeUiEvents()

        setContent {
            FitifySyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }

        viewModel.initChecks()

        if (intent?.getBooleanExtra("trigger_sync", false) == true) {
            viewModel.triggerSync()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("trigger_sync", false)) {
            viewModel.triggerSync()
        }
    }

    private fun configureWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun setupPermissionLauncher() {
        val contract = PermissionController.createRequestPermissionResultContract()
        healthPermissionsLauncher = registerForActivityResult(contract) { _ ->
            // Update permissions in ViewModel
            viewModel.initChecks()
        }
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.ShowToast -> {
                            val duration = if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            Toast.makeText(this@MainActivity, event.message, duration).show()
                        }
                        is UiEvent.RequestHealthPermissions -> {
                            healthPermissionsLauncher.launch(event.permissions)
                        }
                    }
                }
            }
        }
    }
}
