package com.nexora.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nexora.companion.ui.CompanionViewModel
import com.nexora.companion.ui.NexoraApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF74518C),onPrimary=Color.White,secondary=Color(0xFF67546F),background=Color(0xFFFFF9FF),surface=Color(0xFFFFF9FF))) {
                NexoraApp(viewModel<CompanionViewModel>())
            }
        }
    }
}
