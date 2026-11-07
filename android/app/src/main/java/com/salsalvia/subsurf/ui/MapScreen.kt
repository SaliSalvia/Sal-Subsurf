package com.salsalvia.subsurf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salsalvia.subsurf.AppViewModel
import com.salsalvia.subsurf.R
import com.salsalvia.subsurf.UiState
import com.salsalvia.subsurf.geo.GeoMath
import com.salsalvia.subsurf.map.MapLoadingOverlay
import com.salsalvia.subsurf.map.MapZoomControls
import com.salsalvia.subsurf.map.RegionMapController
import com.salsalvia.subsurf.map.RegionMapView
import com.salsalvia.subsurf.ui.components.Pill
import com.salsalvia.subsurf.ui.icons.SubsurfIcons
import com.salsalvia.subsurf.ui.theme.Dims

/**
 * The whole app flows from this screen: sketch a ring on real cartography, watch the live
 * area/vertex counters, then run the analysis. Everything overlays the map so the map itself
 * gets as much room as possible.
 */
@Composable
fun MapScreen(
    vm: AppViewModel,
    state: UiState,
    onAnalyzed: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val haptics = LocalHapticFeedback.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val statusBars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var mapReady by remember { mutableStateOf(false) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var controller by remember { mutableStateOf<RegionMapController?>(null) }

    val vertexCount = state.vertices.size
    val canAnalyze = vertexCount >= GeoMath.MIN_VERTICES && !state.busy
    val areaText = remember(state.vertices) { vm.formatArea(state.vertices) }

    val latestTap = rememberUpdatedState(newValue = { lat: Double, lon: Double ->
        if (vertexCount < GeoMath.MAX_VERTICES) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        vm.addVertex(lat, lon)
    })

    // Keep the ring inside the visible area between the floating header and the action sheet.
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(controller, density) {
        val top = with(density) { (statusBars + 128.dp).toPx() }.toInt()
        val bottom = with(density) { (contentPadding.calculateBottomPadding() + 168.dp).toPx() }.toInt()
        controller?.setViewInset(top = top, bottom = bottom)
    }

    Box(Modifier.fillMaxSize()) {
        RegionMapView(
            vertices = state.vertices,
            onMapTap = { lat, lon -> latestTap.value(lat, lon) },
            modifier = Modifier.fillMaxSize(),
            dark = dark,
            fitRequest = fitRequest,
            onController = { controller = it },
            onLoadState = { mapReady = true },
        )

        /***  Top overlay: brand header + live counters  ***/
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = statusBars + 8.dp, start = Dims.l, end = Dims.l),
            verticalArrangement = Arrangement.spacedBy(Dims.s),
        ) {
            MapHeader(
                vertexCount = vertexCount,
                areaText = if (vertexCount >= GeoMath.MIN_VERTICES) areaText else null,
                perimeterText = if (vertexCount >= GeoMath.MIN_VERTICES) {
                    stringResource(
                        R.string.perimeter_format,
                        formatNumber(GeoMath.perimeterM(state.vertices), 0),
                    )
                } else null,
            )
            AnimatedVisibility(
                visible = vertexCount == 0,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 3 },
                exit = fadeOut(tween(120)),
            ) {
                MapHint()
            }
        }

        /***  Right-hand floating map controls  ***/
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Dims.m),
            verticalArrangement = Arrangement.spacedBy(Dims.s),
            horizontalAlignment = Alignment.End,
        ) {
            MapZoomControls(
                onZoomIn = { controller?.zoomBy(1.0) },
                onZoomOut = { controller?.zoomBy(-1.0) },
            )
            if (vertexCount > 0) {
                FloatingMapButton(
                    icon = SubsurfIcons.Fit,
                    label = stringResource(R.string.map_fit),
                    onClick = { fitRequest++ },
                )
            }
        }

        MapLoadingOverlay(visible = !mapReady)

        /***  Bottom sheet: sketching state + primary action  ***/
        SketchSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            vertexCount = vertexCount,
            busy = state.busy,
            canAnalyze = canAnalyze,
            bottomInset = contentPadding.calculateBottomPadding(),
            onUndo = { if (vertexCount > 0) vm.undo() },
            onClear = { vm.clear() },
            onAnalyze = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.analyze()
            },
        )
    }

    // Jump to the Results tab as soon as the server validation arrives.
    LaunchedEffect(state.validation) {
        if (state.validation != null && !state.busy) onAnalyzed()
    }
}

// --------------------------------------------------------------------------- //

@Composable
private fun MapHeader(vertexCount: Int, areaText: String?, perimeterText: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Dims.s)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dims.s),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dims.s),
            ) {
                BrandMarkSmall()
                Text(
                    stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Pill(
                text = stringResource(R.string.vertices_short, vertexCount, GeoMath.MAX_VERTICES),
                color = if (vertexCount >= GeoMath.MIN_VERTICES) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                icon = SubsurfIcons.Region,
            )
        }

        // Live measurements appear the moment the ring becomes a polygon.
        AnimatedVisibility(
            visible = areaText != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = Dims.l, vertical = Dims.m),
                horizontalArrangement = Arrangement.spacedBy(Dims.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        stringResource(R.string.metric_area),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        areaText ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column {
                    Text(
                        stringResource(R.string.metric_perimeter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        perimeterText ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandMarkSmall() {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SubsurfIcons.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun MapHint() {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f))
            .padding(horizontal = Dims.l, vertical = Dims.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dims.m),
    ) {
        Icon(
            SubsurfIcons.Region,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(R.string.tap_to_add_vertex),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun FloatingMapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Bottom action sheet. Collapses to a single line while the user is placing their first
 * vertices and expands with live measurements once a real ring exists, so the map stays
 * uncluttered in the state that matters most.
 */
@Composable
private fun SketchSheet(
    modifier: Modifier = Modifier,
    vertexCount: Int,
    busy: Boolean,
    canAnalyze: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dims.m, vertical = Dims.m)
            .padding(bottom = bottomInset)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = Dims.l, vertical = Dims.l)
            .animateContentSize(tween(260)),
        verticalArrangement = Arrangement.spacedBy(Dims.m),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dims.s),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (vertexCount == 0) R.string.sheet_title_empty else R.string.sheet_title,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (vertexCount < GeoMath.MIN_VERTICES) {
                        stringResource(R.string.sheet_sub_need_more, GeoMath.MIN_VERTICES - vertexCount)
                    } else {
                        stringResource(R.string.sheet_sub_ready)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SheetStepDots(vertexCount)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dims.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryAction(
                icon = SubsurfIcons.Undo,
                label = stringResource(R.string.action_undo),
                enabled = vertexCount > 0 && !busy,
                onClick = onUndo,
                modifier = Modifier.weight(1f),
            )
            SecondaryAction(
                icon = SubsurfIcons.Minus,
                label = stringResource(R.string.action_clear),
                enabled = vertexCount > 0 && !busy,
                onClick = onClear,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onAnalyze,
                enabled = canAnalyze,
                modifier = Modifier
                    .weight(1.45f)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        SubsurfIcons.Sparkle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    stringResource(if (busy) R.string.analyzing_short else R.string.action_analyze),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }

        if (vertexCount in 1 until GeoMath.MIN_VERTICES) {
            Text(
                stringResource(R.string.need_three_vertices),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetStepDots(vertexCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(GeoMath.MAX_VERTICES) { index ->
            val filled = index < vertexCount
            Box(
                Modifier
                    .size(width = if (filled) 12.dp else 5.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            filled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SecondaryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Column(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

internal fun formatNumber(value: Double, digits: Int): String =
    String.format(java.util.Locale.US, "%.${digits}f", value)

