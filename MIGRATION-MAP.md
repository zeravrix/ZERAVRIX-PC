# ZERAVRIX Native Migration Map

**Status:** Milestone 1 complete  
**Target:** C++20 + WinUI 3 / Windows App SDK main application with a separate Java/Kotlin JVM extension host  
**Compatibility priority:** Existing `SourceProvider`/`Source` extensions remain loadable without C++ rewrites

## 1. Current repository baseline

The current application is a Kotlin/JVM 21 desktop application using Compose Desktop. The root Gradle build includes four production modules: `core`, `reader`, `source-api`, and `desktop-app`. The archive also contains a separately buildable example extension and local-folder source, plus Windows installer/build scripts and historical audit documents. The current production entry point is `desktop-app/src/main/kotlin/com/zeravrix/ZERAVRIX/Main.kt`.

| Current area | Current implementation | Migration owner | Decision |
|---|---|---|---|
| Desktop shell and screens | Compose Desktop in one large `Main.kt` | Native C++ app | Redesign in WinUI 3; preserve user-visible behavior |
| Shared models | Kotlin data classes in `core` | IPC schema + C++ models | Define stable wire DTOs; retain JVM DTO adapters |
| Library/progress | Properties files under `.zeravrix` | Native core/storage | Preserve paths and migration format initially |
| Downloads | Kotlin `DesktopDownloader` with manifests | Native core/download service | Preserve manifest compatibility; redesign concurrency/cancellation |
| Reader | Compose reader with session cache and decode state | Native reader/image pipeline | Replace with bounded caches, virtualization, WIC/Direct2D/WinUI rendering |
| Source contracts | `Source`, `CatalogueSource`, `ConfigurableSource`, `SourceProvider` | JVM host | Preserve API and class-loading model |
| Built-in sources | Kotlin HTML/JSON/Komga/Kavita adapters | JVM host initially | Move only behind host; do not rewrite during first migration |
| Extension installation | `ExtensionStoreClient`, `URLClassLoader`, `ServiceLoader` | JVM host + native UI | IPC commands; add verification/quarantine before load |
| Music | Kotlin/JLayer/local audio and properties stores | Native music service | Preserve tracks/playlists and user-facing controls |
| Installer | Compose distribution + Inno Setup definitions | Native packaging | Replace/standardize with EXE/MSIX packaging |

## 2. Existing extension API

The compatibility contract is defined by the JVM source API. The central `Source` interface exposes stable identity and metadata (`id`, `name`, `language`, `baseUrl`, and capabilities), then asynchronous operations for `popular`, `latest`, `search`, `details`, `chapters`, and `pages`. `CatalogueSource` is a marker interface. `ConfigurableSource` adds a string preference map plus update operations. `SourceProvider` is the ServiceLoader factory entry point.

The current page model carries more than a URL: `MangaPage` includes ordered index, image URL, optional referer, arbitrary HTTP headers, chapter-end marker, and optional chapter label. These fields must be present in the IPC contract because they are required by authenticated sources and chapter transitions.

| JVM contract | Required host behavior | Native-facing IPC representation |
|---|---|---|
| `SourceProvider.create()` | Instantiate provider-created source objects using the extension class loader | Extension/source registration response |
| `Source.id/name/language/baseUrl` | Stable source identity and display metadata | `SourceInfo` |
| `Source.capabilities` | Advertise popular/latest/search/filter support | `SourceCapabilities` |
| `popular/latest/search` | Execute suspend call, return items plus pagination | `MangaPageResult` |
| `details` | Return enriched metadata | `Manga` |
| `chapters` | Return ordered chapter metadata | `Chapter[]` |
| `pages` | Return ordered pages, headers, referer, and flags | `MangaPage[]` |
| `ConfigurableSource.preferences/updatePreference` | Read/update source settings without exposing implementation details | Preference request/response |

The JVM host must load extensions with `ServiceLoader<SourceProvider>` and `URLClassLoader`, and must retain the existing `META-INF/services` discovery mechanism. The main C++ process must never load or execute extension JARs.

## 3. Dependency map

| Module | Important dependencies | Native migration implication |
|---|---|---|
| `core` | Kotlin/JVM 21, coroutines 1.10.2, JLayer 1.0.1 | Core data and downloader behavior become C++ services; JVM host may retain core models/adapters initially |
| `reader` | Compose Desktop, Compose Foundation/Material3/UI, `core` | Replace UI/rendering; keep reader behavior as acceptance criteria |
| `source-api` | `core`, coroutines 1.10.2, Jsoup 1.21.1 | Must remain a Gradle-built JVM host dependency so existing sources continue to work |
| `desktop-app` | Compose Desktop, Compose UI stack, `core`, `source-api`, `reader`, coroutines, Windows JavaFX modules | Replaced by C++ WinUI app; no extension implementation dependency moves into C++ |
| Example extension | Kotlin/JVM plugin 2.1.21, JVM 21, `source-api` on classpath | Use as the first host compatibility fixture |
| Local-folder source | `core` + `source-api`, separate Gradle settings | Build as an optional host extension or migrate later |

## 4. Reader architecture to preserve and improve

The current reader supports single-page, double-page, automatic spread, vertical layout, RTL/LTR direction, zoom, pan, fullscreen, keyboard controls, mouse gestures, translation selection, music controls, page progress, and loading the next chapter. It currently downloads pages serially in source order, decodes each page off the UI thread, and stores decoded bitmaps in an unbounded per-session map. The native redesign must preserve behavior while replacing the memory strategy.

The target pipeline is:

```text
Source page request
    -> JVM host page metadata via IPC
    -> native cancellable HTTP request
    -> atomic disk cache
    -> bounded LRU decoded-image cache
    -> viewport-aware prefetch queue
    -> WIC decode / Direct2D or WinUI image resource
    -> virtualized reader surface
```

The native reader must never retain every decoded page in memory. It should keep a small visible-window cache, cancel obsolete requests when the user jumps, prefetch in reading direction, and dispose decoded resources deterministically. Disk cache keys must remain compatible or be migrated explicitly.

## 5. Storage and compatibility strategy

The current application uses `%USERPROFILE%\\.zeravrix` with subdirectories and files for library metadata, progress, chapter cache, downloads, reader cache, backups, extension JARs, extension-store metadata, repository web sources, source preferences, and music. The first native milestone should read the existing library/progress/download manifests where practical instead of forcing a destructive migration.

Credentials currently stored in source preferences require special treatment. The native application should not copy plaintext password/API-key values into new storage. The JVM host should receive only the specific request-scoped configuration it needs, while the native side stores secrets using Windows Credential Manager/DPAPI or a similarly protected mechanism.

Backups currently do not include all application state. The migration should define a complete backup manifest covering library, progress/history, downloads, source preferences, installed extension packages, extension-store configuration, and music metadata, with explicit exclusion or opt-in rules for secrets.

## 6. Features that must be preserved

| Feature group | Required behavior |
|---|---|
| Library | Add/remove titles, cover/metadata display, cached chapter summaries, reading progress and resume |
| Discovery | Search across sources, source selection, popular/latest where supported, pagination, adult-content visibility control |
| Reading | Single/double/automatic/vertical modes, RTL/LTR, zoom, pan, keyboard/mouse controls, fullscreen behavior, progress updates, next-chapter loading |
| Downloads | Concurrent chapter downloads, retry/error reporting, integrity validation, offline chapter listing and reading, resumable/atomic manifests |
| Sources | Built-in adapters, manually installed trusted JARs, repository index review, enable/disable, source preferences, Komga/Kavita configuration |
| Settings | Theme, language (English/Arabic/French), animation level, artwork themes, reader defaults, tray behavior, refresh schedule, backup schedule, custom icon |
| Music | Local tracks, playlists, playback controls, loop/shuffle, reader integration |
| Windows integration | Maximized bordered window, tray, native shortcuts, DPI-aware scaling, EXE/MSIX distribution |
| Safety/stability | JVM host isolation, restart after host crash, request timeouts, cancellation, source errors, no direct JAR execution in the native process |

## 7. Features that can be redesigned

The following may change internally while preserving behavior: the UI component tree; navigation/state management; reader rendering and image decode pipeline; persistence implementation after compatibility adapters exist; network stack used by the native app; backup format after import migration support; extension-store presentation; and installer technology.

The following should not be redesigned in the first compatibility milestone: `SourceProvider` discovery, `Source` method semantics, `MangaPage` request metadata, source preference keys, extension JAR directory semantics, or the existing source adapter implementations.

## 8. Target process architecture

```text
+-----------------------------+
| ZERAVRIX.exe                |
| C++20 + WinUI 3             |
| UI, reader, storage, music  |
| native HTTP/image pipeline  |
| JVM-host supervisor         |
+--------------+--------------+
               | versioned local IPC
               | Windows Named Pipes
+--------------v--------------+
| ZERAVRIX-JVM-HOST.exe       |
| Kotlin/JVM 21               |
| Source API compatibility    |
| ServiceLoader/URLClassLoader|
| extension/source operations |
+--------------+--------------+
               |
+--------------v--------------+
| Existing source JARs        |
| META-INF/services           |
+-----------------------------+
```

The protocol should use a framed, versioned request/response envelope with correlation IDs, operation names, deadlines, cancellation IDs, and structured errors. Streaming progress/state events should be separate event frames associated with a request. The first implementation can use newline-delimited UTF-8 JSON over a local transport abstraction, with Named Pipes as the Windows transport and a loopback/stdio transport for deterministic tests.

## 9. Milestone gates

| Milestone | Exit criteria |
|---|---|
| 1. Migration map | This document exists and every current module/API/feature has an owner and compatibility decision |
| 2. JVM host | Host starts, loads the example and existing JAR fixtures through ServiceLoader, lists sources, and executes search/details/chapters/pages against deterministic test doubles |
| 3. IPC | Versioned framing, correlation, errors, timeouts, cancellation, and host restart are tested independently of the UI |
| 4. Native shell | C++/WinUI app launches, supervises host, renders navigation, and displays source data without loading JARs |
| 5. Native reader | Bounded LRU cache, virtualized layouts, prefetch/cancellation, offline chapters, zoom/navigation, and failure retry pass tests |
| 6. Feature migration | Library/download/history/settings/music/source-management flows are complete and compatible with existing data |
| 7. Packaging | EXE and MSIX builds are reproducible on Windows, with clean versioning, signing hooks, and installer smoke tests |

## 10. Immediate next step

Implement Milestone 2 first. Add a separate `jvm-host` Gradle application that depends on the existing `core` and `source-api` modules, loads installed JARs using the current extension loader, exposes a host-only command API, and proves compatibility with the example extension and deterministic test extensions before any native UI rewrite begins.
