package net.rodakot.ngxhttpmonitoringclient.domain

import net.rodakot.ngxhttpmonitoringclient.model.RouteKind

object RouteAttemptPlanner {
    fun attempts(
        hasFallbackAliases: Boolean,
        vpnActive: Boolean,
        lanAvailable: Boolean,
    ): List<RouteKind> {
        val result = mutableListOf(RouteKind.SystemDns)
        if (hasFallbackAliases) result += RouteKind.FallbackDns
        if (vpnActive && lanAvailable) {
            result += RouteKind.LanSystemDns
            if (hasFallbackAliases) result += RouteKind.LanFallbackDns
        }
        return result.distinct()
    }
}
