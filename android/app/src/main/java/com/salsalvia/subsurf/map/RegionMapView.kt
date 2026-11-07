package com.salsalvia.subsurf.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.salsalvia.subsurf.geo.Vertex
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val STYLE_URL = "https://demotiles.maplibre.org/style.json"
private const val SRC = "subsurf-region-src"
private const val LAYER_FILL = "subsurf-region-fill"
private const val LAYER_LINE = "subsurf-region-line"
private const val LAYER_POINTS = "subsurf-region-points"

private const val DEFAULT_LAT = 35.6892
private const val DEFAULT_LON = 51.3890
private const val DEFAULT_ZOOM = 9.5

/**
 * Bridges the Compose world to the MapLibre view. Holds the GeoJsonSource and
 * pushes the sketched ring into it; tolerates style reloads (pending vertices
 * are re-applied once the style is ready).
 */
class RegionMapController {

    var onTap: ((Double, Double) -> Unit)? = null
    private var source: GeoJsonSource? = null
    private var pending: List<Vertex> = emptyList()

    fun onMapReady(map: MapLibreMap) {
        map.setStyle(
            Style.Builder().fromUri(STYLE_URL),
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    installLayers(style)
                }
            }
        )
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), DEFAULT_ZOOM)
        )
        map.addOnMapClickListener(
            MapLibreMap.OnMapClickListener { point ->
                onTap?.invoke(point.latitude, point.longitude)
                true
            }
        )
    }

    private fun installLayers(style: Style) {
        style.addSource(GeoJsonSource(SRC).also { source = it })
        style.addLayer(
            FillLayer(LAYER_FILL, SRC).withProperties(
                fillColor(Color.argb(40, 20, 99, 86)),
                fillOutlineColor(Color.argb(255, 20, 99, 86)),
            )
        )
        style.addLayer(
            LineLayer(LAYER_LINE, SRC).withProperties(
                lineColor(Color.rgb(20, 99, 86)),
                lineWidth(2.0f),
            )
        )
        style.addLayer(
            CircleLayer(LAYER_POINTS, SRC).withProperties(
                circleColor(Color.rgb(20, 99, 86)),
                circleRadius(5.0f),
                circleStrokeWidth(2.0f),
                circleStrokeColor(Color.WHITE),
            )
        )
        push(pending)
    }

    fun push(vertices: List<Vertex>) {
        pending = vertices
        val src = source ?: return
        val points = vertices.map { Point.fromLngLat(it.lon, it.lat) }
        val features = ArrayList<Feature>(points.size + 1)
        if (points.size >= 3) {
            features.add(
                Feature.fromGeometry(Polygon.fromLngLats(listOf(points + points.first())))
            )
        }
        for (p in points) features.add(Feature.fromGeometry(p))
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}

@Composable
fun RegionMapView(
    vertices: List<Vertex>,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = remember { RegionMapController() }
    val currentTap by rememberUpdatedState(onMapTap)
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                onCreate(null)
                onStart()
                onResume()
                getMapAsync(OnMapReadyCallback { map -> controller.onMapReady(map) })
                mapView = this
            }
        },
        update = { view ->
            controller.onTap = { lat, lon -> currentTap(lat, lon) }
            controller.push(vertices)
        },
    )

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
