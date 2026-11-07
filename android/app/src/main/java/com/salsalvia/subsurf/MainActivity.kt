package com.salsalvia.subsurf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salsalvia.subsurf.ui.AppRoot
import com.salsalvia.subsurf.ui.theme.SubsurfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubsurfTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}
