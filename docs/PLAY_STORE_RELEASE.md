# Google Play Release Checklist

This project is configured to build Google Play compatible Android App Bundles.

## Current Project Readiness

- Package name: `net.rodakot.ngxhttpmonitoringclient`
- Minimum SDK: 24
- Target SDK: 36
- Version: `VERSION_NAME` / `VERSION_CODE` in `gradle.properties`
- Release build: R8 minification enabled, resource shrinking enabled
- App data backup: disabled for app data, databases, shared preferences, and files
- Release signing: read from environment variables or `local.properties`

## Create An Upload Key

Create an upload keystore outside the repository:

```powershell
keytool -genkeypair `
  -v `
  -keystore "C:\secure\keys\ngx-monitor-upload.jks" `
  -storetype JKS `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -alias ngx-monitor-upload
```

Copy `local.properties.example` to `local.properties` and set:

```properties
NGX_RELEASE_STORE_FILE=C:\Users\meghdad\keystores\ngx-monitor-upload.jks
NGX_RELEASE_STORE_PASSWORD=<redacted>
NGX_RELEASE_KEY_ALIAS=ngx-monitor-upload
NGX_RELEASE_KEY_PASSWORD=<redacted>
```

Do not commit `local.properties` or the keystore.

## Build Release Artifacts

Unsigned release sanity build:

```powershell
$env:GRADLE_USER_HOME='C:\Users\meghdad\AndroidStudioProjects\NGXhttpMonitoringClient\.gradle-user'
.\gradlew.bat :app:bundleRelease --console=plain
```

Signed upload bundle, after signing values are configured:

```powershell
$env:GRADLE_USER_HOME='C:\Users\meghdad\AndroidStudioProjects\NGXhttpMonitoringClient\.gradle-user'
.\gradlew.bat :app:bundleRelease --console=plain  # for bulding aab
.\gradlew.bat :app:assembleRelease --console=plain # for bulding apk
```

Output:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Before Uploading To Play Console

1. Create the app in Play Console.
2. Enable Play App Signing.
3. Upload `app-release.aab`.
4. Fill the store listing using `fastlane/metadata/android/en-US`.
5. Upload phone screenshots and a 1024x500 feature graphic.
6. Host `docs/PRIVACY_POLICY.md` as a public web page and paste that URL into Play Console.
7. Complete Data Safety using `docs/PLAY_STORE_DATA_SAFETY.md`.
8. Complete Content Rating. Suggested category: utility/productivity/server monitoring.
9. Complete Target Audience. This app is for server administrators, not children.
10. Run an internal test release before production.

## Release Version Bump

For every Play upload, increment `VERSION_CODE` in `gradle.properties`.

Example:

```properties
VERSION_CODE=2
VERSION_NAME=1.0.1
```

## Official References

- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Android app signing: https://developer.android.com/studio/publish/app-signing
- Build and upload an Android App Bundle: https://developer.android.com/guide/app-bundle
- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
