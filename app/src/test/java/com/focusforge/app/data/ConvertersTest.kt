package com.focusforge.app.data

import com.focusforge.app.data.local.Converters
import com.focusforge.app.data.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testSessionTypeConversion() {
        assertEquals("DEEP", converters.fromSessionType(SessionType.DEEP))
        assertEquals("SHALLOW", converters.fromSessionType(SessionType.SHALLOW))

        assertEquals(SessionType.DEEP, converters.toSessionType("DEEP"))
        assertEquals(SessionType.SHALLOW, converters.toSessionType("SHALLOW"))
        // Fallback safety on unknown string
        assertEquals(SessionType.DEEP, converters.toSessionType("UNKNOWN_STRING"))
    }
}
