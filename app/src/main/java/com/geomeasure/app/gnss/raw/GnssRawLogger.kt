package com.geomeasure.app.gnss.raw

import android.content.Context
import android.location.GnssMeasurementsEvent
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Writes raw GNSS telemetry on a dedicated IO thread. The GNSS callback only serializes immutable
 * rows; disk writes and fsync-like flushes never run on the main callback executor.
 */
class GnssRawLogger(context: Context) : AutoCloseable {
    private val directory = File(context.filesDir, "gnss-raw").apply { mkdirs() }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GeoMeasure-GnssRawLogger").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0L)

    @Volatile
    private var activeGeneration = 0L

    @Volatile
    var currentFile: File? = null
        private set

    // Accessed only from executor thread.
    private var writer: BufferedWriter? = null
    private var writerGeneration = 0L
    private var pendingEvents = 0

    @Synchronized
    fun startSession() {
        if (activeGeneration != 0L) return
        val id = generation.incrementAndGet()
        activeGeneration = id
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val file = File(directory, "gnss-${formatter.format(Date())}.csv")
        currentFile = file
        executor.execute {
            if (activeGeneration != id) return@execute
            runCatching {
                writer?.close()
                writer = BufferedWriter(FileWriter(file, false)).also {
                    it.write(HEADER)
                    it.newLine()
                    it.flush()
                }
                writerGeneration = id
                pendingEvents = 0
            }.onFailure {
                if (activeGeneration == id) {
                    activeGeneration = 0L
                    currentFile = null
                }
                runCatching { writer?.close() }
                writer = null
                writerGeneration = 0L
            }
        }
    }

    fun append(event: GnssMeasurementsEvent) {
        val id = activeGeneration
        if (id == 0L) return

        val clock = event.clock
        val fullBias = if (clock.hasFullBiasNanos()) clock.fullBiasNanos.toString() else ""
        val bias = if (clock.hasBiasNanos()) clock.biasNanos.toString() else ""
        val now = System.currentTimeMillis()
        val rows = event.measurements.map { m ->
            val carrier = if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toString() else ""
            listOf(
                now,
                clock.timeNanos,
                fullBias,
                bias,
                m.svid,
                m.constellationType,
                m.state,
                m.cn0DbHz,
                carrier,
                m.pseudorangeRateMetersPerSecond,
                m.accumulatedDeltaRangeState,
                m.accumulatedDeltaRangeMeters,
                m.receivedSvTimeNanos,
                m.timeOffsetNanos,
            ).joinToString(",")
        }

        executor.execute {
            // Do not compare with activeGeneration here: stopSession() intentionally stops accepting
            // new callbacks immediately, but append tasks already queued before its close task must
            // still be persisted. The executor-owned writerGeneration prevents cross-session writes.
            if (writerGeneration != id) return@execute
            val out = writer ?: return@execute
            runCatching {
                rows.forEach { row ->
                    out.write(row)
                    out.newLine()
                }
                pendingEvents++
                if (pendingEvents >= FLUSH_EVERY_EVENTS) {
                    out.flush()
                    pendingEvents = 0
                }
            }.onFailure {
                runCatching { out.close() }
                writer = null
                writerGeneration = 0L
                if (activeGeneration == id) {
                    activeGeneration = 0L
                    currentFile = null
                }
            }
        }
    }

    @Synchronized
    fun stopSession() {
        val id = activeGeneration
        if (id == 0L) return
        activeGeneration = 0L
        executor.execute {
            // All append tasks for this generation were queued before this close task.
            if (writerGeneration == id) {
                runCatching { writer?.flush() }
                runCatching { writer?.close() }
                writer = null
                writerGeneration = 0L
                pendingEvents = 0
            }
        }
    }

    override fun close() {
        stopSession()
        executor.shutdown()
    }

    private companion object {
        const val FLUSH_EVERY_EVENTS = 20
        const val HEADER = "eventTimeMillis,clockTimeNanos,fullBiasNanos,biasNanos,svid,constellation,state,cn0DbHz,carrierHz,pseudorangeRateMps,adrState,adrMeters,receivedSvTimeNanos,timeOffsetNanos"
    }
}
