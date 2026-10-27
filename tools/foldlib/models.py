"""The fold library: every model, its steps and the paper variants (the single source of FoldLibraryData.kt).

    python -I tools/foldlib/models.py          # check every variant (landings, bounds, copy budgets)

Square models are authored on the 15 cm square (150 units wide; diamond half diagonal D = 106.1).
Rectangle models are authored on a sheet 150 units wide and 150 * h / w long (A4, Letter). A square model
on printer paper starts with the prep (fold a corner over, crease and cut the strip, unfold, turn).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine as E  # noqa: E402
from engine import D, H, Step  # noqa: E402

PAPERS = {"square": (150.0, 150.0), "a4": (210.0, 297.0), "letter": (215.9, 279.4)}
RECT_PAPERS = ("a4", "letter")
# how far a folded sheet may reach past its start outline (a dart's wing corner dips below the body)
SLACK = 20.0


def v(a, b):
    return (a, b, False)


def m(a, b):
    return (a, b, True)


def S(kind, zh, en, lines=(), picks=(), span=True, **kw):
    s = Step(kind, zh, en, lines, picks, **kw)
    s.span = span
    return s


def at(x, y):
    return ("at", (x, y))


def seg(a, b):
    return ("seg", a, b)


def ray(p, deg, length=400.0):
    """A point [length] from p in direction [deg] (0 = right, 90 = away)."""
    r = math.radians(deg)
    return (p[0] + length * math.cos(r), p[1] + length * math.sin(r))


def bisector_fold(p, q):
    """The fold line that lays point p onto point q (their perpendicular bisector)."""
    mx, my = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
    dx, dy = q[0] - p[0], q[1] - p[1]
    return (mx - dy, my + dx), (mx + dy, my - dx)


# =============================================================================================
# the four v0.1.0 models, exactly as shipped (design package `models.list`)
# =============================================================================================

def basics():
    return dict(id="basics", nameZh="基础折法", nameEn="Folding Basics", cat="basics", needs="square", pose="square", minutes=2, level=1,
                resultZh="看懂图例：虚线往前折、点划线往后折、压痕、角对点",
                resultEn="Read the symbols: dashed folds forward, dash-dot folds behind, creases, corner to point",
                steps=[
                    S("crease", "虚线 = 谷折：近边折到远边，压平再打开", "Dashed = valley: near edge to far edge, then unfold", [v((-75, 0), (75, 0))], [(0, -40)], span=False, landings=[seg((-75, 75), (75, 75))], legend="v"),
                    S("crease", "点划线 = 山折：往背后折，再打开", "Dash-dot = mountain: fold behind, then unfold", [m((0, -75), (0, 75))], [(40, 0)], span=False, landings=[seg((-75, -75), (-75, 75))], legend="m"),
                    S("crease", "对角折：右近角对到左远角", "Diagonal: near-right corner to far-left corner", [v((-75, -75), (75, 75))], [(40, -40)], span=False, landings=[at(-75, 75)], legend="v"),
                    S("fold", "角对点：左近角折到中心", "Fold the near-left corner to the centre", [v((-75, 0), (0, -75))], [(-60, -60)], span=False, landings=[at(0, 0)], legend="v"),
                ])


def dog():
    return dict(id="dog", nameZh="小狗脸", nameEn="Puppy Face", cat="animals", needs="square", pose="diamond", minutes=3, level=1,
                resultZh="三角形狗脸，两只耷拉的耳朵", resultEn="A triangle puppy face with floppy ears",
                steps=[
                    S("fold", "彩色面朝下，远角对折到近角", "Colour side down. Fold far corner to near corner", [v((-D, 0), (D, 0))], [(0, 60)], span=False, landings=[at(0, -D)]),
                    S("fold", "左角往下折，做一只耳朵", "Fold the left corner down for an ear", [v((-36, 0), (-70, -36.1))], [(-90, -5)], span=False, landings=[at(-31.8, -70)]),
                    S("fold", "右角也往下折，做另一只耳朵", "Fold the right corner down for the other ear", [v((36, 0), (70, -36.1))], [(90, -5)], span=False, landings=[at(31.8, -70)]),
                    S("fold", "下巴尖只折上面一层，往上折一点", "Fold just the top layer of the chin up a little", [v((-24.1, -82), (24.1, -82))], [(0, -98)], span=False, top=True, landings=[at(0, -57.9)]),
                    S("draw", "拿笔点上眼睛和鼻子", "Draw the eyes and nose with a pen", dots=[(-22, -34), (22, -34), (0, -50)]),
                ])


def cup():
    return dict(id="cup", nameZh="纸杯", nameEn="Paper Cup", cat="boxes", needs="square", pose="diamond", minutes=5, level=2,
                resultZh="一只能撑开的小纸杯（口宽约 8.8 cm）", resultEn="A little cup that opens (about 8.8 cm wide)",
                steps=[
                    S("fold", "彩色面朝下，近角对折到远角", "Colour side down. Fold near corner to far corner", [v((-D, 0), (D, 0))], [(0, -60)], span=False, landings=[at(0, D)]),
                    S("fold", "右角折到左边，上边要放平", "Right corner to the left edge, top edge level", [v((18.2, 0), (44.0, 62.1))], [(80, 10)], span=False, landings=[at(-44.0, 62.1)]),
                    S("fold", "左角折到右边，对齐刚才那一层", "Left corner across, matching the first flap", [v((-18.2, 0), (-44.0, 62.1))], [(-80, 10)], span=False, landings=[at(44.0, 62.1)]),
                    S("fold", "上面的尖角只折前一层，往你这边折下", "Fold only the front layer of the top point down", [v((-44.0, 62.1), (44.0, 62.1))], [(0, 90)], span=False, top=True, landings=[at(0, 18.1)]),
                    S("flip", "把纸左右翻过来，放回原处", "Flip it over left to right and put it back"),
                    S("fold", "剩下那层尖角也往你这边折下", "Fold the remaining point down towards you", [v((-44.0, 62.1), (44.0, 62.1))], [(0, 90)], span=False, top=True, landings=[at(0, 18.1)]),
                    S("form", "拿起来，捏两边撑开杯口", "Pick it up and squeeze the sides to open it", form="CUP_OPEN"),
                ])


def masu():
    return dict(id="masu", nameZh="方盒", nameEn="Masu Box", cat="boxes", needs="square", pose="diamond", minutes=12, level=3,
                resultZh="一个无盖小方盒（约 5.3 × 5.3 × 2.7 cm）", resultEn="An open box, about 5.3 × 5.3 × 2.7 cm",
                steps=[
                    S("crease", "彩色面朝下，远角对近角压一道痕", "Colour side down. Crease far corner to near", [v((-D, 0), (D, 0))], [(0, 60)], span=False, landings=[at(0, -D)]),
                    S("crease", "右角对左角，再压一道痕", "Crease right corner to left corner", [v((0, -D), (0, D))], [(60, 0)], span=False, landings=[at(-D, 0)]),
                    S("fold", "四个角都折到中心点", "Fold all four corners to the centre",
                      [v((-H, -H), (H, -H)), v((H, -H), (H, H)), v((H, H), (-H, H)), v((-H, H), (-H, -H))],
                      [(0, -90), (90, 0), (0, 90), (-90, 0)], span=False, landings=[at(0, 0)]),
                    S("crease", "远近两条边折到中线，再打开", "Near and far edges to the middle, unfold", [v((-H, 26.5), (H, 26.5)), v((-H, -26.5), (H, -26.5))], [(0, 40), (0, -40)], span=False, landings=[seg((-H, 0), (H, 0))]),
                    S("crease", "左右两条边也折到中线，再打开", "Left and right edges to the middle, unfold", [v((26.5, -H), (26.5, H)), v((-26.5, -H), (-26.5, H))], [(40, 0), (-40, 0)], span=False, landings=[seg((0, -H), (0, H))]),
                    S("fold", "把左右两个角打开", "Unfold the left and right corners", [v((-H, -H), (-H, H)), v((H, -H), (H, H))], [(-20, 0), (20, 0)], span=False, top=True, landings=[at(-D, 0), at(D, 0)]),
                    S("form", "沿压痕把远近两边立起来，变成两面墙", "Raise the near and far sides into walls", form="MASU_WALLS"),
                    S("form", "左边一头收拢，把尖角翻进盒底", "Close the left end, tuck its point inside", form="MASU_END1"),
                    S("form", "右边也一样，方盒就好了", "Same on the right. Your box is done!", form="MASU_END2"),
                ])


ORIGINAL = [basics, dog, cup, masu]


def all_models():
    return [f() for f in ORIGINAL]


# =============================================================================================
# paper prep: printer paper -> the 150-unit square (scaled to the paper's width by the app)
# =============================================================================================

def prep_outline(paper):
    w, h = PAPERS[paper]
    e = 150.0 * (h / w - 1.0)
    return [(-75.0, -75.0), (75.0, -75.0), (75.0, 75.0 + e), (-75.0, 75.0 + e)], e


def prep_steps(paper, diamond):
    _o, e = prep_outline(paper)
    top = 75.0 + e
    steps = [
        S("fold", "右下角往左上折，下边贴齐左边", "Fold the near-right corner up onto the left edge", [v((-75, -75), (75, 75))], [(40, -40)], landings=[at(-75, 75)]),
        S("crease", "多出的纸条往下折，压出折痕再打开", "Fold the strip at the top down, crease, unfold", [v((-75, 75), (75, 75))], [(0, 75 + e / 2)], landings=[seg((-75, 75 - e), (75, 75 - e))]),
        S("cut", "沿折痕剪掉（或撕掉）这条纸", "Cut (or tear) the strip off along the crease", [v((-75, 75), (75, 75))], [(0, 75 + e / 2)], landings=[]),
        S("fold", "把三角形打开，就是一张正方形", "Unfold the triangle: now it is a square", [v((-75, -75), (75, 75))], [(-40, 40)], top=True, landings=[at(75, -75)]),
    ]
    steps[3].leaves_crease = True
    resets = {3: "SQUARE"}
    if diamond:
        steps.append(S("rotate", "把正方形转 45°，让一个角朝向你", "Turn the square 45° so a corner points at you"))
        resets[4] = "DIAMOND"
    return steps, resets


OUTLINES = {"SQUARE": E.SQUARE, "DIAMOND": E.DIAMOND}


def rect_outline(paper):
    w, h = PAPERS[paper]
    return E.rect_outline(150.0, 150.0 * h / w)


def half_len(paper):
    w, h = PAPERS[paper]
    return 75.0 * h / w


def variant(fn, paper):
    """One model on one paper, with its steps finished (fold lines spanned, landings filled in)."""
    model = fn(paper) if getattr(fn, "rect", False) else fn()
    model = dict(model)
    model["paper"] = paper
    model["key"] = "%s@%s" % (model["id"], paper)
    model["prep"] = 0
    resets = dict(model.get("resets") or {})
    if model.get("prepOutline"):
        outline, _e = prep_outline(paper)
    elif model["needs"] == "rect":
        outline = rect_outline(paper)
    elif paper == "square":
        outline = OUTLINES["DIAMOND"] if model["pose"] == "diamond" else OUTLINES["SQUARE"]
    else:
        outline, _e = prep_outline(paper)
        pre, pre_resets = prep_steps(paper, model["pose"] == "diamond")
        model["prep"] = len(pre)
        model["steps"] = pre + model["steps"]
        resets = pre_resets
    model["outline"] = outline
    model["resets"] = {i: OUTLINES[k] for i, k in resets.items()}
    model["resetNames"] = resets
    model["outline_cy"] = (E.bounds(outline)[1] + E.bounds(outline)[3]) / 2
    finish(model)
    return model


def finish(model):
    sheets = E.build(model["outline"], model["steps"], model["resets"])
    for i, st in enumerate(model["steps"]):
        sheet = sheets[i]
        if st.turns and getattr(st, "span", True):
            mv = E.moving(sheet, st)
            lines = []
            for j, (a, b, mt) in enumerate(st.lines):
                stay, move = E.split_for(sheet, a, b, st.pick(j), st.top, st.zr(j), mt)
                aa, bb = E.span(sheet, a, b, facets=move or mv)
                lines.append(((round(aa[0], 2), round(aa[1], 2)), (round(bb[0], 2), round(bb[1], 2)), mt))
            st.lines = lines
        if st.turns and st.landings is None:
            st.landings = E.auto_landings(sheet, st)
        if st.landings is None:
            st.landings = []
    model["sheets"] = E.build(model["outline"], model["steps"], model["resets"])
    return model


def available(fn, paper):
    needs = (fn(RECT_PAPERS[0]) if getattr(fn, "rect", False) else fn())["needs"]
    return needs == "square" or paper != "square"


def all_variants():
    out = []
    for fn in library():
        for paper in PAPERS:
            if available(fn, paper):
                out.append(variant(fn, paper))
    return out


def check(model):
    problems = []
    sheets = model["sheets"]
    ob = E.bounds(model["outline"] + [p for o in model["resets"].values() for p in o])
    for i, st in enumerate(model["steps"]):
        sheet = sheets[i]
        if st.turns:
            if not E.moving(sheet, st):
                problems.append("step %d moves nothing" % (i + 1))
            for l in st.landings:
                err = E.landing_error(sheet, st, l)
                if err > 0.5:
                    problems.append("step %d landing %s off by %.2f" % (i + 1, l, err))
        zh = sum(1 for c in st.zh if ord(c) > 0x2E80)
        if zh > 22:
            problems.append("step %d zh %d chars" % (i + 1, zh))
        if len(st.en) > 55:
            problems.append("step %d en %d chars: %s" % (i + 1, len(st.en), st.en))
        if len(st.lines) > 4:
            problems.append("step %d has %d lines" % (i + 1, len(st.lines)))
    for i, sh in enumerate(sheets):
        b = E.bounds(sh.points())
        if b[0] < ob[0] - SLACK or b[1] < ob[1] - SLACK or b[2] > ob[2] + SLACK or b[3] > ob[3] + SLACK:
            problems.append("sheet %d leaves the start bounds %s" % (i, [round(x, 1) for x in b]))
        if len(sh.facets) > 80:
            problems.append("sheet %d has %d facets" % (i, len(sh.facets)))
    if len(model["resultEn"]) > 90:
        problems.append("result en too long")
    return problems


def dump(model):
    for i, sh in enumerate(model["sheets"]):
        print("  sheet %d:" % i, sorted(set(f.z for f in sh.facets)), len(sh.facets), "facets")
        for f in sorted(sh.facets, key=lambda f: f.z):
            print("     ", f)


def main():
    bad = 0
    variants = all_variants()
    for mdl in variants:
        p = check(mdl)
        print("%-18s %2d steps  %s" % (mdl["key"], len(mdl["steps"]), "ok" if not p else "; ".join(p)))
        bad += bool(p)
    print("%d variants, %d models, %d with problems" % (len(variants), len(library()), bad))
    if len(sys.argv) > 1:
        for mdl in variants:
            if mdl["key"] in sys.argv[1:]:
                print(mdl["key"])
                dump(mdl)
    return 1 if bad else 0


def library():
    import library_v011 as lib
    by = {f.__name__: f for f in ORIGINAL + lib.NEW}
    return [by[i] for i in lib.ORDER]


if __name__ == "__main__":
    sys.exit(main())
