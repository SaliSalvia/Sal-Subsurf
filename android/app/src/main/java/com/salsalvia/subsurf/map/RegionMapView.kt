package com.salsalvia.subsurf.map

import android.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.salsalvia.subsurf.geo.Vertex
import com.salsalvia.subsurf.ui.icons.SubsurfIcons
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Free, key-less vector basemaps (OpenFreeMap, OSM-derived). The demo style that used to be
 * here shipped no real cartography, which made the map look unfinished; these render proper
 * roads, water and land cover.
 */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val SRC = "subsurf-region-src"
private const val LAYER_FILL = "subsurf-region-fill"
private const val LAYER_OPEN = "subsurf-region-open"
private const val LAYER_LINE = "subsurf-region-line"
private const val LAYER_HALO = "subsurf-region-halo"
private const val LAYER_POINTS = "subsurf-region-points"

private const val DEFAULT_LAT = 35.6892
private const val DEFAULT_LON = 51.3890
private const val DEFAULT_ZOOM = 9.5

// Brand teal as raw ARGB ints, mirrored from the Compose palette (drawing code is not Compose).
private const val BRAND = 0xFF14705F.toInt()
private const val BRAND_LIGHT = 0xFF3FB39B.toInt()

/**
 * Bridges the Compose world to the MapLibre view. Owns the GeoJSON source and pushes the
 * sketched geometry into it; tolerates style reloads by replaying the last vertices once the
 * style is ready, so a theme switch never loses the sketch.
 */
class RegionMapController {

    var onTap: ((Double, Double) -> Unit)? = null

    private var map: MapLibreMap? = null
    private var source: GeoJsonSource? = null
    private var pending: List<Vertex> = emptyList()
    private var darkStyle: Boolean? = null
    private var brandArgb: Int = BRAND
    private var lastPushed: List<Vertex>? = null

    fun onMapReady(map: MapLibreMap, dark: Boolean) {
        this.map = map
        darkStyle = dark
        brandArgb = if (dark) BRAND_LIGHT else BRAND
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), DEFAULT_ZOOM)
        )
        map.addOnMapClickListener(
            MapLibreMap.OnMapClickListener { point ->
                onTap?.invoke(point.latitude, point.longitude)
                true
            }
        )
        loadStyle(dark)
    }

    /** Swaps the basemap when the system theme changes, keeping the sketch intact. */
    fun syncTheme(dark: Boolean) {
        if (map == null || darkStyle == dark) return
        darkStyle = dark
        brandArgb = if (dark) BRAND_LIGHT else BRAND
        loadStyle(dark)
    }

    private fun loadStyle(dark: Boolean) {
        val current = map ?: return
        source = null
        lastPushed = null
        current.setStyle(
            Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT),
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    installLayers(style)
                }
            },
        )
    }

    private fun installLayers(style: Style) {
        // installLayers only ever runs on a freshly loaded style, so there is nothing to remove.
        val src = GeoJsonSource(SRC)
        style.addSource(src)
        source = src

        style.addLayer(
            FillLayer(LAYER_FILL, SRC).withProperties(
                fillColor(Color.argb(46, Color.red(brandArgb), Color.green(brandArgb), Color.blue(brandArgb))),
                fillOutlineColor(Color.TRANSPARENT),
            ),
        )
        // Open polyline while the ring is still being drawn.
        style.addLayer(
            LineLayer(LAYER_OPEN, SRC).withProperties(
                lineColor(brandArgb),
                lineWidth(2.0f),
                lineJoin("round"),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_LINE, SRC).withProperties(
                lineColor(brandArgb),
                lineWidth(2.6f),
                lineJoin("round"),
            ),
        )
        // Soft halo behind each vertex improves the perceived tap target on busy imagery.
        style.addLayer(
            CircleLayer(LAYER_HALO, SRC).withProperties(
                circleColor(brandArgb),
                circleRadius(13.0f),
                circleOpacity(0.20f),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_POINTS, SRC).withProperties(
                circleColor(brandArgb),
                circleRadius(6.5f),
                circleStrokeWidth(2.5f),
                circleStrokeColor(Color.WHITE),
            ),
        )
        push(pending)
    }

    /**
     * Pushes the sketch into the GeoJSON source. Called on every recomposition, so it bails out
     * early when nothing changed — rebuilding the feature collection each frame made the map
     * stutter while the action sheet was animating.
     */
    fun push(vertices: List<Vertex>) {
        pending = vertices
        val src = source ?: return
        if (vertices == lastPushed) return
        lastPushed = vertices
        // Nothing to draw for an empty or single-point sketch beyond the point markers below.
        val points = vertices.map { Point.fromLngLat(it.lon, it.lat) }
        val features = ArrayList<Feature>(points.size + 2)
        if (points.size >= 3) {
            features.add(
                Feature.fromGeometry(Polygon.fromLngLats(listOf(points + points.first())))
            )
        } else if (points.size == 2) {
            features.add(Feature.fromGeometry(LineString.fromLngLats(points)))
        }
        for (p in points) features.add(Feature.fromGeometry(p))
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * Shrinks the usable map viewport by the given pixel insets so that "fit to region" frames
     * the sketch in the area that is not covered by the floating header and action sheet.
     */
    fun setViewInset(top: Int, bottom: Int, start: Int = 0, end: Int = 0) {
        val current = map ?: return
        runCatching { current.setPadding(start, top, end, bottom) }
    }

    /** Frame the whole sketch with a comfortable margin. */
    fun fitTo(vertices: List<Vertex>) {
        val current = map ?: return
        if (vertices.isEmpty()) return
        if (vertices.size == 1) {
            current.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(vertices.first().lat, vertices.first().lon), 14.0,
                ),
                600,
            )
            return
        }
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (v in vertices) {
            if (v.lat < minLat) minLat = v.lat
            if (v.lat > maxLat) maxLat = v.lat
            if (v.lon < minLon) minLon = v.lon
            if (v.lon > maxLon) maxLon = v.lon
        }
        val bounds = LatLngBounds.from(maxLat, maxLon, minLat, minLon)
        val pad = 72
        current.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, pad, pad, pad, pad),
            600,
        )
    }

    /** Zoom helper used by the on-map controls. */
    fun zoomBy(delta: Double) {
        val current = map ?: return
        val target = (current.cameraPosition.zoom + delta).coerceIn(1.0, 20.0)
        current.animateCamera(CameraUpdateFactory.zoomTo(target), 220)
    }
}

/**
 * @param fitRequest increment this counter to animate the camera onto the current sketch.
 */
@Composable
fun RegionMapView(
    vertices: List<Vertex>,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    fitRequest: Int = 0,
    onController: (RegionMapController) -> Unit = {},
    onLoadState: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val controller = remember { RegionMapController() }
    val currentTap by rememberUpdatedState(onMapTap)
    val currentDark by rememberUpdatedState(dark)
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                onCreate(null)
                onStart()
                onResume()
                getMapAsync(
                    OnMapReadyCallback { map ->
                        map.uiSettings.apply {
                            isAttributionEnabled = false // a permanent credit line lives in the UI
                            isLogoEnabled = false
                            isCompassEnabled = false
                            isRotateGesturesEnabled = false
                            isTiltGesturesEnabled = false
                        }
                        controller.onMapReady(map, currentDark)
                        onController(controller)
                    },
                )
                addOnDidFinishLoadingMapListener { onLoadState(true) }
                addOnDidFailLoadingMapListener { onLoadState(true) }
                mapView = this
            }
        },
        update = { view ->
            controller.onTap = { lat, lon -> currentTap(lat, lon) }
            controller.push(vertices)
            controller.syncTheme(dark)
            view.onResume()
        },
    )

    LaunchedEffect(fitRequest, mapView) {
        if (fitRequest > 0 && mapView != null) {
            // Give the surface a frame so bounds fitting uses the real viewport.
            kotlinx.coroutines.delay(320)
            controller.fitTo(vertices)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.let { view ->
                view.onPause()
                view.onStop()
                view.onDestroy()
            }
            mapView = null
        }
    }
}

/** Simple on-map zoom pair; kept tiny and translucent so it never competes with the sheet. */
@Composable
fun MapZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        ZoomButton(SubsurfIcons.Plus, "Zoom in", onZoomIn)
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        ZoomButton(SubsurfIcons.Minus, "Zoom out", onZoomOut)
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Soft loading veil shown until the first style has rendered. */
@Composable
fun MapLoadingOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Loading map", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
