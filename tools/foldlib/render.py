"""Contact sheets of the fold library: every step of every model as the demo sheet shows it.

    python -I tools/foldlib/render.py <outDir> [modelId ...]

One PNG per model and paper (step start sheets with the fold line, the moving flap tinted, the landing,
then the finished sheet). Colours follow the app's palette tokens.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine as E  # noqa: E402
import models as M  # noqa: E402

PINK = (236, 184, 196)
CREAM = (251, 244, 234)
EDGE = (120, 108, 124)
HALO = (74, 68, 88)
BLUE = (127, 166, 214)
MINT = (108, 196, 161)
BG = (60, 62, 70)
CELL = 300
SCALE = 1.1


def font(size):
    for f in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def to_px(p, cx, cy, k):
    return (cx + p[0] * k, cy - p[1] * k)


def draw_sheet(d, sheet, cx, cy, k):
    for f in sorted(sheet.facets, key=lambda f: f.z):
        pts = [to_px(p, cx, cy, k) for p in f.pts]
        d.polygon(pts, fill=PINK if f.up else CREAM, outline=EDGE)
    for (a, b, _m) in sheet.creases:
        d.line([to_px(a, cx, cy, k), to_px(b, cx, cy, k)], fill=(255, 255, 255), width=1)


def dashed(d, a, b, cx, cy, k, mountain):
    pat = [6, 1.6, 1.4, 1.6] if mountain else [6, 4]
    total = math.dist(a, b)
    dx, dy = (b[0] - a[0]) / total, (b[1] - a[1]) / total
    s, i = 0.0, 0
    while s < total:
        ln = min(pat[i % len(pat)], total - s)
        if i % 2 == 0:
            p = (a[0] + dx * s, a[1] + dy * s)
            q = (a[0] + dx * (s + ln), a[1] + dy * (s + ln))
            d.line([to_px(p, cx, cy, k), to_px(q, cx, cy, k)], fill=HALO, width=4)
            d.line([to_px(p, cx, cy, k), to_px(q, cx, cy, k)], fill=(255, 255, 255), width=2)
        s += ln
        i += 1


def cell(model, sheets, i, k):
    img = Image.new("RGB", (CELL, CELL + 46), BG)
    d = ImageDraw.Draw(img, "RGBA")
    cx, cy = CELL / 2, CELL / 2 + model["outline_cy"] * k
    sheet = sheets[i]
    draw_sheet(d, sheet, cx, cy, k)
    if i < len(model["steps"]):
        st = model["steps"][i]
        if st.turns:
            for f in E.moving(sheet, st):
                d.polygon([to_px(p, cx, cy, k) for p in f.pts], fill=BLUE + (90,), outline=BLUE)
            for (a, b, m) in st.lines:
                dashed(d, a, b, cx, cy, k, m)
            for j, (a, b, m) in enumerate(st.lines):
                p = st.pick(j)
                q = E.reflect(p, a, b)
                if st.kind != "cut":
                    d.line([to_px(p, cx, cy, k), to_px(q, cx, cy, k)], fill=BLUE, width=2)
                d.ellipse([to_px(p, cx, cy, k)[0] - 3, to_px(p, cx, cy, k)[1] - 3, to_px(p, cx, cy, k)[0] + 3, to_px(p, cx, cy, k)[1] + 3], fill=BLUE)
            for l in st.landings:
                if l[0] == "at":
                    x, y = to_px(l[1], cx, cy, k)
                    d.ellipse([x - 5, y - 5, x + 5, y + 5], outline=MINT, width=3)
                else:
                    d.line([to_px(l[1], cx, cy, k), to_px(l[2], cx, cy, k)], fill=MINT, width=3)
        for dot in st.dots:
            x, y = to_px(dot, cx, cy, k)
            d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(255, 255, 255), outline=HALO)
        label = "%d %s %s" % (i + 1, st.kind, st.zh)
        label2 = st.en
    else:
        label, label2 = "done", model["resultZh"]
    d.text((6, CELL + 2), label, fill=(240, 240, 240), font=font(14))
    d.text((6, CELL + 22), label2[:44], fill=(200, 200, 200), font=font(12))
    return img


def render(model, out_dir):
    sheets = E.build(model["outline"], model["steps"], model.get("resets"))
    n = len(sheets)
    cols = min(n, 5)
    rows = (n + cols - 1) // cols
    k = SCALE * (CELL - 40) / 2 / max(abs(c) for p in sheets[0].points() for c in (p[0], p[1] - model["outline_cy"])) / SCALE
    sheet_img = Image.new("RGB", (cols * CELL, rows * (CELL + 46) + 30), (40, 42, 48))
    d = ImageDraw.Draw(sheet_img)
    d.text((8, 6), "%s  %s / %s  paper=%s  level %d" % (model["key"], model["nameZh"], model["nameEn"], model["paper"], model["level"]), fill=(255, 255, 255), font=font(16))
    for i in range(n):
        sheet_img.paste(cell(model, sheets, i, k), ((i % cols) * CELL, 30 + (i // cols) * (CELL + 46)))
    path = os.path.join(out_dir, "%s.png" % model["key"])
    sheet_img.save(path)
    return path


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    only = set(sys.argv[2:])
    for m in M.all_variants():
        if only and m["id"] not in only and m["key"] not in only:
            continue
        print(render(m, out))


if __name__ == "__main__":
    main()
