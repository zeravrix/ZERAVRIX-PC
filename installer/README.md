# ZERAVRIX Windows installer

`ZERAVRIX.iss` is the custom Inno Setup script for the native Windows release. It packages the complete Compose Desktop app image, including `ZERAVRIX.exe`, the bundled Java runtime, all application dependencies, the Windows Skiko graphics library, and the supplied logo.

The installer uses pre-install pages for the initial theme, language, and NSFW visibility choices. It writes those defaults to `%USERPROFILE%\\.zeravrix\\settings.properties`; all three settings can be changed later from the application’s Settings screen.

Compile on Windows with Inno Setup 7:

```bat
ISCC.exe installer\\ZERAVRIX.iss
```

Or run the project-level `build-windows.bat`, which builds the app image, verifies the required launcher/runtime files, and invokes `ISCC.exe` automatically when it is installed.

The final installer is a normal Windows `ZERAVRIX-Setup-0.8.0.exe`, not an Android package and not a launcher-only archive.
