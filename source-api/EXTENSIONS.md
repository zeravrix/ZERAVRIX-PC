# Desktop source extensions

A ZERAVRIX source extension is a trusted JVM JAR that describes how to discover and read manga, manhua, or manhwa from one source. The design is inspired by Mihon’s separation between the host application and source implementations, but the contract is intentionally desktop-specific so it can run inside a native Windows JVM application.

The host owns the library, database files, download concurrency, page cache, reading progress, reader UI, source installation directory, and Windows packaging. A source owns only source-facing retrieval logic and metadata mapping.

## Lifecycle

1. The user manually selects a trusted source JAR in the Windows Sources screen.
2. The host copies it into `%USERPROFILE%\\.zeravrix\\sources`.
3. `ServiceLoader` discovers `SourceProvider` implementations from the JAR.
4. The provider creates a `Source` with a unique ID, name, language, base URL, and capabilities.
5. The user searches the source and opens manga details.
6. The source returns ordered chapters and ordered image pages.
7. The host downloads and caches pages, then hands them to the native Windows reader.

## Contract

```kotlin
interface SourceProvider {
    fun create(): Source
}

interface Source {
    val id: Long
    val name: String
    val language: String
    val baseUrl: String

    suspend fun search(page: Int, query: String): MangaPageResult
    suspend fun details(manga: Manga): Manga
    suspend fun chapters(manga: Manga): List<Chapter>
    suspend fun pages(chapter: Chapter): List<MangaPage>
}
```

Register the provider at:

```text
META-INF/services/com.zeravrix.ZERAVRIX.source.SourceProvider
```

The source API is **not binary-compatible with Android Mihon APK extensions**. An extension must be compiled for the desktop JVM against this project’s `source-api` and `core` modules. This avoids requiring Android SDK classes or an emulator while preserving the source-oriented architecture requested for the Windows reader.

> A JVM extension JAR executes with the same user privileges as the application. Install only source JARs you trust.
