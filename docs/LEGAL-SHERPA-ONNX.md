# sherpa-onnx (prebuilt Android AAR)

Axvyn uses [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) as the planned offline neural TTS runtime (Stage 6.2 spike). This document records the audited binary used for integration.

## Artifact

- **File**: `app/libs/sherpa-onnx-2.0.3.aar`
- **Version**: `v2.0.3`
- **Source release**: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.3
- **Asset**: `sherpa-onnx-2.0.3.aar`
- **SHA-256**: `4C766E2E74C9D33D595338C21FE3B305C73BA45D47E96A235675C33142A2EC50`
- **Size**: 52,411,426 bytes

## License

sherpa-onnx is licensed under the **Apache License 2.0**. See upstream LICENSE:
https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE

The AAR bundles ONNX Runtime native libraries (`libonnxruntime.so`) and sherpa-onnx JNI/C/C++ libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Review upstream third-party notices for bundled components before release.

## Integration notes

- Added as a local audited AAR dependency: `implementation(files("libs/sherpa-onnx-2.0.3.aar"))`.
- No `INTERNET` permission added for this spike. Model downloads/inference network policy is handled separately.
- Voice model packs have **separate licenses** and must be verified per model before catalog inclusion/download.
