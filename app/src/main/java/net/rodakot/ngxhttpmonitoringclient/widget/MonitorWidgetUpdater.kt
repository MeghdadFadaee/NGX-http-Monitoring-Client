package net.rodakot.ngxhttpmonitoringclient.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.rodakot.ngxhttpmonitoringclient.MainActivity
import net.rodakot.ngxhttpmonitoringclient.R
import net.rodakot.ngxhttpmonitoringclient.data.MonitorRepository
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus

object MonitorWidgetUpdater {
    const val ActionRefreshFleet = "net.rodakot.ngxhttpmonitoringclient.widget.REFRESH_FLEET"
    const val ActionRefreshServer = "net.rodakot.ngxhttpmonitoringclient.widget.REFRESH_SERVER"
    const val ActionRefreshMetric = "net.rodakot.ngxhttpmonitoringclient.widget.REFRESH_METRIC"
    const val ActionRefreshGraph = "net.rodakot.ngxhttpmonitoringclient.widget.REFRESH_GRAPH"
    const val ActionRefreshIncidents = "net.rodakot.ngxhttpmonitoringclient.widget.REFRESH_INCIDENTS"

    private val Green = Color.rgb(53, 211, 139)
    private val Cyan = Color.rgb(76, 201, 240)
    private val Amber = Color.rgb(255, 184, 77)
    private val Red = Color.rgb(255, 90, 82)
    private val Text = Color.rgb(234, 240, 247)
    private val Muted = Color.rgb(154, 166, 182)
    private val Panel = Color.rgb(32, 39, 51)
    private val Grid = Color.rgb(55, 68, 84)

    fun updateAll(context: Context, refreshNetwork: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        updateFleet(context, manager.idsFor(context, FleetWidgetProvider::class.java), refreshNetwork)
        updateServers(context, manager.idsFor(context, ServerWidgetProvider::class.java), refreshNetwork)
        updateMetrics(context, manager.idsFor(context, MetricWidgetProvider::class.java), refreshNetwork)
        updateGraphs(context, manager.idsFor(context, GraphWidgetProvider::class.java), refreshNetwork)
        updateIncidents(context, manager.idsFor(context, IncidentsWidgetProvider::class.java), refreshNetwork)
    }

    fun updateFleet(context: Context, appWidgetIds: IntArray, refreshNetwork: Boolean) {
        if (appWidgetIds.isEmpty()) return
        val repository = MonitorRepository(context)
        val servers = repository.servers()
        if (refreshNetwork) {
            servers.filter { it.enabled }.forEach { repository.refreshServer(it, forcePersist = true) }
        }
        val summaries = repository.latestSummaries(servers.map { it.id })
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, fleetViews(context, servers, summaries, appWidgetId))
        }
    }

    fun updateServers(context: Context, appWidgetIds: IntArray, refreshNetwork: Boolean) {
        if (appWidgetIds.isEmpty()) return
        val repository = MonitorRepository(context)
        val servers = repository.servers()
        val summaries = mutableMapOf<String, MetricSummary>().apply {
            putAll(repository.latestSummaries(servers.map { it.id }))
        }
        val refreshed = mutableSetOf<String>()
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            val server = configuredServer(context, appWidgetId, servers)
            if (refreshNetwork && server != null && refreshed.add(server.id)) {
                summaries[server.id] = repository.refreshServer(server, forcePersist = true).summary
            }
            manager.updateAppWidget(appWidgetId, serverViews(context, appWidgetId, server, server?.let { summaries[it.id] }))
        }
    }

    fun updateMetrics(context: Context, appWidgetIds: IntArray, refreshNetwork: Boolean) {
        if (appWidgetIds.isEmpty()) return
        val repository = MonitorRepository(context)
        val servers = repository.servers()
        val summaries = mutableMapOf<String, MetricSummary>().apply {
            putAll(repository.latestSummaries(servers.map { it.id }))
        }
        val refreshed = mutableSetOf<String>()
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            val server = configuredServer(context, appWidgetId, servers)
            if (refreshNetwork && server != null && refreshed.add(server.id)) {
                summaries[server.id] = repository.refreshServer(server, forcePersist = true).summary
            }
            val history = server?.let { repository.history(it.id).asReversed().takeLast(48) }.orEmpty()
            manager.updateAppWidget(
                appWidgetId,
                metricViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    server = server,
                    summary = server?.let { summaries[it.id] },
                    history = history,
                    metric = WidgetPreferences.metricKind(context, appWidgetId),
                ),
            )
        }
    }

    fun updateGraphs(context: Context, appWidgetIds: IntArray, refreshNetwork: Boolean) {
        if (appWidgetIds.isEmpty()) return
        val repository = MonitorRepository(context)
        val servers = repository.servers()
        val summaries = mutableMapOf<String, MetricSummary>().apply {
            putAll(repository.latestSummaries(servers.map { it.id }))
        }
        val refreshed = mutableSetOf<String>()
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            val server = configuredServer(context, appWidgetId, servers)
            if (refreshNetwork && server != null && refreshed.add(server.id)) {
                summaries[server.id] = repository.refreshServer(server, forcePersist = true).summary
            }
            val history = server?.let { repository.history(it.id).asReversed().takeLast(60) }.orEmpty()
            manager.updateAppWidget(
                appWidgetId,
                graphViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    server = server,
                    summary = server?.let { summaries[it.id] },
                    history = history,
                ),
            )
        }
    }

    fun updateIncidents(context: Context, appWidgetIds: IntArray, refreshNetwork: Boolean) {
        if (appWidgetIds.isEmpty()) return
        val repository = MonitorRepository(context)
        val servers = repository.servers()
        if (refreshNetwork) {
            servers.filter { it.enabled }.forEach { repository.refreshServer(it, forcePersist = true) }
        }
        val summaries = repository.latestSummaries(servers.map { it.id })
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, incidentsViews(context, servers, summaries, appWidgetId))
        }
    }

    private fun fleetViews(
        context: Context,
        servers: List<ServerProfile>,
        summaries: Map<String, MetricSummary>,
        appWidgetId: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_fleet)
        val counters = counters(servers, summaries)
        val headline = fleetHeadline(counters)
        views.setTextViewText(R.id.widget_title, "Fleet Pulse")
        views.setTextViewText(R.id.widget_subtitle, "${servers.size} servers - ${formatTime(System.currentTimeMillis())}")
        views.setTextViewText(R.id.widget_status, headline)
        views.setTextColor(R.id.widget_status, colorForFleet(counters))
        views.setImageViewBitmap(R.id.widget_graph, fleetStatusBitmap(servers, summaries))
        views.setTextViewText(R.id.widget_online, "${counters.online} OK")
        views.setTextViewText(R.id.widget_watch, "${counters.watch} WATCH")
        views.setTextViewText(R.id.widget_down, "${counters.down} DOWN")
        views.setTextViewText(R.id.widget_priority, fleetPriority(servers, summaries))
        views.bindCommon(context, FleetWidgetProvider::class.java, ActionRefreshFleet, appWidgetId)
        return views
    }

    private fun serverViews(
        context: Context,
        appWidgetId: Int,
        server: ServerProfile?,
        summary: MetricSummary?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_server)
        if (server == null) {
            views.setTextViewText(R.id.widget_title, "Choose server")
            views.setTextViewText(R.id.widget_status, "Create a server in the app first")
            views.setTextViewText(R.id.widget_health, "NO SERVER")
            views.setTextColor(R.id.widget_health, Muted)
            clearServerMetrics(views)
        } else {
            val status = summary?.status ?: ServerStatus.Unknown
            views.setTextViewText(R.id.widget_title, server.name)
            views.setTextViewText(R.id.widget_status, summary?.message ?: "Waiting for first sample")
            views.setTextColor(R.id.widget_status, colorForStatus(status))
            views.setTextViewText(R.id.widget_health, statusLabel(status).uppercase(Locale.US))
            views.setTextColor(R.id.widget_health, colorForStatus(status))
            setPercentBar(views, R.id.widget_cpu_bar, R.id.widget_cpu_value, summary?.cpuPercent)
            setPercentBar(views, R.id.widget_memory_bar, R.id.widget_memory_value, summary?.memoryPercent)
            setPercentBar(views, R.id.widget_disk_bar, R.id.widget_disk_value, summary?.diskPercent)
            views.setTextViewText(R.id.widget_rps, "RPS ${summary?.requestRate.number()}")
            views.setTextViewText(R.id.widget_p95, "P95 ${summary?.latencyP95Millis.ms()}")
            views.setTextViewText(R.id.widget_5xx, "5XX ${summary?.errors5xx?.toString() ?: "--"}")
            views.setTextColor(R.id.widget_5xx, if ((summary?.errors5xx ?: 0) > 0) Red else Green)
        }
        views.bindCommon(context, ServerWidgetProvider::class.java, ActionRefreshServer, appWidgetId)
        return views
    }

    private fun metricViews(
        context: Context,
        appWidgetId: Int,
        server: ServerProfile?,
        summary: MetricSummary?,
        history: List<MetricSummary>,
        metric: WidgetMetricKind,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_metric)
        if (server == null) {
            views.setTextViewText(R.id.widget_title, "Choose server")
            views.setTextViewText(R.id.widget_value, "--")
            views.setTextViewText(R.id.widget_status, "Add a server, then add this widget again")
            views.setImageViewBitmap(R.id.widget_graph, emptyBitmap("No samples"))
        } else {
            val color = colorForMetric(metric, summary)
            views.setTextViewText(R.id.widget_title, server.name)
            views.setTextViewText(R.id.widget_value, valueFor(metric, summary))
            views.setTextColor(R.id.widget_value, color)
            views.setTextViewText(
                R.id.widget_status,
                "${metric.label} - ${statusLabel(summary?.status ?: ServerStatus.Unknown)} - ${formatTime(summary?.timestampMillis)}",
            )
            views.setImageViewBitmap(
                R.id.widget_graph,
                sparklineBitmap(
                    values = metricValues(metric, history, summary),
                    metric = metric,
                    color = color,
                ),
            )
        }
        views.bindCommon(context, MetricWidgetProvider::class.java, ActionRefreshMetric, appWidgetId)
        return views
    }

    private fun graphViews(
        context: Context,
        appWidgetId: Int,
        server: ServerProfile?,
        summary: MetricSummary?,
        history: List<MetricSummary>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_graph)
        if (server == null) {
            views.setTextViewText(R.id.widget_title, "Choose server")
            views.setTextViewText(R.id.widget_status, "Create a server in the app first")
            views.setTextViewText(R.id.widget_cpu, "CPU --")
            views.setTextViewText(R.id.widget_memory, "MEM --")
            views.setTextViewText(R.id.widget_disk, "STORAGE --")
            views.setImageViewBitmap(R.id.widget_graph, emptyBitmap("No samples"))
        } else {
            val status = summary?.status ?: ServerStatus.Unknown
            views.setTextViewText(R.id.widget_title, "${server.name} flow")
            views.setTextViewText(R.id.widget_status, "${statusLabel(status)} - ${formatTime(summary?.timestampMillis)}")
            views.setTextColor(R.id.widget_status, colorForStatus(status))
            views.setTextViewText(R.id.widget_cpu, "CPU ${summary?.cpuPercent.percent()}")
            views.setTextViewText(R.id.widget_memory, "MEM ${summary?.memoryPercent.percent()}")
            views.setTextViewText(R.id.widget_disk, "STORAGE ${summary?.diskPercent.percent()}")
            views.setImageViewBitmap(R.id.widget_graph, multilineBitmap(history, summary))
        }
        views.bindCommon(context, GraphWidgetProvider::class.java, ActionRefreshGraph, appWidgetId)
        return views
    }

    private fun incidentsViews(
        context: Context,
        servers: List<ServerProfile>,
        summaries: Map<String, MetricSummary>,
        appWidgetId: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_incidents)
        views.setTextViewText(R.id.widget_title, "Incident Watch")
        views.setTextViewText(R.id.widget_subtitle, "${servers.size} servers - ${formatTime(System.currentTimeMillis())}")
        if (servers.isEmpty()) {
            views.setTextViewText(R.id.widget_issue_count, "--")
            views.setTextViewText(R.id.widget_issue_label, "SETUP")
            views.setTextColor(R.id.widget_issue_count, Muted)
            views.setTextViewText(R.id.widget_issue_one, "No servers configured")
            views.setTextViewText(R.id.widget_issue_two, "Open the app and add endpoints")
            views.setTextViewText(R.id.widget_issue_three, "Then place this widget again")
        } else {
            val issues = servers
                .map { it to summaries[it.id] }
                .filter { (_, summary) -> isIssueStatus(summary?.status ?: ServerStatus.Unknown) }
                .sortedWith(compareBy<Pair<ServerProfile, MetricSummary?>> { statusRank(it.second?.status) }.thenBy { it.first.name.lowercase() })
            if (issues.isEmpty()) {
                views.setTextViewText(R.id.widget_issue_count, "OK")
                views.setTextViewText(R.id.widget_issue_label, "CLEAR")
                views.setTextColor(R.id.widget_issue_count, Green)
                views.setTextColor(R.id.widget_issue_label, Green)
                views.setTextViewText(R.id.widget_issue_one, "All monitored servers look healthy")
                views.setTextColor(R.id.widget_issue_one, Green)
                views.setTextViewText(R.id.widget_issue_two, "No unreachable or degraded nodes")
                views.setTextColor(R.id.widget_issue_two, Muted)
                views.setTextViewText(R.id.widget_issue_three, "Last update ${formatTime(System.currentTimeMillis())}")
                views.setTextColor(R.id.widget_issue_three, Muted)
            } else {
                views.setTextViewText(R.id.widget_issue_count, issues.size.toString())
                views.setTextViewText(R.id.widget_issue_label, "ISSUES")
                views.setTextColor(R.id.widget_issue_count, if (issues.any { isDownStatus(it.second?.status) }) Red else Amber)
                views.setTextColor(R.id.widget_issue_label, if (issues.any { isDownStatus(it.second?.status) }) Red else Amber)
                val rows = issues.take(3).map { (server, summary) ->
                    "${statusLabel(summary?.status ?: ServerStatus.Unknown)} - ${server.name}"
                }
                views.setTextViewText(R.id.widget_issue_one, rows.getOrNull(0) ?: "")
                views.setTextViewText(R.id.widget_issue_two, rows.getOrNull(1) ?: "")
                views.setTextViewText(R.id.widget_issue_three, rows.getOrNull(2) ?: "Open app for the full queue")
                views.setTextColor(R.id.widget_issue_one, colorForStatus(issues.getOrNull(0)?.second?.status ?: ServerStatus.Unknown))
                views.setTextColor(R.id.widget_issue_two, colorForStatus(issues.getOrNull(1)?.second?.status ?: ServerStatus.Unknown))
                views.setTextColor(R.id.widget_issue_three, colorForStatus(issues.getOrNull(2)?.second?.status ?: ServerStatus.Unknown))
            }
        }
        views.bindCommon(context, IncidentsWidgetProvider::class.java, ActionRefreshIncidents, appWidgetId)
        return views
    }

    private fun setPercentBar(views: RemoteViews, barId: Int, valueId: Int, value: Double?) {
        views.setProgressBar(barId, 100, value.progressValue(), false)
        views.setTextViewText(valueId, value.percent())
        views.setTextColor(valueId, colorForPercent(value))
    }

    private fun clearServerMetrics(views: RemoteViews) {
        setPercentBar(views, R.id.widget_cpu_bar, R.id.widget_cpu_value, null)
        setPercentBar(views, R.id.widget_memory_bar, R.id.widget_memory_value, null)
        setPercentBar(views, R.id.widget_disk_bar, R.id.widget_disk_value, null)
        views.setTextViewText(R.id.widget_rps, "RPS --")
        views.setTextViewText(R.id.widget_p95, "P95 --")
        views.setTextViewText(R.id.widget_5xx, "5XX --")
        views.setTextColor(R.id.widget_5xx, Muted)
    }

    private fun configuredServer(context: Context, appWidgetId: Int, servers: List<ServerProfile>): ServerProfile? {
        val configured = WidgetPreferences.serverId(context, appWidgetId)
        return servers.firstOrNull { it.id == configured } ?: servers.firstOrNull()
    }

    private fun RemoteViews.bindCommon(
        context: Context,
        providerClass: Class<*>,
        refreshAction: String,
        appWidgetId: Int,
    ) {
        setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, providerClass, refreshAction, appWidgetId))
    }

    private fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            50_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshIntent(
        context: Context,
        providerClass: Class<*>,
        action: String,
        appWidgetId: Int,
    ): PendingIntent {
        val intent = Intent(context, providerClass)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            70_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun AppWidgetManager.idsFor(context: Context, providerClass: Class<*>): IntArray {
        return getAppWidgetIds(ComponentName(context, providerClass))
    }

    private fun counters(servers: List<ServerProfile>, summaries: Map<String, MetricSummary>): Counters {
        var online = 0
        var watch = 0
        var down = 0
        var unknown = 0
        servers.forEach { server ->
            when (summaries[server.id]?.status ?: ServerStatus.Unknown) {
                ServerStatus.Online -> online += 1
                ServerStatus.Degraded, ServerStatus.RateLimited, ServerStatus.Error, ServerStatus.Insecure -> watch += 1
                ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing -> down += 1
                ServerStatus.Unknown -> unknown += 1
            }
        }
        return Counters(online, watch, down, unknown, servers.size)
    }

    private fun fleetHeadline(counters: Counters): String = when {
        counters.total == 0 -> "Add servers"
        counters.down > 0 -> "${counters.down} down"
        counters.watch > 0 -> "${counters.watch} on watch"
        counters.unknown == counters.total -> "Waiting"
        counters.unknown > 0 -> "${counters.unknown} waiting"
        else -> "All clear"
    }

    private fun colorForFleet(counters: Counters): Int = when {
        counters.down > 0 -> Red
        counters.watch > 0 -> Amber
        counters.unknown > 0 -> Muted
        else -> Green
    }

    private fun fleetPriority(servers: List<ServerProfile>, summaries: Map<String, MetricSummary>): String {
        if (servers.isEmpty()) return "Open the app and add your first monitor endpoint"
        val priority = servers
            .sortedWith(compareBy<ServerProfile> { statusRank(summaries[it.id]?.status) }.thenBy { it.name.lowercase() })
            .filter { isIssueStatus(summaries[it.id]?.status ?: ServerStatus.Unknown) }
            .take(2)
        if (priority.isEmpty()) return "No active incidents"
        return priority.joinToString("  ") { server ->
            "${statusLabel(summaries[server.id]?.status ?: ServerStatus.Unknown)} - ${server.name}"
        }
    }

    private fun isIssueStatus(status: ServerStatus): Boolean = when (status) {
        ServerStatus.Degraded,
        ServerStatus.Offline,
        ServerStatus.AuthFailed,
        ServerStatus.Forbidden,
        ServerStatus.Missing,
        ServerStatus.RateLimited,
        ServerStatus.Insecure,
        ServerStatus.Error,
        -> true
        ServerStatus.Online, ServerStatus.Unknown -> false
    }

    private fun isDownStatus(status: ServerStatus?): Boolean = status in setOf(
        ServerStatus.Offline,
        ServerStatus.AuthFailed,
        ServerStatus.Forbidden,
        ServerStatus.Missing,
    )

    private fun statusRank(status: ServerStatus?): Int = when (status) {
        ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing -> 0
        ServerStatus.Degraded, ServerStatus.RateLimited, ServerStatus.Error -> 1
        ServerStatus.Insecure -> 2
        ServerStatus.Unknown, null -> 3
        ServerStatus.Online -> 4
    }

    private fun statusLabel(status: ServerStatus): String = when (status) {
        ServerStatus.Online -> "Online"
        ServerStatus.Degraded -> "Watch"
        ServerStatus.Offline -> "Offline"
        ServerStatus.AuthFailed -> "Auth"
        ServerStatus.Forbidden -> "Blocked"
        ServerStatus.Missing -> "Missing"
        ServerStatus.RateLimited -> "Limited"
        ServerStatus.Insecure -> "HTTP"
        ServerStatus.Error -> "Error"
        ServerStatus.Unknown -> "Waiting"
    }

    private fun valueFor(metric: WidgetMetricKind, summary: MetricSummary?): String = when (metric) {
        WidgetMetricKind.Cpu -> summary?.cpuPercent.percent()
        WidgetMetricKind.Memory -> summary?.memoryPercent.percent()
        WidgetMetricKind.Disk -> summary?.diskPercent.percent()
        WidgetMetricKind.RequestRate -> summary?.requestRate.number()
        WidgetMetricKind.LatencyP95 -> summary?.latencyP95Millis.ms()
        WidgetMetricKind.Errors5xx -> summary?.errors5xx?.toString() ?: "--"
        WidgetMetricKind.Connections -> summary?.activeConnections?.toString() ?: "--"
    }

    private fun metricValues(metric: WidgetMetricKind, history: List<MetricSummary>, summary: MetricSummary?): List<Double?> {
        val samples = if (history.isEmpty()) listOfNotNull(summary) else history
        return samples.takeLast(48).map { metricValue(metric, it) }
    }

    private fun metricValue(metric: WidgetMetricKind, summary: MetricSummary): Double? = when (metric) {
        WidgetMetricKind.Cpu -> summary.cpuPercent
        WidgetMetricKind.Memory -> summary.memoryPercent
        WidgetMetricKind.Disk -> summary.diskPercent
        WidgetMetricKind.RequestRate -> summary.requestRate
        WidgetMetricKind.LatencyP95 -> summary.latencyP95Millis
        WidgetMetricKind.Errors5xx -> summary.errors5xx?.toDouble()
        WidgetMetricKind.Connections -> summary.activeConnections?.toDouble()
    }

    private fun colorForMetric(metric: WidgetMetricKind, summary: MetricSummary?): Int = when (metric) {
        WidgetMetricKind.Cpu -> colorForPercent(summary?.cpuPercent)
        WidgetMetricKind.Memory -> colorForPercent(summary?.memoryPercent)
        WidgetMetricKind.Disk -> colorForPercent(summary?.diskPercent)
        WidgetMetricKind.Errors5xx -> if ((summary?.errors5xx ?: 0) > 0) Red else Green
        WidgetMetricKind.LatencyP95 -> Amber
        WidgetMetricKind.RequestRate, WidgetMetricKind.Connections -> Cyan
    }

    private fun colorForStatus(status: ServerStatus): Int = when (status) {
        ServerStatus.Online -> Green
        ServerStatus.Degraded, ServerStatus.RateLimited, ServerStatus.Error -> Amber
        ServerStatus.Offline, ServerStatus.AuthFailed, ServerStatus.Forbidden, ServerStatus.Missing -> Red
        ServerStatus.Insecure -> Cyan
        ServerStatus.Unknown -> Muted
    }

    private fun colorForPercent(value: Double?): Int = when {
        value == null -> Muted
        value >= 90.0 -> Red
        value >= 75.0 -> Amber
        else -> Green
    }

    private fun fleetStatusBitmap(
        servers: List<ServerProfile>,
        summaries: Map<String, MetricSummary>,
        width: Int = 640,
        height: Int = 88,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Panel
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 24f, 24f, paint)
        if (servers.isEmpty()) {
            paint.color = Muted
            repeat(6) { index ->
                val left = 22f + index * 98f
                canvas.drawRoundRect(RectF(left, 26f, left + 60f, 62f), 18f, 18f, paint)
            }
            return bitmap
        }

        val statuses = servers
            .map { summaries[it.id]?.status ?: ServerStatus.Unknown }
            .sortedBy { statusRank(it) }
        val count = statuses.size.coerceAtLeast(1)
        val outer = 14f
        val gap = if (count > 38) 2f else 5f
        val cellWidth = ((width - outer * 2 - gap * (count - 1)) / count).coerceAtLeast(3f)
        statuses.forEachIndexed { index, status ->
            val left = outer + index * (cellWidth + gap)
            val right = min(width - outer, left + cellWidth)
            paint.color = colorForStatus(status)
            canvas.drawRoundRect(RectF(left, 18f, right, height - 18f), 18f, 18f, paint)
        }
        return bitmap
    }

    private fun sparklineBitmap(
        values: List<Double?>,
        metric: WidgetMetricKind,
        color: Int,
        width: Int = 520,
        height: Int = 150,
    ): Bitmap {
        val clean = values.filterNotNull().takeLast(48)
        if (clean.isEmpty()) return emptyBitmap("No samples", width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val graph = RectF(10f, 12f, width - 10f, height - 16f)
        drawGraphBackground(canvas, graph)
        val maxValue = if (metric.isPercentMetric()) {
            100.0
        } else {
            max(1.0, clean.maxOrNull().orZero() * 1.25)
        }
        drawLine(
            canvas = canvas,
            values = clean,
            graph = graph,
            color = color,
            minValue = 0.0,
            maxValue = maxValue,
            stroke = 7f,
            fill = true,
        )
        return bitmap
    }

    private fun multilineBitmap(
        history: List<MetricSummary>,
        summary: MetricSummary?,
        width: Int = 720,
        height: Int = 230,
    ): Bitmap {
        val samples = (if (history.isEmpty()) listOfNotNull(summary) else history).takeLast(60)
        if (samples.isEmpty()) return emptyBitmap("No samples", width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val graph = RectF(12f, 10f, width - 12f, height - 14f)
        drawGraphBackground(canvas, graph)
        drawLine(canvas, samples.map { it.cpuPercent }, graph, Green, minValue = 0.0, maxValue = 100.0, stroke = 5f)
        drawLine(canvas, samples.map { it.memoryPercent }, graph, Cyan, minValue = 0.0, maxValue = 100.0, stroke = 5f)
        drawLine(canvas, samples.map { it.diskPercent }, graph, Amber, minValue = 0.0, maxValue = 100.0, stroke = 5f)
        return bitmap
    }

    private fun emptyBitmap(label: String, width: Int = 520, height: Int = 150): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val graph = RectF(10f, 10f, width - 10f, height - 10f)
        drawGraphBackground(canvas, graph)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Muted
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, width / 2f, height / 2f + 12f, paint)
        return bitmap
    }

    private fun drawGraphBackground(canvas: Canvas, graph: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Panel
        canvas.drawRoundRect(graph, 24f, 24f, paint)
        paint.color = withAlpha(Grid, 120)
        paint.strokeWidth = 2f
        repeat(4) { index ->
            val y = graph.top + graph.height() * (index + 1) / 5f
            canvas.drawLine(graph.left + 12f, y, graph.right - 12f, y, paint)
        }
    }

    private fun drawLine(
        canvas: Canvas,
        values: List<Double?>,
        graph: RectF,
        color: Int,
        minValue: Double,
        maxValue: Double,
        stroke: Float,
        fill: Boolean = false,
    ) {
        val clean = values.filterNotNull()
        if (clean.isEmpty()) return
        val plotted = if (clean.size == 1) listOf(clean.first(), clean.first()) else clean
        val path = Path()
        plotted.forEachIndexed { index, value ->
            val x = graph.left + 18f + index * ((graph.width() - 36f) / (plotted.size - 1).coerceAtLeast(1))
            val normalized = ((value - minValue) / (maxValue - minValue).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            val y = graph.bottom - 16f - (graph.height() - 32f) * normalized.toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (fill) {
            val fillPath = Path(path)
            fillPath.lineTo(graph.right - 18f, graph.bottom - 16f)
            fillPath.lineTo(graph.left + 18f, graph.bottom - 16f)
            fillPath.close()
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = withAlpha(color, 45)
                style = Paint.Style.FILL
            }
            canvas.drawPath(fillPath, fillPaint)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke
        }
        canvas.drawPath(path, linePaint)
        val last = plotted.last()
        val lastX = graph.right - 18f
        val lastY = graph.bottom - 16f - (graph.height() - 32f) *
            ((last - minValue) / (maxValue - minValue).coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(lastX, lastY, stroke * 1.25f, dotPaint)
    }

    private fun WidgetMetricKind.isPercentMetric(): Boolean = when (this) {
        WidgetMetricKind.Cpu, WidgetMetricKind.Memory, WidgetMetricKind.Disk -> true
        WidgetMetricKind.RequestRate,
        WidgetMetricKind.LatencyP95,
        WidgetMetricKind.Errors5xx,
        WidgetMetricKind.Connections,
        -> false
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun Double?.progressValue(): Int = this?.coerceIn(0.0, 100.0)?.roundToInt() ?: 0
    private fun Double?.percent(): String = this?.let { "${it.coerceIn(0.0, 999.0).roundToInt()}%" } ?: "--"
    private fun Double?.ms(): String = this?.let { "${it.roundToInt()}ms" } ?: "--"
    private fun Double?.number(): String = this?.let {
        when {
            it >= 1000.0 -> String.format(Locale.US, "%.1fk", it / 1000.0)
            it >= 10.0 -> it.roundToInt().toString()
            else -> String.format(Locale.US, "%.1f", it)
        }
    } ?: "--"

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun formatTime(timestampMillis: Long?): String {
        return timestampMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "--"
    }

    private data class Counters(
        val online: Int,
        val watch: Int,
        val down: Int,
        val unknown: Int,
        val total: Int,
    )
}
