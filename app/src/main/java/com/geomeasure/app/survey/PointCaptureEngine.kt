package com.geomeasure.app.survey

import com.geomeasure.app.geodesy.Wgs84
import com.geomeasure.app.gnss.GnssQualityEvaluator
import com.geomeasure.app.gnss.GnssSnapshot
import com.geomeasure.app.model.PointQuality
import com.geomeasure.app.model.SurveyPoint
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class PointCaptureEngine(
    val minimumSamples: Int = 12,
    private val maximumSamples: Int = 40,
    val minimumObservationMillis: Long = 5_000L,
) {
    init {
        require(minimumSamples >= 3)
        require(maximumSamples >= minimumSamples)
        require(minimumObservationMillis >= 0L)
    }

    private val samples = ArrayDeque<GnssSnapshot>()
    private var lastElapsedRealtimeNanos: Long = Long.MIN_VALUE

    fun clear() {
        samples.clear()
        lastElapsedRealtimeNanos = Long.MIN_VALUE
    }

    fun add(snapshot: GnssSnapshot): CaptureProgress {
        val quality = GnssQualityEvaluator.evaluate(snapshot)
        if (quality == PointQuality.REJECTED || !snapshot.hasFix) {
            return progress(quality)
        }

        // Satellite/raw callbacks can update the StateFlow without a new position. Count only a
        // genuinely new Location fix, ordered by Android's monotonic elapsed realtime clock.
        val fixNanos = snapshot.elapsedRealtimeNanos
        if (fixNanos > 0L && fixNanos <= lastElapsedRealtimeNanos) {
            return progress(quality)
        }
        if (fixNanos == 0L && samples.lastOrNull()?.timestampMillis == snapshot.timestampMillis) {
            return progress(quality)
        }

        samples.addLast(snapshot)
        if (fixNanos > 0L) lastElapsedRealtimeNanos = fixNanos
        while (samples.size > maximumSamples) samples.removeFirst()
        return progress(quality)
    }

    fun buildPoint(id: Int): SurveyPoint? {
        if (!isReady()) return null
        val valid = samples.filter { GnssQualityEvaluator.evaluate(it) != PointQuality.REJECTED && it.hasFix }
        if (valid.size < minimumSamples) return null

        val originLat = valid.mapNotNull { it.latitudeDeg }.average()
        val originLon = circularLongitudeMean(valid.mapNotNull { it.longitudeDeg })
        val originH = valid.mapNotNull { it.ellipsoidalHeightM }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val origin = Wgs84.Geo(originLat, originLon, originH)

        val vectors = valid.map { s ->
            val ecef = Wgs84.toEcef(s.latitudeDeg!!, s.longitudeDeg!!, s.ellipsoidalHeightM ?: originH)
            s to Wgs84.ecefToEnu(ecef, origin)
        }

        // Robust 2-D center first, then MAD-based radial rejection around that robust center.
        val medianE = median(vectors.map { it.second.east })
        val medianN = median(vectors.map { it.second.north })
        val radii = vectors.map { (_, enu) ->
            val e = enu.east - medianE
            val n = enu.north - medianN
            sqrt(e * e + n * n)
        }
        val medianRadius = median(radii)
        val mad = median(radii.map { abs(it - medianRadius) }).coerceAtLeast(0.05)
        val threshold = maxOf(0.20, 3.5 * mad)
        val accepted = vectors.filterIndexed { index, _ -> abs(radii[index] - medianRadius) <= threshold }
        if (accepted.size < minimumSamples) return null

        val lat = accepted.map { it.first.latitudeDeg!! }.average()
        val lon = circularLongitudeMean(accepted.map { it.first.longitudeDeg!! })
        val hValues = accepted.mapNotNull { it.first.ellipsoidalHeightM }
        val h = hValues.takeIf { it.isNotEmpty() }?.average()

        val finalOrigin = Wgs84.Geo(lat, lon, h ?: originH)
        val finalEnu = accepted.map { (snapshot, _) ->
            Wgs84.ecefToEnu(
                Wgs84.toEcef(snapshot.latitudeDeg!!, snapshot.longitudeDeg!!, snapshot.ellipsoidalHeightM ?: finalOrigin.heightM),
                finalOrigin,
            )
        }
        // Center the ENU cloud explicitly before computing dispersion/covariance. The geodetic
        // mean used as ENU origin is only an approximation to the Euclidean sample centroid; using
        // uncentered vectors would slightly inflate the reported dispersion/ellipse.
        val meanEast = finalEnu.map { it.east }.average()
        val meanNorth = finalEnu.map { it.north }.average()
        val centered = finalEnu.map { (it.east - meanEast) to (it.north - meanNorth) }
        val dispersion = sqrt(centered.map { (e, n) -> e * e + n * n }.average())
        val ellipse = precisionEllipse(centered)

        // Conservative metadata: do not advertise the best instant observed during a noisy capture.
        val worstQuality = accepted
            .map { GnssQualityEvaluator.evaluate(it.first) }
            .maxByOrNull { it.ordinal }
            ?: PointQuality.POOR
        val newest = accepted.maxBy { it.first.elapsedRealtimeNanos.takeIf { n -> n > 0L } ?: it.first.timestampMillis }.first
        val horizontalAccuracy = accepted.mapNotNull { it.first.horizontalAccuracyM }.maxOrNull() ?: return null
        val verticalAccuracy = accepted.mapNotNull { it.first.verticalAccuracyM }.maxOrNull()

        return SurveyPoint(
            id = id,
            latitudeDeg = lat,
            longitudeDeg = lon,
            ellipsoidalHeightM = h,
            horizontalAccuracyM = horizontalAccuracy,
            verticalAccuracyM = verticalAccuracy,
            satellitesUsed = accepted.minOf { it.first.satellitesUsed },
            averageCn0DbHz = accepted.mapNotNull { it.first.averageCn0DbHz }.takeIf { it.isNotEmpty() }?.average(),
            rawMeasurements = accepted.minOf { it.first.rawMeasurements },
            capturedAtMillis = newest.timestampMillis,
            observationCount = accepted.size,
            dispersionM = dispersion,
            ellipseSemiMajorM = ellipse?.semiMajorM,
            ellipseSemiMinorM = ellipse?.semiMinorM,
            ellipseAzimuthDeg = ellipse?.azimuthDeg,
            quality = worstQuality,
        )
    }

    private fun progress(currentQuality: PointQuality): CaptureProgress {
        val span = observationSpanMillis()
        return CaptureProgress(
            acceptedSamples = samples.size,
            requiredSamples = minimumSamples,
            observationSpanMillis = span,
            requiredObservationMillis = minimumObservationMillis,
            currentQuality = currentQuality,
            ready = samples.size >= minimumSamples && span >= minimumObservationMillis,
        )
    }

    private fun isReady(): Boolean = samples.size >= minimumSamples && observationSpanMillis() >= minimumObservationMillis

    private fun observationSpanMillis(): Long {
        if (samples.size < 2) return 0L
        val first = samples.first()
        val last = samples.last()
        return if (first.elapsedRealtimeNanos > 0L && last.elapsedRealtimeNanos >= first.elapsedRealtimeNanos) {
            (last.elapsedRealtimeNanos - first.elapsedRealtimeNanos) / 1_000_000L
        } else {
            (last.timestampMillis - first.timestampMillis).coerceAtLeast(0L)
        }
    }

    private fun precisionEllipse(centered: List<Pair<Double, Double>>): Ellipse? {
        if (centered.size < 3) return null
        val denom = (centered.size - 1).toDouble()
        val varE = centered.sumOf { it.first * it.first } / denom
        val varN = centered.sumOf { it.second * it.second } / denom
        val covEN = centered.sumOf { it.first * it.second } / denom
        val trace = varE + varN
        val root = sqrt(((varE - varN) * (varE - varN) + 4.0 * covEN * covEN).coerceAtLeast(0.0))
        val lambda1 = ((trace + root) / 2.0).coerceAtLeast(0.0)
        val lambda2 = ((trace - root) / 2.0).coerceAtLeast(0.0)

        // 95% confidence scale for a 2-D normal distribution: sqrt(chi-square(2, 0.95)).
        val k95 = 2.44774683068
        val semiMajor = k95 * sqrt(lambda1)
        val semiMinor = k95 * sqrt(lambda2)

        // Eigenvector orientation, converted to survey-style azimuth clockwise from North.
        val thetaFromEast = 0.5 * atan2(2.0 * covEN, varE - varN)
        val azimuth = ((90.0 - Math.toDegrees(thetaFromEast)) % 180.0 + 180.0) % 180.0
        return Ellipse(semiMajor, semiMinor, azimuth)
    }

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun circularLongitudeMean(longitudesDeg: List<Double>): Double {
        val radians = longitudesDeg.map(Math::toRadians)
        val x = radians.sumOf { kotlin.math.cos(it) }
        val y = radians.sumOf { kotlin.math.sin(it) }
        return Math.toDegrees(atan2(y, x))
    }

    private data class Ellipse(val semiMajorM: Double, val semiMinorM: Double, val azimuthDeg: Double)
}

data class CaptureProgress(
    val acceptedSamples: Int,
    val requiredSamples: Int,
    val observationSpanMillis: Long,
    val requiredObservationMillis: Long,
    val currentQuality: PointQuality,
    val ready: Boolean,
)
