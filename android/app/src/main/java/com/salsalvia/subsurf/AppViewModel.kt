package com.salsalvia.subsurf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salsalvia.subsurf.data.ApiClient
import com.salsalvia.subsurf.data.CatalogRequest
import com.salsalvia.subsurf.data.SettingsStore
import com.salsalvia.subsurf.data.RegionRequest
import com.salsalvia.subsurf.data.CatalogResponse
import com.salsalvia.subsurf.data.TerrainResponse
import com.salsalvia.subsurf.data.ValidationResponse
import com.salsalvia.subsurf.geo.GeoMath
import com.salsalvia.subsurf.geo.Vertex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class UiState(
    val vertices: List<Vertex> = emptyList(),
    val apiBaseUrl: String = "",
    val busy: Boolean = false,
    val terrainBusy: Boolean = false,
    val message: String? = null,
    val validation: ValidationResponse? = null,
    val catalog: CatalogResponse? = null,
    val terrain: TerrainResponse? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val _state = MutableStateFlow(UiState(apiBaseUrl = settings.apiBaseUrl))
    val state: StateFlow<UiState> = _state

    private fun ctx() = getApplication<Application>()
    private fun res(id: Int): String = ctx().getString(id)
    private fun res(id: Int, vararg args: Any?): String = ctx().getString(id, *args)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun addVertex(lat: Double, lon: Double) {
        _state.update { s ->
            when {
                s.vertices.size >= GeoMath.MAX_VERTICES ->
                    s.copy(message = res(R.string.max_vertices))
                else -> s.copy(
                    vertices = s.vertices + Vertex(lat, lon),
                    validation = null,
                    catalog = null,
                    terrain = null,
                )
            }
        }
    }

    fun undo() = _state.update {
        it.copy(
            vertices = it.vertices.dropLast(1),
            validation = null,
            catalog = null,
            terrain = null,
        )
    }

    fun clear() = _state.update {
        it.copy(vertices = emptyList(), validation = null, catalog = null, terrain = null)
    }

    fun saveBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        settings.apiBaseUrl = normalized
        _state.update { it.copy(apiBaseUrl = normalized) }
    }

    private fun polygonPayload(): List<List<Double>> =
        _state.value.vertices.map { listOf(it.lon, it.lat) }

    fun analyze() {
        val current = _state.value
        if (current.vertices.size < GeoMath.MIN_VERTICES) {
            _state.update { it.copy(message = res(R.string.need_three_vertices)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val payload = polygonPayload()

            // Offline guard first — no network request for a broken sketch.
            if (!GeoMath.localCheck(payload)) {
                _state.update {
                    it.copy(busy = false, message = res(R.string.offline_valid_bad))
                }
                return@launch
            }
            try {
                val api = ApiClient.api(_state.value.apiBaseUrl)
                val validation = api.validate(RegionRequest(polygon = payload))
                if (!validation.valid) {
                    _state.update { it.copy(busy = false, validation = validation) }
                    return@launch
                }
                val today = LocalDate.now()
                val catalog = api.catalogSearch(
                    CatalogRequest(
                        polygon = payload,
                        dateFrom = today.minusDays(90).toString(),
                        dateTo = today.toString(),
                        maxCloudPercent = 30.0,
                        maxResults = 12,
                    )
                )
                _state.update {
                    it.copy(busy = false, validation = validation, catalog = catalog)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, message = res(R.string.error_format, userMessage(e)))
                }
            }
        }
    }

    fun computeTerrain() {
        val payload = polygonPayload()
        if (payload.size < GeoMath.MIN_VERTICES) return
        viewModelScope.launch {
            _state.update { it.copy(terrainBusy = true, message = null) }
            try {
                val terrain = ApiClient.api(_state.value.apiBaseUrl)
                    .terrainIndicators(RegionRequest(polygon = payload))
                _state.update { it.copy(terrainBusy = false, terrain = terrain) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        terrainBusy = false,
                        message = res(R.string.error_format, userMessage(e)),
                    )
                }
            }
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val health = ApiClient.api(_state.value.apiBaseUrl).health()
                onResult(true, health.version)
            } catch (e: Exception) {
                onResult(false, userMessage(e))
            }
        }
    }

    fun formatArea(vertices: List<Vertex>): String {
        val areaSqm = GeoMath.sphericalAreaM2(GeoMath.normalizeRing(vertices))
        return if (areaSqm >= 10_000.0) {
            String.format(Locale.US, "%.1f ha", areaSqm / 10_000.0)
        } else {
            String.format(Locale.US, "%.0f m²", areaSqm)
        }
    }

    private fun userMessage(e: Exception): String =
        e.message ?: e.javaClass.simpleName
}
