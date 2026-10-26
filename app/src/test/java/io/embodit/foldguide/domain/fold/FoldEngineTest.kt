package io.embodit.foldguide.domain.fold

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design `models.verify`: for every model and step, the turned parts land where the design says
 * (within 0.5 mm), the sheet stays inside its start bounds, and the copy fits the budgets
 * (<= 22 Chinese characters / <= 55 English characters).
 */
class FoldEngineTest {

    @Test
    fun everyLandingIsReachedWithinHalfAMillimetre() {
        for (model in FoldLibrary.all) {
            val sheets = FoldEngine.build(model)
            model.steps.forEachIndexed { i, step ->
                if (!step.turns) return@forEachIndexed
                val sheet = sheets[i]
                for (landing in step.landings) {
                    val err = FoldEngine.landingError(sheet, step, landing)
                    assertTrue("${model.id} step ${i + 1}: landing $landing off by $err mm", err <= 0.5f)
                }
            }
        }
    }

    @Test
    fun foldedSheetsStayInsideTheStartSquare() {
        for (model in FoldLibrary.all) {
            val sheets = FoldEngine.build(model)
            for ((i, sheet) in sheets.withIndex()) {
                for (p in sheet.allPoints) {
                    assertTrue("${model.id} sheet $i has a point outside: $p", abs(p.x) <= FoldEngine.D + 0.01f && abs(p.y) <= FoldEngine.D + 0.01f)
                }
                assertTrue("${model.id} sheet $i has too many facets (${sheet.facets.size})", sheet.facets.size <= 40)
            }
        }
    }

    @Test
    fun dogFaceFoldsToATriangleWithTwoEars() {
        val sheets = FoldEngine.build(FoldLibrary.dog)
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
    fun masuCornersMeetAtTheCentre() {
        val sheets = FoldEngine.build(FoldLibrary.masu)
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
        val sheets = FoldEngine.build(FoldLibrary.cup)
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
    fun copyFitsTheBudgets() {
        for (model in FoldLibrary.all) {
            for ((i, step) in model.steps.withIndex()) {
                val zh = step.textZh.count { it.code > 0x2E80 }
                assertTrue("${model.id} step ${i + 1} zh has $zh characters", zh <= 22)
                assertTrue("${model.id} step ${i + 1} en has ${step.textEn.length} characters", step.textEn.length <= 55)
            }
        }
    }

    @Test
    fun stepsHaveOnePickPerLineAndAtMostFourLines() {
        for (model in FoldLibrary.all) {
            for ((i, step) in model.steps.withIndex()) {
                assertTrue("${model.id} step ${i + 1} has ${step.lines.size} lines", step.lines.size <= 4)
                if (step.turns) assertTrue("${model.id} step ${i + 1} needs picks", step.picks.isNotEmpty())
            }
        }
    }
}
