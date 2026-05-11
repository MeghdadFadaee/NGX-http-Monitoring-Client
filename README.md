# NGX Monitor

NGX Monitor is an Android client for `ngx_http_monitoring_module`. It is built with Kotlin and Jetpack Compose for monitoring a fleet of local or private Nginx servers from a phone.

## Features

- Fleet dashboard for multiple servers with live status, tags, favorites, search, and critical-first sorting.
- Server detail cockpit with system, Nginx, request, disk, history, alert, and route diagnostics views.
- JSON API polling via `/monitor/api` and health checks via `/monitor/health`.
- Foreground live streaming via `/monitor/live` Server-Sent Events.
- Encrypted per-server credentials for monitor tokens and optional Nginx Basic Auth.
- Local SQLite history with 1-minute summaries, 15-minute raw snapshots, and 30-day pruning.
- Background periodic checks with local Android notifications.
- VPN/DNS resilience with per-server fallback LAN IP aliases while preserving HTTPS hostnames for TLS/SNI.

## Monitor API

The app expects each server base URL to expose `/monitor`.

Common endpoints used by the app:

- `GET /monitor/api`
- `GET /monitor/health`
- `GET /monitor/live`

Authentication rules:

- Monitor tokens are sent as `X-Monitor-Token`.
- Basic Auth is sent on every monitor request when configured.
- If both are enabled on the server, configure both in the app.

## Local Network And VPN Notes

For private domains, keep the main server URL as the HTTPS domain, for example:

```text
https://edge-1.home.example
```

If VPN or DNS breaks local resolution, add fallback LAN IPs in the server editor:

```text
192.168.1.20, 192.168.1.21
```

The app keeps the URL hostname unchanged and only substitutes DNS results internally. This keeps HTTPS certificate verification and SNI aligned with the private domain.

Android or the VPN app may still block LAN access completely. In that case, the app shows the failure in the Route panel but cannot override the VPN policy.

## Security

- Prefer HTTPS for monitor endpoints.
- Prefer header tokens over query-string tokens.
- Avoid sharing screenshots that expose internal hostnames or route details.
- Keep monitor endpoints behind least-privilege ACLs.
- Do not reuse production admin passwords for Basic Auth.

## Build

From the project root:

```powershell
$env:GRADLE_USER_HOME='C:\Users\meghdad\AndroidStudioProjects\NGXhttpMonitoringClient\.gradle-user'
.\gradlew.bat :app:assembleDebug --console=plain
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```powershell
$env:GRADLE_USER_HOME='C:\Users\meghdad\AndroidStudioProjects\NGXhttpMonitoringClient\.gradle-user'
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

## Project Structure

```text
app/src/main/java/net/rodakot/ngxhttpmonitoringclient/
  background/    JobScheduler checks and notifications
  data/          SQLite storage, encrypted credentials, HTTP/SSE client
  domain/        URL rules, alert evaluation, route planning, sampling policy
  model/         Server, metric, alert, and route data models
  ui/            Compose command-deck UI
  ui/theme/      Material theme and colors
```

## App Assets

- Launcher icon foreground/background: `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`
- Splash artwork: `app/src/main/res/drawable/splash_hero.xml`
- Splash background: `app/src/main/res/drawable/splash_screen.xml`

These assets are vector drawables, so they scale cleanly across screen densities.
