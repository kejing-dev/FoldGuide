package io.embodit.foldguide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The v0.1.1 palette (user, 2026-10-26: "low saturation, fresh and cute"): cream paper cards with ink
 * text, mist pink, mint and mist blue. The only file with colour literals. All 16 `ColorScheme` roles
 * are fixed here and injected into `PicoTheme` (panels are opaque cream cards, so no role follows the
 * system Vibrant scheme). The brand tokens colour the 3D content (demo sheet, guides, reticle).
 */
object FoldColors {
    // ---- ColorScheme roles (panels: cream card, ink text)
    val Ink = Color(0xFF3F3A4D) // design-style: fixed-figma-color v011 ink: labelPrimary, 10.6:1 on the cream card
    val InkSecondary = Color(0xFF6B6477) // design-style: fixed-figma-color v011 inkSecondary: labelSecondary, 5.3:1 on cream
    val InkTertiary = Color(0xFF8E8798) // design-style: fixed-figma-color v011 inkTertiary: labelTertiary
    val InkQuaternary = Color(0xFFB8B1C0) // design-style: fixed-figma-color v011 inkQuaternary: labelQuaternary, disabled
    val OnStrong = Color(0xFFFFFFFF) // design-style: fixed-figma-color v011 onStrong: labelPrimaryLight
    val Cream = Color(0xFFFFF8EF) // design-style: fixed-figma-color v011 cream: the panel card
    val Sand = Color(0xFFEFE3D6) // design-style: fixed-figma-color v011 sand: fillPrimary, selected segment
    val SandSoft = Color(0xFFF4EAE0) // design-style: fixed-figma-color v011 sandSoft: fillSecondary
    val Oat = Color(0xFFF2E7DB) // design-style: fixed-figma-color v011 oat: fillTertiary, plain buttons
    val Milk = Color(0xFFFFFDF9) // design-style: fixed-figma-color v011 milk: fillLight
    val HoverWash = Color(0x143F3A4D) // design-style: fixed-figma-color v011 hoverWash: lightenHover (ink 8 %)
    val PressWash = Color(0x243F3A4D) // design-style: fixed-figma-color v011 pressWash: lightenPressed (ink 14 %)
    val Divider = Color(0xFFE6DACC) // design-style: fixed-figma-color v011 divider: dividerLine
    val MistBlue = Color(0xFFAFCBEB) // design-style: fixed-figma-color v011 mistBlue: interaction (primary buttons, ink text 7.4:1)
    val Mint = Color(0xFF9FD8BF) // design-style: fixed-figma-color v011 mint: passable, done check
    val Honey = Color(0xFFA86B28) // design-style: fixed-figma-color v011 honey: alert (text on cream, 4.4:1)
    val Coral = Color(0xFFF0A79C) // design-style: fixed-figma-color v011 coral: error fill (ink text 6.1:1)

    // ---- brand tokens (3D content and pictograms)
    val MistPink = Color(0xFFEDB8C4) // design-style: fixed-figma-color v011 mistPink: demo sheet colour side, icon background
    val PaperBack = Color(0xFFFBF4EA) // design-style: fixed-figma-color v011 paperBack: demo sheet back
    val FoldWhite = Color(0xFFFFFFFF) // design-style: fixed-figma-color v011 foldWhite: fold line core, dots
    val InkHalo = Color(0xFF4A4458) // design-style: fixed-figma-color v011 inkHalo: fold line halo, dot rims
    val FlapBlue = Color(0xFF7FA6D6) // design-style: fixed-figma-color v011 flapBlue: the moving flap, arrows, reticle dashes
    val LandingMint = Color(0xFF5DBE98) // design-style: fixed-figma-color v011 landingMint: landings, done mark
    val CrossCoral = Color(0xFFE8857A) // design-style: fixed-figma-color v011 crossCoral: "cannot place" cross
    val DeskCream = Color(0xFFF6EBDD) // design-style: fixed-figma-color v011 deskCream: the virtual desk top (D7)
}
