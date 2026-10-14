# Nexora native Android companion

Kotlin, Jetpack Compose, Material 3, Navigation Compose, MVVM, Coroutines/StateFlow and repository interfaces. This is a native companion, not a WebView wrapper. Home, the 14 Studioflow templates, template details, authentication, profile, uploads and privacy settings are native. Existing Nexora service/builder links open Android Custom Tabs. Website sessions are separate; no cross-site session transfer is claimed.

## Build

Open this `android/` directory in Android Studio with JDK 17 and Android SDK 35. Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.1.20. Dependencies are pinned. Run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The public Studioflow Supabase URL and publishable key are configured in `backend.properties`; local.properties and environment variables can override them. The Android Firebase client configuration for project `nexora-beauty` is included. No service-role or Firebase service-account credentials are committed. Production distribution needs your signing keystore, package/store configuration, current Play target-SDK review and release QA. Do not commit signing keys or service-role keys.

## Supabase setup (required for accounts/files)

The authorized backend is the existing Studioflow project `zilwgiuiygqqardzutxi` (NEW FINAL TEMLETE APP). Companion tables/buckets and the JWT-verifying `companion-account` Edge Function are provisioned there. Shared Studioflow Auth identities and salon records are retained.

1. For a **new project**, review and apply `backend/schema.sql` once through its SQL editor. The Studioflow project is already provisioned; do not re-run it there. It creates only `nexora_mobile_*` tables and two private Storage buckets with owner-scoped RLS. The script deliberately fails on duplicate policy/bucket names; it does not overwrite existing policies. Back it up and use normal project migrations for subsequent changes.
2. The included `backend.properties` supplies the authorized public client settings. For an alternative project, copy `local.properties.example` to `local.properties` and supply its HTTPS URL and **publishable key** (legacy anon key also works). Alternatively use environment variables `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` at build time. Public keys are included in the APK; authorization is enforced by RLS and verified JWTs. Never put service-role keys in the app.
3. Enable email/password authentication and production SMTP. Keep email confirmation on. Signup confirmation opens the configured Site URL; the user returns to the app and signs in. No bearer tokens are accepted from arbitrary deep links.
4. For native recovery, update the Supabase **Reset Password** email template to display `{{ .Token }}` as the recovery code. The app exchanges the email + code with `/auth/v1/verify`, then updates the password over the authenticated API. Without this email-template setup, password-reset code entry cannot complete.
5. Deploy the Edge Functions under `backend/` using Supabase CLI after linking the intended project. Review `backend/config.toml`: gateway `verify_jwt=false` is intentional because the handler validates the bearer with **Supabase Auth `/user`**, supporting publishable keys. `send-push` requires a separate server secret. Never deploy these endpoints without their handler checks.
6. The runtime provides `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`. Keep them server-side. `companion-account` derives user identity from validated auth, registers/removes the device and deletes only that user’s companion profile/files. Companion-data deletion requires a sign-in within 10 minutes. It never deletes the shared Auth identity or Studioflow salon records.

The repository uses Supabase's Auth, Storage, PostgREST and Edge Function REST APIs through OkHttp. Session tokens are AES-GCM encrypted with an Android Keystore key; backup is disabled. Refreshes are serialized. Errors are sanitized and HTTP bodies/tokens are never logged. Network features require internet; authenticated operations are not faked or stored as successful offline writes.

## Firebase notifications, crash reporting and analytics

Supabase does **not** have a service called Supabase Cloud Messaging. This app uses **Firebase Cloud Messaging** with Supabase Edge Functions as the trusted dispatch layer.

1. Android package `com.nexora.companion` is registered in Firebase project `nexora-beauty`. The supplied public client configuration is included at `app/google-services.json`, enabling the Google Services and Crashlytics Gradle plugins. Firebase initialization is configured, but all optional collection still starts disabled. Client configuration contains public app identifiers, not server credentials.
2. Enable the FCM HTTP v1 API. Store the service-account JSON as Edge Function secret `FIREBASE_SERVICE_ACCOUNT_JSON`, and a strong independent random `PUSH_DISPATCH_SECRET`. These secrets must never be placed in the APK, source control or chat.
3. Deploy `send-push`. Only trusted backend jobs call it using `Authorization: Bearer <PUSH_DISPATCH_SECRET>` and JSON `{ "user_id": "uuid", "title": "...", "body": "..." }`. Do not send health, document, identity or other sensitive content in push messages. The function sends **data-only** messages, so local consent, current account and OS permission are checked before display. It has no public user-callable broadcast path.
4. Settings has separate opt-ins. Collection is disabled in the manifest before initialization; analytics never records email, profile data or files. Crash reporting is off until consent. Revocation disables collection, deletes unsent crash reports, and resets analytics data. This does not erase diagnostics already sent to Firebase; use provider retention/deletion controls for that.
5. Push registration uses WorkManager network constraints and bounded retry. Logout unregisters the device, deletes its FCM token and clears notifications. Local denial suppresses all received messages, including when offline unregister has not completed. Disable/re-enable if token registration exhausts retries. Google Play services are required for FCM. Normal-priority data messages may be delayed in background/Doze; push delivery is not guaranteed.

## Uploads

Use Android system pickers; no broad storage/camera permissions. Files are capped at 5 MB. Photos accept JPEG/PNG/WebP, are decoded with a pixel limit, EXIF-orientation corrected and recompressed with metadata removed. The profile frame fits the entire photo. Documents accept PDF/JPEG/PNG only and use UUID paths under the authenticated user ID. Storage enforces bucket size/MIME restrictions and per-owner access. Filenames in the list are UUIDs by design. Signed viewing links expire after five minutes. No KYC verification or malware scanning is claimed; configure a trusted scanning/review pipeline before collecting sensitive identity documents in production.

## Validate before release

Run `backend/tests/rls_checks.sql` on a staging project and perform the two-user checks in `backend/tests/MANUAL.md`. Unit tests cover browser URL trust boundaries, file-type policy and consent defaults. Real sign-up/confirmation/recovery, session expiry, private uploads, companion-data deletion, FCM and consent flows need the configured staging services and physical/emulated Android testing.
