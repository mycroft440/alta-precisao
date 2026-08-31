package com.geomeasure.app.rtk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Rtcm3FramerTest {
    @Test
    fun recoversAfterCorruptPrefixAndEmitsValidFrameCopy() {
        val valid = frameWithType(1005)
        val corrupted = valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }
        val emitted = mutableListOf<Rtcm3Framer.RtcmFrame>()
        val framer = Rtcm3Framer { emitted += it }

        framer.feed(byteArrayOf(0x00, 0x7f, 0x55) + corrupted + valid)

        assertEquals(1, emitted.size)
        assertArrayEquals(valid, emitted.single().bytes)
        assertEquals(1005, emitted.single().messageType)
    }

    private fun frameWithType(type: Int): ByteArray {
        require(type in 0..4095)
        val payload = byteArrayOf(
            (type shr 4).toByte(),
            ((type and 0x0f) shl 4).toByte(),
        )
        val frame = ByteArray(3 + payload.size + 3)
        frame[0] = 0xD3.toByte()
        frame[1] = ((payload.size shr 8) and 0x03).toByte()
        frame[2] = (payload.size and 0xff).toByte()
        payload.copyInto(frame, 3)
        val crc = Rtcm3Framer.crc24q(frame, 3 + payload.size)
        frame[3 + payload.size] = ((crc shr 16) and 0xff).toByte()
        frame[4 + payload.size] = ((crc shr 8) and 0xff).toByte()
        frame[5 + payload.size] = (crc and 0xff).toByte()
        return frame
    }
}
