#!/usr/bin/env python3
"""Generate the Experience UI Setup Wizard welcome video (deterministic, from code).

Renders the Experience mark (the four expressive tonal shapes) springing in over a dark
Experience-indigo gradient, then "Welcome to Experience UI" fading in, with a gentle idle
drift, and encodes a short looping H.264 MP4. Output goes to the setupwizard module's
res/raw/welcome.mp4. Requires Pillow, numpy and ffmpeg (libx264), plus the Inter font.

  generate-welcome-video.py <out.mp4> <inter_semibold.otf> [--preview-dir DIR]
"""
import argparse
import math
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

W, H, FPS, SECONDS = 1280, 720, 24, 6
SS = 2  # supersample

# Experience tonal palette (same mark as the boot animation).
BG_TOP = (14, 15, 19)       # #0E0F13
BG_BOT = (20, 24, 40)       # deep indigo
SHAPES = [("circle", (186, 195, 255)), ("squircle", (221, 184, 255)),
          ("cookie", (83, 219, 201)), ("flower", (255, 180, 165))]
TEXT = (242, 243, 247)
MUTED = (154, 160, 176)
ACCENT = (150, 170, 255)


def shape_pts(kind, cx, cy, r, rot=0.0, n=240):
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        if kind == "circle":
            rr = r
        elif kind == "cookie":
            rr = r * (1 + 0.08 * math.cos(9 * t))
        elif kind == "flower":
            rr = r * (0.84 + 0.16 * math.cos(8 * t))
        elif kind == "squircle":
            c, s = math.cos(t), math.sin(t)
            rr = r / ((abs(c) ** 4 + abs(s) ** 4) ** 0.25)
        else:
            rr = r
        a = t + rot
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def spring(t, damping=0.55, freq=2.2):
    if t <= 0:
        return 0.0
    w = 2 * math.pi * freq
    wd = w * math.sqrt(1 - damping ** 2)
    return 1 - math.exp(-damping * w * t) * (math.cos(wd * t) + damping * w / wd * math.sin(wd * t))


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def bg():
    g = Image.new("RGB", (1, H), 0)
    for y in range(H):
        f = y / (H - 1)
        g.putpixel((0, y), tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * f) for i in range(3)))
    return g.resize((W, H))


def frame(ti, font_big, font_small):
    img = bg().convert("RGBA")
    layer = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = W * SS / 2, H * SS * 0.40
    cell = H * SS * 0.11
    r0 = H * SS * 0.085
    idle = 0.03 * math.sin(2 * math.pi * ti / SECONDS)  # loop-continuous idle breathing
    for (kind, col), (dx, dy), phase in zip(SHAPES, [(-1, -1), (1, -1), (-1, 1), (1, 1)], (0, .08, .16, .24)):
        s = spring(ti - phase)
        if s <= 0.001:
            continue
        scale = s * (1 + idle)
        rot = (1 - min(1.0, s)) * 1.1 + 0.25 * math.sin(2 * math.pi * ti / SECONDS + phase * 10)
        d.polygon(shape_pts(kind, cx + dx * cell, cy + dy * cell, r0 * scale, rot), fill=col + (255,))
    mark = layer.resize((W, H), Image.LANCZOS)
    img.alpha_composite(mark)

    d2 = ImageDraw.Draw(img)
    a_small = ease((ti - 0.9) / 0.5)
    a_big = ease((ti - 1.1) / 0.5)
    if a_small > 0:
        t = "Welcome to"
        bb = d2.textbbox((0, 0), t, font=font_small)
        d2.text(((W - (bb[2] - bb[0])) / 2 - bb[0], H * 0.66), t, font=font_small,
                fill=MUTED + (int(255 * a_small),))
    if a_big > 0:
        t = "Experience UI"
        bb = d2.textbbox((0, 0), t, font=font_big)
        d2.text(((W - (bb[2] - bb[0])) / 2 - bb[0], H * 0.72), t, font=font_big,
                fill=TEXT + (int(255 * a_big),))
    return img.convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("font")
    ap.add_argument("--preview-dir")
    a = ap.parse_args()
    if not os.path.exists(a.font):
        sys.exit(f"font not found: {a.font}")
    font_big = ImageFont.truetype(a.font, 84)
    font_small = ImageFont.truetype(a.font, 40)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    n = FPS * SECONDS
    with tempfile.TemporaryDirectory() as tmp:
        for i in range(n):
            frame(i / FPS, font_big, font_small).save(os.path.join(tmp, f"f{i:04d}.png"))
            if a.preview_dir and i in (FPS // 2, int(1.5 * FPS), n - 1):
                os.makedirs(a.preview_dir, exist_ok=True)
                frame(i / FPS, font_big, font_small).save(os.path.join(a.preview_dir, f"welcome_{i}.jpg"), quality=88)
        cmd = ["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-framerate", str(FPS),
               "-i", os.path.join(tmp, "f%04d.png"),
               "-c:v", "libx264", "-profile:v", "baseline", "-level", "3.1",
               "-pix_fmt", "yuv420p", "-crf", "30", "-movflags", "+faststart",
               "-an", "-fflags", "+bitexact", "-flags:v", "+bitexact", a.out]
        subprocess.run(cmd, check=True)
    print(f"welcome.mp4: {os.path.getsize(a.out)//1024} KiB, {n} frames, {SECONDS}s @ {FPS}fps, {W}x{H}")


if __name__ == "__main__":
    main()
