package com.example.juicemachine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juicemachine.ui.JuiceMachineApp
import com.example.juicemachine.ui.SplashScreen
import com.example.juicemachine.ui.theme.JuiceMachineTheme
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModel
import com.example.juicemachine.ui.viewmodel.DrinkMenuViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JuiceMachineTheme {
                val application = this.applicationContext as JuiceMachineApplication
                val isReady by application.isReady.collectAsState()

                if (isReady) {
                    val viewModel: DrinkMenuViewModel = viewModel(
                        factory = DrinkMenuViewModelFactory(
                            application.repository!!, // Repository is guaranteed to be non-null here
                            application.hardwareManager
                        )
                    )
                    JuiceMachineApp(viewModel)
                } else {
                    SplashScreen()
                }
            }
        }
    }
}