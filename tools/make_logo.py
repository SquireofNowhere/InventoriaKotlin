"""Generates the Inventoria mark: a hand rising from the bottom, gripping a clock whose hands make
a check mark. "Take a hold of your life."

Writes the three vector drawables (adaptive foreground, monochrome layer, splash logo) and the
legacy webp launcher icons for API 24/25 devices, all from one parametric description, so the
launcher, the themed icon and the splash never drift apart. Run from anywhere:

    python tools/make_logo.py

The geometry is expressed relative to the clock (centre cx,cy and radius R) so the same art can be
laid out for the adaptive icon's tight safe zone and for the full-size splash logo.
"""
import math
import os
from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")

RING = "#ffffff"          # white ring separates the face from the purple gradient behind it
FACE = "#ede9fe"          # faint lavender face
TICK = "#a855f7"          # purple_secondary_dark
HANDS = "#7c3aed"         # purple_primary_dark
PIVOT = "#e879f9"         # purple_accent
PALM = "#4c1d95"          # deep purple (the old logo's inner_detail), the hand's silhouette
FINGER = "#6d28d9"        # two shades lighter so fingers read against the palm where they overlap


def art(cx, cy, R):
    """The whole mark as a flat list of primitives in draw order (back to front).

    ('disc', x, y, r, color)                 filled circle
    ('seg',  x0, y0, x1, y1, w, color)       thick line with round caps
    ('rrect', x0, y0, x1, y1, radius, color) rounded rectangle
    """
    p = []
    # Palm and wrist first: they sit behind the clock, and the wrist runs off the bottom edge so
    # the hand reads as coming from below the icon rather than floating in it.
    p.append(("rrect", cx - 0.60 * R, cy + 0.85 * R, cx + 0.60 * R, cy + 3.0 * R, 0.40 * R, PALM))
    # Clock.
    p.append(("disc", cx, cy, R, RING))
    p.append(("disc", cx, cy, R * 0.833, FACE))
    for h in range(12):
        a = math.radians(h * 30)
        major = h % 3 == 0
        r0 = R * (0.62 if major else 0.68)
        r1 = R * 0.76
        p.append(("seg", cx + r0 * math.sin(a), cy - r0 * math.cos(a),
                  cx + r1 * math.sin(a), cy - r1 * math.cos(a), R * 0.07, TICK))
    # Check-mark hands: short hand in from upper-left, long hand out to upper-right.
    p.append(("seg", cx - 0.28 * R, cy - 0.28 * R, cx, cy, R * 0.16, HANDS))
    p.append(("seg", cx, cy, cx + 0.48 * R, cy - 0.48 * R, R * 0.16, HANDS))
    p.append(("disc", cx, cy, R * 0.1, PIVOT))
    # Fingers wrap the lower part of the clock from the left, each a band that sags in the middle
    # the way a finger curls round a ball; the thumb comes up the right side. Bands are 0.34R apart
    # and 0.27R thick, so a sliver of face/ring/palm shows between them.
    fw = 0.27 * R
    p.append(("qcurve", cx - 0.62 * R, cy + 0.42 * R, cx - 0.10 * R, cy + 0.64 * R, cx + 0.42 * R, cy + 0.40 * R, fw, FINGER))
    p.append(("qcurve", cx - 0.66 * R, cy + 0.76 * R, cx - 0.10 * R, cy + 0.98 * R, cx + 0.44 * R, cy + 0.74 * R, fw, FINGER))
    p.append(("qcurve", cx - 0.60 * R, cy + 1.10 * R, cx - 0.10 * R, cy + 1.30 * R, cx + 0.40 * R, cy + 1.08 * R, fw, FINGER))
    p.append(("qcurve", cx + 0.60 * R, cy + 1.22 * R, cx + 0.90 * R, cy + 0.92 * R, cx + 0.78 * R, cy + 0.30 * R, 0.30 * R, FINGER))
    return p


def qpoints(x0, y0, cx, cy, x1, y1, n=16):
    """Samples a quadratic Bezier for the raster renderer."""
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        pts.append((u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1))
    return pts


# ---- Vector drawable emission ----------------------------------------------------------------

def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def disc_path(x, y, r):
    return f"M {f(x)} {f(y - r)} A {f(r)} {f(r)} 0 1 1 {f(x)} {f(y + r)} A {f(r)} {f(r)} 0 1 1 {f(x)} {f(y - r)} Z"


def rrect_path(x0, y0, x1, y1, r):
    r = min(r, (x1 - x0) / 2, (y1 - y0) / 2)
    return (f"M {f(x0 + r)} {f(y0)} L {f(x1 - r)} {f(y0)} A {f(r)} {f(r)} 0 0 1 {f(x1)} {f(y0 + r)} "
            f"L {f(x1)} {f(y1 - r)} A {f(r)} {f(r)} 0 0 1 {f(x1 - r)} {f(y1)} L {f(x0 + r)} {f(y1)} "
            f"A {f(r)} {f(r)} 0 0 1 {f(x0)} {f(y1 - r)} L {f(x0)} {f(y0 + r)} A {f(r)} {f(r)} 0 0 1 {f(x0 + r)} {f(y0)} Z")


def vector_paths(prims, indent, color_override=None, skip_face=False):
    lines = []
    for prim in prims:
        kind = prim[0]
        color = color_override or prim[-1]
        if kind == "disc":
            _, x, y, r, c = prim
            if skip_face and c == FACE:
                continue
            lines.append(f'{indent}<path android:fillColor="{color}" android:pathData="{disc_path(x, y, r)}"/>')
        elif kind == "seg":
            _, x0, y0, x1, y1, w, c = prim
            lines.append(f'{indent}<path android:pathData="M {f(x0)} {f(y0)} L {f(x1)} {f(y1)}" '
                         f'android:strokeColor="{color}" android:strokeWidth="{f(w)}" android:strokeLineCap="round"/>')
        elif kind == "qcurve":
            _, x0, y0, qx, qy, x1, y1, w, c = prim
            lines.append(f'{indent}<path android:pathData="M {f(x0)} {f(y0)} Q {f(qx)} {f(qy)} {f(x1)} {f(y1)}" '
                         f'android:strokeColor="{color}" android:strokeWidth="{f(w)}" android:strokeLineCap="round"/>')
        elif kind == "rrect":
            _, x0, y0, x1, y1, r, c = prim
            lines.append(f'{indent}<path android:fillColor="{color}" android:pathData="{rrect_path(x0, y0, x1, y1, r)}"/>')
    return "\n".join(lines)


def vector(width_dp, body, comment):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment}
     Generated by tools/make_logo.py; edit the geometry there, not here. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width_dp}dp"
    android:height="{width_dp}dp"
    android:viewportWidth="128"
    android:viewportHeight="128">
{body}
</vector>
'''


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("wrote", os.path.relpath(path, os.path.join(RES, "..", "..", "..", "..")))


# Adaptive foreground: launchers show roughly the central 72dp of the 108dp canvas (a circle of
# radius ~43 viewport units around the centre), so the clock is kept small and high enough for its
# top to stay inside that, while the wrist runs off the bottom.
ADAPTIVE = dict(cx=64, cy=53, R=27)
# Full-size art for the splash and the legacy icons, which draw their own padding.
FULL = dict(cx=64, cy=44, R=35)

write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
      vector(108, vector_paths(art(**ADAPTIVE), "    "),
             "The app icon: a hand coming up from the bottom, gripping a clock whose hands make a check mark."))

# Monochrome layer: Android tints the alpha, so the face is left open (it would otherwise fill the
# clock into a solid disc and swallow the check). Ring, ticks, hands and the hand silhouette only.
mono_prims = [p for p in art(**ADAPTIVE) if not (p[0] == "disc" and p[-1] == FACE)]
# Turn the filled ring disc into a stroked ring so the face stays transparent.
mono_body_lines = []
for p in mono_prims:
    if p[0] == "disc" and p[-1] == RING:
        _, x, y, r, _ = p
        mono_body_lines.append(f'    <path android:pathData="{disc_path(x, y, r * 0.92)}" android:strokeColor="#ffffff" android:strokeWidth="{f(r * 0.16)}"/>')
    else:
        mono_body_lines.append(vector_paths([p], "    ", color_override="#ffffff"))
write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
      vector(108, "\n".join(mono_body_lines),
             "Themed-icon (monochrome) layer: alpha only, so the clock face is left open."))

write(os.path.join(RES, "drawable", "ic_inventoria_logo.xml"),
      vector(120, vector_paths(art(**FULL), "    "),
             "Splash / in-app logo: the same hand-and-clock as the launcher icon, at full size."))


# ---- Legacy launcher webps (pre-API-26, and launchers that ignore adaptive icons) -----------------

def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(size, c0, c1, c2):
    """135-degree three-stop gradient, matching ic_launcher_background / splash_background."""
    small = 128
    img = Image.new("RGB", (small, small))
    px = img.load()
    a, b, c = hex_rgb(c0), hex_rgb(c1), hex_rgb(c2)
    for y in range(small):
        for x in range(small):
            t = (x + y) / (2 * (small - 1))
            if t < 0.5:
                u = t / 0.5
                col = tuple(round(a[i] + (b[i] - a[i]) * u) for i in range(3))
            else:
                u = (t - 0.5) / 0.5
                col = tuple(round(b[i] + (c[i] - b[i]) * u) for i in range(3))
            px[x, y] = col
    return img.resize((size, size), Image.LANCZOS)


def draw_art(draw, prims, k, ox, oy):
    """Draws primitives with viewport->pixel scale k and offset (ox, oy)."""
    def P(x, y):
        return (ox + x * k, oy + y * k)

    def disc(x, y, r, fill):
        px, py = P(x, y)
        draw.ellipse([px - r * k, py - r * k, px + r * k, py + r * k], fill=fill)

    for prim in prims:
        kind = prim[0]
        if kind == "disc":
            _, x, y, r, c = prim
            disc(x, y, r, c)
        elif kind == "seg":
            _, x0, y0, x1, y1, w, c = prim
            draw.line([P(x0, y0), P(x1, y1)], fill=c, width=max(1, round(w * k)))
            disc(x0, y0, w / 2, c)
            disc(x1, y1, w / 2, c)
        elif kind == "qcurve":
            _, x0, y0, qx, qy, x1, y1, w, c = prim
            pts = [P(x, y) for x, y in qpoints(x0, y0, qx, qy, x1, y1)]
            draw.line(pts, fill=c, width=max(1, round(w * k)), joint="curve")
            disc(x0, y0, w / 2, c)
            disc(x1, y1, w / 2, c)
        elif kind == "rrect":
            _, x0, y0, x1, y1, r, c = prim
            (ax, ay), (bx, by) = P(x0, y0), P(x1, y1)
            draw.rounded_rectangle([ax, ay, bx, by], radius=r * k, fill=c)


def legacy_icon(size, round_shape):
    ss = 4
    big = size * ss
    bg = gradient(big, "#8b5cf6", "#a855f7", "#c084fc").convert("RGBA")
    mask = Image.new("L", (big, big), 0)
    md = ImageDraw.Draw(mask)
    if round_shape:
        inset = 0
        md.ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        # Legacy square icons carry their own padding: ~8% inset, ~18% corner radius.
        inset = round(big * 0.08)
        md.rounded_rectangle([inset, inset, big - 1 - inset, big - 1 - inset], radius=round(big * 0.18), fill=255)
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    layer.paste(bg, (0, 0), mask)
    # Draw the art onto its own layer, then clip it with the same mask so the wrist stops at the
    # icon's edge instead of poking out of the rounded square.
    artl = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    inner = big - 2 * inset
    k = inner / 128.0
    draw_art(ImageDraw.Draw(artl), art(**FULL), k, inset, inset)
    clipped = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    clipped.paste(artl, (0, 0), mask)
    out = Image.alpha_composite(layer, clipped)
    return out.resize((size, size), Image.LANCZOS)


for folder, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    d = os.path.join(RES, f"mipmap-{folder}")
    legacy_icon(size, False).save(os.path.join(d, "ic_launcher.webp"), "WEBP", lossless=True)
    legacy_icon(size, True).save(os.path.join(d, "ic_launcher_round.webp"), "WEBP", lossless=True)
    print("wrote", f"mipmap-{folder}")

# Preview PNGs for eyeballing, written next to this script (gitignored).
here = os.path.dirname(os.path.abspath(__file__))
legacy_icon(512, False).save(os.path.join(here, "preview_launcher.png"))
legacy_icon(512, True).save(os.path.join(here, "preview_round.png"))


# What a launcher shows of the adaptive icon: gradient background, foreground art, circular mask
# over the central 72/108 of the canvas.
def adaptive_preview(size):
    ss = 4
    big = size * ss
    bg = gradient(big, "#8b5cf6", "#a855f7", "#c084fc").convert("RGBA")
    artl = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_art(ImageDraw.Draw(artl), art(**ADAPTIVE), big / 128.0, 0, 0)
    full = Image.alpha_composite(bg, artl)
    mask = Image.new("L", (big, big), 0)
    r = big * 36 / 108
    ImageDraw.Draw(mask).ellipse([big / 2 - r, big / 2 - r, big / 2 + r, big / 2 + r], fill=255)
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(full, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


adaptive_preview(512).save(os.path.join(here, "preview_adaptive.png"))
print("done")
