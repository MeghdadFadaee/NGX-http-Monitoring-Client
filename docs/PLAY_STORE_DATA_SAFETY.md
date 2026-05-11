# Play Console Data Safety Notes

Use this as a starting point for the Play Console Data safety form. Review it before submission because the final answers are your legal responsibility.

## App Data Flow

NGX Monitor is a client for monitor endpoints configured by the user. The developer does not operate a backend for this app.

Stored locally on the device:

- Server names, base URLs, tags, fallback LAN IPs, and alert thresholds
- Monitor API tokens and optional Basic Auth credentials, encrypted with Android Keystore
- Metric samples, alert history, route diagnostics, and widget preferences

Transmitted by the app:

- HTTP(S) requests to user-configured monitor endpoints
- Authentication headers only for the specific endpoint configured by the user
- Notification content generated locally on the device

Not used by the app:

- Advertising ID
- Location APIs
- Contacts, photos, camera, microphone, calendar, or SMS
- Developer-operated analytics or crash reporting SDKs

## Suggested Data Safety Answers

- Data collected by developer: No
- Data shared with third parties by developer: No
- Data is encrypted in transit: Yes, when users configure HTTPS monitor endpoints
- Users can request data deletion: Data is local; users can delete server profiles in-app or clear app data
- App type: utility/productivity
- Account creation: Not required

Important note for the form: the app allows user-configured HTTP endpoints for private LAN monitoring. If you publish with HTTP support enabled, disclose that encryption in transit depends on the endpoint configured by the user.
