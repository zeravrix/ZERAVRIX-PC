# Execution plan (first 40 task cards)
Z-xxx ids refer to blueprint §14 and §18-§22. Do them in order, one at a time. Each card: goal, cite, done-when.

## Phase 0 - Baseline
- **T-001** Build the existing 1.5.0 source (both editions) on Windows; record build time, installer size, startup time. Cite §1. Done when: numbers in PROGRESS.md.
- **T-002** Add gates: detekt, ktlint, `verifyNoGplInStore` (stub), `:bench` skeleton. Cite §13, §21.
- **T-003** Add `THIRD_PARTY_NOTICES.md` and dependency licence table. Cite §0.2, §18.3.

## Release 1.5.x hot-fix
- **T-004 (Z-210)** Downloader: 4 parallel pages per chapter, stream to disk, header-only magic-byte + HTML sniff (first 512 bytes), cached download index (no repeated folder scans). Cite §19. Done when: 100-page chapter downloads at least 3x faster on the same network; HTML-body rejection test.
- **T-005 (Z-050a)** Real source refresh (contacts sources, not just saves a timestamp). Cite §1 F1.
- **T-006 (Z-130)**, **T-007 (Z-166)**, **T-008 (U2 Favorites highlight)**, **T-009 (Z-023)** - see blueprint §14 for each card.

## Release 1.6 - Look and feel, Downloads v2 core, reader settings core
- **T-010** Design tokens, 14 themes, component kit (`:ui-design`). Cite §7C.
- **T-011 (Z-230)** Settings registry (`SettingDef`, scopes, search, reset). Cite §7D, §20.9.
- **T-012** Settings screen shell, grouped and searchable. Cite §7D.
- **T-013 (Z-211)** CBZ writer (stored) + verifier + `ComicInfo.xml` + `zeravrix.json`. Cite §19.2.
- **T-014 (Z-212)** Benchmark spike: temp-dir + pack vs streaming into ZIP; decide; record.
- **T-015 (Z-213)** CBZ reader (read stored entries directly).
- **T-016 (Z-214)** Persisted download queue (journal file until SQLite): states, retries 2/4/8 s, Retry-After, 60 s watchdog.
- **T-017 (Z-215)** Folder-to-CBZ verified migration.
- **T-018 (Z-216)** In-memory index + WatchService.
- **T-019 (Z-231..233, Z-238)** Reader settings core 25 rows (mode, fit, gap, crop, tap zones, keep screen on, transitions, brightness). Cite §20.
- **T-020 (Z-240..243)** Speed layer: stale-while-revalidate, single-flight, sized thumbnail decode, byte-bounded caches. Cite §21.2.

## Release 1.7 - Sources (kotatsu engine can start on a parallel branch earlier)
- **T-021** OkHttp stack, cookies, Cloudflare handling, rate limits. Cite §6.1.
- **T-022 (Z-200)** `:engine-kotatsu` module + pinned submodule + composite build. Cite §18.4.
- **T-023 (Z-201)** JS runtime spike (GraalJS vs Rhino vs WebView2).
- **T-024 (Z-202)** `ZeravrixLoaderContext`.
- **T-025 (Z-203)** Model mapping to `ZSource`; SourceId + alias table; `isBroken` from the generated enum.
- **T-026 (Z-204)** Lazy instantiation + class-load test (0 at startup).
- **T-027 (Z-205)** Source health tracking + signed `sources-status.json`.
- **T-028 (Z-206)** Adult gating (Website only) + age confirmation.
- **T-029 (Z-207)** Canary fleet (about 60 sources per night).
- **T-030 (Z-208)** Sources UI: list, language filter, health badge, "N working today". Cite §10.9.
- **T-031 (Z-209)** Acceptance: 20 sampled sources pass on fixtures; listed vs working counts shown honestly.

## Release 1.8 - Data, feed, persisted queue
- **T-032** SQLDelight schema + migration from 1.5.0 JSON. Cite §3.
- **T-033** Queue moves from journal to SQLite.
- **T-034** Update feed with per-title strategy (skip COMPLETED after one full fetch).
- **T-035** Notifications + tray.

## Release 1.9 - Library and remaining reader settings
- **T-036** Categories, filters, sort, bulk actions.
- **T-037 (Z-234..237, Z-239)** Remaining reader settings, profiles, per-title overrides. Cite §20.
- **T-038** Download automation: schedule, bandwidth limit, keep-awake. Cite §19.2.
- **T-039 (Z-221)** Optional background WebP converter (default off); decide per §15 item 24.
- **T-040 (Z-209a)** Mihon bridge (E3), only if the owner chose "build".
