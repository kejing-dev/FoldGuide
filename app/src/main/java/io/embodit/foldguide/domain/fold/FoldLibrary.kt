package io.embodit.foldguide.domain.fold

/** How the paper lies at the start: a square with edges towards the user, a diamond with a corner, or printer paper (long side away). */
enum class StartPose { SQUARE, DIAMOND, RECT }

/**
 * The step kinds: the design's five (`models.stepKinds`) plus, for printer paper, cutting the strip off
 * and turning the square 45 degrees into a diamond.
 */
enum class StepKind { FOLD, CREASE, FLIP, DRAW, FORM, CUT, ROTATE }

/** Parametric hinge models of the form steps (design `models.formAnimation`). */
enum class FormKind { CUP_OPEN, MASU_WALLS, MASU_END1, MASU_END2 }

/** The paper the user folds (v0.1.1): the 15 cm origami square, or printer paper. Sizes in millimetres. */
enum class Paper(val id: String, val widthMm: Float, val heightMm: Float) {
    SQUARE("square", 150f, 150f),
    A4("a4", 210f, 297f),
    LETTER("letter", 215.9f, 279.4f);

    val rect: Boolean
        get() = this != SQUARE

    companion object {
        fun byId(id: String?): Paper? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** Library shelves (v0.1.1). */
enum class Category(val id: String) {
    BASICS("basics"),
    ANIMALS("animals"),
    FLOWERS("flowers"),
    EVERYDAY("everyday"),
    BOXES("boxes"),
    PLANES("planes");

    companion object {
        fun byId(id: String?): Category? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

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
    /** Per line: only the facets whose layer lies in this range turn (a flap of several layers). Empty = all layers. */
    val layers: List<IntRange> = emptyList(),
    /** After this step the sheet is a clean sheet in this pose again (the printer-paper prep ends with a whole square). */
    val resetTo: StartPose? = null,
    /** This fold is undone in the same step on real paper and leaves its line as a crease (the prep's unfold). */
    val leavesCrease: Boolean = false,
) {
    fun pickAt(i: Int): Pt = picks.getOrElse(i) { picks.first() }

    fun layersAt(i: Int): IntRange? = if (layers.isEmpty()) null else layers.getOrElse(i) { layers.first() }

    /** True when the demo sheet turns a flap for this step (fold / crease). */
    val turns: Boolean
        get() = kind == StepKind.FOLD || kind == StepKind.CREASE

    /** True when the step draws a line and tints a part of the sheet (fold / crease / cut). */
    val marks: Boolean
        get() = turns || kind == StepKind.CUT

    val mountain: Boolean
        get() = lines.any { it.mountain }
}

/** A row of the library: everything that does not depend on the paper. */
data class FoldEntry(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val category: Category,
    /** Printer paper only (its proportions are part of the model). */
    val needsRect: Boolean,
    val pose: StartPose,
    val minutes: Int,
    /** Difficulty 1 - 3 (the three dots on the library row). */
    val level: Int,
    val resultZh: String,
    val resultEn: String,
)

/**
 * A model as folded from one paper: its steps (the printer-paper prep first when a square model is folded
 * from A4 / Letter), the outline of the sheet at the start (model units: the sheet is 150 wide) and the
 * scale from model units to the real sheet's millimetres.
 */
data class FoldModel(
    val entry: FoldEntry,
    val paper: Paper,
    val steps: List<Step>,
    val outline: List<Pt>,
    /** Real millimetres per model unit: 1 on the 15 cm square, 1.4 on A4 (210 mm wide). */
    val scale: Float,
    /** How many of [steps] are the printer-paper prep (0 on the square or for printer-paper models). */
    val prepSteps: Int,
) {
    val id: String
        get() = entry.id

    val key: String
        get() = "${entry.id}@${paper.id}"

    val nameZh: String
        get() = entry.nameZh

    val nameEn: String
        get() = entry.nameEn

    val resultZh: String
        get() = entry.resultZh

    val resultEn: String
        get() = entry.resultEn

    val level: Int
        get() = entry.level

    /** The pose the paper starts in (printer paper: a rectangle, also before the prep). */
    val pose: StartPose
        get() = if (prepSteps > 0 || entry.needsRect) StartPose.RECT else entry.pose

    /** Minutes including the prep (about 2 min). */
    val minutes: Int
        get() = entry.minutes + if (prepSteps > 0) 2 else 0
}

/**
 * The library (v0.1.1: 18 models in six shelves). The fold data is generated from `tools/foldlib`
 * (`FoldLibraryData.kt`); every variant is checked there and again by the unit tests.
 */
object FoldLibrary {
    val entries: List<FoldEntry> = FoldLibraryData.entries

    private val cache = HashMap<String, FoldModel>()

    fun entry(id: String?): FoldEntry? = entries.firstOrNull { it.id == id }

    /** Can [entry] be folded from [paper]? Square models fold from anything; printer-paper models need printer paper. */
    fun available(entry: FoldEntry, paper: Paper): Boolean = !entry.needsRect || paper.rect

    /** The shelf [category] for [paper], in library order. */
    fun shelf(category: Category, paper: Paper): List<FoldEntry> = entries.filter { it.category == category && available(it, paper) }

    /** Shelves that have at least one model for [paper]. */
    fun categories(paper: Paper): List<Category> = Category.entries.filter { shelf(it, paper).isNotEmpty() }

    /** The model [id] folded from [paper]; null when it does not exist or needs printer paper. */
    @Synchronized
    fun model(id: String?, paper: Paper): FoldModel? {
        val e = entry(id) ?: return null
        if (!available(e, paper)) return null
        return cache.getOrPut("${e.id}@${paper.id}") { build(e, paper) }
    }

    /** All models on every paper they can be folded from (the unit tests walk these). */
    fun allVariants(): List<FoldModel> = entries.flatMap { e -> Paper.entries.mapNotNull { model(e.id, it) } }

    private fun build(e: FoldEntry, paper: Paper): FoldModel {
        val scale = paper.widthMm / UNITS
        return when {
            e.needsRect -> {
                val steps = FoldLibraryData.rectSteps(e.id, paper) ?: emptyList()
                val outline = if (e.id == MAKE_SQUARE) prepOutline(paper) else rectOutline(paper)
                FoldModel(e, paper, steps, outline, scale, 0)
            }
            !paper.rect -> FoldModel(e, paper, FoldLibraryData.squareSteps(e.id) ?: emptyList(), FoldEngine.outline(e.pose), 1f, 0)
            else -> {
                val prep = FoldLibraryData.prepSteps(paper, e.pose == StartPose.DIAMOND)
                FoldModel(e, paper, prep + (FoldLibraryData.squareSteps(e.id) ?: emptyList()), prepOutline(paper), scale, prep.size)
            }
        }
    }

    /** Printer paper centred on the origin, 150 units wide. */
    fun rectOutline(paper: Paper): List<Pt> {
        val hh = UNITS / 2f * paper.heightMm / paper.widthMm
        return listOf(Pt(-75f, -hh), Pt(75f, -hh), Pt(75f, hh), Pt(-75f, hh))
    }

    /** Printer paper laid so that the square to be cut from it is the 150-unit square at the origin; the strip lies away. */
    fun prepOutline(paper: Paper): List<Pt> {
        val far = 75f + UNITS * (paper.heightMm / paper.widthMm - 1f)
        return listOf(Pt(-75f, -75f), Pt(75f, -75f), Pt(75f, far), Pt(-75f, far))
    }

    /** Model units per sheet width. */
    const val UNITS = 150f
    const val MAKE_SQUARE = "makesquare"
}
