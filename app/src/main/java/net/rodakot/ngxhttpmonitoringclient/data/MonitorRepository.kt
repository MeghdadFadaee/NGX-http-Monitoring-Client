package net.rodakot.ngxhttpmonitoringclient.data

import android.content.Context
import net.rodakot.ngxhttpmonitoringclient.domain.AlertEvaluator
import net.rodakot.ngxhttpmonitoringclient.domain.HistorySamplingPolicy
import net.rodakot.ngxhttpmonitoringclient.domain.UrlRules
import net.rodakot.ngxhttpmonitoringclient.model.AlertEvent
import net.rodakot.ngxhttpmonitoringclient.model.AlertOverrides
import net.rodakot.ngxhttpmonitoringclient.model.AuthConfig
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.ServerEditorDraft
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONException

class MonitorRepository(context: Context) {
    private val database = MonitorDatabase(context.applicationContext)
    private val cipher = CredentialCipher()
    private val client = MonitorHttpClient()
    private val alertCooldownMillis = TimeUnit.MINUTES.toMillis(30)

    fun servers(): List<ServerProfile> = database.listServers()

    fun latestSummaries(serverIds: List<String>): Map<String, MetricSummary> = database.latestSummaries(serverIds)

    fun history(serverId: String): List<MetricSummary> = database.samplesForServer(serverId)

    fun alerts(): List<AlertEvent> = database.alerts()

    fun draftFor(server: ServerProfile?): ServerEditorDraft {
        if (server == null) return ServerEditorDraft()
        return ServerEditorDraft(
            id = server.id,
            name = server.name,
            baseUrl = server.baseUrl,
            tags = server.tags.joinToString(", "),
            favorite = server.favorite,
            allowHttp = server.allowHttp,
            enabled = server.enabled,
            token = cipher.decrypt(server.tokenCipherText).orEmpty(),
            basicUsername = cipher.decrypt(server.basicUserCipherText).orEmpty(),
            basicPassword = cipher.decrypt(server.basicPasswordCipherText).orEmpty(),
            cpuThreshold = server.alertOverrides.cpuPercent?.trimmed().orEmpty(),
            memoryThreshold = server.alertOverrides.memoryPercent?.trimmed().orEmpty(),
            diskThreshold = server.alertOverrides.diskPercent?.trimmed().orEmpty(),
            latencyThreshold = server.alertOverrides.latencyP95Millis?.trimmed().orEmpty(),
            errors5xxThreshold = server.alertOverrides.errors5xx?.toString().orEmpty(),
        )
    }

    fun saveDraft(draft: ServerEditorDraft): ServerProfile {
        val server = buildServer(draft)
        database.upsertServer(server)
        return server
    }

    private fun buildServer(draft: ServerEditorDraft): ServerProfile {
        val baseUrl = UrlRules.normalizeBaseUrl(draft.baseUrl)
        UrlRules.validateHttpPolicy(baseUrl, draft.allowHttp)
        return ServerProfile(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = draft.name.trim().ifBlank { baseUrl },
            baseUrl = baseUrl,
            tags = draft.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            favorite = draft.favorite,
            allowHttp = draft.allowHttp,
            enabled = draft.enabled,
            tokenCipherText = cipher.encrypt(draft.token),
            basicUserCipherText = cipher.encrypt(draft.basicUsername),
            basicPasswordCipherText = cipher.encrypt(draft.basicPassword),
            alertOverrides = AlertOverrides(
                cpuPercent = draft.cpuThreshold.toDoubleOrNull(),
                memoryPercent = draft.memoryThreshold.toDoubleOrNull(),
                diskPercent = draft.diskThreshold.toDoubleOrNull(),
                latencyP95Millis = draft.latencyThreshold.toDoubleOrNull(),
                errors5xx = draft.errors5xxThreshold.toIntOrNull(),
            ),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun deleteServer(serverId: String) = database.deleteServer(serverId)

    fun authFor(server: ServerProfile): AuthConfig = AuthConfig(
        token = cipher.decrypt(server.tokenCipherText),
        basicUsername = cipher.decrypt(server.basicUserCipherText),
        basicPassword = cipher.decrypt(server.basicPasswordCipherText),
    )

    fun testConnection(draft: ServerEditorDraft): Result<String> = runCatching {
        val server = buildServer(draft.copy(id = draft.id ?: "test"))
        client.fetchHealth(server, authFor(server))
        "Connection works"
    }

    fun fetchApiSummary(server: ServerProfile, forcePersist: Boolean = false): MetricSummary {
        return refreshServer(server, forcePersist).summary
    }

    fun refreshServer(server: ServerProfile, forcePersist: Boolean = false): RefreshResult {
        return try {
            val payload = client.fetchApi(server, authFor(server))
            val parsed = MonitorJsonParser.parse(server.id, payload)
            val events = AlertEvaluator.evaluate(server, parsed)
            val status = if (events.isEmpty()) ServerStatus.Online else ServerStatus.Degraded
            val summary = parsed.copy(
                status = status,
                message = if (status == ServerStatus.Online) "Live" else "Threshold breached",
            )
            persistSample(server, summary, payload, forcePersist)
            RefreshResult(summary, recordAlerts(server, summary))
        } catch (exception: MonitorClientException) {
            val summary = MetricSummary(
                serverId = server.id,
                timestampMillis = System.currentTimeMillis(),
                status = exception.monitorStatus,
                message = exception.message,
            )
            persistSample(server, summary, null, forcePersist = true)
            RefreshResult(summary, recordAlerts(server, summary))
        } catch (exception: JSONException) {
            val summary = MetricSummary(
                serverId = server.id,
                timestampMillis = System.currentTimeMillis(),
                status = ServerStatus.Error,
                message = "Malformed monitor JSON",
            )
            persistSample(server, summary, null, forcePersist = true)
            RefreshResult(summary, recordAlerts(server, summary))
        } catch (exception: Exception) {
            val summary = MetricSummary(
                serverId = server.id,
                timestampMillis = System.currentTimeMillis(),
                status = ServerStatus.Offline,
                message = exception.message ?: "Unable to reach server",
            )
            persistSample(server, summary, null, forcePersist = true)
            RefreshResult(summary, recordAlerts(server, summary))
        }
    }

    fun streamLive(server: ServerProfile, onSummary: (MetricSummary) -> Unit) {
        client.streamLive(server, authFor(server)) { payload ->
            val parsed = MonitorJsonParser.parse(server.id, payload)
            val events = AlertEvaluator.evaluate(server, parsed)
            val status = if (events.isEmpty()) ServerStatus.Online else ServerStatus.Degraded
            val summary = parsed.copy(
                status = status,
                message = if (status == ServerStatus.Online) "Live" else "Threshold breached",
            )
            persistSample(server, summary, payload, forcePersist = false)
            recordAlerts(server, summary)
            onSummary(summary)
        }
    }

    fun recordAlerts(server: ServerProfile, summary: MetricSummary): List<AlertEvent> {
        val events = AlertEvaluator.evaluate(server, summary)
        val cutoff = System.currentTimeMillis() - alertCooldownMillis
        val fresh = events.filterNot { database.hasRecentAlert(server.id, it.title, cutoff) }
        fresh.forEach(database::insertAlert)
        return fresh
    }

    fun pruneHistory() = database.pruneOldSamples()

    private fun persistSample(server: ServerProfile, summary: MetricSummary, rawJson: String?, forcePersist: Boolean) {
        val now = summary.timestampMillis
        val lastSummary = database.lastSampleTimestamp(server.id, rawOnly = false)
        val shouldStoreSummary = HistorySamplingPolicy.shouldStoreSummary(now, lastSummary, forcePersist)
        if (!shouldStoreSummary) return

        val lastRaw = database.lastSampleTimestamp(server.id, rawOnly = true)
        val shouldStoreRaw = HistorySamplingPolicy.shouldStoreRaw(now, lastRaw, rawJson != null, forcePersist)
        database.insertSample(summary.copy(rawJson = if (shouldStoreRaw) rawJson else null))
        database.pruneOldSamples(now)
    }

    private fun Double.trimmed(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
}

data class RefreshResult(
    val summary: MetricSummary,
    val alerts: List<AlertEvent>,
)
