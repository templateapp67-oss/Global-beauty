# Android device test coverage

`CompanionDeviceTest` runs the real application Activity on Android API 26 and
35 in GitHub Actions, with the actual Firebase Android configuration. It checks:

- App installation/startup, native template search, detail navigation and back.
- Login/signup validation, including existing six-character passwords, without
  creating accounts or sending email.
- Correct Firebase Android app identity, optional-consent defaults, no automatic
  FCM initialization, and the signed-out state.

The emulator workflow uploads instrumentation HTML/XML results per API level.
An emulator result is not physical-phone testing. Full authenticated uploads,
account operations and actual FCM delivery are separate staging acceptance
checks, and are not claimed by these tests. Server push dispatch requires a
Firebase account connection/server identity and an opted-in target device.

Local run with an Android device or emulator connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```
