package com.eplico.openfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eplico.openfit.data.UserSettings
import com.eplico.openfit.ui.OpenFitNavHost
import com.eplico.openfit.ui.theme.OpenFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = (application as OpenFitApp).container.settings.settings
        setContent {
            // Wait for settings (a few ms) so the chosen accent colour is used from the first frame.
            val settings: UserSettings? by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val loaded = settings ?: return@setContent
            OpenFitTheme(accent = loaded.accent, trophyColors = loaded.trophyColors) {
                OpenFitNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as OpenFitApp).container.onForeground()
    }
}
