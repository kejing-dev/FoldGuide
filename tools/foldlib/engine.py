"""2D flat-fold engine, a line-for-line port of FoldEngine.kt / Geometry.kt (the app's engine).

Model coordinates: millimetres, origin at the sheet centre, x right, y away from the user. A model is
authored for a sheet 150 units wide (the 15 cm square; printer paper is scaled up by the app).

Operations: fold (all layers / the top facet / a z range per line), crease, flip, cut, draw, form,
rotate (turn the square 45 degrees into a diamond), show (a still step).
"""
import math

EPS = 1e-4
MIN_AREA = 0.5
D = 106.1
H = 53.05


def side(a, b, p):
    return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])


def foot(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    l = math.hypot(dx, dy)
    dx, dy = dx / l, dy / l
    t = (p[0] - a[0]) * dx + (p[1] - a[1]) * dy
    return (a[0] + t * dx, a[1] + t * dy)


def reflect(p, a, b):
    f = foot(p, a, b)
    return (2 * f[0] - p[0], 2 * f[1] - p[1])


def area2(pts):
    s = 0.0
    for i in range(len(pts)):
        p, q = pts[i], pts[(i + 1) % len(pts)]
        s += p[0] * q[1] - q[0] * p[1]
    return s


def clip(pts, a, b, sgn):
    out = []
    n = len(pts)
    for i in range(n):
        p, q = pts[i], pts[(i + 1) % n]
        sp = side(a, b, p) * sgn
        sq = side(a, b, q) * sgn
        if sp >= -EPS:
            out.append(p)
        if (sp > EPS and sq < -EPS) or (sp < -EPS and sq > EPS):
            t = sp / (sp - sq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out if len(out) >= 3 and abs(area2(out) / 2) > MIN_AREA else None


def inside(p, poly):
    c = False
    j = len(poly) - 1
    for i in range(len(poly)):
        a, b = poly[i], poly[j]
        if (a[1] > p[1]) != (b[1] > p[1]) and p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]:
            c = not c
        j = i
    return c


def bounds(pts):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


class Facet:
    __slots__ = ("pts", "z", "up")

    def __init__(self, pts, z, up):
        self.pts, self.z, self.up = list(pts), z, up

    def __repr__(self):
        return "F(z=%d up=%s %s)" % (self.z, self.up, [(round(x, 1), round(y, 1)) for x, y in self.pts])


class Sheet:
    def __init__(self, facets, creases=None):
        self.facets = facets
        self.creases = list(creases or [])

    def points(self):
        return [p for f in self.facets for p in f.pts]


def rect_outline(w, h, cy=0.0):
    hw, hh = w / 2, h / 2
    return [(-hw, cy - hh), (hw, cy - hh), (hw, cy + hh), (-hw, cy + hh)]


SQUARE = rect_outline(150, 150)
DIAMOND = [(0, -D), (D, 0), (0, D), (-D, 0)]


def start(outline):
    return Sheet([Facet(outline, 0, False)])


def split_for(sheet, a, b, pick, top_only=False, zr=None, mountain=False):
    sgn = 1.0 if side(a, b, pick) >= 0 else -1.0
    z_top = None
    if top_only:
        for f in sheet.facets:
            if inside(pick, f.pts) and (z_top is None or f.z > z_top):
                z_top = f.z
    stay, move = [], []
    for f in sheet.facets:
        mv = clip(f.pts, a, b, sgn)
        st = clip(f.pts, a, b, -sgn)
        if top_only:
            eligible = f.z == z_top and inside(pick, f.pts)
        elif zr is not None:
            eligible = zr[0] <= f.z <= zr[1]
        else:
            eligible = True
        if mv is not None and eligible:
            move.append(Facet(mv, f.z, f.up))
        elif mv is not None:
            stay.append(Facet(mv, f.z, f.up))
        if st is not None:
            stay.append(Facet(st, f.z, f.up))
    return stay, move


def apply_split(stay, move, a, b, mountain):
    max_s = max((f.z for f in stay), default=0)
    min_s = min((f.z for f in stay), default=0)
    max_m = max(f.z for f in move)
    min_m = min(f.z for f in move)
    moved = []
    for f in move:
        z = (min_s - 1 - (f.z - min_m)) if mountain else (max_s + 1 + (max_m - f.z))
        moved.append(Facet([reflect(p, a, b) for p in f.pts][::-1], z, not f.up))
    return stay + moved


def flip(sheet):
    return Sheet([Facet([(-x, y) for x, y in f.pts][::-1], -f.z, not f.up) for f in sheet.facets],
                 [((-l[0][0], l[0][1]), (-l[1][0], l[1][1]), l[2]) for l in sheet.creases])


def rot45(p):
    c = s = math.sqrt(0.5)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c)


class Step:
    def __init__(self, kind, zh, en, lines=(), picks=(), top=False, ranges=None, landings=None, dots=(), form=None,
                 legend=None):
        self.kind = kind  # fold crease flip draw form cut rotate show
        self.zh, self.en = zh, en
        self.lines = [tuple(l) for l in lines]  # (a, b, mountain)
        self.picks = list(picks)
        self.top = top
        self.ranges = ranges  # per line (zmin, zmax) or None
        self.landings = landings  # list of ("at", p) / ("seg", a, b); None = automatic
        self.dots = list(dots)
        self.form = form
        self.legend = legend
        self.leaves_crease = False

    def pick(self, i):
        return self.picks[i] if i < len(self.picks) else self.picks[0]

    def zr(self, i):
        if not self.ranges:
            return None
        return self.ranges[i] if i < len(self.ranges) else self.ranges[0]

    @property
    def turns(self):
        return self.kind in ("fold", "crease", "cut")


def anim_parts(sheet, step):
    facets = sheet.facets
    moves = []
    for i, (a, b, m) in enumerate(step.lines):
        stay, move = split_for(Sheet(facets), a, b, step.pick(i), step.top, step.zr(i), m)
        if move:
            moves.append((stay, move, a, b, m))
        facets = stay
    return facets, moves


def fold(sheet, step):
    cur = sheet
    for i, (a, b, m) in enumerate(step.lines):
        stay, move = split_for(cur, a, b, step.pick(i), step.top, step.zr(i), m)
        if not move:
            continue
        cur = Sheet(apply_split(stay, move, a, b, m), cur.creases)
    return cur


def cut(sheet, step):
    a, b, _ = step.lines[0]
    stay, _move = split_for(sheet, a, b, step.pick(0))
    sgn = 1.0 if side(a, b, step.pick(0)) >= 0 else -1.0
    creases = [c for c in sheet.creases if not (side(a, b, c[0]) * sgn > -EPS and side(a, b, c[1]) * sgn > -EPS)]
    return Sheet(stay, creases)


def apply(sheet, step, reset=None):
    k = step.kind
    if k == "fold":
        out = Sheet(fold(sheet, step).facets, sheet.creases + (step.lines if step.leaves_crease else []))
    elif k == "crease":
        out = Sheet(sheet.facets, sheet.creases + step.lines)
    elif k == "flip":
        out = flip(sheet)
    elif k == "cut":
        out = cut(sheet, step)
    elif k == "rotate":
        out = Sheet([Facet([rot45(p) for p in f.pts], f.z, f.up) for f in sheet.facets],
                    [(rot45(l[0]), rot45(l[1]), l[2]) for l in sheet.creases])
    else:
        out = sheet
    if reset is not None:
        out = Sheet([Facet(reset, 0, False)], out.creases)
    return out


def build(outline, steps, resets=None):
    """Sheets at the start of every step plus the finished sheet. resets: {step index: outline} replaces the
    sheet by a clean one after that step (the paper prep ends with a whole square)."""
    resets = resets or {}
    sheet = start(outline)
    out = []
    for i, s in enumerate(steps):
        out.append(sheet)
        sheet = apply(sheet, s, resets.get(i))
    out.append(sheet)
    return out


def moving(sheet, step):
    _stay, moves = anim_parts(sheet, step)
    return [f for (_s, mv, _a, _b, _m) in moves for f in mv]


def landing_error(sheet, step, landing):
    _stay, moves = anim_parts(sheet, step)
    if step.kind == "cut":
        return 0.0
    moved = [reflect(p, a, b) for (_s, mv, a, b, _m) in moves for f in mv for p in f.pts]
    if landing[0] == "at":
        return min(math.dist(p, landing[1]) for p in moved)
    return max(min(math.dist(p, landing[1]) for p in moved), min(math.dist(p, landing[2]) for p in moved))


def auto_landings(sheet, step):
    """One landing per line: the moved vertex farthest from its fold line, at its landing place."""
    out = []
    _stay, moves = anim_parts(sheet, step)
    for (_s, mv, a, b, _m) in moves:
        best, bd = None, -1.0
        for f in mv:
            for p in f.pts:
                d = math.dist(p, foot(p, a, b))
                if d > bd + 1e-6:
                    best, bd = p, d
        r = reflect(best, a, b)
        land = ("at", (round(r[0], 2), round(r[1], 2)))
        if land not in out:
            out.append(land)
    return out


def line_through(p, q, ext=0.0):
    return p, q


def clip_line(a, b, outline_pts, margin=0.0):
    """The part of the infinite line a-b inside the convex hull of outline_pts (for drawing fold lines edge to edge)."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    l = math.hypot(dx, dy)
    dx, dy = dx / l, dy / l
    ts = []
    n = len(outline_pts)
    for i in range(n):
        p, q = outline_pts[i], outline_pts[(i + 1) % n]
        ex, ey = q[0] - p[0], q[1] - p[1]
        den = dx * ey - dy * ex
        if abs(den) < 1e-9:
            continue
        t = ((p[0] - a[0]) * ey - (p[1] - a[1]) * ex) / den
        u = ((p[0] - a[0]) * dy - (p[1] - a[1]) * dx) / den
        if -1e-6 <= u <= 1 + 1e-6:
            ts.append(t)
    if len(ts) < 2:
        return a, b
    t0, t1 = min(ts) + margin, max(ts) - margin
    return (a[0] + dx * t0, a[1] + dy * t0), (a[0] + dx * t1, a[1] + dy * t1)


def span(sheet, a, b, facets=None):
    """The fold line a-b extended (or trimmed) to where it crosses the paper: min / max over every facet."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    l = math.hypot(dx, dy)
    dx, dy = dx / l, dy / l
    lo, hi = None, None
    for f in (facets if facets is not None else sheet.facets):
        pts = f.pts
        n = len(pts)
        ts = []
        for i in range(n):
            p, q = pts[i], pts[(i + 1) % n]
            ex, ey = q[0] - p[0], q[1] - p[1]
            den = dx * ey - dy * ex
            if abs(den) < 1e-9:
                continue
            t = ((p[0] - a[0]) * ey - (p[1] - a[1]) * ex) / den
            u = ((p[0] - a[0]) * dy - (p[1] - a[1]) * dx) / den
            if -1e-6 <= u <= 1 + 1e-6:
                ts.append(t)
        if len(ts) >= 2 and max(ts) - min(ts) > 0.5:
            lo = min(ts) if lo is None else min(lo, min(ts))
            hi = max(ts) if hi is None else max(hi, max(ts))
    if lo is None:
        return a, b
    return (a[0] + dx * lo, a[1] + dy * lo), (a[0] + dx * hi, a[1] + dy * hi)
