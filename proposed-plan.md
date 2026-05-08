# NGX HTTP Monitoring Client App Plan

## Summary

Build a polished Jetpack Compose operations app for monitoring 20+ Nginx monitoring-module servers. The app is local-first, stores encrypted per-server credentials, uses foreground SSE streams for all enabled servers while
the app is open, and uses Android-friendly periodic background checks for alerts.

## Key Changes

- Add app architecture with Compose, Navigation, ViewModels, Room, WorkManager, OkHttp SSE, kotlinx serialization, and encrypted credential storage.
- Create core models: ServerProfile, AuthConfig, MonitorSnapshot, MetricSummary, AlertRule, AlertEvent, and ServerTag.
- Support /monitor/api, /monitor/health, and /monitor/live; send Basic Auth and X-Monitor-Token when configured.
- Add first-run and server setup flow: name, base URL, tags, favorite flag, token, optional Basic Auth, opt-in HTTP toggle, and test connection.
- Store derived metric summaries every 1 minute and full raw JSON snapshots every 15 minutes, retained for 30 days.

## App Experience

- Dashboard: fleet health counts, search, tags, favorites, critical-first sorting, stale/offline indicators, and compact server cards with CPU, memory, disk, request rate, latency, 4xx/5xx, active connections, and last
  update.
- Server detail: live overview plus tabs for System, Nginx, Requests, Network, Disk, Processes, Upstreams, History, Alerts, and Settings.
- Alerts: ship global defaults with per-server overrides for unreachable/stale server, CPU/memory/disk pressure, high 5xx, high latency, and auth/API failures.
- Background monitoring: WorkManager periodic checks using /monitor/health and lightweight API fetches; local notifications fire only on state changes or sustained rule breaches.
- Security UX: HTTPS by default; HTTP allowed only per server with a visible warning. Never log tokens, Basic Auth, or URLs containing secrets.

## Failure Handling

- Treat missing JSON fields as unavailable collectors, not crashes.
- Show clear states for 401, 403, 404, 429, timeout, TLS failure, malformed JSON, and stale SSE.
- Foreground SSE opens one stream per enabled server; if a stream fails repeatedly, fall back to staggered JSON polling for that server and show degraded-live status.
- Apply retry backoff and avoid notification spam with cooldowns and alert state tracking.

## Test Plan

- Unit tests for JSON parsing with missing collectors, auth header creation, alert evaluation, retention pruning, URL validation, and HTTP opt-in rules.
- Repository tests with fake monitor responses for /monitor/api, /monitor/health, and SSE events.
- Compose UI tests for empty state, add server, dashboard filtering/sorting, server detail tabs, and alert rule editing.
- Background-worker tests for periodic checks, notification deduping, and failed-auth handling.

## Assumptions

- No cloud backend or account sync in v1; all data stays on the phone.
- Android minSdk remains 24.
- “SSE everywhere” means all enabled servers while the app is foregrounded; background monitoring uses periodic checks for Android reliability.
- Long history means balanced local retention, not every SSE payload forever.
