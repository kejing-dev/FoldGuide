package io.embodit.foldguide.ui.fold.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.foldguide.domain.fold.FoldModel
import io.embodit.foldguide.domain.fold.Step
import io.embodit.foldguide.domain.model.LanguagePref
import io.embodit.foldguide.ui.fold.debug.probe
import io.embodit.foldguide.ui.theme.FoldColors

/** Panel padding and the corner radius of the glass (design: Material.Regular root, 32 dp corners). */
internal val PanelPadding = 16.dp
internal val KeyWidth = 48.dp
internal val KeyHeight = 48.dp

/** Glass root of a panel: clipped shape, Material.Regular, padding. */
@Composable
internal fun Glass(width: Dp, height: Dp, corner: Dp = 32.dp, material: Material = Material.Regular, padding: Dp = PanelPadding, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(corner)).backgroundMaterial(enable = true, style = material).padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Type roles of the design (`typography.roles`). */
internal object FoldType {
    /** titleLarge 20/26 SemiBold: the big instruction lines. */
    val big: TextStyle
        @Composable get() = PicoTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    /** titleMedium 16/20 SemiBold: model names, panel titles. */
    val title: TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** bodyLarge 16/22 Medium: the step text. */
    val step: TextStyle
        @Composable get() = PicoTheme.typography.bodyLargeMultiline.copy(fontWeight = FontWeight.Medium)

    /** bodyMedium 14/18 Medium: small notes. */
    val small: TextStyle
        @Composable get() = PicoTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    /** labelMedium 12/16 SemiBold: 3 / 9, legend chips, steps · minutes. */
    val label: TextStyle
        @Composable get() = PicoTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")
}

/** True when the UI is in Chinese (the chosen language, or the system's when following it). */
@Composable
@ReadOnlyComposable
internal fun isChinese(language: LanguagePref): Boolean =
    when (language) {
        LanguagePref.ZH -> true
        LanguagePref.EN -> false
        LanguagePref.SYSTEM -> LocalFoldResources.current.configuration.locales[0].language == "zh"
    }

@Composable
@ReadOnlyComposable
internal fun modelName(model: FoldModel, language: LanguagePref): String = if (isChinese(language)) model.nameZh else model.nameEn

@Composable
@ReadOnlyComposable
internal fun modelResult(model: FoldModel, language: LanguagePref): String = if (isChinese(language)) model.resultZh else model.resultEn

@Composable
@ReadOnlyComposable
internal fun stepText(step: Step, language: LanguagePref): String = if (isChinese(language)) step.textZh else step.textEn

/** A 48 x 48 icon key (back, more, settings, replay). */
@Composable
internal fun Key(name: String, iconRes: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val content = if (enabled) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelQuaternary
    IconButton(
        onClick = onClick,
        modifier = Modifier.probe(name, onClick),
        colors = IconButtonDefaults.iconButtonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = content),
        size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
        enabled = enabled,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = description, modifier = Modifier.size(24.dp), tint = content)
    }
}

/**
 * A worded button: capsule, 48 dp high. [primary] = interaction fill with white text (the confirming
 * action), [danger] = error fill with ink text; otherwise the tertiary fill with glass text.
 */
@Composable
internal fun WordButton(
    name: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 104.dp,
) {
    val container =
        when {
            danger -> PicoTheme.colorScheme.error
            primary -> PicoTheme.colorScheme.interaction
            else -> PicoTheme.colorScheme.fillTertiary
        }
    val content =
        when {
            danger -> FoldColors.Ink
            primary -> FoldColors.LabelPrimaryLight
            enabled -> PicoTheme.colorScheme.labelPrimary
            else -> PicoTheme.colorScheme.labelQuaternary
        }
    Button(
        onClick = onClick,
        modifier = modifier.probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = 48.dp, minWidth = minWidth),
        shape = CircleShape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(text = text, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, style = FoldType.title)
    }
}

/** A full-width row button (library rows, menu items): main text, a small trailing figure, optional highlight. */
@Composable
internal fun RowButton(
    name: String,
    text: String,
    sub: String,
    trailing: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    height: Dp = 52.dp,
) {
    val content = PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height).probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = height, minWidth = 300.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (highlighted) PicoTheme.colorScheme.interaction.copy(alpha = 0.32f) else PicoTheme.colorScheme.fillTertiary, contentColor = content),
    ) {
        Text(text = text, color = content, style = FoldType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = sub, color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 1, softWrap = false)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(text = trailing, color = PicoTheme.colorScheme.labelSecondary, style = FoldType.label, maxLines = 1, softWrap = false)
    }
}

/** Title row of a panel: an optional leading key, the title, optional trailing keys. */
@Composable
internal fun TitleRow(title: String, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (leading != null) leading()
        Text(text = title, color = PicoTheme.colorScheme.labelPrimary, style = FoldType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}
