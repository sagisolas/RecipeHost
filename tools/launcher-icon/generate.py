#!/usr/bin/env python3
"""Generate the Android launcher icon: a beacon with a light-blue beam in the centre and four item icons around it.

The four items are pulled from a catalog database (the app's catalog_db.db) by item id:
two at the top beside the beam, two at the bottom corners. The dirt texture from the
app's compose resources is the background layer.

    python3 tools/launcher-icon/generate.py --db catalog_db.db \
        minecraft:gold_block minecraft:diamond minecraft:amethyst_cluster@28 minecraft:apple \
        --preview /tmp/icon-preview.png

Items are given as top-left, top-right, bottom-left, bottom-right. Append @<px> to draw an
item smaller than its 32 px sprite (nearest-neighbour, centred in its cell).

Writes into androidApp/src/main/res for every density:
  mipmap-*/ic_launcher_background.webp   dirt, lossless
  mipmap-*/ic_launcher_foreground.webp   beacon, beam, items, lossless
  mipmap-*/ic_launcher_monochrome.webp   silhouette for themed icons (Android 13+)
  mipmap-*/ic_launcher.png, ic_launcher_round.png   legacy icons (API < 26)

Needs ImageMagick 7 (`magick`) on PATH. No Python packages beyond the standard library.
"""

import argparse
import math
import shutil
import sqlite3
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_RES = REPO / "androidApp/src/main/res"
DIRT = REPO / "shared/src/commonMain/composeResources/drawable/mc_dirt_background.png"
BEACON_ID = "minecraft:beacon"

# Layout, in "art px" on a 216x216 canvas (1 art px = 0.5 dp before the final crop).
CANVAS = 216
CELL = 32
BEACON_DY = 3  # beacon centre below the canvas centre
TOP_DX, TOP_DY = 27, 30  # top items: offset of their centres from the beam axis / above centre
BOTTOM_DX, BOTTOM_DY = 33, 28  # bottom items: offset of their centres from centre
GLOW_RADIUS = 26
OUTLINE = "#140c06"
BEAM_EDGE, BEAM_BODY, BEAM_CORE = "#3fb6e8", "#8fdcff", "#e6fbff"

# Everything but the beam must fit this radius (dp) from the layer centre: the 66 dp safe zone, with a little slack.
SAFE_RADIUS_DP = 32.5
MIN_CROP = 204  # crop of the canvas that spans the 108 dp layer; larger crop = smaller art

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
WEBP = ["-define", "webp:lossless=true", "-define", "webp:exact=true"]


def magick(*args):
    subprocess.run(["magick", *map(str, args)], check=True)


def magick_out(*args):
    return subprocess.run(["magick", *map(str, args)], check=True, capture_output=True, text=True).stdout


def parse_item(spec):
    item_id, _, size = spec.partition("@")
    size = int(size) if size else CELL
    if not 1 <= size <= CELL:
        sys.exit(f"{spec}: size must be 1..{CELL}")
    return item_id, size


def extract_icon(con, item_id, work):
    row = con.execute(
        "SELECT atlas_id, cell, columns, cell_size FROM item_with_icon "
        "WHERE item_id = ? AND atlas_id IS NOT NULL ORDER BY release_id DESC LIMIT 1",
        (item_id,),
    ).fetchone()
    if row is None:
        sys.exit(f"{item_id}: no item with an icon in the database")
    atlas_id, cell, columns, cell_size = row
    atlas = work / f"atlas_{atlas_id}.png"
    if not atlas.exists():
        atlas.write_bytes(con.execute("SELECT png FROM icon_atlas WHERE id = ?", (atlas_id,)).fetchone()[0])
    out = work / (item_id.replace(":", "__").replace("/", "_") + ".png")
    x, y = (cell % columns) * cell_size, (cell // columns) * cell_size
    magick(atlas, "-crop", f"{cell_size}x{cell_size}+{x}+{y}", "+repage", "-filter", "point", "-resize", f"{CELL}x{CELL}", out)
    return out


def fit_in_cell(icon, size, work):
    if size == CELL:
        return icon
    out = work / f"{icon.stem}_{size}.png"
    magick(icon, "-filter", "point", "-resize", f"{size}x{size}", "-background", "none",
           "-gravity", "center", "-extent", f"{CELL}x{CELL}", out)
    return out


def max_radius(image):
    """Distance from the canvas centre to the farthest opaque pixel of the image, outline included, in art px."""
    txt = magick_out(image, "-alpha", "extract", "-threshold", "50%", "-morphology", "Dilate", "Diamond:1",
                     "-depth", "8", "txt:-")
    centre = (CANVAS - 1) / 2
    radius = 0.0
    for line in txt.splitlines()[1:]:
        if "#FFFFFF" in line:
            x, y = map(int, line.split(":")[0].split(","))
            radius = max(radius, math.hypot(x - centre, y - centre))
    return radius


def compose(icons, beacon, work):
    """Draw the foreground on the art canvas; returns (foreground, monochrome, radius of non-beam art)."""
    c = CANVAS // 2
    half = CELL // 2
    beacon_top = c + BEACON_DY - half
    first_row = int(magick_out(beacon, "-alpha", "extract", "-threshold", "50%", "-trim", "-format", "%Y", "info:")
                    .strip().lstrip("+"))
    beam_bottom = beacon_top + first_row + 4  # start slightly inside the beacon's top face

    beam = work / "beam.png"
    magick("-size", f"{CANVAS}x{CANVAS}", "xc:none", "+antialias", "-stroke", "none",
           "-fill", BEAM_EDGE, "-draw", f"rectangle {c - 4},0 {c + 3},{beam_bottom}",
           "-fill", BEAM_BODY, "-draw", f"rectangle {c - 3},0 {c + 2},{beam_bottom}",
           "-fill", BEAM_CORE, "-draw", f"rectangle {c - 1},0 {c},{beam_bottom}",
           beam)

    tl, tr, bl, br = icons
    items = work / "items.png"
    magick("-size", f"{CANVAS}x{CANVAS}", "xc:none",
           tl, "-geometry", f"+{c - TOP_DX - half}+{c - TOP_DY - half}", "-composite",
           tr, "-geometry", f"+{c + TOP_DX - half}+{c - TOP_DY - half}", "-composite",
           bl, "-geometry", f"+{c - BOTTOM_DX - half}+{c + BOTTOM_DY - half}", "-composite",
           br, "-geometry", f"+{c + BOTTOM_DX - half}+{c + BOTTOM_DY - half}", "-composite",
           beacon, "-geometry", f"+{c - half}+{beacon_top}", "-composite",
           items)

    art = work / "art.png"
    magick(beam, items, "-composite", art)
    outline = work / "outline.png"
    magick(art, "-alpha", "extract", "-threshold", "50%", "-morphology", "Dilate", "Diamond:1",
           "-background", OUTLINE, "-alpha", "shape", outline)
    sharp = work / "sharp.png"
    magick(outline, art, "-composite", sharp)

    glow = work / "glow.png"
    magick("-size", f"{CANVAS}x{CANVAS}", "xc:none", "-fill", BEAM_BODY,
           "-draw", f"circle {c},{c + BEACON_DY} {c + GLOW_RADIUS},{c + BEACON_DY}",
           "-channel", "A", "-evaluate", "multiply", "0.55", "+channel", "-blur", "0x9", glow)
    foreground = work / "foreground.png"
    magick(glow, sharp, "-composite", foreground)

    mono = work / "mono.png"
    magick(sharp, "-alpha", "extract", "-threshold", "50%", "-background", "white", "-alpha", "shape", mono)

    return foreground, mono, max_radius(items)


def export(foreground, mono, crop, res, work):
    offset = (CANVAS - crop) // 2
    fg, mn = work / "fg_crop.png", work / "mono_crop.png"
    magick(foreground, "-crop", f"{crop}x{crop}+{offset}+{offset}", "+repage", fg)
    magick(mono, "-crop", f"{crop}x{crop}+{offset}+{offset}", "+repage", mn)

    for density, scale in DENSITIES.items():
        layer = round(108 * scale)
        legacy = round(48 * scale)
        d = res / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        bg_out, fg_out = d / "ic_launcher_background.webp", d / "ic_launcher_foreground.webp"
        magick(DIRT, "-filter", "point", "-resize", f"{layer}x{layer}!", *WEBP, bg_out)
        for src, dst in ((fg, fg_out), (mn, d / "ic_launcher_monochrome.webp")):
            magick(src, "-filter", "point", "-resize", "800%", "-filter", "Lanczos",
                   "-resize", f"{layer}x{layer}!", *WEBP, dst)

        # Legacy icons: the visible 72 dp of the layers, as a rounded square and a circle.
        visible = layer * 2 // 3
        vo = (layer - visible) // 2
        flat = work / f"flat_{density}.png"
        magick(bg_out, fg_out, "-composite", "-crop", f"{visible}x{visible}+{vo}+{vo}", "+repage",
               "-resize", f"{legacy}x{legacy}!", flat)
        r = legacy // 6
        magick(flat, "(", "-size", f"{legacy}x{legacy}", "xc:none", "-fill", "white",
               "-draw", f"roundrectangle 0,0 {legacy - 1},{legacy - 1} {r},{r}", ")",
               "-compose", "DstIn", "-composite", d / "ic_launcher.png")
        magick(flat, "(", "-size", f"{legacy}x{legacy}", "xc:none", "-fill", "white",
               "-draw", f"circle {legacy // 2},{legacy // 2} {legacy // 2},0", ")",
               "-compose", "DstIn", "-composite", d / "ic_launcher_round.png")


def preview(res, out, work):
    """Sheet: full layers with safe zone, circle and squircle masks, themed icon, legacy icon."""
    d = res / "mipmap-xxxhdpi"
    full = work / "full.png"
    magick(d / "ic_launcher_background.webp", d / "ic_launcher_foreground.webp", "-composite", full)

    def masked(src, mask_draw, dst):
        magick(src, "-crop", "288x288+72+72", "+repage",
               "(", "-size", "288x288", "xc:none", "-fill", "white", "-draw", mask_draw, ")",
               "-compose", "DstIn", "-composite", "-background", "#202020", "-compose", "over", "-flatten", dst)

    p1, p2, p3, p4, p5 = (work / f"p{i}.png" for i in range(1, 6))
    magick(full, "-fill", "none", "-stroke", "#00ff88", "-strokewidth", "2", "-draw", "circle 216,216 216,84",
           "-stroke", "#ff00aa", "-draw", "rectangle 72,72 360,360", p1)
    masked(full, "circle 144,144 144,0", p2)
    masked(full, "roundrectangle 0,0 287,287 90,90", p3)
    themed = work / "themed.png"
    magick("-size", "432x432", "xc:#d9e7c9",
           "(", d / "ic_launcher_monochrome.webp", "-fill", "#2c4a14", "-colorize", "100", ")", "-composite", themed)
    masked(themed, "circle 144,144 144,0", p4)
    magick(d / "ic_launcher.png", "-background", "#202020", "-gravity", "center", "-extent", "288x288", p5)
    magick(p1, "(", p2, p3, "-append", ")", "(", p4, p5, "-append", ")", "-background", "#202020",
           "-gravity", "center", "+append", "-bordercolor", "#202020", "-border", "12", out)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db", required=True, type=Path, help="catalog database to pull item icons from")
    parser.add_argument("items", nargs=4, metavar="ITEM[@PX]",
                        help="item ids: top-left top-right bottom-left bottom-right")
    parser.add_argument("--res", type=Path, default=DEFAULT_RES, help=f"Android res dir (default: {DEFAULT_RES})")
    parser.add_argument("--preview", type=Path, help="also write a preview sheet with launcher masks here")
    args = parser.parse_args()

    if shutil.which("magick") is None:
        sys.exit("ImageMagick 7 (`magick`) is required")
    if not args.db.exists():
        sys.exit(f"{args.db}: not found")

    specs = [parse_item(s) for s in args.items]
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
        icons = [fit_in_cell(extract_icon(con, item_id, work), size, work) for item_id, size in specs]
        beacon = extract_icon(con, BEACON_ID, work)
        con.close()

        foreground, mono, radius = compose(icons, beacon, work)
        crop = max(MIN_CROP, round(radius * 108 / SAFE_RADIUS_DP))
        if crop > CANVAS:
            print(f"warning: items reach {radius:.1f} art px from the centre; they will poke out of the safe zone",
                  file=sys.stderr)
            crop = CANVAS
        export(foreground, mono, crop, args.res, work)
        print(f"wrote launcher icons to {args.res} (crop {crop})")
        if args.preview:
            preview(args.res, args.preview, work)
            print(f"wrote preview to {args.preview}")


if __name__ == "__main__":
    main()
