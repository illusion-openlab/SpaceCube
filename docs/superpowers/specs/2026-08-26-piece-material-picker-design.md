# Piece material picker — design

Status: approved by user 2026-08-26, ready for implementation planning.

## 1. What this is

Extend the material-selection system just shipped for the base plate
(`docs/superpowers/specs/2026-08-26-base-plate-material-picker-design.md`) to
the falling/locked *pieces* themselves: let the player choose a visual
material for the 7 tetromino types, among 5 options — **果冻/jelly** (the
existing look, unchanged) plus the same 4 PBR materials already bundled for
the base plate (Wood_02, Tiles_04, Wood_12, Travertine_09). No new Spatial
Editor work, no new assets — this reuses `app/src/main/assets/base_materials.bundle`
and its 4 confirmed `ShaderGraphMaterial` paths as-is.

Both the base-plate picker and this new picker move off the start-screen
card into a single new "外观设置" (Appearance Settings) panel, reachable via
one entry button on the start screen. Selection stays start-screen-only (no
mid-game switching), matching the base-plate picker's existing rule.

## 2. The core tension this design exists to resolve

The 7 piece colors are not decoration — `CandyPieceColors.kt`'s own comment
says cube opacity is "deliberately kept fairly opaque: the 7 candy colors are
the player's piece-identification channel, and legibility degrades fast once
stacked cubes show through each other." Applying a PBR material naively (one
texture, one look, no per-type variation) would make all 7 piece types
visually identical — the game becomes unplayable by sight.

**Resolution: color-tint the material per piece type at runtime.** The
Shader Graph material behind each of the 4 bundled materials is not a fixed
texture — it exposes a `color_tint` input node (`color3f inputs:color_tint`,
discovered when the base-plate material's node graph was inspected during
that feature's Spatial Editor exploration). Per SDK docs
(`spatial-sdk_rendering_shadergraphmaterial.md`, "Manage custom parameters
for ShaderGraphMaterial"), a `Color3`-typed Shader Graph input node is a
real, runtime-settable parameter via `shaderGraphMaterial.setParameter(name,
value)`. So: load one `ShaderGraphMaterial` instance per piece type from the
same bundled material, each with `setParameter("color_tint", <that piece's
candy color>)` — the material still shows its texture (wood grain, marble
banding), but tinted toward the piece's own hue, the same way the current
"frosted jelly" look tints white toward the piece's `candyColorFor()`.

**This is a documented API path, not yet exercised in this codebase** — no
implementation has called `ShaderGraphMaterial.setParameter` here before.
Verify early (first thing in implementation, before building the rest of the
feature around it): load one bundled material, call `getParameterNames()` to
confirm `"color_tint"` is really the parameter's name (Spatial Editor lets an
artist rename input nodes; don't assume the name without checking), and
confirm `setParameter("color_tint", Color3(...))` visibly changes the
material's tint on a test entity.

**Explicitly flagged as unverified, not assumed to work well**: whether 7
tint colors read as clearly distinct once applied over a busy wood-grain or
marble texture, especially at the small scale of a stacked board and from
across a room on a real headset. This needs real gameplay + real-device
judgment, not just a code review. See §7.

## 3. UX

- Start screen: the existing "底座材质" swatch row is removed. In its place,
  a single "外观设置" entry button (same visual weight as the existing 暂停
  button, styled consistently with the rest of the start card).
- Tapping it opens a new panel (a new `AttachmentPanel`, `CandyCard`-styled
  like the other overlays) containing two swatch rows, stacked:
  1. **底座材质** — the existing picker, moved here unchanged (same swatches,
     same selection/highlight behavior, same persistence).
  2. **方块材质** — the new picker, visually identical component style
     (thumbnail swatch + label + selection ring), 5 options: 果冻 (default,
     shown as the current frosted-jelly candy-color look — a small
     illustrative swatch, not a single real material image, same treatment
     as 玻璃's swatch on the base-plate picker) + the 4 PBR materials, using
     the *same* thumbnail images already bundled for the base-plate picker
     (`app/src/main/assets/base_plate_thumbnails/`) — no new thumbnail
     assets needed.
  A close/back control returns to the start screen.
- Selection is start-screen-only, exactly like the base-plate picker: no
  entry point in the pause or game-over overlays.
- Default (first launch / no saved preference): 果冻 (jelly) — the current
  look, unchanged.
- Purely visual, like the base-plate picker: does not affect scoring,
  difficulty, controls, or any gameplay logic. Piece *shape* recognition
  (the actual tetromino outlines) is completely unaffected regardless of
  material — only the fill/texture changes.

## 4. Data model & persistence

New enum, styled like `BasePlateMaterial`:

```kotlin
enum class PieceMaterial { JELLY, WOOD_02, TILES_04, WOOD_12, TRAVERTINE_09 }
```

A parallel `PieceMaterialStore` interface + `InMemoryPieceMaterialStore` +
`SharedPreferencesPieceMaterialStore`, structurally identical to
`BasePlateMaterialStore` (own SharedPreferences file, true cross-restart
persistence, `runCatching { valueOf(...) }` fallback to `JELLY` on an
unrecognized stored value). Kept as a **separate** store/enum from
`BasePlateMaterial` — the two pickers are independent choices (confirmed with
the user: not merged into one "theme" concept), even though they currently
draw from the same 4-material library.

## 5. Reused vs. new infrastructure

**Reused as-is, no changes:**
- `app/src/main/assets/base_materials.bundle` and its 4 confirmed
  `ShaderGraphMaterial` paths (`BaseMaterials/Root/<Name>/material/M_<Name>`).
- `app/src/main/assets/base_plate_thumbnails/*.png` — the same 4 thumbnails,
  reused for both pickers' swatches.
- The `SharedPreferencesXStore` / `InMemoryXStore` pattern (mirrored, not
  imported — each feature keeps its own small store class, matching how
  `HighScoreStore` and `BasePlateMaterialStore` are already two independent
  implementations of the same shape rather than one shared generic class;
  this project's existing convention, not a new decision).

**Refactored (existing, shipped code — a deliberate, scoped improvement, not
a rewrite):** `BasePlateMaterialLoader` currently owns both (a) the cached
`AssetBundle` reference + mutex + open/fallback logic, and (b) glass-specific
material construction. This feature needs the *same* cached bundle (opening
it twice, once per loader, would double the 25MB load and defeat the whole
point of caching by path). Split out a small shared piece:

```kotlin
internal class BaseMaterialsBundle {
    // owns: private var bundle: AssetBundle?, the Mutex, open-with-fallback
    suspend fun withBundle(action: suspend (AssetBundle) -> Unit) // or similar shape
    fun close()
}
```

`BasePlateMaterialLoader` keeps its own glass-construction logic and its own
`bundlePathFor` map, but delegates bundle access to a shared
`BaseMaterialsBundle` instance instead of owning one itself. `GamePage`
constructs one `BaseMaterialsBundle`, passes it to both
`BasePlateMaterialLoader` and the new `PieceMaterialLoader`, and closes it
once (not twice) in the existing cleanup `DisposableEffect`. This is the only
change to already-shipped code this feature requires — everything else is
additive.

## 6. New runtime piece: `PieceMaterialLoader`

```kotlin
internal class PieceMaterialLoader(private val bundle: BaseMaterialsBundle) {
    suspend fun load(selection: PieceMaterial): Map<PieceType, Material>
}
```

- `JELLY` returns 7 fresh `UnlitMaterial` instances built exactly like
  today's `createCube()` does (`BlendingMode.TRANSPARENT`,
  `candyJellyColorFor(type)`, `CUBE_JELLY_OPACITY`) — **the existing jelly
  code path is not touched or rerouted through this loader's PBR machinery**,
  it's reproduced here so JELLY and the 4 PBR options share one return type
  (`Map<PieceType, Material>`) and one call site in the renderer. This
  mirrors the base-plate design's explicit choice to leave glass's code path
  untouched rather than generalize it away.
- The 4 PBR options each load **one** `ShaderGraphMaterial` from the shared
  bundle per piece type (7 loads, not 7 copies of a deep-copied instance —
  `ShaderGraphMaterial` doesn't support deep copy per SDK docs, so each of
  the 7 is its own `loadFromAssetBundle` call against the same bundle path),
  each with `setParameter("color_tint", <that type's candy color as a
  Color3>)` applied immediately after loading. `candyColorFor()` returns a
  `Color4` (it has an alpha channel; `color_tint` is `Color3`, no alpha) —
  the exact conversion (almost certainly constructing a `Color3` from the
  same three RGB channels `candyColorFor` already derives from
  `candyPieceHex()`, dropping alpha) is not yet confirmed against the real
  `Color3` class API and is part of the same early-verification pass as
  `setParameter`/`color_tint` itself in §2. Do not assume a conversion
  helper exists without checking — none has been found in this codebase or
  the SDK docs read so far.
  Same lazy-loading contract as the base-plate loader: JELLY (the default)
  never touches the bundle at all.
- Same defensive fallback pattern as `BasePlateMaterialLoader`: any load
  failure for the *whole* selection (bundle open failure, one of the 7
  per-type loads failing, a wrong/renamed `color_tint` parameter name) falls
  back to the JELLY set with a logged warning, not a partial/mixed result —
  either all 7 types get the new material or none do.

## 7. `BoardCubeRenderer` changes

This is the part that differs most from the base plate, because pieces are
not one entity — up to 184 (`BOARD_WIDTH * BOARD_HEIGHT` locked slots + 4
falling slots) `ModelEntity`s exist, and which one is visible/what type it
represents changes continuously during play (a lock, a new spawn, roughly
every 1-2 seconds of real play, not every frame).

- **Selection change (rare, start-screen only, before any cell is
  occupied):** `render()` has never run with real board content at the point
  a piece-material selection can change (gameplay hasn't started, so every
  pooled cube entity is still `enabled = false` from `attachTo`). Applying a
  new `Map<PieceType, Material>` to all 184+4 pooled entities at this point
  is cheap and has zero visible pop-in risk, since nothing is shown yet. Add
  `fun setPieceMaterials(materials: Map<PieceType, Material>)` to
  `BoardCubeRenderer` that stores the map and, for every pooled `Cube`/
  `MovingCube` whose *current* type is known, re-binds its entity's material.
- **Ongoing gameplay (a cell's occupied type changes):** today, `render()`
  calls `cube.material.setBaseColor(candyJellyColorFor(lockedType))` per
  visible cell every time `engine.revision` changes. For a non-JELLY
  selection, the equivalent operation is not "recolor the same material" —
  it's "bind the correct pre-built per-type material instance to this
  entity." **Use the in-place mutation API confirmed to exist during the
  base-plate feature's final review** (`ModelComponent.materials[0] =
  material`, `core-6.0.0` SDK sources — flagged then as "confirmed to exist
  but never exercised"; this is its first real use in this codebase) instead
  of `Entity.destroy()` + recreate, which the base-plate feature deliberately
  avoided using *because* its own docs call it "a relatively expensive
  operation" — destroying and recreating up to 184 entities every lock/spawn
  cycle would be a real performance problem in a way it never was for the
  base plate's single entity.
  **Verify this early too, alongside the `color_tint` check in §2**: confirm
  `ModelComponent.materials[0] = material` actually swaps the rendered
  material on a live, already-visible entity without requiring re-adding it
  to the scene or any other extra step. If it turns out not to work as
  documented, the fallback is the same destroy-and-recreate pattern the base
  plate uses, and a real device performance check (does 184-entity churn
  under a fast lock cadence cause visible hitching?) becomes a required gate
  before shipping rather than an assumption.
- `Cube`/`MovingCube`'s per-slot `UnlitMaterial` field becomes a more general
  reference to "whichever `Material` this slot currently shows" — for JELLY
  it's still a privately-owned `UnlitMaterial` recolored in place exactly as
  today (zero change to that path's mechanics); for a PBR selection it's a
  shared reference into the 7-entry map from `PieceMaterialLoader`.

## 8. UI implementation

- New `AppearanceSettingsPanel` composable (or similarly named), a new
  `AttachmentPanel` in `GamePage.kt`'s `initial` block, `CandyCard`-styled.
  Contains the relocated `BasePlateMaterialPicker` call and a new
  `PieceMaterialPicker` call, stacked.
- `PieceMaterialPicker` is structurally near-identical to
  `BasePlateMaterialPicker` (same swatch/label/selection-ring pattern, same
  thumbnail assets, same tap-via-`pointerInput`+`detectTapGestures`
  approach). Consider factoring the shared swatch-row rendering into one
  internal composable both pickers call with different data (label list +
  thumbnails + selected/onSelect), rather than duplicating the whole file —
  a judgment call for the implementation plan, not mandated here; don't force
  it if the two pickers' visual details end up diverging (e.g. jelly's swatch
  treatment vs. glass's).
- The start screen's card loses its 底座材质 row and gains one "外观设置"
  button in its place. Consult the `spatial-ui-design-style` skill before
  writing any of this (project-mandatory), same as the base-plate picker's
  own implementation did.

## 9. Testing & verification

- Unit tests for the new persistence store, mirroring
  `SharedPreferencesBasePlateMaterialStoreTest`.
- Device verification follows the same playbook developed for the base
  plate: `adb shell input tap` cannot drive this UI at all (confirmed, not
  just suspected, during that feature's work) — use the seed-`SharedPreferences`
  -and-relaunch technique for both the base-plate and piece-material
  selections.
- **New verification need this feature introduces that the base plate
  didn't have**: the base plate was verified by screenshotting the *start
  screen* only — nobody ever pressed "开始游戏" in any automated pass (`adb
  tap` can't reach that button either). This feature's whole point is how
  pieces look *during play*, so screenshotting the start screen's swatch row
  proves the picker UI works but proves nothing about the feature itself.
  Actual gameplay screenshots (pieces falling, locking, stacking) are
  needed, and per the established limitation, driving actual gameplay input
  on the emulator isn't reliably scriptable — this will most likely need the
  user's own hands-on test on the real headset, flagged as such from the
  start rather than discovered late.
- Explicitly unverified until real gameplay is observed: tint legibility
  (§2) and the in-place material-swap performance/correctness under a real
  lock/spawn cadence (§7).

## 10. Non-goals

- No per-piece-type independent material choice (one style applies to all 7
  types at once, each auto-tinted).
- No mid-game or pause-menu switching for either picker (matches the
  existing base-plate rule, reconfirmed for this feature).
- No new Spatial Editor work, no new material assets — strictly reuses the 4
  materials and their thumbnails already shipped.
- No change to piece shapes, rotation, scoring, or any other gameplay
  mechanic — material is a pure visual layer over unchanged game state.
