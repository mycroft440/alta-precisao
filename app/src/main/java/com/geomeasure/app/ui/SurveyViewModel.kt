package com.geomeasure.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geomeasure.app.data.GeoMeasureDatabase
import com.geomeasure.app.geodesy.Sirgas2000
import com.geomeasure.app.geodesy.Wgs84
import com.geomeasure.app.gnss.AndroidGnssManager
import com.geomeasure.app.gnss.GnssQualityEvaluator
import com.geomeasure.app.gnss.GnssSnapshot
import com.geomeasure.app.model.PointQuality
import com.geomeasure.app.model.SurveyPoint
import com.geomeasure.app.model.SurveyProject
import com.geomeasure.app.survey.CaptureProgress
import com.geomeasure.app.survey.PointCaptureEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SurveyViewModel(application: Application) : AndroidViewModel(application) {
    private val gnssManager = AndroidGnssManager(application)
    private val database = GeoMeasureDatabase(application)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    val gnss: StateFlow<GnssSnapshot> = gnssManager.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, GnssSnapshot())

    private val _points = MutableStateFlow<List<SurveyPoint>>(emptyList())
    val points: StateFlow<List<SurveyPoint>> = _points.asStateFlow()

    private val _projects = MutableStateFlow<List<SurveyProject>>(emptyList())
    val projects: StateFlow<List<SurveyProject>> = _projects.asStateFlow()

    private val _currentProject = MutableStateFlow<SurveyProject?>(null)
    val currentProject: StateFlow<SurveyProject?> = _currentProject.asStateFlow()

    private val _captureProgress = MutableStateFlow<CaptureProgress?>(null)
    val captureProgress: StateFlow<CaptureProgress?> = _captureProgress.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val captureEngine = PointCaptureEngine()
    private var captureJob: Job? = null
    private var captureGeneration = 0L

    init {
        loadInitialProject()
    }

    fun hasPermission(): Boolean = gnssManager.hasFineLocationPermission()
    fun startGnss() = gnssManager.start()
    fun stopGnss() = gnssManager.stop()

    private fun loadInitialProject() {
        viewModelScope.launch {
            dbResult {
                val existing = database.listProjects()
                val project = existing.firstOrNull() ?: database.createProject("Terreno 1")
                Triple(database.listProjects(), project, database.loadPoints(project.id))
            }.onSuccess { (projects, project, points) ->
                _projects.value = projects
                _currentProject.value = project
                _points.value = points
            }.onFailure { reportError("Falha ao abrir o banco de projetos", it) }
        }
    }

    fun createProject(name: String) {
        cancelCapture()
        viewModelScope.launch {
            dbResult {
                val project = database.createProject(name)
                project to database.listProjects()
            }.onSuccess { (project, projects) ->
                _projects.value = projects
                _currentProject.value = project
                _points.value = emptyList()
            }.onFailure { reportError("Não foi possível criar o projeto", it) }
        }
    }

    fun openProject(project: SurveyProject) {
        cancelCapture()
        viewModelScope.launch {
            dbResult { database.loadPoints(project.id) }
                .onSuccess { points ->
                    _currentProject.value = project
                    _points.value = points
                }
                .onFailure { reportError("Não foi possível abrir o projeto", it) }
        }
    }

    fun startPointCapture() {
        if (captureJob?.isActive == true) return
        val projectId = _currentProject.value?.id ?: return
        val pointSequence = _points.value.size + 1
        val generation = ++captureGeneration
        captureEngine.clear()
        _captureProgress.value = CaptureProgress(
            acceptedSamples = 0,
            requiredSamples = captureEngine.minimumSamples,
            observationSpanMillis = 0L,
            requiredObservationMillis = captureEngine.minimumObservationMillis,
            currentQuality = quality(),
            ready = false,
        )

        captureJob = viewModelScope.launch {
            try {
                val built = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                    gnss.map { snapshot ->
                        if (generation != captureGeneration) return@map null
                        val progress = captureEngine.add(snapshot)
                        _captureProgress.value = progress
                        if (progress.ready) captureEngine.buildPoint(pointSequence) else null
                    }.filterNotNull().first()
                }

                if (generation != captureGeneration) return@launch
                if (built == null) {
                    _uiMessage.value = "Não houve estabilidade GNSS suficiente. Vá para céu mais aberto e tente novamente."
                    return@launch
                }

                val point = built.copy(projectId = projectId)
                val result = dbResult {
                    val databaseId = database.savePoint(projectId, point)
                    val projects = database.listProjects()
                    Triple(databaseId, projects, projects.firstOrNull { it.id == projectId })
                }
                result.onSuccess { (databaseId, projects, updatedProject) ->
                    if (generation != captureGeneration) return@onSuccess
                    _points.value = _points.value + point.copy(databaseId = databaseId)
                    _projects.value = projects
                    if (updatedProject != null) _currentProject.value = updatedProject
                }.onFailure { reportError("Falha ao salvar o vértice", it) }
            } finally {
                if (generation == captureGeneration) {
                    _captureProgress.value = null
                    captureEngine.clear()
                    captureJob = null
                }
            }
        }
    }

    fun cancelCapture() {
        captureGeneration++
        captureJob?.cancel()
        captureJob = null
        _captureProgress.value = null
        captureEngine.clear()
    }

    fun removeLastPoint() {
        val projectId = _currentProject.value?.id ?: return
        if (_points.value.isEmpty()) return
        cancelCapture()
        viewModelScope.launch {
            dbResult {
                database.deleteLastPoint(projectId)
                val points = database.loadPoints(projectId)
                val projects = database.listProjects()
                Triple(points, projects, projects.firstOrNull { it.id == projectId })
            }.onSuccess { (points, projects, updatedProject) ->
                _points.value = points
                _projects.value = projects
                if (updatedProject != null) _currentProject.value = updatedProject
            }.onFailure { reportError("Não foi possível remover o último ponto", it) }
        }
    }

    fun clearSurvey() {
        val projectId = _currentProject.value?.id ?: return
        cancelCapture()
        viewModelScope.launch {
            dbResult {
                database.clearProjectPoints(projectId)
                val projects = database.listProjects()
                projects to projects.firstOrNull { it.id == projectId }
            }.onSuccess { (projects, updatedProject) ->
                _points.value = emptyList()
                _projects.value = projects
                if (updatedProject != null) _currentProject.value = updatedProject
            }.onFailure { reportError("Não foi possível limpar o levantamento", it) }
        }
    }

    fun quality(): PointQuality = GnssQualityEvaluator.evaluate(
        gnss.value,
        nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
    )

    fun boundaryMeasurement(): Wgs84.PolygonMeasurement = Wgs84.measurePolygon(
        _points.value.map { Wgs84.Geo(it.latitudeDeg, it.longitudeDeg, it.ellipsoidalHeightM ?: 0.0) },
    )

    fun utm(point: SurveyPoint): Sirgas2000.Utm = Sirgas2000.projectWgs84CompatibleFix(
        point.latitudeDeg,
        point.longitudeDeg,
    )

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    private suspend fun <T> dbResult(block: () -> T): Result<T> = try {
        Result.success(withContext(dbDispatcher) { block() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun reportError(prefix: String, throwable: Throwable) {
        _uiMessage.value = "$prefix: ${throwable.message ?: throwable::class.java.simpleName}"
    }

    override fun onCleared() {
        cancelCapture()
        gnssManager.close()
        database.close()
        super.onCleared()
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 45_000L
    }
}
