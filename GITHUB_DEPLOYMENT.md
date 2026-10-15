# GitHub checks and deployment status

The public GitHub repository stores source; production currently runs on ChatGPT Sites.

## Configured

- `.github/workflows/web-notifications.yml` runs the 27 Node notification regression tests on matching pushes, pull requests and manual dispatch. It requires no Firebase or production credentials, does not send real notifications and is not a production deployment workflow.
- Existing Android build and device workflows are preserved.
- Live Site runtime revision 4 has `FIREBASE_SERVICE_ACCOUNT_JSON`, `PUSH_TOKEN_KEY`, `PUSH_ADMIN_EMAILS` and `PUSH_BRIDGE_SECRET` marked secret. `FCM_VAPID_PUBLIC_KEY` is public client configuration. Values are excluded from this repository.
- `.env.example` documents empty variable names. D1 `DB` remains a platform binding.

## Deployment boundary

GitHub Actions automatic deployment to the existing ChatGPT Site is **not configured**. The available GitHub connector cannot manage Actions Secrets, and no supported noninteractive Sites CI deployment credential/API has been established. Short-lived editor source credentials are not durable deployment credentials and must not be stored in GitHub.

Keep production credentials in the existing Sites runtime storage. Do not duplicate them into GitHub for the test job. Continue publishing through the supported Sites save/deploy workflow, retaining the existing project, D1 binding, authentication boundary and runtime secrets. A direct generic Cloudflare deployment is not interchangeable: this application relies on Sites-provided verified identity headers and platform bindings.

If a supported CI deployment integration becomes available, configure a narrowly scoped deployment identity for that integration and verify source-to-version-to-live provenance before enabling automatic deployment. No production secret values or deployment credentials were added to GitHub by this change.
