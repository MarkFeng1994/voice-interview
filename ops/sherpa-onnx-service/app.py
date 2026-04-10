from __future__ import annotations

import json
import os
import subprocess
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile


APP_DIR = Path(__file__).resolve().parent
SERVICE_PYTHON = os.environ.get("SERVICE_PYTHON", "python3")
MODELS_DIR = Path(os.environ.get("MODELS_DIR", APP_DIR / "models")).resolve()
TMP_DIR = Path(os.environ.get("TMP_DIR", APP_DIR / "tmp")).resolve()
ASR_WORKER = APP_DIR / "worker_asr.py"
TMP_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="sherpa-onnx-asr", version="0.1.0")


def run_worker(args: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        [SERVICE_PYTHON, *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or "worker failed"
        raise HTTPException(status_code=500, detail=detail)
    return completed


@app.get("/healthz")
def healthz() -> dict[str, object]:
    return {
        "ok": True,
        "models_dir": str(MODELS_DIR),
        "asr_model_ready": (
            (MODELS_DIR / "sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16" / "encoder.int8.onnx").is_file()
            and (MODELS_DIR / "sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16" / "decoder.int8.onnx").is_file()
            and (MODELS_DIR / "sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16" / "tokens.txt").is_file()
        ),
    }


@app.post("/asr")
async def asr(file: UploadFile = File(...)) -> dict[str, object]:
    suffix = Path(file.filename or "audio.wav").suffix or ".wav"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix, dir=TMP_DIR) as tmp_in:
        tmp_path = Path(tmp_in.name)
        tmp_in.write(await file.read())

    try:
        completed = run_worker(
            [
                str(ASR_WORKER),
                "--input",
                str(tmp_path),
                "--models-dir",
                str(MODELS_DIR),
                "--threads",
                "2",
            ],
            timeout=120,
        )
        return json.loads(completed.stdout)
    finally:
        tmp_path.unlink(missing_ok=True)
