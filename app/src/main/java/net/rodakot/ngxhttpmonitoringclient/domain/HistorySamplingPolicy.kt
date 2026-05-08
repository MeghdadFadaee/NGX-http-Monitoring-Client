package net.rodakot.ngxhttpmonitoringclient.domain

import net.rodakot.ngxhttpmonitoringclient.model.RawSnapshotIntervalMillis
import net.rodakot.ngxhttpmonitoringclient.model.SummarySampleIntervalMillis

object HistorySamplingPolicy {
    fun shouldStoreSummary(nowMillis: Long, lastSummaryMillis: Long?, force: Boolean): Boolean {
        return force || lastSummaryMillis == null || nowMillis - lastSummaryMillis >= SummarySampleIntervalMillis
    }

    fun shouldStoreRaw(nowMillis: Long, lastRawMillis: Long?, hasRawPayload: Boolean, force: Boolean): Boolean {
        if (!hasRawPayload) return false
        return force || lastRawMillis == null || nowMillis - lastRawMillis >= RawSnapshotIntervalMillis
    }
}
