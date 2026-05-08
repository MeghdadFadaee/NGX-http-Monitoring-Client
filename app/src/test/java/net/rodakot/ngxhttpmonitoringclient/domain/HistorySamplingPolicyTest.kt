package net.rodakot.ngxhttpmonitoringclient.domain

import net.rodakot.ngxhttpmonitoringclient.model.RawSnapshotIntervalMillis
import net.rodakot.ngxhttpmonitoringclient.model.SummarySampleIntervalMillis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySamplingPolicyTest {
    @Test
    fun shouldStoreSummary_everyMinuteByDefault() {
        assertFalse(HistorySamplingPolicy.shouldStoreSummary(10_000L, 0L, force = false))
        assertTrue(HistorySamplingPolicy.shouldStoreSummary(SummarySampleIntervalMillis, 0L, force = false))
    }

    @Test
    fun shouldStoreRaw_everyFifteenMinutesWhenPayloadExists() {
        assertFalse(HistorySamplingPolicy.shouldStoreRaw(10_000L, 0L, hasRawPayload = true, force = false))
        assertTrue(HistorySamplingPolicy.shouldStoreRaw(RawSnapshotIntervalMillis, 0L, hasRawPayload = true, force = false))
        assertFalse(HistorySamplingPolicy.shouldStoreRaw(RawSnapshotIntervalMillis, 0L, hasRawPayload = false, force = true))
    }
}
