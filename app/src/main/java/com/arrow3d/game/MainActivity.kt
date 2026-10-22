package com.arrow3d.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arrow3d.game.presentation.navigation.AppNavHost
import com.arrow3d.game.ui.theme.Arrow3dTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Arrow3dTheme {
                AppNavHost()
            }
        }
    }
}
