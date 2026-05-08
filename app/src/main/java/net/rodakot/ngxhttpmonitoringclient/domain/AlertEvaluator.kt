package net.rodakot.ngxhttpmonitoringclient.domain

import net.rodakot.ngxhttpmonitoringclient.model.AlertEvent
import net.rodakot.ngxhttpmonitoringclient.model.AlertSeverity
import net.rodakot.ngxhttpmonitoringclient.model.DefaultCpuThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DefaultDiskThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DefaultErrors5xxThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DefaultLatencyP95ThresholdMs
import net.rodakot.ngxhttpmonitoringclient.model.DefaultMemoryThreshold
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import java.util.UUID

object AlertEvaluator {
    fun evaluate(server: ServerProfile, summary: MetricSummary): List<AlertEvent> {
        val events = mutableListOf<AlertEvent>()
        val now = summary.timestampMillis
        when (summary.status) {
            ServerStatus.AuthFailed -> events += event(server, now, AlertSeverity.Critical, "Authentication failed", summary.message)
            ServerStatus.Forbidden -> events += event(server, now, AlertSeverity.Critical, "Access denied", summary.message)
            ServerStatus.Missing -> events += event(server, now, AlertSeverity.Critical, "Monitor endpoint missing", summary.message)
            ServerStatus.RateLimited -> events += event(server, now, AlertSeverity.Warning, "Rate limited", summary.message)
            ServerStatus.Offline -> events += event(server, now, AlertSeverity.Critical, "Server unreachable", summary.message)
            ServerStatus.Error -> events += event(server, now, AlertSeverity.Warning, "Monitor error", summary.message)
            else -> Unit
        }

        val cpuLimit = server.alertOverrides.cpuPercent ?: DefaultCpuThreshold
        if ((summary.cpuPercent ?: 0.0) >= cpuLimit) {
            events += event(server, now, AlertSeverity.Warning, "High CPU", "CPU is ${summary.cpuPercent.formatPercent()} (limit ${cpuLimit.formatPercent()})")
        }

        val memoryLimit = server.alertOverrides.memoryPercent ?: DefaultMemoryThreshold
        if ((summary.memoryPercent ?: 0.0) >= memoryLimit) {
            events += event(server, now, AlertSeverity.Warning, "High memory", "Memory is ${summary.memoryPercent.formatPercent()} (limit ${memoryLimit.formatPercent()})")
        }

        val diskLimit = server.alertOverrides.diskPercent ?: DefaultDiskThreshold
        if ((summary.diskPercent ?: 0.0) >= diskLimit) {
            events += event(server, now, AlertSeverity.Critical, "Disk pressure", "Disk usage is ${summary.diskPercent.formatPercent()} (limit ${diskLimit.formatPercent()})")
        }

        val latencyLimit = server.alertOverrides.latencyP95Millis ?: DefaultLatencyP95ThresholdMs
        if ((summary.latencyP95Millis ?: 0.0) >= latencyLimit) {
            events += event(server, now, AlertSeverity.Warning, "High latency", "p95 latency is ${summary.latencyP95Millis?.toInt()} ms (limit ${latencyLimit.toInt()} ms)")
        }

        val errorsLimit = server.alertOverrides.errors5xx ?: DefaultErrors5xxThreshold
        if ((summary.errors5xx ?: 0) >= errorsLimit) {
            events += event(server, now, AlertSeverity.Critical, "5xx spike", "${summary.errors5xx} server errors observed (limit $errorsLimit)")
        }

        return events.distinctBy { it.title }
    }

    fun statusFor(summary: MetricSummary, server: ServerProfile): ServerStatus {
        if (summary.status != ServerStatus.Online && summary.status != ServerStatus.Unknown) return summary.status
        return if (evaluate(server, summary).any { it.severity == AlertSeverity.Critical }) {
            ServerStatus.Degraded
        } else {
            ServerStatus.Online
        }
    }

    private fun event(
        server: ServerProfile,
        now: Long,
        severity: AlertSeverity,
        title: String,
        message: String,
    ) = AlertEvent(
        id = UUID.randomUUID().toString(),
        serverId = server.id,
        timestampMillis = now,
        severity = severity,
        title = title,
        message = message,
    )

    private fun Double?.formatPercent(): String = when (this) {
        null -> "unknown"
        else -> "${toInt()}%"
    }
}
