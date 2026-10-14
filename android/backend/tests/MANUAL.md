# Staging acceptance checks

Use two separate confirmed accounts A and B, Android API 26/35 and a physical Google Play-enabled device. These checks require a deliberately configured staging Supabase/Firebase project.

- Sign up, confirm email, sign in; terminate/restart the process and verify encrypted session restore. Advance expiry/revoke refresh token; verify refresh or explicit sign-in requirement without exposing tokens.
- Request recovery, use the configured email code, set a new password and verify old password rejection.
- Save A's profile, upload a large portrait/landscape/rotated image; verify fit and orientation. Reject SVG/executable, oversized/empty files and malformed photos.
- Upload an allowed PDF as A; open its five-minute signed link, then delete it. Attempt to select/read/write/delete A's profile/object through B's JWT and through anon; all must fail. Confirm B cannot change a profile user_id or photo path to A's.
- Confirm A/B cannot query device tokens or call send-push with their JWTs. Confirm account function ignores any injected user_id and derives identity from Auth. Run `npm ci && npm test` for mocked server-boundary checks as well.
- Sign out, change accounts, kill the app and verify no previous private profile/file remains. Deny all optional permissions and confirm no analytics/crash/push events are sent; no broad storage permission should be requested.
- Enable notifications; deny Android permission, then grant through Settings. Register token; dispatch a server-only data message to A; A sees it, B does not. Revoke app consent offline and verify queued messages remain suppressed. Confirm no FCM `notification` payload is sent.
- Enable/revoke analytics and crash consent separately; verify Firebase DebugView/Crashlytics only after consent. No PII/file contents in events. Consent revocation cannot recall reports already uploaded.
- Delete companion data after fresh sign-in; confirm private objects, mobile profile and device rows are removed, while the shared Auth identity and Studioflow salon records remain. Until a new companion profile is created, the absent profile must cause mobile bucket RLS to reject access.
- Verify talkback labels, text scaling, keyboard/IME, small display layouts, bottom navigation, system back, loading/error/retry and offline states. Existing websites open Custom Tabs; website authentication remains separate.
