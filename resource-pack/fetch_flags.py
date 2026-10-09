#!/usr/bin/env python3
"""Fetch Twemoji flag PNGs used by the resource pack; assets are committed for reproducible builds."""

from __future__ import annotations

import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "src" / "assets" / "betterchat" / "textures" / "flags"
BASE = "https://raw.githubusercontent.com/twitter/twemoji/master/assets/72x72"


def codes() -> list[str]:
    result = subprocess.run(
        ["java", "--source", "25", str(ROOT / "tools" / "PrintCountryCodes.java")],
        check=True,
        capture_output=True,
        text=True,
    )
    return sorted({line.strip().upper() for line in result.stdout.splitlines() if line.strip()})


def codepoints(code: str) -> str:
    return "-".join(f"{0x1F1E6 + ord(letter) - ord('A'):x}" for letter in code)


def download(name: str, path: Path) -> bool:
    if path.exists():
        return True
    request = urllib.request.Request(f"{BASE}/{name}.png", headers={"User-Agent": "BetterChat resource pack builder"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                path.write_bytes(response.read())
            return True
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return False
            if attempt == 3:
                raise
        except OSError:
            if attempt == 3:
                raise
        time.sleep(1 + attempt)
    return False


def main() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)
    missing: list[str] = []
    for code in codes():
        if not download(codepoints(code), TARGET / f"{code.lower()}.png"):
            missing.append(code)
    if missing:
        # Twemoji only draws standardized emoji sequences. The clear globe fallback keeps
        # every ISO entry renderable and the exact missing codes are reported to the owner.
        globe_path = TARGET / "earth.png"
        if not download("1f30d", globe_path):
            raise RuntimeError("Twemoji globe fallback image was not available")
        for code in missing:
            (TARGET / f"{code.lower()}.png").write_bytes(globe_path.read_bytes())
        print("Twemoji has no country emoji graphic for: " + ", ".join(missing))
    if not (TARGET / "earth.png").exists() and not download("1f30d", TARGET / "earth.png"):
        raise RuntimeError("Could not download Twemoji globe graphic")
    license_url = "https://raw.githubusercontent.com/twitter/twemoji/master/LICENSE-GRAPHICS"
    request = urllib.request.Request(license_url, headers={"User-Agent": "BetterChat resource pack builder"})
    with urllib.request.urlopen(request, timeout=20) as response:
        (ROOT / "LICENSE-GRAPHICS").write_bytes(response.read())
    print(f"Prepared {len(codes())} ISO flag images and the Earth glyph in {TARGET}")


if __name__ == "__main__":
    main()
