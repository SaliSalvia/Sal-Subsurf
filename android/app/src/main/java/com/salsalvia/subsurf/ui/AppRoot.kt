package com.salsalvia.subsurf.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.salsalvia.subsurf.AppViewModel
import com.salsalvia.subsurf.R

enum class Tab(@StringRes val labelRes: Int) {
    MAP(R.string.tab_map),
    RESULTS(R.string.tab_results),
    SETTINGS(R.string.tab_settings),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = Tab.entries
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = {
                            when (tab) {
                                Tab.MAP -> Icon(Icons.Filled.Home, contentDescription = null)
                                Tab.RESULTS ->
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                                Tab.SETTINGS ->
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "screen",
            ) { index ->
                when (tabs[index]) {
                    Tab.MAP -> MapScreen(
                        vm = vm,
                        state = state,
                        onAnalyzed = { tabIndex = 1 },
                    )
                    Tab.RESULTS -> ResultsScreen(vm = vm, state = state)
                    Tab.SETTINGS -> SettingsScreen(vm = vm, state = state)
                }
            }
        }
    }
}
