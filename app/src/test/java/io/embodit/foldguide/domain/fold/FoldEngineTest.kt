package io.embodit.foldguide.domain.fold

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design `models.verify`, for every model on every paper it folds from (v0.1.1: 18 models, the square,
 * A4 and Letter): the turned parts land where the data says (within 0.5 mm), the sheet stays near its
 * start outline, and the copy fits the budgets (<= 22 Chinese characters / <= 55 English characters).
 * The same checks run in `tools/foldlib/models.py`, where the data is authored.
 */
class FoldEngineTest {

    private fun square(id: String) = FoldLibrary.model(id, Paper.SQUARE)!!

    @Test
    fun everyLandingIsReachedWithinHalfAMillimetre() {
        for (model in FoldLibrary.allVariants()) {
            val sheets = FoldEngine.build(model)
            model.steps.forEachIndexed { i, step ->
                if (!step.turns) return@forEachIndexed
                val sheet = sheets[i]
                assertTrue("${model.key} step ${i + 1} moves nothing", FoldEngine.movingParts(sheet, step).isNotEmpty())
                for (landing in step.landings) {
                    val err = FoldEngine.landingError(sheet, step, landing)
                    assertTrue("${model.key} step ${i + 1}: landing $landing off by $err mm", err <= 0.5f)
                }
            }
        }
    }

    @Test
    fun foldedSheetsStayNearTheirStartOutline() {
        for (model in FoldLibrary.allVariants()) {
            val outline = model.outline + FoldEngine.outline(StartPose.DIAMOND).takeIf { model.prepSteps > 0 }.orEmpty()
            val b = Geometry.bounds(outline)
            for ((i, sheet) in FoldEngine.build(model).withIndex()) {
                for (p in sheet.allPoints) {
                    assertTrue("${model.key} sheet $i has a point outside: $p", p.x >= b[0] - SLACK && p.y >= b[1] - SLACK && p.x <= b[2] + SLACK && p.y <= b[3] + SLACK)
                }
                assertTrue("${model.key} sheet $i has too many facets (${sheet.facets.size})", sheet.facets.size <= 80)
            }
        }
    }

    @Test
    fun theLibraryHasEighteenModelsOnSixShelves() {
        assertTrue(FoldLibrary.entries.size >= 16)
        assertEquals(FoldLibrary.entries.size, FoldLibrary.entries.map { it.id }.toSet().size)
        for (paper in listOf(Paper.A4, Paper.LETTER)) {
            assertEquals(FoldLibrary.entries.size, FoldLibrary.entries.count { FoldLibrary.model(it.id, paper) != null })
            for (c in Category.entries) {
                val shelf = FoldLibrary.shelf(c, paper)
                assertTrue("$c on $paper has ${shelf.size} models", shelf.size in 2..6)
            }
        }
        // square paper: every square model, no printer-paper model
        for (e in FoldLibrary.entries) assertEquals(e.id, !e.needsRect, FoldLibrary.model(e.id, Paper.SQUARE) != null)
        for (c in FoldLibrary.categories(Paper.SQUARE)) assertTrue(FoldLibrary.shelf(c, Paper.SQUARE).size <= 6)
        assertNull(FoldLibrary.model("dart", Paper.SQUARE))
        assertNotNull(FoldLibrary.model("dart", Paper.LETTER))
    }

    @Test
    fun printerPaperPrepEndsWithTheWholeSquare() {
        for (paper in listOf(Paper.A4, Paper.LETTER)) {
            for (id in listOf("basics", "dog")) {
                val model = FoldLibrary.model(id, paper)!!
                val sheets = FoldEngine.build(model)
                assertEquals(paper.widthMm / 150f, model.scale, 1e-4f)
                // the start: the whole printer sheet, 150 wide, the strip lying away
                val b0 = Geometry.bounds(sheets[0].allPoints)
                assertEquals(150f * paper.heightMm / paper.widthMm, b0[3] - b0[1], 0.05f)
                // after the cut the sheet is the 150 square at the origin
                val afterCut = Geometry.bounds(sheets[3].allPoints)
                assertEquals(75f, afterCut[3], 0.05f)
                // after the prep: one clean facet in the model's pose, the diagonal left as a crease
                val start = sheets[model.prepSteps]
                assertEquals(1, start.facets.size)
                assertEquals(FoldEngine.outline(model.entry.pose), start.facets.first().pts)
                assertTrue(start.creases.isNotEmpty())
                // and from there the square model's own steps run as on the square
                assertEquals(square(id).steps.size, model.steps.size - model.prepSteps)
            }
        }
    }

    @Test
    fun dogFaceFoldsToATriangleWithTwoEars() {
        val sheets = FoldEngine.build(square("dog"))
        // after the first fold the far corner lies on the near corner
        val after1 = sheets[1]
        assertEquals(2, after1.facets.size)
        assertTrue(after1.allPoints.all { it.y <= 0.01f })
        // the finished face has the chin flap on top (highest layer) above the centre line
        val done = sheets.last()
        val top = done.facets.maxByOrNull { it.z }!!
        assertTrue(top.pts.all { it.y >= -82.1f })
    }

    @Test
    fun catEarsStickUpPastTheTopEdge() {
        val done = FoldEngine.build(square("cat")).last()
        assertTrue(done.allPoints.maxOf { it.y } > 15f)
    }

    @Test
    fun masuCornersMeetAtTheCentre() {
        val sheets = FoldEngine.build(square("masu"))
        val after3 = sheets[3]
        val b = Geometry.bounds(after3.allPoints)
        assertEquals(-FoldEngine.H, b[0], 0.05f)
        assertEquals(FoldEngine.H, b[2], 0.05f)
        assertEquals(-FoldEngine.H, b[1], 0.05f)
        assertEquals(FoldEngine.H, b[3], 0.05f)
        // step 6 opens the left and right corners again: the width is the full diagonal
        val after6 = sheets[6]
        val b6 = Geometry.bounds(after6.allPoints)
        assertEquals(-FoldEngine.D, b6[0], 0.05f)
        assertEquals(FoldEngine.D, b6[2], 0.05f)
    }

    @Test
    fun cupFlipMirrorsTheSheet() {
        val sheets = FoldEngine.build(square("cup"))
        val before = sheets[4]
        val after = sheets[5]
        val bb = Geometry.bounds(before.allPoints)
        val ba = Geometry.bounds(after.allPoints)
        assertEquals(-bb[2], ba[0], 0.01f)
        assertEquals(-bb[0], ba[2], 0.01f)
        assertEquals(bb[1], ba[1], 0.01f)
        assertEquals(bb[3], ba[3], 0.01f)
    }

    @Test
    fun dartFoldsToANarrowPlane() {
        for (paper in listOf(Paper.A4, Paper.LETTER)) {
            val done = FoldEngine.build(FoldLibrary.model("dart", paper)!!).last()
            val b = Geometry.bounds(done.allPoints)
            assertTrue("dart on $paper is ${b[2] - b[0]} wide", b[2] - b[0] < 70f)
            assertTrue(abs(b[3] - 75f * paper.heightMm / paper.widthMm) < 0.1f)
        }
    }

    @Test
    fun copyFitsTheBudgets() {
        for (model in FoldLibrary.allVariants()) {
            for ((i, step) in model.steps.withIndex()) {
                val zh = step.textZh.count { it.code > 0x2E80 }
                assertTrue("${model.key} step ${i + 1} zh has $zh characters", zh <= 22)
                assertTrue("${model.key} step ${i + 1} en has ${step.textEn.length} characters", step.textEn.length <= 55)
            }
        }
    }

    @Test
    fun stepsHaveOnePickPerLineAndAtMostFourLines() {
        for (model in FoldLibrary.allVariants()) {
            for ((i, step) in model.steps.withIndex()) {
                assertTrue("${model.key} step ${i + 1} has ${step.lines.size} lines", step.lines.size <= 4)
                if (step.marks) assertTrue("${model.key} step ${i + 1} needs picks", step.picks.isNotEmpty())
                if (step.layers.isNotEmpty()) assertEquals("${model.key} step ${i + 1} layers", step.lines.size, step.layers.size)
            }
        }
    }

    private companion object {
        const val SLACK = 20.05f
    }
}
