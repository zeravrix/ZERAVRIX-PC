# UI/UX rules (blueprint §7C, §7D, §10.9-10.11, §20)

- Fast first: show cached content immediately (stale-while-revalidate), then refresh. Skeletons, never blocking spinners for lists.
- Library grid uses lazy layouts with stable keys; cover images decode at cell size; no layout shift when covers load.
- Everything keyboard reachable; focus visible; scale-safe at 100/125/150/200 % DPI; WCAG AA contrast in all 14 themes.
- Reader: Paged L2R/R2L/Vertical, Webtoon (continuous), Double page; chrome auto-hides; tap zones and keys configurable; position preserved when any layout setting changes.
- Settings: grouped, searchable, each row shows current value and a reset; overrides at source/title level are marked with an icon; reading profiles save/apply a set of rows.
- Copy: short, plain, localisable (all strings in resource keys). Errors say what happened and one action to take.
- Motion: respect "reduce motion"; durations from tokens.
