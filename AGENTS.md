# SpaceCube (空间方块)

A PICO Spatial SDK app: a spatial "俄罗斯方块 / Tetris" game for PICO Swan
(Spatial OS). **As of 2026-08-06 this runs as a full-immersion `Stage`**, not
a windowed widget — see "Why this structure" for how/why that changed from
the original "coexist with other windows" premise.

## What this project currently does

- **Single full-immersion `Stage` default container** (declared via
  `AndroidManifest.xml`: `pico.spatial.stage.id` + `pico.spatial.stage.style="1"`
  for `StageStyle.Mixed`) — there is no window/chrome at all; the app opens
  directly into Full Space with passthrough still visible (Mixed style needs
  no custom skybox). All board/UI entities are parented to one `anchor`
  `Entity` ~1m ahead (Stage's origin is the user's feet, not a window center).
  Its **height is measured from the headset at startup**: `HeadHeightCalibration`
  reads `HMDTrackingProvider` once and places the board so the base plate sits
  `BASE_PLATE_BELOW_HEAD_M = 0.5f` below the user's head (2026-08-07 user
  request "读取当前头戴设备的高度，游戏井底座的高度为头显设备向下0.5米处的高度").
  `ANCHOR_HEIGHT_M = 0.8f` survives only as the fallback if the HMD never
  reports a usable pose within ~3s. Note the anchor sits at the board's
  *middle*, so calibration offsets it by `BoardCubeRenderer.basePlateOffsetY`
  (~-0.49m at 18 rows) to land the *base plate* at the requested height.
- **Start screen (a UI state inside the Stage, not a separate window)**:
  title "空间方块", historical high score, a 慢/中/快 difficulty picker, and
  "开始游戏". Tapping it starts the game immediately in the same window.
  A one-hand pinch anywhere while on this screen instead free-drags the
  whole scene (anchor) around in X/Y (2026-08-06, per user request), so the
  board can be repositioned to a comfortable spot before playing — see
  "Gesture input model".
- **Gameplay**: a `BOARD_WIDTH x BOARD_HEIGHT` = **10×18** board (enlarged
  2026-08-06 from an initial 8×14, per user request "宽高大些，以便能容纳更多方块")
  rendered as pooled frosted-jelly ECS cubes, a translucent warm off-white
  base plate under the stack by default (now one of 5 selectable materials —
  see "Base plate material picker" below; "Material / glass look" below has
  the glass look's own history),
  pinch-and-hold-direction movement (left/right/down, see "Gesture input
  model" below), a quick double-pinch to rotate clockwise (either hand),
  a next-piece preview drawn as mini candy blocks in the piece's own color
  (`NextPiecePreview`, replaced an enum-name text label), score HUD, pause/resume,
  and a Game Over overlay with "再来一局" (restart) / "返回开始画面" (back to
  the start-screen state — not a window switch). Tone-based SFX
  (lock/line-clear/game-over) via `ToneGenerator`; no background music
  (needs a real audio asset this project doesn't have).
  The **ghost-piece landing projection is currently OFF** — built and working,
  then disabled 2026-08-07 per user request ("先不开启方块下落位置投影"). It lives
  behind `SHOW_GHOST_PIECE` in `BoardCubeRenderer.kt`; flip that one constant to
  restore it. `GameEngine` still computes `snapshot.ghostCells` regardless, so
  nothing else has to change.
- The game logic itself (`tech.illusion.spacecube.game.*`) is a
  framework-free Kotlin package with 27 JUnit unit tests (`Board`,
  `PieceType`/rotation, `PieceBag`, `FallingPiece`, `Scoring`, `GameEngine`,
  `InMemoryHighScoreStore`) plus one instrumented test for
  `SharedPreferencesHighScoreStore` — see
  `docs/superpowers/plans/2026-08-05-tetris-gameplay.md` for the
  implementation plan this was originally built from (note: that plan
  predates the single-window merge below; read it for the game-logic
  design, not the current window structure).

## Why this structure

Native PICO Spatial SDK was originally chosen over Unity because the user
wanted a spatial *widget* that coexists with other system windows (à la
visionOS-style multitasking), not a fully immersive takeover (see §2 of the
design spec). The container model evolved twice after that:

1. Original design (`docs/superpowers/specs/2026-08-05-spacecube-design.md`
   §6): a Planar entry window that opened a separate Volumetric game window
   on demand.
2. 2026-08-06 (first change): merged into a single Volumetric default
   window with an in-window start screen — still a coexisting window, just
   one window with two states instead of two windows.
3. **2026-08-06 (second change, current): migrated to a full-immersion
   `Stage`.** On-device testing showed the Volumetric window's chrome — a
   gaze-following caption bar with move/minimize controls — kept
   intercepting drag/tap gameplay gestures on the falling piece, making core
   interaction unreliable. The user confirmed abandoning the "coexist with
   other windows" premise in favor of reliable gameplay: a `Stage` has no
   window chrome at all, so nothing is left to steal gesture input.

## Key files

- `app/src/main/AndroidManifest.xml` — the **only** place the default
  `Stage`'s id and `style` are configured; there is no DSL equivalent for the
  default container's properties. `pico.spatial.stage.style="1"` is
  `StageStyle.Mixed` (passthrough stays visible, no skybox needed).
- `app/src/main/java/tech/illusion/spacecube/Main.kt` — `mainApp()`: just
  `DefaultStage { PicoTheme { GamePage() } }`.
- `app/src/main/java/tech/illusion/spacecube/game/` — framework-free game
  logic: `PieceType` (shapes + box rotation), `PieceBag` (7-bag randomizer),
  `Board` (grid/collision/line-clear), `FallingPiece` (position/rotation),
  `Scoring`/`Difficulty` (points, level, fall-speed curve), `GameEngine`
  (state machine + event queue), `HighScoreStore`/`GameSettings`
  (persistence + difficulty hand-off from the start screen to the engine).
- `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt` — owns the
  `GameEngine` + `BoardCubeRenderer`, the `started` boolean (start-screen vs.
  gameplay), the tick loop (`LaunchedEffect` + `delay(engine.currentFallIntervalMs())`),
  `HandGestureController` (all gameplay input - see "Gesture input model"),
  `HeadHeightCalibration` (one-shot HMD height read at startup), the shared
  `AnchorPlacement` holding the anchor's live position, and every
  `AttachmentPanel` (start screen, score/next-piece, pause,
  pause/game-over overlays) — each gameplay panel's content is gated behind
  `if (started)` so it neither renders nor intercepts input before the game
  begins. `anchor` is `remember`-scoped in `GamePage()` (not created inside
  `initial`) because two things move it: calibration and the scene free-drag.
  **Both must go through `AnchorPlacement.moveTo`** — they previously kept
  separate copies of the position, which would have made a drag after
  calibration snap the board back to the stale value. Every other
  entity/attachment is added as a *child* of `anchor`, not directly via
  `content.addEntity` — this is what let the Stage migration keep every
  relative offset unchanged.
  (`handTrackingRoot` and its pinch-indicator children are the one exception
  — added directly to `content`, unrelated to `anchor`'s offset, since raw
  hand-tracking positions need their own coordinate conversion; see
  "Gesture input model".)
- `app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt` —
  renders the board from **two** pools of reusable `ModelEntity` cubes, plus a
  base-plate entity sized to exactly match the board's footprint (a
  wider/farther-out plate was found to be invisible — see "Debugging notes").
  No longer static or necessarily translucent as of the base-plate material
  picker (see "Base plate material picker" below): `setBasePlateMaterial`
  destroys and recreates it on every material swap, and 4 of its 5 selectable
  materials are opaque PBR, not translucent glass. `attachTo(anchor,
  groundMaterial)` parents everything via `anchor.addChild(...)`.
  - `lockedCubes` — one per grid cell, for LOCKED cells only. Each slot is
    pinned to its own `(row, col)` forever, so its transform is written **once**
    at creation: locked blocks never move, they only appear and disappear.
  - `fallingCubes` — 4 `MovingCube`s carrying persistent identity and an
    animated position. **This split is load-bearing, don't collapse it back.**
    The original single-grid design expressed a move purely as different cells
    switching on/off, which is unanimatable by construction and is exactly why
    fast movement read as a teleport. Cells are sorted by `(row, col)` so a cube
    keeps meaning the same relative cell between frames — a pure translation
    preserves that order, which is what makes the glide correct; a rotation
    reshuffles it, which is fine because rotation should read as instantaneous.
  - `render(snapshot)` sets *targets*; `animate(deltaSeconds)` eases toward them
    and must be called every frame. Easing is exponential (frame-rate
    independent, `MOVE_EASE_TAU_SECONDS`), and a jump beyond `SNAP_DISTANCE_M`
    snaps instead of gliding so a freshly-spawned piece doesn't slide up from
    wherever the last one landed.
  - `isAttached` exists so the caller's frame loop can tell whether the pools
    are built yet; it can win the race against `SpatialView`'s `initial` block.
- `app/src/main/java/tech/illusion/spacecube/content/CandyPieceColors.kt` —
  the single source of truth for the 7 piece colors, defined once as RGB hex
  and exposed three ways: `candyColorFor` (`Color4`, solid), `candyJellyColorFor`
  (`Color4`, milkier frosted variant used by the actual cubes), and
  `candyPieceComposeColor` (Compose `Color`, for the 2D next-piece preview).
  Keep it that way — the 3D cubes and the 2D preview drifting apart is exactly
  what this file exists to prevent.
- `app/src/main/java/tech/illusion/spacecube/content/NextPiecePreview.kt` —
  draws `PieceType.spawnCells` as a grid of rounded candy squares, trimmed to
  the piece's occupied bounding box (the I piece's spawn box is 4×4 with only
  one row filled) inside a frame fixed at the largest box any piece needs
  (4×2) so the NEXT card doesn't resize as pieces change.
- `app/src/main/java/tech/illusion/spacecube/content/GameSoundEffects.kt` —
  `ToneGenerator`-based SFX. No background music: needs a real audio asset
  file, which this project doesn't have.
- `app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterial.kt` — the
  5-value base-plate material enum (styled like `Difficulty`).
- `app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterialStore.kt` —
  `SharedPreferencesBasePlateMaterialStore`, persists the selected material
  (default `GLASS`), same shape as `SharedPreferencesHighScoreStore`.
- `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt`
  — resolves a `BasePlateMaterial` to a live `Material`, caching the shared
  `AssetBundle` for the 4 PBR materials (`GROUND_OPACITY` now lives here, not
  in `BoardCubeRenderer.kt`).
- `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialPicker.kt`
  — the SpatialUI swatch-row picker on the start screen.

## Spatial SDK capabilities in use

- `SpatialView` + `AttachmentPanel` for hosting Compose content inside the
  Stage. **Critical**: `attachments.entity(id)` only looks up the
  attachment entity — it does **not** add it to the render tree. You must
  also call `content.addEntity(attachmentEntity)` (or
  `someOtherEntity.addChild(attachmentEntity)`) yourself, or the panel is
  silently never rendered and never receives input. See "Debugging notes".
- ECS board rendering: `MeshResource.createBox`, `UnlitMaterial.create(BlendingMode)`
  + `setBaseColor`/`setOpacity`, `ModelEntity(mesh, material)`,
  `entity.enabled`, `content.addEntity` (all verified against the SDK 6.0
  `agent-vault` API reference, not guessed).
- Raw hand-tracking (`com.pico.spatial.tracking.hand.*`) for all gameplay
  input — see "Gesture input model" below for the full design and why the
  Spatial-specific gesture detectors (`detectSpatialDragGesture`/
  `detectSpatialRotateGesture`) were abandoned after producing zero
  response on-device despite being real, correctly-used APIs.
- Each `AttachmentPanel` is an independently 3D-positioned surface; along a
  given pointer/gaze ray, whichever panel's surface is physically in front
  wins hit-testing — an invisible panel positioned in front of another can
  silently swallow taps meant for the one behind it. No longer relevant to
  gameplay input (nothing in "Gesture input model" below uses an
  `AttachmentPanel`), but still applies to the SpatialUI buttons/panels.

## Gesture input model (2026-08-06, superseded once already — read the history)

**Attempt 1 (abandoned): `detectSpatialDragGesture`/`detectSpatialRotateGesture`.**
These are real APIs in `com.pico.spatial.ui:foundation:6.0.0` (confirmed by
decompiling the actual dependency jar — the `spatial-ui-ability` skill doc's
example has the right *usage shape* but wrong import paths: `Offset3D` is
actually `com.pico.spatial.ui.geometry.Offset3D`, and `Rotation3D`/
`RotationAxis3D`/`NormalizedPoint3D` are `com.pico.spatial.ui.foundation.geometry.*`,
not `com.pico.spatial.math.*` as the doc claims). Wired up correctly —
including adding the documented prerequisite `InteractableComponent` +
`CollisionComponent` on a dedicated hit-test volume entity, and confirming
that volume rendered in the right place on-device via a temporary visible
material — this still produced **zero response to any pinch**, on the
original test device, across every fix attempt. Root cause never isolated;
the correct-looking code that "should" work per every doc and decompiled
signature simply didn't fire. Fully removed in favor of Attempt 2, not kept
as a fallback (avoids two overlapping, one-of-them-silently-dead input
systems).

**Attempt 2 (current, confirmed working): raw `HandTrackingProvider`.**
`HandGestureController` (a background, no-visual-output composable in
`GamePage.kt`) sidesteps the Spatial gesture-detector layer entirely:

- `HandTrackingProvider()` (remember-scoped, `DisposableEffect` start/stop)
  reports live hand joint positions via `.latestData` (polled every 20ms in
  a `LaunchedEffect`, not via its `dataFlow`/`collectAsState` — polling
  suited a stateful hold/repeat/debounce machine better than reacting to
  each flow emission). **Carries `@RequiredFullSpace`** — confirmed this
  is why it works now that the app is a Stage; would silently stay
  `PENDING` under a `WindowContainer`.
- **A real on-device gotcha, not a code bug**: the *first* physical
  headset tested against this showed a "please update system to 6.0.0"
  prompt the moment hand tracking was used, despite the app already
  building against `spatialBom 6.0.0` for everything else (Stage included).
  Hand-tracking access appears to be gated by the device's own **system
  firmware version**, separately from the app's compile-time SDK version —
  other SDK 6.0.0 features had worked fine on that device without
  triggering this. Switching to a second physical device already running
  system version `6.0.0` (`getprop ro.build.display.id`) resolved it
  immediately: `HandTrackingProvider.start()` returned `SUCCESS`,
  `supportState=SUPPORTED`. If hand-tracking gestures ever "don't work" on
  a fresh device again, check `ro.build.display.id` / the system update
  prompt **before** re-debugging the app code.
- **Pinch detection**: `PinchTracker` computes
  `Vector3.distance(thumbTip, indexTip)` per hand (joints from
  `HandPose.joint(HandJoint.Index.THUMB_TIP/INDEX_TIP)`), with hysteresis
  (`PINCH_START_DISTANCE_M = 0.010f` to start, `PINCH_RELEASE_DISTANCE_M =
  0.018f` to release — tightened **twice** now, both from direct user
  feedback: `0.025`/`0.04` → `0.015`/`0.025` (a screenshot showed a pinch
  registering with a clearly visible gap between thumb and index tip) →
  `0.010`/`0.018` (still felt too loose)) plus a `PINCH_CONFIRM_TICKS = 2`
  debounce — a state flip must repeat for 2 consecutive 20ms polls before
  it's accepted, filtering a single noisy tracking frame from registering
  as a spurious pinch/release. Every time `isPinching` actually flips,
  `PinchTracker` logs the measured distance under `SpaceCubeHandGesture`
  (tagged `left`/`right`) — check that log for real numbers if tightening
  further, rather than guessing again.
- **One-hand pinch + move**: once pinching, the thumb/index midpoint's
  displacement from a reference point is checked against
  `MOVE_LOCK_DISTANCE_M = 0.05f` (raised from an initial `0.04f`, same
  accidental-trigger fix) on each axis; whichever crosses first locks that
  direction and fires one `moveLeft()`/`moveRight()`/`moveDown()`
  immediately. A repeat timer then keeps firing the same move
  (`engine.currentFallIntervalMs() * 0.7`, floor 60ms) for as long as the
  pinch stays held, without requiring further hand travel. **Direction can
  change mid-pinch** (2026-08-06, user reported pinch-right-then-left did
  nothing): the delta keeps being checked every tick even after a lock, but
  only *acts* when the newly-detected direction differs from the current
  lock (same-direction continued drift is left to the repeat timer, so it
  doesn't double-fire) — and changing the lock resets the reference point
  to the current pinch position, so a reversal costs the same
  `MOVE_LOCK_DISTANCE_M` travel the original lock did, not an inflated
  distance measured all the way back through wherever the pinch started.
  **The delta must be computed from the *converted*
  midpoint** (`handTrackingRoot.convertPositionFrom(rawMidpoint, null)`),
  not the raw one — using the raw midpoint is exactly what made "pinch down"
  silently do nothing (2026-08-06 bug): raw hand-tracking joint space
  doesn't reliably share the Stage's up/down axis sense the way the
  converted space (confirmed correct via the pinch indicators' on-screen
  position) does, so left/right happened to still work by coincidence while
  down didn't. See debugging note #9.
- **Quick double-pinch to rotate** (2026-08-06, replaces an earlier
  two-hand-twist design entirely per user request): `PinchTracker` exposes
  `justStartedPinching` (true only on the exact tick a debounce-confirmed
  false→true flip happens). `HandGestureController` timestamps each hand's
  pinch-start independently; a second pinch-start on the *same* hand within
  `DOUBLE_PINCH_WINDOW_MS = 600L` (raised from an initial `400L` — user
  reported the gesture felt unresponsive; the window spans three
  debounce-gated transitions (pinch1-start → release → pinch2-start), each
  needing `PINCH_CONFIRM_TICKS` × 20ms to be recognized, so a chunk of the
  original budget was pure software latency before any finger movement) —
  counts as a double-pinch and fires one `rotateClockwise()`. **Either hand,
  always clockwise** (2026-08-07, per user request "双击旋转的方向都按照顺时针方向
  进行"); an earlier version mapped left hand → counter-clockwise and right hand
  → clockwise, but that split was only ever an assumed default the user never
  asked for. The two hands still keep *separate* double-pinch timestamps on
  purpose — a left pinch followed by a right pinch is two distinct gestures,
  not a double-pinch. `GameEngine.rotateCounterClockwise()` is therefore no
  longer on any gameplay path; it stays as the unit-tested engine-level
  inverse of `rotateClockwise()`, not dead code to delete. The
  old twist-angle approach
  (tracking the heading angle between two simultaneous pinch points via
  `atan2` + `EulerAngles.radianToDegree`) was removed along with
  `ROTATE_STEP_DEGREES` and the `EulerAngles`/`atan2` imports — no native
  "signed planar angle" API exists on `Vector3` anyway (its own `angle()` is
  an unsigned 0–180° enclosed angle), so this is one less place needing that
  workaround.
- **Scene free-drag whenever the engine isn't actively PLAYING** (2026-08-06,
  per user request "游戏没有进行时，手指捏合上下左右移动控制整个游戏场景的位移", then
  extended same-day per "暂停时也是不在游戏，也应该可以进行游戏场景移动" to cover pause too):
  `HandGestureController` takes `isPlaying: Boolean` (= `started &&
  snapshot.state == GameState.PLAYING`), not just `started` — so the branch
  applies on the pre-start screen, while paused, *and* on the game-over
  overlay (generalized from "paused" to "any non-PLAYING state" — the same
  reasoning applies to game-over even though the user only named pause;
  flag it if game-over free-drag is unwanted). This branch drives a direct,
  continuous 1:1 X/Y drag of `anchor`'s position by whichever hand is
  pinching — deliberately **not** the discrete direction-lock-and-repeat
  gameplay uses, since this is meant to feel like grabbing and repositioning
  the board, not nudging a falling piece. `anchor` had to be lifted out of
  `SpatialView`'s `initial` block into a `remember`-scoped `Entity` in
  `GamePage()` (previously a throwaway local var only `initial` ever saw) so
  `HandGestureController` can call `.setPosition(...)` on it directly.
  `currentAnchorPosition` is tracked locally inside the coroutine (seeded
  from the same `Vector3(0f, ANCHOR_HEIGHT_M, -1.0f)` `initial` sets it to)
  rather than read back from `TransformComponent` — no getter for that was
  verified anywhere else in this codebase, only `setPosition`. Depth (Z) is
  intentionally left untouched, matching "上下左右" (no mention of
  closer/farther). Releasing the pinch freezes the position; the next pinch
  starts a fresh drag from wherever it was left, not from the first drag's
  start point.
- **Visual pinch indicators**: `PinchIndicator` — a small sphere
  (`MeshResource.createSphere`, `PINCH_INDICATOR_RADIUS_M = 0.008f`), gray
  while tracked-but-not-pinching, mint while pinching. Added 2026-08-06 per
  user request, both as direct visual feedback and as the fastest way to
  judge whether the distance thresholds above need further tuning. **Four
  instances** (left/right × thumb/index), not one per hand at their
  midpoint — an earlier version showed one ball per hand at the thumb/index
  midpoint, which sits between the two fingers rather than "at" either one
  (obviously wrong when fingers are spread apart) — so `PinchTracker.update`
  returns the raw `FingerTips(thumbTip, indexTip)`, `HandGestureController`
  derives the midpoint locally only for the movement/rotation math, and
  each fingertip gets its own indicator that visibly converges with its
  pair on an actual pinch. Parented under `handTrackingRoot` (a bare
  identity-transform entity added directly to `content`, not `anchor` —
  unrelated to the board's `(0, ANCHOR_HEIGHT_M, -1.0)` offset), and their position must
  go through `handTrackingRoot.convertPositionFrom(rawJointPosition, null)`
  before `setPosition(...)` — a raw `HandJoint.position` is **not** already
  valid as any entity's local position; see debugging note #7. Indicators
  update regardless of `started` (visible on the start screen too); the
  movement/rotation state machine itself is gated on `started`.
- `GameEngine.rotate()` was split into `rotateClockwise()` /
  `rotateCounterClockwise()` (the old single-direction API couldn't express
  two independent inputs). `PieceType.rotateCellsCounterClockwise` /
  `FallingPiece.rotatedCounterClockwise` are the exact mathematical inverse
  of the existing clockwise transform. `moveDown()` reuses the same
  `tryMove()` plumbing as `moveLeft`/`moveRight` (a single-cell hop, not a
  hard drop). `fallBoostActive` was removed — the gesture-layer hold-repeat
  now does that job.
- Tunable constants (first-pass estimates, iterated repeatedly already
  against real on-device feedback, expect to keep tuning): all in
  `GamePage.kt` — `PINCH_START_DISTANCE_M`/`PINCH_RELEASE_DISTANCE_M`/
  `PINCH_CONFIRM_TICKS`, `MOVE_LOCK_DISTANCE_M`,
  `MOVE_REPEAT_INTERVAL_FACTOR`/`MOVE_REPEAT_MIN_INTERVAL_MS`,
  `DOUBLE_PINCH_WINDOW_MS`, `HAND_POLL_INTERVAL_MS`,
  `PINCH_INDICATOR_RADIUS_M`, `BOARD_WIDTH`/`BOARD_HEIGHT`,
  `ANCHOR_HEIGHT_M`.
- Diagnostic logging under tag `SpaceCubeHandGesture`: `start()` result +
  `supportState`/`state` (logged once immediately, again 2s later), and
  whether the first `latestData` had non-null left/right poses. Check this
  tag first if hand tracking ever appears dead again — it will show
  `WITHOUT_PERMISSION`/`NOT_IN_FULL_SPACE`/`DEVICE_NOT_SUPPORTED` directly
  rather than requiring another blind decompile-and-guess round.

## Two control schemes: V1 vs V2 (2026-08-07, built to be compared)

`ControlScheme` (in `game/`) selects what drives the falling piece. **V2 is the
fixed default and there is no UI picker any more** (2026-08-12, "把V1操作隐藏，默认
使用V2操作"); V1 is retained as a code-level fallback, reachable only by editing the
`mutableStateOf` initialiser in `GamePage`. The start-screen picker that used to
switch them is gone.
**Only piece control differs** — scene manipulation (position / yaw / scale while
not playing) always uses V1 hand tracking, so `HandTrackingProvider` and the pinch
indicators must keep running even though V2 owns the piece.

Because nothing writes `controlScheme` any more, `LaunchedEffect(controlScheme)`
fires exactly once and can never repair anything later. That is why the
`SpatialView(initial = …)` block must *derive* `v2ControlPlane.enabled` from
`controlScheme` rather than hardcoding it — a hardcoded `false` there raced the
effect and left the glass plane permanently invisible and un-hit-testable
(user report: "V2系统操作所需的玻璃墙也不见了"). Do not reintroduce a literal there.

Exactly one may drive the engine: `HandGestureController` returns before any
engine call when `pieceControlEnabled` is false, and V2's detectors are keyed on
`active`. Both driving it would double every move.

- **V1** — `HandGestureController`, polling `HandTrackingProvider` at 20ms and
  deriving pinch / drag / double-pinch itself. See "Gesture input model".
- **V2** — `V2ControlSurface.kt`: a 0.2-opacity glass plane behind the
  playfield, driven by the SDK's own spatial gesture detectors. Horizontal drag
  moves, downward drag soft-drops, double-tap rotates.

### V2 facts that are easy to get wrong

- **The detectors MUST go on the `SpatialView`'s modifier, never on an
  `AttachmentPanel`.** A panel's Compose content is hosted in its own separate
  view host and never receives the MotionEvent from a ray hit on a 3D entity in
  the `SpatialView`; and `TargetEntity.hit(entity)` could never match a hit
  delivered against the panel itself. This is almost certainly what made the
  first (abandoned) spatial-gesture attempt produce total silence — the
  stale-firmware theory was a red herring. Add only `pointerInput` there, never
  a sizing modifier (debugging note #1).
- **One detector per `pointerInput` block.** Every `detectSpatial*` body is a
  single `awaitEachGesture` that never returns, so a second detector in the same
  block is unreachable dead code.
- **`SpatialDragValue.dragAmount` is a PER-EVENT delta in PANEL PIXELS** (its own
  KDoc says so), so it must be accumulated, and converted via
  `LocalPhysicalLengthConverter` (`com.pico.spatial.ui.platform`) —
  `converter.lengthToDp(1f, LengthUnit.Meters).toPx()`. **Measured 2500 px/m on
  the emulator**, so ~140px per 0.056m column. A value near 1.0 would mean
  off-platform identity stubs. X needs no sign inversion; Y does (view-space +Y
  is down), which is why a downward drag arrives as positive y and maps straight
  to `moveDown()`. Do NOT reuse this conversion for
  `detectSpatialTransformGesture` — that detector's `dragAmount` is documented as
  normalized space instead.
- **Use `detectTapGestures(onDoubleTap = ...)`, not `detectSpatialTapGesture`.**
  The latter has no double-tap of any kind, and only fires when the platform
  reports `GestureState.TAP` — a firmware-dependent path. The former is PICO's
  entity-scoped fork of the Compose detector, in the same package, whose success
  check is plain Compose down/up logic. `targetedToEntity` has **no default**
  there, and named args are mandatory because
  `androidx.compose.foundation.gestures` has a same-named function.
- `AndroidManifest.xml` sets `pico.spatial.ui.Logger=1`. Without it the SDK's own
  `Logger` level is None and it discards even `Logger.e`, which is why the first
  failure left nothing in logcat. The gesture path reports platform-gate failures
  under tag **`SUISpatialGesture`** — watch that alongside `SpaceCubeV2Gesture`.
- **Both detectors are targeted** via `TargetEntity.hit(controlPlane)`.
  `UNTARGETED_DRAG_PROBE` is a diagnostic that is **`false`** and must stay that
  way: turning it on makes the drag detector accept a pointer-down anywhere in the
  Stage, which starves the AttachmentPanel buttons — that was the actual cause of
  "切换到V2手势后，点击主面板UI上的按钮没有反应" (commit `e8c4c4c`), *not* the glass
  plane's collider, which sits 0.45 m behind every panel (plane z = −0.28 vs panels
  z = +0.17). **This applies only to the app's own `pointerInput`/`detectSpatial*`
  gesture-dispatch path on the `SpatialView`** (the mechanism `e8c4c4c` broke) — it
  does *not* mean the collider is safe to leave enabled outside gameplay; see the
  next bullet for a different, later-discovered bug where it *does* interfere, via
  the platform's own gaze-pinch targeting rather than this app's gesture detectors.
- **2026-08-20 fix: the glass plane must stay `enabled = false` outside actual
  PLAYING, not just while `controlScheme != V2`.** User report: gaze-click (凝视点击)
  worked on the start screen's 慢/中/快/开始游戏 buttons *before* the board finished
  loading, but stopped working right after (direct touch/手戳 still worked) — i.e.
  exactly when `initial` reaches `anchor.addChild(v2ControlPlane)`. Root cause: the
  plane carries `InteractableComponent` + `CollisionComponent`, defaults to
  `enabled = true`, is sized `spanX*2 × spanY*2` (**twice** the board's footprint —
  easily covers the centred start/pause/game-over panels' XY position), and
  `v2ControlPlane.enabled` was only ever tied to `controlScheme` — which never
  changes (V2 is the fixed default) — so once added, it stayed enabled for the
  app's entire lifetime, including the start screen, paused, and game-over states.
  Per PICO's own video-FAQ docs (`spatial-sdk_video_video-faqs_how-can-i-resolve-
  the-issue-of-playback-control-buttons-being-obscured-by-the-video-panel.md`), 3D
  ECS entities and 2D `AttachmentPanel` content are **not in the same depth-sort
  domain** — the plane sitting physically behind a panel in Z does not guarantee
  it's excluded from hit targeting. And per PICO's interaction-design docs
  (`motion-based-interaction.md`), gaze-pinch ("凝视-捏合") is a distinct **far-field
  ray-cast against interactable colliders**, separate from near-field direct
  touch/poke — which is exactly why only gaze broke while direct touch kept
  working. Fixed by gating `v2ControlPlane.enabled` on `isPlaying` (`started &&
  snapshot.state == GameState.PLAYING`) in addition to `controlScheme`, in both
  writers (the `LaunchedEffect`, now keyed on `(controlScheme, isPlaying)` instead
  of just `controlScheme`, and the `initial` block). **Unverified on-device** —
  needs a real gaze-click test on the start screen after this change; see
  "Verified so far" below.
- **Fixed 2026-08-24: `dragAmount.x` yaw skew.** Confirmed via `pico-dev-knowledge`
  (`spatial-sdk_interaction_implement-basic-interactions-for-3d-objects.md`:
  "dragAmount follows Compose View's coordinate system") that this really is
  view-space and never rotates with the plane/anchor, matching the on-device
  report. `v2GestureModifier` now takes `sceneYawDegrees` (wired from
  `anchorPlacement.yawDegrees` in `GamePage`) and scales `stepX` by
  `cos(yawRadians)` before accumulating — the same x-axis term
  `toBoardSpaceDelta` would produce for a world delta with zero Z, which is the
  best available approximation since a 2D screen delta carries no world-Z
  information to feed the fuller rotation. yaw = 0 (the common case) leaves the
  factor at 1, so this is a no-op until the scene is actually turned.
  **Still unverified on real hardware** — the emulator's hand tracking is
  `DEVICE_NOT_SUPPORTED`, so a real non-zero-yaw drag has never been exercised;
  see "Verified so far".

## SpatialUI-only UI rule (mandatory for this project)

All 2D UI must use SpatialUI (`com.pico.spatial.ui.design.*`), wrapped in
`PicoTheme { ... }`. **Material / Material3 is forbidden** — do not
reintroduce `androidx.compose.material*`. Colors/typography must route
through `PicoTheme.colorScheme.<role>` / `PicoTheme.typography.<role>`.
Before any non-trivial UI change, consult the `spatial-ui-design-style`
skill, then run:

```bash
bash <plugin-cache>/pico-spatial-agentic-tools/*/skills/spatial-ui-design-style/scripts/verify-design-style.sh app/src/main/java
```

## Material / glass look (2026-08-07 — read before attempting "real" glass again)

The user asked whether the Unity URP glass shader at
`github.com/omid3098/Unity-URP-GlassShader` could be reproduced here. It was
investigated properly (decompiled `core-6.0.0-sources.jar`, read the actual
`.shadergraph` JSON). Conclusions, so this isn't re-litigated:

- **A literal port is impossible.** That shader's defining effect samples
  `_CameraOpaqueTexture` (Shader Graph `SceneColor`) and warps the lookup with
  noise. **Nothing in PICO Spatial SDK 6.0.0 can read the pixels behind an
  object** — no grab pass, no backdrop/opaque-texture sampling. So no real
  refraction (background distortion) at any price.
- **Confirmed ABSENT from the material API** (searched, not assumed):
  refraction / IOR / transmission / thickness / volume / clearcoat / sheen /
  anisotropy / subsurface / specular color / alphaCutoff / per-material
  UV transform / per-material environment map / render-queue or sort-order.
- **`PhysicallyBasedMaterial` DOES exist** and is much richer than the
  `UnlitMaterial` this project uses: `setRoughness`, `setMetallic`,
  `setNormalTexture`/`setNormalScale`, `setEmissiveColor`,
  `setAmbientOcclusion`, `setCullingMode(MaterialCullingMode.NONE)` for
  double-sided, `setDepthWrite`/`setDepthTest`, and 6 texture slots. Note
  `BlendingMode.TRANSPARENT` preserves specular highlights while `FADE`
  fades them — TRANSPARENT is the one you want for glass.
- **Why we did NOT use it**: glass reads as glass because it reflects an
  environment, which needs an HDR cubemap (`.ktx`) plus
  `StageEnvironmentLightingComponent`. **This project still has no *custom*
  lighting or IBL rig** (`grep -rn "Light\|Environment\|IBL"` over the source
  is empty) — every material this codebase authors is unlit. **Correction
  (2026-08-26, see "Base plate material picker" below): the "would very likely
  render dark" conclusion that used to follow from that was wrong in
  practice.** The base-plate material picker ships 4 PBR Shader Graph
  materials with no lighting rig added and they verifiably render as
  distinct, non-dark surfaces on both the emulator and (pending real-headset
  confirmation — see "Still unverified") presumably the real one too, because
  `StageStyle.Mixed` supplies automatic system IBL — a platform behavior this
  project doesn't configure but does benefit from. The underlying point (no
  *custom* lighting/IBL was ever added here) is still true; only the
  render-dark prediction was wrong. If real glass (reflective, not just PBR)
  is ever wanted, an HDR cubemap asset is still the prerequisite, not the
  material code.
- **`ShaderGraphMaterial` exists but is load-only** —
  `loadFromAssetBundle(bundle, path)` + typed `setParameter(...)`. There is no
  runtime shader-authoring API; a custom shader would have to be authored in
  the Spatial Editor (`~/Library/PICO/sdk/.../editor/`) and shipped in an
  AssetBundle. That path could recover Fresnel rim and cubemap reflections,
  but still not the SceneColor grab.
- **Platform frosted glass is 2D-only**: `Modifier.backgroundMaterial(...)`
  (`com.pico.spatial.ui.foundation.material.backgroundMaterial`) applies to
  Compose panels, is documented as requiring a `WindowContainer` (this app is
  a `DefaultStage`), and cannot be applied to 3D mesh entities.

**What was actually shipped** (user chose the no-lighting path, asking for
"方块做成磨砂透明，果冻状"): plain `UnlitMaterial` with `BlendingMode.TRANSPARENT` —
cubes at `CUBE_JELLY_OPACITY = 0.80f` using `candyJellyColorFor()` (each candy
color mixed 16% toward white, since unlit has no roughness/scattering so
"frosted" can only be *suggested* by a milkier tint), the base plate (glass
default) at `GROUND_OPACITY = 0.80f` with `depthWrite` off — this constant
moved from `BoardCubeRenderer.kt` to `BasePlateMaterialLoader.kt` in the
base-plate material picker's Task 5, see "Base plate material picker" below —
ghost at `0.30f` (but the ghost
is currently disabled — see `SHOW_GHOST_PIECE`). Cube opacity
is deliberately high: the 7 candy colors are the player's piece-identification
channel and legibility drops fast once stacked cubes show through each other.
User confirmed the cubes read well and stay legible when stacked at 0.80, and
that 0.30 made the base plate nearly invisible — the plate was then raised
0.30 → 0.48 → 0.80 across two rounds of feedback, so it is now as solid as the
cubes and reads as a real platform rather than a glassy hint of one.

## Base plate material picker (2026-08-26)

A 5-option picker (玻璃/`Wood_02`/`Tiles_04`/`Wood_12`/`Travertine_09`) added to
the start screen, directly below the 慢/中/快 difficulty row, inside the same
`CandyCard`. Purely visual — swaps only the base-plate material, touches
nothing about gameplay. Design spec:
`docs/superpowers/specs/2026-08-26-base-plate-material-picker-design.md`;
implementation plan: `docs/superpowers/plans/2026-08-26-base-plate-material-picker.md`.

- `game/BasePlateMaterial.kt` — the 5-value enum (styled like `Difficulty`).
- `game/BasePlateMaterialStore.kt` — `SharedPreferencesBasePlateMaterialStore`,
  same shape as `SharedPreferencesHighScoreStore`. Default (no saved value) is
  `GLASS`.
- `content/BasePlateMaterialLoader.kt` — resolves a `BasePlateMaterial` to a
  live `Material`. `GLASS` stays the original `UnlitMaterial` + `BlendingMode.TRANSPARENT`
  look, untouched. The other 4 call `ShaderGraphMaterial.loadFromAssetBundle`
  against `app/src/main/assets/base_materials.bundle` (an AssetBundle built in
  Spatial Editor — SpaceCube's first use of it; the project was pure-code ECS
  before this). Any load failure (missing bundle, wrong path) degrades to
  glass with a logged warning instead of crashing — see the `runCatching`
  wrapping in `load()`.
- `content/BasePlateMaterialPicker.kt` — the swatch row UI (SpatialUI, per the
  "SpatialUI-only UI rule" section above).
- `BoardCubeRenderer.setBasePlateMaterial` — swaps the live base-plate entity's
  material; `@MainThread`-enforced `Entity.destroy()` inside it had never run
  on a device before this feature (flagged as a risk during Task 5 review) —
  now exercised on every material swap below, no crash.
- Real `bundlePathFor` paths (confirmed by reading `AssetInfo.json` out of the
  built bundle, not guessed — see
  `docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md`
  for the one-liner and full provenance):
  `BaseMaterials/Root/Wood_02/material/M_Wood_02`,
  `.../Tiles_04/material/M_Tiles_04`, `.../Wood_12/material/M_Wood_12`,
  `.../Travertine_09/material/M_Travertine_09`.

**Verified (build + emulator-5554, 2026-08-26):** `assembleDebug` succeeds.
Installed and launched 7 times total (5 materials + a persistence baseline +
a persistence-after-restart run) with `adb logcat -b crash -d` empty and zero
`SpaceCubeBasePlateMaterial` "falling back to glass" warnings throughout. Each
of the 5 materials renders a visibly distinct base plate and its swatch shows
the selection ring: `GLASS` translucent warm off-white (unchanged baseline,
`Color4(0.92f, 0.89f, 0.85f, 1f)` — not light-blue; that shade was deliberately
moved away from on 2026-08-07 because it visually merged with the I-piece's
sky-blue `#52D1E8` (see `2026-08-05-spacecube-design.md`'s candy-color table),
predating this task and not separately re-verified here — this section had
previously mis-described it as light-blue again, now corrected),
`WOOD_02` light wood parquet, `TILES_04` white terrazzo speckle, `WOOD_12`
dark walnut grain, `TRAVERTINE_09` blue/cream marble banding. **Persistence
across a real app restart is confirmed end-to-end**: seeded `TRAVERTINE_09`
into `SharedPreferences`, launched (baseline), force-stopped, relaunched
*without* re-seeding — the restart came back showing `Travertine_09` selected
with its ring and the marble base plate, proving `SharedPreferencesBasePlateMaterialStore`
round-trips correctly and `GamePage`'s `initial` block re-resolves the stored
value on a cold start. Screenshots taken during this verification pass were
saved to `/tmp/task7-*.png` (ephemeral, not committed — re-run the technique
below to reproduce them if needed).

**Explicitly unverified — do not claim these work:**

- **How the 4 PBR materials actually look under `StageStyle.Mixed`'s automatic
  system IBL on a real headset.** The emulator screenshots confirm the
  materials render as *something* distinct from glass and from each other —
  not that the lighting/appearance is correct or attractive. See
  `docs/superpowers/specs/2026-08-26-base-plate-material-picker-design.md` §2
  for why this feature doesn't add its own lighting rig (the system is
  documented to supply automatic environment lighting in Mixed mode, but that
  is a documentation finding, not an on-device confirmation).
- **Switching between two non-glass materials while the app is already
  running, in a single session.** Every device run so far (this task and
  Task 6's) only exercised one swap, from a freshly (re)launched app with a
  seeded starting selection — never two swaps back-to-back in one running
  process. The code path is identical (`LaunchedEffect(selectedBasePlateMaterial)`
  → the same `setBasePlateMaterial`), but the release semantics of a *second*
  swap (destroying an entity holding a bundle-loaded `ShaderGraphMaterial`,
  then loading another) were never exercised. Needs the user's own hands-on
  test on the physical headset.

**Testing trick worth reusing for any other `SharedPreferences`-backed
selection in this project**: `adb shell input tap` does not drive this app's
spatial UI **at all** — confirmed directly (not just suspected, see debugging
note #5 above), including a tap at the verified on-screen centre of a swatch
producing no selection change and no log line. Don't try to automate
selection UI this way. Instead, seed the persisted value and relaunch:

```bash
adb -s <serial> shell am force-stop tech.illusion.spacecube   # first, or the
                                                                # app overwrites
                                                                # the seeded file
                                                                # on its way out
adb -s <serial> shell "run-as tech.illusion.spacecube sh -c \
  'cat > /data/data/tech.illusion.spacecube/shared_prefs/<prefs_file>.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="<pref_key>"><value></string>
</map>
XML
adb -s <serial> logcat -c
pico-cli app launch tech.illusion.spacecube --activity .platform.LaunchActivity --device <serial>
```

Poll logcat for a real settle anchor (here, `SpaceCubeHandGesture: "initial:
board build took"`) before screenshotting rather than a fixed `sleep` — pair
every screenshot with a log anchor and never trust a screenshot alone, the
emulator has a known stale-screenshot bug (a ~25s-late correct frame after
the anchor, observed both in Task 6 and in prior sessions). To test
persistence specifically, run this technique once to seed a value, then
force-stop + relaunch a *second* time with no new `cat >` in between. When
done testing, force-stop and delete the seeded prefs file so the next launch
(by anyone, including the user) starts from the real default.

**Environment findings (outlived this task, recorded so the next Spatial
Editor task doesn't rediscover them from scratch):**

- The installed `pico-spatial-agentic-tools` plugin is **v0.3.0**, whose
  skill set has no `spatial-editor` entry and whose `.mcp.json` declares only
  `pico-spatial-knowledge`. The `spatial-editor` skill / `pico-spatial-editor`
  MCP server described in the user-level `PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md`
  belong to a newer plugin version not installed here (`pico-cli plugin
  update` + a Claude Code restart would fix this).
- **Separately**, the published Spatial Editor build itself
  (`spatial_editor_20260805_v6.0.0_mac.zip`, `beta_cn` channel) currently
  ships with **no MCP backend at all** (`Contents/Resources/plugins` is
  missing entirely from the `.app`), so `pico-cli editor pack` doesn't work
  regardless of plugin version — this is a defect in the published editor
  artifact, not fixable by updating the plugin. The GUI editor itself still
  launches and works fine for a human; only headless/agent automation is
  blocked. The undocumented fallback that was used instead — the editor's own
  `ide_build.sh`-driven headless build flags (`--ide_noGraphics
  --ide_buildOutputDir=... --ide_buildResultName=... --ide_resultCodeFile=...`)
  — is recorded in
  `docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md`.
  **Prefer `pico-cli editor pack` again** the moment PICO republishes a
  working editor build; it also writes a `.scenes.json` sidecar the raw
  invocation doesn't.

**App-size and reproducibility notes:**

- The AssetBundle adds ~25 MB to the repo and the debug APK grows from ~33 MB
  to **58.4 MB** (`app-debug.apk`, this task's build). No texture-resolution
  control was found on the headless build path — every texture comes out at a
  uniform ~2.5 MB regardless of source PNG size, i.e. **~6 MB per PBR
  material is a fixed cost**, not a tunable one.
- The Spatial Editor project itself is **not committed** (it would add ~50 MB
  of `.usdz` payload the 25 MB bundle already contains). It's fully
  reproducible from `~/Downloads/Base.usdz` via the copy-pasteable script in
  the editor-notes file — but if that source file is ever lost, the bundle
  cannot be rebuilt without redoing material authoring from scratch in
  Spatial Editor. Worth archiving that file somewhere durable.

## Background / passthrough choice

`StageStyle.Mixed` (`pico.spatial.stage.style="1"`) keeps the real-room
passthrough visible behind all virtual content and needs no custom skybox
or IBL setup — matching the "spatial content floating in your own room"
positioning that motivated a Stage over `StageStyle.Full`.

## Debugging notes (real bugs found and fixed on-device — read before touching rendering/input again)

1. **`SpatialView`'s `modifier` affects 3D content, not just 2D layout.**
   An arbitrary `Modifier.size(560.dp)` (unrelated to the window's actual
   declared size) scaled/clipped the 3D content in ways that didn't match
   the board's own meter-based math. Fixed by using `Modifier.fillMaxSize()`,
   matching every official reference example.
2. **Attachment entities are invisible until explicitly added to the scene.**
   `attachments.entity(id)?.components[...]?.setPosition(...)` alone does
   nothing visible — you must also call `content.addEntity(thatEntity)`.
   This was the root cause of *all* UI panels (score, next-piece, buttons,
   overlays) being invisible and the drag pad never receiving any gesture
   (an unrendered panel has no hit-testable surface).
3. **A wider/farther-out plain ECS entity can be invisible while a
   narrower one (like the board) renders fine** — the ground plane was
   invisible until its footprint was shrunk to exactly match the board's
   width and repositioned flush against the board's bottom edge, instead of
   extending past it with a margin. Suspected cause: Volumetric windows may
   clip 3D content exceeding their bounds, similar to documented Planar
   window content clipping.
4. **An invisible `AttachmentPanel` positioned in front of another
   occludes its input**, even if the one behind is visually a normal
   button. The drag pad (large, invisible, z slightly in front of the
   board) was swallowing taps meant for a separately-positioned "旋转"
   button behind it. Resolved by moving rotation onto the drag pad's own
   surface (tap-to-rotate) instead of a separate button, per the user's
   actual original intent: tap/pinch directly on the falling piece rotates
   it — there was never supposed to be a standalone rotate button.
5. **`adb shell input tap` cannot reliably drive this volumetric/spatial
   UI** — confirmed both by the `spatial-emulator-usage` skill and by
   directly observing a blind tap land on an unrelated pre-installed app's
   dialog instead of SpaceCube. Don't try to automate interaction
   verification this way; rely on build/install/no-crash-in-logcat plus the
   user's own hands-on testing, and ask specific, narrow questions about
   what's actually visible/responsive rather than guessing blindly at fixes.
6. **Windowed chrome can silently steal gameplay gestures.** The Volumetric
   window's gaze-following caption bar (move/minimize controls) intercepted
   drag/tap on the falling piece even after `captionbar="1"` (auto-hide) was
   set — this is what ultimately motivated dropping windows entirely and
   migrating to a `Stage` (see "Why this structure"), rather than continuing
   to patch caption-bar visibility settings.
7. **A raw tracking `Vector3` is never valid as a `TransformComponent`
   position as-is — it must go through `Entity.convertPositionFrom`
   first.** Setting a `HandJoint.position` directly placed the pinch
   indicator ball nowhere near the actual hand. `Entity.convertPositionFrom`'s
   own KDoc and the SDK's `ShowControllerModel()` sample (`Plane.kt`) are
   explicit about this: a position "relative to" some source (a controller,
   a hand joint, `null` for the container) must be converted via
   `entityToPlace.getParent().convertPositionFrom(rawPosition, sourceEntity)`
   before it's meaningful as that entity's local position — even when the
   parent is a bare identity-transform entity added straight to `content`
   with nothing else going on. This generalizes beyond hand-tracking: any
   future feature consuming *TrackingProvider position data (controller,
   body, eye, HMD) needs the same conversion, not just hand-tracking.
8. **`PinchTracker.update` returns a fingertip position for any *tracked*
   hand, not just a *pinching* one — don't `?:`-fallback between two
   hands' points.** `val pinchPoint = leftPoint ?: rightPoint` silently
   used the left hand's position (merely visible/tracked, not pinching)
   over the right hand's actual pinch whenever both hands were in frame at
   once, making right-hand-only pinch-move look completely unresponsive
   (the exact bug the user reported: left hand worked, right hand didn't -
   because `leftPoint` is what the `?:` always prefers when non-null).
   Fixed by branching on `isPinching` explicitly (`if (leftPinching)
   leftPoint else rightPoint`) instead of null-coalescing between the two
   points. Any future two-hand logic here must pick by pinch *state*, never
   by which point happens to be non-null.
9. **Raw hand-tracking joint positions and their `convertPositionFrom`-ed
   counterparts are NOT interchangeable, even for direction/delta math
   that "shouldn't care about coordinate spaces."** The movement state
   machine computed `pinchPoint - origin` from the *raw* fingertip
   midpoint while the visual indicators placed themselves from the
   *converted* one (`handTrackingRoot.convertPositionFrom(raw, null)`) —
   left/right happened to work because raw and converted X apparently
   agree in sign/scale, but "pinch down" did nothing at all, because raw
   Y does not reliably share the Stage's up/down sense the way the
   converted space (confirmed correct via the indicators' on-screen
   position) does. Fixed by converting the midpoint the same way before
   using it in the direction-lock delta. Lesson: once a conversion is
   known-correct for one purpose (visual placement), reuse it for every
   purpose touching that data — don't assume "distance/delta math is
   coordinate-space-agnostic" lets you skip it.

## Most natural next evolution paths

1. **V2 关卡系统** — a leveled/stage mode (different well shapes, obstacles,
   or goal-based clears vs. the current endless-survival MVP). Deliberately
   not designed yet; needs its own brainstorming round before a spec/plan.
2. **Background music** — blocked on having an actual audio asset file;
   the SFX plumbing (`GameSoundEffects`) is already there to extend.
3. Consider richer next-piece preview (mini shape grid instead of a text
   label) and a true dashed-outline ghost piece if the current translucent-cube
   approximation isn't visually clear enough on-device.

Use the `spatial-app-dev-workflow` skill for further follow-up increments
(inspect this file, implement one increment at a time, build/install/launch
on-device, verify, self-repair).

## Build / install / run

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew installDebug
adb shell am start -n tech.illusion.spacecube/.platform.LaunchActivity
```

Note: this machine has JDK 25 as the default (`/usr/libexec/java_home`),
which is incompatible with the current Gradle/AGP — always build with the
JDK 17 override above.

`local.properties` (not checked into VCS) points `sdk.dir` at
`~/Library/Android/sdk` and `spatial.tools.dir` at `~/Library/PICO/sdk`.

If multiple devices/emulators are connected, pass `-s <serial>` to `adb` and
set `ANDROID_SERIAL=<serial>` for Gradle device tasks (`installDebug`,
`connectedAndroidTest`) to target a specific one; `adb devices -l` lists
what's currently attached.

## Verified so far

Static checks — re-run these before claiming anything works:

- SDK is on `spatialBom 6.0.0` (PICO OS 6). `assembleDebug` succeeds.
- 33 JUnit unit tests (`testDebugUnitTest`) + 1 instrumented test
  (`connectedAndroidTest`, `SharedPreferencesHighScoreStoreTest`) all pass.
- `spatial-ui-design-style` verifier: clean (0 errors, 0 warnings).
- Installs and launches on the working device with no crash in logcat, across
  every change in this document.

**User-confirmed working on the real headset** (this is the only evidence that
counts for anything interaction- or appearance-related — see debugging note #5
for why the agent cannot verify these itself):

- Single-hand pinch-drag to move the falling piece, including **down**
  (confirmed via "目前感觉还行" covering that whole batch).
- Double-pinch to rotate, at `DOUBLE_PINCH_WINDOW_MS = 600L` ("目前感觉还行" —
  responsive enough after being raised from 400ms). Note the *direction* was
  changed to always-clockwise afterwards (see below) and that change is
  unverified.
- Pinch thresholds at `0.010f`/`0.018f` — registers only when the fingers are
  genuinely close.
- All four `PinchIndicator` spheres sit accurately on the thumb/index
  fingertips and change color on pinch.
- All `AttachmentPanel` UI (score / next-piece / buttons / overlays) renders.
- Board at `10×18`, anchor at `0.8m` — no complaints about size or placement.
- Scene free-drag while **paused**.
- `NextPiecePreview` mini candy blocks — correct shape and color.
- Frosted-jelly cubes at `CUBE_JELLY_OPACITY = 0.80f` — reads as jelly *and*
  the 7 colors stay legible when stacked (the main risk of that change).

**Verified on the `swan_oversea` emulator (no visuals, no hands — see the
verification-limits list below for what that can and can't prove):**

- The two-pool renderer refactor and the per-frame `withFrameNanos` loop run
  without crashing or ANR-ing.
- `LocalPhysicalLengthConverter` IS available inside a Stage and returns a real
  scale: `pixelsPerMeter=2500.0`.
- Head-height calibration works end to end (`headY=1.797m -> base plate at
  1.297m`).
- ~~Control-scheme switching and the V2 plane's enable/disable follow the picker.~~
  **No longer true (2026-08-12)**: the picker is gone and V2 is fixed. The plane's
  `enabled` is now derived from `controlScheme` in *both* writers — the
  `LaunchedEffect` and the `SpatialView(initial = …)` block. They must agree; a
  hardcoded literal in `initial` beat the effect and left the plane permanently
  invisible.
- **2026-08-24**: installed + launched through the full start-screen lifecycle,
  including the board build (~48s on this emulator — `initial: board build took
  47864ms`), with `adb logcat -b crash` empty throughout. The `LaunchedEffect`
  logs `control scheme = V2_SYSTEM_GESTURE, isPlaying=false, plane enabled =
  false` once at launch and never re-logs (its keys, `controlScheme` and
  `isPlaying`, never change while sitting on the start screen), consistent with
  both writers evaluating the same `controlScheme == V2 && isPlaying` expression.
  `HandTrackingProvider` reports `supportState=DEVICE_NOT_SUPPORTED` on this
  emulator, so the actual gaze-pinch click itself still cannot be exercised
  here — this only confirms the code path and absence of a crash, not the fix's
  actual effect. See the "Still unverified" entry below for what's left.

**Still unverified — do not claim these work:**

- **Gazing the start-screen 慢/中/快/开始游戏 buttons after the board finishes
  loading** (2026-08-20 fix, "Two control schemes: V2 facts" above): the
  `v2ControlPlane.enabled` gate now also requires `isPlaying`, on the theory that
  the always-enabled, oversized glass-plane collider was stealing gaze-pinch
  targeting from the start-screen panel once added to the scene. `assembleDebug`
  and all unit tests pass, and (2026-08-24) a full install/launch through board
  build shows no crash and the expected `plane enabled = false` log at every
  point checked — see "Verified so far" above. But this is still fundamentally a
  gaze/interaction claim that only a real gaze-pinch click can confirm, and the
  emulator's hand tracking is `DEVICE_NOT_SUPPORTED` — needs the user to actually
  gaze-click those buttons on the real headset (both right after launch, before
  the board finishes loading, and after) to confirm the fix, and to confirm gaze
  still works on the pause/game-over overlays too (same gate).
- **V2 drag yaw correction** (2026-08-24 fix, "V2 facts that are easy to get
  wrong" above): `stepX` is now scaled by `cos(sceneYawDegrees)` before
  accumulating into columns. Build-verified only (`assembleDebug` + unit tests
  pass, no runtime path touches this without hand tracking). Needs a real
  headset test: turn the scene to a non-zero yaw on the start screen (two-hand
  twist), start a game, and confirm horizontal drag still moves the piece in
  the direction the hand actually moved rather than a world-locked direction.
- **Double-pinch now rotates clockwise from EITHER hand** (2026-08-07, per
  user request "双击旋转的方向都按照顺时针方向进行", replacing an assumed
  left=CCW/right=CW split the user never asked for). Not yet tested on-device
  — in particular, whether "clockwise" as the engine computes it matches what
  reads as clockwise to the player. `GameEngine.rotateCounterClockwise()` is
  now unused by gameplay but retained + unit-tested as the rotation inverse.
- **Base plate (glass default) at `GROUND_OPACITY = 0.80f`, now defined in
  `BasePlateMaterialLoader.kt` (moved out of `BoardCubeRenderer.kt` in the
  base-plate material picker's Task 5)** — raised 0.30 → 0.48 → 0.80 over
  two rounds; the 0.80 value hasn't been looked at yet.
- **Everything about V2** on real hardware: whether the spatial gesture
  detectors fire at all now that they're on the `SpatialView`, whether drag maps
  to columns at a usable sensitivity, and whether double-tap rotates. The
  emulator cannot test this (no hand tracking).
- **The falling piece's movement easing** (`MOVE_EASE_TAU_SECONDS = 0.055f`) —
  whether it reads as motion without feeling laggy.
- **Panel depth**: all five panels now share `z = 0.17` (the three tilted status
  panels on the base plate, plus the start-screen / paused / game-over card
  overlays via `MAIN_PANEL_Z_M`). The board stays at `BOARD_Z_M = 0`. An earlier
  pass moved the *board* forward instead, on a misreading of "游戏主面板" as the
  playfield rather than the centred card overlays - the user corrected it
  ("你现在弄反了，反而将方块那一排弄到前面去了"), so don't reintroduce that.
- **Ghost piece being off** (`SHOW_GHOST_PIECE = false`) — the toggle compiles
  and installs, but nobody has confirmed on-device that the projection is
  actually gone and that nothing else regressed with an empty ghost pool.
- **Head-height board placement.** The calibration chain itself IS verified
  from logcat (`headY=1.218m -> base plate at 0.718m, anchorY=1.211m`, no
  crash), so the measurement and the offset math work. What's unverified is
  whether 0.5m-below-head *feels* right in the headset, and whether dragging
  the scene after calibration behaves correctly now that both go through
  `AnchorPlacement` (the bug that sharing was introduced to prevent).
- **Reversing move direction mid-pinch.** The user *reported* that
  pinch-right-then-left did nothing; the gate was root-caused and removed,
  but they never re-tested it afterwards (the device disconnected mid-verify
  and the conversation moved on to new features).
- **Scene free-drag on the pre-start screen and on the game-over overlay.**
  Only the paused case was confirmed. All three share one code path gated on
  `isPlaying`, so they very likely behave the same, but that is inference.
- Anything on a device whose system firmware is older than `6.0.0` — hand
  tracking is gated by device firmware, so on such a device *every* gesture
  feature above is expected to be dead. Check `getprop ro.build.display.id`
  before debugging gesture code on an unfamiliar device.
- **The 4 PBR base-plate materials' real appearance under `StageStyle.Mixed`'s
  automatic system IBL on a real headset** (2026-08-26, see "Base plate
  material picker" above). Emulator screenshots confirm `Wood_02`/`Tiles_04`/
  `Wood_12`/`Travertine_09` render as something visually distinct from glass
  and from each other, not that the lighting/appearance is correct or
  attractive on a real headset.
- **Swapping between two non-glass base-plate materials while the app is
  already running, in a single session** (2026-08-26, see "Base plate
  material picker" above). Every device run so far only exercised one swap
  from a freshly (re)launched app with a seeded starting selection; the
  release semantics of a *second* bundle-material swap in one running session
  are unexercised.
- **Whether the score/next/pause status panels (and the translucent falling
  cubes) still read correctly against an opaque, depth-writing PBR base-plate
  material.** Every *deliberate* device run through this plan (Tasks 6, 7, and
  this fix round's own smoke test) only ever reached the start screen —
  `adb tap` cannot press 开始游戏 on this UI, see "Testing trick worth reusing"
  above. One incidental data point exists beyond that: during this fix round's
  device session, this emulator is shared across concurrent sessions/users,
  and something other than this agent's own scripted input (most likely a
  human interacting with the emulator's own window - the log showed real
  `GazePinch` drag callbacks the agent never issued) left the app mid-game
  against `WOOD_02`. A screenshot taken at that moment showed the score panel
  ("100"), next-piece preview, and stacked candy cubes all clearly legible
  against the wood plate, paused overlay included, nothing visibly broken.
  That is real but incidental emulator-only evidence, not a deliberate
  verification pass, and says nothing about the real headset's actual
  IBL/lighting. The status panels sit close to
  the plate, at `platePanelY = renderer.basePlateOffsetY + PLATE_PANEL_LIFT_M`
  in `GamePage.kt` (whose own comment calls `PLATE_PANEL_LIFT_M` "Estimated"
  and may need nudging to avoid clipping into the plate) — that estimate was
  only ever deliberately eyeballed against the old translucent glass with
  `depthWrite = false`, not an opaque PBR surface. Still needs a deliberate
  real playthrough with each material on the real headset.

**Agent verification limits, learned the hard way this session:**

- **Corrected 2026-08-26 (was wrong for the emulator half — see "Base plate
  material picker" above): emulator screenshots DO show real Stage content.**
  This bullet used to claim that on the emulator, `screencap` "succeeds — and
  that is a trap: the PNG contains only the emulator's virtual room
  environment, with none of the app's Stage content in it." That claim is
  false, at least for `pico-cli capture screenshot --device emulator-5554`:
  all 6 screenshots taken while verifying the base-plate material picker
  (`task7-WOOD_02.png` through `task7-persistence-after-restart.png`) show
  the full composited Stage — base plate, the 空间方块 start-screen card, and
  the material swatch row with its selection ring, all clearly rendered, not
  an empty room. This repo's history starts from a single squashed init
  commit, so there's no earlier context to recover on what originally
  produced the "empty room" belief — treat it as a stale/incorrect claim, not
  a documented regression to chase. **The real-device half is unchanged**:
  `adb shell screencap` and `pico-cli capture screenshot` are still believed
  to fail outright on a physical headset ("Failed to take screenshot") — that
  part was never contradicted by this task or any other. **Going forward**:
  `pico-cli capture screenshot --device <serial>` against the emulator is a
  legitimate way for the agent to see rendered Stage content — pair it with a
  real log-anchor before shooting (see the persistence technique in "Base
  plate material picker" above for the known stale-screenshot timing issue,
  a separate and still-real caveat), rather than assuming screenshots are
  useless. Real-device appearance still needs the user's own eyes; ask with
  concrete multiple-choice options rather than an open "does it look right?".
- `adb shell am start` frequently fails with "Activity not started because the
  current activity is being kept for the user" when the headset isn't being
  worn, or when another app holds the foreground. That is not a crash and not a
  code problem — commit the work and ask the user to launch it themselves.
- **The `swan_oversea` emulator (API 36, display id 6.0.0) IS worth using** — for
  no-crash/no-ANR verification, confirming startup logging reaches the points
  it should, any value the app can measure and log itself, *and* (corrected
  2026-08-26, see the bullet above) for real Stage visuals via `pico-cli
  capture screenshot`. It confirmed `pixelsPerMeter=2500.0` and the
  head-height calibration chain this way, and every base-plate-material-picker
  screenshot in "Base plate material picker" above. It has **no hand
  tracking** (`first latestData: left=false, right=false`), so no gesture
  behaviour can be tested there — that part of the original caveat still
  stands.
- Devices in this project are not interchangeable. `minSdk = 35`, so an
  Android-14/API-34 headset (e.g. the `PFDM MR` / `yvr_d3`, firmware
  `D3_4.3.0.162`) **cannot even install the app** — don't interpret that as a
  code failure. Check `getprop ro.build.version.sdk` and
  `ro.build.display.id` before drawing conclusions from an unfamiliar device.
