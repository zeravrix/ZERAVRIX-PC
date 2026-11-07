# ZERAVRIX Local IPC Protocol v1

## Transport

Protocol v1 is UTF-8, newline-delimited JSON. Each line is one complete request or response envelope. The transport must be local-only and reliable. On Windows, the production transport is a restricted Windows Named Pipe. The JVM host server is transport-neutral and currently accepts any pair of input/output streams, which enables deterministic tests and allows the C++ client to supply the Named Pipe stream implementation.

The native application owns process supervision and starts `ZERAVRIX-JVM-HOST.exe`. The JVM host owns all Java class loading and extension execution. The native process must never open a JAR with a JVM class loader.

## Request envelope

```json
{
  "protocol": 1,
  "requestId": "c0ffee-001",
  "op": "search",
  "sourceId": 9000001,
  "page": 1,
  "query": "title"
}
```

`protocol` is required and must equal `1`. `requestId` is a caller-generated correlation identifier and must be unique among in-flight requests. `op` identifies the operation. Additional fields depend on the operation.

## Response envelope

Successful response:

```json
{
  "protocol": 1,
  "requestId": "c0ffee-001",
  "ok": true,
  "result": {
    "kind": "manga_page",
    "items": [],
    "hasNextPage": false
  }
}
```

Failure response:

```json
{
  "protocol": 1,
  "requestId": "c0ffee-001",
  "ok": false,
  "error": {
    "code": "SOURCE_BLOCKED",
    "message": "Source verification is required",
    "retryable": false
  }
}
```

## Operations

| Operation | Required fields | Result |
|---|---|---|
| `hello` | none | Protocol, host name, and capabilities |
| `list_sources` | none | `sources[]` with ID, name, language, base URL, capabilities, enabled state |
| `load_extensions` | none | Sources loaded from the host source directory |
| `install_jar` | `path` | Sources provided by the installed JAR |
| `remove_extension` | `sourceId` | `{ "removed": true }` |
| `enable_source` | `sourceId` | Source ID and `enabled: true` |
| `disable_source` | `sourceId` | Source ID and `enabled: false` |
| `popular` | `sourceId`, optional `page` | `kind: manga_page` |
| `latest` | `sourceId`, optional `page` | `kind: manga_page` |
| `search` | `sourceId`, `query`, optional `page` | `kind: manga_page` |
| `details` | `sourceId`, `manga` | `kind: manga_details` |
| `chapters` | `sourceId`, `manga` | `kind: chapter_list` |
| `pages` | `sourceId`, `chapter` | `kind: page_list` |
| `cancel` | `targetRequestId` | Whether the target request was cancelled |

## Data models

`Manga` carries `id`, `title`, nullable `coverUrl`, `description`, `sourceId`, nullable `author` and `artist`, `genres[]`, nullable `status`, and nullable `cachedChapterCount`. `Chapter` carries `id`, `mangaId`, `title`, nullable numeric `number`, `sourceId`, nullable `volume`, nullable `scanlator`, and nullable `uploadDate`. `MangaPage` carries ordered `index`, `imageUrl`, nullable `referer`, arbitrary `headers`, `isChapterEnd`, and nullable `chapterLabel`.

## Errors and lifecycle

The host uses these stable error codes: `INVALID_JSON`, `UNKNOWN_OPERATION`, `SOURCE_NOT_FOUND`, `SOURCE_DISABLED`, `EXTENSION_INSTALL_FAILED`, `SOURCE_BLOCKED`, `SOURCE_ERROR`, `TIMEOUT`, `CANCELLED`, and `HOST_ERROR`. The C++ supervisor should restart the host after an unexpected process exit, but it should not automatically retry a non-idempotent extension installation. In-flight requests are failed with a host-restart error when the pipe disconnects.

The v1 implementation dispatches each request in a supervised coroutine, applies a default source-operation timeout, and handles cancellation by request ID. Future versions may add event frames with an `event` field while retaining the same envelope and request correlation rules.
