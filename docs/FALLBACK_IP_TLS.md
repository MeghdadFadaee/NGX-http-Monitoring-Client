# Fallback IP Routing And TLS Verification

The app stores the server as a normal URL plus optional fallback IP addresses.
The fallback IPs are used as DNS answers for the URL host; the app does not
rewrite the request URL to an IP address.

## Where It Is Implemented

- `ServerProfile.baseUrl` and `ServerProfile.fallbackIpAddresses` store the URL
  and fallback list in `app/src/main/java/net/rodakot/ngxhttpmonitoringclient/model/MonitorModels.kt`.
- The editor exposes those values as `Base URL` and `Fallback LAN IPs` in
  `app/src/main/java/net/rodakot/ngxhttpmonitoringclient/ui/MonitorApp.kt`.
- `MonitorRepository.buildServer()` normalizes the URL, checks whether plain
  HTTP is allowed, and parses fallback addresses in
  `app/src/main/java/net/rodakot/ngxhttpmonitoringclient/data/MonitorRepository.kt`.
- `RouteAttemptPlanner.attempts()` tries `SystemDns` first, then `FallbackDns`
  when fallback addresses exist. When a VPN is active and a LAN network is
  available, it also adds `LanSystemDns` and `LanFallbackDns`.
- `MonitorHttpClient.clientFor()` installs a custom OkHttp `Dns` implementation
  for fallback routes.

## Request Flow

For a server configured like this:

```text
Base URL: https://monitor.example.com
Fallback LAN IPs: 192.168.1.20, 10.0.0.5
```

the app builds endpoint URLs from the base URL:

```text
https://monitor.example.com/monitor/api
https://monitor.example.com/monitor/health
https://monitor.example.com/monitor/live
```

The first request uses normal system DNS. If that network request fails before
a usable HTTP response is returned, the app tries the fallback route. In that
route, OkHttp still receives a request for `https://monitor.example.com/...`,
but the custom `FallbackDns` returns `192.168.1.20` and `10.0.0.5` when OkHttp
asks how to resolve `monitor.example.com`.

The LAN variants do the same thing through Android's LAN network socket factory
when the active device state includes both VPN and Wi-Fi or Ethernet.

## Why TLS Still Works

TLS verification is based on the URL hostname, not the numeric socket address.
Because the app keeps the request URL as `https://monitor.example.com/...`,
OkHttp still uses `monitor.example.com` for:

- SNI during the TLS handshake.
- Certificate hostname verification after the certificate is received.

That means the server certificate must be valid for `monitor.example.com`.
It does not need to contain `192.168.1.20` as an IP subject alternative name
because the client is not asking TLS to verify `192.168.1.20`.

This is different from requesting:

```text
https://192.168.1.20/monitor/api
```

With that URL, the TLS hostname is `192.168.1.20`, so the certificate would
need to be valid for the IP address. Adding only an HTTP `Host` header is not
enough to fix that because TLS verification happens before the HTTP request is
processed.

If the fallback IP reaches a server that does not present a certificate valid
for the original URL host, OkHttp throws an SSL error and the app reports it as
`TlsFailure`.

## Bash Equivalent

The same behavior in curl is `--resolve`:

```bash
curl --resolve monitor.example.com:443:192.168.1.20 \
  https://monitor.example.com/monitor/api
```

`--resolve` says "connect to this IP for this host and port." The URL still
uses `monitor.example.com`, so curl sends SNI for `monitor.example.com` and
verifies the certificate against `monitor.example.com`.

The sample script in this directory wraps that pattern:

```bash
bash docs/fallback-ip-curl-example.sh \
  --base-url https://monitor.example.com \
  --fallback-ip 192.168.1.20 \
  --fallback-ip 10.0.0.5 \
  --token '<monitor token>'
```

For Basic Auth:

```bash
bash docs/fallback-ip-curl-example.sh \
  --base-url https://monitor.example.com \
  --fallback-ips "192.168.1.20,10.0.0.5" \
  --basic-auth 'user:password'
```

For the health endpoint:

```bash
bash docs/fallback-ip-curl-example.sh \
  --base-url https://monitor.example.com \
  --fallback-ip 192.168.1.20 \
  --endpoint /monitor/health
```

Do not use `--insecure` for this behavior. `--insecure` disables certificate
verification, while the app's fallback keeps verification enabled.
