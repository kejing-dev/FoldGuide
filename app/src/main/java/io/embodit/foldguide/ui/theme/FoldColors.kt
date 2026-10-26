package io.embodit.foldguide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The design's exact colours (design package `palette`). The only file with colour literals. Four
 * `ColorScheme` roles are fixed here and injected into `PicoTheme`; the other twelve inherit the
 * system Vibrant scheme. The brand tokens colour the 3D content (demo sheet, guides, reticle).
 */
object FoldColors {
    // ---- ColorScheme roles with a fixed design value
    val Interaction = Color(0xFF4D8DFF) // design-style: fixed-figma-color interaction: primary button
    val Passable = Color(0xFF5FD3A4) // design-style: fixed-figma-color passable: done check
    val Alert = Color(0xFFFFB648) // design-style: fixed-figma-color alert
    val Error = Color(0xFFFF6B5E) // design-style: fixed-figma-color error: clear button, reticle cross

    // ---- brand tokens (design `palette.brandTokens`)
    val KamiRose = Color(0xFFD9466F) // design-style: fixed-figma-color kamiRose: demo sheet colour side, icon background
    val PaperBack = Color(0xFFF7F4EE) // design-style: fixed-figma-color paperBack: demo sheet back
    val FoldWhite = Color(0xFFFFFFFF) // design-style: fixed-figma-color foldWhite: fold line core, dots
    val GraphiteHalo = Color(0xFF2B2F36) // design-style: fixed-figma-color graphiteHalo: fold line halo
    val FlapAmber = Color(0xFFFFB648) // design-style: fixed-figma-color flapAmber: the moving flap, arrows
    val LandingJade = Color(0xFF5FD3A4) // design-style: fixed-figma-color landingJade: landings
    val Coral = Color(0xFFFF6B5E) // design-style: fixed-figma-color coral: "cannot place" cross
    val Ink = Color(0xFF1F2430) // design-style: fixed-figma-color ink: text on the error fill
    val LabelPrimaryLight = Color(0xFFFFFFFF) // design-style: fixed-figma-color labelPrimaryLight
}
