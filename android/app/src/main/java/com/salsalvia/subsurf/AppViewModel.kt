package com.salsalvia.subsurf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salsalvia.subsurf.data.ApiClient
import com.salsalvia.subsurf.data.CatalogRequest
import com.salsalvia.subsurf.data.SettingsStore
import com.salsalvia.subsurf.data.ThemeMode
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
    /** Purely presentational: how the user wants light/dark resolved. */
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Human-readable failure of the last collection step, if any (drives the retry UI). */
    val runError: String? = null,
    /** Area text captured when the run started, so the report can echo it. */
    val runAreaLabel: String? = null,
    /**
     * Resolved light/dark flag. Written by the activity once per composition so that screens
     * (and the map basemap) follow the user's theme choice, not just the system setting.
     */
    val isDark: Boolean = true,
    /** Result of the startup reachability probe (`null` = not checked yet). */
    val backendOnline: Boolean? = null,
    val backendChecked: Boolean = false,
) {
    /** True while either network stage of a run is in flight. */
    val isCollecting: Boolean get() = busy || terrainBusy
}


class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val _state = MutableStateFlow(
        UiState(apiBaseUrl = settings.apiBaseUrl, themeMode = settings.themeMode),
    )
    val state: StateFlow<UiState> = _state

    private fun ctx() = getApplication<Application>()
    private fun res(id: Int): String = ctx().getString(id)
    private fun res(id: Int, vararg args: Any?): String = ctx().getString(id, *args)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Probes the configured backend once at startup so the UI can explain itself when nothing is
     * reachable instead of failing later on the first collection run.
     */
    fun probeBackend() {
        viewModelScope.launch {
            _state.update { it.copy(backendChecked = true) }
            try {
                ApiClient.api(_state.value.apiBaseUrl).health()
                _state.update { it.copy(backendOnline = true) }
            } catch (e: Exception) {
                _state.update { it.copy(backendOnline = false) }
            }
        }
    }

    /** Stores the resolved appearance so the map can pick its basemap. */
    fun applyResolvedTheme(dark: Boolean) {
        if (_state.value.isDark != dark) _state.update { it.copy(isDark = dark) }
    }

    /** Clears a failed run so the retry button can be pressed again. */
    fun dismissError() = _state.update { it.copy(runError = null) }

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

    /** Cycles / selects the appearance mode; the choice is remembered locally. */
    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
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
            _state.update {
                it.copy(
                    busy = true,
                    message = null,
                    runError = null,
                    terrain = null,
                    runAreaLabel = formatArea(_state.value.vertices),
                )
            }
            val payload = polygonPayload()

            // Offline guard first — no network request for a broken sketch.
            if (!GeoMath.localCheck(payload)) {
                _state.update {
                    it.copy(
                        busy = false,
                        runError = res(R.string.offline_valid_bad),
                        message = res(R.string.offline_valid_bad),
                    )
                }
                return@launch
            }
            try {
                val api = ApiClient.api(_state.value.apiBaseUrl)
                val validation = api.validate(RegionRequest(polygon = payload))
                if (!validation.valid) {
                    _state.update {
                        it.copy(
                            busy = false,
                            validation = validation,
                            runError = validation.issues.firstOrNull()
                                ?: res(R.string.invalid_region),
                        )
                    }
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
                val detail = userMessage(e)
                _state.update {
                    it.copy(
                        busy = false,
                        runError = detail,
                        message = res(R.string.error_format, detail),
                    )
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
                val detail = userMessage(e)
                _state.update {
                    it.copy(
                        terrainBusy = false,
                        runError = detail,
                        message = res(R.string.error_format, detail),
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

    /**
     * Turns transport-level exceptions into something a user can act on, instead of raw
     * "Failed to connect to /10.0.2.2:8000" strings.
     */
    private fun userMessage(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> res(R.string.error_no_host, e.message ?: "")
        is java.net.SocketTimeoutException -> res(R.string.error_timeout)
        is java.net.ConnectException -> res(R.string.error_unreachable)
        is java.io.IOException -> e.message ?: res(R.string.error_network)
        else -> e.message ?: e.javaClass.simpleName
    }
}
