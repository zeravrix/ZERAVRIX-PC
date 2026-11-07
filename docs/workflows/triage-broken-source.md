# Triage a broken source
1. Reproduce with the Test Source screen; capture status code and first 512 bytes of the body.
2. Classify: Cloudflare challenge / layout change / domain change / site down / rate-limit.
3. Native source: fix config or template, refresh fixtures. Kotatsu-engine source: mark broken in `sources-status.json`, report upstream.
4. Add a regression fixture. Record the outcome in PROGRESS.md.
