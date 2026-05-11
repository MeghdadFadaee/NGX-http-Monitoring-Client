package net.rodakot.ngxhttpmonitoringclient.widget

import android.content.Context

enum class WidgetMetricKind(val label: String) {
    Cpu("CPU"),
    Memory("Memory"),
    Disk("Storage"),
    RequestRate("Requests/s"),
    LatencyP95("p95 latency"),
    Errors5xx("5xx errors"),
    Connections("Connections"),
}

object WidgetPreferences {
    private const val Name = "ngx_monitor_widgets"

    fun saveServerWidget(context: Context, appWidgetId: Int, serverId: String) {
        prefs(context).edit().putString(serverKey(appWidgetId), serverId).apply()
    }

    fun serverId(context: Context, appWidgetId: Int): String? {
        return prefs(context).getString(serverKey(appWidgetId), null)
    }

    fun saveMetricWidget(context: Context, appWidgetId: Int, serverId: String, metric: WidgetMetricKind) {
        prefs(context).edit()
            .putString(serverKey(appWidgetId), serverId)
            .putString(metricKey(appWidgetId), metric.name)
            .apply()
    }

    fun metricKind(context: Context, appWidgetId: Int): WidgetMetricKind {
        val raw = prefs(context).getString(metricKey(appWidgetId), null)
        return runCatching { WidgetMetricKind.valueOf(raw ?: "") }.getOrDefault(WidgetMetricKind.Cpu)
    }

    fun delete(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit().apply {
            appWidgetIds.forEach { appWidgetId ->
                remove(serverKey(appWidgetId))
                remove(metricKey(appWidgetId))
            }
        }.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Name, Context.MODE_PRIVATE)
    private fun serverKey(appWidgetId: Int) = "server_$appWidgetId"
    private fun metricKey(appWidgetId: Int) = "metric_$appWidgetId"
}
