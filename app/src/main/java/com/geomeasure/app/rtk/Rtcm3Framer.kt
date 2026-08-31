package com.geomeasure.app.rtk

/** Splits a byte stream into validated RTCM 3.x frames using CRC-24Q. */
class Rtcm3Framer(private val onFrame: (RtcmFrame) -> Unit) {
    private var buffer = ByteArray(0)

    fun feed(bytes: ByteArray, count: Int = bytes.size) {
        require(count in 0..bytes.size)
        if (count == 0) return
        val incoming = bytes.copyOf(count)
        buffer = if (buffer.isEmpty()) incoming else buffer + incoming
        if (buffer.size > MAX_BUFFER_BYTES) {
            // Keep enough tail for one maximum RTCM frame plus resynchronization bytes.
            buffer = buffer.copyOfRange(buffer.size - MAX_BUFFER_BYTES, buffer.size)
        }
        drain()
    }

    private fun drain() {
        var offset = 0
        while (true) {
            while (offset < buffer.size && (buffer[offset].toInt() and 0xFF) != 0xD3) offset++
            if (buffer.size - offset < 3) break

            // RTCM 3 reserves the upper six bits of byte 1. If they are nonzero, this D3 was noise.
            if ((buffer[offset + 1].toInt() and 0xFC) != 0) {
                offset++
                continue
            }
            val length = ((buffer[offset + 1].toInt() and 0x03) shl 8) or
                (buffer[offset + 2].toInt() and 0xFF)
            val frameLength = 3 + length + 3
            if (buffer.size - offset < frameLength) break

            val frame = buffer.copyOfRange(offset, offset + frameLength)
            val expected = ((frame[frameLength - 3].toInt() and 0xFF) shl 16) or
                ((frame[frameLength - 2].toInt() and 0xFF) shl 8) or
                (frame[frameLength - 1].toInt() and 0xFF)
            val actual = crc24q(frame, frameLength - 3)
            if (actual == expected) {
                val type = if (length >= 2) {
                    ((frame[3].toInt() and 0xFF) shl 4) or ((frame[4].toInt() and 0xF0) shr 4)
                } else null
                onFrame(RtcmFrame(frame.copyOf(), type))
                offset += frameLength
            } else {
                // Do not discard a whole candidate frame on CRC failure. Advance one byte so a
                // valid D3 inside corrupted data can become the next synchronization point.
                offset++
            }
        }
        buffer = if (offset >= buffer.size) ByteArray(0) else buffer.copyOfRange(offset, buffer.size)
    }

    data class RtcmFrame(val bytes: ByteArray, val messageType: Int?)

    companion object {
        private const val MAX_RTCM_PAYLOAD = 1023
        private const val MAX_FRAME_BYTES = 3 + MAX_RTCM_PAYLOAD + 3
        private const val MAX_BUFFER_BYTES = MAX_FRAME_BYTES * 4

        fun crc24q(data: ByteArray, length: Int = data.size): Int {
            require(length in 0..data.size)
            var crc = 0
            for (i in 0 until length) {
                crc = crc xor ((data[i].toInt() and 0xFF) shl 16)
                repeat(8) {
                    crc = if ((crc and 0x800000) != 0) ((crc shl 1) xor 0x1864CFB) else (crc shl 1)
                    crc = crc and 0xFFFFFF
                }
            }
            return crc
        }
    }
}
