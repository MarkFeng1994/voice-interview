from __future__ import annotations

import argparse
import json
import sys
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Path to input wav file")
    parser.add_argument("--models-dir", required=True, help="Directory containing sherpa-onnx models")
    parser.add_argument("--threads", type=int, default=2)
    return parser.parse_args()


def read_wave(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path), "rb") as wav:
        if wav.getnchannels() != 1:
            raise ValueError("Only mono wav is supported")
        if wav.getsampwidth() != 2:
            raise ValueError("Only 16-bit PCM wav is supported")
        frames = wav.readframes(wav.getnframes())
        samples = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
        return samples, wav.getframerate()


def main() -> int:
    args = parse_args()
    model_dir = Path(args.models_dir) / "sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16"
    encoder = model_dir / "encoder.int8.onnx"
    decoder = model_dir / "decoder.int8.onnx"
    tokens = model_dir / "tokens.txt"
    if not encoder.is_file() or not decoder.is_file() or not tokens.is_file():
        raise FileNotFoundError(f"ASR model is incomplete under {model_dir}")

    samples, sample_rate = read_wave(Path(args.input))
    recognizer = sherpa_onnx.OfflineRecognizer.from_fire_red_asr(
        encoder=str(encoder),
        decoder=str(decoder),
        tokens=str(tokens),
        num_threads=args.threads,
        decoding_method="greedy_search",
        debug=False,
        provider="cpu",
    )
    stream = recognizer.create_stream()
    stream.accept_waveform(sample_rate, samples)
    recognizer.decode_streams([stream])
    result = {
        "text": stream.result.text,
        "sample_rate": sample_rate,
        "num_samples": int(len(samples)),
    }
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        raise
