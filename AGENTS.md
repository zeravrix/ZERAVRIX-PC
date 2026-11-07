# ZERAVRIX — agent instructions (read first)

ZERAVRIX is a **Windows-native manga/manhwa reader**: Kotlin 2.1.21, Compose Desktop 1.8.2, JVM 21, Skia. PC only. No Android.
The full spec is `docs/BLUEPRINT.md` (v5). This file is the short version. When they disagree, the blueprint wins; tell the user.

## How to work
1. Take ONE ticket at a time from `docs/EXECUTION_PLAN.md` (the first task not marked done in `docs/PROGRESS.md`).
2. Read only the blueprint sections the ticket cites (§ numbers). Do not read the whole blueprint every time.
3. Write the test first when the ticket has an acceptance test. Implement the smallest change that passes.
4. Run the quality gates in `.agents/rules/20-quality-gates.md`. Never mark done with a red gate.
5. Update `docs/PROGRESS.md` (ticket id, date, what changed, what is unverified). Then stop and report.
6. Never invent facts about Windows behaviour, library APIs or site behaviour. Mark anything unverified `[CHECK]` and write a small spike instead.

## Hard rules
- Two editions: **Store** (`:app-store`) and **Website/GitHub** (`:app-website`). `:engine-kotatsu` is GPL-3.0 and may be used ONLY by `:app-website`. Gradle task `verifyNoGplInStore` must pass. (§18.3)
- One HTTP stack: OkHttp. Sources go through the `ZSource` contract (§10.2). No source code touches UI.
- Downloads: per-chapter **CBZ, stored (no compression), original bytes, never re-encoded** by default. Temp file on same drive, then fsync and atomic rename. (§19)
- Speed contract (§21) is a requirement: cold start <= 1.5 s, open title <= 150 ms, first page <= 400 ms cached. No blocking I/O on the UI thread. Lazy parsers: 0 loaded at startup.
- Secrets: DPAPI vault only. Never commit keys, tokens or passwords. Never log tokens or full URLs with credentials.
- Settings are registry rows (§20, §7D): id, default, scope (app/source/title), range, locale keys, search keywords. No ad-hoc preference keys.
- UI: follow the design system (§7C/§7D): tokens, 14 themes, source icons, tab strip. No hard-coded colours or sizes.
- Adult sources: Website only, opt-in, age confirmation. (§18.6)

## Do not
- Copy code from the Kotatsu app (GPL) into core modules. Copy from Mihon only with Apache-2.0 notice kept.
- Add a dependency without writing its licence and why in `docs/PROGRESS.md`.
- Change public contracts (`ZSource`, DB schema, CBZ layout) without a migration and a blueprint note.
- Report "done" without the evidence (test output, benchmark number, screenshot path).

## Where things are
- `.agents/rules/` architecture, gates, licensing/security, UI/UX
- `.agents/skills/` implement-ticket, add-source-template, perf-check
- `docs/workflows/` new-release, bump-parsers, triage-broken-source
