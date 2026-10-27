package io.embodit.foldguide.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app theme: all 16 colour roles fixed to the v0.1.1 pastel palette (the panels are cream cards,
 * so ink labels replace the glass's white ones) and the Noto Sans type family.
 */
@Composable
fun FoldGuideTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val system = systemColorScheme(context)
    val colors =
        remember(system) {
            system.copy(
                fillPrimary = FoldColors.Sand,
                fillSecondary = FoldColors.SandSoft,
                fillTertiary = FoldColors.Oat,
                fillLight = FoldColors.Milk,
                labelPrimaryLight = FoldColors.OnStrong,
                labelPrimary = FoldColors.Ink,
                labelSecondary = FoldColors.InkSecondary,
                labelTertiary = FoldColors.InkTertiary,
                labelQuaternary = FoldColors.InkQuaternary,
                lightenHover = FoldColors.HoverWash,
                lightenPressed = FoldColors.PressWash,
                error = FoldColors.Coral,
                alert = FoldColors.Honey,
                passable = FoldColors.Mint,
                interaction = FoldColors.MistBlue,
                dividerLine = FoldColors.Divider,
            )
        }
    val family = rememberFoldFontFamily()
    val base = PicoTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    PicoTheme(colorScheme = colors, typography = typography, content = content)
}
