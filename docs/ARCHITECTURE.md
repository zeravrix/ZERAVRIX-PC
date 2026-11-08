# ZERAVRIX PC architecture

```text
Native Windows Compose Desktop UI
    ├── Library: saved manga/manhua/manhwa
    ├── Search: query a manually installed source
    ├── Source extensions: install trusted desktop JARs
    ├── Manga details: metadata and chapter actions
    └── Native comic reader
         ├── Single page
         ├── Double page
         ├── Automatic desktop spread
         ├── Vertical webtoon
         ├── RTL / LTR
         ├── Zoom and pan
         └── Keyboard and mouse controls
                 │
                 ▼
Application services
    ├── LibraryStore: properties-based desktop persistence
    ├── DesktopDownloader: bounded concurrent page downloads
    ├── PageCache: remote or local page materialization
    └── SourceManager: trusted extension registry and lifecycle
                 │
                 ▼
Desktop Source API
    ├── Source: unique ID, metadata, search, details, chapters, pages
    ├── CatalogueSource: catalogue-capable source marker
    ├── HttpSource: HTTP/2, redirects, headers, HTML parsing helpers
    ├── ConfigurableSource: source preferences boundary
    └── SourceProvider: ServiceLoader entry point for manually installed JARs
                 │
                 ▼
User-installed source JARs
```

The boundary is intentionally Mihon-inspired: sources describe how to retrieve comic metadata and page URLs, while the host owns the library, persistence, cache, download infrastructure, source installation, and UI. The implementation is desktop JVM code and is not binary-compatible with Android Mihon APK extensions.

The reader workflow takes inspiration from Kotatsu’s separation of reader modes and settings, adapted for Windows input and window management. The PC version uses Compose Desktop windows, keyboard shortcuts, mouse gestures, and a bundled Windows Java runtime instead of Android activities, fragments, RecyclerViews, or APK packaging.

ZERAVRIX accepts only comic page streams represented by `MangaPage`. No PDF, EPUB, CBZ, CBR, or general document reader is included in the application boundary.
