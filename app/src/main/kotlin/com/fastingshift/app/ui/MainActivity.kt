package com.fastingshift.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.app.ui.timer.TimerRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FastingShiftTheme {
                TimerRoute()
            }
        }
    }
}
