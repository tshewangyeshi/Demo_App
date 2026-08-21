# Design System — JDWNRH Scheduler

## Product Context
- **What this is:** A hospital appointment scheduling web app — booking, staff/doctor daily operations, and admin management.
- **Who it's for:** Patients (all ages, skewing older), front-desk/nursing staff, doctors, and hospital administrators.
- **Space/industry:** Institutional healthcare / government health service software.
- **Project type:** Web app (not a marketing site) — dense admin/reporting screens alongside a simple patient booking flow.

## Aesthetic Direction
- **Direction:** Institutional / Civic Trust (Industrial/Utilitarian, function-first).
- **Decoration level:** Minimal — typography and color carry the hierarchy, no illustration or stock imagery.
- **Mood:** Calm, trustworthy, clinical. Reads as serious public-health infrastructure, not a generic startup SaaS template.
- **Reference:** NHS.uk (government health service) — strong institutional blue, white/light-gray surfaces, sharp corners, zero decoration, high legibility. Not copied directly (distinct color, distinct accent) — used as the category-conventions grounding.

## Typography
- **Display/Headings:** General Sans (weights 600/700) — clean geometric-humanist grotesque, distinctive without chasing trends. Loaded via Fontshare: `https://api.fontshare.com/v2/css?f[]=general-sans@500,600,700&display=swap`. Fallback: `'Source Sans 3', sans-serif`.
- **Body/Labels/UI:** Source Sans 3 (weights 400/500/600/700) — the same lineage real government design systems (USWDS) use; tested for legibility at scale, including for older patients. Loaded via Google Fonts: `https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@400;500;600;700&display=swap`.
- **Data/Tables:** Source Sans 3 with `font-variant-numeric: tabular-nums` — reference numbers, times, dates all align.
- **Scale:** body 16px/1.5, h1 28-32px, h2 22px, h3 18px, label/caption 12-13px uppercase (0.04-0.08em tracking), never below 12px.
- **Font count:** 2 families only. Neither Inter, Roboto, Arial, nor system-ui is used as primary.

## Color
- **Approach:** Balanced — one institutional primary, one restrained accent, semantic colors kept visually distinct from both.
- **Primary:** `#0B4F6C` (Himalayan Blue, deep teal-blue) — headers, primary nav, links, focus states. Distinct from the generic dev-default indigo (`#1a56db`) the wireframe baseline used.
- **Accent:** `#C98A2C` (Saffron Gold) — used *sparingly*, only for the single primary call-to-action per screen (Book Appointment, primary submit) and active-state highlights (selected slot, active queue item). Never used for large surfaces or body text. A restrained nod to Bhutanese visual culture (saffron in dress, architecture, Buddhist iconography) — not literal (no flag, no dragon).
- **Neutrals:** Ink `#1A1F23` (text, not pure black), muted `#55606A`, borders `#C7CFD5` / `#E2E7EA`, surfaces `#FFFFFF` / `#F5F7F8`.
- **Semantic:** success `#1E7A3D` (bg `#E9F5EC`), warning `#B5560B` (bg `#FCEEE1` — deliberately a distinct amber-orange from the saffron accent so they never collide), error `#B3261E` (bg `#FBEAE9`), info `#0B4F6C` (bg `#E4EDF1`).
- **Dark mode:** Surfaces get real elevation tiers (`#14181B` → `#1B2124` → `#1E2528`), not just an inverted background. Text goes off-white (`#EAEDEF`, never pure white). Primary and accent both lighten/desaturate for dark backgrounds (`#4C93B2` primary, `#E0A748` accent) rather than staying at their light-mode saturation.

## Spacing
- **Base unit:** 8px.
- **Density:** Comfortable on patient-facing screens (booking, my appointments), tighter on admin/reporting tables — same scale, smaller steps, not a different system.
- **Scale:** 2xs(2) xs(4) sm(8) md(16) lg(24) xl(32) 2xl(48) 3xl(64).

## Layout
- **Approach:** Grid-disciplined. Predictable alignment, no asymmetric/editorial layout — this is an operational app, not a marketing site.
- **Max content width:** 640px for single-column forms (login, register), up to 1180px for dashboard/table/report views.
- **Border radius:** Small and precise, not bubbly — `--radius-sm: 4px` (inputs, buttons), `--radius-md: 8px` (cards), `--radius-lg: 12px` (large containers/mockup frames). Reinforces "serious infrastructure" over "friendly consumer app."

## Motion
- **Approach:** Minimal-functional — only transitions that confirm a state actually changed (queue status, form validation, focus).
- **Duration:** 150-250ms.
- **Easing:** ease-out entering, ease-in exiting, ease-in-out moving.
- Respects `prefers-reduced-motion`.

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-08-21 | Initial design system created | Prior baseline (`src/index.css`) was explicitly a wireframe-only placeholder (its own comment said so) — no visual identity existed. Researched NHS.uk as a real government-health-service reference, proposed Institutional/Civic Trust direction via `/design-consultation`, approved by user, HTML preview generated and confirmed before implementation (OpenAI mockup generation unavailable — no API key configured — so used the HTML preview path instead). |
