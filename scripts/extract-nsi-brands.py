#!/usr/bin/env python3
"""Build a compact CA/US/worldwide brand list from OSM Name Suggestion Index.

Source: https://github.com/osmlab/name-suggestion-index (BSD-3-Clause)
Download nsi.min.json from the npm package, then:

  python3 scripts/extract-nsi-brands.py /tmp/nsi/nsi.min.json \\
      receipt-engine/src/main/resources/nsi-brands.txt
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

PARENS = re.compile(r"\s*\([^)]*\)")


def in_canada_us_world(location_set: dict | None) -> bool:
    if not location_set:
        return False
    for raw in location_set.get("include") or []:
        code = str(raw).lower().replace(".geojson", "")
        if code in {"ca", "us", "001", "*"}:
            return True
        if code.startswith(("ca-", "us-", "ca_", "us_")):
            return True
    return False


def strip_parens(value: str) -> str:
    return re.sub(r"\s+", " ", PARENS.sub(" ", value)).strip()


def add_phrase(phrases: list[str], value: object) -> None:
    if not isinstance(value, str):
        return
    cleaned = strip_parens(value)
    if cleaned:
        phrases.append(cleaned)


def canonical_name(item: dict) -> str:
    tags = item.get("tags") or {}
    for key in ("brand:en", "brand", "name:en", "name"):
        value = tags.get(key)
        if isinstance(value, str) and value.strip():
            return strip_parens(value)
    display = item.get("displayName")
    if isinstance(display, str):
        return strip_parens(display)
    return ""


def phrases_for(item: dict) -> list[str]:
    tags = item.get("tags") or {}
    phrases: list[str] = []
    add_phrase(phrases, item.get("displayName"))
    for key in ("name", "brand", "name:en", "brand:en", "short_name", "alt_name"):
        add_phrase(phrases, tags.get(key))
    for alias in item.get("matchNames") or []:
        add_phrase(phrases, alias)
    return phrases


def extract(nsi: dict) -> list[tuple[str, str]]:
    rows: set[tuple[str, str]] = set()
    for tree, body in nsi.items():
        if not str(tree).startswith("brands/"):
            continue
        for item in body.get("items") or []:
            if not in_canada_us_world(item.get("locationSet")):
                continue
            canonical = canonical_name(item)
            if not canonical:
                continue
            for phrase in phrases_for(item):
                rows.add((canonical, phrase))
    return sorted(rows, key=lambda row: (row[0].casefold(), row[1].casefold()))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("nsi_json")
    parser.add_argument("output")
    args = parser.parse_args()
    payload = json.loads(Path(args.nsi_json).read_text(encoding="utf-8"))
    meta = payload.get("_meta") or {}
    rows = extract(payload["nsi"])
    lines = [
        f"# OSM Name Suggestion Index {meta.get('version', '')}".strip(),
        "# https://github.com/osmlab/name-suggestion-index",
        "# License: BSD-3-Clause. CA/US/worldwide (001) brand names only.",
        f"# generated {meta.get('generated', '')}".strip(),
    ]
    for canonical, phrase in rows:
        lines.append(f"{canonical}\t{phrase}")
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} phrases -> {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
