package com.geomeasure.app.rtk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class NmeaGgaParserTest {
    @Test
    fun parsesRtkFixedSolutionAndEllipsoidalHeight() {
        val p = NmeaGgaParser.parse("\$GNGGA,123519,2150.0000,S,04524.0000,W,4,22,0.7,910.2,M,-4.0,M,0.8,1234")
        assertNotNull(p)
        assertEquals(RtkFixType.RTK_FIXED, p!!.fixType)
        assertEquals(-21.8333333333, p.latitudeDeg, 1e-8)
        assertEquals(-45.4, p.longitudeDeg, 1e-8)
        assertEquals(22, p.satellites)
        assertEquals(906.2, p.ellipsoidalHeightM!!, 1e-9)
    }

    @Test
    fun acceptsOtherValidTalkerIds() {
        assertNotNull(NmeaGgaParser.parse("\$GAGGA,123519,2150.0000,S,04524.0000,W,5,18,0.8,900.0,M,-4.0,M,1.0,1"))
        assertNotNull(NmeaGgaParser.parse("\$GBGGA,123519,2150.0000,S,04524.0000,W,2,14,0.9,900.0,M,-4.0,M,1.0,1"))
    }

    @Test
    fun rejectsMalformedChecksumAndCoordinates() {
        assertNull(NmeaGgaParser.parse("\$GNGGA,123519,2150.0000,S,04524.0000,W,4,22,0.7,910.2,M,-4.0,M,0.8,1234*ZZ"))
        assertNull(NmeaGgaParser.parse("\$GNGGA,123519,2160.0000,S,04524.0000,W,4,22,0.7,910.2,M,-4.0,M,0.8,1234"))
        assertNull(NmeaGgaParser.parse("\$GNGGA,123519,2150.0000,X,04524.0000,W,4,22,0.7,910.2,M,-4.0,M,0.8,1234"))
    }
}
