#!/usr/bin/env python3
"""Generate the Experience UI Setup Wizard welcome video (deterministic, from code).

The Experience mark (four tonal shapes) springs in over a dark indigo gradient, then
gives way to the big "EXPERIENCE / UI" logo with a pink->purple gradient — the same reveal
as the boot animation — with a gentle gradient flow, encoded to a short looping H.264 MP4.
Output: the setupwizard module's res/raw/welcome.mp4. Requires Pillow and ffmpeg (libx264)
plus the Inter font (Inter-SemiBold + Inter-ExtraBold).

  generate-welcome-video.py <out.mp4> <inter_semibold.otf> [--preview-dir DIR]
"""
import argparse
import math
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

W, H, FPS, SECONDS = 1280, 720, 24, 8
SS = 2

BG_TOP = (14, 15, 19)
BG_BOT = (20, 24, 40)
SHAPES = [("circle", (186, 195, 255)), ("squircle", (221, 184, 255)),
          ("cookie", (83, 219, 201)), ("flower", (255, 180, 165))]
LABEL = (240, 230, 255)
UI_GRADIENT = ["#FFB3EC", "#F56ED6", "#C05BFF", "#7A3FF2"]  # pink -> purple


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _lerp(a, b, f):
    return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3))


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
        g.putpixel((0, y), _lerp(BG_TOP, BG_BOT, f))
    return g.resize((W, H))


def grad_strip(height, stops):
    cols = [rgb(c) for c in stops]
    seq = cols + cols[-2::-1]  # periodic (pink->purple->pink) for seamless flow
    img = Image.new("RGB", (1, height))
    n = len(seq) - 1
    for y in range(height):
        f = (y / (height - 1)) * n
        i = min(int(f), n - 1)
        img.putpixel((0, y), _lerp(seq[i], seq[i + 1], f - i))
    return img


def gradient_text(size, text, font, shift=0.0):
    w, h = size[0] * SS, size[1] * SS
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    bb = d.textbbox((0, 0), text, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    ox, oy = (w - tw) / 2 - bb[0], (h - th) / 2 - bb[1]
    d.text((ox, oy), text, font=font, fill=255)
    top = int(oy + bb[1])
    strip = grad_strip(2 * th, UI_GRADIENT)
    off = int(shift * th) % th
    band = strip.crop((0, off, 1, off + th)).resize((w, th)).convert("RGBA")
    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    grad.paste(band, (0, top))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out.resize(size, Image.LANCZOS)


def frame(ti, fonts):
    big, small = fonts
    img = bg().convert("RGBA")

    # Timeline: blobs spring in (0-1s) -> rotate for a while (1-3.4s), whirl inward ->
    # big gradient UI logo reveal/hold -> fade at the end for a seamless loop.
    SPIN_START, SPIN_END = 1.0, 3.4
    blob_alpha = 1.0 if ti < SPIN_END - 0.25 else max(0.0, 1.0 - ease((ti - (SPIN_END - 0.25)) / 0.3))
    if blob_alpha > 0.001:
        layer = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        cx, cy = W * SS / 2, H * SS * 0.46
        cell, r0 = H * SS * 0.11, H * SS * 0.085
        for (kind, col), (dx, dy), phase in zip(SHAPES, [(-1, -1), (1, -1), (-1, 1), (1, 1)], (0, .08, .16, .24)):
            s = spring(ti - phase)
            px, py, rot = cx + dx * cell, cy + dy * cell, (1 - min(1.0, s)) * 1.1
            if ti >= SPIN_START:
                sp = (ti - SPIN_START) / (SPIN_END - SPIN_START)       # 0..1 over the spin
                ang0 = math.atan2(dy, dx)
                A = 2 * math.pi * 2.5 * sp
                conv = ease((sp - 0.78) / 0.22)
                s = 1.0 - conv
                rad = math.hypot(dx, dy) * (1.0 - 0.9 * conv)
                px = cx + cell * rad * math.cos(ang0 + A)
                py = cy + cell * rad * math.sin(ang0 + A)
                rot = A * 1.3
            if s <= 0.001:
                continue
            d.polygon(shape_pts(kind, px, py, r0 * s, rot), fill=col + (int(255 * blob_alpha),))
        img.alpha_composite(layer.resize((W, H), Image.LANCZOS))

    reveal_at = SPIN_END - 0.1
    logo_in = ease((ti - reveal_at) / 0.5)
    logo_out = ease((ti - (SECONDS - 0.5)) / 0.5)   # fade at the end for a seamless loop
    logo_alpha = logo_in * (1 - logo_out)
    label_alpha = ease((ti - (reveal_at + 0.35)) / 0.5) * (1 - logo_out)
    if logo_alpha > 0.001:
        shift = (ti - reveal_at) * 0.12                   # slow gradient flow
        s = spring(ti - reveal_at, damping=0.6, freq=2.2)
        scale = 0.6 + 0.4 * min(1.25, s)
        ui = gradient_text((W, H), "UI", big, shift)
        if scale != 1.0:
            nh = max(1, int(H * scale))
            ui = ui.resize((int(W * scale), nh), Image.LANCZOS)
        a = ui.split()[3].point(lambda v: int(v * logo_alpha))
        ui.putalpha(a)
        img.alpha_composite(ui, ((W - ui.width) // 2, (H - ui.height) // 2 + int(H * 0.04)))
    if label_alpha > 0.001:
        d = ImageDraw.Draw(img)
        t = "E X P E R I E N C E"
        bb = d.textbbox((0, 0), t, font=small)
        d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], H * 0.24), t, font=small,
               fill=LABEL + (int(255 * label_alpha),))
    return img.convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("font", help="Inter-SemiBold.otf (Inter-ExtraBold.otf is used for the big UI from the same folder)")
    ap.add_argument("--preview-dir")
    a = ap.parse_args()
    if not os.path.exists(a.font):
        sys.exit(f"font not found: {a.font}")
    big_path = os.path.join(os.path.dirname(a.font), "Inter-ExtraBold.otf")
    big = ImageFont.truetype(big_path if os.path.exists(big_path) else a.font, 300)
    small = ImageFont.truetype(a.font, 36)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    n = FPS * SECONDS
    with tempfile.TemporaryDirectory() as tmp:
        for i in range(n):
            frame(i / FPS, (big, small)).save(os.path.join(tmp, f"f{i:04d}.png"))
            if a.preview_dir and i in (FPS // 2, int(1.6 * FPS), int(2.5 * FPS), n - 8):
                os.makedirs(a.preview_dir, exist_ok=True)
                frame(i / FPS, (big, small)).save(os.path.join(a.preview_dir, f"welcome_{i}.jpg"), quality=88)
        cmd = ["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-framerate", str(FPS),
               "-i", os.path.join(tmp, "f%04d.png"),
               "-c:v", "libx264", "-profile:v", "baseline", "-level", "3.1",
               "-pix_fmt", "yuv420p", "-crf", "30", "-movflags", "+faststart",
               "-an", "-fflags", "+bitexact", "-flags:v", "+bitexact", a.out]
        subprocess.run(cmd, check=True)
    print(f"welcome.mp4: {os.path.getsize(a.out)//1024} KiB, {n} frames, {SECONDS}s @ {FPS}fps, {W}x{H}")


if __name__ == "__main__":
    main()
