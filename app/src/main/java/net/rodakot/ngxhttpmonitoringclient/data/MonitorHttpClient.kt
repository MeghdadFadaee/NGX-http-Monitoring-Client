package net.rodakot.ngxhttpmonitoringclient.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import net.rodakot.ngxhttpmonitoringclient.domain.RouteAttemptPlanner
import net.rodakot.ngxhttpmonitoringclient.domain.UrlRules
import net.rodakot.ngxhttpmonitoringclient.model.AuthConfig
import net.rodakot.ngxhttpmonitoringclient.model.NetworkIssue
import net.rodakot.ngxhttpmonitoringclient.model.RouteDiagnostics
import net.rodakot.ngxhttpmonitoringclient.model.RouteKind
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile
import net.rodakot.ngxhttpmonitoringclient.model.ServerStatus
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.InterruptedIOException
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class MonitorHttpClient(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun fetchApi(server: ServerProfile, auth: AuthConfig): RouteResponse {
        return fetchText(server, auth, UrlRules.endpoint(server.baseUrl, "/monitor/api"))
    }

    fun fetchHealth(server: ServerProfile, auth: AuthConfig): RouteResponse {
        return fetchText(server, auth, UrlRules.endpoint(server.baseUrl, "/monitor/health"))
    }

    fun streamLive(
        server: ServerProfile,
        auth: AuthConfig,
        onDiagnostics: (RouteDiagnostics) -> Unit,
        onMetrics: (String) -> Unit,
    ) {
        val routed = openRoutedResponse(
            server = server,
            auth = auth,
            url = UrlRules.endpoint(server.baseUrl, "/monitor/live"),
            accept = "text/event-stream",
            streaming = true,
        )
        onDiagnostics(routed.diagnostics)
        routed.response.use { response ->
            if (!response.isSuccessful) {
                throw response.toException(routed.diagnostics)
            }
            BufferedReader(InputStreamReader(response.body!!.byteStream(), StandardCharsets.UTF_8)).use { reader ->
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
        }
    }

    private fun fetchText(server: ServerProfile, auth: AuthConfig, url: String): RouteResponse {
        val routed = openRoutedResponse(server, auth, url, accept = "application/json", streaming = false)
        routed.response.use { response ->
            if (!response.isSuccessful) {
                throw response.toException(routed.diagnostics)
            }
            val body = response.body?.string().orEmpty()
            return RouteResponse(body, routed.diagnostics)
        }
    }

    private fun openRoutedResponse(
        server: ServerProfile,
        auth: AuthConfig,
        url: String,
        accept: String,
        streaming: Boolean,
    ): RoutedResponse {
        val state = networkState()
        val attempts = RouteAttemptPlanner.attempts(
            hasFallbackAliases = server.fallbackIpAddresses.isNotEmpty(),
            vpnActive = state.vpnActive,
            lanAvailable = state.lanNetwork != null,
        )
        var lastDiagnostics: RouteDiagnostics? = null
        var lastException: IOException? = null

        attempts.forEach { kind ->
            val diagnostics = diagnosticsFor(server, kind, state)
            try {
                val response = clientFor(kind, state, server, streaming)
                    .newCall(request(url, auth, accept))
                    .execute()
                return RoutedResponse(response, diagnostics.copy(success = true, issue = NetworkIssue.None))
            } catch (exception: IOException) {
                lastDiagnostics = diagnostics.copy(success = false, issue = classify(exception, kind))
                lastException = exception
            }
        }

        val diagnostics = lastDiagnostics ?: RouteDiagnostics(
            serverId = server.id,
            timestampMillis = System.currentTimeMillis(),
            activeNetwork = state.activeNetworkLabel,
            vpnActive = state.vpnActive,
            issue = NetworkIssue.Unknown,
        )
        throw MonitorClientException(
            httpStatus = null,
            monitorStatus = ServerStatus.Offline,
            message = diagnostics.issue.label,
            diagnostics = diagnostics,
            cause = lastException,
        )
    }

    private fun clientFor(
        kind: RouteKind,
        state: NetworkState,
        server: ServerProfile,
        streaming: Boolean,
    ): OkHttpClient {
        val builder = baseClient.newBuilder()
            .readTimeout(if (streaming) 0 else 15, TimeUnit.SECONDS)
            .callTimeout(if (streaming) 0 else 20, TimeUnit.SECONDS)

        val lanNetwork = state.lanNetwork
        val dnsDelegate = when {
            kind == RouteKind.LanSystemDns || kind == RouteKind.LanFallbackDns -> lanNetwork?.let(::NetworkDns) ?: Dns.SYSTEM
            else -> Dns.SYSTEM
        }
        if (kind == RouteKind.LanSystemDns || kind == RouteKind.LanFallbackDns) {
            lanNetwork?.let { builder.socketFactory(it.socketFactory) }
        }
        if (kind == RouteKind.FallbackDns || kind == RouteKind.LanFallbackDns) {
            builder.dns(FallbackDns(server.hostName(), server.fallbackIpAddresses, dnsDelegate))
        } else {
            builder.dns(dnsDelegate)
        }
        return builder.build()
    }

    private fun request(url: String, auth: AuthConfig, accept: String): Request {
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", accept)
        auth.token?.takeIf { it.isNotBlank() }?.let {
            builder.header("X-Monitor-Token", it)
        }
        if (!auth.basicUsername.isNullOrBlank() && !auth.basicPassword.isNullOrBlank()) {
            builder.header("Authorization", Credentials.basic(auth.basicUsername, auth.basicPassword))
        }
        return builder.build()
    }

    private fun diagnosticsFor(server: ServerProfile, kind: RouteKind, state: NetworkState): RouteDiagnostics {
        val fallbackUsed = kind == RouteKind.FallbackDns || kind == RouteKind.LanFallbackDns
        val dnsResult = when (kind) {
            RouteKind.SystemDns -> "System DNS"
            RouteKind.FallbackDns -> server.fallbackIpAddresses.joinToString(", ")
            RouteKind.LanSystemDns -> "LAN DNS"
            RouteKind.LanFallbackDns -> server.fallbackIpAddresses.joinToString(", ")
        }
        return RouteDiagnostics(
            serverId = server.id,
            timestampMillis = System.currentTimeMillis(),
            activeNetwork = state.activeNetworkLabel,
            routeUsed = kind,
            dnsResult = dnsResult.ifBlank { "None" },
            issue = if (state.vpnActive) NetworkIssue.VpnActive else NetworkIssue.None,
            vpnActive = state.vpnActive,
            fallbackUsed = fallbackUsed,
            success = false,
        )
    }

    private fun networkState(): NetworkState {
        val networks = connectivityManager?.allNetworks.orEmpty()
        val activeLabel = connectivityManager?.activeNetwork
            ?.let { network -> connectivityManager.getNetworkCapabilities(network)?.transportLabel() }
            ?: "No active network"
        val vpnActive = networks.any { network ->
            connectivityManager?.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val lanNetwork = networks.firstOrNull { network ->
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }
        return NetworkState(activeLabel, vpnActive, lanNetwork)
    }

    private fun classify(exception: IOException, kind: RouteKind): NetworkIssue {
        return when (exception) {
            is UnknownHostException -> NetworkIssue.DnsFailure
            is SocketTimeoutException -> NetworkIssue.Timeout
            is SSLException -> NetworkIssue.TlsFailure
            is NoRouteToHostException -> NetworkIssue.LanRouteBlocked
            is ConnectException -> if (kind == RouteKind.LanSystemDns || kind == RouteKind.LanFallbackDns) {
                NetworkIssue.LanRouteBlocked
            } else {
                NetworkIssue.Timeout
            }
            is InterruptedIOException -> NetworkIssue.Timeout
            else -> NetworkIssue.Unknown
        }
    }

    private fun Response.toException(diagnostics: RouteDiagnostics): MonitorClientException {
        val issue = code.toNetworkIssue()
        return MonitorClientException(
            httpStatus = code,
            monitorStatus = code.toMonitorStatus(),
            message = code.messageForStatus(),
            diagnostics = diagnostics.copy(success = false, issue = issue),
        )
    }

    private fun Int.toNetworkIssue(): NetworkIssue = when (this) {
        401 -> NetworkIssue.AuthFailure
        429 -> NetworkIssue.RateLimited
        else -> NetworkIssue.ApiFailure
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

    private fun NetworkCapabilities.transportLabel(): String {
        return buildList {
            if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
        }.ifEmpty { listOf("Unknown") }.joinToString(" + ")
    }

    private fun ServerProfile.hostName(): String = java.net.URI(baseUrl).host.orEmpty()
}

data class RouteResponse(
    val body: String,
    val diagnostics: RouteDiagnostics,
)

private data class RoutedResponse(
    val response: Response,
    val diagnostics: RouteDiagnostics,
)

private data class NetworkState(
    val activeNetworkLabel: String,
    val vpnActive: Boolean,
    val lanNetwork: Network?,
)

private class FallbackDns(
    private val hostName: String,
    private val fallbackIps: List<String>,
    private val delegate: Dns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.equals(hostName, ignoreCase = true) && fallbackIps.isNotEmpty()) {
            return fallbackIps.map { InetAddress.getByName(it) }
        }
        return delegate.lookup(hostname)
    }
}

private class NetworkDns(private val network: Network) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return network.getAllByName(hostname).toList()
    }
}

class MonitorClientException(
    val httpStatus: Int?,
    val monitorStatus: ServerStatus,
    override val message: String,
    val diagnostics: RouteDiagnostics,
    cause: Throwable? = null,
) : Exception(message, cause)
