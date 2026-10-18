package de.gdak.app.data.cachedb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheFilterTest {

    @Test
    fun gsakTypesBelongToTheirGroup() {
        assertEquals(CacheTypeGroup.TRADITIONAL, CacheTypeGroup.of("T"))
        assertEquals(CacheTypeGroup.MULTI, CacheTypeGroup.of("M"))
        assertEquals(CacheTypeGroup.MYSTERY, CacheTypeGroup.of("U"))
        assertEquals(CacheTypeGroup.MYSTERY, CacheTypeGroup.of("B"))
    }

    @Test
    fun unknownTypesFallIntoOther() {
        assertEquals(CacheTypeGroup.OTHER, CacheTypeGroup.of("L"))
        assertEquals(CacheTypeGroup.OTHER, CacheTypeGroup.of(""))
    }

    @Test
    fun defaultFilterIsNotActive() {
        assertFalse(CacheFilter().isActive)
    }

    @Test
    fun searchTextDoesNotCountAsActive() {
        assertFalse(CacheFilter(searchText = "GC1").isActive)
    }

    @Test
    fun changedStatusMakesFilterActive() {
        assertTrue(CacheFilter(showDnf = false).isActive)
    }

    @Test
    fun distanceLimitKeepsCachesInsideOnly() {
        val filter = CacheFilter(maxDistanceKm = 10f)
        assertTrue(filter.allowsDistance(9.9))
        assertFalse(filter.allowsDistance(10.1))
    }

    @Test
    fun withoutDistanceLimitEverythingIsAllowed() {
        assertTrue(CacheFilter().allowsDistance(999.0))
    }

    @Test
    fun unknownDistanceIsNeverHidden() {
        assertTrue(CacheFilter(maxDistanceKm = 1f).allowsDistance(null))
    }
}
