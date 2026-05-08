package net.rodakot.ngxhttpmonitoringclient.data

import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import org.json.JSONArray
import org.json.JSONObject

object MonitorJsonParser {
    fun parse(serverId: String, json: String, timestampMillis: Long = System.currentTimeMillis()): MetricSummary {
        val root = JSONObject(json)
        val system = root.obj("system")
        val nginx = root.obj("nginx")
        val requests = root.obj("requests")
        val nginxRequests = nginx?.obj("requests")
        val connections = root.obj("connections") ?: nginx?.obj("connections")

        val cpu = system?.obj("cpu")?.number("usage")
            ?: system?.number("cpu_usage")
            ?: system?.number("cpu")
        val memory = system?.obj("memory")?.number("used_pct")
            ?: system?.obj("memory")?.number("used_percent")
            ?: system?.number("memory_used_pct")
        val disk = parseDisk(root.obj("disk"))

        val requestRate = requests?.number("requests_per_sec")
            ?: nginxRequests?.number("requests_per_sec")
            ?: nginx?.number("requests_per_sec")
        val latency = requests?.obj("latency")?.number("p95")
            ?: requests?.obj("latency")?.number("p99")
            ?: requests?.number("latency_p95")
        val status = requests?.obj("status") ?: requests?.obj("statuses")

        return MetricSummary(
            serverId = serverId,
            timestampMillis = timestampMillis,
            status = ServerStatus.Online,
            message = "Live",
            cpuPercent = normalizePercent(cpu),
            memoryPercent = normalizePercent(memory),
            diskPercent = normalizePercent(disk),
            requestRate = requestRate,
            latencyP95Millis = latency,
            errors4xx = status?.int("4xx"),
            errors5xx = status?.int("5xx"),
            activeConnections = connections?.int("active"),
            rawJson = json,
        )
    }

    private fun parseDisk(disk: JSONObject?): Double? {
        if (disk == null) return null
        disk.number("used_pct")?.let { return it }
        disk.number("used_percent")?.let { return it }
        val filesystems = disk.array("filesystems") ?: disk.array("mounts") ?: return null
        var max: Double? = null
        for (index in 0 until filesystems.length()) {
            val item = filesystems.optJSONObject(index) ?: continue
            val usage = item.number("used_pct") ?: item.number("used_percent") ?: item.number("usage")
            if (usage != null && (max == null || usage > max!!)) max = usage
        }
        return max
    }

    private fun normalizePercent(value: Double?): Double? {
        if (value == null) return null
        return if (value in 0.0..1.0) value * 100.0 else value
    }

    private fun JSONObject.obj(name: String): JSONObject? = optJSONObject(name)
    private fun JSONObject.array(name: String): JSONArray? = optJSONArray(name)

    private fun JSONObject.number(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONObject.int(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
