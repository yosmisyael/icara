package com.example.icara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.icara.ui.MyAppNavHost
import com.example.icara.ui.theme.ICaraTheme
import com.example.icara.managers.PreferencesManager

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val splashScreen = installSplashScreen()

        preferencesManager = PreferencesManager(this)

        splashScreen.setKeepOnScreenCondition { false }

        setContent {
            ICaraTheme {
                MyAppNavHost(preferencesManager = preferencesManager)
            }
        }
    }
}