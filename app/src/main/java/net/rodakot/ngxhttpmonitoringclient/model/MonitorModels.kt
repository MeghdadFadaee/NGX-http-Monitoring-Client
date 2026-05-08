package net.rodakot.ngxhttpmonitoringclient.model

const val DefaultCpuThreshold = 85.0
const val DefaultMemoryThreshold = 90.0
const val DefaultDiskThreshold = 90.0
const val DefaultLatencyP95ThresholdMs = 1_000.0
const val DefaultErrors5xxThreshold = 5
const val HistoryRetentionDays = 30
const val SummarySampleIntervalMillis = 60_000L
const val RawSnapshotIntervalMillis = 15 * 60_000L

data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val allowHttp: Boolean = false,
    val enabled: Boolean = true,
    val tokenCipherText: String? = null,
    val basicUserCipherText: String? = null,
    val basicPasswordCipherText: String? = null,
    val alertOverrides: AlertOverrides = AlertOverrides(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class AuthConfig(
    val token: String? = null,
    val basicUsername: String? = null,
    val basicPassword: String? = null,
)

data class AlertOverrides(
    val cpuPercent: Double? = null,
    val memoryPercent: Double? = null,
    val diskPercent: Double? = null,
    val latencyP95Millis: Double? = null,
    val errors5xx: Int? = null,
)

enum class ServerStatus {
    Unknown,
    Online,
    Degraded,
    Offline,
    AuthFailed,
    Forbidden,
    Missing,
    RateLimited,
    Insecure,
    Error,
}

enum class AlertSeverity {
    Info,
    Warning,
    Critical,
}

data class MetricSummary(
    val serverId: String,
    val timestampMillis: Long,
    val status: ServerStatus = ServerStatus.Unknown,
    val message: String = "Waiting for data",
    val cpuPercent: Double? = null,
    val memoryPercent: Double? = null,
    val diskPercent: Double? = null,
    val requestRate: Double? = null,
    val latencyP95Millis: Double? = null,
    val errors4xx: Int? = null,
    val errors5xx: Int? = null,
    val activeConnections: Int? = null,
    val rawJson: String? = null,
)

data class AlertEvent(
    val id: String,
    val serverId: String,
    val timestampMillis: Long,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val resolved: Boolean = false,
)

data class ServerEditorDraft(
    val id: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val tags: String = "",
    val favorite: Boolean = false,
    val allowHttp: Boolean = false,
    val enabled: Boolean = true,
    val token: String = "",
    val basicUsername: String = "",
    val basicPassword: String = "",
    val cpuThreshold: String = "",
    val memoryThreshold: String = "",
    val diskThreshold: String = "",
    val latencyThreshold: String = "",
    val errors5xxThreshold: String = "",
    val testMessage: String? = null,
    val isTesting: Boolean = false,
)

enum class DetailTab(val label: String) {
    Overview("Overview"),
    System("System"),
    Nginx("Nginx"),
    Requests("Requests"),
    Disk("Disk"),
    History("History"),
    Alerts("Alerts"),
    Settings("Settings"),
}

data class MonitorUiState(
    val servers: List<ServerProfile> = emptyList(),
    val summaries: Map<String, MetricSummary> = emptyMap(),
    val history: Map<String, List<MetricSummary>> = emptyMap(),
    val alerts: List<AlertEvent> = emptyList(),
    val selectedServerId: String? = null,
    val showDetail: Boolean = false,
    val selectedTab: DetailTab = DetailTab.Overview,
    val query: String = "",
    val tagFilter: String? = null,
    val editorDraft: ServerEditorDraft? = null,
    val globalMessage: String? = null,
    val liveMonitoring: Boolean = false,
)
