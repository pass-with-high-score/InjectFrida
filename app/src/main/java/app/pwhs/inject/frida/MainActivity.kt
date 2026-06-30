package app.pwhs.inject.frida

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.pwhs.inject.frida.ui.screens.MainScreen
import app.pwhs.inject.frida.ui.theme.InjectFridaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InjectFridaTheme(darkTheme = true) {
                MainScreen()
            }
        }
    }
}