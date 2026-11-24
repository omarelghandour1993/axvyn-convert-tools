# LAME licensing and compliance (Axvyn Stage 5)

## Component

- **Library**: LAME (LAME Ain't an MP3 Encoder) 3.100
- **Used for**: MP3 encoding only (PCM → MP3). Axvyn does **not** use LAME/mpglib for decoding.
- **Vendored source**: `app/src/main/cpp/lame-3.100/`
- **Upstream provenance**: https://lame.sourceforge.io/ (SourceForge project `lame`, release `3.100`, `lame-3.100.tar.gz`)

## License

LAME 3.100 is distributed under the **GNU Library General Public License (LGPL) version 2** (see `app/src/main/cpp/lame-3.100/COPYING`).

LAME's license notice also states that the decoding functions in LAME use an old mpglib engine under GPL restrictions. Axvyn does **not** compile or link `mpglib/` and does **not** define `HAVE_MPGLIB`; only `libmp3lame` encoding APIs are used. Audio decoding is performed by Android platform `MediaExtractor`/`MediaCodec`.

## How Axvyn uses LAME

- `app/src/main/cpp/CMakeLists.txt` builds `libmp3lame` as a **separate shared library** (`libmp3lame.so`).
- `libaxvyn_lame.so` (Axvyn JNI wrapper) dynamically links to `libmp3lame.so`.
- Kotlin/JNI wrapper: `com.axvyn.app.features.audiotomp3.LameMp3Encoder`.
- No FFmpeg is included. No `INTERNET` permission is required.

## Relinking / replacement procedure (LGPL)

To replace or modify the LAME library used by Axvyn:

1. Obtain/modify LAME source (the vendored copy is in `app/src/main/cpp/lame-3.100/`, or use upstream LAME 3.100 source).
2. Keep LGPL v2 notices and provide your modified LAME source under LGPL v2 as required.
3. Rebuild the native libraries with Android NDK + CMake:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot'
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   .\gradlew.bat :app:externalNativeBuildDebug
   ```

   Or build the full APK with `.\gradlew.bat assembleDebug`.
4. The resulting `libmp3lame.so` is packaged per ABI under `app/build/intermediates/cmake/` and inside the APK `lib/<abi>/libmp3lame.so`. Replacing that shared library with a compatible rebuilt version satisfies the LGPL relinking requirement for this dynamic-linking arrangement.
5. If you distribute a modified Axvyn APK, also provide corresponding LAME source/modifications and this notice as required by LGPL v2.

## ABI coverage

Gradle is configured to build native libs for: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.

## Source files used

The CMake build compiles the standard `libmp3lame` sources listed in `app/src/main/cpp/CMakeLists.txt` from the vendored LAME 3.100 tree. No mpglib decoder sources are compiled.