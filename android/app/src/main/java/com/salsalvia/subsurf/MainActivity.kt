package com.salsalvia.subsurf

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salsalvia.subsurf.data.ThemeMode
import com.salsalvia.subsurf.ui.AppRoot
import com.salsalvia.subsurf.ui.theme.SubsurfTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge with transparent system bars; the Compose theme flips the icon colours.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.state.collectAsState()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Publish the resolved appearance so the map can choose its basemap, then render.
            androidx.compose.runtime.LaunchedEffect(dark) { vm.applyResolvedTheme(dark) }
            // One startup probe, so the map can show a "backend not reachable" hint.
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.probeBackend() }
            SubsurfTheme(darkTheme = dark) {
                AppRoot(vm)
            }
        }
    }
}
