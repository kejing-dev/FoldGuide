# Fold Guide (折纸向导)

Origami coaching app for PICO OS 6. Kotlin, PICO Spatial SDK 6.1.9, one Mixed Stage (Full Space,
passthrough), package `io.embodit.foldguide`. A life-size (15 cm) demo sheet lies on your desk beside your
own paper; for every step it draws the fold line, tints the flap that moves, arches an arrow to the landing
and then folds itself three times, stopping folded. You fold your real paper along. No paper detection, no
aligning (gate B decision). v0.1.1 (see "v0.1.1 polish" below): pastel palette, 18 models on six shelves, and
printer paper (A4 / Letter) besides the 15 cm square.

Design package (binding): `../spatial-app-factory/apps/fold-guide/design-package.json` (archetype D,
container STAGE_MIXED, window model single_panel_with_popup, 14 states S00 - S18, copy, fold data).
Mockup: `../spatial-app-factory/apps/fold-guide/sample.html` (its `folds.preview.js` holds the same fold
data and engine rules as `FoldLibrary.kt` / `FoldEngine.kt`). Reference project (same skeleton):
`../spatial-ruler` (its `AGENTS.md` holds the SDK findings this app relies on; `../fire-plan` is the
sibling built from it the same week).

Built by the factory's build station on 2026-10-25 (v0.1.0, debug; v0.1.1 polish on pc1 2026-10-26) and self-tested on emulator-5564 the same
day (see "Verified on the emulator"); QA does the full pass next (see "QA hooks").

## v0.1.1 polish (pc1, 2026-10-26; user feedback after trying v0.1.0 on the headset)

User, verbatim: "颜色现在有点丑，可以用低饱和色吗，清新可爱一点，然后library 太少了，就4个，要多一点，另外打印纸尺寸
的也支持，不要只有方的".

- **Palette** (`ui/theme/FoldColors.kt`): cream paper cards with ink text instead of the dark glass (opaque root,
  `// design-style: opaque-root`; Stage attachment panels have no system glass switch), mist-blue primary buttons and
  selections, oat plain buttons, mint done, coral danger. 3D: mist-pink colour side, cream back, ink halo under the white
  fold line, mist-blue flap / arrows / reticle dashes, mint landings. All 16 `ColorScheme` roles are set in
  `FoldGuideTheme`. Icon: dusty mist-pink tile, cream sheet, mist-blue folded corner. `verify-design-style.sh` PASS.
- **Library: 18 models, six shelves** (入门 / 动物 / 花草 / 日常 / 盒子 / 飞机): basics, square from printer paper; puppy,
  kitten, fox, cicada, whale; tulip, four-petal flower; heart, envelope, paper hat; cup, masu, card wallet; paper arrow,
  dart, glider (Nakamura lock). The library panel: paper chip + gear, a SegmentControl of shelves, two-column tiles.
- **Fold data is authored in Python** (`tools/foldlib/`): `engine.py` (port of FoldEngine, plus layer-range folds, cut,
  turn 45 degrees, reset), `models.py` (the four v0.1.0 models verbatim, the printer-paper prep, checks),
  `library_v011.py` (the new models), `render.py` (one PNG per model and paper: every step with line, flap, landing),
  `gen_kotlin.py` -> `domain/fold/FoldLibraryData.kt` (GENERATED, do not edit). Workflow: edit the Python, run
  `python -I tools/foldlib/models.py` (landings <= 0.5 mm, bounds, copy budgets), `render.py build/foldlib`, look at the
  pictures, `gen_kotlin.py`, `subset_cjk_font.py` (new Chinese characters), unit tests.
- **Paper** (`Paper` SQUARE / A4 / LETTER, `Settings.paper`, asked once after the welcome: S19 `Mode.PAPER`, the chip
  on S07 opens it again). Printer-paper models (makesquare, hat, wallet, dart, glider) are authored on the paper's own
  proportions; square models on printer paper start with the prep: fold the near-right corner up onto the left edge,
  crease the strip, cut it off (StepKind.CUT: the strip slides away and fades), unfold (`leavesCrease`, `resetTo`
  SQUARE), and for diamond models turn the square 45 degrees (StepKind.ROTATE, `resetTo` DIAMOND). Model units stay
  150 per sheet width; `FoldModel.scale` (1.4 on A4, 1.44 on Letter) scales the paper root, so the demo is life-size;
  the guide panel's offsets scale with it. `CurrentFold` remembers the paper.
- **No scanning stall, no walking** (factory rules D7 / B17): S02 waits about 5 s for a desk, then the sheet goes on a
  virtual desk top 0.45 m ahead and 0.42 m below the eyes (`FoldScene.placeVirtual`, `VirtualDeskView`, cream plate
  under the sheet); "Put it in front" on S02 / S03 / S15 does it at once. S15 now offers it as the primary action.
- **Headset install**: v0.1.0 on the headset was signed by kj-home-pc's debug key; `install -r` from pc1 fails with
  INSTALL_FAILED_UPDATE_INCOMPATIBLE, so it was uninstalled first (its progress was lost).

Debug hooks added: `paper <square|a4|letter>`, `category <shelf>`, `virtual`, `state S19`; controls `paper_chip`,
`paper_<id>`, `cat_<shelf>`, `in_front`, `model_<id>`. v0.1.1 screenshots: `../spatial-app-factory/apps/fold-guide/build/
v011-contact.png` (cut, turn, A4 dog, cicada / dart / heart done, Letter hat, glider) and `v011-contact-2.png` (S01, S19,
S07 A4 animals / square basics, S15, S03 on A4, virtual desk, S08, S13), taken on emulator-5562 (PICO_6_1_Factory2).

## Structure

```
app/src/main/java/io/embodit/foldguide/
  Main.kt                      DefaultStage { FoldGuideTheme { FoldScreen() } }
  platform/                    SpatialApplication (launch), LaunchActivity, FoldGraph (wiring), FoldLog
    sense/PlaneTracker         plane detection -> PlaneSurface (Stage metres)            [from Spatial Ruler]
    tracking/HeadTracker       HMD pose, block reason (permission)                        [from Spatial Ruler]
    input/ControllerInput      controller ray, trigger, thumbstick                        [from Fire Plan]
    input/SystemPointer        what is known about the gaze + pinch pointer               [from Spatial Ruler]
  domain/model                 Vec3 / Quat4, PlaneSurface, Settings, Progress, LanguagePref, Hand
  domain/fold                  Geometry (2D), FoldEngine (fold-all / fold-top / layer range / flip / crease / cut /
                               turn 45, animParts), FoldLibrary (Paper, Category, FoldEntry, FoldModel per paper),
                               FoldLibraryData (GENERATED by tools/foldlib/gen_kotlin.py)
  domain/usecase               DeskUseCase (which planes are desks, the sheet's yaw), HitSurface, FacePanel,
                               RestBody, ViewFrame                                        [last four from Spatial Ruler]
  data/FoldRepository          SharedPreferences JSON: progress (done set + current model/step), settings
  ui/theme                     FoldColors (the only colour literals), FoldGuideTheme (PicoTheme, 16 roles), typography
  ui/fold                      FoldViewModel (the state machine S00 - S18), FoldUiState / FoldEvent, FoldScreen
  ui/fold/components           FoldStage (SpatialView + panel pool), GuidePanel (every mode), Popups (settings +
                               confirm strip, more menu), Diagrams (pose / flip pictograms, step dots, dwell ring), Common
  ui/fold/scene                FoldScene (ECS: one frame System; taps, drags, reticle, panel placement), PaperView
                               (the demo sheet: facet meshes under hinge entities, fold / crease / flip motion),
                               GuideViews (fold lines, flap tint, arrows, landings, creases, dots), FormView
                               (cup / masu hinge keyframes), ReticleView, PanelRig, Follower, PlaneColliders, SceneKit
  ui/fold/debug/FoldProbe      seams for the debug receiver (no-ops in release)
app/src/debug/                 DebugCommandReceiver + DebugCommands (the QA hooks)
app/src/test/                  FoldEngineTest: every landing within 0.5 mm, bounds, flip mirror, copy budgets
tools/                         subset_cjk_font.py (Noto Sans SC subset, 270 chars), make_icon.py (512 PNG)
```

One direction of data: panel controls, scene callbacks (desk taps, plane counts, "demo played once",
controller presence) and debug hooks -> `FoldViewModel.onEvent` -> `FoldUiState` -> `FoldScene.render`
(rebuilds the sheet and guides only when model / step / serial / mode / ghost change) and the panels.
Per-frame work (hinge angles, lift, flip roll, done spin, reticle, lazy follow, thumbstick) lives in
`FoldScene.onFrame` only and never goes through Compose.

## The demo sheet

- Paper-local frame: x right, y up, z towards the user; model millimetres (x right, y away) -> metres
  (`Pt.local()`), placed at `DemoPose(center, yaw)` on the desk. The sheet's "away" axis is the horizontal
  direction from the viewer to the pinch point at placement.
- Facets (convex polygons with a layer `z` and a colour-side flag) become two meshes per group: rose faces
  and paper-white faces, one-sided each (`MaterialCullingMode.BACK`), layer gap 0.35 mm, paper 0.3 mm thick.
- A fold / crease step: the facets that move hang under a hinge entity on the fold line (pivot chosen so
  the flap lands just above the stay layers; below for a mountain fold). Motion: pause 0.6 s, turn 0.9 s
  (ease in-out), hold 0.9 s, three loops, then it stays folded (fold) or flat (crease) and the guides fade
  to 35 %. Mountain folds lift the whole sheet 40 mm first. Flip: the sheet rolls about its front-back
  axis (0.9 s, twice) lifting by its half width. Form steps: `FormView` hinge models lifted 150 mm (cup: two
  plates opening on the base crease; masu: base + four walls + two tucking points), keyframes tweened
  in 0.25 s and held 0.9 s. Done: the finished sheet turns slowly (6 s per turn).
- Guides on the sheet: fold line = white 1.2 mm core over graphite 2.2 mm halo, valley 6 / 4 dashes,
  mountain 6 / 1.6 / 1.4 / 1.6; flap = amber 22 % + 0.6 mm edge; arrow = amber arc (28 % rise) from the pick
  to its mirror image with a two-stroke head; landing = jade ring (8 mm) or 1.1 mm segment; earlier creases
  = white 30 %, 0.4 mm; draw dots = white discs with a graphite rim. One mesh per appearance, a yield between.
- Reticle while placing: the model's start pose (square / diamond) as rose 25 % fill + amber dashes gliding
  on the desk under the gaze (head forward ray against the desk planes); a coral cross where the gaze is not
  on a desk; frozen while the gaze is on the guide panel. Pinch = system tap on a desk plane collider.

## Interaction

- Three actions only (design `input.rule`): gaze + pinch on buttons and on the desk (place), pinch-drag on the
  sheet turns it about its vertical axis (0.5 degree per view pixel; back to its designed yaw 2 s after release),
  look-to-advance on "Next".
- Look-to-advance (rework 1, QA round 1 finding q1-1): the system never tells the app where its pointer hovers
  (no Compose Enter / Exit from the PICO pointer), so the gaze is the head's forward ray, tested every frame in
  `FoldScene.dwellFrame` against the "Next" button: the ray meets the guide panel's plane, the hit is mapped to
  the panel's root pixels (panel half size in metres <-> `root.size` px) and compared with the bounds the button
  registers in `DwellTarget` (`DwellRing`'s `onGloballyPositioned`, every build). Counting runs on the wall
  clock (the idle emulator renders 1 - 4 frames a second), starts only once the gaze has been off the button
  since the step began and the demo has turned once (`dwellArmed`), fills the ring in 1 s (`DwellTarget.progress`,
  written in 0.05 steps) and sends `DwellFired` -> `NextClicked`; leaving the button clears it; "Flipped" / "Done"
  never use it. Setting in the popup; debug builds start with it off (design). Logs `DWELL start|cleared|fire|advance`.
- Controller: when `ControllerTrackingProvider` delivers a pose the step panel shows "put the controller
  down, fold by hand · stick left / right turns the demo" and the thumbstick x turns the sheet (dead zone
  0.15, 1.6 rad/s), returning after 2 s like a drag (factory rule D1; the sheet has no other target to snap to
  than the placement reticle, which fixes its yaw towards the viewer at placement).
- Desk: horizontal planes (|n.y| > 0.95) 0.45 - 1.15 m above the floor; TABLE planes always count. 20 s
  without one = "Can't find a desk". Plane tracking stays on after placement (see Deviations).
- Panels: the guide panel lazy-follows (0.6 m, yaw only; its elevation = gaze elevation - 15 degrees, clamped to
  0 - 25 degrees below the horizontal, so it does not dive onto the desk when the user looks down; tilted to face
  the head) until the sheet is down; on entering S03 it is planted once on the desk at the first reticle hit; after placement it stands
  at (+110, +200) mm of the sheet (left hand: -110), 30 mm up, leaning back 20 degrees, turned to the head of
  that moment. Settings and the more menu are pooled panels above it (no `Menu` popup, no `AlertDialog`:
  Spatial Ruler findings). Copy: `res/values` (en) + `res/values-zh`, read through `rs()` so the in-app
  language switch works without a restart; model names and step text come from `FoldLibrary` in both languages.

## QA hooks (debug build only)

```
adb -s <serial> shell "am broadcast -a io.embodit.foldguide.DEBUG -p io.embodit.foldguide --es cmd '<command>'"
```

| command | effect |
|---|---|
| `seed` | the demo sheet on the desk ahead: rays 10 - 60 degrees below the gaze against the desk planes, nearest hit wins (the near edge of the table / bed platform in front); else the nearest desk, else 0.6 m ahead and 0.45 m down |
| `state S00 [hold]`, `S01`, `S02`, `S03`, `S07`, `S08 [model] [step]`, `S09 [cup 5]`, `S10 [masu 7]`, `S12 [model]`, `S13`, `S14 [model] [step]`, `S15`, `S17 [second]`, `S18` | straight to that designed state (seeds the sheet when a step needs it). Defaults: S08 = masu 3, S09 = cup 5, S10 = masu 7, S12 = cup, S14 = masu 5 |
| `model <basics|dog|cup|masu>`, `step <n>` | pick a model / jump to a step |
| `next`, `prev`, `replay`, `more`, `back` | the step panel's controls |
| `ghost <0..1>` / `ghost off` | freeze the demo at that share of the fold (0.5 = half way) |
| `dwell on|off`, `lang system|zh|en`, `hand right|left`, `noPlanes [on|off]`, `rotate <deg>`, `clear` | settings and injections |
| `ui <control>` | presses a panel control by name (`DebugCommands.kt` lists them) |
| `dump` | `DUMP {json}` (also files/dump.json): state id, model, step, demo pose, facets and fold lines in Stage metres, guide panel pose |
| `aim [control]` | `AIM next turnRight=.. turnUp=.. dist=.. point=(x,y,z)`: how far to turn the head to look at that guide-panel control, and its Stage point |
| `gaze x,y,z` / `gaze off` | the gaze rests on that Stage point instead of the head's forward (dwell tests when the camera cannot be turned) |
| `pose`, `bounds`, `planes`, `park`, `fps on|off` | scene queries |

Screenshot list for QA (design `states[].qaCapture`): S00 (`state S00 hold`), S01 (`clear`), S07 (and after
`state S12 dog` + `back`: the row with ✓), S02 (within 2 s of `state S02`), S03 (`state S03`, gaze on the
desk: diamond frame), S08 ×4 (`state S08 masu 3`: four lines + centre ring; `state S08 cup 2`: landing at the
left; `state S08 basics 2`: mountain, sheet lifted 40 mm; `state S08 dog 5`: three dots), S08 half-way
(`ghost 0.5` after any S08), S08 finished (wait 9 s), S09 (`state S09`, then again mid-roll), S10 (`state S10`,
mid-keyframe; then `rotate 90`), S12 (`state S12 cup`, `state S12 masu`), S13 (`state S13`; `lang en` then
`state S08`; `hand left` then `state S08`: the panel on the other side), S14 (`state S14`, or force-stop mid
model and relaunch), S15 (`state S15`), S17 (`state S17`), S18 (`state S18`). Console screenshots: take two in a
row (the first often shows the previous frame); at idle fps give a fresh step 3 - 5 s to build its meshes.

Log tag `FoldGuide`: `STATE <Sxx> ...` on every state change, `STEP`, `DEMO placed|seeded`, `PLANES n= desks=`,
`TAP surface|paper|backstop|other`, `DRAG paper start|end`, `DWELL start|fire`, `PANEL desk pose|planted|bound`,
`SCENE attached|step built|detached`, `TRACK controller|blocked`, `FOLLOW`, `CMD`, `DUMP`, `POSE`, `PLANE`, `FPS`.

## Build

```
# PowerShell (Git Bash fails on gradlew: CRLF); no daemon on the shared build machine
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug --no-daemon
.\gradlew.bat testDebugUnitTest --no-daemon        # FoldEngineTest
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> shell monkey -p io.embodit.foldguide -c android.intent.category.LAUNCHER 1
```

Material gate: `grep -rEf ../spatial-app-factory/bin/material-grep.txt --include=*.kt --include=*.kts app/src`
must be empty. Fonts: Noto Sans (four weights) + a 270-character Noto Sans SC subset (`tools/subset_cjk_font.py`,
source: fire-plan's full font), OFL in `third_party/`.

## Deviations from the design package (recorded in the factory's state.json, all within tolerance)

- Fold data lives in Kotlin (`FoldLibrary.kt`, typed, unit-tested) rather than an `assets/folds.json`; same
  fields and numbers as the design table.
- Plane tracking is not stopped after placement: the emulator reports its first plane 20 - 45 s after start,
  so stopping and restarting it would make "move the demo sheet" wait again. Colliders stay; nothing else uses them.
- Form keyframes are tweened (0.25 s hinge-angle interpolation) instead of cross-faded; same keyframes.
- Library rows, menu rows and the legend chip are SpatialUI `Button`s / a plain capsule rather than
  `ListItem` / `Chip` (their slot signatures are not in the vault; same look and metrics).
- The reticle follows the head's forward ray; on the emulator the pinch lands where the mouse is, so the
  frame and the placed sheet can differ when the cursor is off centre (gaze and cursor coincide on a headset).

## Verified on the emulator (build station self-test, emulator-5564 / Pico_Emulator_6_1_b, 2026-10-25)

- Install, launch, S00 -> S01 in 1.1 s; 57 planes / 13 desks reported 3 s after start; no crash in 40 min.
- All 14 designed states captured through the debug broadcast (`apps/fold-guide/build/shots/`, 25 frames: the
  S08 variants masu 3 / cup 2 / basics 2 / dog 5, `ghost 0.5`, finished, S09, S10 + `rotate 90`, S12 cup / masu,
  S13, zh, left hand, S14, S15, S17, S18). The sheet and its guides draw as designed; the flap animation, the
  mountain lift, the flip, the form keyframes and the done spin run; the settings popup and the more menu open
  above the guide panel; the in-app language switch works without a restart.
- 30 open / close cycles of the more menu and the settings popup: the glass still draws, 0 crashes.
- Capture recipe: walk the camera 1.7 m forward (`emu.sh key w -HoldMs 1300`), pitch it 48 degrees down (right
  drags with the toolbar's camera_rotation toggle on, `tools/harness/uia_cam.ps1`), `seed` lands the sheet on the
  bed platform's near edge; `tools/qa_shot.sh`, `tools/qa_all.sh`, `tools/panel_soak.sh`, `tools/contact_sheet.py`.
- Known on the emulator: `ui menu_settings` right after `more` can miss (the menu's controls register two frames
  after it opens): send `more`, wait a second, then `ui menu_settings`. `state S09` captures the end of the
  flip unless taken within a second (the roll lasts 0.9 s).

## Rework 1 (QA round 1, emulator-5560 / Pico_Emulator_6_1_fresh, 2026-10-25)

- q1-1 look-to-advance did not fire: the ring listened to Compose hover events, which the PICO pointer never
  sends. Now counted in the frame system from the head's forward ray (above). Checked on 5560: `state S08 dog 1`,
  `dwell on`, `aim next` -> the button's Stage point; projecting that point with the Spatial Ruler camera model
  (left eye, fx 1286.8 px, principal point 1080 / 1079) lands on the drawn "Next" button in the screencap
  (`apps/fold-guide/build/dwell-aim-check.png`), so the ray -> panel -> pixel mapping is right; `gaze <point>`
  then logs `DWELL start`, `DWELL fire after ~1000 ms`, `DWELL advance`, `STEP dog 2/5` (`build/dwell-*.png`).
  A first attempt counted frame time clamped to 100 ms and reached 300 ms in 3 s at idle fps: hence the wall clock.
- Start-up ANR seen once by QA (47 s on the first open after install): plane colliders are now made three per
  frame from a queue instead of 57 in one burst; start-up on 5560 was `SCENE attached build_ms=610`.

## Not verified

Headset-only: legibility of the 1.2 mm lines in passthrough, look-to-advance with a real head (the head's
forward ray stands in for eye tracking; the emulator check turned a synthetic gaze onto the button), pinch-drag on the sheet with a real hand, pinch while
holding paper, the controller thumbstick (the emulator's controller provider reports DEVICE_NOT_SUPPORTED in
eye mode), the spatial permission flow (S17), the real folding experience.
