package net.rodakot.ngxhttpmonitoringclient.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import net.rodakot.ngxhttpmonitoringclient.data.MonitorRepository

class ServerWidgetConfigureActivity : BaseWidgetConfigureActivity() {
    override val mode: WidgetConfigureMode = WidgetConfigureMode.Server
}

class MetricWidgetConfigureActivity : BaseWidgetConfigureActivity() {
    override val mode: WidgetConfigureMode = WidgetConfigureMode.Metric
}

class GraphWidgetConfigureActivity : BaseWidgetConfigureActivity() {
    override val mode: WidgetConfigureMode = WidgetConfigureMode.Graph
}

abstract class BaseWidgetConfigureActivity : Activity() {
    protected abstract val mode: WidgetConfigureMode
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = MonitorRepository(applicationContext)
        val servers = repository.servers()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(14, 17, 22))
        }
        root.addView(title(mode.title))
        root.addView(subtitle(mode.description))

        if (servers.isEmpty()) {
            root.addView(subtitle("No servers exist yet. Open NGX Monitor and add a server first."))
            root.addView(actionButton("Close") { finish() })
            setContentView(root)
            return
        }

        val serverGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            servers.forEachIndexed { index, server ->
                addView(RadioButton(this@BaseWidgetConfigureActivity).apply {
                    id = View.generateViewId()
                    text = server.name
                    setTextColor(Color.rgb(234, 240, 247))
                    textSize = 16f
                    setPadding(0, 10, 0, 10)
                })
            }
            check(getChildAt(0).id)
        }
        val scroll = ScrollView(this).apply {
            addView(serverGroup)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(scroll)

        val metricSpinner = if (mode == WidgetConfigureMode.Metric) {
            Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@BaseWidgetConfigureActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    WidgetMetricKind.entries.map { it.label },
                )
            }.also { root.addView(it) }
        } else {
            null
        }

        root.addView(actionButton("Add ${mode.buttonLabel}") {
            val checkedView = serverGroup.findViewById<RadioButton>(serverGroup.checkedRadioButtonId)
            val serverIndex = serverGroup.indexOfChild(checkedView).coerceAtLeast(0)
            val server = servers[serverIndex]
            when (mode) {
                WidgetConfigureMode.Server -> {
                    WidgetPreferences.saveServerWidget(this, appWidgetId, server.id)
                    MonitorWidgetUpdater.updateServers(this, intArrayOf(appWidgetId), refreshNetwork = false)
                }
                WidgetConfigureMode.Metric -> {
                    val metric = WidgetMetricKind.entries[metricSpinner?.selectedItemPosition ?: 0]
                    WidgetPreferences.saveMetricWidget(this, appWidgetId, server.id, metric)
                    MonitorWidgetUpdater.updateMetrics(this, intArrayOf(appWidgetId), refreshNetwork = false)
                }
                WidgetConfigureMode.Graph -> {
                    WidgetPreferences.saveServerWidget(this, appWidgetId, server.id)
                    MonitorWidgetUpdater.updateGraphs(this, intArrayOf(appWidgetId), refreshNetwork = false)
                }
            }
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        })
        setContentView(root)
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(234, 240, 247))
        textSize = 24f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, 8)
    }

    private fun subtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(154, 166, 182))
        textSize = 14f
        setPadding(0, 0, 0, 20)
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }
}

enum class WidgetConfigureMode(
    val title: String,
    val description: String,
    val buttonLabel: String,
) {
    Server(
        title = "Server Bars",
        description = "Choose one server for a large health state with CPU, memory, and storage bars.",
        buttonLabel = "server widget",
    ),
    Metric(
        title = "Metric Sparkline",
        description = "Choose one server and one signal for a focused history graph.",
        buttonLabel = "metric widget",
    ),
    Graph(
        title = "Telemetry Graph",
        description = "Choose one server for CPU, memory, and storage trend lines.",
        buttonLabel = "graph widget",
    ),
}
