# Example source extension

This is a **template**, not a real manga website connector. It demonstrates the desktop extension boundary.

Build it in your own extension project with `source-api` as a dependency, then package the provider class into a JAR with:

`META-INF/services/com.zeravrix.zeravrix.source.SourceProvider`

The host application loads that JAR from `%USERPROFILE%\\.zeravrix\\sources`.
