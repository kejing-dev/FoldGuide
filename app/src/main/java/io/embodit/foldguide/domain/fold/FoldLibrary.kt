package io.embodit.foldguide.domain.fold

/** How the square is set at the start (design `spatialLayout.paper.startPose`). */
enum class StartPose { SQUARE, DIAMOND }

/** The five step kinds of the design (`models.stepKinds`). */
enum class StepKind { FOLD, CREASE, FLIP, DRAW, FORM }

/** Parametric hinge models of the form steps (design `models.formAnimation`). */
enum class FormKind { CUP_OPEN, MASU_WALLS, MASU_END1, MASU_END2 }

/** One step of a model: the copy, the fold lines, the pick points (one per line), landings and extras. */
data class Step(
    val kind: StepKind,
    val textZh: String,
    val textEn: String,
    val lines: List<FoldLine> = emptyList(),
    val picks: List<Pt> = emptyList(),
    /** fold-top: only the topmost facet under the pick turns. */
    val topOnly: Boolean = false,
    val landings: List<Landing> = emptyList(),
    /** Draw steps: the dots. */
    val dots: List<Pt> = emptyList(),
    /** Form steps: which hinge model plays. */
    val form: FormKind? = null,
    /** Shows the legend chip for valley ("v") or mountain ("m") when set (the basics). */
    val legend: String? = null,
) {
    fun pickAt(i: Int): Pt = picks.getOrElse(i) { picks.first() }

    /** True when the demo sheet turns a flap for this step (fold / crease). */
    val turns: Boolean
        get() = kind == StepKind.FOLD || kind == StepKind.CREASE

    val mountain: Boolean
        get() = lines.any { it.mountain }
}

/** A model of the library (design `models.list`). */
data class FoldModel(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val pose: StartPose,
    val minutes: Int,
    /** Difficulty 1 - 3 (the three dots on the library row). */
    val level: Int,
    val resultZh: String,
    val resultEn: String,
    val steps: List<Step>,
)

/**
 * The four models, exactly the design's `models.list` (generated from the mockup's `folds.preview.js`:
 * coordinates in millimetres, D = 106.1, H = 53.05). The single source of the fold data; the engine
 * tests check every landing against it.
 */
object FoldLibrary {
    private const val D = FoldEngine.D
    private const val H = FoldEngine.H

    private fun p(x: Float, y: Float) = Pt(x, y)

    private fun v(x0: Float, y0: Float, x1: Float, y1: Float) = FoldLine(Pt(x0, y0), Pt(x1, y1), mountain = false)

    private fun m(x0: Float, y0: Float, x1: Float, y1: Float) = FoldLine(Pt(x0, y0), Pt(x1, y1), mountain = true)

    private fun at(x: Float, y: Float): Landing = Landing.At(Pt(x, y))

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float): Landing = Landing.Seg(Pt(x0, y0), Pt(x1, y1))

    val basics =
        FoldModel(
            id = "basics",
            nameZh = "基础折法",
            nameEn = "Folding Basics",
            pose = StartPose.SQUARE,
            minutes = 2,
            level = 1,
            resultZh = "看懂图例：虚线往前折、点划线往后折、压痕、角对点",
            resultEn = "Read the symbols: dashed folds forward, dash-dot folds behind, creases, corner to point",
            steps =
                listOf(
                    Step(StepKind.CREASE, "虚线 = 谷折：近边折到远边，压平再打开", "Dashed = valley: near edge to far edge, then unfold", listOf(v(-75f, 0f, 75f, 0f)), listOf(p(0f, -40f)), landings = listOf(seg(-75f, 75f, 75f, 75f)), legend = "v"),
                    Step(StepKind.CREASE, "点划线 = 山折：往背后折，再打开", "Dash-dot = mountain: fold behind, then unfold", listOf(m(0f, -75f, 0f, 75f)), listOf(p(40f, 0f)), landings = listOf(seg(-75f, -75f, -75f, 75f)), legend = "m"),
                    Step(StepKind.CREASE, "对角折：右近角对到左远角", "Diagonal: near-right corner to far-left corner", listOf(v(-75f, -75f, 75f, 75f)), listOf(p(40f, -40f)), landings = listOf(at(-75f, 75f)), legend = "v"),
                    Step(StepKind.FOLD, "角对点：左近角折到中心", "Fold the near-left corner to the centre", listOf(v(-75f, 0f, 0f, -75f)), listOf(p(-60f, -60f)), landings = listOf(at(0f, 0f)), legend = "v"),
                ),
        )

    val dog =
        FoldModel(
            id = "dog",
            nameZh = "小狗脸",
            nameEn = "Puppy Face",
            pose = StartPose.DIAMOND,
            minutes = 3,
            level = 1,
            resultZh = "三角形狗脸，两只耷拉的耳朵",
            resultEn = "A triangle puppy face with floppy ears",
            steps =
                listOf(
                    Step(StepKind.FOLD, "彩色面朝下，远角对折到近角", "Colour side down. Fold far corner to near corner", listOf(v(-D, 0f, D, 0f)), listOf(p(0f, 60f)), landings = listOf(at(0f, -D))),
                    Step(StepKind.FOLD, "左角往下折，做一只耳朵", "Fold the left corner down for an ear", listOf(v(-36f, 0f, -70f, -36.1f)), listOf(p(-90f, -5f)), landings = listOf(at(-31.8f, -70f))),
                    Step(StepKind.FOLD, "右角也往下折，做另一只耳朵", "Fold the right corner down for the other ear", listOf(v(36f, 0f, 70f, -36.1f)), listOf(p(90f, -5f)), landings = listOf(at(31.8f, -70f))),
                    Step(StepKind.FOLD, "下巴尖只折上面一层，往上折一点", "Fold just the top layer of the chin up a little", listOf(v(-24.1f, -82f, 24.1f, -82f)), listOf(p(0f, -98f)), topOnly = true, landings = listOf(at(0f, -57.9f))),
                    Step(StepKind.DRAW, "拿笔点上眼睛和鼻子", "Draw the eyes and nose with a pen", dots = listOf(p(-22f, -34f), p(22f, -34f), p(0f, -50f))),
                ),
        )

    val cup =
        FoldModel(
            id = "cup",
            nameZh = "纸杯",
            nameEn = "Paper Cup",
            pose = StartPose.DIAMOND,
            minutes = 5,
            level = 2,
            resultZh = "一只能撑开的小纸杯（口宽约 8.8 cm）",
            resultEn = "A little cup that opens (about 8.8 cm wide)",
            steps =
                listOf(
                    Step(StepKind.FOLD, "彩色面朝下，近角对折到远角", "Colour side down. Fold near corner to far corner", listOf(v(-D, 0f, D, 0f)), listOf(p(0f, -60f)), landings = listOf(at(0f, D))),
                    Step(StepKind.FOLD, "右角折到左边，上边要放平", "Right corner to the left edge, top edge level", listOf(v(18.2f, 0f, 44f, 62.1f)), listOf(p(80f, 10f)), landings = listOf(at(-44f, 62.1f))),
                    Step(StepKind.FOLD, "左角折到右边，对齐刚才那一层", "Left corner across, matching the first flap", listOf(v(-18.2f, 0f, -44f, 62.1f)), listOf(p(-80f, 10f)), landings = listOf(at(44f, 62.1f))),
                    Step(StepKind.FOLD, "上面的尖角只折前一层，往你这边折下", "Fold only the front layer of the top point down", listOf(v(-44f, 62.1f, 44f, 62.1f)), listOf(p(0f, 90f)), topOnly = true, landings = listOf(at(0f, 18.1f))),
                    Step(StepKind.FLIP, "把纸左右翻过来，放回原处", "Flip it over left to right and put it back"),
                    Step(StepKind.FOLD, "剩下那层尖角也往你这边折下", "Fold the remaining point down towards you", listOf(v(-44f, 62.1f, 44f, 62.1f)), listOf(p(0f, 90f)), topOnly = true, landings = listOf(at(0f, 18.1f))),
                    Step(StepKind.FORM, "拿起来，捏两边撑开杯口", "Pick it up and squeeze the sides to open it", form = FormKind.CUP_OPEN),
                ),
        )

    val masu =
        FoldModel(
            id = "masu",
            nameZh = "方盒",
            nameEn = "Masu Box",
            pose = StartPose.DIAMOND,
            minutes = 12,
            level = 3,
            resultZh = "一个无盖小方盒（约 5.3 × 5.3 × 2.7 cm）",
            resultEn = "An open box, about 5.3 × 5.3 × 2.7 cm",
            steps =
                listOf(
                    Step(StepKind.CREASE, "彩色面朝下，远角对近角压一道痕", "Colour side down. Crease far corner to near", listOf(v(-D, 0f, D, 0f)), listOf(p(0f, 60f)), landings = listOf(at(0f, -D))),
                    Step(StepKind.CREASE, "右角对左角，再压一道痕", "Crease right corner to left corner", listOf(v(0f, -D, 0f, D)), listOf(p(60f, 0f)), landings = listOf(at(-D, 0f))),
                    Step(
                        StepKind.FOLD,
                        "四个角都折到中心点",
                        "Fold all four corners to the centre",
                        listOf(v(-H, -H, H, -H), v(H, -H, H, H), v(H, H, -H, H), v(-H, H, -H, -H)),
                        listOf(p(0f, -90f), p(90f, 0f), p(0f, 90f), p(-90f, 0f)),
                        landings = listOf(at(0f, 0f)),
                    ),
                    Step(StepKind.CREASE, "远近两条边折到中线，再打开", "Near and far edges to the middle, unfold", listOf(v(-H, 26.5f, H, 26.5f), v(-H, -26.5f, H, -26.5f)), listOf(p(0f, 40f), p(0f, -40f)), landings = listOf(seg(-H, 0f, H, 0f))),
                    Step(StepKind.CREASE, "左右两条边也折到中线，再打开", "Left and right edges to the middle, unfold", listOf(v(26.5f, -H, 26.5f, H), v(-26.5f, -H, -26.5f, H)), listOf(p(40f, 0f), p(-40f, 0f)), landings = listOf(seg(0f, -H, 0f, H))),
                    Step(StepKind.FOLD, "把左右两个角打开", "Unfold the left and right corners", listOf(v(-H, -H, -H, H), v(H, -H, H, H)), listOf(p(-20f, 0f), p(20f, 0f)), topOnly = true, landings = listOf(at(-D, 0f), at(D, 0f))),
                    Step(StepKind.FORM, "沿压痕把远近两边立起来，变成两面墙", "Raise the near and far sides into walls", form = FormKind.MASU_WALLS),
                    Step(StepKind.FORM, "左边一头收拢，把尖角翻进盒底", "Close the left end, tuck its point inside", form = FormKind.MASU_END1),
                    Step(StepKind.FORM, "右边也一样，方盒就好了", "Same on the right. Your box is done!", form = FormKind.MASU_END2),
                ),
        )

    val all: List<FoldModel> = listOf(basics, dog, cup, masu)

    fun byId(id: String?): FoldModel? = all.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }
}
