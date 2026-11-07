package com.salsalvia.subsurf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salsalvia.subsurf.AppViewModel
import com.salsalvia.subsurf.R
import com.salsalvia.subsurf.UiState
import com.salsalvia.subsurf.map.RegionMapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: AppViewModel, state: UiState, onAnalyzed: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.map_title)) })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            RegionMapView(
                vertices = state.vertices,
                onMapTap = { lat, lon -> vm.addVertex(lat, lon) },
                modifier = Modifier.fillMaxSize(),
            )
            Card(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.vertices_format, state.vertices.size),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (state.vertices.size >= 3) {
                        Text(
                            stringResource(R.string.area_format, vm.formatArea(state.vertices)),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    } else {
                        Text(
                            stringResource(R.string.tap_to_add_vertex),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { vm.undo() },
                enabled = state.vertices.isNotEmpty(),
            ) { Text(stringResource(R.string.action_undo)) }
            OutlinedButton(
                onClick = { vm.clear() },
                enabled = state.vertices.isNotEmpty(),
            ) { Text(stringResource(R.string.action_clear)) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.analyze() },
                enabled = !state.busy && state.vertices.size >= 3,
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(if (state.busy) R.string.analyzing else R.string.action_analyze))
            }
        }
    }

    // Jump to the Results tab as soon as the server validation arrives.
    LaunchedEffect(state.validation) {
        if (state.validation != null && !state.busy) onAnalyzed()
    }
}
