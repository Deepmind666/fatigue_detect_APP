package com.example.juicemachine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.juicemachine.ui.AppNavigation
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val keepSplash = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplash.get() }
        super.onCreate(savedInstanceState)

        (application as? JuiceMachineApplication)?.ensureDefaultRecipesAsync()

        setContent {
            JuiceMachineTheme {
                AppNavigation(
                    onFirstContentReady = { keepSplash.set(false) }
                )
            }
        }
    }
}
