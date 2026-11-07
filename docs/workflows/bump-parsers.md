# Monthly kotatsu-parsers bump (GPL module)
1. Update the pinned submodule to a chosen commit.
2. Rebuild `:engine-kotatsu`; run adapter fixtures and the class-load test (0 at startup).
3. Generate the diff report: added, removed, renamed, newly broken sources; update the SourceId alias table for renames.
4. Run the canary fleet; regenerate and sign `sources-status.json`.
5. Release notes list added/removed sources.
