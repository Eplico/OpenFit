package com.eplico.openfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eplico.openfit.ui.OpenFitNavHost
import com.eplico.openfit.ui.theme.OpenFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OpenFitTheme {
                OpenFitNavHost()
            }
        }
    }
}
