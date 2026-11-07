# New release
1. All gates green on `main`; PROGRESS.md has no open `[CHECK]` for shipped features.
2. Bump version; update CHANGELOG and THIRD_PARTY_NOTICES.
3. Build Store MSIX and Website installer from the tagged commit; run `verifyNoGplInStore`.
4. Sign both; verify signatures; smoke test install, upgrade from previous release, uninstall.
5. Publish GitHub release; update the signed update-feed JSON; then Store submission.
