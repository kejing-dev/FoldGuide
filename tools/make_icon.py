"""Fold Guide icon: the 512 PNG for the factory showcase, drawn from the same shapes as the adaptive
icon (`res/drawable/ic_launcher_foreground.xml` on `@color/ic_launcher_background`).

    python tools/make_icon.py <export.png>

Kami rose tile, a white square sheet whose top-right corner is folded down (light pink back), a short
dashed valley line beside the fold.
"""
import sys
from PIL import Image, ImageDraw

S = 1024
K = S / 108.0
ROSE = (217, 70, 111, 255)
WHITE = (255, 255, 255, 255)
PINK = (247, 198, 211, 255)
DASH = (255, 255, 255, 180)


def main(out):
    im = Image.new("RGBA", (S, S), ROSE)
    d = ImageDraw.Draw(im)
    # launcher icons show the inner 72 of the 108 viewport: scale the drawing up by 1.5 about the centre
    def P(x, y):
        return ((x - 54) * 1.5 + 54) * K, ((y - 54) * 1.5 + 54) * K
    d.polygon([P(30, 30), P(64, 30), P(78, 44), P(78, 78), P(30, 78)], fill=WHITE)
    d.polygon([P(64, 30), P(78, 44), P(64, 44)], fill=PINK)
    for (x0, y0, x1, y1) in [(57, 26, 60, 29), (62.5, 31.5, 65.5, 34.5), (68, 37, 71, 40), (73.5, 42.5, 76.5, 45.5)]:
        d.line([P(x0, y0), P(x1, y1)], fill=DASH, width=int(2.4 * 1.5 * K))
    im.resize((512, 512), Image.LANCZOS).save(out)
    print("icon written", out)


if __name__ == "__main__":
    main(sys.argv[1])
