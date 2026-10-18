package de.gdak.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFormatTest {

    @Test
    fun formatsNorthAndEastWithDecimalMinutes() {
        assertEquals("N 52° 31.499'  E 013° 24.567'", formatDmm(52.52498, 13.40945))
    }

    @Test
    fun formatsSouthAndWestWithDecimalMinutes() {
        assertEquals("S 33° 30.000'  W 070° 15.000'", formatDmm(-33.5, -70.25))
    }

    @Test
    fun roundingNeverProducesSixtyMinutes() {
        assertEquals("N 48° 00.000'  E 011° 00.000'", formatDmm(47.9999999, 10.9999999))
    }
}
