package net.rodakot.ngxhttpmonitoringclient

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rodakot.ngxhttpmonitoringclient.data.MonitorRepository
import net.rodakot.ngxhttpmonitoringclient.model.DetailTab
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.MonitorUiState
import net.rodakot.ngxhttpmonitoringclient.model.ServerEditorDraft
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import kotlin.coroutines.coroutineContext

class MonitorController(context: Context) {
    private val repository = MonitorRepository(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveJobs = mutableMapOf<String, Job>()
    private val _state = MutableStateFlow(MonitorUiState())

    val state: StateFlow<MonitorUiState> = _state

    init {
        reload()
    }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val servers = repository.servers()
            val selected = _state.value.selectedServerId?.takeIf { id -> servers.any { it.id == id } }
                ?: servers.firstOrNull()?.id
            val summaries = repository.latestSummaries(servers.map { it.id })
            val history = selected?.let { mapOf(it to repository.history(it)) }.orEmpty()
            val alerts = repository.alerts()
            val routeDiagnostics = repository.routeDiagnostics()
            _state.update {
                it.copy(
                    servers = servers,
                    selectedServerId = selected,
                    summaries = summaries,
                    history = history,
                    routeDiagnostics = routeDiagnostics,
                    alerts = alerts,
                    globalMessage = null,
                )
            }
            if (_state.value.liveMonitoring) restartLiveMonitoring(servers)
        }
    }

    fun startLiveMonitoring() {
        _state.update { it.copy(liveMonitoring = true) }
        restartLiveMonitoring(_state.value.servers)
    }

    fun stopLiveMonitoring() {
        liveJobs.values.forEach { it.cancel() }
        liveJobs.clear()
        _state.update { it.copy(liveMonitoring = false) }
    }

    fun close() {
        stopLiveMonitoring()
        scope.cancel()
    }

    fun refreshAll() {
        scope.launch(Dispatchers.IO) {
            _state.value.servers.filter { it.enabled }.forEach { server ->
                updateSummary(repository.fetchApiSummary(server, forcePersist = true))
            }
            reloadAlertsAndSelectedHistory()
        }
    }

    fun refreshSelected() {
        val server = selectedServer() ?: return
        scope.launch(Dispatchers.IO) {
            updateSummary(repository.fetchApiSummary(server, forcePersist = true))
            reloadAlertsAndSelectedHistory()
        }
    }

    fun selectServer(serverId: String) {
        _state.update { it.copy(selectedServerId = serverId, showDetail = true, selectedTab = DetailTab.Overview) }
        scope.launch(Dispatchers.IO) {
            val history = repository.history(serverId)
            _state.update { it.copy(history = it.history + (serverId to history)) }
        }
    }

    fun showDashboard() {
        _state.update { it.copy(showDetail = false) }
    }

    fun setDetailTab(tab: DetailTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setTagFilter(tag: String?) {
        _state.update { it.copy(tagFilter = tag) }
    }

    fun showAddServer() {
        _state.update { it.copy(editorDraft = ServerEditorDraft()) }
    }

    fun showEditSelectedServer() {
        val server = selectedServer() ?: return
        _state.update { it.copy(editorDraft = repository.draftFor(server)) }
    }

    fun dismissEditor() {
        _state.update { it.copy(editorDraft = null) }
    }

    fun updateDraft(transform: (ServerEditorDraft) -> ServerEditorDraft) {
        _state.update { current ->
            val draft = current.editorDraft ?: return@update current
            current.copy(editorDraft = transform(draft))
        }
    }

    fun saveEditor() {
        val draft = _state.value.editorDraft ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { repository.saveDraft(draft) }
                .onSuccess { server ->
                    val servers = repository.servers()
                    val summaries = repository.latestSummaries(servers.map { it.id })
                    val history = repository.history(server.id)
                    val routeDiagnostics = repository.routeDiagnostics()
                    _state.update {
                        it.copy(
                            servers = servers,
                            summaries = summaries,
                            routeDiagnostics = routeDiagnostics,
                            selectedServerId = server.id,
                            showDetail = true,
                            selectedTab = DetailTab.Overview,
                            history = it.history + (server.id to history),
                            editorDraft = null,
                            globalMessage = "Server saved",
                        )
                    }
                    if (_state.value.liveMonitoring) restartLiveMonitoring(servers)
                }
                .onFailure { error ->
                    _state.update { it.copy(editorDraft = draft.copy(testMessage = error.message ?: "Unable to save server")) }
                }
        }
    }

    fun testEditorConnection() {
        val draft = _state.value.editorDraft ?: return
        _state.update { it.copy(editorDraft = draft.copy(isTesting = true, testMessage = null)) }
        scope.launch(Dispatchers.IO) {
            val result = repository.testConnection(draft)
            _state.update { current ->
                current.copy(
                    editorDraft = current.editorDraft?.copy(
                        isTesting = false,
                        testMessage = result.getOrElse { it.message ?: "Connection failed" },
                    ),
                )
            }
        }
    }

    fun deleteSelectedServer() {
        val server = selectedServer() ?: return
        scope.launch(Dispatchers.IO) {
            liveJobs.remove(server.id)?.cancel()
            repository.deleteServer(server.id)
            val servers = repository.servers()
            val selected = servers.firstOrNull()?.id
            _state.update {
                it.copy(
                    servers = servers,
                    selectedServerId = selected,
                    showDetail = false,
                    selectedTab = DetailTab.Overview,
                    summaries = repository.latestSummaries(servers.map { item -> item.id }),
                    history = selected?.let { id -> mapOf(id to repository.history(id)) }.orEmpty(),
                    routeDiagnostics = repository.routeDiagnostics(),
                    alerts = repository.alerts(),
                    globalMessage = "Server deleted",
                )
            }
        }
    }

    private fun restartLiveMonitoring(servers: List<ServerProfile>) {
        val enabledIds = servers.filter { it.enabled }.map { it.id }.toSet()
        liveJobs.filterKeys { it !in enabledIds }.values.forEach { it.cancel() }
        liveJobs.keys.removeAll { it !in enabledIds }
        servers.filter { it.enabled && liveJobs[it.id]?.isActive != true }.forEach { server ->
            liveJobs[server.id] = scope.launch(Dispatchers.IO) { liveLoop(server) }
        }
    }

    private suspend fun liveLoop(server: ServerProfile) {
        var backoffMillis = 2_000L
        while (coroutineContext.isActive) {
            runCatching {
                repository.streamLive(server) { summary ->
                    backoffMillis = 2_000L
                    updateSummary(summary)
                }
            }.onFailure {
                updateSummary(repository.fetchApiSummary(server, forcePersist = true))
                delay(backoffMillis)
                backoffMillis = (backoffMillis * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun updateSummary(summary: MetricSummary) {
        _state.update { current ->
            current.copy(summaries = current.summaries + (summary.serverId to summary))
        }
    }

    private suspend fun reloadAlertsAndSelectedHistory() {
        val selected = _state.value.selectedServerId
        val history = selected?.let { repository.history(it) }
        val alerts = repository.alerts()
        val routeDiagnostics = repository.routeDiagnostics()
        withContext(Dispatchers.Main.immediate) {
            _state.update {
                it.copy(
                    alerts = alerts,
                    routeDiagnostics = routeDiagnostics,
                    history = if (selected != null && history != null) it.history + (selected to history) else it.history,
                )
            }
        }
    }

    private fun selectedServer(): ServerProfile? {
        val selected = _state.value.selectedServerId ?: return null
        return _state.value.servers.firstOrNull { it.id == selected }
    }
}
