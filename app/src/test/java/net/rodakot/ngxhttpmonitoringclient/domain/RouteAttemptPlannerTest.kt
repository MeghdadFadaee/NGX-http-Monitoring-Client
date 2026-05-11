package net.rodakot.ngxhttpmonitoringclient.domain

import net.rodakot.ngxhttpmonitoringclient.model.RouteKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteAttemptPlannerTest {
    @Test
    fun attempts_usesSystemDnsWhenNoFallbacksOrVpn() {
        assertEquals(
            listOf(RouteKind.SystemDns),
            RouteAttemptPlanner.attempts(hasFallbackAliases = false, vpnActive = false, lanAvailable = false),
        )
    }

    @Test
    fun attempts_addsFallbackDnsWhenAliasesExist() {
        assertEquals(
            listOf(RouteKind.SystemDns, RouteKind.FallbackDns),
            RouteAttemptPlanner.attempts(hasFallbackAliases = true, vpnActive = false, lanAvailable = false),
        )
    }

    @Test
    fun attempts_addsLanRoutesWhenVpnAndLanAreAvailable() {
        assertEquals(
            listOf(
                RouteKind.SystemDns,
                RouteKind.FallbackDns,
                RouteKind.LanSystemDns,
                RouteKind.LanFallbackDns,
            ),
            RouteAttemptPlanner.attempts(hasFallbackAliases = true, vpnActive = true, lanAvailable = true),
        )
    }
}
