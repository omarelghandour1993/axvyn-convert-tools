# sherpa-onnx (prebuilt Android AAR)

Axvyn uses [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) as the offline neural TTS runtime for Piper VITS voice packs. This document records the audited binary used for integration.

## Artifact

- **File**: `app/libs/sherpa-onnx-1.12.20.aar`
- **Version**: `v1.12.20`
- **Source release**: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.20
- **Asset**: `sherpa-onnx-1.12.20.aar`
- **SHA-256**: `E3905B1258B577D8D374DB798618602CEAAFF5805CBF9B356AB25267CD98144D`
- **Size**: 38,013,991 bytes

## License

sherpa-onnx is licensed under the **Apache License 2.0**. See upstream LICENSE:
https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE

The AAR bundles ONNX Runtime native libraries (`libonnxruntime.so`) and sherpa-onnx JNI/C/C++ libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Review upstream third-party notices for bundled components before release.

## Integration notes

- Added as a local audited AAR dependency: `implementation(files("libs/sherpa-onnx-1.12.20.aar"))`.
- v1.12.20 is used (instead of v2.0.3) because it provides the simpler Piper VITS path with built-in espeak-ng `dataDir` support and no external phonemizer requirement.
- No `INTERNET` permission is added until the voice-pack download phase; downloads use narrow HTTPS access only.
- Voice model packs have **separate licenses** and must be verified per model before catalog inclusion/download.
