# Automatic business notifications

Scope: the existing Studioflow salon and customer accounts on Supabase
`zilwgiuiygqqardzutxi`, and the live Growth Partner portal (its deployed browser
bundle was checked against the same project). The original Nexora hub's ChatGPT
identity and its already opted-in browser tokens remain unchanged.

## Events, in requested order

1. `studioflow_bookings` INSERT → new-booking notification for `owner_id`.
2. Booking inserted confirmed, or status actually changes to confirmed →
   confirmation for `customer_id`. Pending payment does not count as confirmed.
3. `growth_partner_reward_claims` status changes → reward claim update;
   `growth_partner_commissions` creation/status changes → commission update,
   both for the stored `partner_user_id`.

Triggers do not modify prices, payments, booking status, reward eligibility,
commission amounts, programme rules or business authentication. They enqueue
only future committed records, with no historical backfill. Copy is generic;
customer personal details and financial totals are not placed on lock screens.
The canonical legacy `bookings` table and unrelated external booking providers
are not covered by this Studioflow integration.

## Verified connection and consent

A signed-in salon owner/customer chooses **Connect Nexora notifications** in
Studioflow. A partner uses the same button in `/partner/notifications`. The RPC
creates a 256-bit, five-minute, single-use account proof, stored only as a hash.
The fragment link opens the Nexora notification hub, which removes the fragment
from the address bar and asks the signed-in ChatGPT visitor to explicitly
connect that business account. The server derives the Site identity from
platform headers. Email matching and caller-supplied user IDs are not used.

Only after connection do future business events route to that Site account's
opted-in browsers. No new notification permission is silently granted. Hub users
can disconnect business accounts independently of disabling browser push.
These are separate business and ChatGPT logins; the existing login flows remain.

## Delivery and security

Private Supabase outbox + leased batches; backend dispatcher authorized by a
random shared capability kept in Supabase Vault and Sites runtime secrets.
Bridge RPCs reveal no data without that credential. Tables have RLS and no
browser grants. Account-proof minting requires the existing authenticated UID.
A one-minute dispatcher job drains committed events. A failed request can retry
up to eight times within 24 hours; no subscription means no notification, and
accepted per-device deliveries are not resent. Transport is at-least-once:
a crash after Google acceptance but before recording it can still duplicate a
message; a stable notification tag replaces the same event notification.
Outbox results distinguish accepted, no_subscription, retry, expired and failed.
FCM acceptance is not proof of device display. Display receipts remain separate.

Browser tokens remain encrypted in D1. Existing test receipts are retained by an
additive D1 migration. Business notification history has fixed safe dashboard
links, and excludes any tokens/credentials. The public dispatcher rejects user
JWTs and anonymous calls. Account connect/disconnect requires authenticated
same-origin JSON and explicit confirmation.

## Verification

- 15 automated boundary/migration/template tests pass; no real FCM delivery is
  implied by those tests.
- Live Postgres transaction tests verify owner/customer routing, confirmed-only
  timing, reward/commission transitions, deduplication, one-time proof/replay,
  table privacy, invalid bridge credentials, leasing and disconnect. Source
  fixtures are temporary replicas and all queue changes are rolled back.
- TypeScript checks and production builds pass for hub, Studioflow and partner
  integration changes. Managed browser QA is unavailable in this environment.
- The user previously confirmed real Android Chrome foreground, background,
  click-through and disable/re-enable **test-notification** delivery.
- A real business-event notification on a linked user's phone remains a
  separate acceptance check. Do not label it PASS before making a genuine
  booking or authorized reward/commission change and observing the phone.

Deployment details and remaining user acceptance steps are reported in the
conversation. No production financial records were changed for testing.

## Deployment verification (2026-10-15)

- Hub version11 and Studioflow version42 deployed successfully.
- Partner integration commit `9bac9d16448bb7da5256e3df4437cf089bdc515f`
  deployed successfully to the existing Vercel partner app.
- Vault-authorized pg_net → production hub dispatcher → Supabase bridge returned
  HTTP200: processed0, accepted0, skipped0, retry0. This verifies transport/auth,
  not business-event phone delivery. There were no connected business accounts.
- The `nexora-beauty-business-push` one-minute job is active. It makes no HTTP
  call while the queue is empty. Original live business data was not mutated.
- Verification left zero queue fixtures and zero account-link tickets behind.

Reconstruction order: apply `backend/automatic-push.sql`; securely configure the
same `nexora_business_push_bridge` Vault secret and `PUSH_BRIDGE_SECRET` Sites
secret; deploy the hub's additive D1 migration and code; then apply
`backend/automatic-push-dispatch.sql`. Never include the credential value in SQL
source. `backend/automatic-push-verification.sql` is rollback-only.


Gap audit follow-up: legacy `public.bookings` is now covered as well. Stale
events, inbox aggregation, receipt recovery, consent timing, health and retries
were reviewed and fixed; see NOTIFICATION_GAP_AUDIT.md for scope and remaining
genuine business-event phone acceptance requirements.
