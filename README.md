# Axvyn

Axvyn is a native Android utility app for offline file tools. The foundation,
**Word to PDF**, **PowerPoint to PDF**, **Image Compressor**, **PDF to Images**, **WEBP / AVIF to PNG**
and **Audio to MP3** are implemented.

## Principles

- Native Kotlin + Jetpack Compose (Material 3). No WebView, Flutter or React Native.
- Everything processes on the phone. No backend, accounts, uploads, analytics or ads.
- `INTERNET` permission is deliberately absent; conversions work in airplane mode.
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
│   └── preferences/          Device-local theme preference
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
    ├── imageconverter/
    ├── pdftoimage/
    └── audiotomp3/
```

Each feature follows Screen → ViewModel → State. Engine and repository classes are
added with the actual conversions, so the UI never owns processing code.

## Tools (planned)

| Tool | Input | Output / notes |
| --- | --- | --- |
| Word to PDF | `.docx`, max 10 MB | Custom offline renderer; not Word-perfect; basic paragraphs/headings/tables/images/RTL |
| PowerPoint to PDF | `.pptx`, max 10 MB | Best-effort offline renderer; legacy `.ppt` not supported yet; not PowerPoint-perfect |
| Image Compressor | JPEG, PNG, WebP | Quality, optional resize, before/after size |
| WEBP / AVIF to PNG | WebP, AVIF | PNG output, preserves dimensions/transparency, clear unsupported-AVIF error |
| PDF to Images | PDF | JPG/PNG, page selection, 150/300 DPI, one page at a time |
| Audio to MP3 | M4A, WAV, FLAC | 128/192/256/320 kbps MP3 via Android MediaCodec decode + vendored LAME (LGPL v2) |

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

Stage 5 native build requires Android NDK `27.2.12479018` and CMake `3.22.1` (installed via SDK Manager). Native ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.

## Third-party notices

Audio to MP3 uses vendored [LAME 3.100](https://lame.sourceforge.io/) for MP3 encoding only (LGPL v2). See [`docs/LEGAL-LAME.md`](docs/LEGAL-LAME.md) for provenance, license, and relinking details. No mpglib decoding is compiled; no FFmpeg is bundled.

## Roadmap

1. ✅ Foundation — project, theme, navigation, home and placeholders.
2. ✅ Image Compressor — real JPEG/PNG/WebP engine with quality, resize and target size.
3. ✅ PDF to Images — PdfRenderer, JPG/PNG, page selection, 150/300 DPI, one page at a time.
4. ✅ WEBP / AVIF to PNG — ImageDecoder/BitmapFactory decode, PNG output, dimensions/transparency preserved, clear unsupported-AVIF error.
5. ✅ Audio to MP3 — MediaExtractor/MediaCodec decode + vendored LAME 3.100 (LGPL v2) encode, 128/192/256/320 kbps, no FFmpeg.
6. ✅ Word to PDF — custom offline DOCX parser + PdfDocument renderer (not Word-perfect), basic paragraphs/headings/tables/images/RTL, 10 MB limit.
7. ✅ PowerPoint to PDF — best-effort offline PPTX parser + PdfDocument renderer (not PowerPoint-perfect); legacy `.ppt` not supported yet; 10 MB limit.
8. Final testing: airplane mode, interruptions, large files, low storage, ABI coverage.
