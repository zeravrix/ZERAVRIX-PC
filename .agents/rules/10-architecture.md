# Architecture rules (blueprint §3, §6, §10, §18)

Modules (Gradle): `:core-model`, `:core-net` (OkHttp, cache, rate limits), `:core-db` (SQLDelight), `:core-sources` (ZSource SPI + native engine E2), `:engine-kotatsu` (GPL, E1), `:feature-reader`, `:feature-library`, `:feature-browse`, `:feature-downloads`, `:feature-settings`, `:ui-design` (tokens, components), `:app-store`, `:app-website`.

Dependency direction: app -> feature -> core. Features never depend on each other except through `core-*` interfaces. `:engine-kotatsu` depends only on `:core-sources` and `:core-net`.

Concurrency: coroutines. UI state in immutable data classes + StateFlow. All disk and network on `Dispatchers.IO`; decode on a bounded decode dispatcher. Single-flight for identical in-flight requests.

Caches are byte-bounded (not count-bounded): image memory cache, decoded-bitmap cache, HTTP cache, thumbnails. Thumbnails decode at display size.

Sources: `ZSource` exposes popular/latest/search/details/chapters/pages with typed errors (NetworkError, CloudflareChallenge, ParseError, RateLimited, Broken). Parsers are instantiated lazily on first use. Health is tracked per source; broken sources are hidden from search by default and shown with a reason in Sources UI.

Database: SQLite via SQLDelight, WAL mode, migrations tested from every released schema. Library, history, progress, downloads index, source health, categories.

Downloads: see blueprint §19. Queue is persisted; states QUEUED, RUNNING, PAUSED, RETRY_WAIT, FAILED, DONE; pages parallel (4 per source), round-robin across sources.
