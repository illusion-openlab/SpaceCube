# Piece material untinted swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove per-piece-type `color_tint` from the PBR piece-material path so all 7 piece types share one untinted material per selection, per real-headset feedback that the forced tint looked bad.

**Architecture:** `PieceMaterialLoader.load()`'s PBR branch changes from 7 independent `loadFromAssetBundle` + `setParameter("color_tint", ...)` calls to a single `loadFromAssetBundle` call, with that one `Material` instance reused as the value for all 7 `PieceType` keys in the returned map. `BoardCubeRenderer` is untouched — it already just looks up `pieceMaterials?.get(type)`, and a shared instance behind multiple map keys is a normal, supported usage.

**Tech Stack:** Kotlin, PICO Spatial SDK 6.0.0 (`ShaderGraphMaterial`, `AssetBundle`), Jetpack Compose/SpatialUI (unaffected by this plan).

## Global Constraints

- Working directly on `main`, no worktree — same precedent as the rest of the piece-material-picker feature line this session.
- JELLY (the default look) must not change in any way — it is a plain-color `UnlitMaterial`, not a texture being tinted, and was not part of the feedback.
- `BoardCubeRenderer.kt`, `PieceMaterialPicker.kt`, `PieceMaterial.kt`, `PieceMaterialStore.kt`, and the appearance-settings panel wiring in `GamePage.kt` must not change — this plan is scoped to `PieceMaterialLoader.kt` and its documentation in `AGENTS.md`.
- Design spec: `docs/superpowers/specs/2026-08-26-piece-material-untinted-design.md` (amends `docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`).
- `adb shell input tap` cannot drive this app's spatial UI at all (confirmed repeatedly this session) — device verification uses the seed-`SharedPreferences`-and-relaunch technique (`AGENTS.md`, "Testing trick worth reusing for any other `SharedPreferences`-backed selection in this project").
- Every device/adb command must explicitly target `-s emulator-5554` / `--device emulator-5554` — a physical PICO headset is also attached and must never be touched.
- Acquire the device lock before any device command and release it immediately after: `bash /Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh acquire "<stable-owner-string>" 120 emulator-5554` / `... release "<same-owner-string>" emulator-5554`, with `PICO_LOCK_MAX_WAIT=120` exported before acquiring.

---

### Task 1: Remove per-type color tint from `PieceMaterialLoader`

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt` (full-file rewrite — small file, shown in full below)
- Modify: `AGENTS.md:195-199` (Key files entry)
- Modify: `AGENTS.md:770-772` (section intro, add pointer to the amendment)
- Modify: `AGENTS.md:786-816` (loader description + independence-probe paragraph)
- Modify: `AGENTS.md:817-828` (KNOWN GAP paragraph — handle count 28 → 4)

**Interfaces:**
- Consumes: `BaseMaterialsBundle.withBundle` (unchanged, from `content/BaseMaterialsBundle.kt`), `PieceMaterial`/`PieceType` enums (unchanged), `candyJellyColorFor(type: PieceType): Color4` (unchanged, from `content/CandyPieceColors.kt`, same-package so no import needed).
- Produces: `PieceMaterialLoader.load(selection: PieceMaterial): Map<PieceType, Material>` — **signature and return type are unchanged** from what `GamePage.kt` and `BoardCubeRenderer.kt` already call; only the PBR branch's internal behavior changes (all 7 values now `===` the same object for a given call). No caller needs to change.

- [ ] **Step 1: Replace `PieceMaterialLoader.kt` with the untinted version**

Replace the entire contents of `app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt` with:

```kotlin
package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.illusion.spacecube.game.PieceMaterial
import tech.illusion.spacecube.game.PieceType

private const val PIECE_MATERIAL_LOADER_LOG_TAG = "SpaceCubePieceMaterial"

// Same values BoardCubeRenderer.createCube() has always used for the
// falling-piece jelly look - reproduced here (not imported from there) so
// this loader owns its own JELLY material construction and never needs to
// reach into the renderer, matching how BasePlateMaterialLoader owns glass.
private const val CUBE_JELLY_OPACITY = 0.80f

private fun bundlePathFor(material: PieceMaterial): String = when (material) {
    PieceMaterial.JELLY -> error("JELLY has no AssetBundle path")
    PieceMaterial.WOOD_02 -> "BaseMaterials/Root/Wood_02/material/M_Wood_02"
    PieceMaterial.TILES_04 -> "BaseMaterials/Root/Tiles_04/material/M_Tiles_04"
    PieceMaterial.WOOD_12 -> "BaseMaterials/Root/Wood_12/material/M_Wood_12"
    PieceMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/Travertine_09/material/M_Travertine_09"
}

/**
 * Resolves a [PieceMaterial] selection to one [Material] per [PieceType],
 * via the [bundle] shared with [BasePlateMaterialLoader].
 *
 * JELLY reproduces `BoardCubeRenderer.createCube()`'s existing jelly-look
 * construction exactly (fresh [UnlitMaterial] per type, not per cube - see
 * [createJellyMaterials]) rather than routing through the PBR machinery
 * below, so the already-shipped default look can never regress by sharing
 * code with a newer, less-tested path.
 *
 * The 4 PBR options load ONE [ShaderGraphMaterial] per selection and reuse
 * that same instance as the value for all 7 [PieceType] keys in the
 * returned map - every piece type renders identically under a PBR
 * material (its natural, untinted look). This is deliberate (2026-08-26):
 * an earlier version of this loader tinted each of 7 separately-loaded
 * per-type instances via a `color_tint` Shader Graph parameter so piece
 * types stayed color-distinct under any material, but real-headset testing
 * found the forced tint on top of a wood/tile/stone texture looked bad, so
 * per-type tinting was removed. Piece-type identification under a PBR
 * material now relies on shape and the always-candy-colored "next piece"
 * preview panel ([NextPiecePreview]), not color on the pieces themselves.
 *
 * Historical note, kept so this isn't rediscovered by accident: whether 7
 * independently-loaded instances from the same bundle path are genuinely
 * independent (as opposed to sharing one cached native resource) WAS a
 * real, verified question when per-type tinting existed - confirmed
 * independent via two separate on-device probes (2026-08-26, see this
 * project's git history around commit 2e6d715 and
 * `docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`).
 * That question is moot now that every key deliberately shares one
 * instance, but if per-type visual distinction is ever reintroduced,
 * re-read that history before assuming a shared instance can be tinted
 * per-key - it cannot: `setParameter` on a shared [ShaderGraphMaterial]
 * changes every entity that references it, since they all reference the
 * same object.
 *
 * If the load fails, the WHOLE selection falls back to the JELLY map.
 *
 * KNOWN GAP - the loaded material is never released. [BasePlateMaterialLoader]
 * gets its cleanup for free: `setBasePlateMaterial` destroys and recreates the
 * ground entity, and destroying an entity releases the resources it held. The
 * piece path is the exact opposite by design - `BoardCubeRenderer` swaps
 * `ModelComponent.materials[0]` on cube entities that are pooled at
 * `attachTo()` time and never destroyed - so nothing ever closes the
 * [ShaderGraphMaterial] instance a PBR selection creates. Browsing all 4 PBR
 * swatches in one session therefore orphans 4 material handles (one per
 * selection - down from 7 per selection before per-type tinting was
 * removed, since every piece type now shares a single loaded instance),
 * bounded only by the app process ending (`BaseMaterialsBundle.close()` on
 * dispose drops the bundle's strong references, but per `AssetBundle.close()`'s
 * own docs that deliberately does not invalidate resources still in use).
 *
 * Closing the outgoing material when a new selection replaces it is NOT safe
 * as the renderer stands today, which is why it isn't done: `render()` only
 * rebinds cubes that are currently *enabled*, so every disabled (empty-cell)
 * cube keeps the previous selection's material in its slot 0 until the cell
 * next fills. A `close()` after a PBR->PBR swap would leave those pooled
 * entities holding an invalidated handle, and `render()` sets
 * `entity.enabled = true` *before* calling `bindCubeMaterial`. Fixing this
 * properly means giving `setPieceMaterials` a rebind-every-pooled-entity pass
 * for the PBR->PBR case too (it already has one for PBR->null), and only then
 * closing the old material - a renderer change, not a loader change.
 */
internal class PieceMaterialLoader(private val bundle: BaseMaterialsBundle) {
    suspend fun load(selection: PieceMaterial): Map<PieceType, Material> = withContext(Dispatchers.IO) {
        if (selection == PieceMaterial.JELLY) return@withContext createJellyMaterials()

        bundle.withBundle { loadedBundle ->
            if (loadedBundle == null) return@withBundle createJellyMaterials()

            val path = bundlePathFor(selection)
            runCatching { ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, path) }
                .fold(
                    onSuccess = { material -> PieceType.entries.associateWith { material } },
                    onFailure = { error ->
                        Log.w(
                            PIECE_MATERIAL_LOADER_LOG_TAG,
                            "failed to load piece material for $selection, falling back to jelly",
                            error,
                        )
                        createJellyMaterials()
                    },
                )
        }
    }

    private fun createJellyMaterials(): Map<PieceType, Material> =
        PieceType.entries.associateWith { type ->
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(candyJellyColorFor(type))
                setOpacity(CUBE_JELLY_OPACITY)
            }
        }
}
```

Note what's gone from the old file: the `COLOR_TINT_PARAMETER_NAME` constant, the `Color3`/`Color4` imports, the `Color4.toColor3()` extension, and the per-type `associateWith { type -> ... setParameter(...) }` loop with its all-or-nothing-across-7 failure aggregation (`firstOrNull { it.isFailure }`). The new single-load path only has one call that can fail, so `runCatching { }.fold(...)` replaces that aggregation directly — there is no partial-failure case to guard against anymore (there's only one load, not seven).

- [ ] **Step 2: Build and run the existing unit test suite**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/SpaceCube
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all existing unit tests pass (38 tests — this task adds none, since `PieceMaterialLoader` has never had a JVM unit test: it depends on the Spatial SDK's `AssetBundle`/`ShaderGraphMaterial`, which only run on-device, same as `BasePlateMaterialLoader`). This step exists to catch a compile error or an accidental signature change before spending device time.

- [ ] **Step 3: Device verification — PBR selection still loads and renders without crashing or falling back**

Acquire the device lock first:

```bash
export PICO_LOCK_MAX_WAIT=120
bash /Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh acquire "SpaceCube-piecematerial-untint" 120 emulator-5554
```

Install the rebuilt APK, then seed a PBR piece-material selection (any of the 4; `WOOD_12` used here for continuity with prior verification passes) and relaunch:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

adb -s emulator-5554 shell am force-stop tech.illusion.spacecube
adb -s emulator-5554 shell "run-as tech.illusion.spacecube sh -c \
  'cat > /data/data/tech.illusion.spacecube/shared_prefs/spacecube_piece_material.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="selected_material">WOOD_12</string>
</map>
XML

adb -s emulator-5554 logcat -c
pico-cli app launch tech.illusion.spacecube --activity .platform.LaunchActivity --device emulator-5554
```

Poll logcat for the board-build settle anchor rather than a fixed sleep:

```bash
adb -s emulator-5554 logcat -d | grep "initial: board build took"
```

Repeat the poll (short sleep between checks, not a single long fixed wait) until that line appears, then:

```bash
adb -s emulator-5554 logcat -d -b crash
adb -s emulator-5554 logcat -d | grep -i "falling back to jelly\|ResourceLoadingException\|FATAL\|AndroidRuntime"
```

Expected: the crash buffer is empty, and the grep for fallback/error markers returns nothing (an empty grep match is success here — it means the single-load path didn't fail and didn't fall back). Take a screenshot of the resulting start screen as a basic smoke-test artifact (`adb -s emulator-5554 exec-out screencap -p > <scratchpad>/untinted_regression.png`) — this confirms the app is alive and not stuck on an error screen; it does **not** show gameplay (pieces only render during play, and no automated technique in this project can press 开始游戏, same pre-existing limitation as every prior device check for this feature).

Clean up regardless of outcome:

```bash
adb -s emulator-5554 shell am force-stop tech.illusion.spacecube
adb -s emulator-5554 shell "run-as tech.illusion.spacecube sh -c \
  'rm -f /data/data/tech.illusion.spacecube/shared_prefs/spacecube_piece_material.xml'"
bash /Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh release "SpaceCube-piecematerial-untint" emulator-5554
```

- [ ] **Step 4: Update `AGENTS.md`'s Key files entry**

In the Key files list, find:

```
- `app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt` —
  resolves a `PieceMaterial` to **one `Material` per `PieceType`** (7, not 5),
  each PBR instance separately tinted via `color_tint` so the 7-color piece
  identification survives a material swap. Its materials are never released —
  see the KNOWN GAP paragraph in its KDoc.
```

Replace with:

```
- `app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt` —
  resolves a `PieceMaterial` to **one `Material` per `PieceType`** (the map
  has 7 keys, but for a PBR selection they all point at the *same* loaded
  instance — no per-type `color_tint` since 2026-08-26; see "Piece material
  picker" below). Its material is never released — see the KNOWN GAP
  paragraph in its KDoc.
```

- [ ] **Step 5: Update the "Piece material picker" section's intro to point at the amendment**

Find:

```
key `selected_material`), default `JELLY`. Design spec:
`docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`;
implementation plan: `docs/superpowers/plans/2026-08-26-piece-material-picker.md`.
```

Replace with:

```
key `selected_material`), default `JELLY`. Design spec:
`docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`;
implementation plan: `docs/superpowers/plans/2026-08-26-piece-material-picker.md`.
**Amended 2026-08-26** to remove per-piece-type color tinting from the PBR
path (real-headset feedback: the forced tint looked bad) — see
`docs/superpowers/specs/2026-08-26-piece-material-untinted-design.md` and
`docs/superpowers/plans/2026-08-26-piece-material-untinted.md`.
```

- [ ] **Step 6: Rewrite the loader-description paragraph in the "Piece material picker" section**

Find the paragraph beginning `- content/PieceMaterialLoader.kt — resolves a PieceMaterial to one` and ending at `... don't re-litigate this.` (the full block spanning the load/tint description and the independence-probe paragraph — currently `AGENTS.md:786-816`):

```
- `content/PieceMaterialLoader.kt` — resolves a `PieceMaterial` to one
  `Material` per `PieceType` (7 entries, one per candy color, not 5). `JELLY`
  reproduces `BoardCubeRenderer.createCube()`'s existing per-cube
  `UnlitMaterial` + `candyJellyColorFor()` look exactly, built fresh here
  rather than routed through the PBR path, so the shipped default can't
  regress by sharing code with the newer path. The other 4 each load one
  `ShaderGraphMaterial.loadFromAssetBundle(bundle, path)` per `PieceType`
  (`ShaderGraphMaterial` has no deep-copy per SDK docs, so this is 7 real
  loads, not 7 copies of one instance) from the same 4 bundle paths the
  base-plate picker already uses, then tints each with that type's
  `candyColorFor` color so the 7-color identification survives a material
  swap. **The Shader Graph input node name, `color_tint`, was confirmed live
  on-device in this plan's Task 3 Step 1 — not assumed** — via
  `ShaderGraphMaterial.setParameter("color_tint", Color3)`; anyone adding a
  future PBR-material feature to this project should reuse that name and
  call shape rather than re-deriving it. Any load/tint failure across the 7
  falls the WHOLE selection back to the jelly map (never a partial mix of old
  and new per-type materials), logged as a `SpaceCubePieceMaterial` "falling
  back to jelly" warning.
  **That the 7 loads really are 7 independent instances was also confirmed
  live on-device (final-review fix wave, 2026-08-26)**, and it was a genuine
  open question: `AssetBundle.releaseResource(path)` is path-keyed, which
  would be consistent with a path→resource cache handing every caller the same
  native material (in which case all 7 types would render in whichever tint
  was written last, defeating the whole feature). A throwaway probe loaded
  `BaseMaterials/Root/Wood_02/material/M_Wood_02` twice and got two distinct
  wrappers; setting only the first to red left the second reading the untinted
  default `Color3(1,1,1)`; after setting the second to blue the two read back
  `(1,0,0)` and `(0,0,1)`; and two simultaneously-visible cubes rendered red
  wood and blue wood side by side. **Path-keyed release does not imply a
  path-keyed load cache** — don't re-litigate this.
```

Replace with:

```
- `content/PieceMaterialLoader.kt` — resolves a `PieceMaterial` to one
  `Material` per `PieceType` (map has 7 keys). `JELLY` reproduces
  `BoardCubeRenderer.createCube()`'s existing per-cube `UnlitMaterial` +
  `candyJellyColorFor()` look exactly, built fresh here rather than routed
  through the PBR path, so the shipped default can't regress by sharing code
  with the newer path. The other 4 each load **one**
  `ShaderGraphMaterial.loadFromAssetBundle(bundle, path)` per selection —
  not per piece type — from the same 4 bundle paths the base-plate picker
  already uses, and that single instance is reused as the value for all 7
  `PieceType` keys in the returned map: every piece type renders identically
  (the material's natural, untinted look) under a PBR selection. If the load
  fails, the WHOLE selection falls back to the jelly map, logged as a
  `SpaceCubePieceMaterial` "falling back to jelly" warning.
  **This is a deliberate simplification made 2026-08-26**, after real-headset
  feedback that an earlier version — which tinted 7 *separately-loaded*
  per-type instances via the Shader Graph's `color_tint` parameter so piece
  types stayed color-distinct under any material — looked bad. Piece-type
  identification under a PBR material now relies on shape and the
  always-candy-colored "next piece" preview panel, not color on the pieces
  themselves. **`color_tint` is still a real, confirmed-live Shader Graph
  parameter name on these materials** (`ShaderGraphMaterial.setParameter`,
  confirmed on-device in the original piece-material-picker plan's Task 3) —
  it's simply unused now; a future feature that needs per-instance tinting
  again should reuse that confirmed name.
  **The independence of 7 separately-loaded instances from the same bundle
  path was verified twice on-device while per-type tinting existed (Task 3
  and the final-review fix wave, both 2026-08-26)** — no longer load-bearing
  for this feature now that every key deliberately shares one instance, but
  the evidence is preserved in `PieceMaterialLoader.kt`'s KDoc and this
  project's git history (`2e6d715`) in case per-type tinting is ever
  reconsidered. Don't rediscover this from scratch.
```

- [ ] **Step 7: Update the KNOWN GAP paragraph's handle count**

Find:

```
  **KNOWN GAP — the PBR materials are never released.** Unlike
  `BasePlateMaterialLoader`, whose materials are freed for free because
  `setBasePlateMaterial` destroys and recreates the ground entity, the piece
  cubes are pooled at `attachTo()` time and *never* destroyed (that's the whole
  point of the in-place `materials[0]` swap), so nothing closes the 7
  `ShaderGraphMaterial`s a PBR selection creates. Browsing all 4 PBR swatches
  in one session orphans 28 handles until the process exits. Closing the
  outgoing set is **not** safe as the renderer stands — `render()` only rebinds
  *enabled* cubes, so disabled ones keep the old material in slot 0 — and the
  full reasoning plus what a real fix would require lives in
  `PieceMaterialLoader`'s KDoc. Documented deliberately rather than patched
  under time pressure; a rushed `close()` here would be a use-after-close bug.
```

Note: this paragraph is a continuation of the same top-level bullet as the
loader-description paragraph above it (two-space indent, no leading `- `) —
confirmed against the live file at commit `2e6d715`, the commit this plan
was verified against. The original version of this plan incorrectly showed
a leading `- ` here; that was a transcription error in the plan, not
something that changed in the file.

Replace with:

```
  **KNOWN GAP — the PBR material is never released.** Unlike
  `BasePlateMaterialLoader`, whose material is freed for free because
  `setBasePlateMaterial` destroys and recreates the ground entity, the piece
  cubes are pooled at `attachTo()` time and *never* destroyed (that's the whole
  point of the in-place `materials[0]` swap), so nothing closes the
  `ShaderGraphMaterial` a PBR selection creates. Browsing all 4 PBR swatches
  in one session orphans 4 handles until the process exits (down from 28
  before per-type tinting was removed 2026-08-26, since every piece type now
  shares one loaded instance instead of each creating its own). Closing the
  outgoing material is **not** safe as the renderer stands — `render()` only
  rebinds *enabled* cubes, so disabled ones keep the old material in slot 0 —
  and the full reasoning plus what a real fix would require lives in
  `PieceMaterialLoader`'s KDoc. Documented deliberately rather than patched
  under time pressure; a rushed `close()` here would be a use-after-close bug.
```

- [ ] **Step 8: Commit**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/SpaceCube
git add app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt AGENTS.md
git commit -m "$(cat <<'EOF'
Remove per-piece-type color tint from PBR piece materials

Real-headset feedback: forcing color_tint on top of a PBR material's
own texture looked bad. All 7 piece types now share one untinted
material instance per PBR selection instead of 7 independently-tinted
ones - BoardCubeRenderer is unaffected (it already just looks up by
type). JELLY is unchanged. Also shrinks the previously-documented
material-leak gap 7x (1 handle per PBR browse instead of 7).
EOF
)"
```

---

## Self-Review Notes

- **Spec coverage:** §2 (single untinted instance, JELLY unchanged) → Step 1. §3 (loader simplification, leak footprint shrink, historical KDoc note) → Step 1 + Steps 6–7. §4 (device verification, same seed-and-relaunch technique, visible-gameplay-effect stays unverifiable) → Step 3. §5 (no change to base-plate picker, PBR material list, or picker UI) → not touched by any step, consistent with the spec's non-goals.
- **Placeholder scan:** no TBD/TODO; every step has literal code, exact `adb`/`gradlew` commands, and exact find/replace text for `AGENTS.md`.
- **Type consistency:** `PieceMaterialLoader.load(selection: PieceMaterial): Map<PieceType, Material>` — identical signature to the shipped version `BoardCubeRenderer.kt` and `GamePage.kt` already call against; no caller-side changes needed anywhere in this plan.
