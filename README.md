# Axvyn

Axvyn is a native Android utility app for six offline file tools. The foundation
and **Stage 2: Image Compressor** are complete: the UI navigates between all six
tools, and the compressor has a real on-device engine. The other five tool screens
remain placeholders.

## Principles

- Native Kotlin + Jetpack Compose (Material 3). No WebView, Flutter or React Native.
- Everything processes on the phone. No backend, accounts, uploads, analytics or ads.
- `INTERNET` permission is deliberately absent. It will be added only for voice-pack
  downloads; conversions must work in airplane mode.
- Files are selected with `ACTION_OPEN_DOCUMENT` and saved with `ACTION_CREATE_DOCUMENT`.
  Scratch files live in the app-private cache.
- Light and dark themes follow the system by default; the user can override in Settings.

## Architecture

```
app/src/main/java/com/axvyn/app/
├── AxvynApplication.kt       Application entry point
├── MainActivity.kt           ComponentActivity hosting Compose
├── core/
│   ├── files/                SAF metadata and output helpers
│   ├── processing/           Shared processing state/result contracts
│   ├── storage/              App-private cache manager
│   └── workers/              Reserved WorkManager conversion worker
├── data/
│   ├── preferences/          Device-local theme preference
│   └── voicepacks/           Voice-pack metadata contract and empty catalog
├── navigation/               Routes and NavHost
├── ui/
│   ├── components/           Scaffolds, tool cards, shared screen sections
│   ├── history/              History placeholder screen
│   ├── home/                 Dashboard, search and tool catalog
│   ├── settings/             Theme choice and about screen
│   └── theme/                Colour tokens, typography and Material 3 themes
└── features/
    ├── wordtopdf/
    ├── imagecompressor/
    ├── texttovoice/
    ├── imageconverter/
    ├── pdftoimage/
    └── audiotomp3/
```

Each feature follows Screen → ViewModel → State. Engine and repository classes are
added with the actual conversions, so the UI never owns processing code.

## Tools (planned)

| Tool | Input | Output / notes |
| --- | --- | --- |
| Word to PDF | `.docx`, max 10 MB | Custom renderer; not Word-perfect |
| Image Compressor | JPEG, PNG, WebP | Quality, optional resize, before/after size |
| Text to Voice | Up to 3,000 characters | Downloadable offline voice packs, MP3 export |
| WEBP / AVIF to PNG | WebP, AVIF | Preserve dimensions and transparency |
| PDF to Images | PDF | JPG/PNG, page selection, resolution choice |
| Audio to MP3 | M4A, WAV, FLAC | 320 kbps via a vetted local FFmpeg build |

## Dependencies

Managed in `gradle/libs.versions.toml`:

| Dependency | Version |
| --- | --- |
| Android Gradle Plugin | 9.5.2 (built-in Kotlin support) |
| Kotlin / Compose compiler | 2.3.21 |
| Compose BOM | 2026.09.00 |
| Activity Compose | 1.13.0 |
| Navigation Compose | 2.10.2 |
| Lifecycle | 2.11.1 |
| WorkManager | 2.12.0 |
| Kotlin coroutines | 1.10.2 |
| JUnit | 4.13.2 |

Gradle wrapper: 9.6.0. Compile/target SDK: 37; minimum SDK: 26. Java 17 bytecode.
AGP 9 does not need the `org.jetbrains.kotlin.android` plugin; only the Compose
compiler plugin is applied.

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

1. ✅ Foundation — project, theme, navigation, home and placeholders.
2. ✅ Image Compressor — real JPEG/PNG/WebP engine with quality, resize and target size.
3. PDF to JPG / PNG.
4. WEBP / AVIF to PNG (with AVIF fallback on older devices).
5. Audio to MP3 (license and codec audit before adding FFmpeg).
6. Word to PDF (DOCX parser + PDF renderer, including Arabic/RTL).
7. Text to Voice (engine selection, 14-language verification, offline packs).
8. Final testing: airplane mode, interruptions, large files, low storage, ABI coverage.

Do not start the conversion engines until the foundation is reviewed and approved.
