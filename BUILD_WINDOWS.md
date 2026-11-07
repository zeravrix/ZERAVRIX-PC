# Building ZERAVRIX for Windows

ZERAVRIX is a native Kotlin/JVM and Compose Desktop application for Windows. It is not an Android build, emulator package, or APK. The final Windows distribution must keep the launcher, bundled Java runtime, and application libraries together.

## One-command build

Install **JDK 21** and **Inno Setup 7** on Windows, set `JAVA_HOME`, and run this from the project root:

```bat
build-windows.bat
```

The script builds the Windows-specific app image, verifies that `ZERAVRIX.exe`, `runtime\\bin\\javaw.exe`, and `app\\skiko-windows-x64.dll` exist, and then compiles the customizable installer when Inno Setup’s `ISCC.exe` is installed.

The installer output is:

```text
desktop-app\\build\\installer\\ZERAVRIX-Setup-0.8.0.exe
```

The installer’s pre-install pages let the user select the initial theme, language, and NSFW visibility. The installed application can change these values later in Settings.

## Fixed portable layout

The portable distribution is a directory containing:

```text
ZERAVRIX\\ZERAVRIX.exe
ZERAVRIX\\runtime\\       bundled Windows Java 21 runtime
ZERAVRIX\\app\\           application and Compose dependencies
ZERAVRIX\\app\\skiko-windows-x64.dll
```

The previous broken delivery occurred when only `ZERAVRIX.exe` was separated from its adjacent runtime and `app` directory. Do not launch or distribute the executable alone. Use the supplied Inno installer or preserve the complete portable folder.

## Development

```bat
gradlew.bat :desktop-app:run
gradlew.bat clean compileKotlin
```

## Extension stores

The application starts with the three configured default repository indexes and also allows custom HTTPS index URLs in Settings → Extension stores. JSON metadata indexes are fetched safely; protobuf-style indexes are identified separately. Downloaded source packages are never executed automatically, and only trusted desktop JVM extensions compiled against this project’s source API should be installed.
