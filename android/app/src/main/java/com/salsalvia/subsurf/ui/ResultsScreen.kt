package com.salsalvia.subsurf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salsalvia.subsurf.AppViewModel
import com.salsalvia.subsurf.R
import com.salsalvia.subsurf.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: AppViewModel, state: UiState) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.results_title)) })
        if (state.validation == null && state.catalog == null && state.terrain == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.results_empty), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ValidationCard(state) }
            state.catalog?.let { catalog ->
                item { CatalogCard(catalog.totalMatched, catalog.products) }
                if (catalog.warnings.isNotEmpty()) item { WarningsCard(catalog.warnings) }
                if (catalog.provenance.isNotEmpty()) item { ProvenanceCard(catalog.provenance.map { it.provider }) }
            }
            item { TerrainCard(vm, state) }
            state.terrain?.let { terrain ->
                if (terrain.provenance.isNotEmpty()) {
                    item { ProvenanceCard(terrain.provenance.map { it.provider }) }
                }
                item { CaveatsCard(terrain.caveats) }
            }
            item { Spacer(Modifier.size(12.dp)) }
        }
    }
}

@Composable
private fun ValidationCard(state: UiState) {
    val v = state.validation ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.validation_card), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (v.valid) R.string.valid_region else R.string.invalid_region),
                color = if (v.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            if (!v.valid) {
                v.issues.forEach { issue ->
                    Text("• $issue", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(R.string.vertices_format, v.vertexCount) + " · " +
                    stringResource(R.string.area_hectares_format, fmt(v.areaHectares, 1)) + " · " +
                    stringResource(R.string.perimeter_format, fmt(v.perimeterM, 0)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CatalogCard(totalMatched: Int, products: List<com.salsalvia.subsurf.data.ProductSummary>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.catalog_card), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.products_found, products.size) +
                    " · " + stringResource(R.string.total_matched_format, totalMatched),
                style = MaterialTheme.typography.bodySmall,
            )
            if (products.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyMedium)
            }
            products.forEach { p ->
                HorizontalDivider()
                Column {
                    Text(p.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Text(
                        buildString {
                            append(p.datetime?.take(10) ?: "—")
                            p.cloudCoverPercent?.let {
                                append(" · ").append(stringResource(R.string.cloud_cover_format, fmt(it, 1)))
                            }
                            p.sizeMb?.let {
                                append(" · ").append(stringResource(R.string.size_format, fmt(it, 0)))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerrainCard(vm: AppViewModel, state: UiState) {
    val t = state.terrain
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.terrain_card), style = MaterialTheme.typography.titleMedium)
            if (t == null) {
                Button(onClick = { vm.computeTerrain() }, enabled = !state.terrainBusy) {
                    if (state.terrainBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(if (state.terrainBusy) R.string.computing else R.string.compute_terrain))
                }
                return@Column
            }
            Text(
                stringResource(
                    R.string.elevation_format,
                    fmt(t.elevation.minM, 0), fmt(t.elevation.maxM, 0),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.slope_mean_format, fmt(t.slope.meanDeg, 1)) + " · " +
                    stringResource(R.string.slope_max_format, fmt(t.slope.maxDeg, 1)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.roughness_format, fmt(t.roughnessTriMean, 2)) + " · " +
                    stringResource(R.string.resolution_format, fmt(t.resolutionMPerPx, 1)),
                style = MaterialTheme.typography.bodySmall,
            )
            t.dominantGradientAzimuthDeg?.let { az ->
                Text(
                    stringResource(R.string.gradient_consistency_format, fmt(t.gradientConsistency ?: 0.0, 2)) +
                        " · " + com.salsalvia.subsurf.geo.GeoMath.compassLabel(az),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            t.surfaceAnomalyScore?.let { score ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.anomaly_score_format, fmt(score, 3)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                t.scoreComponents.forEach { (name, value) ->
                    Text("$name = ${fmt(value, 3)}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.warnings_title), style = MaterialTheme.typography.titleMedium)
            warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ProvenanceCard(providers: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.provenance_title), style = MaterialTheme.typography.titleMedium)
            providers.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CaveatsCard(caveats: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.caveats_title), style = MaterialTheme.typography.titleMedium)
            caveats.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun fmt(v: Double, digits: Int): String =
    String.format(Locale.US, "%.${digits}f", v)
