"""Turns the designed icon (tools/design/inventoria-icon-source.svg, a traced vector) into the
app's launcher and splash drawables, and the legacy launcher webps.

    python tools/svg_to_vector.py

Writes:
  app/src/main/res/drawable/ic_launcher_foreground.xml  adaptive-icon art layer (tile scaled to 0.9
                                                        about the centre so the clock clears the
                                                        launcher mask and the wrist runs off it)
  app/src/main/res/drawable/ic_inventoria_logo.xml      splash logo: the whole tile, corners rounded
                                                        by a clip-path inside the vector
  tools/design/inventoria-icon-clean.svg                the source minus its corner frames, with a
                                                        viewBox and rounded clip -- what to rasterise
  app/src/main/res/mipmap-*/ic_launcher*.webp           only if tools/design/inventoria-icon-512.png
                                                        exists (a browser render of the clean SVG);
                                                        square keeps its rounded corners, round is
                                                        circle-masked

The traced SVG paints two full-canvas "frame" paths over the corners (the outside of the rounded
tile, one purple, one black). Those are dropped: the app rounds its own corners, and a launcher
mask would otherwise show black slivers.

The monochrome (themed-icon) layer is NOT produced here: a 289-path trace tints to mush. It stays
the flat clock-in-hand from tools/make_logo.py.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
DESIGN = os.path.join(HERE, "design")
SOURCE = os.path.join(DESIGN, "inventoria-icon-source.svg")
RASTER = os.path.join(DESIGN, "inventoria-icon-512.png")

CORNER_RADIUS_FRACTION = 0.20   # of the tile edge; matches the design's rounded square
BACKGROUND = "#8F4AE2"           # the tile's flat base colour, also ic_launcher_background


def load_paths():
    svg = open(SOURCE, encoding="utf-8").read()
    size = float(re.search(r'width="([\d.]+)"', svg).group(1))
    paths = []
    for attrs in re.findall(r"<path\s+([^>]*?)/>", svg, flags=re.S):
        d = re.search(r'd="([^"]*)"', attrs).group(1).strip()
        fill = re.search(r'fill="([^"]*)"', attrs).group(1)
        m = re.search(r"translate\(([-\d.]+),([-\d.]+)\)", attrs)
        tx, ty = (float(m.group(1)), float(m.group(2))) if m else (0.0, 0.0)
        paths.append((d, fill, tx, ty))
    return size, paths


def is_corner_frame(d, tx, ty, size):
    """A path that starts at the canvas origin with translate(0,0), spans the whole canvas and has
    a second subpath: the tracer's 'everything outside the rounded tile' shape."""
    if tx or ty:
        return False
    starts_at_origin = d.startswith("M0 0 ")
    nums = [float(x) for x in re.findall(r"-?\d+\.?\d*(?:e-?\d+)?", d)]
    spans = max(nums[0::2]) >= size - 1 and max(nums[1::2]) >= size - 1
    return starts_at_origin and spans and d.count("Z") >= 2


def rounded_rect_path(size, r):
    s = size
    return (f"M {r} 0 L {s - r} 0 A {r} {r} 0 0 1 {s} {r} L {s} {s - r} A {r} {r} 0 0 1 {s - r} {s} "
            f"L {r} {s} A {r} {r} 0 0 1 0 {s - r} L 0 {r} A {r} {r} 0 0 1 {r} 0 Z")


def vector_body(paths, indent):
    lines = []
    for d, fill, tx, ty in paths:
        if tx or ty:
            lines.append(f'{indent}<group android:translateX="{tx:g}" android:translateY="{ty:g}">')
            lines.append(f'{indent}    <path android:fillColor="{fill}" android:pathData="{d}"/>')
            lines.append(f"{indent}</group>")
        else:
            lines.append(f'{indent}<path android:fillColor="{fill}" android:pathData="{d}"/>')
    return "\n".join(lines)


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    size, all_paths = load_paths()
    paths = [p for p in all_paths if not is_corner_frame(p[0], p[2], p[3], size)]
    dropped = len(all_paths) - len(paths)
    print(f"{len(all_paths)} paths, dropped {dropped} corner frame(s)")
    r = size * CORNER_RADIUS_FRACTION
    s = f"{size:g}"

    # Clean SVG for rasterising (browser or any SVG tool).
    svg_paths = []
    for d, fill, tx, ty in paths:
        tr = f' transform="translate({tx:g},{ty:g})"' if (tx or ty) else ""
        svg_paths.append(f'<path d="{d}" fill="{fill}"{tr}/>')
    clean = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {s} {s}" width="{s}" height="{s}">\n'
             f'<defs><clipPath id="tile"><path d="{rounded_rect_path(size, r)}"/></clipPath></defs>\n'
             f'<g clip-path="url(#tile)">\n' + "\n".join(svg_paths) + "\n</g>\n</svg>\n")
    write(os.path.join(DESIGN, "inventoria-icon-clean.svg"), clean)

    header = ('<?xml version="1.0" encoding="utf-8"?>\n'
              "<!-- Generated by tools/svg_to_vector.py from tools/design/inventoria-icon-source.svg.\n"
              "     Edit the SVG (or the script), not this file. -->\n")

    # Adaptive foreground. Launchers show about the central 72dp of the 108dp canvas; at 0.9 the
    # clock's top (14% down the tile) lands at ~19dp, just inside the 18dp mask edge, and the tile
    # still covers the whole visible circle so ic_launcher_background never peeks through.
    foreground = header + f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="{s}"
    android:viewportHeight="{s}">
    <group
        android:pivotX="{size / 2:g}"
        android:pivotY="{size / 2:g}"
        android:scaleX="0.9"
        android:scaleY="0.9">
{vector_body(paths, "        ")}
    </group>
</vector>
'''
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), foreground)

    # Splash logo: the whole tile with rounded corners baked in, so it draws correctly both from
    # Compose and from the pre-Compose window background (splash_background.xml).
    logo = header + f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="{s}"
    android:viewportHeight="{s}">
    <clip-path android:pathData="{rounded_rect_path(size, r)}"/>
{vector_body(paths, "    ")}
</vector>
'''
    write(os.path.join(RES, "drawable", "ic_inventoria_logo.xml"), logo)

    # Legacy launcher webps, from a browser render of the clean SVG when one has been saved.
    if os.path.exists(RASTER):
        from PIL import Image, ImageDraw
        src = Image.open(RASTER).convert("RGBA")
        for folder, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            d = os.path.join(RES, f"mipmap-{folder}")
            # Square: legacy icons carry ~8% padding, and the clean render already has rounded
            # transparent corners.
            inner = round(px * 0.84)
            square = Image.new("RGBA", (px, px), (0, 0, 0, 0))
            square.paste(src.resize((inner, inner), Image.LANCZOS), ((px - inner) // 2, (px - inner) // 2))
            square.save(os.path.join(d, "ic_launcher.webp"), "WEBP", lossless=True)
            # Round: circle-masked, full size.
            big = src.resize((px * 4, px * 4), Image.LANCZOS)
            mask = Image.new("L", big.size, 0)
            ImageDraw.Draw(mask).ellipse([0, 0, big.size[0] - 1, big.size[1] - 1], fill=255)
            rnd = Image.new("RGBA", big.size, (0, 0, 0, 0))
            rnd.paste(big, (0, 0), mask)
            rnd.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_round.webp"), "WEBP", lossless=True)
            print("wrote", f"mipmap-{folder}")
    else:
        print(f"no {os.path.relpath(RASTER, ROOT)}: legacy webps left as they are "
              "(render inventoria-icon-clean.svg at 512px and save it there, then rerun)")


if __name__ == "__main__":
    main()
