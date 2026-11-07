---
name: perf-check
description: Use after changes to startup, library, reader, caches or downloads, or when asked whether ZERAVRIX is fast enough.
---
# Performance check (blueprint §21)
1. Run the benchmark harness (`:bench`): cold start, library open with 5,000 titles, open title, open chapter cached/uncached, scroll 200 pages, download a 100-page chapter to CBZ.
2. Compare to budgets in §21.3 / §9. Record numbers and the machine in `docs/PROGRESS.md`.
3. If any budget is missed by more than 10 %: profile (JFR), name the cause, fix or open a ticket. Never raise a budget silently.
4. Check startup loads 0 parser classes and idle RSS in tray is at most 50 % of window-open RSS.
5. Never compare numbers from different machine classes.
