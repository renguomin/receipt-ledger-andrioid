#!/usr/bin/env python3
"""Compare GLiNER and NuExtract-tiny on saved OCR dumps. Project-local only."""

from __future__ import annotations

import json
import re
from pathlib import Path

OCR_DIR = Path(__file__).resolve().parents[1] / "receipt-engine/src/test/resources/ocr"
OUT_PATH = Path(__file__).resolve().parents[1] / "scripts/ocr_extract_bakeoff.json"

PHONE_DUMPS = {
    "ocr-20260824-185000.txt": "Shell",
    "ocr-20260824-185108.txt": "Best Buy",
    "ocr-20260824-185253.txt": "Superstore",
    "ocr-20260824-185339.txt": "T&T",
}

TEMPLATE = {
    "merchant": "",
    "date": "",
    "currency": "",
    "subtotal": "",
    "total": "",
    "gst": "",
    "pst": "",
    "hst": "",
    "paymentMethod": "",
    "gstHstNumber": "",
}

GLINER_LABELS = [
    "merchant",
    "store name",
    "date",
    "total",
    "subtotal",
    "GST amount",
    "PST amount",
    "HST amount",
    "payment method",
    "GST HST number",
]


def load_dumps() -> dict[str, str]:
    texts = {}
    for name in PHONE_DUMPS:
        path = OCR_DIR / name
        texts[name] = path.read_text(encoding="utf-8")
    return texts


def gliner_extract(text: str, model) -> dict:
    hits = model.predict_entities(text, GLINER_LABELS, threshold=0.4)
    grouped: dict[str, list[str]] = {}
    for hit in hits:
        grouped.setdefault(hit["label"], []).append(f"{hit['text']} ({hit['score']:.2f})")
    return grouped


def nuextract_extract(text: str, model, tokenizer) -> dict:
    import torch

    template = json.dumps(TEMPLATE, indent=2)
    prompt = f"<|input|>\n### Template:\n{template}\n### Text:\n{text}\n\n<|output|>"
    encoded = tokenizer(
        prompt,
        return_tensors="pt",
        truncation=True,
        max_length=4096,
    )
    with torch.no_grad():
        out = model.generate(**encoded, max_new_tokens=400, do_sample=False)
    decoded = tokenizer.decode(out[0], skip_special_tokens=True)
    payload = decoded.split("<|output|>")[-1].strip()
    payload = re.sub(r"^```(?:json)?\s*|\s*```$", "", payload).strip()
    try:
        return json.loads(payload)
    except json.JSONDecodeError:
        return {"raw": payload}


def main() -> None:
    texts = load_dumps()
    results: dict = {"dumps": PHONE_DUMPS, "gliner": {}, "nuextract": {}, "errors": []}

    try:
        from gliner import GLiNER

        print("Loading GLiNER small-v2.1…")
        gliner = GLiNER.from_pretrained("urchade/gliner_small-v2.1")
        for name, text in texts.items():
            print(f"GLiNER {PHONE_DUMPS[name]}")
            results["gliner"][name] = gliner_extract(text, gliner)
    except Exception as exc:  # noqa: BLE001
        results["errors"].append(f"GLiNER: {exc}")
        print(f"GLiNER failed: {exc}")

    try:
        from transformers import AutoModelForCausalLM, AutoTokenizer

        print("Loading NuExtract-tiny 1.5…")
        model_id = "numind/NuExtract-1.5-tiny"
        tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)
        model = AutoModelForCausalLM.from_pretrained(model_id, trust_remote_code=True)
        model.eval()
        for name, text in texts.items():
            print(f"NuExtract {PHONE_DUMPS[name]}")
            results["nuextract"][name] = nuextract_extract(text, model, tokenizer)
    except Exception as exc:  # noqa: BLE001
        results["errors"].append(f"NuExtract: {exc}")
        print(f"NuExtract failed: {exc}")

    OUT_PATH.write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"Wrote {OUT_PATH}")


if __name__ == "__main__":
    main()
