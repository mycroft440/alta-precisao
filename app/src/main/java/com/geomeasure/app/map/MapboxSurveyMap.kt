package com.geomeasure.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geomeasure.app.R
import com.geomeasure.app.geodesy.Wgs84
import com.geomeasure.app.gnss.GnssSnapshot
import com.geomeasure.app.model.SurveyPoint
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.generated.rasterDemSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.extension.style.terrain.generated.terrain
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions

@Composable
fun MapboxSurveyMap(
    modifier: Modifier = Modifier,
    points: List<SurveyPoint>,
    gnss: GnssSnapshot,
    displayMode: MapDisplayMode,
    terrainEnabled: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val token = stringResource(R.string.mapbox_access_token)
    if (!token.startsWith("pk.")) {
        MapTokenPlaceholder(modifier)
        return
    }

    val mapView = remember(token) {
        MapboxOptions.accessToken = token
        MapView(context).also { view -> view.tag = SurveyMapController(view) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false

        fun startIfNeeded() {
            if (!started) {
                mapView.onStart()
                started = true
            }
        }

        fun resumeIfNeeded() {
            startIfNeeded()
            if (!resumed) {
                mapView.onResume()
                resumed = true
            }
        }

        fun pauseIfNeeded() {
            if (resumed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stopIfNeeded() {
            pauseIfNeeded()
            if (started) {
                mapView.onStop()
                started = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startIfNeeded()
                Lifecycle.Event.ON_RESUME -> resumeIfNeeded()
                Lifecycle.Event.ON_PAUSE -> pauseIfNeeded()
                Lifecycle.Event.ON_STOP -> stopIfNeeded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startIfNeeded()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeIfNeeded()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopIfNeeded()
            (mapView.tag as? SurveyMapController)?.dispose()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            (view.tag as? SurveyMapController)?.update(points, gnss, displayMode, terrainEnabled)
        },
    )
}

@Composable
private fun MapTokenPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF101820))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.height(10.dp))
            Text("Mapa 3D preparado", style = MaterialTheme.typography.titleMedium)
            Text(
                "Defina MAPBOX_ACCESS_TOKEN=pk.* em ~/.gradle/gradle.properties, local.properties/Gradle ou variável de ambiente para carregar satélite e terreno 3D.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private class SurveyMapController(private val mapView: MapView) {
    private val mapboxMap = mapView.mapboxMap
    private val pointManager: CircleAnnotationManager = mapView.annotations.createCircleAnnotationManager()
    private val currentManager: CircleAnnotationManager = mapView.annotations.createCircleAnnotationManager()
    private val lineManager: PolylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager()
    private val polygonManager: PolygonAnnotationManager = mapView.annotations.createPolygonAnnotationManager()

    private var lastMode: MapDisplayMode? = null
    private var lastTerrain: Boolean? = null
    private var cameraInitialized = false
    private var lastCameraTerrain: Boolean? = null
    private var lastCameraPointKey: CameraPointKey? = null
    private var renderedPoints: List<SurveyPoint> = emptyList()
    private var surveyAnnotationsDirty = true
    private var renderedFixKey: CurrentFixKey? = null
    private var styleReady = false

    private var latestPoints: List<SurveyPoint> = emptyList()
    private var latestGnss: GnssSnapshot = GnssSnapshot()
    private var latestTerrainEnabled: Boolean = false

    fun update(
        points: List<SurveyPoint>,
        gnss: GnssSnapshot,
        mode: MapDisplayMode,
        terrainEnabled: Boolean,
    ) {
        latestPoints = points
        latestGnss = gnss
        latestTerrainEnabled = terrainEnabled

        if (mode != lastMode || terrainEnabled != lastTerrain) {
            lastMode = mode
            lastTerrain = terrainEnabled
            loadStyle(mode, terrainEnabled)
            return
        }
        if (styleReady) renderLatestState()
    }

    private fun loadStyle(mode: MapDisplayMode, terrainEnabled: Boolean) {
        styleReady = false
        surveyAnnotationsDirty = true
        renderedFixKey = null
        val styleUri = if (mode == MapDisplayMode.SATELLITE) Style.STANDARD_SATELLITE else Style.STANDARD
        val onLoaded = Style.OnStyleLoaded {
            styleReady = true
            // Runtime annotations are style-bound; rebuild them only after the replacement style
            // has fully loaded, otherwise a late style load can erase freshly-created vertices.
            renderLatestState()
        }
        if (terrainEnabled) {
            mapboxMap.loadStyle(
                style(style = styleUri) {
                    +rasterDemSource(TERRAIN_SOURCE_ID) {
                        url(TERRAIN_URL)
                        tileSize(514)
                    }
                    +terrain(TERRAIN_SOURCE_ID) {
                        exaggeration(1.0)
                    }
                },
                onLoaded,
            )
        } else {
            mapboxMap.loadStyle(styleUri, onLoaded)
        }
    }

    private fun renderLatestState() {
        if (surveyAnnotationsDirty || renderedPoints != latestPoints) {
            updateSurveyAnnotations(latestPoints)
            renderedPoints = latestPoints.toList()
            surveyAnnotationsDirty = false
        }
        updateCurrentFix(latestGnss)
        updateCamera(latestPoints, latestGnss, latestTerrainEnabled)
    }

    private fun updateSurveyAnnotations(points: List<SurveyPoint>) {
        pointManager.deleteAll()
        lineManager.deleteAll()
        polygonManager.deleteAll()

        if (points.isEmpty()) return
        val mapPoints = points.map { Point.fromLngLat(it.longitudeDeg, it.latitudeDeg) }
        pointManager.create(
            mapPoints.map {
                CircleAnnotationOptions()
                    .withPoint(it)
                    .withCircleRadius(7.0)
                    .withCircleColor("#39E27D")
                    .withCircleStrokeColor("#FFFFFF")
                    .withCircleStrokeWidth(2.0)
            },
        )

        val boundaryValid = if (points.size >= 3) {
            Wgs84.measurePolygon(
                points.map { Wgs84.Geo(it.latitudeDeg, it.longitudeDeg, it.ellipsoidalHeightM ?: 0.0) },
            ).isValid
        } else true

        if (mapPoints.size >= 2) {
            val linePoints = if (mapPoints.size >= 3 && boundaryValid) mapPoints + mapPoints.first() else mapPoints
            lineManager.create(
                PolylineAnnotationOptions()
                    .withPoints(linePoints)
                    .withLineColor(if (boundaryValid) "#39E27D" else "#FF6B6B")
                    .withLineWidth(4.0),
            )
        }
        if (mapPoints.size >= 3 && boundaryValid) {
            val ring = mapPoints + mapPoints.first()
            polygonManager.create(
                PolygonAnnotationOptions()
                    .withPoints(listOf(ring))
                    .withFillColor("#39E27D")
                    .withFillOpacity(0.18),
            )
        }
    }

    private fun updateCurrentFix(gnss: GnssSnapshot) {
        val key = CurrentFixKey(gnss.hasFix, gnss.elapsedRealtimeNanos, gnss.latitudeDeg, gnss.longitudeDeg)
        if (key == renderedFixKey) return
        renderedFixKey = key
        currentManager.deleteAll()
        if (!gnss.hasFix) return
        val lat = gnss.latitudeDeg ?: return
        val lon = gnss.longitudeDeg ?: return
        currentManager.create(
            CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(lon, lat))
                .withCircleRadius(6.0)
                .withCircleColor("#2D8CFF")
                .withCircleStrokeColor("#FFFFFF")
                .withCircleStrokeWidth(2.0),
        )
    }

    private fun updateCamera(points: List<SurveyPoint>, gnss: GnssSnapshot, terrainEnabled: Boolean) {
        val pitch = if (terrainEnabled) 58.0 else 0.0
        if (lastCameraTerrain != terrainEnabled && cameraInitialized) {
            // A terrain toggle reloads the style without changing the point count, so update the
            // camera pitch explicitly instead of leaving a stale 3D/2D viewing angle.
            mapboxMap.setCamera(CameraOptions.Builder().pitch(pitch).build())
        }
        lastCameraTerrain = terrainEnabled
        val lastPoint = points.lastOrNull()
        val pointKey = lastPoint?.let {
            CameraPointKey(it.projectId, it.databaseId, it.id, it.latitudeDeg, it.longitudeDeg)
        }
        if (pointKey != null && pointKey != lastCameraPointKey) {
            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(lastPoint.longitudeDeg, lastPoint.latitudeDeg))
                    .zoom(18.0)
                    .pitch(pitch)
                    .build(),
            )
            lastCameraPointKey = pointKey
            cameraInitialized = true
            return
        }
        if (lastPoint == null) lastCameraPointKey = null
        if (!cameraInitialized && points.isEmpty() && gnss.hasFix) {
            val lat = gnss.latitudeDeg ?: return
            val lon = gnss.longitudeDeg ?: return
            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(lon, lat))
                    .zoom(17.5)
                    .pitch(if (terrainEnabled) 50.0 else 0.0)
                    .build(),
            )
            cameraInitialized = true
        }
    }

    fun dispose() {
        pointManager.deleteAll()
        currentManager.deleteAll()
        lineManager.deleteAll()
        polygonManager.deleteAll()
    }

    private data class CameraPointKey(
        val projectId: Long,
        val databaseId: Long,
        val sequence: Int,
        val latitude: Double,
        val longitude: Double,
    )

    private data class CurrentFixKey(
        val valid: Boolean,
        val elapsedRealtimeNanos: Long,
        val latitude: Double?,
        val longitude: Double?,
    )

    private companion object {
        const val TERRAIN_SOURCE_ID = "geomeasure-terrain-dem"
        const val TERRAIN_URL = "mapbox://mapbox.mapbox-terrain-dem-v1"
    }
}
