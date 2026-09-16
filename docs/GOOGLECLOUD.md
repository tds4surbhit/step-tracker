# Step-Tracker — Setup Status

Context handoff. Full architecture rationale (why Cloud Run + Neon, cost
model) is in [`INFRASTRUCTURE.md`](INFRASTRUCTURE.md); overall project
context is in [`../PROJECT_PLAN.md`](../PROJECT_PLAN.md).

---

## The stack (decided)

| Layer | Choice |
|---|---|
| Mobile | React Native + Expo (EAS Build + EAS Update) |
| Backend | Spring Boot, modular monolith, Docker → Cloud Run |
| Database | Neon serverless Postgres |
| Object storage | Cloudflare R2 (only if actually needed) |
| Registry | GCP Artifact Registry |
| Secrets | GCP Secret Manager |
| CI/CD | GitHub Actions + Workload Identity Federation |

**Region decision:** Cloud Run in `asia-southeast1` (Singapore), Neon in AWS
`ap-southeast-1` (Singapore). Deliberately NOT Mumbai — Neon has no Mumbai
region, and splitting app/DB across Mumbai↔Singapore adds ~40ms *per query*,
which compounds badly on multi-query requests. Colocating in Singapore costs
Indian users ~30ms once per request instead.

---

## ✅ Done

**GCP project**
- Project created: name `Step-Tracker`, **ID `steptracker09`** (IDs are
  permanent — this is the one baked into image paths)
- Org: `surbhit-4zeracing-org`
- Both default "My First Project" projects shut down
- `gcloud config set project steptracker09` — confirmed active in Cloud Shell

**APIs enabled**
- `run.googleapis.com`
- `artifactregistry.googleapis.com`
- `secretmanager.googleapis.com`

**Artifact Registry**
- Repo `apps`, format docker, location `asia-southeast1`
- Image path: `asia-southeast1-docker.pkg.dev/steptracker09/apps/api:latest`

**Billing**
- Free trial active: ₹28,664 credits, **expires 14 December 2026**
- Budget alert: ₹500/month, thresholds 50/90/100/150% of actual spend
- Scoped to "This billing account" (fine — only one project exists)
- Duplicate budget deleted

---

## ⬜ Not done yet

**Immediate**
- [ ] **Neon signup — must select AWS Singapore `ap-southeast-1`.** Region is
      locked at project creation and cannot be changed. This is the only
      irreversible decision in the whole setup.
- [ ] Verify Step-Tracker is linked to the billing account (else
      `gcloud run deploy` fails with a confusing error)

**Before first deploy**
- [ ] Spring Boot project scaffold + Dockerfile (multi-stage)
- [ ] Flyway migration for the initial schema
- [ ] Secrets into Secret Manager: Neon connection string, JWT signing key
- [ ] GitHub Actions workflow: build → push → `gcloud run deploy`
- [ ] Workload Identity Federation (NOT a downloaded service-account JSON key)

**Deferred, low priority**
- [ ] Add a 100%-of-*forecasted* threshold to the budget (catches runaway
      spend on day 3 instead of day 26)
- [ ] Re-scope budget to the project specifically once a second app exists
- [ ] Expand the "No organization" node in Manage Resources — there are
      pre-2023 projects there, worth confirming nothing is running

**Calendar**
- [ ] Reminder for **late November 2026** to activate the full billing
      account. Trial expires 14 Dec; if it lapses, deployed services go dark.
      Activating keeps all remaining credits and only bills after they're
      gone. (Note: December is also the wedding month, so don't rely on
      remembering.)
      - The autopay mandate on the card was **deliberately cancelled**
        (2026-09-14) as a precaution against surprise charges. This means
        the November step is now "re-add a card + set up a fresh mandate"
        rather than a one-click upgrade — same RBI e-mandate friction as
        initial signup, so don't leave it to the last day.

---

## Rules that must not be skipped

1. **`--max-instances=5`** on every Cloud Run deploy. The budget alert only
   emails; this is the actual ceiling.
2. **No Cloud Load Balancer.** ~$18/month for zero benefit — the client is
   the mobile app, nobody sees the URL. Use `*.run.app`.
3. **No Cloud SQL, ever.** No always-free tier, $10–25/month billed 24/7,
   cancels out the entire reason for choosing Cloud Run.
4. **Neon pooled connection string** (hostname contains `-pooler`), and
   `spring.datasource.hikari.maximum-pool-size: 3`, `minimum-idle: 0`.
   Cloud Run scales horizontally — N instances means N pools against Neon's
   connection limit.
5. **Stateless backend.** No in-memory sessions, no local disk writes. This
   is what makes scaling a console setting rather than a rewrite.
6. **`-XX:MaxRAMPercentage=75`** so the JVM respects the container limit
   instead of reading the host's.

Accept 5–15s JVM cold starts for now. CDS/AOT later if annoying. GraalVM
native only if traffic becomes genuinely spiky — it's its own project, not a
flag.

---

## App-specific open question

If the app reads step data from the phone: HealthKit (iOS) and Health
Connect (Android) both have their own permission flows and review
requirements. Apple rejects apps requesting health permissions without a
clear justification string. This may force a native module and knock the
build off pure Expo Go. **Prototype the permission flow early**, not last —
it's the most likely part to eat a weekend and the most likely to change the
build setup.

Backend side is simple by contrast: a time-series of step counts per user
per day. Roughly one table. Neon's free tier holds years of it.

---

## Store requirements (for later, but they cause rejections)

- Sign in with Apple is **mandatory** if Google login is offered
- In-app account deletion required by both stores; Google also wants a
  public web URL for deletion requests
- Privacy policy + terms as static pages (Cloudflare Pages, free)
- Google Play: one-time $25 (~₹2,200) — buy any time
- Apple: $99/year (~₹10,000) — **buy only when weeks from submitting**, it's
  recurring
