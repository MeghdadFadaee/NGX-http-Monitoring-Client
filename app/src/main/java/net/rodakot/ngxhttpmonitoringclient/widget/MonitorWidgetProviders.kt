package net.rodakot.ngxhttpmonitoringclient.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class FleetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetScope.launch { MonitorWidgetUpdater.updateFleet(context.applicationContext, appWidgetIds, refreshNetwork = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonitorWidgetUpdater.ActionRefreshFleet) {
            val pending = goAsync()
            widgetScope.launch {
                try {
                    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    val ids = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf() else intArrayOf(id)
                    MonitorWidgetUpdater.updateFleet(context.applicationContext, ids, refreshNetwork = true)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }
}

class ServerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetScope.launch { MonitorWidgetUpdater.updateServers(context.applicationContext, appWidgetIds, refreshNetwork = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonitorWidgetUpdater.ActionRefreshServer) {
            val pending = goAsync()
            widgetScope.launch {
                try {
                    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    val ids = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf() else intArrayOf(id)
                    MonitorWidgetUpdater.updateServers(context.applicationContext, ids, refreshNetwork = true)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPreferences.delete(context, appWidgetIds)
    }
}

class MetricWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetScope.launch { MonitorWidgetUpdater.updateMetrics(context.applicationContext, appWidgetIds, refreshNetwork = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonitorWidgetUpdater.ActionRefreshMetric) {
            val pending = goAsync()
            widgetScope.launch {
                try {
                    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    val ids = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf() else intArrayOf(id)
                    MonitorWidgetUpdater.updateMetrics(context.applicationContext, ids, refreshNetwork = true)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPreferences.delete(context, appWidgetIds)
    }
}

class GraphWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetScope.launch { MonitorWidgetUpdater.updateGraphs(context.applicationContext, appWidgetIds, refreshNetwork = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonitorWidgetUpdater.ActionRefreshGraph) {
            val pending = goAsync()
            widgetScope.launch {
                try {
                    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    val ids = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf() else intArrayOf(id)
                    MonitorWidgetUpdater.updateGraphs(context.applicationContext, ids, refreshNetwork = true)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPreferences.delete(context, appWidgetIds)
    }
}

class IncidentsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        widgetScope.launch { MonitorWidgetUpdater.updateIncidents(context.applicationContext, appWidgetIds, refreshNetwork = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonitorWidgetUpdater.ActionRefreshIncidents) {
            val pending = goAsync()
            widgetScope.launch {
                try {
                    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    val ids = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        AppWidgetManager.getInstance(context).getAppWidgetIds(
                            android.content.ComponentName(context, IncidentsWidgetProvider::class.java),
                        )
                    } else {
                        intArrayOf(id)
                    }
                    MonitorWidgetUpdater.updateIncidents(context.applicationContext, ids, refreshNetwork = true)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }
}
