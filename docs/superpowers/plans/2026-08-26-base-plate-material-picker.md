# Base-Plate Material Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the player pick the base plate's visual material from a 5-option
row (玻璃 + 4 PBR materials) on the start screen, persisted across app
restarts.

**Architecture:** A new `BasePlateMaterial` enum + `SharedPreferences`-backed
store (mirrors `HighScoreStore`) drives a `BasePlateMaterialPicker` Composable
on the `start_screen` panel. Selecting a swatch updates `BoardCubeRenderer`'s
ground entity via a new `setBasePlateMaterial` method, which destroys and
recreates the ground `ModelEntity` with the chosen `Material` (mesh/position
unchanged). Glass keeps its exact existing `UnlitMaterial` code path,
untouched. The 4 new materials are Spatial Editor Shader Graph exports —
SpaceCube's first use of Spatial Editor — packaged into one AssetBundle and
loaded via `ShaderGraphMaterial.loadFromAssetBundle`, lazily (only once a
non-glass swatch is actually selected).

**Tech Stack:** Kotlin, Jetpack Compose (via PICO's `com.pico.spatial.ui.*`
re-exports), PICO Spatial SDK 6.0.0 (`spatialBom`), JUnit (host unit tests),
AndroidJUnit4/Espresso (instrumented tests), Spatial Editor (asset export).

## Global Constraints

- SpatialUI only, wrapped in `PicoTheme` — Material/Material3 is forbidden in
  this project (`app/src/main/java/tech/illusion/spacecube/AGENTS.md`, "UI
  rule"). Run the `spatial-ui-design-style` verifier on every UI change:
  `bash <plugin-cache>/pico-spatial-agentic-tools/*/skills/spatial-ui-design-style/scripts/verify-design-style.sh app/src/main/java`
- Build with JDK 17, not the machine's default JDK 25:
  `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew <task>`
- Device/emulator work must go through this workspace's shared-device lock
  protocol: `PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh`
  (`acquire <owner> <ttl_seconds> emulator-5554` before touching the device,
  `release <owner> emulator-5554` immediately after `pico-cli app stop`). Use a
  stable owner string across all `acquire`/`release` calls in one task — not
  `$$`. Always pass `emulator-5554` explicitly (this workspace often has a
  real device attached too; the wrong serial silently locks the wrong device).
- Never claim a visual/interaction result works from an emulator screenshot
  alone if it depends on gaze, hand tracking, or real ambient lighting — this
  project's `AGENTS.md` already documents the emulator's hand-tracking as
  `DEVICE_NOT_SUPPORTED` and its lighting as not representative of a real
  headset. Flag such claims "unverified — needs real headset" instead.
- Preserve the existing glass base-plate code path's exact values
  (`GROUND_OPACITY = 0.80f`, base color `Color4(0.92f, 0.89f, 0.85f, 1f)`,
  `depthWrite = false`) — do not "improve" or re-derive them.

---

### Task 1: `BasePlateMaterial` enum + in-memory persistence store

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterial.kt`
- Create: `app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterialStore.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/InMemoryBasePlateMaterialStoreTest.kt`

**Interfaces:**
- Produces: `enum class BasePlateMaterial { GLASS, WOOD_02, TILES_04, WOOD_12, TRAVERTINE_09 }`;
  `interface BasePlateMaterialStore { fun get(): BasePlateMaterial; fun set(material: BasePlateMaterial) }`;
  `class InMemoryBasePlateMaterialStore : BasePlateMaterialStore`.

This mirrors the existing `HighScoreStore` / `InMemoryHighScoreStore` pattern
in `app/src/main/java/tech/illusion/spacecube/game/HighScoreStore.kt` — read
that file first if anything below is unclear.

- [ ] **Step 1: Write the failing test**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryBasePlateMaterialStoreTest {
    @Test
    fun `defaults to glass and remembers the last value set`() {
        val store = InMemoryBasePlateMaterialStore()
        assertEquals(BasePlateMaterial.GLASS, store.get())

        store.set(BasePlateMaterial.WOOD_02)
        assertEquals(BasePlateMaterial.WOOD_02, store.get())

        store.set(BasePlateMaterial.TRAVERTINE_09)
        assertEquals(BasePlateMaterial.TRAVERTINE_09, store.get())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.InMemoryBasePlateMaterialStoreTest"`
Expected: FAIL to compile — `BasePlateMaterial` and `InMemoryBasePlateMaterialStore` don't exist yet.

- [ ] **Step 3: Create the enum**

```kotlin
package tech.illusion.spacecube.game

/**
 * Visual material for the game board's base plate (井体底座). Purely
 * cosmetic - does not affect gameplay, board geometry, or the falling
 * piece's candy-color material.
 *
 * GLASS is the original look (see `BoardCubeRenderer`'s ground material) and
 * the default for a fresh install. The other four come from Spatial Editor
 * Shader Graph exports the user supplied (see
 * docs/superpowers/specs/2026-08-26-base-plate-material-picker-design.md).
 */
enum class BasePlateMaterial {
    GLASS,
    WOOD_02,
    TILES_04,
    WOOD_12,
    TRAVERTINE_09,
}
```

- [ ] **Step 4: Create the store interface + in-memory implementation**

```kotlin
package tech.illusion.spacecube.game

interface BasePlateMaterialStore {
    fun get(): BasePlateMaterial
    fun set(material: BasePlateMaterial)
}

class InMemoryBasePlateMaterialStore : BasePlateMaterialStore {
    private var current: BasePlateMaterial = BasePlateMaterial.GLASS

    override fun get(): BasePlateMaterial = current
    override fun set(material: BasePlateMaterial) {
        current = material
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.InMemoryBasePlateMaterialStoreTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterial.kt \
        app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterialStore.kt \
        app/src/test/java/tech/illusion/spacecube/game/InMemoryBasePlateMaterialStoreTest.kt
git commit -m "Add BasePlateMaterial enum and in-memory persistence store"
```

---

### Task 2: `SharedPreferences`-backed persistence (real cross-restart storage)

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterialStore.kt`
- Test: `app/src/androidTest/java/tech/illusion/spacecube/game/SharedPreferencesBasePlateMaterialStoreTest.kt`

**Interfaces:**
- Consumes: `BasePlateMaterial`, `BasePlateMaterialStore` (Task 1).
- Produces: `class SharedPreferencesBasePlateMaterialStore(context: Context) : BasePlateMaterialStore`.

The design explicitly calls for **true cross-restart persistence** (like
`SharedPreferencesHighScoreStore`), not the in-memory-only pattern
`GameSettings.difficulty` uses — the user confirmed this after learning
`difficulty` doesn't actually survive an app restart. Mirrors
`SharedPreferencesHighScoreStore` in `HighScoreStore.kt` exactly, including
its own SharedPreferences file (don't reuse `"spacecube_scores"` — this is an
unrelated setting).

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package tech.illusion.spacecube.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesBasePlateMaterialStoreTest {
    @Test
    fun selectionPersistsAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_base_plate_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        SharedPreferencesBasePlateMaterialStore(context).set(BasePlateMaterial.TILES_04)
        val reloaded = SharedPreferencesBasePlateMaterialStore(context)

        assertEquals(BasePlateMaterial.TILES_04, reloaded.get())
    }

    @Test
    fun defaultsToGlassWhenNothingStoredYet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_base_plate_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        assertEquals(BasePlateMaterial.GLASS, SharedPreferencesBasePlateMaterialStore(context).get())
    }

    @Test
    fun fallsBackToGlassOnUnrecognizedStoredValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_base_plate_material", Context.MODE_PRIVATE)
            .edit().putString("selected_material", "SOME_FUTURE_MATERIAL_THIS_VERSION_DOESNT_KNOW").apply()

        assertEquals(BasePlateMaterial.GLASS, SharedPreferencesBasePlateMaterialStore(context).get())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew connectedAndroidTest --tests "tech.illusion.spacecube.game.SharedPreferencesBasePlateMaterialStoreTest"`

(Requires a connected device/emulator — acquire the device lock first per the
Global Constraints, e.g. `bash PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh acquire "SpaceCube-materialpicker-task2" 180 emulator-5554`,
then `release` right after this step's test run finishes, whether it passes
or fails — don't hold the lock across steps.)

Expected: FAIL to compile — `SharedPreferencesBasePlateMaterialStore` doesn't exist yet.

- [ ] **Step 3: Implement it, appended to `BasePlateMaterialStore.kt`**

```kotlin
class SharedPreferencesBasePlateMaterialStore(context: Context) : BasePlateMaterialStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): BasePlateMaterial {
        val stored = prefs.getString(KEY_SELECTED_MATERIAL, null) ?: return BasePlateMaterial.GLASS
        return runCatching { BasePlateMaterial.valueOf(stored) }.getOrDefault(BasePlateMaterial.GLASS)
    }

    override fun set(material: BasePlateMaterial) {
        prefs.edit().putString(KEY_SELECTED_MATERIAL, material.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "spacecube_base_plate_material"
        private const val KEY_SELECTED_MATERIAL = "selected_material"
    }
}
```

Add `import android.content.Context` at the top of `BasePlateMaterialStore.kt`
(it isn't needed by the interface or the in-memory class, only this one).

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2, inside the same device-lock window.
Expected: all 3 tests PASS. Then release the device lock immediately
(`device-lock.sh release "SpaceCube-materialpicker-task2" emulator-5554`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/BasePlateMaterialStore.kt \
        app/src/androidTest/java/tech/illusion/spacecube/game/SharedPreferencesBasePlateMaterialStoreTest.kt
git commit -m "Add SharedPreferences-backed base-plate material persistence"
```

---

### Task 3: Extract the 4 thumbnail images as app assets

**Files:**
- Create: `app/src/main/assets/base_plate_thumbnails/wood_02.png`
- Create: `app/src/main/assets/base_plate_thumbnails/tiles_04.png`
- Create: `app/src/main/assets/base_plate_thumbnails/wood_12.png`
- Create: `app/src/main/assets/base_plate_thumbnails/travertine_09.png`

**Interfaces:**
- Produces: 4 PNG files at the paths above, each a 256×256 RGBA preview
  image, for Task 4's picker UI to load via `context.assets.open(...)`.

The user's source file is `/Users/zohar/Downloads/Base.usdz`. It is a
container: `Base.usdz`'s root references 4 independent nested `.usdz` files
(`Wood_02.usdz`, `Tiles_04.usdz`, `Wood_12.usdz`, `Travertine_09.usdz`), and
each of those contains a `thumbnail/thumbnail.usdz.png` (despite the name,
this is a plain 256×256 PNG, not a usdz). The zip entries come out with mode
`000` (no read permission) — `chmod` before use.

This task is purely mechanical extraction — no code changes.

- [ ] **Step 1: Extract and copy the 4 thumbnails**

```bash
WORK=$(mktemp -d)
DEST=/Users/zohar/WorkSpace/Project/PicoProjects/SpaceCube/app/src/main/assets/base_plate_thumbnails
mkdir -p "$DEST"

unzip -o -q "/Users/zohar/Downloads/Base.usdz" -d "$WORK/base"

declare -A NAMES=(
  [Wood_02]=wood_02
  [Tiles_04]=tiles_04
  [Wood_12]=wood_12
  [Travertine_09]=travertine_09
)

for src in "${!NAMES[@]}"; do
  dst="${NAMES[$src]}"
  unzip -o -q "$WORK/base/0/${src}.usdz" -d "$WORK/${src}"
  chmod 644 "$WORK/${src}/thumbnail/thumbnail.usdz.png"
  cp "$WORK/${src}/thumbnail/thumbnail.usdz.png" "$DEST/${dst}.png"
done

rm -rf "$WORK"
```

- [ ] **Step 2: Verify the 4 files exist and are real 256×256 PNGs**

```bash
for f in wood_02 tiles_04 wood_12 travertine_09; do
  file "/Users/zohar/WorkSpace/Project/PicoProjects/SpaceCube/app/src/main/assets/base_plate_thumbnails/${f}.png"
done
```

Expected: 4 lines, each reading `PNG image data, 256 x 256, 8-bit/color RGBA, non-interlaced`.
If any file is missing or shows `0 bytes`/`permission denied`-style output,
the `chmod` step was skipped for that file — re-run Step 1 for it.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/base_plate_thumbnails/
git commit -m "Add base-plate material thumbnail images"
```

---

### Task 4: `BasePlateMaterialPicker` UI — selection and persistence only

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialPicker.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `BasePlateMaterial`, `SharedPreferencesBasePlateMaterialStore`
  (Task 1/2); the 4 thumbnail assets (Task 3); `CandyCardInk`,
  `CandyCardInkDim`, `CandyAccentMint` from `CandyPanelTheme.kt`.
- Produces: `@Composable fun BasePlateMaterialPicker(selected: BasePlateMaterial, onSelect: (BasePlateMaterial) -> Unit)`,
  rendered inside the `start_screen` `AttachmentPanel`. A new
  `selectedBasePlateMaterial` state var in `GamePage()`, persisted through the
  store on every change. **Does not yet affect the 3D base plate** — that's
  Task 5. This task's own deliverable is "the picker renders, taps toggle
  selection, selection survives an app restart."

Read `NextPiecePreview.kt` and `CandyPanelTheme.kt` first — this task follows
their exact style (`Box`/`Column`/`Row` + `Modifier.background`/`.clip`, no
Material/Material3, `internal` visibility, no KDoc-free public API).

This project's SDK build excludes `androidx.compose.ui:ui`/`ui-graphics`/
`ui-text` and `androidx.compose.foundation:foundation` from direct
resolution, but PICO's own `com.pico.spatial.ui.*` artifacts re-export
classes under those same package names — `Canvas`, `background`, `border`,
`shape.RoundedCornerShape`, `foundation.gestures.detectTapGestures`, and
`ui.input.pointer.pointerInput` are confirmed to compile in this exact
project. **Do not use `Modifier.clickable`** — it hasn't been confirmed in
this project; use `pointerInput` + `detectTapGestures` instead, which has.

- [ ] **Step 1: Consult the `spatial-ui-design-style` skill before writing any code**

Invoke `pico-spatial-agentic-tools:spatial-ui-design-style` (via the `Skill`
tool) and read it before writing the composable below. This project's own
routing rules make this mandatory for any new Compose UI, not optional
follow-up: check whether it recommends a built-in component or hover/tap
idiom for a tappable custom swatch instead of the hand-rolled
`pointerInput`/`detectTapGestures` approach sketched in Step 2 below. If it
does, use that instead — the code in this brief is a working fallback we
know compiles in this project, not a mandate to ignore the skill's guidance.

- [ ] **Step 2: Create the picker file**

```kotlin
package tech.illusion.spacecube.content

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import tech.illusion.spacecube.game.BasePlateMaterial

private val SWATCH_SIZE = 44.dp
private val SWATCH_CORNER = 10.dp
private val SWATCH_GAP = 10.dp
private val SWATCH_LABEL_GAP = 4.dp
private val SWATCH_RING_WIDTH = 2.dp

// The glass swatch has no real texture (it's the app's own UnlitMaterial look,
// not a Spatial Editor asset) - approximated as a light translucent gradient,
// matching the picker layout mockup the user approved.
private val GLASS_SWATCH_GRADIENT = Brush.linearGradient(
    listOf(Color(0xFFC9E2F5), Color(0xFFEEF6FB))
)

private val BASE_PLATE_THUMBNAIL_ASSET_PATHS: Map<BasePlateMaterial, String> = mapOf(
    BasePlateMaterial.WOOD_02 to "base_plate_thumbnails/wood_02.png",
    BasePlateMaterial.TILES_04 to "base_plate_thumbnails/tiles_04.png",
    BasePlateMaterial.WOOD_12 to "base_plate_thumbnails/wood_12.png",
    BasePlateMaterial.TRAVERTINE_09 to "base_plate_thumbnails/travertine_09.png",
)

private fun loadBasePlateThumbnails(context: Context): Map<BasePlateMaterial, ImageBitmap> =
    BASE_PLATE_THUMBNAIL_ASSET_PATHS.mapValues { (_, path) ->
        context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream).asImageBitmap() }
    }

private fun basePlateMaterialLabel(material: BasePlateMaterial): String = when (material) {
    BasePlateMaterial.GLASS -> "玻璃"
    BasePlateMaterial.WOOD_02 -> "Wood_02"
    BasePlateMaterial.TILES_04 -> "Tiles_04"
    BasePlateMaterial.WOOD_12 -> "Wood_12"
    BasePlateMaterial.TRAVERTINE_09 -> "Travertine_09"
}

/**
 * The start-screen row for picking the base plate's visual material - 玻璃
 * (the existing look) plus 4 Spatial Editor PBR materials, each shown as a
 * small thumbnail swatch. Selecting one only updates [onSelect]'s state and
 * (via the caller) persists it; it does not itself touch any 3D entity.
 */
@Composable
internal fun BasePlateMaterialPicker(
    selected: BasePlateMaterial,
    onSelect: (BasePlateMaterial) -> Unit,
) {
    val context = LocalContext.current
    val thumbnails = remember { loadBasePlateThumbnails(context) }

    Column {
        Text(
            text = "底座材质",
            color = CandyCardInkDim,
            style = PicoTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
            BasePlateMaterial.entries.forEach { material ->
                BasePlateMaterialSwatch(
                    material = material,
                    thumbnail = thumbnails[material],
                    isSelected = material == selected,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun BasePlateMaterialSwatch(
    material: BasePlateMaterial,
    thumbnail: ImageBitmap?,
    isSelected: Boolean,
    onSelect: (BasePlateMaterial) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SWATCH_LABEL_GAP),
    ) {
        val ringModifier = if (isSelected) {
            Modifier.border(SWATCH_RING_WIDTH, CandyAccentMint, RoundedCornerShape(SWATCH_CORNER))
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .clip(RoundedCornerShape(SWATCH_CORNER))
                .then(ringModifier)
                .pointerInput(material) {
                    detectTapGestures(onTap = { onSelect(material) })
                },
        ) {
            if (thumbnail != null) {
                Canvas(modifier = Modifier.size(SWATCH_SIZE)) {
                    drawImage(thumbnail, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            } else {
                Box(modifier = Modifier.size(SWATCH_SIZE).background(GLASS_SWATCH_GRADIENT))
            }
        }
        Text(
            text = basePlateMaterialLabel(material),
            color = if (isSelected) CandyCardInk else CandyCardInkDim,
            style = PicoTheme.typography.bodySmall,
        )
    }
}
```

- [ ] **Step 3: Wire it into `GamePage.kt`**

In `GamePage()`, add near the other `remember`/store declarations (right
after the `highScoreStore` line):

```kotlin
val basePlateMaterialStore = remember { SharedPreferencesBasePlateMaterialStore(context) }
var selectedBasePlateMaterial by remember { mutableStateOf(basePlateMaterialStore.get()) }
```

Add the import: `import tech.illusion.spacecube.game.BasePlateMaterial` and
`import tech.illusion.spacecube.game.SharedPreferencesBasePlateMaterialStore`.

In the `start_screen` `AttachmentPanel` content (inside the existing
`CandyCard { Column(...) { ... } }`), add the picker directly below the
difficulty `Row` and above the "旋转：注视方块，双击旋转方块" hint text:

```kotlin
BasePlateMaterialPicker(
    selected = selectedBasePlateMaterial,
    onSelect = { material ->
        selectedBasePlateMaterial = material
        basePlateMaterialStore.set(material)
    },
)
```

- [ ] **Step 4: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any compile errors before continuing — the
most likely one is a missing/misnamed SpatialUI import; check the exact names
used in `NextPiecePreview.kt`/`CandyPanelTheme.kt` against what you wrote.

- [ ] **Step 5: Run the SpatialUI design-style verifier**

```bash
bash /Users/zohar/.claude/plugins/cache/pico-xr/pico-spatial-agentic-tools/*/skills/spatial-ui-design-style/scripts/verify-design-style.sh app/src/main/java
```

Expected: 0 errors. If it flags something (e.g. a raw color not routed
through a `Candy*`/`PicoTheme` role, or a disallowed import), fix it and
re-run — don't skip this, it's a hard project rule.

- [ ] **Step 6: Device verification**

```bash
export PICO_LOCK_MAX_WAIT=120
LOCK=/Users/zohar/WorkSpace/Project/PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh
bash "$LOCK" acquire "SpaceCube-materialpicker-task4" 240 emulator-5554
```

Then, with the lock held:

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device emulator-5554
adb -s emulator-5554 logcat -c
pico-cli app launch tech.illusion.spacecube --activity .platform.LaunchActivity --device emulator-5554
# poll for the process instead of a fixed sleep - board build alone can take
# ~50s on a loaded emulator
PKG=tech.illusion.spacecube
for i in $(seq 1 15); do
  pid="$(adb -s emulator-5554 shell pidof $PKG | tr -d '\r')"
  [ -n "$pid" ] && break
  adb -s emulator-5554 shell sleep 1
done
adb -s emulator-5554 shell sleep 55
adb -s emulator-5554 logcat -b crash -d
pico-cli capture screenshot --device emulator-5554 --out /tmp/task4-picker.png
```

Read `/tmp/task4-picker.png` and confirm: the start screen shows a "底座材质"
row with 5 swatches (glass gradient + 4 real thumbnails) below the difficulty
row, and 玻璃 shows the selected-ring highlight (it's the default). Confirm
`adb logcat -b crash -d` printed nothing for this run.

Then stop the app and release the lock in the same window:

```bash
pico-cli app stop tech.illusion.spacecube --device emulator-5554
bash "$LOCK" release "SpaceCube-materialpicker-task4" emulator-5554
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialPicker.kt \
        app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "Add base-plate material picker UI to the start screen"
```

---

### Task 5: `BoardCubeRenderer` material swap + lazy `BasePlateMaterialLoader`

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt`
- Create: `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `BasePlateMaterial` (Task 1), `selectedBasePlateMaterial` state
  and the `BasePlateMaterialPicker` callback (Task 4).
- Produces: `BoardCubeRenderer.attachTo(anchor: Entity, groundMaterial: Material)`
  (signature change - was `attachTo(anchor: Entity)`); `BoardCubeRenderer.setBasePlateMaterial(material: Material)`;
  `class BasePlateMaterialLoader { suspend fun load(selection: BasePlateMaterial): Material }`.
  Later tasks (7) only need to edit the two constants inside
  `BasePlateMaterialLoader.kt` — nothing in `BoardCubeRenderer` or `GamePage`
  changes again after this task.

**Important correctness note, confirmed via `pico-dev-knowledge`
(`spatial-sdk_resource-management_assetbundle.md`, "Cache management for
AssetBundle"): destroying a `ModelEntity` auto-releases the `Material`
instance it was holding.** That means the loader must **not** cache
individual `Material`/`ShaderGraphMaterial` instances across a swap (the old
one becomes invalid the moment its entity is destroyed) - only the
`AssetBundle` itself is cached. Loading the same material path from an
already-open bundle multiple times is cheap (the SDK caches by path
internally; the docs' own example loads the same path into two independent,
separately-closable instances that "occupy memory only once").

- [ ] **Step 1: Refactor `BoardCubeRenderer`'s ground handling**

In `BoardCubeRenderer.kt`, add the import
`import com.pico.spatial.core.ecs.resource.Material` alongside the existing
`UnlitMaterial` import.

Change the `attachTo` signature and body (currently around line 195):

```kotlin
suspend fun attachTo(anchor: Entity, groundMaterial: Material) {
    val locked = ArrayList<Cube>(boardWidth * boardHeight)
    for (index in 0 until boardWidth * boardHeight) {
        val cube = createCube(anchor, BlendingMode.TRANSPARENT, opacity = CUBE_JELLY_OPACITY)
        // Pinned position, written once: index encodes the cell.
        cube.entity.components[TransformComponent::class.java]
            ?.setPosition(cellPosition(index / boardWidth, index % boardWidth))
        locked.add(cube)
        if (index % ENTITY_CREATE_YIELD_INTERVAL == 0) yield()
    }
    lockedCubes = locked
    fallingCubes = List(MAX_PIECE_CELLS) {
        val cube = createCube(anchor, BlendingMode.TRANSPARENT, opacity = CUBE_JELLY_OPACITY)
        MovingCube(cube.entity, cube.material)
    }
    // An empty pool makes render()'s ghost loop a no-op - no need to branch there.
    ghostCubes = if (SHOW_GHOST_PIECE) {
        List(GHOST_POOL_SIZE) { createCube(anchor, BlendingMode.TRANSPARENT, opacity = GHOST_OPACITY) }
    } else {
        emptyList()
    }
    attachGround(anchor, groundMaterial)
}
```

(Only the last line and the signature changed — everything above is
unchanged from the current file, copied here so the diff is unambiguous.)

Replace `attachGround` (currently builds its own `UnlitMaterial` inline) with:

```kotlin
private var groundEntity: ModelEntity? = null
private var groundAnchor: Entity? = null

private fun attachGround(anchor: Entity, material: Material) {
    groundAnchor = anchor
    groundEntity = createGroundEntity(material).also { anchor.addChild(it) }
}

private fun createGroundEntity(material: Material): ModelEntity {
    val groundSpan = (boardWidth + GROUND_MARGIN_CELLS * 2) * CELL_STEP_M
    val mesh = MeshResource.createBox(Vector3(groundSpan, GROUND_THICKNESS_M, groundSpan), cornerRadius = 0.01f)
    val entity = ModelEntity(mesh, material)
    entity.components[TransformComponent::class.java]?.setPosition(Vector3(0f, basePlateOffsetY, 0f))
    return entity
}

/**
 * Swaps the base plate's material by destroying and recreating the ground
 * entity (mesh/position unchanged) - there is no confirmed in-place material
 * mutation API for switching between unrelated material types (e.g. Unlit ->
 * ShaderGraph), so this rebuilds instead of trying to mutate one.
 * No-op if [attachTo] hasn't run yet.
 */
fun setBasePlateMaterial(material: Material) {
    val anchor = groundAnchor ?: return
    groundEntity?.destroy()
    groundEntity = createGroundEntity(material).also { anchor.addChild(it) }
}
```

Delete the old `attachGround(anchor: Entity)` function body entirely (the one
that built `UnlitMaterial` inline with the comment about the base plate's
opacity history) — that logic moves to `BasePlateMaterialLoader` in Step 2,
verbatim, so the glass look is byte-for-byte unchanged.

- [ ] **Step 2: Create `BasePlateMaterialLoader`**

```kotlin
package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.illusion.spacecube.game.BasePlateMaterial

private const val BASE_PLATE_LOADER_LOG_TAG = "SpaceCubeBasePlateMaterial"

// Unchanged from BoardCubeRenderer's original attachGround() - moved here
// verbatim so the glass look never depends on whether the AssetBundle exists.
private const val GROUND_OPACITY = 0.80f

private const val BASE_MATERIALS_BUNDLE_PATH = "asset://base_materials.bundle"

// Filled in for real by Task 7, using whatever scene/material names Spatial
// Editor's export actually produced - see
// docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md
// for the confirmed values. These are best-guess placeholders until then;
// the try/catch in load() below means a wrong path here degrades to glass
// with a logged warning instead of crashing.
private fun bundlePathFor(material: BasePlateMaterial): String = when (material) {
    BasePlateMaterial.GLASS -> error("GLASS has no AssetBundle path")
    BasePlateMaterial.WOOD_02 -> "BaseMaterials/Root/MyMaterials/Wood_02"
    BasePlateMaterial.TILES_04 -> "BaseMaterials/Root/MyMaterials/Tiles_04"
    BasePlateMaterial.WOOD_12 -> "BaseMaterials/Root/MyMaterials/Wood_12"
    BasePlateMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/MyMaterials/Travertine_09"
}

/**
 * Resolves a [BasePlateMaterial] selection to a live [Material] instance.
 *
 * Lazy by design: glass never touches the AssetBundle at all. The bundle
 * itself is loaded at most once (cached for the loader's lifetime) on first
 * request for any non-glass material; each individual material load after
 * that is a fresh [ShaderGraphMaterial.loadFromAssetBundle] call rather than
 * an instance cache, because destroying the ModelEntity that held a previous
 * material auto-releases it (see AssetBundle release-semantics docs) - an
 * instance cache would hand back an already-released material on the second
 * selection of the same swatch.
 */
internal class BasePlateMaterialLoader {
    private var bundle: AssetBundle? = null

    suspend fun load(selection: BasePlateMaterial): Material = withContext(Dispatchers.IO) {
        if (selection == BasePlateMaterial.GLASS) return@withContext createGlassMaterial()

        val loadedBundle = bundle ?: runCatching { AssetBundle.load(BASE_MATERIALS_BUNDLE_PATH) }
            .onFailure {
                Log.w(BASE_PLATE_LOADER_LOG_TAG, "failed to load $BASE_MATERIALS_BUNDLE_PATH, falling back to glass", it)
            }
            .getOrNull()
            ?.also { bundle = it }
            ?: return@withContext createGlassMaterial()

        runCatching { ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, bundlePathFor(selection)) }
            .onFailure {
                Log.w(BASE_PLATE_LOADER_LOG_TAG, "failed to load material for $selection, falling back to glass", it)
            }
            .getOrDefault(createGlassMaterial())
    }

    private fun createGlassMaterial(): UnlitMaterial = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
        setBaseColor(Color4(0.92f, 0.89f, 0.85f, 1f))
        setOpacity(GROUND_OPACITY)
        setDepthWrite(false)
    }
}
```

- [ ] **Step 3: Wire it into `GamePage.kt`**

Add near `basePlateMaterialStore`:

```kotlin
val basePlateMaterialLoader = remember { BasePlateMaterialLoader() }
```

In the `initial = { content, attachments -> ... }` block, find
`renderer.attachTo(anchor)` and change it to:

```kotlin
renderer.attachTo(anchor, basePlateMaterialLoader.load(selectedBasePlateMaterial))
```

(This is already inside a `suspend` block and already runs on a background
path per the file's own comments about `attachTo`'s yielding — no additional
`withContext` needed here.)

Add a new effect, near the other top-level `LaunchedEffect`s in `GamePage()`
(after the `HandGestureController(...)` call is fine):

```kotlin
LaunchedEffect(selectedBasePlateMaterial) {
    if (!renderer.isAttached) return@LaunchedEffect
    renderer.setBasePlateMaterial(basePlateMaterialLoader.load(selectedBasePlateMaterial))
}
```

The `!renderer.isAttached` guard matters: this effect fires immediately on
first composition too, racing `initial`'s own `attachTo` call (which can take
tens of seconds - see the existing `isAttached` note on the frame-loop
`LaunchedEffect` for the same race). Without the guard, the very first
material would be built and torn down again a moment after `attachTo`
finishes, for no visible reason.

- [ ] **Step 4: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Device verification — glass regression + no-crash on all 5 swatches**

Acquire the device lock (same pattern as Task 4, new owner string e.g.
`"SpaceCube-materialpicker-task5"`), then:

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device emulator-5554
adb -s emulator-5554 logcat -c
pico-cli app launch tech.illusion.spacecube --activity .platform.LaunchActivity --device emulator-5554
# wait for process + board build, same polling pattern as Task 4 Step 5
pico-cli capture screenshot --device emulator-5554 --out /tmp/task5-glass-baseline.png
```

Confirm `/tmp/task5-glass-baseline.png` shows the base plate looking exactly
as it did before this task (warm-neutral translucent plate) — this is the
regression check for the refactor.

The AssetBundle (`base_materials.bundle`) does not exist yet at this point in
the plan (Task 7 creates it), so tapping a non-glass swatch on the emulator
right now cannot be used to prove those materials render correctly — only
that nothing crashes. `adb tap`/spatial-gesture automation on this UI is not
reliable for driving it (see this project's `AGENTS.md`, "debugging note
#5"), so don't try to script taps here. Instead:

```bash
adb -s emulator-5554 logcat -d | grep "SpaceCubeBasePlateMaterial"
```

If nothing printed, that's expected too (the effect only fires on a real
selection change, and nothing has been tapped). Just confirm:

```bash
adb -s emulator-5554 logcat -b crash -d
```

is empty, then stop the app and release the lock.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt \
        app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt \
        app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "Wire base-plate material swap through BoardCubeRenderer"
```

---

### Task 6: Spatial Editor asset pipeline — import materials, export AssetBundle

**This task is exploratory.** No one has used Spatial Editor on this project
before, and the exact steps to import 4 pre-exported material-library `.usdz`
packages and produce a loadable AssetBundle are not known in advance. Expect
to adjust the plan below based on what the Editor's actual UI/MCP tools
support — do not force a step that doesn't match what you observe.

**Files:**
- Create: `app/src/main/assets/base_materials.bundle` (exact name may change
  based on what Spatial Editor actually produces — if it does, update
  `BASE_MATERIALS_BUNDLE_PATH` in `BasePlateMaterialLoader.kt` too, as part of
  this task, not Task 7).
- Create: `docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md`

**Interfaces:**
- Consumes: the 4 nested `.usdz` material packages inside
  `/Users/zohar/Downloads/Base.usdz` (`0/Wood_02.usdz`, `0/Tiles_04.usdz`,
  `0/Wood_12.usdz`, `0/Travertine_09.usdz` after unzipping it — reuse Task
  3's extraction approach, or extract fresh; these are separate from the
  thumbnail PNGs Task 3 pulled out).
- Produces: a `.bundle` file placed in `app/src/main/assets/`, plus a written
  note (the file above) recording: the exact bundle filename used, and the
  exact `bundle.loadMaterial(...)`/`ShaderGraphMaterial.loadFromAssetBundle(...)`
  path string for each of the 4 materials, as actually confirmed to load
  without error. Task 7 reads this note — it must be concrete enough that
  Task 7 does not need to re-derive anything.

- [ ] **Step 1: Invoke the `spatial-editor` skill**

Use the `Skill` tool with `pico-spatial-agentic-tools:spatial-editor`. Explain
the goal: import 4 standalone Spatial Editor material-library exports
(re-extracted from the user's `/Users/zohar/Downloads/Base.usdz`, each a
`Material`-only `.usdz` with no mesh — `ND_UsdPreviewSurface_surfaceshader`
Shader Graph materials with base-color/normal/roughness textures) into a
SpaceCube Spatial Editor project, expose all 4 in one scene so they can be
loaded individually by path at runtime, and export the scene as an
AssetBundle. Establish editor backend readiness first (per that skill's own
instructions — `ensure_editor_ready` or equivalent) before attempting any
scene mutation; select the concrete import/organize/export tool calls from
the live runtime tool list the skill exposes, not from guesses in this plan.

- [ ] **Step 2: Import the 4 materials and organize them in a scene**

Follow whatever the `spatial-editor` skill's actual workflow turns out to
require for importing an externally-supplied material `.usdz` (this may be a
straightforward asset-browser import, or may require an intermediate step —
document whichever it actually is). The goal state: one Spatial Editor scene
whose Hierarchy contains all 4 materials at a stable, discoverable path (the
docs' convention is a `MyMaterials` group under `Root`, but confirm the
actual resulting path rather than assuming it matches that convention).

- [ ] **Step 3: Export the AssetBundle and place it in the app**

Export via the editor's packaging capability (`pack_editor_bundle` or
equivalent, per the skill). Copy the resulting `.bundle` file to
`app/src/main/assets/base_materials.bundle` (or whatever name the export
actually produced — if different, use that real name consistently in this
task and in Step 5 below).

- [ ] **Step 4: Confirm each material actually loads, on-device**

This is the load-bearing verification for this task — a bundle that exports
without error but whose material paths are wrong is a silent failure (Task
5's `BasePlateMaterialLoader` swallows load errors and falls back to glass,
so a wrong path here would look like "still glass" on-device, not a crash).

Write a short, temporary standalone check — do not leave this in the app
permanently. The simplest option: temporarily hardcode one of the 4 real
paths you believe is correct into `bundlePathFor` in
`BasePlateMaterialLoader.kt`, rebuild, install, launch, tap that swatch on
the start screen (direct touch works for `AttachmentPanel` buttons on this
emulator per this project's established testing pattern — this is a flat 2D
panel, not the gaze/hand-tracking-dependent 3D gesture surface), screenshot,
and confirm the base plate visibly changed material and
`adb logcat -d | grep SpaceCubeBasePlateMaterial` shows no fallback warning
for that selection. Repeat for the remaining 3 paths. Use the device-lock
protocol for every install/launch/screenshot cycle, same as prior tasks.

- [ ] **Step 5: Write the notes file**

Create `docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md`
recording, concretely:

```markdown
# Spatial Editor asset pipeline notes — base-plate materials

- AssetBundle asset path used at runtime: `asset://<the real filename>`
- Confirmed-working `ShaderGraphMaterial.loadFromAssetBundle` paths:
  - WOOD_02: `<confirmed path>`
  - TILES_04: `<confirmed path>`
  - WOOD_12: `<confirmed path>`
  - TRAVERTINE_09: `<confirmed path>`
- Spatial Editor workflow actually used (so a future material addition
  doesn't have to rediscover this): <steps, in your own words, including any
  tool/skill call names that worked>
- Anything that didn't work as the plan assumed: <e.g. import format
  quirks, naming surprises, size/texture-resolution controls found or not
  found in the Editor's import settings>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/assets/base_materials.bundle \
        docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md
git commit -m "Add Spatial Editor AssetBundle for base-plate PBR materials"
```

(If Step 4 required a temporary hardcoded path edit to
`BasePlateMaterialLoader.kt`, revert that edit before this commit — Task 7 is
what makes those edits permanent, deliberately, with all 4 paths at once.)

---

### Task 7: Final wiring, full verification, and documentation

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/AGENTS.md`

**Interfaces:**
- Consumes: the notes file from Task 6.
- Produces: nothing new — this task finishes the feature and updates
  project docs to match this project's existing convention (see
  `AGENTS.md`'s "Verified so far" / "Still unverified" sections).

- [ ] **Step 1: Update the bundle path and per-material paths**

Open `docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md`
(Task 6's output) and copy its confirmed values into
`BasePlateMaterialLoader.kt`: update `BASE_MATERIALS_BUNDLE_PATH` and the
body of `bundlePathFor` to match exactly. Remove the "best-guess placeholder"
comment above `bundlePathFor` (it's no longer a guess) but keep a short note
pointing at the editor-notes file for provenance.

- [ ] **Step 2: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full device verification — all 5 materials + persistence**

Acquire the device lock (owner e.g. `"SpaceCube-materialpicker-task7"`), then
install/launch as in prior tasks. For each of the 5 materials (玻璃, then the
4 swatches in the row's order): tap the swatch (direct touch on the
`AttachmentPanel`, not a gaze gesture — this is a flat 2D panel button, same
as the existing difficulty buttons), wait ~2s, screenshot to
`/tmp/task7-<material>.png`. Confirm each screenshot shows a visibly
different base-plate surface and `adb logcat -b crash -d` stays empty
throughout.

Then verify persistence: with a non-glass material selected, force-stop and
relaunch the app (`pico-cli app stop` then `pico-cli app launch` again), and
confirm the start screen comes back up with that same material's swatch
already showing the selected ring (not reset to glass). Screenshot this as
`/tmp/task7-persistence-after-restart.png`.

Stop the app and release the lock when done.

- [ ] **Step 4: Update `AGENTS.md`**

Add a new subsection under "Most natural next evolution paths" (or as its own
top-level section if that reads better once you see the current file) titled
something like "Base plate material picker (2026-08-26)". Follow this
project's existing documentation style exactly — see how the "V2 facts" and
"Verified so far" sections are written, including the explicit distinction
between what's build/emulator-verified versus what still needs a real
headset. At minimum, record:

- The feature exists: 5-option picker on the start screen, persisted via
  `SharedPreferencesBasePlateMaterialStore`.
- Build, install/launch, and all 5 on-device screenshots from Step 3 are
  verified (link/name the screenshots or just describe what they showed).
- **Explicitly unverified**: how the 4 PBR materials actually look under
  `StageStyle.Mixed`'s automatic system IBL on a real headset — the emulator
  screenshot confirms the materials render as *something* distinct from
  glass and from each other, not that the lighting/appearance is correct.
  Point at `docs/superpowers/specs/2026-08-26-base-plate-material-picker-design.md`
  §2 for why this app doesn't add its own lighting rig for this feature.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt \
        app/src/main/java/tech/illusion/spacecube/AGENTS.md
git commit -m "Finish base-plate material picker: real AssetBundle paths + verification notes"
```
