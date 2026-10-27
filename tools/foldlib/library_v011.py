"""v0.1.1 models (user feedback 2026-10-26: "the library is too small, only 4"). Imported by models.py."""
import math

from engine import D, H
from models import PAPERS, S, at, bisector_fold, half_len, m, prep_steps, ray, seg, v


def rect(fn):
    fn.rect = True
    return fn


FAR_TO_NEAR = ("彩色面朝下，远角对折到近角", "Colour side down. Fold far corner to near corner")


def far_to_near():
    return S("fold", *FAR_TO_NEAR, [v((-D, 0), (D, 0))], [(0, 60)], landings=[at(0, -D)])


def mx(p):
    return (-p[0], p[1])


def mirrored(line):
    return (mx(line[1]), mx(line[0]))


def cat():
    ear = bisector_fold((-31.8, -70), (-60, 24))
    return dict(id="cat", nameZh="小猫脸", nameEn="Kitten Face", cat="animals", needs="square", pose="diamond", minutes=4, level=2,
                resultZh="两只尖耳朵竖起来的小猫脸", resultEn="A kitten face with two pointy ears up",
                steps=[
                    far_to_near(),
                    S("fold", "左角往下折", "Fold the left corner down", [v((-36, 0), (-70, -36.1))], [(-90, -5)], landings=[at(-31.8, -70)]),
                    S("fold", "右角也往下折", "Fold the right corner down", [v((36, 0), (70, -36.1))], [(90, -5)], landings=[at(31.8, -70)]),
                    S("fold", "左边那片的尖角翻上去，伸出上边，做耳朵", "Fold the left flap's tip back up past the top: an ear", [v(*ear)], [(-33, -64)], ranges=[(2, 3)]),
                    S("fold", "右边那片也翻上去，做另一只耳朵", "Same with the right flap for the other ear", [v(*mirrored(ear))], [(33, -64)], ranges=[(4, 5)]),
                    S("fold", "下巴尖往后折一点", "Fold the chin point behind a little", [m((-30, -86), (30, -86))], [(0, -100)]),
                    S("draw", "画上眼睛和鼻子，小猫就好了", "Draw the eyes and nose: your kitten is done", dots=[(-24, -36), (24, -36), (0, -56)]),
                ])


def corners_down():
    return S("fold", "左右两个角都往下折到底角", "Fold both side corners down to the bottom point",
             [v((0, 0), (-H, -H)), v((0, 0), (H, -H))], [(-85, -5), (85, -5)], landings=[at(0, -D)])


def fox():
    l3 = bisector_fold((0, -D), (-52, 22))
    return dict(id="fox", nameZh="狐狸脸", nameEn="Fox Face", cat="animals", needs="square", pose="diamond", minutes=4, level=2,
                resultZh="尖下巴、两只大耳朵的狐狸脸", resultEn="A fox face with a pointy chin and big ears",
                steps=[
                    far_to_near(),
                    corners_down(),
                    S("fold", "左边那片的尖角往左上翻出来，做耳朵", "Fold the left flap's point up and out: an ear", [v(*l3)], [(-6, -98)], ranges=[(2, 3)]),
                    S("fold", "右边那片也翻上去，做另一只耳朵", "Same with the right flap for the other ear", [v(*mirrored(l3))], [(6, -98)], ranges=[(4, 5)]),
                    S("draw", "点上眼睛和鼻子，狐狸就好了", "Dot the eyes and nose: your fox is done", dots=[(-20, -50), (20, -50), (0, -92)]),
                ])


def cicada():
    l3 = bisector_fold((0, D), (-34, -12))
    return dict(id="cicada", nameZh="知了", nameEn="Cicada", cat="animals", needs="square", pose="diamond", minutes=6, level=2,
                resultZh="一只张开翅膀的知了（蝉）", resultEn="A cicada with its wings spread",
                steps=[
                    S("fold", "彩色面朝下，近角对折到远角", "Colour side down. Fold near corner to far corner", [v((-D, 0), (D, 0))], [(0, -60)], landings=[at(0, D)]),
                    S("fold", "左右两个角都往上折到顶角", "Fold both side corners up to the top point", [v((0, 0), (-H, H)), v((0, 0), (H, H))], [(-85, 5), (85, 5)], landings=[at(0, D)]),
                    S("fold", "左边那片往下斜着折，尖角露出下边", "Fold the left flap down at a slant, past the edge", [v(*l3)], [(-6, 98)], ranges=[(2, 3)]),
                    S("fold", "右边那片也一样，就是两只翅膀", "Same on the right: two wings", [v(*mirrored(l3))], [(6, 98)], ranges=[(4, 5)]),
                    S("fold", "顶角只折上面一层，往下折到快到底", "Fold only the top layer of the top point down", [v((-40, 58), (40, 58))], [(0, 100)], top=True),
                    S("fold", "下一层也往下折，比刚才高一点", "Fold the next layer down too, a little higher", [v((-40, 68), (40, 68))], [(0, 100)], top=True),
                    S("fold", "左右两边往后折，知了就好了", "Fold both sides behind: your cicada is done", [m((-36, -10), (-24, 100)), m((36, -10), (24, 100))], [(-60, 20), (60, 20)]),
                ])


def whale():
    a = (-D, 0)
    return dict(id="whale", nameZh="小鲸鱼", nameEn="Little Whale", cat="animals", needs="square", pose="diamond", minutes=5, level=2,
                resultZh="一条翘着尾巴的小鲸鱼", resultEn="A little whale with its tail up",
                steps=[
                    S("crease", "彩色面朝下，远角对近角压一道痕", "Colour side down. Crease far corner to near", [v((-D, 0), (D, 0))], [(0, 60)], landings=[at(0, -D)]),
                    S("fold", "左边两条边都折到中线，像风筝", "Fold both left edges to the middle: a kite", [v(a, ray(a, -22.5)), v(a, ray(a, 22.5))], [(0, -80), (0, 80)]),
                    S("fold", "右边的尖角往左折一点", "Fold the right point in a little", [v((62, -60), (62, 60))], [(95, 0)]),
                    S("fold", "沿中线往后对折", "Fold in half behind along the middle", [m((-D, 0), (D, 0))], [(0, 30)]),
                    S("fold", "尾巴往上折起来", "Fold the tail up", [v((-58, 0), ray((-58, 0), 315, 60))], [(-95, -2)]),
                    S("draw", "点上眼睛，小鲸鱼就好了", "Dot the eye: your whale is done", dots=[(36, -14)]),
                ])


def tulip():
    l2 = bisector_fold((-D, 0), (-42, 34))
    return dict(id="tulip", nameZh="郁金香", nameEn="Tulip", cat="flowers", needs="square", pose="diamond", minutes=2, level=1,
                resultZh="一朵郁金香花", resultEn="A tulip flower",
                steps=[
                    far_to_near(),
                    S("fold", "左角往上斜着折，尖角伸出上边", "Fold the left corner up at a slant, past the top", [v(*l2)], [(-98, -3)]),
                    S("fold", "右角也一样，郁金香就开了", "Same with the right corner: a tulip", [v(*mirrored(l2))], [(98, -3)]),
                    S("fold", "两边的小角往后折，花形更圆", "Fold the small side corners behind to round it", [m((-62, 10), (-46, -40)), m((62, 10), (46, -40))], [(-64, -20), (64, -20)]),
                ])


def petals():
    q = 37.5
    return dict(id="petals", nameZh="四瓣花", nameEn="Four-Petal Flower", cat="flowers", needs="square", pose="square", minutes=6, level=2,
                resultZh="中间四片小花瓣的方形花", resultEn="A square flower with four little petals",
                steps=[
                    S("crease", "两条对角线都压出折痕", "Crease both diagonals", [v((-75, -75), (75, 75)), v((-75, 75), (75, -75))], [(40, -40), (-40, -40)]),
                    S("fold", "四个角都折到中心点", "Fold all four corners to the centre",
                      [v((0, -75), (75, 0)), v((75, 0), (0, 75)), v((0, 75), (-75, 0)), v((-75, 0), (0, -75))],
                      [(60, -60), (60, 60), (-60, 60), (-60, -60)], landings=[at(0, 0)]),
                    S("flip", "翻过来", "Turn it over"),
                    S("fold", "四个角再折到中心点", "Fold the four corners to the centre again",
                      [v((-q, -q), (q, -q)), v((q, -q), (q, q)), v((q, q), (-q, q)), v((-q, q), (-q, -q))],
                      [(0, -60), (60, 0), (0, 60), (-60, 0)], landings=[at(0, 0)]),
                    S("fold", "中间四个尖角往外翻，伸出边外做花瓣", "Fold the four centre points out past the edges",
                      [v((-q, -25), (q, -25)), v((25, -q), (25, q)), v((q, 25), (-q, 25)), v((-25, q), (-25, -q))],
                      [(0, -8), (8, 0), (0, 8), (-8, 0)], ranges=[(1, 5), (6, 10), (11, 14), (15, 17)]),
                ])


def heart():
    q = D / 4
    return dict(id="heart", nameZh="爱心", nameEn="Heart", cat="everyday", needs="square", pose="diamond", minutes=4, level=2,
                resultZh="一颗可以夹在信里的爱心", resultEn="A heart to tuck into a card",
                steps=[
                    S("fold", "彩色面朝下，远角折到中心点", "Colour side down. Fold far corner to the centre", [v((-60, H), (60, H))], [(0, 95)], landings=[at(0, 0)]),
                    S("fold", "近角往上折到上边", "Fold the near corner up to the top edge", [v((-90, -q), (90, -q))], [(0, -95)]),
                    S("fold", "右边下半部沿中线往上折", "Fold the lower right edge up along the centre", [v((0, -q), ray((0, -q), 45, 120))], [(80, -12)]),
                    S("fold", "左边也一样往上折", "Same on the left", [v((0, -q), ray((0, -q), 135, 120))], [(-80, -12)]),
                    S("fold", "四个角往后折一点，爱心就好了", "Fold the four lobe corners behind: a heart",
                      [m((16.6, 69.7), (36.4, 69.7)), m((56.4, 49.7), (56.4, 29.9)), m((-36.4, 69.7), (-16.6, 69.7)), m((-56.4, 29.9), (-56.4, 49.7))],
                      [(26.5, 76), (63, 39.8), (-26.5, 76), (-63, 39.8)]),
                ])


def envelope():
    return dict(id="envelope", nameZh="小信封", nameEn="Little Envelope", cat="everyday", needs="square", pose="diamond", minutes=3, level=1,
                resultZh="一个能装小纸条的信封", resultEn="An envelope for a little note",
                steps=[
                    S("fold", "彩色面朝下，左角折到中心点", "Colour side down. Left corner to the centre", [v((-H, -60), (-H, 60))], [(-90, 0)], landings=[at(0, 0)]),
                    S("fold", "右角也折到中心点", "Right corner to the centre too", [v((H, -60), (H, 60))], [(90, 0)], landings=[at(0, 0)]),
                    S("fold", "近角往上折，过中心一点", "Fold the near corner up, just past the centre", [v((-80, -45.55), (80, -45.55))], [(0, -95)]),
                    S("fold", "远角折下来当封口，信封好了", "Fold the far corner down as the flap: done", [v((-80, 43.05), (80, 43.05))], [(0, 95)]),
                ])


def arrow():
    n = (0, D)
    return dict(id="arrow", nameZh="小飞镖", nameEn="Paper Arrow", cat="planes", needs="square", pose="diamond", minutes=5, level=2,
                resultZh="一架方纸折的尖头小飞机", resultEn="A pointy little plane from a square",
                steps=[
                    S("crease", "彩色面朝下，左右对折压出中线", "Colour side down. Crease left corner to right", [v((0, -D), (0, D))], [(-60, 0)], landings=[at(D, 0)]),
                    S("fold", "远处两条边折到中线", "Fold the two far edges to the centre line", [v(n, ray(n, 247.5)), v(n, ray(n, 292.5))], [(-60, 30), (60, 30)]),
                    S("fold", "沿中线往后对折", "Fold in half behind along the centre line", [m((0, -D), (0, D))], [(-20, 0)]),
                    S("fold", "上面的机翼沿虚线往下折", "Fold the top wing down along the line", [v(n, (18, -D))], [(40, -40)], ranges=[(0, 99)]),
                    S("flip", "翻过来", "Turn it over"),
                    S("fold", "另一边机翼也折下来，展开就能飞", "Fold the other wing to match, open both, fly", [v(n, (-18, -D))], [(-40, -40)]),
                ])


@rect
def makesquare(paper):
    steps, _resets = prep_steps(paper, False)
    return dict(id="makesquare", nameZh="打印纸变正方形", nameEn="Square from Printer Paper", cat="basics", needs="rect", pose="rect", minutes=2, level=1,
                resultZh="一张和纸一样宽的正方形，什么都能折", resultEn="A square as wide as your paper, ready for any model",
                steps=steps[:4], resets={3: "SQUARE"}, prepOutline=True)


@rect
def hat(paper):
    hh = half_len(paper)
    sh = hh - 75
    return dict(id="hat", nameZh="小纸帽", nameEn="Paper Hat", cat="everyday", needs="rect", pose="rect", minutes=4, level=1,
                resultZh="一顶给玩偶戴的小纸帽", resultEn="A little hat for a doll or a toy",
                steps=[
                    S("fold", "竖着放，远边对折到近边", "Long side up. Fold the far edge to the near edge", [v((-75, 0), (75, 0))], [(0, hh / 2)], landings=[seg((-75, -hh), (75, -hh))]),
                    S("crease", "左右对折压出中线，再打开", "Fold in half left to right, crease, unfold", [v((0, -hh), (0, 0))], [(-40, -hh / 2)], landings=[seg((75, 0), (75, -hh))]),
                    S("fold", "上面两个角折到中线", "Fold the two top corners to the centre line", [v((0, 0), (-75, -75)), v((0, 0), (75, -75))], [(-62, -6), (62, -6)], landings=[at(0, -75)]),
                    S("fold", "下面的纸条只折上面一层，往上折", "Fold the top layer of the bottom strip up", [v((-75, -75), (75, -75))], [(0, -75 - sh / 2)], top=True, landings=[seg((-75, -75 + sh), (75, -75 + sh))]),
                    S("flip", "翻过来", "Turn it over"),
                    S("fold", "另一层纸条也往上折，帽子好了", "Fold the other strip up: your hat is done", [v((-75, -75), (75, -75))], [(0, -75 - sh / 2)], top=True, landings=[seg((-75, -75 + sh), (75, -75 + sh))]),
                ])


@rect
def wallet(paper):
    hh = half_len(paper)
    return dict(id="wallet", nameZh="卡片钱包", nameEn="Card Wallet", cat="boxes", needs="rect", pose="rect", minutes=4, level=1,
                resultZh="一个有四个口袋的卡片钱包", resultEn="A card wallet with four pockets",
                steps=[
                    S("crease", "左右对折压出中线，再打开", "Fold in half left to right, crease, unfold", [v((0, -hh), (0, hh))], [(-40, 0)], landings=[seg((75, -hh), (75, hh))]),
                    S("fold", "左右两条边都折到中线", "Fold the left and right edges to the centre", [v((-37.5, -hh), (-37.5, hh)), v((37.5, -hh), (37.5, hh))], [(-60, 0), (60, 0)], landings=[seg((0, -hh), (0, hh))]),
                    S("flip", "翻过来", "Turn it over"),
                    S("fold", "远近两条边也折到中线", "Fold the near and far edges to the centre", [v((-40, -hh / 2), (40, -hh / 2)), v((-40, hh / 2), (40, hh / 2))], [(0, -hh * 0.8), (0, hh * 0.8)], landings=[seg((-37.5, 0), (37.5, 0))]),
                    S("fold", "近边往上对折，钱包就好了", "Fold in half, near edge up: your wallet", [v((-40, 0), (40, 0))], [(0, -20)]),
                ])


def dart_start(hh):
    n = (0, hh)
    return [
        S("crease", "左右对折压出中线，再打开", "Fold in half left to right, crease, unfold", [v((0, -hh), (0, hh))], [(-40, 0)], landings=[seg((75, -hh), (75, hh))]),
        S("fold", "上面两个角折到中线", "Fold the two top corners to the centre line", [v(n, (-75, hh - 75)), v(n, (75, hh - 75))], [(-62, hh - 6), (62, hh - 6)], landings=[at(0, hh - 75)]),
    ]


@rect
def dart(paper):
    hh = half_len(paper)
    n = (0, hh)
    return dict(id="dart", nameZh="尖头纸飞机", nameEn="Dart Plane", cat="planes", needs="rect", pose="rect", minutes=4, level=1,
                resultZh="飞得又快又直的尖头纸飞机", resultEn="A fast, straight-flying dart",
                steps=dart_start(hh) + [
                    S("fold", "两条斜边再折到中线", "Fold the slanted edges to the centre line again", [v(n, ray(n, 247.5)), v(n, ray(n, 292.5))], [(-60, hh - 60), (60, hh - 60)]),
                    S("fold", "沿中线往后对折", "Fold in half behind along the centre line", [m((0, -hh), (0, hh))], [(-30, -40)]),
                    S("fold", "上面的机翼沿虚线往下折", "Fold the top wing down along the line", [v(n, (22, -hh))], [(50, -60)], ranges=[(0, 99)]),
                    S("flip", "翻过来", "Turn it over"),
                    S("fold", "另一边机翼也折下来，展开就能飞", "Fold the other wing to match, open both, fly", [v(n, (-22, -hh))], [(-50, -60)]),
                ])


@rect
def glider(paper):
    hh = half_len(paper)
    t = hh - 75
    meet = t - math.sqrt(65 ** 2 - 10 ** 2)
    l4 = bisector_fold((-75, t), (0, meet))
    return dict(id="glider", nameZh="滑翔机", nameEn="Glider", cat="planes", needs="rect", pose="rect", minutes=6, level=3,
                resultZh="平头滑翔机（中村锁），飞得又稳又远", resultEn="A blunt-nose glider (Nakamura lock) that floats far",
                steps=dart_start(hh) + [
                    S("fold", "把上面的三角形往下折", "Fold the top triangle down", [v((-75, t), (75, t))], [(0, hh - 20)], landings=[at(0, hh - 150)]),
                    S("fold", "两个角折到中线，下面露出小尖角", "Fold both corners to the centre, tip showing", [v(*l4), v(*mirrored(l4))], [(-68, t - 4), (68, t - 4)], landings=[at(0, meet)]),
                    S("fold", "小尖角往上折，压住两个角", "Fold the little tip up to lock the corners", [v((-30, meet - 3), (30, meet - 3))], [(0, hh - 148)], ranges=[(1, 3)]),
                    S("fold", "沿中线往后对折", "Fold in half behind along the centre line", [m((0, -hh), (0, hh))], [(-30, -40)]),
                    S("fold", "上面的机翼沿虚线往下折", "Fold the top wing down along the line", [v((20, t), (20, -hh))], [(50, -60)], ranges=[(0, 99)]),
                    S("flip", "翻过来", "Turn it over"),
                    S("fold", "另一边机翼也折下来，展开就能飞", "Fold the other wing to match, open both, fly", [v((-20, t), (-20, -hh))], [(-50, -60)]),
                ])


ORDER = ["basics", "makesquare", "dog", "cat", "fox", "cicada", "whale", "tulip", "petals", "heart", "envelope", "hat", "cup", "masu", "wallet", "arrow", "dart", "glider"]
NEW = [makesquare, cat, fox, cicada, whale, tulip, petals, heart, envelope, hat, wallet, arrow, dart, glider]
