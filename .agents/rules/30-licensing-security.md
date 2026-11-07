# Licensing and security rules

Licences: Mihon Apache-2.0 (adapt with notice). Kotatsu app and kotatsu-parsers GPL-3.0: library use only inside `:engine-kotatsu` for the Website/GitHub build. manga-api projects: do not use. Keep `THIRD_PARTY_NOTICES.md` current; every new dependency gets a row.

The Store build must never contain GPL code or load code dynamically (Store policy). CI task `verifyNoGplInStore` enforces the first; the second is reviewed on every PR.

Core licence decision is open (blueprint §15 item 21). Until the owner decides, do not add licence headers claiming a specific licence to core files.

Security: tokens and credentials in DPAPI vault; HTTPS only (no cleartext); validate every downloaded file (magic bytes, HTML sniff); sanitise all file names (reserved names CON, PRN, NUL, trailing dots/spaces, 260-char paths -> long-path prefix); zip-slip checks on any archive extraction; signed `sources-status.json` (Ed25519) verified before use; no secrets in git, logs or crash reports.
Updater: verify signature and hash before installing; never run a downloaded executable without both.
