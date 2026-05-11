package net.rodakot.ngxhttpmonitoringclient.domain

import java.net.URI
import java.net.InetAddress

object UrlRules {
    fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Base URL is required")
        }
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            throw IllegalArgumentException("Use an http or https URL")
        }
        if (uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("URL must include a host")
        }
        return trimmed
    }

    fun validateHttpPolicy(baseUrl: String, allowHttp: Boolean) {
        val scheme = URI(baseUrl).scheme?.lowercase()
        if (scheme == "http" && !allowHttp) {
            throw IllegalArgumentException("HTTP must be explicitly enabled for this server")
        }
    }

    fun endpoint(baseUrl: String, path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizeBaseUrl(baseUrl) + normalizedPath
    }

    fun parseFallbackIps(input: String): List<String> {
        return input
            .split(',', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .onEach { value ->
                runCatching { InetAddress.getByName(value) }
                    .getOrElse { throw IllegalArgumentException("Invalid fallback IP: $value") }
            }
    }
}
