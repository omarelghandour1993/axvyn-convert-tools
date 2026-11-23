# Axvyn — agent handoff

Read this before changing anything. It records where work stopped on **Mon 23 Nov 2026**
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

The six tools, in the agreed order:

1. Word to PDF — DOCX, max 10 MB, custom renderer, not Word-perfect
2. **Image Compressor — DONE**
3. **PDF to Images — NEXT**
4. WEBP / AVIF to PNG
5. Audio to MP3 — only after licence/codec audit
6. Text to Voice — offline voice packs, 14-language verification

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

Stages 1 and 2 are complete, built, installed, tested on the real phone, committed and pushed.
Working tree was clean before this handoff file was added.

- `ed2e7c2` — foundation: theme, navigation, Home, History, Settings, six placeholders
- `c2f9a64` — Stage 2 Image Compressor

After committing this handoff, the repo must be pushed to `origin/main`.

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
│   ├── preferences/        Device-local theme preference
│   └── voicepacks/         Voice-pack contract and empty catalog
├── navigation/             Routes and NavHost
├── ui/
│   ├── components/         Scaffolds, tool cards, shared sections
│   ├── history/            History placeholder
│   ├── home/               Dashboard, search, catalog
│   ├── settings/           Theme choice and About
│   └── theme/              Colour tokens and Material 3 themes
└── features/
    ├── imagecompressor/    REAL ENGINE — Stage 2
    ├── wordtopdf/          placeholder
    ├── texttovoice/        placeholder
    ├── imageconverter/     placeholder
    ├── pdftoimage/         placeholder — Stage 3 target
    └── audiotomp3/         placeholder
```

## Where to begin tomorrow

**Start Stage 3: PDF to Images**, replacing
`features/pdftoimage/PdfToImageScreen.kt`, `PdfToImageViewModel.kt` and `PdfToImageState.kt`.

Requirements for Stage 3:

1. Keep the offline-first rules: no `INTERNET`, no network SDK, no fake progress.
2. Use Android's built-in `android.graphics.pdf.PdfRenderer` (API 21+, already available at
   minSdk 26). Do not add a PDF library unless there is a concrete reason.
3. Reuse `ACTION_OPEN_DOCUMENT` for the PDF and `ACTION_CREATE_DOCUMENT` for each rendered
   image. Keep output in app-private cache until saved.
4. Let the member choose JPG or PNG, page number and resolution/DPI. Decode one page at a
   time; never render all pages into memory.
5. Add `PdfRenderingEngine` and `PdfToImageRepository` under `features/pdftoimage/`.
   ViewModel owns validation, progress, cancellation and temp-file lifecycle.
6. Show page count, selected page and output size before/after. Support cancellation.
7. Build, install on the connected phone, test with a real multi-page PDF, then commit and
   push. Update README roadmap and this AGENTS.md.

Useful reminder: the user explicitly wants the app installed on the connected phone after
each stage. Check `adb devices` before assuming the cable is still attached.

## Git state at handoff

When this file was created, `main` was clean at `c2f9a64`. This AGENTS.md must be the only
new change in the next commit:

```powershell
git add AGENTS.md
git commit -m "Add Axvyn agent handoff notes"
git push origin main
git status --short
```

After that, begin Stage 3 from a clean tree.
