package io.embodit.foldguide.ui.fold.scene

/** Ids and dp sizes of every attachment panel (design `panels`); the Compose side declares exactly these, once. */
object PanelSpec {
    const val GUIDE = "guidePanel"
    const val SETTINGS = "settingsPanel"
    const val MENU = "menuPanel"

    /** Width / height in dp. */
    val GUIDE_SIZE = 440f to 300f
    val SETTINGS_SIZE = 400f to 320f
    val MENU_SIZE = 320f to 232f

    /** All panels are shown at 0.75 (1 dp = 0.6 mm): the guide panel is 0.264 x 0.18 m (design `physicalM`). */
    const val SCALE = 0.75f

    val pool: List<String> = listOf(GUIDE, SETTINGS, MENU)

    fun size(id: String): Pair<Float, Float> =
        when (id) {
            GUIDE -> GUIDE_SIZE
            SETTINGS -> SETTINGS_SIZE
            else -> MENU_SIZE
        }

    /** Half width / height of a panel in metres at [scale]. */
    fun halfM(id: String, scale: Float = SCALE): Pair<Float, Float> {
        val (w, h) = size(id)
        return (w * PanelRig.DP_M * scale / 2f) to (h * PanelRig.DP_M * scale / 2f)
    }
}
