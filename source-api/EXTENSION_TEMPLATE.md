# ZERAVRIX source extension template

Create a separate JVM project that depends on `source-api`. Implement `SourceProvider`, return your `Source` implementation, and register the provider under:

`META-INF/services/com.zeravrix.zeravrix.source.SourceProvider`

For HTTP/HTML sources, extend `HttpSource`. Keep parsing, URLs, headers and source-specific logic inside the extension. Do not put Compose/UI, filesystem library state, or download-manager code in an extension.

**Only install trusted JARs:** extensions run as JVM code inside the application process.
