#!/usr/bin/env python3
"""Generate Experience UI system assets deterministically from code.

Outputs (paths relative to the vendor/experience directory given as argument):
  bootanimation/bootanimation.zip                         boot animation (stored zip)
  wallpapers/experience_{bloom,horizon,dusk,paper}.png    bundled wallpapers
  overlay/static/frameworks/base/core/res/res/drawable-nodpi/default_wallpaper.png
  sounds/{ringtones,notifications,alarms}/*.ogg           default sounds (needs ffmpeg + libvorbis)

The artwork is built from Material 3 Expressive-style primitive shapes (circle,
squircle, pill, scalloped "cookie") filled with tonal colours of the Experience
Indigo palette. Everything is generated from constants, so the same inputs always
give the same files. Requirements: Python 3 with Pillow and numpy, ffmpeg with
libvorbis, and the Inter font (Debian/Ubuntu package fonts-inter).

Usage: generate-assets.py <rom/vendor/experience> [--preview-dir DIR]
"""
import argparse
import io
import math
import os
import shutil
import subprocess
import sys
import tempfile
import wave
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# Experience Indigo tonal palette (M3 tones of seed #4355B9) and supporting hues.
INDIGO = {10: "#00105B", 20: "#08218A", 30: "#293CA0", 40: "#4355B9", 80: "#BAC3FF", 90: "#DEE0FF", 95: "#F0EFFF"}
VIOLET = {30: "#5A2D90", 40: "#7444AA", 80: "#DDB8FF", 90: "#F0DBFF"}
TEAL = {30: "#005048", 40: "#006A60", 80: "#53DBC9", 90: "#74F8E5"}
CORAL = {40: "#A73A26", 80: "#FFB4A5", 90: "#FFDAD3"}
NEUTRAL = {6: "#121318", 10: "#1B1B21", 98: "#FBF8FF"}

FONT_CANDIDATES = [
    "/usr/share/fonts/opentype/inter/InterDisplay-SemiBold.otf",
    "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf",
    "/usr/share/fonts/truetype/inter/Inter-SemiBold.ttf",
]

SS = 3  # supersampling factor for anti-aliasing


def rgb(hex_color):
    v = int(hex_color.lstrip("#"), 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


# ---------------------------------------------------------------------------
# Shapes
# ---------------------------------------------------------------------------

def shape_points(kind, cx, cy, r, rotation=0.0, n=360):
    """Polygon for an expressive shape of 'radius' r centred at (cx, cy)."""
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        if kind == "circle":
            rr = r
        elif kind == "cookie":  # 9-scallop cookie
            rr = r * (1 + 0.08 * math.cos(9 * t))
        elif kind == "clover":  # 4-leaf clover
            rr = r * (0.82 + 0.18 * abs(math.cos(2 * t)) ** 0.6)
        elif kind == "squircle":  # superellipse |x|^4 + |y|^4 = 1
            c, s = math.cos(t), math.sin(t)
            rr = r / ((abs(c) ** 4 + abs(s) ** 4) ** 0.25)
        elif kind == "flower":  # 8-petal soft flower
            rr = r * (0.84 + 0.16 * math.cos(8 * t))
        else:
            raise ValueError(kind)
        a = t + rotation
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def draw_shape(draw, kind, cx, cy, r, colour, rotation=0.0):
    if kind == "pill":
        # horizontal pill rotated by 'rotation'
        w, h = r * 2.2, r * 0.95
        pts = []
        for i in range(181):
            t = math.pi / 2 + math.pi * i / 180
            pts.append((-w / 2 + h / 2 + (h / 2) * math.cos(t), (h / 2) * math.sin(t)))
        for i in range(181):
            t = -math.pi / 2 + math.pi * i / 180
            pts.append((w / 2 - h / 2 + (h / 2) * math.cos(t), (h / 2) * math.sin(t)))
        c, s = math.cos(rotation), math.sin(rotation)
        pts = [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]
    else:
        pts = shape_points(kind, cx, cy, r, rotation)
    draw.polygon(pts, fill=colour)


def render(size, bg, shapes):
    """Render shapes (kind, cx, cy, r, colour, rotation) in [0..1] units, supersampled."""
    w, h = size
    img = Image.new("RGB", (w * SS, h * SS), rgb(bg))
    d = ImageDraw.Draw(img)
    unit = min(w, h) * SS
    for kind, cx, cy, r, colour, rot in shapes:
        draw_shape(d, kind, cx * w * SS, cy * h * SS, r * unit, colour if isinstance(colour, tuple) else rgb(colour), rot)
    return img.resize((w, h), Image.LANCZOS)


# ---------------------------------------------------------------------------
# Wallpapers
# ---------------------------------------------------------------------------

WALLPAPERS = {
    # name: (background, [shapes])
    "experience_bloom": (INDIGO[10], [
        ("flower", 0.78, 0.18, 0.42, INDIGO[30], 0.2),
        ("cookie", 0.16, 0.52, 0.30, VIOLET[30], 0.0),
        ("squircle", 0.82, 0.74, 0.26, INDIGO[40], 0.35),
        ("circle", 0.40, 0.86, 0.22, TEAL[30], 0.0),
        ("pill", 0.30, 0.20, 0.16, INDIGO[80], -0.6),
        ("clover", 0.58, 0.50, 0.11, VIOLET[80], 0.4),
    ]),
    "experience_horizon": (INDIGO[95], [
        ("circle", 0.50, 0.92, 0.62, INDIGO[90], 0.0),
        ("circle", 0.50, 0.98, 0.46, INDIGO[80], 0.0),
        ("cookie", 0.22, 0.28, 0.20, VIOLET[90], 0.3),
        ("pill", 0.74, 0.40, 0.18, TEAL[90], 0.5),
        ("squircle", 0.70, 0.14, 0.09, CORAL[90], 0.2),
    ]),
    "experience_dusk": (NEUTRAL[6], [
        ("squircle", 0.18, 0.16, 0.34, VIOLET[30], 0.25),
        ("flower", 0.86, 0.46, 0.38, INDIGO[20], 0.1),
        ("pill", 0.36, 0.70, 0.30, TEAL[30], 0.75),
        ("cookie", 0.70, 0.90, 0.18, CORAL[40], 0.0),
        ("circle", 0.62, 0.22, 0.07, INDIGO[80], 0.0),
    ]),
    "experience_paper": (NEUTRAL[98], [
        ("cookie", 0.82, 0.16, 0.30, INDIGO[90], 0.1),
        ("squircle", 0.14, 0.62, 0.26, TEAL[90], 0.4),
        ("pill", 0.66, 0.80, 0.24, VIOLET[90], -0.45),
        ("clover", 0.34, 0.30, 0.10, CORAL[90], 0.3),
    ]),
}


def make_wallpapers(vendor, preview_dir):
    out = os.path.join(vendor, "wallpapers")
    os.makedirs(out, exist_ok=True)
    size = (1440, 2560)
    for name, (bg, shapes) in WALLPAPERS.items():
        img = render(size, bg, shapes)
        path = os.path.join(out, f"{name}.png")
        img.save(path, optimize=True)
        if preview_dir:
            img.resize((360, 640), Image.LANCZOS).save(os.path.join(preview_dir, f"{name}.jpg"), quality=88)
        if name == "experience_bloom":
            dw = os.path.join(vendor, "overlay/static/frameworks/base/core/res/res/drawable-nodpi")
            os.makedirs(dw, exist_ok=True)
            shutil.copyfile(path, os.path.join(dw, "default_wallpaper.png"))
    print(f"wallpapers: {len(WALLPAPERS)} written to {out} (default: experience_bloom)")


# ---------------------------------------------------------------------------
# Boot animation
# ---------------------------------------------------------------------------

def spring(t, damping=0.55, freq=2.4):
    """Underdamped spring step response, 0 -> 1 with overshoot (expressive motion)."""
    if t <= 0:
        return 0.0
    w = 2 * math.pi * freq
    zeta = damping
    wd = w * math.sqrt(1 - zeta ** 2)
    return 1 - math.exp(-zeta * w * t) * (math.cos(wd * t) + zeta * w / wd * math.sin(wd * t))


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


MARK = [  # (kind, dx, dy, colour, phase) — the Experience mark: four tonal shapes in a 2x2 cluster
    ("circle", -1, -1, INDIGO[80], 0.0),
    ("squircle", 1, -1, VIOLET[80], 0.08),
    ("cookie", -1, 1, TEAL[80], 0.16),
    ("flower", 1, 1, CORAL[80], 0.24),
]


def boot_frame(size, font, t_intro=None, t_loop=None, t_out=None):
    w = h = size
    shapes = []
    word_alpha = 0.0
    cell, r0 = 0.13, 0.105
    for kind, dx, dy, colour, phase in MARK:
        if t_intro is not None:
            s = spring(t_intro - phase)
            rot = (1 - min(1.0, s)) * 1.2
            word_alpha = ease((t_intro - 0.6) / 0.5)
        else:
            s = 1.0
            rot = 0.0
            word_alpha = 1.0
        if t_loop is not None:
            # gentle breathing and rotation, phase-shifted per shape; loop-continuous
            a = 2 * math.pi * t_loop
            s = 1.0 + 0.06 * math.sin(a + phase * 12)
            rot = 0.35 * math.sin(a + phase * 8)
        if t_out is not None:
            s *= 1 - ease(t_out)
            word_alpha = 1 - ease(t_out * 1.5)
        if s <= 0.001:
            continue
        shapes.append((kind, 0.5 + dx * cell, 0.42 + dy * cell, r0 * s, colour, rot))
    img = render((w, h), "#000000", shapes)
    if word_alpha > 0:
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        text = "Experience"
        bbox = d.textbbox((0, 0), text, font=font)
        tw = bbox[2] - bbox[0]
        d.text(((w - tw) / 2 - bbox[0], h * 0.74), text, font=font,
               fill=rgb(INDIGO[90]) + (int(255 * word_alpha),))
        img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    return img



# Pinkish-purplish gradient for the Experience UI logo reveal (top -> bottom).
UI_GRADIENT = ["#FFB3EC", "#F56ED6", "#C05BFF", "#7A3FF2"]


def _lerp(a, b, f):
    return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3))


def grad_strip(height, stops, periodic=False):
    """A 1-wide vertical gradient image of `height`, optionally pink->purple->pink (loops)."""
    cols = [rgb(c) for c in stops]
    seq = cols + cols[-2::-1] if periodic else cols
    img = Image.new("RGB", (1, height))
    n = len(seq) - 1
    for y in range(height):
        f = (y / (height - 1)) * n
        i = min(int(f), n - 1)
        img.putpixel((0, y), _lerp(seq[i], seq[i + 1], f - i))
    return img


def gradient_text(size, text, font, stops, shift=0.0):
    """RGBA image of `text`, centred, filled with a vertical gradient (shift in [0,1) flows it)."""
    w, h = size[0] * SS, size[1] * SS
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    bb = d.textbbox((0, 0), text, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    ox, oy = (w - tw) / 2 - bb[0], (h - th) / 2 - bb[1]
    d.text((ox, oy), text, font=font, fill=255)
    # The gradient spans exactly the glyph height, so pink->purple runs across the letters.
    top = int(oy + bb[1])
    strip = grad_strip(2 * th, stops, periodic=True)
    off = int(shift * th) % th
    band = strip.crop((0, off, 1, off + th)).resize((w, th)).convert("RGBA")
    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    grad.paste(band, (0, top))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out.resize(size, Image.LANCZOS)


def logo_frame(size, big_font, small_font, t_reveal=None, t_loop=None, t_out=None):
    """The big gradient 'UI' logo with EXPERIENCE above it."""
    w = h = size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    scale, alpha, shift = 1.0, 1.0, 0.0
    label_alpha = 1.0
    if t_reveal is not None:
        s = spring(t_reveal, damping=0.6, freq=2.2)
        scale = 0.55 + 0.45 * min(1.3, s)
        alpha = ease(t_reveal / 0.4)
        label_alpha = ease((t_reveal - 0.35) / 0.4)
    if t_loop is not None:
        shift = t_loop                                   # flow the gradient, loop-continuous
        scale = 1.0 + 0.02 * math.sin(2 * math.pi * t_loop)
    if t_out is not None:
        alpha = 1 - ease(t_out)
        label_alpha = 1 - ease(t_out * 1.4)
    if alpha <= 0.001:
        return img.convert("RGB")

    ui = gradient_text((w, h), "UI", big_font, UI_GRADIENT, shift)
    if scale != 1.0:
        nw = max(1, int(w * scale))
        ui = ui.resize((nw, nw), Image.LANCZOS)
    if alpha < 1.0:
        a = ui.split()[3].point(lambda v: int(v * alpha))
        ui.putalpha(a)
    img.alpha_composite(ui, ((w - ui.width) // 2, (h - ui.height) // 2 + int(h * 0.02)))

    if label_alpha > 0:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        text = "E X P E R I E N C E"
        bb = d.textbbox((0, 0), text, font=small_font)
        d.text(((w - (bb[2] - bb[0])) / 2 - bb[0], h * 0.28), text, font=small_font,
               fill=rgb("#F0E6FF") + (int(255 * label_alpha),))
        img = Image.alpha_composite(img, layer)
    return img.convert("RGB")


def make_bootanimation(vendor, preview_dir, font_path):
    size, fps = 1080, 30
    font = ImageFont.truetype(font_path, 92)
    import os as _os
    inter_dir = _os.path.dirname(font_path)
    big_path = _os.path.join(inter_dir, "Inter-ExtraBold.otf")
    big_font = ImageFont.truetype(big_path if _os.path.exists(big_path) else font_path, 560)
    small_font = ImageFont.truetype(font_path, 54)
    parts = {
        # Tiny blobs spring in, then give way to the big gradient UI logo, which holds (loop),
        # then fades.
        "part0": [boot_frame(size, font, t_intro=i / fps) for i in range(int(1.3 * fps))],
        "part1": [logo_frame(size, big_font, small_font, t_reveal=i / (1.1 * fps)) for i in range(int(1.1 * fps) + 1)],
        "part2": [logo_frame(size, big_font, small_font, t_loop=i / (2.4 * fps)) for i in range(int(2.4 * fps))],
        "part3": [logo_frame(size, big_font, small_font, t_out=i / (0.6 * fps)) for i in range(int(0.6 * fps) + 1)],
    }
    out_dir = os.path.join(vendor, "bootanimation")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "bootanimation.zip")
    desc = (f"{size} {size} {fps}\n"
            "c 1 0 part0\n"     # tiny blobs intro, once
            "c 1 0 part1\n"     # big gradient UI logo reveal, once
            "c 0 0 part2\n"     # logo holds (loops) until boot completes
            "c 1 0 part3\n")    # outro
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as z:
        info = zipfile.ZipInfo("desc.txt", date_time=(2026, 1, 1, 0, 0, 0))
        z.writestr(info, desc)
        for part, frames in parts.items():
            for i, frame in enumerate(frames):
                buf = io.BytesIO()
                frame.quantize(colors=128, method=Image.MEDIANCUT, dither=Image.NONE).save(buf, "PNG", optimize=True)
                info = zipfile.ZipInfo(f"{part}/{i:05d}.png", date_time=(2026, 1, 1, 0, 0, 0))
                z.writestr(info, buf.getvalue())
    if preview_dir:
        strip = Image.new("RGB", (size // 4 * 4, size // 4), "black")
        picks = [parts["part0"][6], parts["part0"][-1], parts["part1"][-1], parts["part2"][0]]
        for i, f in enumerate(picks):
            strip.paste(f.resize((size // 4, size // 4), Image.LANCZOS), (i * size // 4, 0))
        strip.save(os.path.join(preview_dir, "bootanimation_frames.jpg"), quality=88)
    n = sum(len(f) for f in parts.values())
    print(f"bootanimation: {n} frames, {os.path.getsize(path) // 1024} KiB -> {path}")


# ---------------------------------------------------------------------------
# Sounds
# ---------------------------------------------------------------------------

RATE = 48000


def note(freq, dur, amp=0.5, decay=3.0, partials=((1, 1.0), (2, 0.35), (3, 0.12), (4.2, 0.06))):
    """Mallet/bell-like tone: decaying additive partials with a short attack."""
    t = np.arange(int(dur * RATE)) / RATE
    env = np.exp(-decay * t) * np.minimum(1.0, t / 0.004)
    sig = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-decay * (m - 1) * 0.6 * t) for m, a in partials)
    return amp * env * sig


def mix(length, events):
    buf = np.zeros(int(length * RATE))
    for start, sig in events:
        i = int(start * RATE)
        buf[i:i + len(sig)] += sig[: max(0, len(buf) - i)]
    peak = np.max(np.abs(buf)) or 1.0
    return buf / peak * 0.8


def hz(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


def write_ogg(samples, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((samples * 32767).astype("<i2").tobytes())
        cmd = ["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
               "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", path]
        subprocess.run(cmd, check=True)


def make_sounds(vendor):
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg (with libvorbis) is required to encode the default sounds")
    # Ringtone "Horizon": rising C-major-9 arpeggio, two phrases, loop friendly (6 s).
    c5, e5, g5, b5, d6 = hz(3), hz(7), hz(10), hz(14), hz(17)
    phrase = [(0.0, c5), (0.18, e5), (0.36, g5), (0.54, b5), (0.72, d6), (1.05, b5), (1.23, g5)]
    events = []
    for rep in range(2):
        base = rep * 3.0
        events += [(base + s, note(f, 1.6, 0.45, 2.6)) for s, f in phrase]
        events.append((base + 1.5, note(hz(-9), 1.4, 0.25, 1.8)))  # C4 pad
    write_ogg(mix(6.0, events), os.path.join(vendor, "sounds/ringtones/Experience_Horizon.ogg"))
    # Notification "Bloom": two-note upward chime (0.9 s).
    write_ogg(mix(0.9, [(0.0, note(hz(10), 0.8, 0.5, 5.0)), (0.11, note(hz(15), 0.75, 0.5, 5.5))]),
              os.path.join(vendor, "sounds/notifications/Experience_Bloom.ogg"))
    # Alarm "Daybreak": four bell patterns, each brighter than the last (8 s).
    events = []
    for k in range(4):
        base, lift = k * 2.0, k * 2
        for j, st in enumerate((0, 4, 7, 12)):
            events.append((base + j * 0.16, note(hz(3 + st + lift), 1.2, 0.35 + 0.08 * k, 3.0)))
    write_ogg(mix(8.0, events), os.path.join(vendor, "sounds/alarms/Experience_Daybreak.ogg"))
    print("sounds: Experience_Horizon (ringtone), Experience_Bloom (notification), Experience_Daybreak (alarm)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("vendor", help="path to rom/vendor/experience (or its copy in the source tree)")
    ap.add_argument("--preview-dir", help="also write small JPEG previews here")
    args = ap.parse_args()
    font = next((f for f in FONT_CANDIDATES if os.path.exists(f)), None)
    if font is None:
        sys.exit("Inter font not found (install fonts-inter); needed for a deterministic wordmark")
    if args.preview_dir:
        os.makedirs(args.preview_dir, exist_ok=True)
    make_wallpapers(args.vendor, args.preview_dir)
    make_bootanimation(args.vendor, args.preview_dir, font)
    make_sounds(args.vendor)


if __name__ == "__main__":
    main()
