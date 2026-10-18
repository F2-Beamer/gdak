package de.gdak.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanDateTest {

    @Test
    fun isoDateBecomesGermanDate() {
        assertEquals("21.04.2014", germanDate("2014-04-21"))
    }

    @Test
    fun emptyDateStaysEmpty() {
        assertEquals("", germanDate(""))
    }

    @Test
    fun unknownFormatStaysUnchanged() {
        assertEquals("unbekannt", germanDate("unbekannt"))
    }
}
