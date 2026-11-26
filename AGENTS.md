# Axvyn — agent handoff

Read this before changing anything. It records where work stopped on **Tue 24 Nov 2026**
and exactly where to resume.

## What this is

Axvyn is an offline-first native Android utility app for six file tools. It is a separate
project from Swapply and lives at `C:\Users\Administrator\Documents\Axvyn`.

- Native Kotlin + Jetpack Compose + Material 3. No WebView, Flutter or React Native.
- MVVM per feature: Screen → ViewModel → State, with real Engine/Repository classes
  once a stage is implemented. UI must never own conversion code.
- Everything runs on the phone. No backend, accounts, uploads, analytics, ads or
  `INTERNET` permission for conversion stages.
- Files come in through `ACTION_OPEN_DOCUMENT` and go out through
  `ACTION_CREATE_DOCUMENT`. Scratch files stay in the app-private cache and are deleted
  after save, discard, replacement, cancellation or ViewModel teardown.
- One image/file at a time. No batch processing in the current stages.
- Light/dark theme follows the system by default; Settings can override and persists locally.

The tools, in the agreed order:

1. **Word to PDF — DONE** (DOCX, max 10 MB, custom renderer, not Word-perfect)
2. **Image Compressor — DONE**
3. **PDF to Images — DONE**
4. **WEBP / AVIF to PNG — DONE**
5. **Audio to MP3 — DONE**

Do not start a later engine until the current stage is approved.

## Identity and environment

| Item | Value |
| --- | --- |
| GitHub repo | `https://github.com/omarelghandour1993/axvyn-convert-tools` (public) |
| Local Git identity | `omarelghandour1993 <omarelghandour1993@users.noreply.github.com>` |
| Application ID | `com.axvyn.app` — treat as permanent |
| Gradle wrapper | 9.6.0 |
| AGP | 9.5.2, built-in Kotlin support |
| Kotlin / Compose compiler | 2.3.21 |
| Compose BOM | 2026.09.00 |
| Navigation | 2.10.2 |
| Lifecycle | 2.11.1 |
| WorkManager | 2.12.0 (reserved, not used yet) |
| Coroutines | 1.10.2 |
| Activity Compose | 1.13.0 |
| core-ktx | 1.19.1 |
| Compile/target SDK | 37 |
| minSdk | 26 |
| Java bytecode | 17 |
| JDK used locally | `C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot` |
| Test phone | `RK8X700ZAGA`, Samsung SM-A155F, Android 17, USB debugging on |

AGP 9 does **not** use `org.jetbrains.kotlin.android`; only the Compose compiler plugin is
applied. SDK platform 37 had to be installed manually because the installed `sdkmanager`
could not see it.

There is a local-only Gradle init script at
`C:\Users\Administrator\.gradle\init.d\axvyn-mirrors.gradle` that redirects Google Maven
to Aliyun because `dl.google.com` DNS was intermittent. **Do not commit it.**

## Commands

Build:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

Install on the connected phone:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell monkey -p com.axvyn.app -c android.intent.category.LAUNCHER 1
```

APK: `app\build\outputs\apk\debug\app-debug.apk`.

Never commit `local.properties`, build outputs, `*.jks`, signing properties or the mirror
init script.

## Current state

Stages 1–5 in the current tool set are complete, built, installed, tested on the real phone, committed and pushed. The legacy Text-to-Speech system was later removed from the codebase. Word to PDF has now been implemented with a custom offline DOCX→PDF renderer, built and installed on the test device; further real-device validation is still recommended.

- `ed2e7c2` — foundation: theme, navigation, Home, History, Settings, placeholders
- `c2f9a64` — Stage 2 Image Compressor
- `4b0fd4b` — agent handoff notes
- `b3f6c72` — Stage 3 PDF to Images
- `986d1fe` — Stage 4 WEBP / AVIF to PNG
- `d2897ce` — Stage 5 Audio to MP3

## Stage 2 details — Image Compressor

Implemented files:

```
features/imagecompressor/
├── ImageCompressorState.kt        State, format enum, result models, formatBytes()
├── ImageCompressionEngine.kt      BitmapFactory/ImageDecoder + Bitmap.compress()
├── ImageCompressorRepository.kt   SAF/cache coordination, FileProvider URI
├── ImageCompressorViewModel.kt    UI state, validation, progress, cancel, save
└── ImageCompressorScreen.kt       Compose UI, OpenDocument/CreateDocument launchers
```

Support files changed for Stage 2:

- `AndroidManifest.xml` — added `androidx.core.content.FileProvider` with authority
  `${applicationId}.fileprovider`; still **no `INTERNET` permission**
- `res/xml/file_paths.xml` — cache-path entry for the FileProvider
- `core/processing/ProcessingState.kt` — `Success` is now `data object Success`
- `app/build.gradle.kts` + `libs.versions.toml` — added `androidx.core:core-ktx:1.19.1`

Behaviour:

- Input: JPEG, PNG, WebP via `ACTION_OPEN_DOCUMENT`
- Output: JPEG, PNG or WebP
- Quality slider: 1–100
- Optional target size in KB: binary search over quality, max 8 passes, progressively
  reducing quality until the output fits. It is a **maximum**, not an exact target.
- PNG is lossless: target size is rejected in the ViewModel with a clear message.
- Optional resize by width/height; if only one is supplied the aspect ratio is preserved.
- Progress and cancellation use coroutines; `currentCoroutineContext().ensureActive()`
  is called inside the encode loop.
- Output is written to app-private cache, exposed through FileProvider for preview and
  copied to the SAF destination on “Save a copy”. Temp file is deleted after save/discard.
- No fake progress and no online processing.

### Stage 2 phone verification (23 Nov 2026)

Verified through on-screen UI hierarchy and filesystem checks:

- Opened Home → Image Compressor.
- Selected `axvyn-noise.jpg`: 880.4 KB, 1000 × 800 JPEG.
- Default 80% quality → 468.1 KB, 47% smaller, 1000 × 800.
- Saved to `/sdcard/Download/axvyn-1795442132681.jpg`; file existed with 468,065 bytes.
- Result card cleared after save, confirming cache cleanup.
- Target size 200 KB → 31.5 KB, 96% smaller.
- Resize applied and the “target size could not be reached at minimum quality” warning
  appeared when the requested size was impossible.
- `logcat` showed no Axvyn fatal exception.

Two test files remain in phone Downloads for inspection:

- `axvyn-noise.jpg` — source
- `axvyn-1795442132681.jpg` — compressed output

No code change came out of testing.

## Stage 3 details — PDF to Images

Implemented files:

```
features/pdftoimage/
├── PdfToImageState.kt          State, format enum, source/result models, formatPdfBytes()
├── PdfRenderingEngine.kt       PdfRenderer-based one-page-at-a-time renderer
├── PdfToImageRepository.kt     SAF/cache coordination, FileProvider URI
├── PdfToImageViewModel.kt      UI state, validation, progress, cancel, save
└── PdfToImageScreen.kt         Compose UI, OpenDocument/CreateDocument launchers
```

Behaviour:

- Input: PDF via `ACTION_OPEN_DOCUMENT` (`application/pdf`).
- Output: JPG or PNG, selected by the user.
- Resolution: 150 DPI or 300 DPI.
- Page selection: single page number, validated against page count.
- Rendering uses Android's built-in `android.graphics.pdf.PdfRenderer`; one page is opened,
  rendered to a bitmap, encoded, and the bitmap/page are closed before moving on.
- Output is written to app-private cache, exposed through FileProvider for preview, and
  copied to the SAF destination on "Save a copy". Temp file is deleted after save/discard.
- Progress and cancellation use coroutines; `currentCoroutineContext().ensureActive()` is
  checked during rendering. No fake progress, no online processing.

### Stage 3 phone verification (24 Nov 2026)

Verified on `RK8X700ZAGA` (SM-A155F, Android 17) with a pushed 2-page test PDF
(`/sdcard/Download/axvyn-test.pdf`, 880 B):

- Opened Home → PDF to Images.
- Selected PDF: detected `axvyn-test.pdf`, 880 B, 2 pages.
- Page 1, JPG, 150 DPI → 1275 × 1649, 22.8 KB, `image/jpeg`.
- Saved to `/sdcard/Download/axvyn-page-1-1795522634784.jpg`; file existed with 22,763 bytes.
- Result card cleared after save, confirming temp cleanup.
- Page 2, PNG, 300 DPI → 2550 × 3299, 66.2 KB, `image/png`.
- Saved to `/sdcard/Download/axvyn-page-2-1795522783267.png`; file existed with 66,163 bytes.
- No Axvyn fatal exception observed in `logcat`.

Test artifacts left on phone Downloads for inspection:

- `axvyn-test.pdf` — source
- `axvyn-page-1-1795522634784.jpg` — page 1 JPG output
- `axvyn-page-2-1795522783267.png` — page 2 PNG output

## Stage 4 details — WEBP / AVIF to PNG

Implemented files:

```
features/imageconverter/
├── ImageConverterState.kt        State, source/result models, formatImageBytes()
├── ImageConversionEngine.kt      ImageDecoder/BitmapFactory decode + PNG encode
├── ImageConverterRepository.kt   SAF/cache coordination, FileProvider URI
├── ImageConverterViewModel.kt    UI state, validation, progress, cancel, save
└── ImageConverterScreen.kt       Compose UI, OpenDocument/CreateDocument launchers
```

Behaviour:

- Input: WebP and AVIF via `ACTION_OPEN_DOCUMENT` (`image/webp`, `image/avif`).
- Output: PNG via `ACTION_CREATE_DOCUMENT` (`image/png`).
- Preserves dimensions and transparency when the source can be decoded on-device.
- Decodes one image at a time using `ImageDecoder` (API 28+) with software allocation and
  `BitmapFactory` bounds fallback; encodes PNG with `Bitmap.CompressFormat.PNG`.
- AVIF decode is device/codec dependent. If decode fails for an AVIF, the engine throws a
  clear `IllegalArgumentException` explaining that the AVIF can't be decoded on this device;
  the UI shows it as conversion failure/message rather than crashing.
- Output is written to app-private cache, exposed through FileProvider for preview, and
  copied to the SAF destination on "Save a copy". Temp file is deleted after save/discard.
- Progress and cancellation use coroutines; no fake progress, no online processing.

### Stage 4 phone verification (24 Nov 2026)

Verified on `RK8X700ZAGA` (SM-A155F, Android 17) with test images pushed to Downloads:

- **WebP** (`axvyn-test.webp`, 44 B, 96×64, WebP): converted to PNG 257 B, 96×64, `image/png`.
  Saved to `/sdcard/Download/axvyn-converted-1795523825499.png` (257 bytes). Result cleared
  after save, confirming temp cleanup.
- **AVIF** (`axvyn-test.avif`, 323 B, 64×64, AVIF): decoded and converted on this device to
  PNG 180 B, 64×64, `image/png`. Dimensions preserved.
- No Axvyn fatal exception observed in `logcat`.

Test artifacts left on phone Downloads for inspection:

- `axvyn-test.webp` — WebP source
- `axvyn-test.avif` — AVIF source
- `axvyn-converted-1795523825499.png` — WebP-to-PNG output

## Stage 5 details — Audio to MP3

Implemented files:

```
features/audiotomp3/
├── AudioToMp3State.kt          State, bitrate enum, source/result models
├── AudioConversionEngine.kt    MediaExtractor/MediaCodec decode + LAME encode
├── AudioToMp3Repository.kt     SAF/cache coordination, FileProvider URI
├── AudioToMp3ViewModel.kt      UI state, validation, progress, cancel, save
├── AudioToMp3Screen.kt         Compose UI, bitrate selector, SAF launchers
└── LameMp3Encoder.kt           Kotlin JNI wrapper for libmp3lame

app/src/main/cpp/
├── CMakeLists.txt              Builds libmp3lame (shared) + libaxvyn_lame (JNI)
├── config.h                    Minimal Android config for LAME
├── lame_jni.c                  JNI bridge (encode/flush/close only)
└── lame-3.100/                 Vendored LAME 3.100 source (LGPL v2)
```

Behaviour:

- Input: M4A, WAV, FLAC via `ACTION_OPEN_DOCUMENT` (`audio/*` picker filter).
- Output: MP3 via `ACTION_CREATE_DOCUMENT` (`audio/mpeg`).
- Bitrate selectable: 128 / 192 / 256 / 320 kbps (CBR, LAME VBR off, quality 2).
- Decode: Android `MediaExtractor` + `MediaCodec` to PCM16 (requests `KEY_PCM_ENCODING` PCM 16BIT; converts float PCM if device returns float).
- Encode: vendored LAME 3.100 via JNI. Only encoding APIs used; no mpglib decoding compiled (`HAVE_MPGLIB` not defined).
- Chunk-based processing with 10ms codec dequeue timeouts, progress from presentation time / duration, coroutine cancellation checks.
- Temp output in app-private cache; deleted on save/discard/cancel/failure/teardown.
- No `INTERNET` permission; no FFmpeg.

### LAME audit / compliance

- **Provenance**: LAME 3.100 source from https://lame.sourceforge.io/ (SourceForge), vendored at `app/src/main/cpp/lame-3.100/`.
- **License**: LGPL v2 (`app/src/main/cpp/lame-3.100/COPYING`).
- **Compliance**: `libmp3lame.so` built as a separate shared library; `libaxvyn_lame.so` links dynamically. Relinking/replacement procedure documented in `docs/LEGAL-LAME.md`.
- **ABIs**: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- **Build deps**: Android NDK `27.2.12479018`, CMake `3.22.1`.

### Stage 5 phone verification (24 Nov 2026)

Verified on `RK8X700ZAGA` (SM-A155F, Android 17) with ffmpeg-generated 2s test tones pushed to Downloads:

- **WAV** (`axvyn-test.wav`, 352.9 KB, 0:02): 320 kbps → **MP3 82.5 KB, 0:02, audio/mpeg**. Saved to `/sdcard/Download/axvyn-audio-1795525846791.mp3` (82,546 bytes). Result cleared after save.
- **M4A/AAC** (`axvyn-test.m4a`, 33.5 KB, 0:02): 128 kbps → **MP3 33.4 KB, 0:02, audio/mpeg**. Saved to `/sdcard/Download/axvyn-audio-1795526176896.mp3` (33,435 bytes). (Initial test exposed a configure bug — decoder was configured with a stripped format missing AAC CSD; fixed by configuring with original inputFormat and requesting PCM16.)
- **FLAC** (`axvyn-test.flac`, 39.3 KB, 0:02): 320 kbps → **MP3 82.5 KB, 0:02, audio/mpeg**.
- No Axvyn fatal exception observed in `logcat`.

## Layout

```
app/src/main/java/com/axvyn/app/
├── AxvynApplication.kt
├── MainActivity.kt
├── core/
│   ├── files/              SAF metadata and output helpers
│   ├── processing/         ProcessingState
│   ├── storage/            LocalStorageManager
│   └── workers/            Reserved WorkManager conversion worker
├── data/
│   └── preferences/        Device-local theme preference
├── navigation/             Routes and NavHost
├── ui/
│   ├── components/         Scaffolds, tool cards, shared sections
│   ├── history/            History placeholder
│   ├── home/               Dashboard, search, catalog
│   ├── settings/           Theme choice and About
│   └── theme/              Colour tokens and Material 3 themes
└── features/
    ├── imagecompressor/    REAL ENGINE — Stage 2
    ├── pdftoimage/         REAL ENGINE — Stage 3
    ├── imageconverter/     REAL ENGINE — Stage 4
    ├── audiotomp3/         REAL ENGINE — Stage 5
    └── wordtopdf/          REAL ENGINE — Word to PDF
```

## Where to go next

Word to PDF is implemented (custom offline DOCX→PDF renderer, not Word-perfect; built and installed on `RK8X700ZAGA`). It still needs real-device validation.

1. Test Word to PDF on the connected phone with real `.docx` files: simple text, headings/tables/images, Arabic/RTL, 10 MB boundary, invalid/oversized files, cancel mid-conversion, save/reopen, airplane mode.
2. Fix any renderer/layout issues found on device. Keep everything offline and document compromises.
3. Continue final testing from README: airplane mode, interruptions, large files, low storage, ABI coverage.

Useful reminder: the user explicitly wants the app installed on the connected phone after each stage. Check `adb devices` before assuming the cable is still attached.
