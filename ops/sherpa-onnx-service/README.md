# sherpa-onnx-service

Lightweight trial deployment assets for Tencent Cloud.

Design:
- FastAPI entrypoint with `/healthz`, `/asr`
- `ASR` runs in a short-lived worker process so the model does not stay resident in memory
- Default models:
  - `sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16`

Default port:
- `18100`

Expected server layout:
- `/opt/sherpa-onnx-service/app`
- `/opt/sherpa-onnx-service/models`
- `/opt/sherpa-onnx-service/tmp`
- `/opt/sherpa-onnx-service/.venv`

Expected model layout:

- `/opt/sherpa-onnx-service/models/sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16`
  - `encoder.int8.onnx`
  - `decoder.int8.onnx`
  - `tokens.txt`

Switch notes:

- `ASR` now uses FireRed large zh/en via `OfflineRecognizer.from_fire_red_asr`
- `TTS` is no longer served by this sherpa service in the current project architecture
