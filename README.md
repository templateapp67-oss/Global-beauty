# Nexora | The Global Beauty Growth Network

Source for https://nexora-beauty-network.templateapp67.chatgpt.site, including the deployed web push backend and the separate native Android companion.

## Website and notifications

The website now uses the Sites Vinext / Cloudflare Worker runtime with D1. `app/` serves the existing pages and protected push APIs; `push/` contains the trusted FCM HTTP v1 integration. `public/` contains browser assets and the notification service worker. `legacy-static/`, `render-source.js` and `prepare.mjs` preserve the original page sources. The older `dist/` export is retained for reference; it cannot run the notification backend.

Use Node 22.13+ and the package manager pinned in `package.json`. Run `pnpm install --frozen-lockfile`, `node scripts/prepare-push-pages.mjs`, then `pnpm build`. Run `node --test tests/*.test.mjs` for notification regression tests. Runtime bindings and secret names are documented in [PUSH_IMPLEMENTATION.md](PUSH_IMPLEMENTATION.md).

`drizzle/` contains D1 migrations; `backend/` contains the Supabase business-event adapters, dispatcher and rollback verification scripts. Source files contain no production user data or server credentials. Existing applied migrations should not be blindly rerun against a different environment.

See [AUTOMATIC_NOTIFICATIONS.md](AUTOMATIC_NOTIFICATIONS.md) for setup and [NOTIFICATION_GAP_AUDIT.md](NOTIFICATION_GAP_AUDIT.md) for findings and test boundaries. Browser test delivery was confirmed by the user; genuine booking/reward/commission-to-phone acceptance remains pending. The web backend uses server-side HTTP v1 authorization; native Android server dispatch has its own remaining prerequisites.

## Studioflow template collection

The home and Templates page now use all 14 templates from [Studioflow](https://github.com/templateapp67-oss/studioflow). Catalogue metadata and sample services were sourced from `src/categoryTemplates.ts` (blob `0885fe5c9c94578c83e65ff7b30a5a7ef8bc7bb6`). The original 28-template collection has been replaced. Template detail dialogs show source covers, palettes and sample service menus. The Use in Studioflow button opens the Studioflow builder; sign in and choose the matching template there. No automatic cross-site template selection or account transfer is implemented.

## Native Android companion

See [`android/README.md`](android/README.md) for the Kotlin / Jetpack Compose / Material 3 companion app, its Supabase Auth and private Storage setup, protected Edge Functions, Firebase Cloud Messaging, and consent-controlled diagnostics. The Android companion is separate from the website runtime. The Android app is configured with the authorized Studioflow Supabase backend; companion profile tables, private Storage buckets and the authenticated account Edge Function are deployed. Firebase Android client configuration for `nexora-beauty` is included in v1.0.1. Analytics, Crashlytics and FCM SDKs are configured with separate opt-in controls. Trusted server push dispatch still requires Firebase server authorization. GitHub Actions builds the debug APK and runs unit/security-boundary tests. Verified successful build: https://github.com/templateapp67-oss/Global-beauty/actions/runs/38650003894 (source commit `3e88b0c`). Download the APK from that run’s artifacts. Physical-device QA and trusted server push delivery remain pending.


Android emulator verification: [device-test run 38651519754](https://github.com/templateapp67-oss/Global-beauty/actions/runs/38651519754) passed all three instrumentation tests on both Android 8 / API 26 and Android 15 / API 35. Coverage includes actual APK installation/startup, native template search/detail navigation, authentication input validation, Firebase Android app identity, and disabled-by-default optional collection. Per-device reports are attached to that run. This does not verify physical-phone or live push delivery. The server authorization boundary suite also passes all seven tests, including rejection of ordinary user tokens at the push endpoint.
