# NGX Monitor

NGX Monitor is an Android client for `ngx_http_monitoring_module`. It is built with Kotlin and Jetpack Compose for monitoring a fleet of local or private Nginx servers from a phone.

## Features

- Fleet dashboard for multiple servers with live status, tags, favorites, search, and critical-first sorting.
- Server detail cockpit with system, Nginx, request, disk, history, alert, and route diagnostics views.
- Home-screen widgets for fleet status, server resource bars, metric sparklines, telemetry graphs, and incident watch.
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

## Home-Screen Widgets

The app provides five Android widgets with separate picker previews and labels:

- **NGX Fleet Pulse**: all-server visual status strip, large fleet health state, and priority servers.
- **NGX Server Bars**: one selected server with large status text and CPU, memory, and storage bars.
- **NGX Metric Sparkline**: one selected server and one focused signal with a large value and history graph.
- **NGX Telemetry Graph**: one selected server with CPU, memory, and storage trend lines.
- **NGX Incident Watch**: fleet incident queue for unreachable, blocked, degraded, or insecure servers.

Widgets update automatically through Android's widget scheduler and after the app's periodic background checks. Each widget also has an icon refresh control that fetches fresh monitor data for that widget's scope.

To add a widget:

1. Long-press the Android home screen.
2. Choose Widgets.
3. Select NGX Monitor.
4. For server, metric, and telemetry graph widgets, choose the server during configuration. Metric widgets also ask which signal to graph.

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
  widget/        Android home-screen widgets
```

## App Assets

- Launcher icon foreground/background: `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`
- Splash artwork: `app/src/main/res/drawable/splash_hero.xml`
- Splash background: `app/src/main/res/drawable/splash_screen.xml`
- Widget layouts: `app/src/main/res/layout/widget_fleet.xml`, `widget_server.xml`, `widget_metric.xml`, `widget_graph.xml`, `widget_incidents.xml`

These assets are vector drawables, so they scale cleanly across screen densities.

## Google Play Release

Release configuration, signing instructions, Play Console metadata, and privacy/data-safety drafts live in:

- `docs/PLAY_STORE_RELEASE.md`
- `docs/PLAY_STORE_DATA_SAFETY.md`
- `docs/PRIVACY_POLICY.md`
- `fastlane/metadata/android/en-US/`
