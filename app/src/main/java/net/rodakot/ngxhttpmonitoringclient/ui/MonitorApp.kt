package net.rodakot.ngxhttpmonitoringclient.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.rodakot.ngxhttpmonitoringclient.MonitorController
import net.rodakot.ngxhttpmonitoringclient.model.AlertEvent
import net.rodakot.ngxhttpmonitoringclient.model.AlertSeverity
import net.rodakot.ngxhttpmonitoringclient.model.DefaultCpuThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DefaultDiskThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DefaultErrors5xxThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DefaultLatencyP95ThresholdMs
import net.rodakot.ngxhttpmonitoringclient.model.DefaultMemoryThreshold
import net.rodakot.ngxhttpmonitoringclient.model.DetailTab
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.MonitorUiState
import net.rodakot.ngxhttpmonitoringclient.model.NetworkIssue
import net.rodakot.ngxhttpmonitoringclient.model.RouteDiagnostics
import net.rodakot.ngxhttpmonitoringclient.model.ServerEditorDraft
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorApp(controller: MonitorController) {
    val state by controller.state.collectAsState()
    val selected = state.servers.firstOrNull { it.id == state.selectedServerId }

    DisposableEffect(controller) {
        controller.startLiveMonitoring()
        onDispose { controller.stopLiveMonitoring() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.showDetail && selected != null) selected.name else "Command Deck",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (state.showDetail && selected != null) selected.baseUrl else "NGX fleet monitor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (state.showDetail) {
                        TextButton(onClick = controller::showDashboard) { Text("Back") }
                    }
                },
                actions = {
                    TextButton(onClick = controller::refreshAll) { Text("Sync") }
                    LiveSwitch(
                        live = state.liveMonitoring,
                        onClick = {
                            if (state.liveMonitoring) controller.stopLiveMonitoring() else controller.startLiveMonitoring()
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.showDetail && selected != null) {
            ServerCockpit(
                state = state,
                server = selected,
                controller = controller,
                modifier = Modifier.padding(padding),
            )
        } else {
            FleetCommandDeck(
                state = state,
                controller = controller,
                modifier = Modifier.padding(padding),
            )
        }
    }

    state.editorDraft?.let { draft ->
        ServerEditorDialog(
            draft = draft,
            onDismiss = controller::dismissEditor,
            onSave = controller::saveEditor,
            onTest = controller::testEditorConnection,
            onChange = controller::updateDraft,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetCommandDeck(
    state: MonitorUiState,
    controller: MonitorController,
    modifier: Modifier = Modifier,
) {
    val filtered = filteredServers(state)
    val tags = state.servers.flatMap { it.tags }.distinct().sorted()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FleetHero(
                state = state,
                onAdd = controller::showAddServer,
            )
        }
        item {
            CommandFilters(
                query = state.query,
                tags = tags,
                selectedTag = state.tagFilter,
                onQuery = controller::setQuery,
                onTag = controller::setTagFilter,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Priority Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${filtered.size} visible", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.servers.isEmpty()) {
            item { EmptyFleet(onAdd = controller::showAddServer) }
        } else if (filtered.isEmpty()) {
            item { EmptyPanel("No servers match the current command filters.") }
        } else {
            items(filtered, key = { it.id }) { server ->
                ServerCommandRow(
                    server = server,
                    summary = state.summaries[server.id],
                    diagnostics = state.routeDiagnostics[server.id],
                    onClick = { controller.selectServer(server.id) },
                )
            }
        }
    }
}

@Composable
private fun FleetHero(state: MonitorUiState, onAdd: () -> Unit) {
    val counters = fleetCounters(state)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        BoxWithConstraints(Modifier.padding(18.dp)) {
            val compact = maxWidth < 420.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    HeroCopy(state, counters, onAdd)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        FleetGauge(counters, Modifier.size(142.dp))
                        CounterStack(counters, Modifier.weight(1f))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeroCopy(state, counters, onAdd, Modifier.weight(1f))
                    FleetGauge(counters, Modifier.size(154.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroCopy(state: MonitorUiState, counters: FleetCounters, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NOC Overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onAdd, shape = RoundedCornerShape(8.dp)) { Text("Add") }
        }
        Text(
            text = "${state.servers.size} servers, ${counters.critical} critical, ${counters.watch} on watch",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CounterChip("Online", counters.online, StatusOnline, Modifier.weight(1f))
            CounterChip("Watch", counters.watch, StatusWarning, Modifier.weight(1f))
            CounterChip("Down", counters.critical, StatusCritical, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CounterStack(counters: FleetCounters, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CounterChip("Online", counters.online, StatusOnline)
        CounterChip("Watch", counters.watch, StatusWarning)
        CounterChip("Down", counters.critical, StatusCritical)
        CounterChip("Waiting", counters.waiting, StatusUnknown)
    }
}

@Composable
private fun CounterChip(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.13f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(value.toString(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun FleetGauge(counters: FleetCounters, modifier: Modifier = Modifier) {
    val total = counters.total.coerceAtLeast(1)
    val onlineSweep = counters.online / total.toFloat()
    val watchSweep = counters.watch / total.toFloat()
    val criticalSweep = counters.critical / total.toFloat()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 18f, cap = StrokeCap.Round)
            drawArc(StatusUnknown.copy(alpha = 0.18f), -90f, 360f, false, style = stroke)
            var start = -90f
            drawArc(StatusOnline, start, onlineSweep * 360f, false, style = stroke)
            start += onlineSweep * 360f
            drawArc(StatusWarning, start, watchSweep * 360f, false, style = stroke)
            start += watchSweep * 360f
            drawArc(StatusCritical, start, criticalSweep * 360f, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(counters.total.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("servers", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandFilters(
    query: String,
    tags: List<String>,
    selectedTag: String?,
    onQuery: (String) -> Unit,
    onTag: (String?) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find server, URL, or tag") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedTag == null, onClick = { onTag(null) }, label = { Text("All") })
                    tags.forEach { tag ->
                        FilterChip(selected = selectedTag == tag, onClick = { onTag(tag) }, label = { Text(tag) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerCommandRow(
    server: ServerProfile,
    summary: MetricSummary?,
    diagnostics: RouteDiagnostics?,
    onClick: () -> Unit,
) {
    val status = summary?.status ?: ServerStatus.Unknown
    val health = healthScore(summary)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.heightIn(min = 118.dp)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(colorForStatus(status)),
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (server.favorite) LabelCapsule("PIN", MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            server.baseUrl,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    HealthBadge(health, status)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelCapsule(statusLabel(status).uppercase(), colorForStatus(status))
                    if (!server.enabled) LabelCapsule("OFF", StatusUnknown)
                    if (server.allowHttp) LabelCapsule("HTTP", StatusInsecure)
                    RouteBadges(diagnostics)
                    server.tags.take(3).forEach { LabelCapsule(it.uppercase(), MaterialTheme.colorScheme.secondary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniMetric("CPU", summary?.cpuPercent.formatPercent(), colorForPercent(summary?.cpuPercent), Modifier.weight(1f))
                    MiniMetric("MEM", summary?.memoryPercent.formatPercent(), colorForPercent(summary?.memoryPercent), Modifier.weight(1f))
                    MiniMetric("RPS", summary?.requestRate.formatNumber(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MiniMetric("5XX", summary?.errors5xx?.toString() ?: "--", StatusCritical, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ServerCockpit(
    state: MonitorUiState,
    server: ServerProfile,
    controller: MonitorController,
    modifier: Modifier = Modifier,
) {
    val summary = state.summaries[server.id]
    val history = state.history[server.id].orEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CockpitHero(server, summary, controller)
        }
        item {
            RoutePanel(state.routeDiagnostics[server.id])
        }
        item {
            CockpitTabs(selected = state.selectedTab, onSelect = controller::setDetailTab)
        }
        when (state.selectedTab) {
            DetailTab.Overview -> item { OverviewCockpit(summary, history) }
            DetailTab.System -> item { SystemCockpit(summary) }
            DetailTab.Nginx -> item { NginxCockpit(summary) }
            DetailTab.Requests -> item { RequestsCockpit(summary, history) }
            DetailTab.Disk -> item { DiskCockpit(summary, history) }
            DetailTab.History -> item { HistoryCockpit(history) }
            DetailTab.Alerts -> {
                val alerts = state.alerts.filter { it.serverId == server.id }
                if (alerts.isEmpty()) item { EmptyPanel("No recent alerts for this server.") }
                else items(alerts) { alert -> AlertTicket(alert) }
            }
            DetailTab.Settings -> item { SettingsCockpit(server, controller) }
        }
    }
}

@Composable
private fun CockpitHero(server: ServerProfile, summary: MetricSummary?, controller: MonitorController) {
    val status = summary?.status ?: ServerStatus.Unknown
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HealthRing(
                    score = healthScore(summary),
                    color = colorForStatus(status),
                    modifier = Modifier.size(92.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(statusLabel(status), color = colorForStatus(status), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(summary?.message ?: "Waiting for monitor data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary?.timestampMillis.formatTime(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = controller::refreshSelected, shape = RoundedCornerShape(8.dp)) { Text("Pulse") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("CPU", summary?.cpuPercent.formatPercent(), colorForPercent(summary?.cpuPercent), Modifier.weight(1f))
                MiniMetric("MEM", summary?.memoryPercent.formatPercent(), colorForPercent(summary?.memoryPercent), Modifier.weight(1f))
                MiniMetric("DISK", summary?.diskPercent.formatPercent(), colorForPercent(summary?.diskPercent), Modifier.weight(1f))
                MiniMetric("P95", summary?.latencyP95Millis.formatMillis(), StatusWarning, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoutePanel(diagnostics: RouteDiagnostics?) {
    DataPanel("Route") {
        if (diagnostics == null) {
            Text("No route check has completed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleRow("Network", diagnostics.activeNetwork)
                RuleRow("Route", diagnostics.routeUsed.label)
                RuleRow("DNS", diagnostics.dnsResult)
                RuleRow("VPN", if (diagnostics.vpnActive) "Active" else "Inactive")
                RuleRow("Fallback", if (diagnostics.fallbackUsed) "Used" else "Not used")
                RuleRow("Last result", diagnostics.summary)
                RuleRow("Checked", diagnostics.timestampMillis.formatTime())
            }
        }
    }
}

@Composable
private fun CockpitTabs(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailTab.entries.forEach { tab ->
            FilterChip(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                label = { Text(tab.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun OverviewCockpit(summary: MetricSummary?, history: List<MetricSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SignalBoard(summary)
        TelemetryChart("CPU telemetry", history.mapNotNull { it.cpuPercent }, StatusOnline)
        TelemetryChart("Memory telemetry", history.mapNotNull { it.memoryPercent }, StatusWarning)
    }
}

@Composable
private fun SystemCockpit(summary: MetricSummary?) {
    DataPanel("System Load") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Meter("CPU usage", summary?.cpuPercent, 100.0)
            Meter("Memory used", summary?.memoryPercent, 100.0)
            MiniMetric("Last update", summary?.timestampMillis.formatTime(), MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NginxCockpit(summary: MetricSummary?) {
    DataPanel("Nginx Signals") { SignalBoard(summary, system = false) }
}

@Composable
private fun RequestsCockpit(summary: MetricSummary?, history: List<MetricSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DataPanel("Request Path") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("RPS", summary?.requestRate.formatNumber(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MiniMetric("P95", summary?.latencyP95Millis.formatMillis(), StatusWarning, Modifier.weight(1f))
                MiniMetric("4XX", summary?.errors4xx?.toString() ?: "--", StatusInsecure, Modifier.weight(1f))
                MiniMetric("5XX", summary?.errors5xx?.toString() ?: "--", StatusCritical, Modifier.weight(1f))
            }
        }
        TelemetryChart("Request rate", history.mapNotNull { it.requestRate }, MaterialTheme.colorScheme.primary)
        TelemetryChart("p95 latency", history.mapNotNull { it.latencyP95Millis }, StatusCritical)
    }
}

@Composable
private fun DiskCockpit(summary: MetricSummary?, history: List<MetricSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DataPanel("Storage") { Meter("Disk usage", summary?.diskPercent, 100.0) }
        TelemetryChart("Disk telemetry", history.mapNotNull { it.diskPercent }, StatusCritical)
    }
}

@Composable
private fun HistoryCockpit(history: List<MetricSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TelemetryChart("CPU", history.mapNotNull { it.cpuPercent }, StatusOnline)
        TelemetryChart("Memory", history.mapNotNull { it.memoryPercent }, StatusWarning)
        TelemetryChart("Disk", history.mapNotNull { it.diskPercent }, StatusCritical)
        Text("${history.size} retained samples in view", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsCockpit(server: ServerProfile, controller: MonitorController) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DataPanel("Alert Rules") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleRow("CPU limit", "${server.alertOverrides.cpuPercent ?: DefaultCpuThreshold}%")
                RuleRow("Memory limit", "${server.alertOverrides.memoryPercent ?: DefaultMemoryThreshold}%")
                RuleRow("Disk limit", "${server.alertOverrides.diskPercent ?: DefaultDiskThreshold}%")
                RuleRow("p95 latency", "${(server.alertOverrides.latencyP95Millis ?: DefaultLatencyP95ThresholdMs).toInt()} ms")
                RuleRow("5xx limit", "${server.alertOverrides.errors5xx ?: DefaultErrors5xxThreshold}")
                RuleRow("Fallback IPs", server.fallbackIpAddresses.ifEmpty { listOf("None") }.joinToString(", "))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = controller::showEditSelectedServer, shape = RoundedCornerShape(8.dp)) { Text("Edit") }
                    OutlinedButton(onClick = controller::deleteSelectedServer, shape = RoundedCornerShape(8.dp)) { Text("Delete") }
                }
            }
        }
        DataPanel("Privacy") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleRow("Storage", "Server profiles, credentials, samples, alerts, and widget choices stay on this device.")
                RuleRow("Credentials", "Monitor tokens and Basic Auth passwords are encrypted with Android Keystore.")
                RuleRow("Network", "The app connects only to monitor endpoints configured by the user.")
                RuleRow("Backups", "Android cloud backup and device transfer are disabled for app data.")
            }
        }
    }
}

@Composable
private fun SignalBoard(summary: MetricSummary?, system: Boolean = true) {
    DataPanel("Live Signals") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("CONN", summary?.activeConnections?.toString() ?: "--", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                MiniMetric("RPS", summary?.requestRate.formatNumber(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            if (system) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniMetric("CPU", summary?.cpuPercent.formatPercent(), colorForPercent(summary?.cpuPercent), Modifier.weight(1f))
                    MiniMetric("MEM", summary?.memoryPercent.formatPercent(), colorForPercent(summary?.memoryPercent), Modifier.weight(1f))
                    MiniMetric("DISK", summary?.diskPercent.formatPercent(), colorForPercent(summary?.diskPercent), Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("P95", summary?.latencyP95Millis.formatMillis(), StatusWarning, Modifier.weight(1f))
                MiniMetric("4XX", summary?.errors4xx?.toString() ?: "--", StatusInsecure, Modifier.weight(1f))
                MiniMetric("5XX", summary?.errors5xx?.toString() ?: "--", StatusCritical, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DataPanel(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Text(text, modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyFleet(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The command deck is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Add an Nginx monitor endpoint to start tracking live server signals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd, shape = RoundedCornerShape(8.dp)) { Text("Add first server") }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LabelCapsule(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun RouteBadges(diagnostics: RouteDiagnostics?) {
    if (diagnostics == null) return
    if (diagnostics.vpnActive) LabelCapsule("VPN", StatusWarning)
    if (diagnostics.fallbackUsed) LabelCapsule("LAN FALLBACK", StatusOnline)
    if (!diagnostics.success && diagnostics.issue != NetworkIssue.None) {
        LabelCapsule(diagnostics.issue.label.uppercase(), routeIssueColor(diagnostics.issue))
    }
}

@Composable
private fun HealthBadge(score: Int, status: ServerStatus) {
    val color = colorForStatus(status)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            text = score.toString(),
            modifier = Modifier.padding(12.dp),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun HealthRing(score: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14f, cap = StrokeCap.Round)
            drawArc(StatusUnknown.copy(alpha = 0.18f), -90f, 360f, false, style = stroke)
            drawArc(color, -90f, score.coerceIn(0, 100) * 3.6f, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Meter(label: String, value: Double?, maxValue: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(value.formatPercent(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(((value ?: 0.0) / maxValue).coerceIn(0.0, 1.0).toFloat())
                    .height(10.dp)
                    .background(colorForPercent(value)),
            )
        }
    }
}

@Composable
private fun TelemetryChart(title: String, values: List<Double>, color: Color) {
    DataPanel(title) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val grid = Color(0x2A7E8796)
            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.2f)
            }
            if (values.size < 2) return@Canvas
            val maxValue = max(values.maxOrNull() ?: 1.0, 1.0)
            val step = size.width / (values.size - 1)
            val path = Path()
            var latest = Offset.Zero
            values.forEachIndexed { index, value ->
                val x = step * index
                val y = size.height - ((value / maxValue).coerceIn(0.0, 1.0).toFloat() * size.height)
                latest = Offset(x, y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
            drawCircle(color, 7f, latest)
        }
    }
}

@Composable
private fun AlertTicket(alert: AlertEvent) {
    val color = when (alert.severity) {
        AlertSeverity.Critical -> StatusCritical
        AlertSeverity.Warning -> StatusWarning
        AlertSeverity.Info -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(96.dp)
                    .background(color),
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(alert.title, fontWeight = FontWeight.Bold)
                Text(alert.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(alert.timestampMillis.formatTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveSwitch(live: Boolean, onClick: () -> Unit) {
    val color = if (live) StatusOnline else StatusUnknown
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(7.dp))
            Text(if (live) "Live" else "Hold", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerEditorDialog(
    draft: ServerEditorDraft,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onChange: ((ServerEditorDraft) -> ServerEditorDraft) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(if (draft.id == null) "Register Server" else "Server Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                item { EditorField("Display name", draft.name) { value -> onChange { it.copy(name = value) } } }
                item { EditorField("Base URL", draft.baseUrl) { value -> onChange { it.copy(baseUrl = value) } } }
                item { EditorField("Fallback LAN IPs", draft.fallbackIpAddresses) { value -> onChange { it.copy(fallbackIpAddresses = value) } } }
                item { EditorField("Tags", draft.tags) { value -> onChange { it.copy(tags = value) } } }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleRow("Favorite", draft.favorite) { value -> onChange { it.copy(favorite = value) } }
                        ToggleRow("Enabled", draft.enabled) { value -> onChange { it.copy(enabled = value) } }
                        ToggleRow("Allow HTTP", draft.allowHttp) { value -> onChange { it.copy(allowHttp = value) } }
                    }
                }
                item { HorizontalDivider() }
                item { SectionTitle("Auth") }
                item { EditorField("Monitor token", draft.token, secret = true) { value -> onChange { it.copy(token = value) } } }
                item { EditorField("Basic Auth username", draft.basicUsername) { value -> onChange { it.copy(basicUsername = value) } } }
                item { EditorField("Basic Auth password", draft.basicPassword, secret = true) { value -> onChange { it.copy(basicPassword = value) } } }
                item { HorizontalDivider() }
                item { SectionTitle("Alert overrides") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField("CPU %", draft.cpuThreshold, { value -> onChange { it.copy(cpuThreshold = value) } }, Modifier.weight(1f))
                        NumberField("Memory %", draft.memoryThreshold, { value -> onChange { it.copy(memoryThreshold = value) } }, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField("Disk %", draft.diskThreshold, { value -> onChange { it.copy(diskThreshold = value) } }, Modifier.weight(1f))
                        NumberField("p95 ms", draft.latencyThreshold, { value -> onChange { it.copy(latencyThreshold = value) } }, Modifier.weight(1f))
                    }
                }
                item { NumberField("5xx limit", draft.errors5xxThreshold, { value -> onChange { it.copy(errors5xxThreshold = value) } }, Modifier.fillMaxWidth()) }
                draft.testMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                        OutlinedButton(onClick = onTest, enabled = !draft.isTesting, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text(if (draft.isTesting) "Testing" else "Test")
                        }
                        Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun EditorField(label: String, value: String, secret: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private data class FleetCounters(
    val total: Int,
    val online: Int,
    val watch: Int,
    val critical: Int,
    val waiting: Int,
)

private fun fleetCounters(state: MonitorUiState): FleetCounters {
    val summaries = state.servers.map { state.summaries[it.id] }
    val online = summaries.count { it?.status == ServerStatus.Online }
    val watch = summaries.count { it?.status in setOf(ServerStatus.Degraded, ServerStatus.RateLimited, ServerStatus.Error) }
    val critical = summaries.count {
        it?.status in setOf(ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing)
    }
    val waiting = (state.servers.size - online - watch - critical).coerceAtLeast(0)
    return FleetCounters(state.servers.size, online, watch, critical, waiting)
}

private fun filteredServers(state: MonitorUiState): List<ServerProfile> {
    val query = state.query.trim().lowercase()
    return state.servers
        .filter { server -> state.tagFilter?.let { it in server.tags } ?: true }
        .filter { server ->
            query.isBlank() ||
                server.name.lowercase().contains(query) ||
                server.baseUrl.lowercase().contains(query) ||
                server.tags.any { it.lowercase().contains(query) }
        }
        .sortedWith(
            compareByDescending<ServerProfile> { it.favorite }
                .thenBy { statusRank(state.summaries[it.id]?.status) }
                .thenBy { it.name.lowercase() },
        )
}

private fun statusRank(status: ServerStatus?): Int = when (status) {
    ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing -> 0
    ServerStatus.Degraded, ServerStatus.RateLimited, ServerStatus.Error -> 1
    ServerStatus.Unknown, null -> 2
    ServerStatus.Online -> 3
    ServerStatus.Insecure -> 4
}

private fun healthScore(summary: MetricSummary?): Int {
    if (summary == null) return 0
    val statusPenalty = when (summary.status) {
        ServerStatus.Online -> 0
        ServerStatus.Degraded -> 20
        ServerStatus.RateLimited, ServerStatus.Error -> 30
        ServerStatus.Unknown, ServerStatus.Insecure -> 35
        ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing -> 70
    }
    val cpu = ((summary.cpuPercent ?: 0.0) * 0.18).toInt()
    val mem = ((summary.memoryPercent ?: 0.0) * 0.16).toInt()
    val disk = ((summary.diskPercent ?: 0.0) * 0.18).toInt()
    val latency = ((summary.latencyP95Millis ?: 0.0) / 100.0).toInt().coerceAtMost(18)
    val errors = ((summary.errors5xx ?: 0) * 4).coerceAtMost(24)
    return (100 - statusPenalty - cpu - mem - disk - latency - errors).coerceIn(0, 100)
}

private fun statusLabel(status: ServerStatus): String = when (status) {
    ServerStatus.Online -> "Online"
    ServerStatus.Degraded -> "Watch"
    ServerStatus.Offline -> "Offline"
    ServerStatus.AuthFailed -> "Auth failed"
    ServerStatus.Forbidden -> "Blocked"
    ServerStatus.Missing -> "Missing"
    ServerStatus.RateLimited -> "Rate limited"
    ServerStatus.Insecure -> "Insecure"
    ServerStatus.Error -> "Error"
    ServerStatus.Unknown -> "Waiting"
}

private fun colorForStatus(status: ServerStatus): Color = when (status) {
    ServerStatus.Online -> StatusOnline
    ServerStatus.Degraded, ServerStatus.RateLimited, ServerStatus.Error -> StatusWarning
    ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing -> StatusCritical
    ServerStatus.Insecure -> StatusInsecure
    ServerStatus.Unknown -> StatusUnknown
}

private fun colorForPercent(value: Double?): Color = when {
    value == null -> StatusUnknown
    value >= 90.0 -> StatusCritical
    value >= 75.0 -> StatusWarning
    else -> StatusOnline
}

private fun routeIssueColor(issue: NetworkIssue): Color = when (issue) {
    NetworkIssue.None -> StatusOnline
    NetworkIssue.DnsFailure -> StatusInsecure
    NetworkIssue.VpnActive -> StatusWarning
    NetworkIssue.LanRouteBlocked -> StatusWarning
    NetworkIssue.Timeout -> StatusWarning
    NetworkIssue.TlsFailure -> StatusCritical
    NetworkIssue.AuthFailure -> StatusCritical
    NetworkIssue.ApiFailure -> StatusCritical
    NetworkIssue.RateLimited -> StatusWarning
    NetworkIssue.Unknown -> StatusUnknown
}

private fun Double?.formatPercent(): String = this?.let { "${it.toInt()}%" } ?: "--"
private fun Double?.formatMillis(): String = this?.let { "${it.toInt()} ms" } ?: "--"
private fun Double?.formatNumber(): String = this?.let { if (it >= 10) it.toInt().toString() else "%.1f".format(it) } ?: "--"
private fun Long?.formatTime(): String = this?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "--"

private val StatusOnline = Color(0xFF35D38B)
private val StatusWarning = Color(0xFFFFB84D)
private val StatusCritical = Color(0xFFFF5A52)
private val StatusUnknown = Color(0xFF87909F)
private val StatusInsecure = Color(0xFF7D8CFF)
