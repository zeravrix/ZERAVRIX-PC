# Quality gates (a ticket is not done until all pass)

1. `./gradlew build` green for both `:app-store` and `:app-website`.
2. `./gradlew test` green. New behaviour has a test; bug fixes have a regression test.
3. `./gradlew detekt ktlintCheck` clean (no new suppressions without a comment why).
4. `./gradlew verifyNoGplInStore` passes (Store classpath contains no GPL module).
5. Anything touching reader, library, startup, downloads: run the `perf-check` skill and record numbers against §21.3 budgets in `docs/PROGRESS.md`.
6. UI ticket: screenshot at 100 % and 150 % Windows scaling, light and dark theme, saved under `docs/shots/<ticket>/`.
7. No `TODO` without a ticket id. No `println`; use the logger. No blocking call on the UI thread.
8. Windows-specific behaviour (long paths, reserved names, file locks, DPAPI, tray) is verified on a real Windows machine or marked `[CHECK]` in PROGRESS.md. Do not claim it works from a Linux run.
