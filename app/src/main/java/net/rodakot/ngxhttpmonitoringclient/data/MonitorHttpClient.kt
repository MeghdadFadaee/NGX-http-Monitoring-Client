package net.rodakot.ngxhttpmonitoringclient.data

import android.util.Base64
import net.rodakot.ngxhttpmonitoringclient.domain.UrlRules
import net.rodakot.ngxhttpmonitoringclient.model.AuthConfig
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MonitorHttpClient {
    fun fetchApi(server: ServerProfile, auth: AuthConfig): String {
        return fetchText(UrlRules.endpoint(server.baseUrl, "/monitor/api"), auth)
    }

    fun fetchHealth(server: ServerProfile, auth: AuthConfig): String {
        return fetchText(UrlRules.endpoint(server.baseUrl, "/monitor/health"), auth)
    }

    fun streamLive(server: ServerProfile, auth: AuthConfig, onMetrics: (String) -> Unit) {
        val connection = openConnection(UrlRules.endpoint(server.baseUrl, "/monitor/live"), auth).apply {
            readTimeout = 0
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw MonitorClientException(status, status.toMonitorStatus(), status.messageForStatus())
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var eventName = "message"
                val data = StringBuilder()
                while (!Thread.currentThread().isInterrupted) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isBlank() -> {
                            if (data.isNotBlank() && (eventName == "metrics" || eventName == "message")) {
                                onMetrics(data.toString().trim())
                            }
                            eventName = "message"
                            data.clear()
                        }
                        line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                        line.startsWith("data:") -> data.appendLine(line.substringAfter("data:").trim())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchText(url: String, auth: AuthConfig): String {
        val connection = openConnection(url, auth)
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw MonitorClientException(status, status.toMonitorStatus(), status.messageForStatus())
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (exception: IOException) {
            throw MonitorClientException(null, ServerStatus.Offline, exception.message ?: "Network error", exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, auth: AuthConfig): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 15_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        auth.token?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("X-Monitor-Token", it)
        }
        if (!auth.basicUsername.isNullOrBlank() && !auth.basicPassword.isNullOrBlank()) {
            val encoded = Base64.encodeToString(
                "${auth.basicUsername}:${auth.basicPassword}".toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP,
            )
            connection.setRequestProperty("Authorization", "Basic $encoded")
        }
        return connection
    }

    private fun Int.toMonitorStatus(): ServerStatus = when (this) {
        401 -> ServerStatus.AuthFailed
        403 -> ServerStatus.Forbidden
        404 -> ServerStatus.Missing
        429 -> ServerStatus.RateLimited
        else -> ServerStatus.Error
    }

    private fun Int.messageForStatus(): String = when (this) {
        401 -> "Basic Auth or monitor token is missing or wrong"
        403 -> "Client IP is blocked by monitor access rules"
        404 -> "Monitor API/SSE endpoint is not enabled at this location"
        429 -> "Monitor rate limit was exceeded"
        else -> "HTTP $this from monitor endpoint"
    }
}

class MonitorClientException(
    val httpStatus: Int?,
    val monitorStatus: ServerStatus,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
