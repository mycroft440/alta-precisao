package com.geomeasure.app.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.geomeasure.app.gnss.raw.GnssRawLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidGnssManager(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val rawLogger = GnssRawLogger(appContext)

    private val _snapshot = MutableStateFlow(GnssSnapshot())
    val snapshot: StateFlow<GnssSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var started = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = updateLocation(location)

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _snapshot.update { it.copy(providerEnabled = true) }
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _snapshot.update { it.copy(providerEnabled = false) }
            }
        }
    }

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            var cn0Sum = 0.0
            var cn0Count = 0
            for (i in 0 until status.satelliteCount) {
                if (!status.usedInFix(i)) continue
                used++
                val cn0 = status.getCn0DbHz(i)
                if (cn0.isFinite() && cn0 > 0f) {
                    cn0Sum += cn0
                    cn0Count++
                }
            }
            _snapshot.update {
                it.copy(
                    satellitesVisible = status.satelliteCount,
                    satellitesUsed = used,
                    averageCn0DbHz = if (cn0Count > 0) cn0Sum / cn0Count else null,
                )
            }
        }
    }

    private val measurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            rawLogger.append(eventArgs)

            val bandsBySatellite = mutableMapOf<Pair<Int, Int>, MutableSet<FrequencyBand>>()
            eventArgs.measurements.forEach { measurement ->
                if (!measurement.hasCarrierFrequencyHz()) return@forEach
                val band = frequencyBand(measurement.carrierFrequencyHz.toDouble()) ?: return@forEach
                bandsBySatellite
                    .getOrPut(measurement.constellationType to measurement.svid) { mutableSetOf() }
                    .add(band)
            }
            val dualFrequencySatellites = bandsBySatellite.values.count {
                FrequencyBand.LOWER in it && FrequencyBand.UPPER in it
            }

            _snapshot.update {
                it.copy(
                    rawMeasurements = eventArgs.measurements.size,
                    dualFrequencySignals = dualFrequencySatellites,
                    rawLogPath = rawLogger.currentFile?.absolutePath ?: it.rawLogPath,
                )
            }
        }
    }

    fun hasFineLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasFineLocationPermission()) return
        if (LocationManager.GPS_PROVIDER !in locationManager.allProviders) {
            _snapshot.update {
                it.copy(providerEnabled = false, errorMessage = "Este aparelho não disponibiliza o provedor GNSS/GPS do Android.")
            }
            return
        }

        val providerEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(LocationManagerCompat.isLocationEnabled(locationManager))
        _snapshot.update { it.copy(providerEnabled = providerEnabled, errorMessage = null) }

        // Mark the manager started only for the duration of this setup attempt. Any critical failure
        // rolls the partial registration back so a later lifecycle start can retry cleanly.
        started = true
        try {
            rawLogger.startSession()
            _snapshot.update { it.copy(rawLogPath = rawLogger.currentFile?.absolutePath) }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0f,
                locationListener,
            )
            val executor = ContextCompat.getMainExecutor(appContext)
            runCatching {
                LocationManagerCompat.registerGnssStatusCallback(locationManager, executor, statusCallback)
            }
            runCatching {
                LocationManagerCompat.registerGnssMeasurementsCallback(locationManager, executor, measurementsCallback)
            }

            // A last-known fix is useful only if it is newer than anything already observed.
            // updateLocation uses monotonic elapsedRealtimeNanos to reject stale data.
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::updateLocation)
        } catch (t: Exception) {
            started = false
            runCatching { locationManager.removeUpdates(locationListener) }
            runCatching { LocationManagerCompat.unregisterGnssStatusCallback(locationManager, statusCallback) }
            runCatching { LocationManagerCompat.unregisterGnssMeasurementsCallback(locationManager, measurementsCallback) }
            rawLogger.stopSession()
            _snapshot.update {
                it.copy(
                    providerEnabled = false,
                    rawLogPath = null,
                    errorMessage = "Falha ao iniciar GNSS: ${t.message ?: t::class.java.simpleName}",
                )
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { locationManager.removeUpdates(locationListener) }
        runCatching { LocationManagerCompat.unregisterGnssStatusCallback(locationManager, statusCallback) }
        runCatching { LocationManagerCompat.unregisterGnssMeasurementsCallback(locationManager, measurementsCallback) }
        rawLogger.stopSession()
        _snapshot.update { it.copy(rawLogPath = null) }
    }

    fun close() {
        stop()
        rawLogger.close()
    }

    private fun updateLocation(location: Location) {
        val elapsed = location.elapsedRealtimeNanos
        val verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters.toDouble()
        } else null
        val mock = LocationCompat.isMock(location)

        _snapshot.update { current ->
            // Never let an old lastKnownLocation or an out-of-order callback overwrite a fresher fix.
            if (current.elapsedRealtimeNanos > 0L && elapsed <= current.elapsedRealtimeNanos) {
                return@update current
            }
            current.copy(
                latitudeDeg = location.latitude,
                longitudeDeg = location.longitude,
                ellipsoidalHeightM = if (location.hasAltitude()) location.altitude else null,
                horizontalAccuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                verticalAccuracyM = verticalAccuracy,
                timestampMillis = location.time,
                elapsedRealtimeNanos = elapsed,
                providerEnabled = true,
                isMock = mock,
                errorMessage = null,
            )
        }
    }

    private fun frequencyBand(hz: Double): FrequencyBand? = when (hz) {
        in 1.15e9..1.30e9 -> FrequencyBand.LOWER
        in 1.45e9..1.65e9 -> FrequencyBand.UPPER
        else -> null
    }

    private enum class FrequencyBand { LOWER, UPPER }
}
