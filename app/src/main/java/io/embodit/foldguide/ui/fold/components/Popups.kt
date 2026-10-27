package io.embodit.foldguide.ui.fold.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import io.embodit.foldguide.BuildConfig
import io.embodit.foldguide.R
import io.embodit.foldguide.domain.model.Hand
import io.embodit.foldguide.domain.model.LanguagePref
import io.embodit.foldguide.ui.fold.FoldEvent
import io.embodit.foldguide.ui.fold.FoldUiState
import io.embodit.foldguide.ui.fold.debug.probe
import io.embodit.foldguide.ui.fold.scene.PanelSpec

/**
 * Settings (400 x 320 dp at 0.75, S13; design `panels[1]`), a pooled attachment panel above the guide
 * panel (a Stage popup): language, main hand, look-to-advance, clear history, about. "Clear history"
 * swaps the content for the confirm strip (S18; no floating dialog in a Stage).
 */
@Composable
internal fun SettingsPanel(state: FoldUiState, filled: Boolean, onEvent: (FoldEvent) -> Unit) {
    val (w, h) = PanelSpec.SETTINGS_SIZE
    Glass(w.dp, h.dp, corner = 24.dp, popup = true, padding = 14.dp) {
        if (!filled) return@Glass
        if (state.confirmClear) {
            Text(text = rs(R.string.settings_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.title, maxLines = 1)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = rs(R.string.confirm_clear), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.step, maxLines = 2)
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WordButton("confirm_cancel", rs(R.string.action_cancel), { onEvent(FoldEvent.ClearCancelled) })
                WordButton("confirm_clear", rs(R.string.action_clear), { onEvent(FoldEvent.ClearConfirmed) }, danger = true)
            }
            return@Glass
        }
        Text(text = rs(R.string.settings_title), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.title, maxLines = 1)
        SettingRow(rs(R.string.settings_language)) {
            Segments(
                items = listOf("lang_system" to rs(R.string.lang_system), "lang_zh" to rs(R.string.lang_zh), "lang_en" to rs(R.string.lang_en)),
                selected = state.settings.language.ordinal,
                onSelect = { onEvent(FoldEvent.LanguageSelected(LanguagePref.entries[it])) },
                itemWidth = 76.dp,
            )
        }
        SettingRow(rs(R.string.settings_hand)) {
            Segments(
                items = listOf("hand_right" to rs(R.string.hand_right), "hand_left" to rs(R.string.hand_left)),
                selected = state.settings.hand.ordinal,
                onSelect = { onEvent(FoldEvent.HandSelected(Hand.entries[it])) },
                itemWidth = 84.dp,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(text = rs(R.string.settings_dwell), color = PicoTheme.colorScheme.labelPrimary, style = FoldType.small, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(state.settings.dwell, { on -> onEvent(FoldEvent.DwellSelected(on)) }, Modifier.probe("dwell_switch") { onEvent(FoldEvent.DwellSelected(!state.settings.dwell)) })
        }
        Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WordButton("clear", rs(R.string.settings_clear), { onEvent(FoldEvent.ClearClicked) }, enabled = state.progress.done.isNotEmpty() || state.progress.current != null, minWidth = 120.dp)
            Text(text = rs(R.string.settings_about_body, BuildConfig.VERSION_NAME), color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, color = PicoTheme.colorScheme.labelPrimary, style = FoldType.small, maxLines = 1, modifier = Modifier.weight(1f))
        content()
    }
}

/** A SegmentControl whose items are [itemWidth] x [itemHeight]; the selected item is mist blue. */
@Composable
internal fun Segments(
    items: List<Pair<String, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    itemWidth: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp = KeyHeight,
    small: Boolean = false,
) {
    SegmentControl(modifier = Modifier.width(itemWidth * items.size).height(itemHeight), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        items.forEachIndexed { i, (name, text) ->
            val onClick = { onSelect(i) }
            SegmentItem(
                selected = selected == i,
                onClick = onClick,
                modifier = Modifier.width(itemWidth).height(itemHeight).probe(name, onClick),
                textStyle = if (small) PicoTheme.typography.labelMedium else PicoTheme.typography.labelLarge,
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = PicoTheme.colorScheme.labelPrimary,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.interaction,
                        selectedItemContentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Text(text = text, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                },
            )
        }
    }
}

/** The step panel's more menu (320 x 232 dp at 0.75): move the demo / start over / settings / library. */
@Composable
internal fun MenuPanel(filled: Boolean, onEvent: (FoldEvent) -> Unit) {
    val (w, h) = PanelSpec.MENU_SIZE
    Glass(w.dp, h.dp, corner = 24.dp, popup = true, padding = 8.dp) {
        if (!filled) return@Glass
        RowButton("menu_move", rs(R.string.menu_move), "", "", false, { onEvent(FoldEvent.MenuMove) }, height = 48.dp)
        RowButton("menu_restart", rs(R.string.menu_restart), "", "", false, { onEvent(FoldEvent.MenuRestart) }, height = 48.dp)
        RowButton("menu_settings", rs(R.string.menu_settings), "", "", false, { onEvent(FoldEvent.MenuSettings) }, height = 48.dp)
        RowButton("menu_home", rs(R.string.menu_home), "", "", false, { onEvent(FoldEvent.MenuHome) }, height = 48.dp)
    }
}
