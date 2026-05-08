package net.rodakot.ngxhttpmonitoringclient.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlRulesTest {
    @Test
    fun normalizeBaseUrl_trimsTrailingSlash() {
        assertEquals("https://monitor.example.com", UrlRules.normalizeBaseUrl(" https://monitor.example.com/ "))
    }

    @Test
    fun validateHttpPolicy_rejectsHttpWithoutOptIn() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlRules.validateHttpPolicy("http://10.0.0.10:8080", allowHttp = false)
        }
    }

    @Test
    fun endpoint_appendsMonitorPath() {
        assertEquals(
            "https://monitor.example.com/monitor/api",
            UrlRules.endpoint("https://monitor.example.com/", "/monitor/api"),
        )
    }
}
