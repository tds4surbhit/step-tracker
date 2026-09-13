# Step Tracker — UI Design Prompt

Use this as the starting prompt for an AI design tool (Claude, Figma AI/Figma Make, v0, etc.)
to generate the mobile UI. It's derived directly from the backend contract in
[`API.md`](API.md), so the screens map cleanly onto real data the backend already returns.

---

## Prompt

> Design a mobile-first fitness app called **Step Tracker** for iOS and Android. It's a
> minimal, motivating daily step counter — not a social network. Tone: clean, energetic,
> encouraging, not clinical. Support both light and dark mode.
>
> **Core user flow:** sign up → grant health permissions → see today's steps vs. goal on a
> home dashboard → check history/trends → adjust goal/profile in settings.
>
> **Screens to design:**
>
> 1. **Onboarding / Auth** — welcome screen, Sign Up (email, password, display name), Log In.
>    Keep it to 1–2 fields per step, minimal friction.
> 2. **Health Permission / Device Connect** — a single screen asking the user to connect
>    Apple Health (iOS) or Health Connect (Android) for automatic step tracking, with a
>    "I'll enter steps manually instead" fallback option.
> 3. **Home / Today Dashboard** — the hero screen. A large circular progress ring showing
>    today's step count against the daily goal (e.g. "6,342 / 10,000 steps"), a motivating
>    status line (e.g. "63% of your goal — keep going!"), and a way to jump to History.
> 4. **History / Trends** — a scrollable list or bar chart of daily step counts over the last
>    7/30 days, with a date-range picker. Tapping a day shows that day's exact count.
> 5. **Goal Settings** — a simple screen to view and edit the daily step goal (numeric input
>    or slider, default 10,000).
> 6. **Profile** — view/edit display name, date of birth, height, weight, gender; show email
>    read-only. Logout action here.
>
> **Data model available from the backend** (so components/fields match real fields, not
> placeholders):
> - Profile: `displayName`, `email`, `dateOfBirth`, `heightCm`, `weightKg`, `gender`
> - Steps: a `stepCount` (integer) per calendar `date`, tagged with its `source`
>   (`healthkit` / `health_connect` / `sensor` / `manual`)
> - Goal: a single `dailyGoalSteps` integer per user
>
> **Explicitly out of scope** — do not design for: friends/leaderboards, social feed,
> notifications/achievements, or calorie/distance metrics. This is a steps-only MVP.
>
> Deliver: a component set (progress ring, stat card, date picker, list item, primary/secondary
> buttons, input fields) plus the 6 screens above, at mobile (iOS + Android) breakpoints.

---

## Notes for whoever runs this prompt

- If the tool asks for a design system/brand, there isn't one fixed yet — let it propose one,
  optimizing for legibility of large numbers (the step count) and a strong light/dark mode.
- The backend enforces `dailyGoalSteps > 0` and non-negative step counts — no need to design
  for negative/zero-goal edge cases beyond basic input validation.
- If/when native health permission flows (HealthKit / Health Connect) are wired up, screen 2
  becomes a system permission dialog, not a custom UI — the custom screen is just the
  explanation/CTA leading into it.
