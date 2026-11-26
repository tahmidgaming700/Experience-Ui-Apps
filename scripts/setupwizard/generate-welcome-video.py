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

import importlib.util

from PIL import Image, ImageChops, ImageDraw, ImageFont

# Share the boot animation's cinematic effects (starfield, vortex, lens flare,
# particle wave, gradient text) so the welcome clip and the boot animation never
# drift apart. generate-assets.py lives two dirs over, under scripts/rom/.
_GEN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "rom", "generate-assets.py")
_spec = importlib.util.spec_from_file_location("genassets", _GEN)
G = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(G)

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
    """Landscape mirror of the boot animation: starfield -> the mark orbits ->
    spiral vortex -> lens-flare reveal -> EXPERIENCE UI with a particle wave,
    fading to black at the end for a seamless loop."""
    big, small = fonts
    cx, cy = 0.5, 0.46
    cell, r0 = 0.11, 0.085
    ar = H / W                                            # keep the cluster circular in landscape
    INTRO_END, ORB_END, VORTEX_END, REVEAL_AT = 1.0, 2.9, 3.8, 3.7
    fade_out = ease((ti - (SECONDS - 0.5)) / 0.5)         # fade to black for a seamless loop

    base = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    G._draw_starfield(base, G.STARS, ti, dim=1.0 if ti < REVEAL_AT else 0.7)
    out = base.convert("RGB")

    # the mark springs in, orbits upright, then dissolves into the vortex
    shapes, blob_alpha, word_alpha = [], 1.0, 0.0
    if ti < ORB_END + 0.2:
        if ti < INTRO_END:
            for kind, dx, dy, colour, phase in G.MARK:
                s = G.spring(ti - phase)
                if s <= 0.001:
                    continue
                shapes.append((kind, cx + dx * cell * ar, cy + dy * cell,
                               r0 * min(1.0, s), colour, (1 - min(1.0, s)) * 1.2))
            word_alpha = G.ease((ti - 0.6) / 0.5)
        else:
            sp = min(1.0, (ti - INTRO_END) / (ORB_END - INTRO_END))
            blob_alpha = 1.0 if ti < ORB_END else max(0.0, 1.0 - (ti - ORB_END) / 0.15)
            if blob_alpha > 0.003:
                for kind, dx, dy, colour, phase in G.MARK:
                    ang = math.atan2(dy, dx) + 2 * math.pi * 2.0 * sp
                    rad = math.hypot(dx, dy)
                    shapes.append((kind, cx + cell * rad * math.cos(ang) * ar,
                                   cy + cell * rad * math.sin(ang), r0, colour, 0.0))
    if shapes:
        blobs = G.render((W, H), "#000000", shapes)
        if blob_alpha < 1.0:
            blobs = Image.eval(blobs, lambda v: int(v * blob_alpha))
        out = ImageChops.screen(out, blobs)

    if ORB_END - 0.1 <= ti < VORTEX_END + 0.35:
        vp = (ti - ORB_END) / (VORTEX_END - ORB_END)
        out = ImageChops.screen(out, G._draw_vortex((W, H), G.VORTEX, max(0.0, min(1.0, vp)), cx, cy))

    img = out.convert("RGBA")

    if word_alpha > 0.001:
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        bb = d.textbbox((0, 0), "Experience", font=small)
        d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], H * 0.72), "Experience", font=small,
               fill=G.rgb(G.INDIGO[90]) + (int(255 * min(1.0, word_alpha)),))
        img = Image.alpha_composite(img, layer)

    rt = ti - REVEAL_AT
    if rt > -0.2:
        fl = (math.sin(math.pi * min(1.0, rt / 0.6)) if rt < 0.6 else max(0.0, 1 - (rt - 0.6) / 0.5))
        fl *= (1 - fade_out)
        if fl > 0.001:
            img = G._screen_over(img.convert("RGB"), G._draw_lensflare((W, H), 0.5, 0.5, fl)).convert("RGBA")
        logo_in = ease(rt / 0.45)
        logo_alpha = logo_in * (1 - fade_out)
        if logo_alpha > 0.001:
            s = spring(rt, damping=0.6, freq=2.2)
            scale = 0.6 + 0.4 * min(1.25, s)
            ui = G.gradient_text((W, H), "UI", big, UI_GRADIENT, max(0.0, rt) * 0.12)
            if scale != 1.0:
                ui = ui.resize((int(W * scale), max(1, int(H * scale))), Image.LANCZOS)
            a = ui.split()[3].point(lambda v: int(v * logo_alpha))
            ui.putalpha(a)
            img.alpha_composite(ui, ((W - ui.width) // 2, (H - ui.height) // 2 + int(H * 0.04)))
            label_alpha = ease((rt - 0.35) / 0.5) * (1 - fade_out)
            if label_alpha > 0.001:
                layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                d = ImageDraw.Draw(layer)
                t = "E X P E R I E N C E"
                bb = d.textbbox((0, 0), t, font=small)
                d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], H * 0.24), t, font=small,
                       fill=LABEL + (int(255 * min(1.0, label_alpha)),))
                img = Image.alpha_composite(img, layer)
        wstr = ease((rt - 0.3) / 0.6) * 0.8 * (1 - fade_out)
        if wstr > 0.001:
            img = G._screen_over(img.convert("RGB"),
                                 G._draw_wave((W, H), G.WAVE, max(0.0, rt), 0.62, wstr)).convert("RGBA")

    res = img.convert("RGB")
    if fade_out > 0.001:
        res = Image.eval(res, lambda v: int(v * (1 - fade_out)))
    return res


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
