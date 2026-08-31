package com.geomeasure.app.geodesy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sirgas2000Test {
    @Test
    fun campanhaMg_projectsToZone23South() {
        // Reference generated from EPSG:4674 -> EPSG:31983 (SIRGAS 2000 / UTM 23S).
        val utm = Sirgas2000.projectWgs84CompatibleFix(-21.833, -45.4)
        assertEquals(23, utm.zone)
        assertEquals('S', utm.hemisphere)
        assertEquals(458663.251, utm.eastingM, 0.02)
        assertEquals(7585603.750, utm.northingM, 0.02)
        assertTrue(utm.approximateFrameTransform)
    }
}
