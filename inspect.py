#!/usr/bin/env python3
"""Verify synthetic moats captures and transport review images through GitHub job logs."""

# The filename is inspect.py. Ensure Pillow and its dependencies can import the stdlib inspect.
import os
import sys
sys.path[:] = [p for p in sys.path if os.path.realpath(p) != os.path.dirname(os.path.realpath(__file__))]

import argparse
import base64
import hashlib
from io import BytesIO
from pathlib import Path

from PIL import Image

ENGLISH = (
    "hub-1", "hub-2", "settings-sunnah", "settings-report", "trust-1", "trust-2",
    "history-challenges-row", "challenges", "challenge-detail", "challenge-join",
    "history-kinds", "log-metrics", "body-signals", "ramadan-safety", "share-fast",
    "share-week", "share-challenge", "doctor-report-page-1",
)
MALAY = ("settings-sunnah", "hub-1")
EXPECTED = [f"en/{name}.png" for name in ENGLISH] + [f"ms/{name}.png" for name in MALAY]
SETTINGS = ["settings/4-settings-light.png", "settings/4-settings-dark.png"]

ORIGINALS = {*SETTINGS, "en/hub-1.png", "en/challenge-detail.png", "en/trust-1.png"}
CHUNK_BYTES = 3072


def read_image(path):
    raw = path.read_bytes()
    with Image.open(BytesIO(raw)) as im:
        if im.format != "PNG":
            raise ValueError(f"{path} is not a PNG")
        im.verify()
    with Image.open(BytesIO(raw)) as im:
        im.load()
        return raw, im.convert("RGBA")


def preview(im):
    im.thumbnail((700, 1250), Image.Resampling.LANCZOS)
    rgb = Image.new("RGB", im.size, "white")
    rgb.paste(im, mask=im.getchannel("A"))
    buf = BytesIO()
    rgb.save(buf, format="JPEG", quality=90, optimize=True, subsampling=0)
    return buf.getvalue(), im.size


def emit(path, raw, size):
    checksum = hashlib.sha256(raw).hexdigest()
    count = (len(raw) + CHUNK_BYTES - 1) // CHUNK_BYTES
    print(f"FSIMG|BEGIN|{path}|{len(raw)}|{checksum}|{size[0]}|{size[1]}|{count}")
    for index in range(count):
        start = index * CHUNK_BYTES
        encoded = base64.b64encode(raw[start:start + CHUNK_BYTES]).decode("ascii")
        print(f"FSIMG|PART|{path}|{index}|{encoded}")
    print(f"FSIMG|END|{path}|{count}|{checksum}")


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("directory", type=Path, help="extracted capture directory containing en/ and ms/")
    cli.add_argument("--only", choices=EXPECTED, action="append",
                     help="limit output to this capture (repeatable); all inputs are still verified")
    args = cli.parse_args()
    selected = set(args.only or EXPECTED)
    missing = [name for name in EXPECTED if not (args.directory / name).is_file()]
    if missing:
        cli.error(f"missing {len(missing)} required captures: {', '.join(missing)}")
    # Validate the complete manifest before writing log payloads. Keep only encoded deliverables.
    deliverables = []
    for name in EXPECTED:
        raw, im = read_image(args.directory / name)
        try:
            if name not in selected:
                continue
            original_size = im.size
            jpeg, size = preview(im)
            deliverables.append((name[:-4] + ".jpg", jpeg, size))
            if name in ORIGINALS:
                deliverables.append((name, raw, original_size))
        finally:
            im.close()
    for item in deliverables:
        emit(*item)
    print(f"FSIMG|COMPLETE|{len(EXPECTED)}|{len(deliverables)}")


if __name__ == "__main__":
    main()
