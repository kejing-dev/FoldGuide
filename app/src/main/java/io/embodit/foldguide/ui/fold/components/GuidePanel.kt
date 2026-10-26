package io.embodit.foldguide.ui.fold.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import io.embodit.foldguide.R
import io.embodit.foldguide.domain.fold.FoldLibrary
import io.embodit.foldguide.domain.fold.StartPose
import io.embodit.foldguide.domain.fold.StepKind
import io.embodit.foldguide.ui.fold.FoldEvent
import io.embodit.foldguide.ui.fold.FoldUiState
import io.embodit.foldguide.ui.fold.Mode
import io.embodit.foldguide.ui.fold.scene.PanelSpec
import io.embodit.foldguide.ui.theme.FoldColors

/**
 * The guide panel (440 x 300 dp at 0.75, design `panels[0]`): one panel whose content follows the
 * mode: loading, welcome, library, finding desk, place demo, the step (fold / flip / form), done,
 * resume, no desk, no permission. Never more than one primary + one secondary + one icon button.
 */
@Composable
internal fun GuidePanel(state: FoldUiState, filled: Boolean, onEvent: (FoldEvent) -> Unit) {
    val (w, h) = PanelSpec.GUIDE_SIZE
    Glass(w.dp, h.dp) {
        if (!filled) return@Glass
        when (state.mode) {
            Mode.LOADING -> Loading()
            Mode.WELCOME -> Welcome(onEvent)
            Mode.LIBRARY -> Library(state, onEvent)
            Mode.FINDING_DESK -> FindingDesk(onEvent)
            Mode.PLACE_DEMO -> PlaceDemo(state, onEvent)
            Mode.STEP -> StepContent(state, onEvent)
            Mode.DONE -> Done(state, onEvent)
            Mode.RESUME -> Resume(state, onEvent)
            Mode.NO_DESK -> NoDesk(onEvent)
            Mode.NO_PERMISSION -> NoPermission(state, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.Loading() {
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PicoTheme.colorScheme.labelPrimary, progressSize = CircularProgressDefaults.circleProgressSize(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = rs(R.string.loading), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 1)
    }
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun ColumnScope.Welcome(onEvent: (FoldEvent) -> Unit) {
    Text(text = rs(R.string.welcome_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
        Pictogram(R.drawable.ic_paper, rs(R.string.welcome_paper), tint = FoldColors.KamiRose)
        Pictogram(R.drawable.ic_table, rs(R.string.welcome_table), tint = PicoTheme.colorScheme.labelPrimary)
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.welcome_body), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("welcome_go", rs(R.string.welcome_go), { onEvent(FoldEvent.WelcomeDone) }, primary = true, minWidth = 160.dp) }
}

@Composable
private fun Pictogram(iconRes: Int, caption: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(40.dp), tint = tint)
        Text(text = caption, color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.Library(state: FoldUiState, onEvent: (FoldEvent) -> Unit) {
    TitleRow(title = rs(R.string.library_title), trailing = { Key("gear", R.drawable.ic_gear, rs(R.string.action_settings)) { onEvent(FoldEvent.SettingsClicked) } })
    val firstTime = state.progress.done.isEmpty()
    for ((i, m) in FoldLibrary.all.withIndex()) {
        val done = m.id in state.progress.done
        val level = "●".repeat(m.level) + "○".repeat(3 - m.level)
        RowButton(
            name = "model_${m.id}",
            text = modelName(m, state.language),
            sub = (if (i == 0 && firstTime) rs(R.string.library_first) + " " else "") + rs(R.string.library_meta, m.steps.size, m.minutes),
            trailing = (if (done) "✓  " else "") + level,
            highlighted = i == 0 && firstTime,
            onClick = { onEvent(FoldEvent.ModelPicked(i)) },
            height = 48.dp,
        )
    }
}

@Composable
private fun ColumnScope.FindingDesk(onEvent: (FoldEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painter = painterResource(R.drawable.ic_look_down), contentDescription = null, modifier = Modifier.size(36.dp), tint = PicoTheme.colorScheme.labelPrimary)
        Text(text = rs(R.string.finding_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(modifier = Modifier.weight(1f))
    // indeterminate: a value that sweeps 0 -> 1 forever
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart)) }
    LinearProgressIndicator({ sweep.value }, Modifier.fillMaxWidth())
    Text(text = rs(R.string.finding_body), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 1)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("cancel", rs(R.string.action_cancel), { onEvent(FoldEvent.CancelClicked) }) }
}

@Composable
private fun ColumnScope.PlaceDemo(state: FoldUiState, onEvent: (FoldEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PoseDiagram(diamond = state.model.pose == StartPose.DIAMOND, size = 64.dp)
            Text(text = rs(R.string.pose_colour_down), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 1)
        }
        Text(text = rs(R.string.place_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.place_body), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 2)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("cancel", rs(R.string.action_cancel), { onEvent(FoldEvent.CancelClicked) }) }
}

/** S08 / S09 / S10: the step. */
@Composable
private fun ColumnScope.StepContent(state: FoldUiState, onEvent: (FoldEvent) -> Unit) {
    val model = state.model
    val step = state.currentStep
    val n = model.steps.size
    val last = state.lastStep
    TitleRow(
        title = modelName(model, state.language),
        leading = { Key("back", R.drawable.ic_back, rs(R.string.action_back)) { onEvent(FoldEvent.BackClicked) } },
        trailing = { Key("more", R.drawable.ic_more, rs(R.string.action_more)) { onEvent(FoldEvent.MoreClicked) } },
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = rs(R.string.step_count, state.step + 1, n), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 1)
        Spacer(modifier = Modifier.weight(1f))
        StepDots(count = n, index = state.step)
    }
    if (state.controllerActive) {
        Text(text = rs(R.string.step_controller), color = PicoTheme.colorScheme.alert, style = FoldType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        if (step.kind == StepKind.FLIP) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlipDiagram(size = 56.dp)
                Text(text = stepText(step, state.language), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        } else {
            Text(text = stepText(step, state.language), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            state.step == 0 -> Text(text = rs(if (model.pose == StartPose.DIAMOND) R.string.step_first_diamond else R.string.step_first_square), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            step.kind == StepKind.FORM -> Text(text = rs(R.string.step_form_hint), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            step.kind == StepKind.DRAW -> Text(text = rs(R.string.step_draw_hint), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        step.legend?.let { legend -> LegendChip(rs(if (legend == "m") R.string.legend_mountain else R.string.legend_valley)) }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val primaryText = if (step.kind == StepKind.FLIP) rs(R.string.action_flipped) else if (last) rs(R.string.action_done) else rs(R.string.action_next)
        val useDwell = step.kind != StepKind.FLIP && step.kind != StepKind.FORM && !last
        if (useDwell) {
            DwellRing(enabledSetting = state.settings.dwell) {
                WordButton("next", primaryText, { onEvent(FoldEvent.NextClicked) }, primary = true, minWidth = 112.dp)
            }
        } else {
            Box(modifier = Modifier.padding(3.dp)) { WordButton("next", primaryText, { onEvent(FoldEvent.NextClicked) }, primary = true, minWidth = 112.dp) }
        }
        WordButton("prev", rs(R.string.action_back_step), { onEvent(FoldEvent.PrevClicked) }, enabled = state.step > 0, minWidth = 96.dp)
        Spacer(modifier = Modifier.weight(1f))
        if (step.kind != StepKind.DRAW && step.kind != StepKind.FLIP) Key("replay", R.drawable.ic_replay, rs(R.string.action_replay)) { onEvent(FoldEvent.ReplayClicked) }
    }
}

/** Read-only legend chip (sui-chip semantics): a small capsule with the symbol and its meaning. */
@Composable
private fun LegendChip(text: String) {
    Box(modifier = Modifier.clip(CircleShape).height(28.dp).padding(horizontal = 0.dp)) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).height(28.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 1)
        }
    }
}

@Composable
private fun ColumnScope.Done(state: FoldUiState, onEvent: (FoldEvent) -> Unit) {
    Spacer(modifier = Modifier.weight(1f))
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DoneMark()
        Text(text = rs(R.string.done_title, modelName(state.model, state.language)), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(text = modelResult(state.model, state.language), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        WordButton("again", rs(R.string.action_again), { onEvent(FoldEvent.AgainClicked) }, primary = true, minWidth = 120.dp)
        WordButton("library", rs(R.string.action_library), { onEvent(FoldEvent.BackClicked) }, minWidth = 120.dp)
    }
}

@Composable
private fun ColumnScope.Resume(state: FoldUiState, onEvent: (FoldEvent) -> Unit) {
    val cur = state.resume
    val model = FoldLibrary.byId(cur?.modelId) ?: state.model
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("back", R.drawable.ic_back, rs(R.string.action_back)) { onEvent(FoldEvent.BackClicked) }
        Text(text = rs(R.string.resume_title, modelName(model, state.language), (cur?.step ?: 0) + 1), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.resume_body), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.small, maxLines = 2)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) { WordButton("resume", rs(R.string.action_resume), { onEvent(FoldEvent.ResumeClicked) }, primary = true, minWidth = 140.dp) }
}

@Composable
private fun ColumnScope.NoDesk(onEvent: (FoldEvent) -> Unit) {
    Text(text = rs(R.string.nodesk_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 1)
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.nodesk_body), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 2)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WordButton("rescan", rs(R.string.action_rescan), { onEvent(FoldEvent.RescanClicked) }, primary = true)
        WordButton("library", rs(R.string.action_library), { onEvent(FoldEvent.BackClicked) })
    }
}

@Composable
private fun ColumnScope.NoPermission(state: FoldUiState, onEvent: (FoldEvent) -> Unit) {
    Text(text = rs(R.string.perm_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.big, maxLines = 1)
    Spacer(modifier = Modifier.weight(1f))
    Text(text = rs(R.string.perm_body), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 2)
    Spacer(modifier = Modifier.weight(1f))
    Row(modifier = Modifier.fillMaxWidth()) {
        WordButton("allow", rs(if (state.permissionTries >= 1) R.string.action_system_settings else R.string.action_allow), { onEvent(FoldEvent.AllowClicked) }, primary = true, minWidth = 140.dp)
    }
}
