package com.geomeasure.app.rtk

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.SocketException
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * NTRIP v1/v2 streaming client. Run [stream] on an IO coroutine/thread. The client emits copies of
 * received RTCM chunks so callers can safely buffer/frame them after the next socket read.
 */
class NtripClient {
    data class Config(
        val host: String,
        val port: Int = 2101,
        val mountPoint: String,
        val username: String? = null,
        val password: String? = null,
        val useTls: Boolean = false,
        val allowInsecureBasicAuth: Boolean = false,
        val connectTimeoutMs: Int = 10_000,
        val readTimeoutMs: Int = 5_000,
        val ggaIntervalMs: Long = 10_000L,
    ) {
        init {
            require(host.isNotBlank())
            require(port in 1..65535)
            require(mountPoint.trim('/').isNotBlank())
            require(connectTimeoutMs > 0)
            require(readTimeoutMs > 0)
            require(ggaIntervalMs >= 1_000L)
            requireNoHeaderInjection(host, "host")
            requireNoHeaderInjection(mountPoint, "mountPoint")
            username?.let { requireNoHeaderInjection(it, "username") }
            password?.let { requireNoHeaderInjection(it, "password") }
            if (!username.isNullOrBlank() && !useTls && !allowInsecureBasicAuth) {
                require(false) { "Basic authentication over unencrypted NTRIP is disabled. Enable TLS or explicitly allow insecure auth." }
            }
        }
    }

    @Volatile
    private var socket: Socket? = null

    /**
     * [ggaProvider] should return a complete NMEA GGA sentence when VRS/NRTK service requires the
     * rover position. It is sent immediately and then periodically while the stream is active.
     */
    fun stream(
        config: Config,
        ggaProvider: (() -> String?)? = null,
        onRtcm: (ByteArray, Int) -> Unit,
    ) {
        close()
        val s = openConnectedSocket(config)
        socket = s

        try {
            val out = BufferedOutputStream(s.getOutputStream())
        val mount = config.mountPoint.trim('/')
        val auth = if (!config.username.isNullOrBlank()) {
            val raw = "${config.username}:${config.password.orEmpty()}".toByteArray(Charsets.UTF_8)
            "Authorization: Basic ${Base64.getEncoder().encodeToString(raw)}\r\n"
        } else ""
        val request = buildString {
            append("GET /$mount HTTP/1.1\r\n")
            append("Host: ${config.host}:${config.port}\r\n")
            append("Ntrip-Version: Ntrip/2.0\r\n")
            append("User-Agent: NTRIP GeoMeasure/0.2.1\r\n")
            append("Accept: */*\r\n")
            append(auth)
            append("Connection: keep-alive\r\n\r\n")
        }
        out.write(request.toByteArray(Charsets.US_ASCII))
        out.flush()

        val input = BufferedInputStream(s.getInputStream())
        val response = readResponseHeader(input)
        if (!response.accepted) {
            close()
            error("NTRIP caster rejected request: ${response.statusLine}")
        }

        var lastGgaAtNanos = 0L
        fun maybeSendGga(force: Boolean = false) {
            val provider = ggaProvider ?: return
            val nowNanos = System.nanoTime()
            val intervalNanos = config.ggaIntervalMs * 1_000_000L
            if (!force && nowNanos - lastGgaAtNanos < intervalNanos) return
            val gga = provider()?.trim()?.takeIf { it.startsWith('$') && it.contains("GGA,") } ?: return
            requireNoHeaderInjection(gga, "GGA", allowTerminalNewline = true)
            out.write(gga.trimEnd('\r', '\n').toByteArray(Charsets.US_ASCII))
            out.write("\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            lastGgaAtNanos = nowNanos
        }

            maybeSendGga(force = true)
            val buffer = ByteArray(8192)
            while (!s.isClosed) {
                try {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) onRtcm(buffer.copyOf(count), count)
                    maybeSendGga()
                } catch (_: SocketTimeoutException) {
                    // Timeouts are used as a heartbeat opportunity for VRS GGA, not as stream failure.
                    maybeSendGga()
                } catch (e: SocketException) {
                    // close() is also the cancellation mechanism. Do not surface an expected
                    // "socket closed" exception to the caller when shutdown was intentional.
                    if (socket !== s || s.isClosed) break
                    throw e
                }
            }
        } finally {
            if (socket === s) socket = null
            runCatching { s.close() }
        }
    }

    fun close() {
        val s = socket
        socket = null
        runCatching { s?.close() }
    }

    private fun openConnectedSocket(config: Config): Socket {
        val address = InetSocketAddress(config.host, config.port)
        if (!config.useTls) {
            return Socket().apply {
                connect(address, config.connectTimeoutMs)
                soTimeout = config.readTimeoutMs
            }
        }

        // Connect the TCP socket first, then wrap it. Passing the hostname to createSocket is
        // important for TLS peer identity/SNI on GNSS casters hosted behind virtual endpoints.
        val plain = Socket()
        try {
            plain.connect(address, config.connectTimeoutMs)
            plain.soTimeout = config.readTimeoutMs
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(plain, config.host, config.port, true) as SSLSocket
            ssl.soTimeout = config.readTimeoutMs
            ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            ssl.startHandshake()
            return ssl
        } catch (t: Exception) {
            runCatching { plain.close() }
            throw t
        }
    }

    private fun readResponseHeader(input: BufferedInputStream): ResponseHeader {
        val statusLine = readAsciiLine(input) ?: error("NTRIP caster closed before sending a status line")
        val accepted = statusLine.startsWith("ICY 200", ignoreCase = true) ||
            Regex("^HTTP/\\d(?:\\.\\d)?\\s+200(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(statusLine)

        // Classic NTRIP v1 commonly sends only `ICY 200 OK\r\n` and RTCM immediately after it.
        // Some casters append optional ASCII headers. Peek safely: never consume a D3 RTCM preamble.
        if (statusLine.startsWith("ICY ", ignoreCase = true)) {
            consumeOptionalIcyHeaders(input)
            return ResponseHeader(statusLine, accepted)
        }

        var headerBytes = statusLine.length + 2
        while (true) {
            val line = readAsciiLine(input) ?: break
            headerBytes += line.length + 2
            if (headerBytes > MAX_HEADER_BYTES) error("NTRIP response header is too large")
            if (line.isEmpty()) break
        }
        return ResponseHeader(statusLine, accepted)
    }


    private fun consumeOptionalIcyHeaders(input: BufferedInputStream) {
        input.mark(MAX_HEADER_BYTES)
        val first = input.read()
        if (first < 0) return
        input.reset()
        if (first == 0xD3 || first !in 0x09..0x7E) return

        var headerBytes = 0
        while (true) {
            val line = readAsciiLine(input) ?: return
            headerBytes += line.length + 2
            if (headerBytes > MAX_HEADER_BYTES) error("NTRIP ICY response header is too large")
            if (line.isEmpty()) return
            // An optional ICY header must look like an HTTP-style header. If not, restore the
            // stream to the beginning because it may already be correction payload/noise.
            if (':' !in line) {
                input.reset()
                return
            }
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = StringBuilder()
        while (out.length <= MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return if (out.isEmpty()) null else out.toString()
            if (b == '\n'.code) return out.toString().removeSuffix("\r")
            // HTTP/NTRIP headers are ASCII. Binary before a line terminator is a malformed response.
            if (b !in 0x09..0x7E && b != '\r'.code) error("Malformed NTRIP response header")
            out.append(b.toChar())
        }
        error("NTRIP response line is too large")
    }

    private data class ResponseHeader(val statusLine: String, val accepted: Boolean)

    companion object {
        private const val MAX_HEADER_BYTES = 16_384

        private fun requireNoHeaderInjection(value: String, field: String, allowTerminalNewline: Boolean = false) {
            val checked = if (allowTerminalNewline) value.trimEnd('\r', '\n') else value
            require('\r' !in checked && '\n' !in checked) { "$field contains a line break" }
            require(checked.none { it.code < 0x20 && it != '\t' }) { "$field contains a control character" }
        }
    }
}
