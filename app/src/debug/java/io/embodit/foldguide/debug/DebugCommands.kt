package io.embodit.foldguide.debug

import android.content.Context
import io.embodit.foldguide.domain.fold.Category
import io.embodit.foldguide.domain.fold.Paper
import io.embodit.foldguide.domain.model.Hand
import io.embodit.foldguide.domain.model.LanguagePref
import io.embodit.foldguide.domain.model.Vec3
import io.embodit.foldguide.platform.FoldLog
import io.embodit.foldguide.ui.fold.FoldEvent
import io.embodit.foldguide.ui.fold.debug.FoldProbe
import io.embodit.foldguide.ui.fold.scene.FoldScene
import java.io.File
import java.util.Locale

/**
 * QA hooks of the debug build (design `verification.debugHooks`). One log line (tag `FoldGuide`)
 * answers every command.
 *
 *   adb -s <serial> shell "am broadcast -a io.embodit.foldguide.DEBUG -p io.embodit.foldguide --es cmd '<command>'"
 *
 * - `seed` / `seedDemo`          puts the demo sheet on the nearest table (else in front of the head), facing the viewer
 * - `state <Sxx> [model] [step] [hold]`  straight to a designed state (S00 S01 S02 S03 S07 S08 S09 S10 S12 S13 S14 S15 S17 S18 S19);
 *                                 `state S08 masu 3`, `state S09 cup 5`, `state S10 masu 7`, `state S12 cup`, `state S14 masu 5`,
 *                                 `state S17 second` (the "open system settings" variant), `state S00 hold`
 * - `model <id>`                 picks a model (ids: FoldLibraryData.entries; restarts at step 1 when a step is showing);
 *                                a printer-paper-only model on square paper switches that fold to A4
 * - `paper <square|a4|letter>`   the paper (as on the S19 panel; S07 shows that paper's shelves)
 * - `category <basics|animals|flowers|everyday|boxes|planes>`  the library shelf
 * - `virtual`                    "put it in front": the demo sheet on the virtual desk top ahead (D7)
 * - `step <n>`                   straight to step n of the current model (seeds the demo when needed)
 * - `next` / `prev` / `replay` / `more` / `back`   the step panel's controls
 * - `ghost <0..1>` / `ghost off` freezes the demo at that share of the fold (screenshots), `off` plays again
 * - `dwell on|off`               look-to-advance (debug builds start with it off)
 * - `noPlanes [on|off]`          no plane counts as a desk (S02 -> virtual desk top after 5 s)
 * - `rotate <deg>`               turns the demo sheet about its vertical axis (as a pinch-drag would)
 * - `gaze x,y,z` / `gaze off`     the gaze rests on that Stage point instead of the head's forward (dwell tests)
 * - `aim [control]`              `AIM next turnRight=.. turnUp=.. dist=..`: how far to turn the head to look at that control
 * - `lang system|zh|en`, `hand right|left`, `clear` (history + settings, back to S01)
 * - `ui <control>`               presses a panel control by name: welcome_go, gear, paper_chip, paper_<square|a4|letter>,
 *                                cat_<shelf>, model_<id>, in_front, cancel, back, more, next,
 *                                prev, replay, again, library, resume, rescan, allow, menu_move, menu_restart, menu_settings,
 *                                menu_home, lang_*, hand_*, dwell_switch, clear, confirm_cancel, confirm_clear
 * - `dump`                       `DUMP {...}` (also files/dump.json): state, model, step, demo pose, facets and fold lines in Stage metres
 * - `pose`, `bounds`, `planes`, `park`, `fps on|off`
 */
internal object DebugCommands {

    fun run(context: Context, raw: String, a: String?, s: String?) {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val name = tokens.first().lowercase(Locale.US)
        val rest = tokens.drop(1)
        val text = (s ?: rest.joinToString(" ")).trim()
        val numbers = (a ?: rest.joinToString(",")).split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        val host = FoldProbe.host
        if (host == null) {
            FoldLog.i("CMD $name ignored: the Stage is not running")
            return
        }
        try {
            dispatch(context, host, name, numbers, text)
        } catch (t: Throwable) {
            FoldLog.e("CMD $name failed: $t", t)
        }
    }

    private fun dispatch(context: Context, host: FoldProbe.Host, name: String, numbers: List<Float>, text: String) {
        val scene = host.scene() ?: return FoldLog.i("CMD $name ignored: no scene")
        val fold = host.sceneHandle() as? FoldScene
        val word = text.lowercase(Locale.US)
        when (name) {
            "seed", "seeddemo" -> fold?.seedDemo()
            "state" -> host.dispatch(FoldEvent.DebugState(text))
            "model" -> host.dispatch(FoldEvent.DebugModel(word))
            "paper" -> Paper.byId(word)?.let { host.dispatch(FoldEvent.DebugPaper(it)) } ?: FoldLog.i("CMD paper unknown $word")
            "category" -> Category.byId(word)?.let { host.dispatch(FoldEvent.CategoryPicked(it)) } ?: FoldLog.i("CMD category unknown $word")
            "virtual" -> host.dispatch(FoldEvent.PlaceInFrontClicked)
            "step" -> host.dispatch(FoldEvent.DebugStep(numbers.firstOrNull()?.toInt() ?: 1))
            "next" -> host.dispatch(FoldEvent.NextClicked)
            "prev" -> host.dispatch(FoldEvent.PrevClicked)
            "replay" -> host.dispatch(FoldEvent.ReplayClicked)
            "more" -> host.dispatch(FoldEvent.MoreClicked)
            "back" -> host.dispatch(FoldEvent.BackClicked)
            "ghost" -> host.dispatch(FoldEvent.DebugGhost(if (word == "off") null else (numbers.firstOrNull() ?: 0.5f).coerceIn(0f, 1f)))
            "dwell" -> host.dispatch(FoldEvent.DebugDwell(word != "off"))
            "noplanes" -> {
                val on = word != "off"
                fold?.setNoPlanes(on)
                if (on) host.dispatch(FoldEvent.DebugNoPlanes)
            }
            "rotate" -> fold?.setSpin(numbers.firstOrNull() ?: 0f)
            "aim" -> FoldLog.i(fold?.aimLine(text.ifEmpty { "next" }) ?: "AIM: no scene")
            "gaze" -> {
                fold?.gazeOverride = if (word == "off" || numbers.size < 3) null else Vec3(numbers[0], numbers[1], numbers[2])
                FoldLog.i("GAZE override=${fold?.gazeOverride?.let { FoldLog.pos(it) } ?: "off"}")
            }
            "lang" ->
                host.dispatch(
                    FoldEvent.LanguageSelected(
                        when (word) {
                            "zh" -> LanguagePref.ZH
                            "en" -> LanguagePref.EN
                            else -> LanguagePref.SYSTEM
                        }
                    )
                )
            "hand" -> host.dispatch(FoldEvent.HandSelected(if (word == "left") Hand.LEFT else Hand.RIGHT))
            "clear" -> host.dispatch(FoldEvent.DebugClear)
            "ui" -> FoldLog.i(if (host.pressControl(text)) "UI $text" else "UI $text: no such control is composed")
            "dump" -> {
                val json = org.json.JSONObject(host.dumpJson())
                json.put("scene", scene.sceneJson())
                val line = json.toString()
                FoldLog.i("DUMP $line")
                runCatching { File(context.filesDir, "dump.json").writeText(line) }
            }
            "pose" -> FoldLog.i(scene.poseLine())
            "bounds" -> {
                scene.panelLines().forEach { FoldLog.i(it) }
                for (panel in listOf("guidePanel", "settingsPanel", "menuPanel")) {
                    for ((ctrl, b) in FoldProbe.controlsOf(panel)) FoldLog.i("CTRL panel=$panel name=$ctrl cx=${b.cx} cy=${b.cy} w=${b.w} h=${b.h}")
                }
            }
            "planes" -> scene.planesSummary().forEach { FoldLog.i(it) }
            "park" -> FoldLog.i(scene.parkPanel())
            "fps" -> FoldLog.i(scene.setFpsLine(word != "off"))
            else -> FoldLog.i("CMD unknown $name")
        }
    }
}
