#!/usr/bin/env python3
"""Build the BetterChat Java Edition flag font pack from its checked-in flag images."""

from __future__ import annotations

import json
import math
import struct
import subprocess
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "src"
OUTPUT = ROOT / "BetterChat-Flags.zip"


def country_codes() -> list[str]:
    result = subprocess.run(
        ["java", "--source", "25", str(ROOT / "tools" / "PrintCountryCodes.java")],
        check=True,
        capture_output=True,
        text=True,
    )
    return sorted({line.strip().upper() for line in result.stdout.splitlines() if line.strip()})


def glyph_for(code: str) -> str:
    if code == "EARTH":
        return chr(0xE6FF)
    return chr(0xE000 + (ord(code[0]) - ord("A")) * 26 + ord(code[1]) - ord("A"))


def make_earth_flag(path: Path) -> None:
    """Create a centered 3:2 Earth flag in a square glyph canvas."""
    width, height, scale = 48, 48, 3
    flag_height, flag_top = 32, 8
    high_width, high_height = width * scale, height * scale
    transparent = (0, 0, 0, 0)
    pixels: list[list[tuple[int, int, int, int]]] = [[transparent] * high_width for _ in range(high_height)]
    for y in range(flag_top * scale, (flag_top + flag_height) * scale):
        shade = (y / scale - flag_top) / max(1, flag_height - 1)
        ocean = (22, 111 + round(shade * 22), 183 + round(shade * 17), 255)
        pixels[y] = [ocean] * high_width

    def polygon(points: tuple[tuple[float, float], ...], color: tuple[int, int, int, int]) -> None:
        points = tuple((x * 2 / 3, y * 2 / 3 + flag_top) for x, y in points)
        top = max(0, math.floor(min(y for _, y in points) * scale))
        bottom = min(high_height, math.ceil(max(y for _, y in points) * scale))
        for py in range(top, bottom):
            scan_y = (py + 0.5) / scale
            crossings: list[float] = []
            for index, (x1, y1) in enumerate(points):
                x2, y2 = points[(index + 1) % len(points)]
                if (y1 <= scan_y < y2) or (y2 <= scan_y < y1):
                    crossings.append(x1 + (scan_y - y1) * (x2 - x1) / (y2 - y1))
            crossings.sort()
            for index in range(0, len(crossings) - 1, 2):
                left, right = crossings[index:index + 2]
                start = max(0, math.ceil(left * scale - 0.5))
                end = min(high_width, math.ceil(right * scale - 0.5))
                for px in range(start, end):
                    pixels[py][px] = color

    land = (83, 177, 91, 255)
    highlight = (104, 195, 102, 255)
    continents = [
        # North America, Central America, and Greenland
        ((9, 13), (12, 9), (18, 7), (22, 8), (26, 10), (28, 13), (26, 16),
         (24, 17), (23, 21), (20, 23), (18, 21), (16, 18), (12, 17), (10, 15)),
        ((26, 20), (29, 21), (31, 25), (33, 28), (31, 30), (28, 28), (27, 25)),
        ((29, 29), (33, 28), (37, 31), (38, 34), (36, 37), (35, 41), (33, 45),
         (31, 41), (31, 37), (29, 34), (28, 31)),
        ((28, 7), (31, 5), (34, 6), (34, 9), (32, 11), (29, 10)),
        # Europe and Africa
        ((39, 14), (42, 12), (46, 13), (48, 15), (46, 18), (43, 18), (41, 17)),
        ((42, 19), (46, 18), (50, 21), (51, 25), (49, 30), (47, 35), (45, 40),
         (43, 36), (42, 31), (40, 27), (40, 23)),
        # Asia, the Indian peninsula, and island regions
        ((47, 14), (50, 11), (55, 9), (61, 10), (66, 12), (69, 15), (66, 18),
         (62, 18), (60, 20), (56, 18), (53, 21), (50, 20), (48, 18)),
        ((53, 20), (57, 21), (59, 24), (58, 27), (56, 26), (55, 23)),
        ((58, 25), (62, 26), (64, 29), (62, 30), (59, 28)),
        ((60, 34), (64, 33), (68, 35), (67, 38), (64, 40), (61, 38)),
        ((68, 20), (69, 19), (70, 21), (69, 23)),
        ((49, 35), (50, 36), (49, 39), (48, 38)),
    ]
    for shape in continents:
        polygon(shape, land)
    # Small highlight regions add definition after the flag is scaled to chat size.
    polygon(((12, 11), (18, 9), (23, 10), (25, 12), (21, 13), (17, 12)), highlight)
    polygon(((43, 21), (46, 20), (48, 23), (47, 27), (45, 30), (44, 26)), highlight)
    polygon(((52, 12), (57, 11), (62, 12), (65, 14), (60, 14), (56, 15)), highlight)

    # Supersample then average each 3x3 block for smooth edges at Minecraft's small font size.
    final_pixels: list[list[tuple[int, int, int, int]]] = []
    for y in range(height):
        row = []
        for x in range(width):
            samples = [pixels[y * scale + dy][x * scale + dx] for dy in range(scale) for dx in range(scale)]
            row.append(tuple(sum(sample[channel] for sample in samples) // len(samples) for channel in range(4)))
        final_pixels.append(row)

    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in final_pixels)

    def chunk(name: bytes, data: bytes) -> bytes:
        body = name + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def make_pack() -> None:
    providers: list[dict[str, object]] = []
    for code in country_codes():
        file = SOURCE / "assets" / "betterchat" / "textures" / "flags" / f"{code.lower()}.png"
        if not file.is_file():
            continue
        providers.append({
            "type": "bitmap",
            "file": f"betterchat:flags/{code.lower()}.png",
            "ascent": 7,
            "height": 8,
            "chars": [glyph_for(code)],
        })

    earth = SOURCE / "assets" / "betterchat" / "textures" / "flags" / "earth.png"
    make_earth_flag(earth)
    providers.append({
        "type": "bitmap", "file": "betterchat:flags/earth.png", "ascent": 7, "height": 8,
        "chars": [glyph_for("EARTH")],
    })

    (SOURCE / "assets" / "betterchat" / "font").mkdir(parents=True, exist_ok=True)
    (SOURCE / "assets" / "betterchat" / "font" / "flags.json").write_text(
        json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (SOURCE / "pack.mcmeta").write_text(
        json.dumps({"pack": {
            "min_format": [88, 0],
            "max_format": [97, 1],
            "description": "BetterChat country flags and rectangular Earth flag",
        }},
                   ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for file in sorted(SOURCE.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(SOURCE).as_posix())
        for file in (ROOT / "THIRD_PARTY_NOTICES.md", ROOT / "LICENSE-GRAPHICS"):
            if file.is_file():
                archive.write(file, file.name)
    print(f"Created {OUTPUT} ({OUTPUT.stat().st_size:,} bytes)")
    print(f"Mapped {len(providers) - 1} country flags and the rectangular Earth flag into betterchat:flags")


if __name__ == "__main__":
    make_pack()
