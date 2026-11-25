#!/usr/bin/env python3
"""Draw the two resource images required by the manifest.

These are original flat marks made for this public tree: a dark square icon and a wide banner.
They are not the excluded launcher icon or banner, and they are not a claim that any other image is licensed.
"""
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON = os.path.join(ROOT, "app/res/mipmap-xhdpi/ic_launcher.png")
BANNER = os.path.join(ROOT, "app/res/drawable-xhdpi/banner.png")

BG = (11, 11, 10, 255)
GOLD = (196, 165, 116, 255)
INK = (236, 231, 221, 255)


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path, width, height, pixel):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixel(x, y))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def disc(cx, cy, r, x, y):
    return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r


def ring(cx, cy, r, w, x, y):
    d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
    return (r - w) * (r - w) <= d <= r * r


def icon_pixel(x, y):
    if ring(96, 96, 70, 7, x, y) or disc(96, 96, 5, x, y):
        return GOLD
    # Short hour hand and longer minute hand, axis-aligned so the mark stays simple.
    if 96 <= x <= 132 and 90 <= y <= 102:
        return INK
    if 90 <= x <= 102 and 40 <= y <= 96:
        return INK
    return BG


def banner_pixel(x, y):
    t = y / 179.0
    base = blend((11, 11, 10, 255), (28, 32, 36, 255), t)
    if 36 <= x <= 210 and 118 <= y <= 122:
        return GOLD
    if ring(252, 78, 26, 4, x, y):
        return GOLD
    return base


def main():
    check = "--check" in sys.argv
    targets = [(ICON, 192, 192, icon_pixel), (BANNER, 320, 180, banner_pixel)]
    if check:
        import tempfile
        bad = False
        for path, w, h, pixel in targets:
            fd, tmp = tempfile.mkstemp(suffix=".png")
            os.close(fd)
            try:
                write_png(tmp, w, h, pixel)
                with open(tmp, "rb") as a, open(path, "rb") as b:
                    if a.read() != b.read():
                        print("brand png drift: " + os.path.relpath(path, ROOT))
                        bad = True
            finally:
                os.remove(tmp)
        if bad:
            return 1
        print("brand pngs match the generator")
        return 0
    for path, w, h, pixel in targets:
        write_png(path, w, h, pixel)
        print("wrote " + os.path.relpath(path, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
