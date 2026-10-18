package de.gdak.app.data.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun samePointHasNoDistance() {
        assertEquals(0.0, distanceKm(48.1, 11.5, 48.1, 11.5), 1e-9)
    }

    @Test
    fun oneDegreeOfLatitudeIsAboutOneHundredEleven() {
        val km = distanceKm(48.0, 11.0, 49.0, 11.0)
        assertTrue("war $km km", km in 111.0..111.4)
    }

    @Test
    fun munichToBerlinIsAboutFiveHundredKilometers() {
        val km = distanceKm(48.1371, 11.5754, 52.5200, 13.4050)
        assertTrue("war $km km", km in 500.0..510.0)
    }

    @Test
    fun distanceIsSymmetric() {
        assertEquals(
            distanceKm(48.1, 11.5, 52.5, 13.4),
            distanceKm(52.5, 13.4, 48.1, 11.5),
            1e-9,
        )
    }
}
