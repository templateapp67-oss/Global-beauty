# Nexora notification end-to-end gap audit

2026-10-15. This report covers the Nexora FCM integration, its business event
adapters and the existing account opt-in entry points. It is not a claim that
all unrelated salon/payment/partner features have been audited.

## Observed production state

At audit start: one connected business account; private outbox empty; one-minute
dispatcher enabled and recent jobs succeeded. The user had confirmed Android
Chrome foreground/background test-notification delivery, tap-through and
notification disable/re-enable. No genuine business event had yet been observed.
Credentials remain in runtime secret storage / Supabase Vault; none in source.

## Gaps found and fixed

| Gap | Fix |
| --- | --- |
| Only Studioflow bookings generated booking pushes | Added adapters on existing `public.bookings` for its stored owner and verified `user_id`; no email/phone matching or guest identity guessing |
| Owner/customer legacy app had no connect control | Added the existing proof-link button to the owner dashboard and customer Activity page; salon and partner controls retained |
| Cancelled booking or changed financial status could leave obsolete events pending | Re-check authoritative row, recipient and status before leasing; superseded events are not dispatched |
| A new account link could receive older queued events | Enforce link-created-before-event; reconnecting the same account preserves its original consent timestamp |
| Multi-device sends produced duplicate inbox entries | Added a user-scoped inbox keyed by business event, separate from per-device delivery attempts |
| A connected account without a current push device lost all visibility | Save the event in its inbox as no_subscription; no Google send and no opt-in inferred |
| Display could precede send acknowledgement | Keep displayed as terminal success; late Google acceptance cannot overwrite it |
| Interrupted background receipt lacked recovery | Bounded receipt queue retries on the next real message/page connection; missing Origin is accepted only on the random one-time receipt capability endpoint; foreign Origin is rejected |
| Token rotation could leave the previous token registered | Replace only the current user's explicitly supplied previous device hash; no cross-user deletion |
| Revoked permission and enabled/test buttons could disagree | Refresh real browser state on visibility; suppress test until this browser is registered; remove revoked subscription |
| Every business notification opened the generic hub | Fixed allowlisted owner/customer/partner destinations; test notifications still open the hub; hostile URLs fall back safely |
| Delivery failures and queue lag were invisible to admin | Added admin-only health: active timer, last run, backlog age, queue states and per-device outcomes |
| Cleanup depended on new traffic | Timer clears expired proof tickets / old finished outbox even while idle; dispatch prunes stale D1 subscriptions/history |
| Failed retry limit and expiry were conflated | Distinct failed/expired outcomes, bounded exponential backoff, stable per-device idempotency and a dispatch time budget |
| Returning to a previously used reward/commission status suppressed a genuine later update | Generate a new event only for an actual status change; repeated unchanged writes still do not enqueue |
| Chat widget covered controls on mobile screenshots | Hide it only on the notification pages and reserve bottom navigation space |

## Verification evidence

- 27 automated tests pass, including real RSA signing with a test-only key and
  mocked Google HTTP responses, SQLite migration preservation, partial retry,
  idempotent replay, invalid tokens, receipt races, owner-only health, one-time
  receipt capabilities, logout suppression, worker receipt retry and safe clicks.
  These tests generate no notifications on a user's device.
- Live Postgres rollback verification passes: canonical owner/customer routing,
  pending-payment and unpaid-advance exclusion, cancelled-event supersession,
  link-time privacy, same-account renewal, wrong-lease rejection, backoff and
  browser-inaccessible private helpers. No test booking/payment commits.
- Original 12 page main markup and external login links are checked unchanged.
- Web Worker and modified source-app type-checks and production builds pass.
  The web TypeScript target excludes the separate Android/Deno backend runtime;
  generated-page lookup typing was corrected without changing route behavior.
- Security advisor reports only the intentional INFO about RLS without browser
  policies on a service-only private table; no new notification warning/error.
- Post-test counts: one original Studioflow booking, zero legacy bookings,
  zero commission/reward claim fixtures, zero queued test events, one existing
  account connection. No real financial status was altered or charged.

## Remaining acceptance boundaries

1. Genuine business event → intended recipient's phone is still NOT VERIFIED.
   Existing manual test pushes do not prove a real booking or claim transition.
   Make a genuine booking on a published salon site, then observe both the owner
   alert and confirmed-only customer alert. Use two appropriately linked users.
2. Reward/commission real phone acceptance requires an authorized actual status
   transition. Fake transactions, rewards and settlement must not be inserted.
3. Guest bookings without verified Auth `user_id` have no user-specific customer
   push destination. The owner alert still works; customer must sign in/opt in.
4. External apps backed by another Supabase project are not integrated by this
   project's triggers. Providers must supply trusted events before coverage can
   be claimed; no cross-project credentials or mappings have been invented.
5. FCM acceptance, browser display completion and human observation are separate.
   The OS can mute/block notifications, and offline devices can miss the 10-minute
   FCM TTL. No delivery guarantee or fabricated display/read PASS is shown.
6. At-least-once transport has a crash window after Google accepts but before the
   database records acceptance. Stable tags limit duplicate visual notifications;
   perfect exactly-once delivery is not claimed.
7. Browser QA automation is unavailable; new UI and updated service-worker behavior
   have automated coverage but require a real browser follow-up. New worker
   activates without changing caching/authentication or intercepting requests.

## Reproduction

`node --test tests/*.test.mjs`

Run `backend/notification-gap-verification.sql` through the authorized database
connection; it ends with ROLLBACK. Append `backend/notification-gap-fixes.sql`
after the initial automatic-push and dispatcher schema. Apply the additive D1
migration through the normal Sites publishing workflow. Do not replay previously
applied schema SQL. All secrets must remain outside these files.

Follow-up schema order: apply `backend/notification-gap-fixes.sql`, then
`backend/notification-transition-events.sql` exactly once after the initial
automatic-push schema. The transition regression is reproducible with
`backend/notification-transition-verification.sql` (ROLLBACK). A status returning
from cleared → reversed → cleared now produces distinct committed transition
events; an unchanged repeated cleared write produces none.

The connected account's published Studioflow salon was found (`bestsalon`), so
asking the user to locate its public link again is unnecessary. The genuine
booking/phone acceptance step can use that existing salon. This audit has not
created or paid a genuine booking on the user's behalf.
