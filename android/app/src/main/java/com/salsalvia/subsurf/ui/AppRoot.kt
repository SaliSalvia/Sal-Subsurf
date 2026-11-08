package com.salsalvia.subsurf.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salsalvia.subsurf.AppViewModel
import com.salsalvia.subsurf.R
import com.salsalvia.subsurf.ui.icons.SubsurfIcons

enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    MAP(R.string.tab_map, SubsurfIcons.Region),
    RESULTS(R.string.tab_results, SubsurfIcons.Analytics),
    SETTINGS(R.string.tab_settings, SubsurfIcons.Theme),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = Tab.entries
    val snackbar = remember { SnackbarHostState() }

    // "Collect everything" orchestration: once the satellite catalog for the locked area has
    // arrived, the terrain indicators are fetched automatically so a single run produces the
    // complete report. This is pure UI choreography — the ViewModel/backend calls are unchanged.
    var terrainRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.catalog, state.terrain, state.terrainBusy) {
        if (!terrainRequested && state.catalog != null && state.terrain == null && !state.terrainBusy) {
            // Only auto-run when a valid sketch exists (the terrain endpoint needs a polygon).
            if (state.vertices.size >= 3) {
                terrainRequested = true
                vm.computeTerrain()
            }
        }
    }
    LaunchedEffect(state.validation) {
        if (state.validation == null) terrainRequested = false
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // One-shot navigation to Results when a new validation report arrives. This lives here (and
    // not in MapScreen) because the tab content is disposed/recomposed on every tab switch;
    // an effect inside MapScreen would fire again on return and trap the user on Results.
    LaunchedEffect(state.analysisSeq) {
        if (state.analysisSeq > 0) tabIndex = Tab.RESULTS.ordinal
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(
                snackbar,
                modifier = Modifier.padding(bottom = 8.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        bottomBar = {
            SubsurfNavigationBar(
                tabs = tabs,
                selected = tabIndex,
                onSelect = { tabIndex = it },
            )
        },
    ) { padding ->
        // The map screen draws edge-to-edge underneath the bars; the scrolling screens consume
        // the bottom inset themselves so content is never clipped by the navigation pill.
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "screen",
            ) { index ->
                when (tabs[index]) {
                    Tab.MAP -> MapScreen(
                        vm = vm,
                        state = state,
                        contentPadding = padding,
                    )
                    Tab.RESULTS -> ResultsScreen(
                        vm = vm,
                        state = state,
                        onGoToMap = { tabIndex = Tab.MAP.ordinal },
                        contentPadding = padding,
                    )
                    Tab.SETTINGS -> SettingsScreen(vm = vm, state = state, contentPadding = padding)
                }
            }
        }
    }
}

/**
 * Floating, pill-shaped navigation bar. The selected item gets a tinted capsule so the current
 * section is obvious at a glance, and the whole bar floats above the content instead of
 * cutting it with a hard divider.
 */
@Composable
private fun SubsurfNavigationBar(
    tabs: List<Tab>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = 6.dp,
                bottom = bottomInset + 8.dp,
            ),
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            // Insets are handled by the surrounding Box so the pill can float.
            windowInsets = WindowInsets(0, 0, 0, 0),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(22.dp),
                ),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selected == index
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.92f,
                    animationSpec = tween(180),
                    label = "navIcon",
                )
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(index) },
                    label = {
                        Text(
                            stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    icon = {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/** Compact screen title used at the top of the content screens. */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}
