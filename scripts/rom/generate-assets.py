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
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

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

# --------------------------------------------------------------------------- #
# Cinematic boot effects: starfield, particle vortex, lens flare, particle wave.
# All are deterministic (seeded) and expressed in [0,1] units so they scale to
# any resolution. They render at a small internal scale (FXS) and composite with
# screen/add so glows stay bright without washing the palette to white.
# --------------------------------------------------------------------------- #
FXS = 1                 # effect-layer supersample (kept at output res; blur gives AA)
ARM_COLS = [TEAL[80], VIOLET[80], CORAL[80], INDIGO[80]]


def _starfield(n=190, seed=7):
    rng = np.random.default_rng(seed)
    return list(zip(rng.random(n), rng.random(n), rng.random(n) ** 2.2,
                    0.6 + 1.9 * rng.random(n), rng.random(n)))


def _draw_starfield(img, stars, t, dim=1.0):
    w, h = img.size
    d = ImageDraw.Draw(img, "RGBA")
    for x, y, br, rad, tw in stars:
        tw_b = 0.65 + 0.35 * math.sin(2 * math.pi * (t * 0.25 + tw))
        a = int(190 * br * tw_b * dim)
        if a <= 2:
            continue
        px, py, r = x * w, y * h, rad * (min(w, h) / 1080)
        d.ellipse([px - r, py - r, px + r, py + r], fill=(220, 225, 255, a))


def _vortex_particles(arms=4, per=30, seed=11):
    rng = np.random.default_rng(seed)
    parts = []
    for ai in range(arms):
        arm_ang = 2 * math.pi * ai / arms
        tcol = ARM_COLS[ai % len(ARM_COLS)]
        for j in range(per):
            u = (j + rng.random()) / per
            r0 = 0.03 + 0.29 * u
            parts.append((arm_ang + rng.normal(0, 0.05), r0,
                          1.6 + 3.0 * (1 - u), 0.26 * rng.random(), tcol))
    return parts


def _draw_vortex(size, parts, p, cx, cy, intensity=1.0):
    """Spiral arms collapse into a bright core. p in [0,1]. Returns an additive RGB glow."""
    w, h = size
    unit = min(w, h)
    cx0, cy0 = cx * w, cy * h
    arms = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    da = ImageDraw.Draw(arms, "RGBA")
    turns = 2.0
    for ang0, r0, width, delay, tcol in parts:
        lp = (p - delay) / (1.0 - delay)
        if lp <= 0:
            continue
        lp = min(1.05, lp)
        seg = []
        for k in range(10):
            llp = max(0.0, lp - 0.03 * k)
            rr = r0 * (1.0 - llp) ** 1.05
            aa = ang0 + 2 * math.pi * turns * llp
            seg.append((cx0 + rr * unit * math.cos(aa), cy0 + rr * unit * math.sin(aa)))
        a = int(235 * intensity * (0.4 + 0.6 * (1 - abs(2 * lp - 1))))
        col = rgb(tcol) + (a,)
        da.line(seg, fill=col, width=max(1, int(width * unit / 1080 * 1.1)), joint="curve")
        r = 1.6 * unit / 1080
        da.ellipse([seg[0][0] - r, seg[0][1] - r, seg[0][0] + r, seg[0][1] + r], fill=col)
    core = ease(max(0.0, (p - 0.5) / 0.5))
    if core > 0:
        cr = (0.01 + 0.055 * core) * unit
        da.ellipse([cx0 - cr, cy0 - cr, cx0 + cr, cy0 + cr],
                   fill=(255, 244, 255, int(255 * core)))
    rgb_arms = Image.new("RGB", (w, h), (0, 0, 0))
    rgb_arms.paste(arms, (0, 0), arms)
    blur = max(1, int(2.6 * unit / 1080))
    bloom = rgb_arms.filter(ImageFilter.GaussianBlur(blur))
    core_g = rgb_arms.filter(ImageFilter.GaussianBlur(max(1, blur // 3)))
    return ImageChops.add(ImageChops.add(core_g, bloom), rgb_arms)


def _draw_lensflare(size, cx, cy, strength):
    w, h = size
    unit = min(w, h)
    g = Image.new("RGB", (w, h), (0, 0, 0))
    d = ImageDraw.Draw(g)
    cx0, cy0 = cx * w, cy * h
    br = int(0.09 * unit)
    d.ellipse([cx0 - br, cy0 - br, cx0 + br, cy0 + br], fill=(255, 250, 255))
    sw, sh = int(w * 0.95), max(2, int(0.006 * unit))
    d.ellipse([cx0 - sw / 2, cy0 - sh, cx0 + sw / 2, cy0 + sh], fill=(210, 190, 255))
    for fx, fr, col in [(-0.28, 0.012, (120, 230, 215)), (0.33, 0.016, (255, 180, 165)),
                        (0.12, 0.008, (200, 190, 255))]:
        rr = fr * unit
        d.ellipse([cx0 + fx * w - rr, cy0 - rr, cx0 + fx * w + rr, cy0 + rr], fill=col)
    g = g.filter(ImageFilter.GaussianBlur(3 * unit / 1080))
    return Image.eval(g, lambda v: int(v * strength)) if strength < 1.0 else g


def _wave_particles(n=130, seed=5):
    rng = np.random.default_rng(seed)
    return list(zip(rng.random(n), 0.012 + 0.03 * rng.random(n), 1.5 + 3.0 * rng.random(n),
                    rng.random(n), 1.0 + 2.2 * rng.random(n), rng.integers(0, 3, n),
                    0.5 + 1.0 * rng.random(n)))


_WAVE_COLS = [(120, 230, 215), (221, 184, 255), (255, 180, 165)]


def _draw_wave(size, parts, t_loop, cy, strength=1.0):
    w, h = size
    unit = min(w, h)
    g = Image.new("RGB", (w, h), (0, 0, 0))
    d = ImageDraw.Draw(g)
    for x0, amp, k, ph, sz, col, sp in parts:
        x = (x0 + sp * t_loop * 0.12) % 1.0
        y = cy + amp * math.sin(2 * math.pi * (k * x + ph + t_loop))
        r = sz * unit / 1080
        d.ellipse([x * w - r, y * h - r, x * w + r, y * h + r], fill=_WAVE_COLS[col])
    g = g.filter(ImageFilter.GaussianBlur(1.3 * unit / 1080))
    return Image.eval(g, lambda v: int(v * strength)) if strength < 1.0 else g


def _screen_over(base_rgb, glow_rgb):
    return ImageChops.screen(base_rgb, glow_rgb)


# Deterministic, resolution-independent effect data (built once).
STARS = _starfield()
VORTEX = _vortex_particles()
WAVE = _wave_particles()


def boot_frame(size, font, t_intro=None, t_spin=None):
    """part0 (t_intro): starfield + blobs spring into the 2x2 mark + 'Experience'.
    part1 (t_spin): the blobs orbit upright, then dissolve into a spiral vortex that
    collapses to a bright core."""
    w = h = size
    cell, r0 = 0.13, 0.105
    cx, cy = 0.5, 0.42
    star_t = t_intro if t_intro is not None else 1.1 + (t_spin or 0.0) * 2.6
    base = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    _draw_starfield(base, STARS, star_t)
    out = base.convert("RGB")

    shapes = []
    word_alpha = 0.0
    blob_alpha = 1.0
    if t_intro is not None:
        for kind, dx, dy, colour, phase in MARK:
            s = spring(t_intro - phase)
            if s <= 0.001:
                continue
            rot = (1 - min(1.0, s)) * 1.2
            shapes.append((kind, cx + dx * cell, cy + dy * cell, r0 * min(1.0, s), colour, rot))
        word_alpha = ease((t_intro - 0.6) / 0.5)
    elif t_spin is not None:
        ORB = 0.55                                        # orbit, then dissolve into the vortex
        sp = min(1.0, t_spin / ORB)
        blob_alpha = 1.0 if t_spin < ORB else max(0.0, 1.0 - (t_spin - ORB) / 0.12)
        if blob_alpha > 0.003:
            for kind, dx, dy, colour, phase in MARK:
                ang0 = math.atan2(dy, dx)
                ang = ang0 + 2 * math.pi * 2.0 * sp       # Pixel-style: orbit upright, no self-spin
                rad = math.hypot(dx, dy)
                shapes.append((kind, cx + cell * rad * math.cos(ang),
                               cy + cell * rad * math.sin(ang), r0, colour, 0.0))

    if shapes:
        blobs = render((w, h), "#000000", shapes)         # light tonal blobs; screen keeps them bright
        if blob_alpha < 1.0:
            blobs = Image.eval(blobs, lambda v: int(v * blob_alpha))
        out = ImageChops.screen(out, blobs)

    if t_spin is not None:
        vp = (t_spin - 0.55) / 0.45
        if vp > -0.12:
            out = _screen_over(out, _draw_vortex((w, h), VORTEX, max(0.0, min(1.0, vp)), cx, cy))

    if word_alpha > 0:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        bbox = d.textbbox((0, 0), "Experience", font=font)
        d.text(((w - (bbox[2] - bbox[0])) / 2 - bbox[0], h * 0.74), "Experience", font=font,
               fill=rgb(INDIGO[90]) + (int(255 * word_alpha),))
        out = Image.alpha_composite(out.convert("RGBA"), layer).convert("RGB")
    return out



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
    """The big gradient 'UI' logo with EXPERIENCE above it, over a dim starfield,
    with a lens-flare burst on reveal and a particle wave drifting beneath."""
    w = h = size
    star_t = (t_reveal or 0.0) if t_reveal is not None else 1.0 + (t_loop or 0.0)
    base = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    _draw_starfield(base, STARS, star_t, dim=0.7)
    out = base.convert("RGB")

    if t_reveal is not None:                              # lens-flare bursts, then fades
        fl = math.sin(math.pi * min(1.0, t_reveal / 0.6)) if t_reveal < 0.6 \
            else max(0.0, 1 - (t_reveal - 0.6) / 0.5)
        if fl > 0:
            out = _screen_over(out, _draw_lensflare((w, h), 0.5, 0.5, fl))
    img = out.convert("RGBA")

    scale, alpha, shift, label_alpha = 1.0, 1.0, 0.0, 1.0
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

    if alpha > 0.001:
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
                   fill=rgb("#F0E6FF") + (int(255 * min(1.0, label_alpha)),))
            img = Image.alpha_composite(img, layer)

    wstr = (ease((t_reveal - 0.5) / 0.5) * 0.8 if t_reveal is not None else
            0.8 if t_loop is not None else (1 - ease(t_out)) * 0.8)
    if wstr > 0.001:
        img = _screen_over(img.convert("RGB"),
                           _draw_wave((w, h), WAVE, t_loop or 0.0, 0.62, wstr)).convert("RGBA")
    return img.convert("RGB")


# ---------------------------------------------------------------------------
# Boot animation — Android-TV-structured reveal (landscape).
#   part0  "powered by Experience UI" wordmark, held a while
#   part1  the wordmark dissolves and the four Experience blobs fly out
#   part2  the blobs drift across a wide horizontal band (seamless loading loop)
#   part3  the blobs gather; top & bottom pairs merge into a horizontal pink->
#          purple gradient bar, which reveals the "Experience UI" logo
#   part4  the logo settles and holds
# The wordmark/logo use Poppins (an OFL geometric face close to the Android TV
# wordmark's Product Sans); see scripts/rom/fonts/.
# ---------------------------------------------------------------------------
BT_SS = 2
BT_CELL, BT_R0 = 0.085, 0.072        # 2x2 half-spacing / blob radius (unit = H)
_BT_BLOBS = [("circle", rgb(INDIGO[80]), (-1, -1)),
             ("squircle", rgb(VIOLET[80]), (1, -1)),
             ("cookie", rgb(TEAL[80]), (-1, 1)),
             ("flower", rgb(CORAL[80]), (1, 1))]
_BT_LOOP = [  # home-x offset (frac of W), Ax, Ay, fx, fy, phase-x, phase-y
    (-0.26, 0.05, 0.12, 1, 2, 0.00, 0.10),
    (-0.09, 0.06, 0.14, 2, 1, 0.30, 0.55),
    (0.09, 0.06, 0.13, 1, 2, 0.60, 0.25),
    (0.26, 0.05, 0.11, 2, 1, 0.85, 0.70),
]


def _bt_loop_pos(i, lp):
    hx, ax, ay, fx, fy, phx, phy = _BT_LOOP[i]
    px = 0.5 + hx + ax * math.sin(2 * math.pi * (fx * lp + phx))
    py = 0.5 + ay * math.sin(2 * math.pi * (fy * lp + phy))
    r = BT_R0 * (1.0 + 0.06 * math.sin(2 * math.pi * (lp + i / 4.0)))
    return px, py, r


def _bt_hgrad(w, h, stops):
    cols = [rgb(c) for c in stops]
    img = Image.new("RGB", (w, 1))
    n = len(cols) - 1
    for x in range(w):
        f = (x / (w - 1)) * n
        i = min(int(f), n - 1)
        img.putpixel((x, 0), _lerp(cols[i], cols[i + 1], f - i))
    return img.resize((w, h))


def _bt_grad_text(size, text, font, stops):
    """RGBA of `text`, centred, filled with a horizontal pink->purple gradient."""
    w, h = size[0] * BT_SS, size[1] * BT_SS
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    bb = d.textbbox((0, 0), text, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text(((w - tw) / 2 - bb[0], (h - th) / 2 - bb[1]), text, font=font, fill=255)
    grad = _bt_hgrad(w, h, stops).convert("RGBA")
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out.resize(size, Image.LANCZOS), (tw / BT_SS, th / BT_SS)


def _bt_draw_blobs(size, placements):
    w, h = size
    layer = Image.new("RGBA", (w * BT_SS, h * BT_SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    unit = h * BT_SS
    for kind, (px, py), r, colour, alpha in placements:
        if r <= 0 or alpha <= 0.003:
            continue
        d.polygon(shape_points(kind, px * w * BT_SS, py * h * BT_SS, r * unit),
                  fill=tuple(colour) + (int(255 * min(1.0, max(0.0, alpha))),))
    return layer.resize((w, h), Image.LANCZOS)


def _bt_wordmark(size, fonts, alpha):
    w, h = size
    reg, med, _ = fonts
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    a = int(255 * min(1.0, max(0.0, alpha)))
    bb1 = d.textbbox((0, 0), "powered by", font=reg)
    d.text(((w - (bb1[2] - bb1[0])) / 2 - bb1[0], h * 0.40), "powered by", font=reg, fill=(170, 176, 200, a))
    bb2 = d.textbbox((0, 0), "Experience UI", font=med)
    d.text(((w - (bb2[2] - bb2[0])) / 2 - bb2[0], h * 0.47), "Experience UI", font=med, fill=(245, 245, 250, a))
    return img


def _bt_part0(size, fonts, a):
    base = Image.new("RGBA", size, (0, 0, 0, 255))
    base.alpha_composite(_bt_wordmark(size, fonts, ease(a)))
    return base.convert("RGB")


def _bt_part1(size, fonts, p):
    base = Image.new("RGBA", size, (0, 0, 0, 255))
    wa = 1 - ease(min(1.0, p / 0.5))
    if wa > 0.003:
        base.alpha_composite(_bt_wordmark(size, fonts, wa))
    sp = spring(p)
    placements = []
    for i, (kind, col, _) in enumerate(_BT_BLOBS):
        hx, hy, _r = _bt_loop_pos(i, 0.0)
        placements.append((kind, (0.5 + (hx - 0.5) * ease(p), 0.5 + (hy - 0.5) * ease(p)),
                           BT_R0 * (0.15 + 0.85 * sp), col, ease(min(1.0, p / 0.4))))
    base.alpha_composite(_bt_draw_blobs(size, placements))
    return base.convert("RGB")


def _bt_part2(size, fonts, lp):
    base = Image.new("RGBA", size, (0, 0, 0, 255))
    placements = []
    for i, (kind, col, _) in enumerate(_BT_BLOBS):
        px, py, r = _bt_loop_pos(i, lp)
        placements.append((kind, (px, py), r, col, 1.0))
    base.alpha_composite(_bt_draw_blobs(size, placements))
    return base.convert("RGB")


def _bt_part3(size, fonts, p):
    w, h = size
    base = Image.new("RGBA", size, (0, 0, 0, 255))
    logo, (lw, lh) = _bt_grad_text(size, "Experience UI", fonts[2], UI_GRADIENT)
    pill_hw = lw / 2 + 0.03 * w
    pill_hh = BT_R0 * h * 0.82
    gather = ease(min(1.0, p / 0.32))
    collapse = ease(max(0.0, (p - 0.30) / 0.34))
    reveal = ease(max(0.0, (p - 0.62) / 0.38))
    if collapse < 0.999:
        placements = []
        for i, (kind, col, (dx, dy)) in enumerate(_BT_BLOBS):
            hx, hy, _r = _bt_loop_pos(i, 0.0)
            gx, gy = 0.5 + dx * BT_CELL * (h / w), 0.5 + dy * BT_CELL
            px, py = hx + (gx - hx) * gather, hy + (gy - hy) * gather
            px += (0.5 + dx * (pill_hw / w) * 0.72 - px) * collapse
            py += (0.5 - py) * collapse
            placements.append((kind, (px, py), BT_R0 * (1 - 0.1 * collapse),
                               _lerp(col, rgb("#E866D0"), collapse), 1 - collapse))
        base.alpha_composite(_bt_draw_blobs(size, placements))
    if collapse > 0.01:
        pw = max(2, int(pill_hh * 1.6 + (pill_hw - pill_hh * 1.6) * collapse))
        strip = _bt_hgrad(pw * 2, int(pill_hh * 2), UI_GRADIENT).convert("RGBA")
        m = Image.new("L", strip.size, 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, strip.size[0] - 1, strip.size[1] - 1],
                                            radius=int(pill_hh), fill=int(255 * collapse * (1 - reveal)))
        strip.putalpha(m)
        pill = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        pill.alpha_composite(strip, (int(w / 2 - pw), int(h / 2 - pill_hh)))
        base.alpha_composite(pill.filter(ImageFilter.GaussianBlur(1)))
    if reveal > 0.01:
        lg = logo.copy()
        lg.putalpha(logo.split()[3].point(lambda v: int(v * reveal)))
        base.alpha_composite(lg)
    return base.convert("RGB")


def _bt_part4(size, fonts):
    base = Image.new("RGBA", size, (0, 0, 0, 255))
    logo, _ = _bt_grad_text(size, "Experience UI", fonts[2], UI_GRADIENT)
    base.alpha_composite(logo)
    return base.convert("RGB")


def make_bootanimation(vendor, preview_dir, font_path):
    W, H, fps = 1280, 720, 30
    fdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts")

    def _pop(name, px):
        p = os.path.join(fdir, name)
        return ImageFont.truetype(p if os.path.exists(p) else font_path, px)
    fonts = (_pop("Poppins-Regular.ttf", int(0.030 * H)),
             _pop("Poppins-Medium.ttf", int(0.065 * H)),
             _pop("Poppins-SemiBold.ttf", int(0.11 * H)))
    size = (W, H)
    n0, hold0 = int(0.5 * fps), 60       # wordmark fade-in frames; desc pause holds it
    n1, n2, n3, n4 = int(1.0 * fps), int(3.0 * fps), int(2.1 * fps), int(0.9 * fps)
    parts = {
        "part0": [_bt_part0(size, fonts, i / (n0 - 1)) for i in range(n0)],
        "part1": [_bt_part1(size, fonts, i / n1) for i in range(n1)],
        "part2": [_bt_part2(size, fonts, i / n2) for i in range(n2)],
        "part3": [_bt_part3(size, fonts, i / n3) for i in range(n3)],
        "part4": [_bt_part4(size, fonts) for _ in range(n4)],
    }
    out_dir = os.path.join(vendor, "bootanimation")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "bootanimation.zip")
    desc = (f"{W} {H} {fps}\n"
            f"c 1 {hold0} part0\n"   # 'powered by Experience UI', held a while
            "c 1 0 part1\n"          # breaks into the four blobs
            "c 0 0 part2\n"          # blobs float (loops until boot completes)
            "c 1 0 part3\n"          # pairs merge into the pink gradient, logo reveals
            "c 1 0 part4\n")         # logo settles and holds
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as z:
        info = zipfile.ZipInfo("desc.txt", date_time=(2026, 1, 1, 0, 0, 0))
        z.writestr(info, desc)
        for part, frames in parts.items():
            for i, frame in enumerate(frames):
                buf = io.BytesIO()
                frame.quantize(colors=256, method=Image.MEDIANCUT, dither=Image.NONE).save(buf, "PNG", optimize=True)
                info = zipfile.ZipInfo(f"{part}/{i:05d}.png", date_time=(2026, 1, 1, 0, 0, 0))
                z.writestr(info, buf.getvalue())
    if preview_dir:
        picks = [parts["part0"][-1], parts["part2"][len(parts["part2"]) // 3],
                 parts["part3"][int(len(parts["part3"]) * 0.78)], parts["part4"][0]]
        tw, th = W // 4, H // 4
        strip = Image.new("RGB", (tw * 4, th), "black")
        for i, f in enumerate(picks):
            strip.paste(f.resize((tw, th), Image.LANCZOS), (i * tw, 0))
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
