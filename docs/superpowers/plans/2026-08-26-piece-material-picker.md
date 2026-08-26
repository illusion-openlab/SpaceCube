# Piece Material Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the player pick a visual material for the falling/locked
tetromino pieces (果冻/jelly + the 4 PBR materials already shipped for the
base plate), each piece type auto-tinted with its own candy color so the
material change doesn't break color-based piece identification.

**Architecture:** Reuses `app/src/main/assets/base_materials.bundle` and its
4 confirmed material paths — no new Spatial Editor work. A new
`BaseMaterialsBundle` class (extracted from the already-shipped
`BasePlateMaterialLoader`) owns the one shared `AssetBundle` reference, used
by both the existing base-plate loader and a new `PieceMaterialLoader`. The
piece loader resolves a selection to 7 `Material` instances (one per
`PieceType`), each Shader-Graph-tinted via `setParameter("color_tint", ...)`
for the 4 PBR options, or 7 fresh `UnlitMaterial`s built exactly like today's
code for JELLY. `BoardCubeRenderer` swaps a cell's bound material in place
via `ModelComponent.materials[0] = material` when a PBR set is active,
leaving the existing per-cube `UnlitMaterial` + `setBaseColor()` path for
JELLY completely untouched. Both material pickers (base plate + piece) move
off the start screen into one new "外观设置" panel.

**Tech Stack:** Kotlin, Jetpack Compose (via PICO's `com.pico.spatial.ui.*`
re-exports), PICO Spatial SDK 6.0.0, JUnit (host tests), AndroidJUnit4
(instrumented tests).

## Global Constraints

- SpatialUI only, wrapped in `PicoTheme` — Material/Material3 is forbidden.
  Consult the `spatial-ui-design-style` skill before writing any new
  Compose UI, then run:
  `bash <plugin-cache>/pico-spatial-agentic-tools/*/skills/spatial-ui-design-style/scripts/verify-design-style.sh app/src/main/java`
- Build with JDK 17: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew <task>`.
- Device/emulator work goes through this workspace's shared-device lock:
  `PicoProjects/.claude/skills/spatial-design-first-build/scripts/device-lock.sh`
  (`acquire <owner> <ttl> emulator-5554` / `release <owner> emulator-5554`,
  stable owner string across every call in one task, never `$$`). Every
  `pico-cli`/`adb` command explicitly targets `--device emulator-5554` /
  `-s emulator-5554` — never an unscoped command (this workspace also has a
  physical PICO headset attached; it must not be touched by agent
  verification).
- `adb shell input tap` **cannot drive any of this app's UI at all** —
  confirmed, not suspected (base-plate feature's own device work proved
  this, including for flat 2D `AttachmentPanel` buttons). Use the
  seed-`SharedPreferences`-and-relaunch technique
  (`AGENTS.md`, "Base plate material picker" section) for any verification
  that depends on a persisted selection.
- **No automated tooling in this project can drive actual gameplay**
  (pressing "开始游戏", moving/locking pieces). Every task below is
  explicit about what it CAN verify on-device (persistence, UI rendering,
  no-crash) versus what genuinely needs the user's own hands-on test on a
  real headset (how tinted PBR pieces actually look while playing). Do not
  write or accept a verification step that pretends to cover the latter.
- Preserve the JELLY rendering path's exact current mechanics
  (`CUBE_JELLY_OPACITY = 0.80f`, `candyJellyColorFor()`, per-cube private
  `UnlitMaterial` + `setBaseColor()`) — do not route it through the new
  per-type-material-swap machinery built for the PBR options.
- Exact SDK facts to use verbatim (verified against `core-6.0.0-sources.jar`
  / `foundation-6.0.0-sources.jar`, not guessed):
  - `entity.components[ModelComponent::class.java]?.materials?.set(0, material)`
    swaps a `ModelEntity`'s bound material in place. `ModelComponent` is
    `@MainThread`; `materials` is a `val` (`ModelComponent.MaterialArray`,
    no wholesale reassignment); its `set(index, material)` calls
    `checkIsMainThread()` and throws if called off the main thread.
  - `Color3(red: Float, green: Float, blue: Float)` —
    `com.pico.spatial.core.math`. **No conversion helper exists** between
    `Color4` and `Color3` anywhere in the SDK or this codebase — construct
    one manually: `Color3(color4.red, color4.green, color4.blue)`
    (`Color4` already exposes `.red`/`.green`/`.blue`, used today in
    `CandyPieceColors.kt`'s `candyJellyColorFor`).
  - `ShaderGraphMaterial.setParameter(parameterName: String, value: Color3)`
    is one of 11 concrete overloads (not generic), `@Throws(ResourceLoadingException::class)`,
    runs via the SDK's internal `runOnScheduleThread` (not `@MainThread`
    semantics — safe to call from a background coroutine).
  - `ShaderGraphMaterial.getParameterNames(): Array<String>` (not `List`),
    also `@Throws(ResourceLoadingException::class)`.

---

### Task 1: `PieceMaterial` enum + persistence store

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/PieceMaterial.kt`
- Create: `app/src/main/java/tech/illusion/spacecube/game/PieceMaterialStore.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/InMemoryPieceMaterialStoreTest.kt`
- Test: `app/src/androidTest/java/tech/illusion/spacecube/game/SharedPreferencesPieceMaterialStoreTest.kt`

**Interfaces:**
- Produces: `enum class PieceMaterial { JELLY, WOOD_02, TILES_04, WOOD_12, TRAVERTINE_09 }`;
  `interface PieceMaterialStore { fun get(): PieceMaterial; fun set(material: PieceMaterial) }`;
  `class InMemoryPieceMaterialStore : PieceMaterialStore`;
  `class SharedPreferencesPieceMaterialStore(context: Context) : PieceMaterialStore`.

This is a direct mirror of `BasePlateMaterial.kt` / `BasePlateMaterialStore.kt`
(`app/src/main/java/tech/illusion/spacecube/game/`) — read those two files
first; this task's files follow their exact structure and style, just with
`PieceMaterial`/`JELLY` in place of `BasePlateMaterial`/`GLASS`.

- [ ] **Step 1: Write the failing unit test**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryPieceMaterialStoreTest {
    @Test
    fun `defaults to jelly and remembers the last value set`() {
        val store = InMemoryPieceMaterialStore()
        assertEquals(PieceMaterial.JELLY, store.get())

        store.set(PieceMaterial.WOOD_02)
        assertEquals(PieceMaterial.WOOD_02, store.get())

        store.set(PieceMaterial.TRAVERTINE_09)
        assertEquals(PieceMaterial.TRAVERTINE_09, store.get())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.InMemoryPieceMaterialStoreTest"`
Expected: FAIL to compile — `PieceMaterial` / `InMemoryPieceMaterialStore` don't exist yet.

- [ ] **Step 3: Create the enum**

```kotlin
package tech.illusion.spacecube.game

/**
 * Visual material for the falling/locked tetromino pieces. Purely cosmetic
 * - does not affect gameplay, piece shapes, or scoring.
 *
 * JELLY is the original look (see `BoardCubeRenderer`'s per-cube
 * `UnlitMaterial` + `candyJellyColorFor()`) and the default for a fresh
 * install. The other four reuse the same Spatial Editor Shader Graph
 * materials shipped for `BasePlateMaterial` (see
 * docs/superpowers/specs/2026-08-26-piece-material-picker-design.md) -
 * each auto-tinted per piece type at load time so switching materials
 * doesn't erase the 7-color identification the player relies on.
 */
enum class PieceMaterial {
    JELLY,
    WOOD_02,
    TILES_04,
    WOOD_12,
    TRAVERTINE_09,
}
```

- [ ] **Step 4: Create the store interface + in-memory implementation**

```kotlin
package tech.illusion.spacecube.game

interface PieceMaterialStore {
    fun get(): PieceMaterial
    fun set(material: PieceMaterial)
}

class InMemoryPieceMaterialStore : PieceMaterialStore {
    private var current: PieceMaterial = PieceMaterial.JELLY

    override fun get(): PieceMaterial = current
    override fun set(material: PieceMaterial) {
        current = material
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS.

- [ ] **Step 6: Write the failing instrumented test**

```kotlin
package tech.illusion.spacecube.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesPieceMaterialStoreTest {
    @Test
    fun selectionPersistsAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_piece_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        SharedPreferencesPieceMaterialStore(context).set(PieceMaterial.TILES_04)
        val reloaded = SharedPreferencesPieceMaterialStore(context)

        assertEquals(PieceMaterial.TILES_04, reloaded.get())
    }

    @Test
    fun defaultsToJellyWhenNothingStoredYet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_piece_material", Context.MODE_PRIVATE)
            .edit().clear().apply()

        assertEquals(PieceMaterial.JELLY, SharedPreferencesPieceMaterialStore(context).get())
    }

    @Test
    fun fallsBackToJellyOnUnrecognizedStoredValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_piece_material", Context.MODE_PRIVATE)
            .edit().putString("selected_material", "SOME_FUTURE_MATERIAL_THIS_VERSION_DOESNT_KNOW").apply()

        assertEquals(PieceMaterial.JELLY, SharedPreferencesPieceMaterialStore(context).get())
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Acquire the device lock first (owner e.g. `"SpaceCube-piecematerial-task1"`,
`export PICO_LOCK_MAX_WAIT=120` before acquiring).

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew connectedDebugAndroidTest --serial emulator-5554 -Pandroid.testInstrumentationRunnerArguments.class=tech.illusion.spacecube.game.SharedPreferencesPieceMaterialStoreTest`

(Use `connectedDebugAndroidTest --serial emulator-5554`, NOT the aggregate
`connectedAndroidTest` — the latter runs on every attached device including
the physical headset, which must not be touched. This is a lesson from the
base-plate plan's own Task 2.)

Expected: FAIL to compile — `SharedPreferencesPieceMaterialStore` doesn't exist yet.

- [ ] **Step 8: Implement it, appended to `PieceMaterialStore.kt`**

```kotlin
class SharedPreferencesPieceMaterialStore(context: Context) : PieceMaterialStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): PieceMaterial {
        val stored = prefs.getString(KEY_SELECTED_MATERIAL, null) ?: return PieceMaterial.JELLY
        return runCatching { PieceMaterial.valueOf(stored) }.getOrDefault(PieceMaterial.JELLY)
    }

    override fun set(material: PieceMaterial) {
        prefs.edit().putString(KEY_SELECTED_MATERIAL, material.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "spacecube_piece_material"
        private const val KEY_SELECTED_MATERIAL = "selected_material"
    }
}
```

Add `import android.content.Context` at the top of `PieceMaterialStore.kt`.

- [ ] **Step 9: Run test to verify it passes**

Same command as Step 7. Expected: all 3 tests PASS. Release the device lock
immediately after.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/PieceMaterial.kt \
        app/src/main/java/tech/illusion/spacecube/game/PieceMaterialStore.kt \
        app/src/test/java/tech/illusion/spacecube/game/InMemoryPieceMaterialStoreTest.kt \
        app/src/androidTest/java/tech/illusion/spacecube/game/SharedPreferencesPieceMaterialStoreTest.kt
git commit -m "Add PieceMaterial enum and persistence store"
```

---

### Task 2: Extract `BaseMaterialsBundle` (shared bundle owner)

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/BaseMaterialsBundle.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt`

**Interfaces:**
- Produces: `internal class BaseMaterialsBundle { suspend fun <T> withBundle(action: suspend (AssetBundle?) -> T): T; fun close() }`.
- Consumes (from `BasePlateMaterialLoader`, unchanged): `BasePlateMaterial`.
- Later tasks (3, 6) depend on `BaseMaterialsBundle` being constructible with
  no arguments and shareable across multiple loaders.

`BasePlateMaterialLoader` currently owns the cached `AssetBundle` + `Mutex` +
open-with-fallback logic itself. Task 3's `PieceMaterialLoader` needs the
*same* cached bundle — opening it a second time would double the 25MB load
and defeat the whole point of caching by path. This task moves that shared
piece out into its own class with **the exact same locking behavior as
today** (the mutex still wraps the *entire* per-call resolve, not just the
bundle-open — this task preserves that scope deliberately; see the "why" in
`BasePlateMaterialLoader`'s existing KDoc, unchanged in this task, about the
race two concurrent call sites can hit).

This is a refactor of already-shipped, device-verified code. The regression
bar is: base plate must behave **identically** after this task — same
values, same lazy-load contract, same fallback behavior.

- [ ] **Step 1: Create `BaseMaterialsBundle.kt`**

```kotlin
package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.AssetBundle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val BASE_MATERIALS_BUNDLE_LOG_TAG = "SpaceCubeBaseMaterialsBundle"
private const val BASE_MATERIALS_BUNDLE_PATH = "asset://base_materials.bundle"

/**
 * Owns the one shared `AssetBundle` for `base_materials.bundle`, used by
 * both `BasePlateMaterialLoader` and `PieceMaterialLoader` - opening it
 * twice (once per loader) would double the 25MB load and defeat the point
 * of caching by path. Extracted from `BasePlateMaterialLoader`, which
 * previously owned this alone; see that file's history for why the mutex
 * spans the *entire* [withBundle] call, not just the open: two independent
 * coroutines can call into a loader concurrently (a swatch tapped during
 * the ~60s cold-start board build races the `initial` block's own
 * resolve), and without full serialization both could see the bundle as
 * unopened at once and each call `AssetBundle.load(...)`.
 */
internal class BaseMaterialsBundle {
    private var bundle: AssetBundle? = null
    private val mutex = Mutex()

    /**
     * Runs [action] with exclusive access to the shared bundle, opening it
     * (once, cached for this instance's lifetime) on first use if it isn't
     * already open. [action] receives `null` if the open failed - callers
     * should fall back to their own default material rather than propagate
     * the failure.
     */
    suspend fun <T> withBundle(action: suspend (AssetBundle?) -> T): T = mutex.withLock {
        val loadedBundle = bundle ?: runCatching { AssetBundle.load(BASE_MATERIALS_BUNDLE_PATH) }
            .onFailure {
                Log.w(BASE_MATERIALS_BUNDLE_LOG_TAG, "failed to load $BASE_MATERIALS_BUNDLE_PATH", it)
            }
            .getOrNull()
            ?.also { bundle = it }
        action(loadedBundle)
    }

    /**
     * Closes the cached `AssetBundle`, if one was ever opened - per its
     * class-level docs ("Close the AssetBundle when no longer needed").
     * Safe to call more than once (a second call sees `bundle == null` and
     * no-ops). Expected to run only after every in-flight [withBundle] call
     * has already been cancelled - callers should invoke this from a
     * `DisposableEffect(Unit) { onDispose { ... } }` at the same composable
     * scope that owns this instance.
     */
    fun close() {
        val hadBundle = bundle != null
        bundle?.close()
        bundle = null
        Log.i(BASE_MATERIALS_BUNDLE_LOG_TAG, "close(): AssetBundle ${if (hadBundle) "closed" else "was already null, no-op"}")
    }
}
```

- [ ] **Step 2: Refactor `BasePlateMaterialLoader.kt` to use it**

Replace the whole file with:

```kotlin
package tech.illusion.spacecube.content

import android.util.Log
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

// Confirmed on-device (2026-08-26) by reading AssetInfo.json out of the built
// bundle rather than guessing - see
// docs/superpowers/plans/2026-08-26-base-plate-material-picker-editor-notes.md
// for the manifest one-liner and full provenance.
private fun bundlePathFor(material: BasePlateMaterial): String = when (material) {
    BasePlateMaterial.GLASS -> error("GLASS has no AssetBundle path")
    BasePlateMaterial.WOOD_02 -> "BaseMaterials/Root/Wood_02/material/M_Wood_02"
    BasePlateMaterial.TILES_04 -> "BaseMaterials/Root/Tiles_04/material/M_Tiles_04"
    BasePlateMaterial.WOOD_12 -> "BaseMaterials/Root/Wood_12/material/M_Wood_12"
    BasePlateMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/Travertine_09/material/M_Travertine_09"
}

/**
 * Resolves a [BasePlateMaterial] selection to a live [Material] instance,
 * via the [bundle] shared with [PieceMaterialLoader].
 *
 * Lazy by design: glass never touches the AssetBundle at all. Each
 * individual material load is a fresh [ShaderGraphMaterial.loadFromAssetBundle]
 * call rather than an instance cache, because destroying the ModelEntity
 * that held a previous material auto-releases it (see AssetBundle
 * release-semantics docs) - an instance cache would hand back an
 * already-released material on the second selection of the same swatch.
 */
internal class BasePlateMaterialLoader(private val bundle: BaseMaterialsBundle) {
    suspend fun load(selection: BasePlateMaterial): Material = withContext(Dispatchers.IO) {
        if (selection == BasePlateMaterial.GLASS) return@withContext createGlassMaterial()

        bundle.withBundle { loadedBundle ->
            if (loadedBundle == null) return@withBundle createGlassMaterial()
            runCatching { ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, bundlePathFor(selection)) }
                .onFailure {
                    Log.w(BASE_PLATE_LOADER_LOG_TAG, "failed to load material for $selection, falling back to glass", it)
                }
                .getOrElse { createGlassMaterial() }
        }
    }

    private fun createGlassMaterial(): UnlitMaterial = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
        setBaseColor(Color4(0.92f, 0.89f, 0.85f, 1f))
        setOpacity(GROUND_OPACITY)
        setDepthWrite(false)
    }
}
```

Note what's gone: the `AssetBundle`/`Mutex` fields, `BASE_MATERIALS_BUNDLE_PATH`
(moved to `BaseMaterialsBundle.kt`), and `close()` (moved there too, since
the bundle it closes is no longer this class's own).

- [ ] **Step 3: Update `GamePage.kt`'s construction + cleanup**

At `GamePage.kt:775-776` (currently):

```kotlin
    val basePlateMaterialStore = remember { SharedPreferencesBasePlateMaterialStore(context) }
    val basePlateMaterialLoader = remember { BasePlateMaterialLoader() }
```

change to:

```kotlin
    val basePlateMaterialStore = remember { SharedPreferencesBasePlateMaterialStore(context) }
    val baseMaterialsBundle = remember { BaseMaterialsBundle() }
    val basePlateMaterialLoader = remember { BasePlateMaterialLoader(baseMaterialsBundle) }
```

At `GamePage.kt:858-867` (the existing cleanup effect):

```kotlin
    DisposableEffect(Unit) {
        onDispose {
            soundEffects.release()
            // AssetBundle is Closeable (SDK class-level docs: "Close the
            // AssetBundle when no longer needed") - basePlateMaterialLoader
            // caches one internally once any non-glass material is picked,
            // and nothing else in this composable's lifecycle ever closed it.
            basePlateMaterialLoader.close()
        }
    }
```

change the last line and its comment to close the shared bundle instead of
the loader (which no longer has a `close()` method):

```kotlin
    DisposableEffect(Unit) {
        onDispose {
            soundEffects.release()
            // AssetBundle is Closeable (SDK class-level docs: "Close the
            // AssetBundle when no longer needed") - baseMaterialsBundle caches
            // one internally once any non-glass base-plate or piece material is
            // picked, shared by both loaders, and nothing else in this
            // composable's lifecycle ever closed it.
            baseMaterialsBundle.close()
        }
    }
```

- [ ] **Step 4: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Regression device check — base plate behaves identically**

Acquire the device lock (owner e.g. `"SpaceCube-piecematerial-task2"`),
install, launch, wait for the board-build log anchor
(`initial: board build took ...ms`), screenshot the start screen. Confirm:
visually identical to the pre-refactor base plate (glass default, swatch
row still renders — it's still on the start screen at this point, Task 5
moves it), `adb logcat -b crash -d` empty.

Then verify a real non-glass base-plate load still works, using the
seed-and-relaunch technique (this exercises `BaseMaterialsBundle.withBundle`
end to end, the part that actually changed):

```bash
adb -s emulator-5554 shell am force-stop tech.illusion.spacecube
adb -s emulator-5554 shell "run-as tech.illusion.spacecube sh -c \
  'cat > /data/data/tech.illusion.spacecube/shared_prefs/spacecube_base_plate_material.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="selected_material">WOOD_12</string>
</map>
XML
adb -s emulator-5554 logcat -c
pico-cli app launch tech.illusion.spacecube --activity .platform.LaunchActivity --device emulator-5554
```

Wait for the board-build anchor, screenshot: base plate should show the
Wood_12 material exactly as it did before this refactor. Confirm no crash,
no `falling back to glass` warning in logcat. Clean up: force-stop, delete
the seeded prefs file, release the lock.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BaseMaterialsBundle.kt \
        app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialLoader.kt \
        app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "Extract BaseMaterialsBundle so base-plate and piece materials share one AssetBundle"
```

---

### Task 3: `PieceMaterialLoader`

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt`

**Interfaces:**
- Consumes: `BaseMaterialsBundle` (Task 2), `PieceMaterial` (Task 1),
  `PieceType` (`game/PieceType.kt`), `candyColorFor` (`CandyPieceColors.kt`).
- Produces: `internal class PieceMaterialLoader(bundle: BaseMaterialsBundle) { suspend fun load(selection: PieceMaterial): Map<PieceType, Material> }`.
  Later tasks (4, 6) consume this exact signature. `JELLY` returns a map of
  7 fresh `UnlitMaterial`s (never touches the bundle); the 4 PBR options
  return a map of 7 `ShaderGraphMaterial`s, each tinted per its `PieceType`.
  On any failure the WHOLE selection falls back to the JELLY map - never a
  partial mix of old and new materials across the 7 types.

**This task's first step is a live proof-of-concept, before writing the
real loader** - the design spec flags `color_tint`'s exact runtime behavior
as unverified (the parameter's real name in the shipped bundle was never
confirmed - only seen in an *un-tinted* material's Shader Graph source
during the base-plate feature's Spatial Editor exploration, never actually
called via `setParameter` at runtime). Confirm it before building the rest
of the feature around it.

- [ ] **Step 1: Confirm the `color_tint` parameter name and that `setParameter` visibly changes appearance**

This step's own device-verification IS its deliverable - do this before
Step 2's "real" implementation. Write a small throwaway diagnostic (do not
leave it in the codebase after this step): temporarily add a few lines
inside `BasePlateMaterialLoader.load()` (or a scratch call site) that, right
after `ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, bundlePathFor(selection))`
succeeds for e.g. `BasePlateMaterial.WOOD_02`, calls
`.getParameterNames()` and logs the result:

```kotlin
val material = ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, bundlePathFor(selection))
Log.i("SpaceCubeColorTintProbe", "parameter names: ${material.getParameterNames().joinToString()}")
```

Build, install, launch on `emulator-5554` (device-lock protocol, same as
prior tasks), seed `WOOD_02` as the base-plate selection (seed-and-relaunch
technique) so this code path actually runs, and read the logged parameter
names from `adb logcat`. Confirm `"color_tint"` (or whatever the real name
turns out to be - **use the real logged name for the rest of this task**,
don't assume) is present and its type is consistent with `Color3` (the SDK
doc's type-mapping table: a Shader Graph "Color3" input node maps to the
SDK's `Color3` type).

Then, still as part of this throwaway probe, call
`material.setParameter(<real_name>, Color3(1f, 0f, 0f))` (bright red) on
that loaded `WOOD_02` material, apply it to the base plate the normal way
(`renderer.setBasePlateMaterial(material)`), and screenshot: confirm the
base plate visibly shows a red-tinted wood grain, not an untinted or
crashed result.

**Revert the throwaway probe code** (the `Log.i` call and the hardcoded red
`setParameter` test) before moving to Step 2 — this was a diagnostic, not a
permanent feature. If the parameter name turns out to be something other
than `"color_tint"`, or the tint visibly does nothing, or `setParameter`
throws, STOP and report back rather than proceeding to Step 2 with an
unconfirmed assumption — this is exactly the kind of "verify early" gate
the design spec asked for.

- [ ] **Step 2: Create `PieceMaterialLoader.kt`**

Use the real confirmed parameter name from Step 1 in place of
`"color_tint"` below if it turned out to be different.

```kotlin
package tech.illusion.spacecube.content

import android.util.Log
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color3
import com.pico.spatial.core.math.Color4
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

// The Shader Graph input node name confirmed live on-device in Task 3 Step 1
// (see that step for how this was confirmed, not guessed).
private const val COLOR_TINT_PARAMETER_NAME = "color_tint"

private fun bundlePathFor(material: PieceMaterial): String = when (material) {
    PieceMaterial.JELLY -> error("JELLY has no AssetBundle path")
    PieceMaterial.WOOD_02 -> "BaseMaterials/Root/Wood_02/material/M_Wood_02"
    PieceMaterial.TILES_04 -> "BaseMaterials/Root/Tiles_04/material/M_Tiles_04"
    PieceMaterial.WOOD_12 -> "BaseMaterials/Root/Wood_12/material/M_Wood_12"
    PieceMaterial.TRAVERTINE_09 -> "BaseMaterials/Root/Travertine_09/material/M_Travertine_09"
}

private fun Color4.toColor3(): Color3 = Color3(red, green, blue)

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
 * The 4 PBR options each load one [ShaderGraphMaterial] per [PieceType]
 * from the same bundle path (`ShaderGraphMaterial` doesn't support deep
 * copy per SDK docs, so this is 7 real loads, not 7 copies of one
 * instance), tinted via [COLOR_TINT_PARAMETER_NAME] with that type's
 * [candyColorFor] color so switching materials doesn't erase the 7-color
 * identification the player relies on. If ANY of the 7 per-type loads or
 * tint calls fails, the WHOLE selection falls back to the JELLY map - never
 * a partial result mixing old and new materials across piece types.
 */
internal class PieceMaterialLoader(private val bundle: BaseMaterialsBundle) {
    suspend fun load(selection: PieceMaterial): Map<PieceType, Material> = withContext(Dispatchers.IO) {
        if (selection == PieceMaterial.JELLY) return@withContext createJellyMaterials()

        bundle.withBundle { loadedBundle ->
            if (loadedBundle == null) return@withBundle createJellyMaterials()

            val path = bundlePathFor(selection)
            val results = PieceType.entries.associateWith { type ->
                runCatching {
                    ShaderGraphMaterial.loadFromAssetBundle(loadedBundle, path).apply {
                        setParameter(COLOR_TINT_PARAMETER_NAME, candyColorFor(type).toColor3())
                    }
                }
            }

            val firstFailure = results.values.firstOrNull { it.isFailure }?.exceptionOrNull()
            if (firstFailure != null) {
                Log.w(PIECE_MATERIAL_LOADER_LOG_TAG, "failed to load piece material for $selection, falling back to jelly", firstFailure)
                createJellyMaterials()
            } else {
                results.mapValues { (_, result) -> result.getOrThrow() }
            }
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

- [ ] **Step 3: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. This task doesn't wire `PieceMaterialLoader`
into `GamePage.kt` yet (Task 6 does) - a clean build with the file compiling
and unused is the expected state here (Kotlin doesn't warn on an unused
`internal class`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/PieceMaterialLoader.kt
git commit -m "Add PieceMaterialLoader with per-piece-type color_tint"
```

(If Step 1's probe required editing `BasePlateMaterialLoader.kt`, make sure
that edit was fully reverted before this commit - `git diff` should show
zero changes to that file from this task.)

---

### Task 4: `BoardCubeRenderer` — per-type material swap for PBR selections

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt`

**Interfaces:**
- Consumes: `Map<PieceType, Material>` (Task 3's `PieceMaterialLoader.load()`
  return type).
- Produces: `BoardCubeRenderer.setPieceMaterials(materials: Map<PieceType, Material>?)`.
  `null` means "use the existing JELLY per-cube path" (the sentinel Task 6's
  `GamePage.kt` wiring uses for a JELLY selection); non-null means "this PBR
  set is active, swap cells to it in place."

This is the highest-risk task in this plan: up to 184 (`BOARD_WIDTH *
BOARD_HEIGHT`) locked-cell entities plus 4 falling-piece entities exist, and
which `PieceType` a given slot represents changes continuously during real
play (every lock + spawn, not every frame). Unlike the base plate's single
entity, destroying and recreating up to 184 entities on every such change
would be a real performance risk - this is why the design calls for the
in-place `ModelComponent.materials[0] = material` swap instead (see Global
Constraints for the exact confirmed API).

- [ ] **Step 1: Add the import and the `pieceMaterials` field**

Add to the import block: `import com.pico.spatial.core.ecs.ModelComponent`.

Add near the other private fields (after `private var groundAnchor: Entity? = null`
at `BoardCubeRenderer.kt:219`):

```kotlin
    // null = the existing per-cube UnlitMaterial + setBaseColor() path below
    // (JELLY, the default - completely unchanged by this feature). Non-null =
    // a PBR PieceMaterial set is active; render() swaps each visible cell's
    // bound material to materials.getValue(type) via ModelComponent.materials[0]
    // instead of recoloring the cube's own private UnlitMaterial.
    private var pieceMaterials: Map<PieceType, Material>? = null

    /**
     * Sets which per-[PieceType] material set `render()` should bind cells to.
     * `null` reverts to the original jelly look (each cube's own private
     * `UnlitMaterial`, recolored via `setBaseColor` as this class has always
     * done). Safe to call before [attachTo] - `render()` itself is a no-op
     * until [isAttached].
     */
    fun setPieceMaterials(materials: Map<PieceType, Material>?) {
        pieceMaterials = materials
    }
```

Add `import tech.illusion.spacecube.game.PieceType` to the import block too
(needed for the `Map<PieceType, Material>` type).

- [ ] **Step 2: Add the swap helper**

Add near `render()`:

```kotlin
    /**
     * Binds [entity] to show [type]: either the shared, pre-tinted PBR
     * material for that type (in-place swap - see [pieceMaterials]'s KDoc
     * for why this doesn't destroy/recreate), or - the JELLY path,
     * unchanged from before this feature - recolors [fallbackMaterial]
     * (the entity's own private `UnlitMaterial`) via `setBaseColor`.
     */
    private fun bindCubeMaterial(entity: ModelEntity, fallbackMaterial: UnlitMaterial, type: PieceType) {
        val pbrMaterial = pieceMaterials?.get(type)
        if (pbrMaterial != null) {
            entity.components[ModelComponent::class.java]?.materials?.set(0, pbrMaterial)
        } else {
            fallbackMaterial.setBaseColor(candyJellyColorFor(type))
        }
    }
```

(Uses the plain `PieceType` - Step 1 already added its import to this file.)

- [ ] **Step 3: Route `render()`'s three loops through it**

In `render()` (`BoardCubeRenderer.kt:257-313`), replace each of the three
`cube.material.setBaseColor(candyJellyColorFor(...))` call sites with a call
to `bindCubeMaterial`. The locked-cell loop (currently lines 261-272):

```kotlin
        for (row in 0 until boardHeight) {
            for (col in 0 until boardWidth) {
                val cube = lockedCubes[row * boardWidth + col]
                val lockedType = snapshot.boardCells[row][col]
                if (lockedType == null) {
                    cube.entity.enabled = false
                } else {
                    cube.entity.enabled = true
                    bindCubeMaterial(cube.entity, cube.material, lockedType)
                }
            }
        }
```

The falling-piece loop (currently lines 279-299), only the material line
inside the `else` branch changes:

```kotlin
                cube.live = true
                cube.entity.enabled = true
                bindCubeMaterial(cube.entity, cube.material, snapshot.fallingType)
```

The ghost loop (currently lines 302-312), same change:

```kotlin
            } else {
                cube.entity.enabled = true
                bindCubeMaterial(cube.entity, cube.material, snapshot.fallingType)
                cube.entity.components[TransformComponent::class.java]
                    ?.setPosition(cellPosition(cell.first, cell.second))
            }
```

Everything else in `render()` (position/transform logic, the `entity.enabled`
toggling, the sort-by-cell ordering) is unchanged.

- [ ] **Step 4: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Regression device check — JELLY path is byte-for-byte unaffected**

This task doesn't wire `setPieceMaterials` into `GamePage.kt` yet (Task 6
does), so `pieceMaterials` stays `null` for the whole app right now -
`bindCubeMaterial` always takes the `else` branch, meaning this task's
change should be **completely invisible** on-device. Acquire the device
lock, install, launch, screenshot the start screen (board build completes,
no crash) - confirm it looks identical to before this task. This is a
compile-and-no-crash check, not a new visual capability yet.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt
git commit -m "Add per-piece-type material swap path to BoardCubeRenderer"
```

---

### Task 5: UI — "外观设置" panel, relocated base-plate picker, new piece picker

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/PieceMaterialPicker.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `PieceMaterial` (Task 1), `BasePlateMaterialPicker` (existing,
  unmodified component, just called from a new location).
- Produces: `@Composable fun PieceMaterialPicker(selected: PieceMaterial, onSelect: (PieceMaterial) -> Unit)`.
  A new `appearance_settings` `AttachmentPanel`. A `showAppearanceSettings`
  state var in `GamePage()`. **This task is UI-only** - selecting a piece
  material updates state/persistence but does not yet touch the 3D
  renderer (Task 6 wires that).

- [ ] **Step 1: Consult the `spatial-ui-design-style` skill before writing code**

Invoke `pico-spatial-agentic-tools:spatial-ui-design-style` (via the `Skill`
tool). Read `app/src/main/java/tech/illusion/spacecube/content/BasePlateMaterialPicker.kt`
in full first - it already went through this same skill's review during the
base-plate feature (see its own KDoc explaining the `spatialHoverEffect` +
`pointerInput`/`detectTapGestures` choices) - this task's new picker should
match that file's patterns exactly rather than re-deriving them.

- [ ] **Step 2: Create `PieceMaterialPicker.kt`**

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import tech.illusion.spacecube.game.PieceMaterial
import tech.illusion.spacecube.game.PieceType

private val SWATCH_SIZE = 44.dp
private val SWATCH_CORNER = 10.dp
private val SWATCH_GAP = 10.dp
private val SWATCH_LABEL_GAP = 4.dp
private val SWATCH_RING_WIDTH = 2.dp
private val JELLY_SWATCH_CELL_GAP = 2.dp

// JELLY has no single-image thumbnail (it's the app's own per-piece UnlitMaterial
// look, not a Spatial Editor asset, and unlike glass it's meant to read as
// "still multicolor" rather than one flat tone) - shown as a small 2x2 grid of
// 4 representative candy colors, echoing NextPiecePreview's mini-grid language
// rather than inventing a new visual idiom.
private val JELLY_SWATCH_COLORS = listOf(
    candyPieceComposeColor(PieceType.I),
    candyPieceComposeColor(PieceType.O),
    candyPieceComposeColor(PieceType.T),
    candyPieceComposeColor(PieceType.S),
)

private val PIECE_THUMBNAIL_ASSET_PATHS: Map<PieceMaterial, String> = mapOf(
    PieceMaterial.WOOD_02 to "base_plate_thumbnails/wood_02.png",
    PieceMaterial.TILES_04 to "base_plate_thumbnails/tiles_04.png",
    PieceMaterial.WOOD_12 to "base_plate_thumbnails/wood_12.png",
    PieceMaterial.TRAVERTINE_09 to "base_plate_thumbnails/travertine_09.png",
)

private fun loadPieceThumbnails(context: Context): Map<PieceMaterial, ImageBitmap> =
    PIECE_THUMBNAIL_ASSET_PATHS.mapValues { (_, path) ->
        context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream).asImageBitmap() }
    }

private fun pieceMaterialLabel(material: PieceMaterial): String = when (material) {
    PieceMaterial.JELLY -> "果冻"
    PieceMaterial.WOOD_02 -> "Wood_02"
    PieceMaterial.TILES_04 -> "Tiles_04"
    PieceMaterial.WOOD_12 -> "Wood_12"
    PieceMaterial.TRAVERTINE_09 -> "Travertine_09"
}

/**
 * The "外观设置" panel row for picking the falling/locked pieces' visual
 * material - 果冻 (the existing look) plus the same 4 PBR materials shown
 * by [BasePlateMaterialPicker], reusing their thumbnails. Selecting one
 * only updates [onSelect]'s state and (via the caller) persists it; it does
 * not itself touch any 3D entity - see `BoardCubeRenderer.setPieceMaterials`
 * for where the actual swap happens.
 */
@Composable
internal fun PieceMaterialPicker(
    selected: PieceMaterial,
    onSelect: (PieceMaterial) -> Unit,
) {
    val context = LocalContext.current
    val thumbnails = remember { loadPieceThumbnails(context) }

    Column {
        Text(
            text = "方块材质",
            color = CandyCardInkDim,
            style = PicoTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
            PieceMaterial.entries.forEach { material ->
                PieceMaterialSwatch(
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
private fun PieceMaterialSwatch(
    material: PieceMaterial,
    thumbnail: ImageBitmap?,
    isSelected: Boolean,
    onSelect: (PieceMaterial) -> Unit,
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
                .spatialHoverEffect()
                .pointerInput(material) {
                    detectTapGestures(onTap = { onSelect(material) })
                }
                .then(ringModifier),
        ) {
            if (thumbnail != null) {
                Canvas(modifier = Modifier.size(SWATCH_SIZE)) {
                    drawImage(thumbnail, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(JELLY_SWATCH_CELL_GAP)) {
                    JELLY_SWATCH_COLORS.chunked(2).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(JELLY_SWATCH_CELL_GAP)) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size((SWATCH_SIZE - JELLY_SWATCH_CELL_GAP) / 2)
                                        .background(color),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = pieceMaterialLabel(material),
            color = if (isSelected) CandyCardInk else CandyCardInkDim,
            style = PicoTheme.typography.bodySmall,
        )
    }
}
```

- [ ] **Step 3: Add the panel + entry button + state to `GamePage.kt`**

Add near `selectedBasePlateMaterial` (`GamePage.kt:775-777`):

```kotlin
    val pieceMaterialStore = remember { SharedPreferencesPieceMaterialStore(context) }
    var selectedPieceMaterial by remember { mutableStateOf(pieceMaterialStore.get()) }
    var showAppearanceSettings by remember { mutableStateOf(false) }
```

Add imports: `tech.illusion.spacecube.game.PieceMaterial` and
`tech.illusion.spacecube.game.SharedPreferencesPieceMaterialStore`.

In the `initial` block, add an `attach("appearance_settings", ...)` call
alongside the other overlay attaches (after `attach("exit_confirm_overlay", ...)`
at `GamePage.kt:1131`):

```kotlin
            attach("appearance_settings", Vector3(0f, 0f, MAIN_PANEL_Z_M))
```

In the `start_screen` panel's content (`GamePage.kt:1170-1176`), replace the
inline `BasePlateMaterialPicker(...)` call with a single entry button:

```kotlin
                            Button(
                                onClick = { showAppearanceSettings = true },
                                colors = candyButtonColors(primary = false),
                            ) { Text("外观设置") }
```

Add a new `AttachmentPanel(id = "appearance_settings") { ... }` block,
placed after the `"start_screen"` panel block (i.e. right after the closing
of the block ending at `GamePage.kt:1204`):

```kotlin
            AttachmentPanel(id = "appearance_settings") {
                if (showAppearanceSettings) {
                    CandyCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "外观设置",
                                color = CandyCardInk,
                                style = PicoTheme.typography.titleMedium,
                            )
                            BasePlateMaterialPicker(
                                selected = selectedBasePlateMaterial,
                                onSelect = { material ->
                                    selectedBasePlateMaterial = material
                                    basePlateMaterialStore.set(material)
                                },
                            )
                            PieceMaterialPicker(
                                selected = selectedPieceMaterial,
                                onSelect = { material ->
                                    selectedPieceMaterial = material
                                    pieceMaterialStore.set(material)
                                },
                            )
                            Button(
                                onClick = { showAppearanceSettings = false },
                                colors = candyButtonColors(primary = true),
                            ) { Text("完成") }
                        }
                    }
                }
            }
```

The `onSelect` lambdas above are moved verbatim from the old inline
`BasePlateMaterialPicker` call site in `start_screen` - same state
update + persistence write, just relocated.

**Gating note**: `appearance_settings`'s visibility (`showAppearanceSettings`)
and `start_screen`'s visibility (`!started && !showExitConfirm`) are
independent booleans - both could theoretically be true at once if this
isn't handled. Add `&& !showAppearanceSettings` to `start_screen`'s
existing condition (`GamePage.kt:1135`, currently `if (!started && !showExitConfirm)`)
so the two cards don't render on top of each other:

```kotlin
                if (!started && !showExitConfirm && !showAppearanceSettings) {
```

**Back-button conflict**: the existing `OnBackPressedCallback`
(`GamePage.kt:841-856`) sets `showExitConfirm = true` on any back press,
with no awareness of `showAppearanceSettings` - pressing the physical back
button while the new panel is open would pop the exit-confirm dialog
*on top of* it (two independent `AttachmentPanel`s, both now visible at
once), rather than the expected "back out of the settings panel" behavior.
Fix the callback to check the new panel first:

```kotlin
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showExitConfirm) return
                if (showAppearanceSettings) {
                    showAppearanceSettings = false
                    return
                }
                pausedForExitConfirm = started && snapshot.state == GameState.PLAYING
                if (pausedForExitConfirm) {
                    engine.pause()
                    snapshot = engine.snapshot()
                }
                showExitConfirm = true
            }
        }
```

(Only the new `if (showAppearanceSettings) { ... }` block is added; every
other line is unchanged from the existing callback.)

- [ ] **Step 4: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the SpatialUI design-style verifier**

```bash
bash /Users/zohar/.claude/plugins/cache/pico-xr/pico-spatial-agentic-tools/*/skills/spatial-ui-design-style/scripts/verify-design-style.sh app/src/main/java
```

Expected: 0 errors, 0 warnings.

- [ ] **Step 6: Device verification**

Acquire the device lock (owner e.g. `"SpaceCube-piecematerial-task5"`),
install, launch, wait for the board-build anchor.

Screenshot the start screen: confirm it now shows a "外观设置" button where
the swatch row used to be, and no swatch row inline. Screenshot this as
`/tmp/task5-start-screen.png`.

`adb tap` cannot reach the "外观设置" button (established limitation), so
verify the new panel's *rendering* via the seed-and-relaunch technique is
not applicable here either (there's no persisted "panel open" state - it's
transient UI state, not something `SharedPreferences`-seedable). Instead,
verify by code inspection that `showAppearanceSettings`'s only writers are
the entry button's `onClick` and the panel's own "完成" button's `onClick`
(grep the diff), and confirm no crash from having the new panel attached
but not yet visible:

```bash
adb -s emulator-5554 logcat -b crash -d
```

Expected: empty. This task cannot fully device-verify the panel's own
interactive rendering (opening it) given the tap limitation - that's
covered by Task 6's fuller verification pass, which additionally seeds a
non-default `PieceMaterial` selection and confirms the picker shows the
right swatch selected on next launch (proving the picker's wiring is
correct even though the open/close interaction itself can't be scripted).

Stop the app, release the lock.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/PieceMaterialPicker.kt \
        app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "Add appearance settings panel with piece material picker"
```

---

### Task 6: Final wiring, verification, and documentation

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/AGENTS.md`

**Interfaces:**
- Consumes: `PieceMaterialLoader` (Task 3), `BoardCubeRenderer.setPieceMaterials`
  (Task 4), `selectedPieceMaterial`/`pieceMaterialStore` (Task 5).
- Produces: nothing new - this task connects everything already built and
  finishes the feature.

- [ ] **Step 1: Construct the loader and wire the effect**

Add near `basePlateMaterialLoader` (`GamePage.kt`, after Task 2's edit):

```kotlin
    val pieceMaterialLoader = remember { PieceMaterialLoader(baseMaterialsBundle) }
```

Add a new effect alongside the existing
`LaunchedEffect(selectedBasePlateMaterial) { ... }` (`GamePage.kt:922-925`),
mirroring it exactly including the `isAttached` guard and its reasoning:

```kotlin
    // Swaps the piece materials when the user picks a new one from
    // PieceMaterialPicker. Same isAttached guard as the base-plate effect
    // above, and for the same reason - this fires on first composition too,
    // racing initial's own attachTo() call.
    LaunchedEffect(selectedPieceMaterial) {
        if (!renderer.isAttached) return@LaunchedEffect
        renderer.setPieceMaterials(
            if (selectedPieceMaterial == PieceMaterial.JELLY) null else pieceMaterialLoader.load(selectedPieceMaterial)
        )
    }
```

- [ ] **Step 2: Resolve the initial selection in the `initial` block**

Mirror the base-plate pattern at `GamePage.kt:1047-1069` (capture-before-`attachTo`,
gated re-resolve after `sceneReady = true`). The piece-material set doesn't
feed into `attachTo`'s arguments (unlike the ground material - `attachTo`
doesn't build any locked/falling cube pool differently based on which
`PieceMaterial` is active, since every cell starts `enabled = false`
regardless), so this is simpler: resolve and apply it once, right after
`sceneReady = true`, gated the same way:

```kotlin
            val boardBuildStartMs = System.currentTimeMillis()
            val basePlateMaterialAtAttach = selectedBasePlateMaterial
            renderer.attachTo(anchor, basePlateMaterialLoader.load(basePlateMaterialAtAttach))
            sceneReady = true
            if (selectedBasePlateMaterial != basePlateMaterialAtAttach) {
                renderer.setBasePlateMaterial(basePlateMaterialLoader.load(selectedBasePlateMaterial))
            }
            val initialPieceMaterial = selectedPieceMaterial
            renderer.setPieceMaterials(
                if (initialPieceMaterial == PieceMaterial.JELLY) null else pieceMaterialLoader.load(initialPieceMaterial)
            )
            if (selectedPieceMaterial != initialPieceMaterial) {
                renderer.setPieceMaterials(
                    if (selectedPieceMaterial == PieceMaterial.JELLY) null else pieceMaterialLoader.load(selectedPieceMaterial)
                )
            }
            Log.i(HAND_GESTURE_LOG_TAG, "initial: board build took ${System.currentTimeMillis() - boardBuildStartMs}ms")
```

(This replaces only the `Log.i(HAND_GESTURE_LOG_TAG, "initial: board build took...")`
line's position relative to the base-plate re-resolve block - the base-plate
lines themselves are unchanged from Task 2/existing code, just shown here
for placement context. The new piece-material block goes between the
existing base-plate re-resolve and that log line.)

- [ ] **Step 3: Build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`
Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 4: Device verification — persistence + no-crash across all combinations reachable pre-game**

Acquire the device lock (owner e.g. `"SpaceCube-piecematerial-task6"`).

For each of the 5 `PieceMaterial` values, seed it and relaunch (same
technique as the base-plate feature, targeting the piece-material prefs
file this time):

```bash
adb -s emulator-5554 shell am force-stop tech.illusion.spacecube
adb -s emulator-5554 shell "run-as tech.illusion.spacecube sh -c \
  'cat > /data/data/tech.illusion.spacecube/shared_prefs/spacecube_piece_material.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="selected_material">WOOD_02</string>
</map>
XML
adb -s emulator-5554 logcat -c
pico-cli app launch tech.illusion.spacecube --activity .platform.LaunchActivity --device emulator-5554
```

Substitute each of `JELLY`, `WOOD_02`, `TILES_04`, `WOOD_12`,
`TRAVERTINE_09` in turn. Poll for the process, wait for the board-build log
anchor, then confirm via `adb logcat -d | grep SpaceCubePieceMaterial` that
non-JELLY selections show no `falling back to jelly` warning, and confirm
`adb logcat -b crash -d` is empty for every run. **This proves the
selection resolves and applies without crashing** - it does NOT prove the
pieces look right, because nothing is visible on the start screen for
piece materials (unlike the base plate, which is always on-screen; pieces
only exist once the board has locked/falling cells, i.e. during actual
gameplay, which no tooling here can reach). Don't screenshot expecting to
see tinted pieces at this stage - there's nothing to see yet by design.

Then verify persistence the same way as the base-plate feature: after the
last seeded run above, force-stop and relaunch again *without* re-seeding,
confirm (via the same logcat check - there's no visible UI to screenshot
that would prove this either, so rely on the log evidence) that the same
material's resolution ran again on the fresh launch. Clean up: force-stop,
delete the seeded `spacecube_piece_material.xml`, release the lock.

- [ ] **Step 5: Update `AGENTS.md`**

Add a new dated section (e.g. "Piece material picker (2026-08-26)"),
following this project's existing documentation style (see "Base plate
material picker" for the most directly comparable precedent - same
verified/unverified distinction, same dated-entry convention). Record:

- The feature: 5-option picker for the falling/locked pieces, in the new
  "外观设置" panel alongside the relocated base-plate picker; persisted via
  `SharedPreferencesPieceMaterialStore`; default JELLY.
- The `BaseMaterialsBundle` refactor and why (two loaders sharing one
  `AssetBundle`).
- The `color_tint` parameter name as actually confirmed in Task 3 Step 1
  (not assumed) and the exact API shape used
  (`ShaderGraphMaterial.setParameter(name, Color3)`), for anyone adding a
  future PBR-material feature to this project.
- The `ModelComponent.materials[0] = material` in-place swap as this
  codebase's first real use of that API (previously only confirmed to
  exist, never exercised - see the base-plate feature's final review).
- Verified (build + emulator, this task's device pass): all 5 selections
  resolve and apply with no crash and no fallback warning; persistence
  confirmed via seed-and-relaunch; the JELLY/default path is unaffected by
  this whole feature (Tasks 2, 4, 5's own regression checks).
- **Explicitly unverified — do not claim these work**, matching this
  project's registry convention (add to the "Still unverified" list, not
  just to this new section):
  - How tinted PBR pieces actually look during real gameplay - falling,
    locking, stacking - has never been observed by any tooling in this
    project, because nothing can press "开始游戏" without a human. This is
    the single most important unverified claim this feature ships with.
  - Whether 7 tint colors read as clearly distinct from each other once
    applied over a busy wood-grain or marble texture (the core risk the
    design spec flagged from the start) - needs real gameplay judgment,
    not a code read.
  - Whether repeatedly swapping `ModelComponent.materials[0]` on up to
    ~10-20 visible cells per lock/spawn cycle during fast play causes any
    perceptible hitching - no tooling here can simulate real gameplay
    timing to check this.
  - Point at `docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`
    for the full reasoning behind these gaps.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GamePage.kt \
        app/src/main/java/tech/illusion/spacecube/AGENTS.md
git commit -m "Wire piece material picker end to end; document unverified gameplay appearance"
```
