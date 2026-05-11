package net.rodakot.ngxhttpmonitoringclient.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiskUsageCalculationTest {
    @Test
    fun usedPercentFromSizes_calculatesStoragePercent() {
        assertEquals(25.0, MonitorJsonParser.usedPercentFromSizes(500.0, 2000.0) ?: -1.0, 0.001)
    }

    @Test
    fun usedPercentFromSizes_matchesMonitorApiRootFilesystemSample() {
        assertEquals(
            20.307,
            MonitorJsonParser.usedPercentFromSizes(
                used = 100_424_171_520.0,
                total = 494_532_001_792.0,
            ) ?: -1.0,
            0.001,
        )
    }

    @Test
    fun usedPercentFromSizes_ignoresInvalidTotals() {
        assertNull(MonitorJsonParser.usedPercentFromSizes(500.0, 0.0))
        assertNull(MonitorJsonParser.usedPercentFromSizes(500.0, null))
    }
}
