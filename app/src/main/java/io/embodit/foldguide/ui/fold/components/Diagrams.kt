package io.embodit.foldguide.ui.fold.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import io.embodit.foldguide.ui.theme.FoldColors

/** The paper in its start pose (square / diamond), as a small pictogram (design `spatialUiMap.paperDiagram`). */
@Composable
internal fun PoseDiagram(diamond: Boolean, size: Dp = 72.dp) {
    val fill = FoldColors.PaperBack.copy(alpha = 0.9f)
    val edge = Color.White.copy(alpha = 0.6f)
    Canvas(modifier = Modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension * 0.36f
        val path = Path()
        if (diamond) {
            path.moveTo(c.x, c.y - r)
            path.lineTo(c.x + r, c.y)
            path.lineTo(c.x, c.y + r)
            path.lineTo(c.x - r, c.y)
        } else {
            val s = r * 0.8f
            path.moveTo(c.x - s, c.y - s)
            path.lineTo(c.x + s, c.y - s)
            path.lineTo(c.x + s, c.y + s)
            path.lineTo(c.x - s, c.y + s)
        }
        path.close()
        drawPath(path, fill)
        drawPath(path, edge, style = Stroke(width = 2f))
    }
}

/** Flip pictogram: a light sheet, a coloured sheet, an arc arrow over them. */
@Composable
internal fun FlipDiagram(size: Dp = 72.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRect(FoldColors.PaperBack.copy(alpha = 0.9f), topLeft = Offset(w * 0.14f, h * 0.3f), size = Size(w * 0.3f, h * 0.44f))
        drawRect(FoldColors.KamiRose, topLeft = Offset(w * 0.56f, h * 0.3f), size = Size(w * 0.3f, h * 0.44f))
        val p = Path()
        p.moveTo(w * 0.3f, h * 0.22f)
        p.quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.7f, h * 0.22f)
        drawPath(p, Color.White, style = Stroke(width = 3f, cap = StrokeCap.Round))
        drawLine(Color.White, Offset(w * 0.7f, h * 0.22f), Offset(w * 0.6f, h * 0.2f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.7f, h * 0.22f), Offset(w * 0.66f, h * 0.1f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

/** The done mark: a jade ring with a check. */
@Composable
internal fun DoneMark(size: Dp = 56.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        drawCircle(FoldColors.LandingJade, radius = w * 0.44f, style = Stroke(width = w * 0.07f))
        val p = Path()
        p.moveTo(w * 0.3f, w * 0.52f)
        p.lineTo(w * 0.44f, w * 0.66f)
        p.lineTo(w * 0.72f, w * 0.36f)
        drawPath(p, FoldColors.LandingJade, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
    }
}

/** Read-only step dots (sui-page-control semantics): the current step filled, the others faint. */
@Composable
internal fun StepDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    val on = PicoTheme.colorScheme.labelPrimary
    val off = PicoTheme.colorScheme.labelQuaternary
    Canvas(modifier = modifier.size(width = (count * 14).dp, height = 12.dp)) {
        val step = 14.dp.toPx()
        val r = 3.dp.toPx()
        for (i in 0 until count) {
            drawCircle(if (i == index) on else off, radius = if (i == index) r * 1.25f else r, center = Offset(step * i + step / 2f, size.height / 2f))
        }
    }
}

/**
 * Look-to-advance (design `input.dwell`): wraps the "Next" button. The button's bounds are reported
 * to [DwellTarget]; the scene's frame system counts while the gaze ray (head forward) rests on them
 * after entering since the step began and once the demo has played, and fires `NextClicked` at 1 s.
 * This composable only draws the ring from the progress the scene writes.
 */
@Composable
internal fun DwellRing(enabledSetting: Boolean, content: @Composable () -> Unit) {
    val progress = DwellTarget.progress.floatValue
    val track = Color.White.copy(alpha = 0.12f)
    DisposableEffect(Unit) { onDispose { DwellTarget.bounds = null } }
    Box(
        modifier =
            Modifier.onGloballyPositioned { c ->
                if (c.isAttached) {
                    val root = c.findRootCoordinates()
                    val tl = root.localPositionOf(c, Offset.Zero)
                    val br = root.localPositionOf(c, Offset(c.size.width.toFloat(), c.size.height.toFloat()))
                    DwellTarget.bounds = DwellTarget.Bounds((tl.x + br.x) / 2f, (tl.y + br.y) / 2f, br.x - tl.x, br.y - tl.y, root.size.width, root.size.height)
                }
            }
    ) {
        Box(modifier = Modifier.padding(3.dp)) { content() }
        if (enabledSetting) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                if (progress > 0f) drawArc(Color.White, -90f, 360f * progress, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Suppress("unused") private val keepImports: @Composable () -> Unit = { Box(Modifier.fillMaxSize()) }
