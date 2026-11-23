#!/usr/bin/env python3
"""Reskin a TWRP theme in place: EMUI-style dark palette + Experience logo.

Only recolours theme <variable> values and regenerates image assets at their EXACT
original dimensions. It never changes layout/positions, so the recovery stays
functional. Run against an extracted .../twres directory.

  skin.py <twres_dir> <inter_semibold.otf> <inter_regular.otf>
"""
import os
import re
import sys
import math
from PIL import Image, ImageDraw, ImageFont

# EMUI-style dark surfaces with an Experience indigo-blue accent.
BG = "#0E0F13"
SURFACE = "#1B1E27"
SURFACE2 = "#232733"
ACCENT = "#5B7CFA"
ACCENT_DK = "#3B51C4"
TEXT = "#F2F3F7"
MUTED = "#8A90A6"

# theme variable -> new value (only keys that already exist are replaced)
COLORS = {
    "accent_color": ACCENT,
    "background_color": BG,
    "text_color": TEXT,
    "text_button_color": "#FFFFFF",
    "header_color": "#2A3550",
    "fileselector_highlight_color": SURFACE2,
    "fileselector_linecolor": "#2A2E3A",
    "highlight_color": "#5B7CFA40",
    "caps_highlight_color": "#5B7CFA40",
}

SS = 4
# Experience tonal shapes (same mark as the boot animation).
SHAPES = [("circle", "#BAC3FF"), ("squircle", "#DDB8FF"), ("cookie", "#53DBC9"), ("flower", "#FFB4A5")]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + ((int(h[6:8], 16),) if len(h) == 8 else ())


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


def draw_mark(size, box):
    """4-shape Experience cluster centred, fitted into a square of side `box`."""
    img = Image.new("RGBA", (size[0] * SS, size[1] * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = size[0] * SS / 2, size[1] * SS / 2
    cell = box * SS * 0.26
    r = box * SS * 0.22
    for (kind, col), (dx, dy) in zip(SHAPES, [(-1, -1), (1, -1), (-1, 1), (1, 1)]):
        d.polygon(shape_pts(kind, cx + dx * cell, cy + dy * cell, r, 0.2), fill=rgb(col))
    return img.resize(size, Image.LANCZOS)


def rounded(size, radius, fill, border=None, bw=0):
    img = Image.new("RGBA", (size[0] * SS, size[1] * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size[0] * SS - 1, size[1] * SS - 1], radius=radius * SS, fill=rgb(fill),
                        outline=rgb(border) if border else None, width=bw * SS)
    return img.resize(size, Image.LANCZOS)


def main():
    twres, f_semi, f_reg = sys.argv[1], sys.argv[2], sys.argv[3]
    imgs = os.path.join(twres, "images")

    # 1. recolour theme variables
    for xml in ("ui.xml", "landscape.xml", "splash.xml"):
        p = os.path.join(twres, xml)
        if not os.path.exists(p):
            continue
        s = open(p).read()
        for key, val in COLORS.items():
            s = re.sub(r'(<variable name="%s" value=")[^"]*(")' % re.escape(key),
                       lambda m, v=val: m.group(1) + v + m.group(2), s)
        open(p, "w").write(s)

    def size(name):
        return Image.open(os.path.join(imgs, name)).size

    def save(name, im):
        im.save(os.path.join(imgs, name))

    # 2. logos
    save("splashlogo.png", compose_splash(size("splashlogo.png"), f_semi))
    save("logo.png", draw_mark(size("logo.png"), min(size("logo.png"))))
    save("splashteamwin.png", wordmark(size("splashteamwin.png"), "EXPERIENCE UI", f_semi))

    # 3. EMUI-style controls (same sizes, rounded dark)
    for n in ("main_button.png", "main_button_half_width.png", "main_button_half_width_low.png"):
        w, h = size(n)
        save(n, rounded((w, h), min(w, h) // 2, SURFACE2, ACCENT, 2))
    for n in ("tab_3.png", "tab_4.png"):
        w, h = size(n)
        save(n, rounded((w, h), h // 2, SURFACE))
    w, h = size("slider.png"); save("slider.png", rounded((w, h), h // 2, SURFACE2))
    save("slider_used.png", rounded(size("slider_used.png"), size("slider_used.png")[1] // 2, ACCENT_DK))
    w, h = size("slider_touch.png"); save("slider_touch.png", rounded((w, h), h // 2, ACCENT))
    w, h = size("progress_empty.png"); save("progress_empty.png", rounded((w, h), h // 2, SURFACE))
    save("progress_fill.png", rounded(size("progress_fill.png"), size("progress_fill.png")[1] // 2, ACCENT))
    print("reskinned:", twres)


def compose_splash(size, font_path):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    mark = draw_mark((int(size[0] * 0.62), int(size[0] * 0.62)), int(size[0] * 0.62))
    img.alpha_composite(mark, ((size[0] - mark.width) // 2, int(size[1] * 0.06)))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(font_path, int(size[0] * 0.12))
    t = "Experience"
    bb = d.textbbox((0, 0), t, font=f)
    d.text(((size[0] - (bb[2] - bb[0])) / 2 - bb[0], int(size[1] * 0.74)), t, font=f, fill=rgb(TEXT))
    return img


def wordmark(size, text, font_path):
    img = Image.new("RGBA", (size[0] * SS, size[1] * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(font_path, int(size[1] * SS * 0.62))
    bb = d.textbbox((0, 0), text, font=f)
    d.text(((size[0] * SS - (bb[2] - bb[0])) / 2 - bb[0], (size[1] * SS - (bb[3] - bb[1])) / 2 - bb[1]),
           text, font=f, fill=rgb(TEXT))
    return img.resize(size, Image.LANCZOS)


if __name__ == "__main__":
    main()
