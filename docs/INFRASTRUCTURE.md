# Side Project Architecture — Java + React Native on GCP

**Target:** small apps shipped to App Store + Play Store, near-zero users at launch, ability to scale if traction happens.
**Constraint:** lowest possible recurring cost without architectural dead ends.

---

## 1. Stack at a glance

| Layer | Choice | Cost |
|---|---|---|
| Mobile app | React Native + Expo (EAS) | ₹0 |
| Web build (optional) | react-native-web → Cloudflare Pages | ₹0 |
| Backend | Spring Boot (modular monolith), Docker → **Cloud Run** | ₹0–400/mo |
| Database | **Neon** (serverless Postgres) | ₹0 |
| Object storage | Cloudflare R2 | ₹0 |
| Image registry | GCP Artifact Registry | ₹0 |
| Secrets | GCP Secret Manager | ₹0 |
| CI/CD | GitHub Actions + Workload Identity Federation | ₹0 |
| Errors | Sentry (backend + mobile) | ₹0 |
| Analytics | PostHog or Firebase Analytics | ₹0 |
| Email | Resend | ₹0 |
| Push | Expo Push (wraps APNs + FCM) | ₹0 |

**Realistic monthly infra bill: ₹0–400.** The dominant recurring cost is the Apple Developer Program at ~₹10,000/year, not hosting.

---

## 2. Topology

```
┌─────────────────────────────────────────┐
│  Expo app (iOS / Android / web)         │
│  EAS Build + EAS Update (OTA)           │
└──────────────────┬──────────────────────┘
                   │ HTTPS (*.run.app)
                   ▼
┌─────────────────────────────────────────┐
│  Cloud Run   [GCP]   asia-southeast1     │
│  Spring Boot container, stateless        │
│  scale-to-zero, max-instances capped     │
└─────────┬──────────────────┬────────────┘
          │                  │
          ▼                  ▼
┌──────────────────┐  ┌──────────────────┐
│ Neon Postgres    │  │ Cloudflare R2    │
│ [AWS] ap-se-1    │  │ user uploads     │
│ scale-to-zero    │  │ zero egress fees │
└──────────────────┘  └──────────────────┘

  Both in Singapore. Different clouds — see §3.
```

Supporting services outside the request path: Artifact Registry (images), Secret Manager (config), GitHub Actions (deploy), Sentry (errors), Cloudflare Pages (privacy policy, terms, account-deletion page).

---

## 3. Region strategy — read this before anything else

**Neon has no Mumbai region.** Its nearest location to India is AWS Singapore (`ap-southeast-1`). The region is fixed at project creation and cannot be changed later; moving means creating a new project and migrating.

This creates a trap. The obvious setup — Cloud Run in Mumbai (`asia-south1`) with Neon in Singapore — puts ~35–45ms between your app server and your database. A single request doing five queries eats 200ms+ in pure network round trips before any work happens. That is worse than the cold-start problem everyone worries about.

**Fix: colocate the app server with the database.**

| Setup | App↔DB | Bangalore user↔App | Verdict |
|---|---|---|---|
| Cloud Run Mumbai + Neon Singapore | ~40ms | ~10ms | ❌ Chatty queries destroy it |
| **Cloud Run Singapore + Neon Singapore** | **<5ms** | **~40ms** | ✅ Correct |
| Cloud Run Mumbai + Supabase Mumbai | <5ms | ~10ms | ✅ But costs $25/mo (see §4) |

**Run Cloud Run in `asia-southeast1` (Singapore).** The ~40ms from Indian users to Singapore is imperceptible on a mobile app. The 40ms per query between app and DB is not.

### Why the per-query distinction matters

One API request typically fires five to ten queries. Each one pays the app↔DB round trip.

- Mumbai app + Singapore DB: 10 queries × 40ms = **400ms of pure waiting added to every request**
- Singapore app + Singapore DB: 10 queries × 3ms = 30ms, invisible

Moving the server to Singapore costs your users ~30ms extra **once per request**, not per query. That trade is free money.

### What the region IDs actually are

Cloud providers split the world into regions and give each a string ID. Nothing more.

| ID | Provider | Place |
|---|---|---|
| `asia-southeast1` | GCP | Singapore |
| `asia-south1` | GCP | Mumbai |
| `ap-southeast-1` | AWS | Singapore |
| `ap-south-1` | AWS | Mumbai |

For Cloud Run it's one deploy flag:

```bash
gcloud run deploy my-api \
  --image=asia-southeast1-docker.pkg.dev/PROJECT/repo/my-api:latest \
  --region=asia-southeast1
```

Change the flag, redeploy, the service runs somewhere else. No migration, no architecture work.

### Your backend and database will be on different clouds

This is worth stating plainly because it looks odd at first:

- **Cloud Run** is a Google product. It runs on GCP and nowhere else.
- **Neon** is an independent company that runs its infrastructure on AWS. That's why its region IDs are AWS IDs.

So this architecture is GCP compute talking to AWS-hosted Postgres. "Colocated" means both are physically in Singapore, not that they share a private network — traffic goes over the public internet, TLS-encrypted, travelling a few kilometres across one city instead of across the Bay of Bengal. That's the 1–5ms.

You never create an AWS account, never see an AWS bill, never open the AWS console. You get a connection string from Neon and a bill from Neon. AWS is their supplier, not yours.

Cross-cloud is normal and fine at this scale. Supabase also runs on AWS; PlanetScale runs on AWS and GCP. It only becomes a cost concern at high volume, where egress meters on both sides start to add up — and you'd be far past free tiers by then.

### The one irreversible decision in this whole document

Cloud Run's region is a flag you can change any time. **Neon's region is fixed at project creation** and cannot be changed afterwards; moving means creating a new project and migrating the data.

So when you sign up for Neon, deliberately pick **AWS Singapore (`ap-southeast-1`)**. Everything else here is reversible in an afternoon. This one isn't.

---

## 4. Database: Neon vs Supabase

### Verdict: Neon

You are writing a Spring Boot backend with jOOQ. That single fact decides it.

Supabase's value is auth + storage + realtime + auto-generated REST + row-level security — the layer that lets a client talk directly to Postgres with no backend at all. **You have a backend.** You will use Spring Security for auth, R2 for files, and your own controllers for the API. You would be paying for a bundle you route around.

### The decisive difference

| | Neon | Supabase |
|---|---|---|
| Free tier | 0.5 GB storage/project, 100 CU-hours/month, up to 100 projects | 500 MB DB, 1 GB files, 5 GB egress, 50K MAU, 2 projects |
| **Idle behaviour** | **Scales to zero after 5 min, resumes in a few hundred ms** | **Pauses after 7 days, cold start 10–30 seconds, manual resume** |
| India region | ❌ Singapore is nearest | ✅ Mumbai |
| Branching | Yes, git-style, genuinely useful for migrations | Yes |
| Backups on free | Thin — roll your own | None |
| Next tier | Usage-based, no monthly minimum | $25/mo Pro (includes $10 compute credit) |

Supabase's 7-day pause is disqualifying for an app that real people downloaded from a store. A user taps your icon after a quiet week and waits 30 seconds. Neon's suspend resumes in under a second and is invisible.

### When to switch to Supabase

- You decide to ship an app with **no backend at all** (client → Postgres via RLS). Perfectly valid for something simple; Supabase is the right tool then.
- You need Mumbai specifically for data residency or measured latency, and you're willing to pay $25/mo to skip the pause.
- You want auth + file storage + realtime bundled and would otherwise spend weeks wiring them yourself.

### Neon caveats to know

- Databricks acquired Neon in 2025 for roughly $1B. It still runs as a standalone product and prices actually dropped afterwards, but the roadmap is now set by a company whose main interest is AI workloads, not side projects.
- Pricing model changed post-acquisition. One documented change so far, but it's a usage-based meter, not a flat fee — watch it.
- Neon's region list is AWS's region list, minus wherever they've chosen not to deploy. They previously offered Azure regions and deprecated them. Mumbai's absence is a business decision, not a technical limit, and could change.
- Free-plan projects inactive for 90+ days are subject to deletion (policy effective Oct 5, 2026). Not an issue for a live app; relevant for abandoned experiments.
- The 100 CU-hours/month is per project. A low-traffic app with scale-to-zero enabled stays well inside it. A busy always-on app will not.

**Neither locks you in.** Both are stock Postgres. `pg_dump` out, `pg_restore` in. Keep your schema in Flyway migrations and switching providers is an afternoon.

---

## 5. Backend design rules (day one)

These cost nothing now and make scaling a config change rather than a rewrite.

- **Stateless.** No in-memory sessions, no local disk writes, no sticky routing. Cloud Run will run N copies of your container.
- **Everything in Docker.** One `Dockerfile`, one image, same artifact in every environment.
- **Flyway from commit one.** Schema in version control, no manual DDL, ever.
- **Config via env vars only.** Nothing environment-specific compiled into the jar.
- **`-XX:MaxRAMPercentage=75`** so the JVM respects the container's memory limit instead of reading the host's.
- **Uploads go to R2**, never the filesystem. Container disks are ephemeral.
- **One `/health` endpoint**, structured JSON logs to stdout.
- **Modular monolith.** Package by feature, not by layer. Microservices at this scale buy nothing but distributed debugging.

### Connection pooling — the gotcha that will bite you

Cloud Run scales horizontally. Ten instances means ten HikariCP pools, and Neon has connection limits.

1. Use Neon's **pooled** connection string (the one with `-pooler` in the hostname), not the direct one.
2. Set `spring.datasource.hikari.maximum-pool-size: 3` (not the default 10). Per instance × instance count is what Neon actually sees.
3. Set `minimum-idle: 0` so idle instances hold nothing.

### Cold starts

Plain Spring Boot on Cloud Run boots in 5–15s. Options in increasing order of effort:

| Approach | Startup | Cost | Effort |
|---|---|---|---|
| Accept it | 5–15s | ₹0 | none |
| Spring Boot CDS/AOT | 3–8s | ₹0 | low, build config |
| `min-instances=1` | 0 | ~₹900/mo | trivial, but kills the cost advantage |
| GraalVM native image | 50–100ms | ₹0 | **high** — reflection hints, 10+ min builds, jOOQ/Spring friction |

**Start by accepting it.** With a handful of users the container is warm most of the time anyway. Do CDS/AOT when it annoys you. Only reach for GraalVM if traffic becomes spiky and real — it is its own project, not a config flag.

---

## 6. Keeping the cost at zero — the playbook

### Hard rules

1. **No Cloud Load Balancer.** A Global External HTTPS LB is ~$18/month and would be your largest line item. Your client is your own mobile app; nobody sees the URL. Use the `*.run.app` hostname until you have a web frontend humans type into.
2. **Cap `max-instances` at 5** while small. This is your only real protection against a bug, a retry storm, or a scraper turning into a four-figure bill.
3. **Set a GCP budget alert at ₹500** with email notification. Do this before deploying anything.
4. **No Cloud SQL, ever, for this project.** There is no always-free tier — only the $300/90-day credit. Cheapest usable instance is $10–25/month billed 24/7, which cancels out the entire reason you chose Cloud Run.
5. **R2 over GCS** for user files. R2 charges zero egress; GCS does not, and egress is where object storage bills actually come from.
6. **Keep scale-to-zero on** for both Cloud Run and Neon. Idle cost is the whole game at this stage.
7. **Colocate app and DB in the same region** (§3). Cross-region egress is metered on both sides and query latency compounds.

### Soft wins

- Multi-stage Docker builds — smaller images pull faster, which shortens cold starts.
- Set Cloud Run concurrency to 80 (default) rather than 1. One JVM handles many concurrent requests; low concurrency multiplies your instance count and your cost.
- Use EAS free build tier; subscribe to the $19/mo tier only during an active release week, then cancel.
- Buy the Apple Developer account (~₹10,000/yr, recurring) only when you are weeks from submitting. Google Play is a **one-time** $25 (~₹2,200) so buy that whenever.
- Ship one app first. Don't build a shared platform for "some small applications" until the second one proves what's actually common.

### Explicitly skip for now

Redis · message queues · API gateway · Cloud Armor · Terraform · Kubernetes · staging environment · custom domain · CDN · read replicas · service mesh

Every one of these is real and useful at some scale. None at eleven users.

---

## 7. What you still need to build

### Before deploying
- [ ] Artifact Registry repo (first 0.5 GB free)
- [ ] Secret Manager entries: Neon connection string, JWT signing key, third-party keys
- [ ] GitHub Actions workflow: build → push → `gcloud run deploy`
- [ ] **Workload Identity Federation**, not a downloaded service-account JSON key. 20 extra minutes; means no long-lived credential sits in GitHub secrets.
- [ ] R2 bucket + API token

### Before store submission
- [ ] **Sign in with Apple** — mandatory because the app offers Google Sign-In. Hard rejection
  from Apple review otherwise. See [`TECH_GUIDE.md`](TECH_GUIDE.md) for how it slots into the
  existing auth flow (same "verify with provider → issue our own JWT" shape as Google).
- [ ] **In-app account deletion** — required by both stores. Google additionally wants a publicly reachable web URL to request deletion without installing the app.
- [ ] Privacy policy + terms as static pages (Cloudflare Pages, free)
- [ ] Push notifications — Expo Push covers APNs + FCM behind one API. Needs an APNs key and an FCM project.
- [ ] Google Cloud OAuth consent screen + Android/iOS OAuth client IDs (needed for Google Sign-In
  to work outside internal testing — see `TECH_GUIDE.md`).

### The moment real users exist
- [ ] **Sentry** on both backend and mobile. Without it, a crash on someone's phone in another city is completely invisible to you. This is the least optional item on the page.
- [ ] PostHog or Firebase Analytics — tells you whether anyone opens the app twice
- [ ] Rate limiting — Bucket4j in Spring. A public API with a mobile client gets hammered eventually.
- [ ] **Nightly `pg_dump` to R2** via a GitHub Actions cron. Fifteen minutes to write, costs nothing, and free-tier backup guarantees are thin.

### If you charge money
Both stores mandate in-app purchase for digital goods and take 30% (15% under the small-business thresholds). **RevenueCat** is free under $2,500/month of tracked revenue and saves writing receipt validation twice. Only physical goods or real-world services can use Razorpay and skip the cut.

---

## 8. Scaling path

Do the next step only when a metric demands it. Skipping ahead is how side projects acquire bills.

1. **Raise `max-instances`.** Cloud Run already autoscales; you capped it deliberately. Lifting the cap is the entire first scaling step.
2. **Turn off Neon's scale-to-zero** (paid plan) once the suspend/resume cycle is constant anyway.
3. **`min-instances=1`** to eliminate cold starts, once the latency matters more than the ~₹900/month.
4. **Redis** (Memorystore, or Upstash for the cheap serverless option) for caching and rate limiting.
5. **Read replicas** and a CDN for static assets.
6. **GraalVM native image** if traffic is spiky enough that cold starts dominate cost.
7. **Only now** consider splitting a service out of the monolith.

Because the backend is a stateless container, steps 1–3 are console settings, not engineering work. That is the entire point of the day-one rules in §5.

---

## 9. Alternatives and exit paths

**Cloud Run is GCP-only.** The equivalents elsewhere are AWS App Runner / ECS Fargate and Azure Container Apps. They all run a Docker image; Cloud Run is the one with genuine scale-to-zero and per-100ms billing, which is why it's the pick here.

Your container is portable regardless. The same `Dockerfile` deploys to any of the below with no code change — only the deploy command differs. That portability is the reason the day-one rules in §5 exist.

If Cloud Run proves annoying, the container moves with near-zero rework:

| Where | Cost | Why you'd move |
|---|---|---|
| Railway | $5/mo Hobby, $20/mo Pro incl. $20 credits | Best DX; Singapore region; no cold starts |
| Fly.io | ~$11/mo for a 2 GB machine | True scale-to-zero, Docker-native, no base fee |
| DigitalOcean droplet (BLR1) | $6–12/mo | Bangalore region, flat price, you own ops |
| AWS Lightsail | $7–12/mo | Mumbai region, if you want AWS on the résumé |
| Render | $7/mo Starter | Free tier spins down 15 min → 30–50s wake; skip free |

**On AWS:** if your account is new, the twelve-months-free story is dead. Accounts created on or after 15 July 2025 get $100 credits (up to $200 via onboarding tasks) expiring after six months, not a separate 12-month allowance. Don't pick AWS expecting free.

**On Azure:** no reason to choose it here unless someone hands you credits.

**On AWS as a learning exercise:** legitimate, but use Lightsail or App Runner. Wiring ECS + ALB + RDS + Secrets Manager for eleven users teaches you AWS billing, not AWS architecture.

---

## 10. One-line summary

Spring Boot container on Cloud Run in Singapore, Neon Postgres in the same region, Cloudflare R2 for files, Expo for the apps, everything else on free tiers — roughly ₹0/month until people actually show up, and nothing in the design has to change when they do.
