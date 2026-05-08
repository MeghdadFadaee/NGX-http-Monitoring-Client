package net.rodakot.ngxhttpmonitoringclient.domain

import net.rodakot.ngxhttpmonitoringclient.model.AlertOverrides
import net.rodakot.ngxhttpmonitoringclient.model.AlertSeverity
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    private val server = ServerProfile(
        id = "server-1",
        name = "edge-1",
        baseUrl = "https://edge-1.example.com",
    )

    @Test
    fun evaluate_returnsNoAlertsForHealthySummary() {
        val alerts = AlertEvaluator.evaluate(
            server,
            MetricSummary(
                serverId = server.id,
                timestampMillis = 1L,
                status = ServerStatus.Online,
                cpuPercent = 20.0,
                memoryPercent = 40.0,
                diskPercent = 50.0,
                latencyP95Millis = 80.0,
                errors5xx = 0,
            ),
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun evaluate_usesPerServerOverrides() {
        val alerts = AlertEvaluator.evaluate(
            server.copy(alertOverrides = AlertOverrides(cpuPercent = 50.0)),
            MetricSummary(
                serverId = server.id,
                timestampMillis = 1L,
                status = ServerStatus.Online,
                cpuPercent = 51.0,
            ),
        )

        assertEquals(listOf("High CPU"), alerts.map { it.title })
    }

    @Test
    fun evaluate_marksAuthFailureCritical() {
        val alerts = AlertEvaluator.evaluate(
            server,
            MetricSummary(
                serverId = server.id,
                timestampMillis = 1L,
                status = ServerStatus.AuthFailed,
                message = "Token rejected",
            ),
        )

        assertEquals(AlertSeverity.Critical, alerts.single().severity)
        assertEquals("Authentication failed", alerts.single().title)
    }
}
