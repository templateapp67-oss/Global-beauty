# Nexora | The Global Beauty Growth Network

Complete source export of https://nexora-beauty-network.templateapp67.chatgpt.site.

## Contents

- `dist/`: ready-to-host website with 12 pages, JavaScript, CSS, images and fonts.
- `render-source.js`: page rendering source.
- `prepare.mjs`: regenerates rendered HTML pages.
- `template-catalog.json`: template catalogue.
- `reward-image-sources.json`: reward image references.
- `.openai/hosting.json`: original Sites hosting configuration.

## Preview

Run `python3 -m http.server 8000 --directory dist` from the repository root, then open http://localhost:8000.

## Rebuild

With Node.js installed, run `node prepare.mjs`. No npm dependencies are required. Keep the existing `dist/` assets and HTML shell; the generator uses them as inputs.

## Hosting

Publish `dist/` as the static output directory. Each route includes its own `index.html`. Sitemap and canonical metadata refer to the original domain; update them when using a different domain.

## Backend status

This is the existing static website. It has no connected database, SQL migrations, server-side authentication or payment backend. External service links remain as in the original Site. No production database records or credentials are included.

## Studioflow template collection

The home and Templates page now use all 14 templates from [Studioflow](https://github.com/templateapp67-oss/studioflow). Catalogue metadata and sample services were sourced from `src/categoryTemplates.ts` (blob `0885fe5c9c94578c83e65ff7b30a5a7ef8bc7bb6`). The original 28-template collection has been replaced. Template detail dialogs show source covers, palettes and sample service menus. The Use in Studioflow button opens the Studioflow builder; sign in and choose the matching template there. No automatic cross-site template selection or account transfer is implemented.

## Native Android companion

See [`android/README.md`](android/README.md) for the Kotlin / Jetpack Compose / Material 3 companion app, its Supabase Auth and private Storage setup, protected Edge Functions, Firebase Cloud Messaging, and consent-controlled diagnostics. The Android companion is separate from the static website. The Android app is configured with the authorized Studioflow Supabase backend; companion profile tables, private Storage buckets and the authenticated account Edge Function are deployed. Firebase Android client configuration for `nexora-beauty` is included in v1.0.1. Analytics, Crashlytics and FCM SDKs are configured with separate opt-in controls. Trusted server push dispatch still requires Firebase server authorization. GitHub Actions builds the debug APK and runs unit/security-boundary tests. Verified successful build: https://github.com/templateapp67-oss/Global-beauty/actions/runs/38650003894 (source commit `3e88b0c`). Download the APK from that run’s artifacts. Physical-device QA and trusted server push delivery remain pending.


Android emulator verification: [device-test run 38651519754](https://github.com/templateapp67-oss/Global-beauty/actions/runs/38651519754) passed all three instrumentation tests on both Android 8 / API 26 and Android 15 / API 35. Coverage includes actual APK installation/startup, native template search/detail navigation, authentication input validation, Firebase Android app identity, and disabled-by-default optional collection. Per-device reports are attached to that run. This does not verify physical-phone or live push delivery. The server authorization boundary suite also passes all seven tests, including rejection of ordinary user tokens at the push endpoint.
