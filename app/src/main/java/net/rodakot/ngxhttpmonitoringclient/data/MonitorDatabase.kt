package net.rodakot.ngxhttpmonitoringclient.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import net.rodakot.ngxhttpmonitoringclient.model.AlertEvent
import net.rodakot.ngxhttpmonitoringclient.model.AlertOverrides
import net.rodakot.ngxhttpmonitoringclient.model.AlertSeverity
import net.rodakot.ngxhttpmonitoringclient.model.HistoryRetentionDays
import net.rodakot.ngxhttpmonitoringclient.model.MetricSummary
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class MonitorDatabase(context: Context) : SQLiteOpenHelper(context, "ngx_monitor.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE servers (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                tags_json TEXT NOT NULL,
                favorite INTEGER NOT NULL,
                allow_http INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                token_cipher TEXT,
                basic_user_cipher TEXT,
                basic_password_cipher TEXT,
                cpu_override REAL,
                memory_override REAL,
                disk_override REAL,
                latency_override REAL,
                errors5xx_override INTEGER,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                server_id TEXT NOT NULL,
                timestamp_millis INTEGER NOT NULL,
                status TEXT NOT NULL,
                message TEXT NOT NULL,
                cpu_percent REAL,
                memory_percent REAL,
                disk_percent REAL,
                request_rate REAL,
                latency_p95_millis REAL,
                errors_4xx INTEGER,
                errors_5xx INTEGER,
                active_connections INTEGER,
                raw_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX samples_server_time ON samples(server_id, timestamp_millis DESC)")
        db.execSQL(
            """
            CREATE TABLE alerts (
                id TEXT PRIMARY KEY,
                server_id TEXT NOT NULL,
                timestamp_millis INTEGER NOT NULL,
                severity TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                resolved INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX alerts_server_time ON alerts(server_id, timestamp_millis DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun listServers(): List<ServerProfile> {
        val result = mutableListOf<ServerProfile>()
        readableDatabase.query("servers", null, null, null, null, null, "favorite DESC, name COLLATE NOCASE ASC").use { cursor ->
            while (cursor.moveToNext()) result += cursor.toServer()
        }
        return result
    }

    @Synchronized
    fun upsertServer(server: ServerProfile) {
        writableDatabase.insertWithOnConflict("servers", null, server.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun deleteServer(serverId: String) {
        writableDatabase.delete("servers", "id = ?", arrayOf(serverId))
        writableDatabase.delete("samples", "server_id = ?", arrayOf(serverId))
        writableDatabase.delete("alerts", "server_id = ?", arrayOf(serverId))
    }

    @Synchronized
    fun insertSample(summary: MetricSummary) {
        writableDatabase.insert("samples", null, summary.toValues())
    }

    @Synchronized
    fun latestSummaries(serverIds: List<String>): Map<String, MetricSummary> {
        val result = linkedMapOf<String, MetricSummary>()
        serverIds.forEach { serverId ->
            readableDatabase.query(
                "samples",
                null,
                "server_id = ?",
                arrayOf(serverId),
                null,
                null,
                "timestamp_millis DESC",
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) result[serverId] = cursor.toSummary()
            }
        }
        return result
    }

    @Synchronized
    fun samplesForServer(serverId: String, limit: Int = 360): List<MetricSummary> {
        val result = mutableListOf<MetricSummary>()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HistoryRetentionDays.toLong())
        readableDatabase.query(
            "samples",
            null,
            "server_id = ? AND timestamp_millis >= ?",
            arrayOf(serverId, cutoff.toString()),
            null,
            null,
            "timestamp_millis DESC",
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toSummary()
        }
        return result.asReversed()
    }

    @Synchronized
    fun lastSampleTimestamp(serverId: String, rawOnly: Boolean): Long? {
        val selection = if (rawOnly) "server_id = ? AND raw_json IS NOT NULL" else "server_id = ?"
        readableDatabase.query(
            "samples",
            arrayOf("timestamp_millis"),
            selection,
            arrayOf(serverId),
            null,
            null,
            "timestamp_millis DESC",
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    @Synchronized
    fun insertAlert(alert: AlertEvent) {
        writableDatabase.insertWithOnConflict("alerts", null, alert.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun hasRecentAlert(serverId: String, title: String, afterMillis: Long): Boolean {
        readableDatabase.query(
            "alerts",
            arrayOf("id"),
            "server_id = ? AND title = ? AND timestamp_millis >= ?",
            arrayOf(serverId, title, afterMillis.toString()),
            null,
            null,
            "timestamp_millis DESC",
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    @Synchronized
    fun alerts(limit: Int = 200): List<AlertEvent> {
        val result = mutableListOf<AlertEvent>()
        readableDatabase.query("alerts", null, null, null, null, null, "timestamp_millis DESC", limit.toString()).use { cursor ->
            while (cursor.moveToNext()) result += cursor.toAlert()
        }
        return result
    }

    @Synchronized
    fun pruneOldSamples(nowMillis: Long = System.currentTimeMillis()) {
        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(HistoryRetentionDays.toLong())
        writableDatabase.delete("samples", "timestamp_millis < ?", arrayOf(cutoff.toString()))
    }

    private fun ServerProfile.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("base_url", baseUrl)
        put("tags_json", tagsToJson(tags))
        put("favorite", favorite.asInt())
        put("allow_http", allowHttp.asInt())
        put("enabled", enabled.asInt())
        putNullable("token_cipher", tokenCipherText)
        putNullable("basic_user_cipher", basicUserCipherText)
        putNullable("basic_password_cipher", basicPasswordCipherText)
        putNullable("cpu_override", alertOverrides.cpuPercent)
        putNullable("memory_override", alertOverrides.memoryPercent)
        putNullable("disk_override", alertOverrides.diskPercent)
        putNullable("latency_override", alertOverrides.latencyP95Millis)
        putNullable("errors5xx_override", alertOverrides.errors5xx)
        put("updated_at", updatedAtMillis)
    }

    private fun MetricSummary.toValues() = ContentValues().apply {
        put("server_id", serverId)
        put("timestamp_millis", timestampMillis)
        put("status", status.name)
        put("message", message)
        putNullable("cpu_percent", cpuPercent)
        putNullable("memory_percent", memoryPercent)
        putNullable("disk_percent", diskPercent)
        putNullable("request_rate", requestRate)
        putNullable("latency_p95_millis", latencyP95Millis)
        putNullable("errors_4xx", errors4xx)
        putNullable("errors_5xx", errors5xx)
        putNullable("active_connections", activeConnections)
        putNullable("raw_json", rawJson)
    }

    private fun AlertEvent.toValues() = ContentValues().apply {
        put("id", id)
        put("server_id", serverId)
        put("timestamp_millis", timestampMillis)
        put("severity", severity.name)
        put("title", title)
        put("message", message)
        put("resolved", resolved.asInt())
    }

    private fun Cursor.toServer(): ServerProfile {
        return ServerProfile(
            id = string("id"),
            name = string("name"),
            baseUrl = string("base_url"),
            tags = tagsFromJson(string("tags_json")),
            favorite = int("favorite") == 1,
            allowHttp = int("allow_http") == 1,
            enabled = int("enabled") == 1,
            tokenCipherText = nullableString("token_cipher"),
            basicUserCipherText = nullableString("basic_user_cipher"),
            basicPasswordCipherText = nullableString("basic_password_cipher"),
            alertOverrides = AlertOverrides(
                cpuPercent = nullableDouble("cpu_override"),
                memoryPercent = nullableDouble("memory_override"),
                diskPercent = nullableDouble("disk_override"),
                latencyP95Millis = nullableDouble("latency_override"),
                errors5xx = nullableInt("errors5xx_override"),
            ),
            updatedAtMillis = long("updated_at"),
        )
    }

    private fun Cursor.toSummary(): MetricSummary {
        return MetricSummary(
            serverId = string("server_id"),
            timestampMillis = long("timestamp_millis"),
            status = runCatching { ServerStatus.valueOf(string("status")) }.getOrDefault(ServerStatus.Unknown),
            message = string("message"),
            cpuPercent = nullableDouble("cpu_percent"),
            memoryPercent = nullableDouble("memory_percent"),
            diskPercent = nullableDouble("disk_percent"),
            requestRate = nullableDouble("request_rate"),
            latencyP95Millis = nullableDouble("latency_p95_millis"),
            errors4xx = nullableInt("errors_4xx"),
            errors5xx = nullableInt("errors_5xx"),
            activeConnections = nullableInt("active_connections"),
            rawJson = nullableString("raw_json"),
        )
    }

    private fun Cursor.toAlert(): AlertEvent {
        return AlertEvent(
            id = string("id"),
            serverId = string("server_id"),
            timestampMillis = long("timestamp_millis"),
            severity = runCatching { AlertSeverity.valueOf(string("severity")) }.getOrDefault(AlertSeverity.Warning),
            title = string("title"),
            message = string("message"),
            resolved = int("resolved") == 1,
        )
    }

    private fun tagsToJson(tags: List<String>): String {
        val array = JSONArray()
        tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach(array::put)
        return array.toString()
    }

    private fun tagsFromJson(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun Boolean.asInt() = if (this) 1 else 0

    private fun ContentValues.putNullable(key: String, value: String?) = if (value == null) putNull(key) else put(key, value)
    private fun ContentValues.putNullable(key: String, value: Double?) = if (value == null) putNull(key) else put(key, value)
    private fun ContentValues.putNullable(key: String, value: Int?) = if (value == null) putNull(key) else put(key, value)

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.nullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }
    private fun Cursor.nullableDouble(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }
    private fun Cursor.nullableInt(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }
}
