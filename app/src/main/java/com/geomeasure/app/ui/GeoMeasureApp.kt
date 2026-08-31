package com.geomeasure.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geomeasure.app.geodesy.Wgs84
import com.geomeasure.app.gnss.GnssQualityEvaluator
import com.geomeasure.app.gnss.GnssSnapshot
import com.geomeasure.app.map.MapDisplayMode
import com.geomeasure.app.map.MapboxSurveyMap
import com.geomeasure.app.model.PointQuality
import com.geomeasure.app.model.SurveyPoint
import com.geomeasure.app.model.SurveyProject
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoMeasureApp(viewModel: SurveyViewModel) {
    val gnss by viewModel.gnss.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val capture by viewModel.captureProgress.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionGranted by remember { mutableStateOf(viewModel.hasPermission()) }
    var showProjects by remember { mutableStateOf(false) }
    var mapMode by remember { mutableStateOf(MapDisplayMode.SATELLITE) }
    var terrainEnabled by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (permissionGranted) viewModel.startGnss()
    }
    val requestPreciseLocation: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    DisposableEffect(lifecycleOwner, permissionGranted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val granted = viewModel.hasPermission()
                    permissionGranted = granted
                    if (granted) viewModel.startGnss()
                }
                Lifecycle.Event.ON_STOP -> viewModel.stopGnss()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            val granted = viewModel.hasPermission()
            permissionGranted = granted
            if (granted) viewModel.startGnss()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopGnss()
        }
    }

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearUiMessage()
    }

    if (showProjects) {
        ProjectsDialog(
            projects = projects,
            currentProject = currentProject,
            onOpen = {
                viewModel.openProject(it)
                showProjects = false
            },
            onCreate = {
                viewModel.createProject(it)
                showProjects = false
            },
            onDismiss = { showProjects = false },
        )
    }

    val quality = viewModel.quality()
    val captureAllowed = GnssQualityEvaluator.isCaptureQualityAllowed(quality)
    val measurement = remember(points) {
        Wgs84.measurePolygon(
            points.map { Wgs84.Geo(it.latitudeDeg, it.longitudeDeg, it.ellipsoidalHeightM ?: 0.0) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GeoMeasure", fontWeight = FontWeight.Bold)
                        Text(currentProject?.name ?: "Carregando projeto…", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showProjects = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Projetos")
                    }
                    IconButton(onClick = viewModel::removeLastPoint, enabled = points.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "Desfazer")
                    }
                    IconButton(onClick = viewModel::clearSurvey, enabled = points.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Limpar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!permissionGranted) {
                PermissionCard(requestPreciseLocation)
            } else {
                QualityHeader(
                    quality = quality,
                    gnss = gnss,
                )
            }

            MapControls(
                mode = mapMode,
                terrainEnabled = terrainEnabled,
                onMode = { mapMode = it },
                onTerrain = { terrainEnabled = it },
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                MapboxSurveyMap(
                    modifier = Modifier.fillMaxSize(),
                    points = points,
                    gnss = gnss,
                    displayMode = mapMode,
                    terrainEnabled = terrainEnabled,
                )
            }

            SurveySummary(
                points = points.size,
                measurement = measurement,
            )

            points.lastOrNull()?.let { LastPointCard(it, viewModel) }

            if (capture != null) {
                val progress = capture!!
                val sampleRatio = progress.acceptedSamples.toFloat() / progress.requiredSamples.toFloat()
                val timeRatio = if (progress.requiredObservationMillis > 0L) {
                    progress.observationSpanMillis.toFloat() / progress.requiredObservationMillis.toFloat()
                } else 1f
                LinearProgressIndicator(
                    progress = { minOf(sampleRatio, timeRatio).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Estabilizando: ${progress.acceptedSamples}/${progress.requiredSamples} • " +
                            "${(progress.observationSpanMillis / 1000.0).fmt(1)} s",
                    )
                    TextButton(onClick = viewModel::cancelCapture) { Text("Cancelar") }
                }
            }

            Button(
                onClick = {
                    if (!permissionGranted) requestPreciseLocation()
                    else viewModel.startPointCapture()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = capture == null && permissionGranted && captureAllowed && currentProject != null,
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("MARCAR PONTO", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MapControls(
    mode: MapDisplayMode,
    terrainEnabled: Boolean,
    onMode: (MapDisplayMode) -> Unit,
    onTerrain: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = mode == MapDisplayMode.SATELLITE,
            onClick = { onMode(MapDisplayMode.SATELLITE) },
            label = { Text("Satélite") },
        )
        FilterChip(
            selected = mode == MapDisplayMode.STANDARD,
            onClick = { onMode(MapDisplayMode.STANDARD) },
            label = { Text("Mapa") },
        )
        Spacer(Modifier.weight(1f))
        FilterChip(
            selected = terrainEnabled,
            onClick = { onTerrain(!terrainEnabled) },
            label = { Text(if (terrainEnabled) "3D ligado" else "3D desligado") },
        )
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Acesso GNSS necessário", fontWeight = FontWeight.Bold)
            Text("A medição exige localização exata. No Android 12+, selecione Precisa/Exata quando o sistema perguntar.")
            Button(onClick = onRequest) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Permitir localização precisa")
            }
        }
    }
}

@Composable
private fun QualityHeader(quality: PointQuality, gnss: GnssSnapshot) {
    val qualityText = when {
        gnss.isMock -> "LOCALIZAÇÃO SIMULADA"
        !gnss.providerEnabled -> "GPS DESATIVADO"
        else -> when (quality) {
            PointQuality.EXCELLENT -> "EXCELENTE"
            PointQuality.GOOD -> "BOA"
            PointQuality.MODERATE -> "MODERADA"
            PointQuality.POOR -> "RUIM"
            PointQuality.REJECTED -> "NÃO MEDIR"
        }
    }
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(qualityText, fontWeight = FontWeight.Bold)
                Text("H r68 ${gnss.horizontalAccuracyM?.fmt(2) ?: "—"} m")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SAT usados: ${gnss.satellitesUsed}")
                Text("V 68% ${gnss.verticalAccuracyM?.fmt(2) ?: "—"} m")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("C/N0: ${gnss.averageCn0DbHz?.fmt(1) ?: "—"} dB-Hz")
                Text(
                    "RAW: ${gnss.rawMeasurements}" +
                        (if (gnss.dualFrequencySignals > 0) " • ${gnss.dualFrequencySignals} sat. multifreq." else "") +
                        (if (gnss.rawLogPath != null) " • LOG" else ""),
                )
            }
            gnss.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } ?: if (gnss.isMock) {
                Text("Pontos de localização simulada são rejeitados.", color = MaterialTheme.colorScheme.error)
            } else if (!gnss.providerEnabled) {
                Text("Ative a localização/GPS do aparelho para medir.", color = MaterialTheme.colorScheme.error)
            } else if (!GnssQualityEvaluator.isCaptureQualityAllowed(quality)) {
                Text(
                    "Aguarde qualidade BOA ou EXCELENTE. Amostras MODERADAS/RUINS não entram no levantamento.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Unit
            }
        }
    }
}

@Composable
private fun SurveySummary(points: Int, measurement: Wgs84.PolygonMeasurement) {
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Pontos", points.toString())
                if (measurement.isValid) {
                    val area = measurement.areaSquareMeters
                    Metric("Área", if (area >= 10_000) "${(area / 10_000).fmt(4)} ha" else "${area.fmt(2)} m²")
                    Metric(if (points == 2) "Distância" else "Perímetro", "${measurement.perimeterMeters.fmt(2)} m")
                } else {
                    Metric("Área", "INVÁLIDA")
                    Metric("Perímetro", "—")
                }
            }
            measurement.issue?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LastPointCard(point: SurveyPoint, viewModel: SurveyViewModel) {
    val utm = remember(point) { viewModel.utm(point) }
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P${point.id} • UTM ${utm.zoneLabel}", fontWeight = FontWeight.Bold)
                Text(point.quality.name)
            }
            Text("E ${utm.eastingM.fmt(3)}  •  N ${utm.northingM.fmt(3)}")
            Text("Lat ${point.latitudeDeg.fmt(8)}  •  Lon ${point.longitudeDeg.fmt(8)}")
            point.ellipseSemiMajorM?.let { major ->
                Text(
                    "Dispersão amostral 95%: ${major.fmt(2)} × ${point.ellipseSemiMinorM?.fmt(2) ?: "—"} m  •  az ${point.ellipseAzimuthDeg?.fmt(1) ?: "—"}°",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "A elipse descreve a repetibilidade das amostras, não o erro absoluto da coordenada.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                "SIRGAS2000/UTM: projeção operacional aproximada do fix GNSS do telefone; modo RTK fará transformação rigorosa.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProjectsDialog(
    projects: List<SurveyProject>,
    currentProject: SurveyProject?,
    onOpen: (SurveyProject) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projetos de terreno") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(projects, key = { it.id }) { project ->
                        ListItem(
                            headlineContent = { Text(project.name) },
                            supportingContent = { Text(if (project.id == currentProject?.id) "Aberto agora" else "Toque para abrir") },
                            modifier = Modifier.clickable { onOpen(project) },
                        )
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Novo projeto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

private fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)