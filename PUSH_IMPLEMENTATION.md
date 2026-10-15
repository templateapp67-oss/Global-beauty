# Nexora web push: audit, implementation and acceptance

Audit date: 2026-10-15 (India).

## Existing Site audit

The selected Site is `appgprj_6ac9a7a283dc8191bfbb6198e67e307a`, public,
https://nexora-beauty-network.templateapp67.chatgpt.site/.
Before this change its hosting manifest declared only a static `dist/` directory.
It had no hosted server routes, database binding, runtime environment entries,
notification service worker or account-linked browser token store. Existing login
buttons lead to other Sites/apps; those sessions cannot be assumed to authenticate
this origin. The existing 12 pages, assets and external login links are retained.

The Sites platform supports Cloudflare Workers, D1 and runtime secrets. The Site
now uses the supplied Vinext/Worker integration, D1 migrations and dispatch-owned
optional ChatGPT sign-in specifically for notification account ownership. Existing
salon/partner authentication is unchanged; cross-app identity linking is not claimed.
R2 is not needed for notification tokens and remains unbound.

## What is implemented

- `/notifications/`: explicit opt-in permission, supported-browser checks,
  Firebase token registration/renewal, disable and safe sign-out.
- `/firebase-messaging-sw.js`: actual Firebase background messages and foreground
  forwarding, account scope check, system notification and click handling.
- `/admin/notifications/`: Site-owner email allowlist enforced on the server;
  Send Test Notification targets only the current admin's registered browser.
- `/api/push/status`, `register`, `unregister`, `test`, `receipt`.
- D1 token rows keyed by hash, user ID derived only from platform identity;
  tokens encrypted with AES-256-GCM and random IV. No endpoint returns tokens.
- Same-origin JSON writes, explicit consent, bounded payloads, max 10 browsers
  per user, 30-day registration freshness, 30-second atomic test-send limit.
- HTTP v1 dispatch via service-account OAuth JWT signed with Web Crypto. Private
  credentials remain server-only, and Google calls have timeouts.
- FCM acceptance and browser display receipts are distinct. The latter requires
  the one-time 256-bit receipt capability contained in the real FCM payload;
  it means showNotification completed, not that a human saw/read the message.
- Invalid FCM registration tokens are removed on UNREGISTERED errors. Test
  history older than seven days and stale tokens are pruned during operations;
  there is no scheduled cleanup configured.

## Secrets / Firebase prerequisites

Configured through Sites runtime secret storage (never in source):
- `PUSH_TOKEN_KEY`: generated 32-byte AES key; keep stable to decrypt existing rows.
- `PUSH_ADMIN_EMAILS`: current Site owner's verified account email allowlist.

Configured after the initial audit (2026-10-15):
- `FCM_VAPID_PUBLIC_KEY`: public key from **nexora-beauty → Project settings →
  Cloud Messaging → Web Push certificates**. It is public, not the private key.
- `FIREBASE_SERVICE_ACCOUNT_JSON`: server-only credential for nexora-beauty with
  `cloudmessaging.messages.create` (e.g. Firebase Cloud Messaging API Admin role).
  Configure as a **secret** through Sites settings; never paste it into frontend
  code, a public repository or chat. The configured service account project must
  match nexora-beauty. FCM HTTP v1 and FCM Registration APIs must be enabled.

Deploy a saved Site version after changing environment values. The provided service-account credential was securely configured. Google OAuth
and FCM validate-only authorization returned HTTP200; no IAM changes were made. The provided Firebase Web config and Android
`google-services.json` do not authorize server sends or supply a Web VAPID key.

## Runtime limitations

Firebase Admin SDK is not installed on this Site. A full Node.js Admin SDK
compatibility test on Cloudflare Workers has not been established; installing
firebase-admin on a Windows PC does not add it to a hosted ChatGPT Site.
The implemented HTTP v1 adapter is Firebase's supported alternate server protocol
and uses only HTTPS and Web Crypto, suitable for the Worker runtime.
No fallback demo notification or client-side admin credential is present.

Managed browser QA is unavailable because the required control-browser skill
is not exposed. No physical Android phone is connected. Therefore OS permission,
FCM token issuance, authenticated production actions, foreground/background
notifications and Android notification-shade delivery remain **NOT VERIFIED**
until Firebase prerequisites and a real opted-in browser are available.

## Automated verification

`node --test tests/push.test.mjs`: nine tests passing against an in-memory SQLite
adapter: unauthorized/admin boundary, CSRF, encrypted ownership, cross-user
isolation, explicit consent/setup blocking, absent server credentials, receipt
replay/expiry, AES-GCM random IV and preservation of all original page main markup
and external login URLs. These are automated boundary tests using synthetic
identities/tokens, not production auth or real FCM delivery tests.

`node --check` passes for server, client and service worker. The production Worker
build passes. Live HTTP checks are recorded in the task handoff separately.

## Android Chrome acceptance procedure

1. Configure the two missing Firebase runtime values securely and deploy.
2. In normal Android Chrome, open this exact HTTPS Site (outside an embedded
   browser/incognito), visit Notifications, sign in with ChatGPT and opt in.
3. Confirm registration succeeds. Permission alone is not token registration.
4. As the configured administrator, visit the notification dashboard on the
   same browser. Tap Send Test Notification, then switch tabs or go Home.
5. Record server result: failed / FCM accepted / browser display receipt. Look
   in Android's notification shade and tap the message to return to this Site.
6. Repeat with the Site foregrounded, with background tab, with notifications
   disabled, signed out and a non-admin account. Non-admin sends must fail403;
   signed-out writes401; cross-user removal must not affect another account.
7. A real phone observer confirms actual receipt. Do not call token generation,
   FCM acceptance, local simulations or emulator startup a phone-delivery PASS.


Follow-up: the user has confirmed real Android Chrome foreground/background
notification receipt, click-through and disable/re-enable test delivery.
See AUTOMATIC_NOTIFICATIONS.md for business-event integration scope and the
separate genuine business-event acceptance check.
