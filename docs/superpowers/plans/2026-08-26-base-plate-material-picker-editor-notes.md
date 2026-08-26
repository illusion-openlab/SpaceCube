# Spatial Editor asset pipeline notes — base-plate materials

Written by Task 6 of the base-plate-material-picker plan (2026-08-26). Every
value below was **confirmed on-device**, not inferred — see "Verification" at
the bottom.

## The answers Task 7 needs

- **AssetBundle asset path used at runtime:** `asset://base_materials.bundle`
  (unchanged from the placeholder — `BASE_MATERIALS_BUNDLE_PATH` in
  `BasePlateMaterialLoader.kt` needs no edit).
- **Confirmed-working `ShaderGraphMaterial.loadFromAssetBundle` paths:**

  | `BasePlateMaterial` | path |
  | --- | --- |
  | `WOOD_02` | `BaseMaterials/Root/Wood_02/material/M_Wood_02` |
  | `TILES_04` | `BaseMaterials/Root/Tiles_04/material/M_Tiles_04` |
  | `WOOD_12` | `BaseMaterials/Root/Wood_12/material/M_Wood_12` |
  | `TRAVERTINE_09` | `BaseMaterials/Root/Travertine_09/material/M_Travertine_09` |

> The plan's guessed paths (`BaseMaterials/Root/MyMaterials/Wood_02`) were
> wrong in two independent ways: there is **no `MyMaterials` group** (that is
> just the group name used in the SDK doc's screenshot, not a convention the
> exporter enforces), and the material prim is named `M_<Name>` and lives
> under a `material` **Scope**, not directly under the group.
>
> The path shape is `<SceneName>/<composed USD prim path>`. `Wood_02` is the
> prim in the scene that references `0/Wood_02.usdz`; that package's own
> `defaultPrim = "Root"` composes its children (`Scope "material"` →
> `M_Wood_02`) underneath it. So the path is derivable from the source `.usda`
> — but read it out of the bundle manifest (below) rather than deriving it.

## How to read the real paths out of any bundle (do this, don't guess)

A `.bundle` is a plain **zip** containing an `AssetInfo.json` manifest whose
`materialinstance` entries carry the exact runtime path in their `path` field:

```bash
unzip -p app/src/main/assets/base_materials.bundle AssetInfo.json \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{
      for (const e of JSON.parse(s).assetInfo.__elements)
        if (e.type!=="texture") console.log(e.type, "|", e.name, "|", e.path);
    })'
```

This is the single most useful trick in this document: it turns "guess the
path, rebuild, reinstall, wait 90 s, read logcat" into a one-second local
check. Audio (`loadAudioResource`) and scene (`loadModel`) paths show up in
the same listing.

## Spatial Editor workflow actually used

**Important: this is NOT the workflow the plan assumed, and NOT the workflow
the `spatial-editor` skill describes.** See "What didn't work" below.

Source material: `~/Downloads/Base.usdz` — itself a Spatial Editor v6.0.1
scene export. Its root `Base.usda` is an Xform `Root` with four prims each
referencing a nested, standalone material-library `.usdz`
(`0/Wood_02.usdz`, `0/Tiles_04.usdz`, `0/Wood_12.usdz`, `0/Travertine_09.usdz`).
Each nested package is `Material`-only (no mesh): `/Root/material/M_<Name>`,
a Shader Graph material with base-color / normal / roughness PNG textures.

Because that file already *is* an editor-authored scene containing all four
materials, no import or scene authoring was needed — only packaging. The whole
pipeline is this script (re-runnable from scratch):

```bash
WORK=/tmp/basematerials            # anywhere outside the repo
PROJ="$WORK/BaseMaterialsProj"
OUT="$WORK/out"
EXE="$HOME/Library/PICO/sdk/6.0/editor/Spatial Editor.app/Contents/MacOS/Spatial Editor"

# 1. Unpack the user's export.
rm -rf "$WORK"; mkdir -p "$WORK/mats" "$OUT"
unzip -o -q ~/Downloads/Base.usdz -d "$WORK/mats"

# 2. Scaffold an editor project. Layout + file contents are exactly what
#    `pico-cli editor start --create-project` writes (taken from pico-cli
#    0.4.4's own scaffolding code); only the scene name differs.
mkdir -p "$PROJ"/{Sources/Scenes,Sources/Assets,.Component,.ProjectData/UserSettings}
cp "$WORK/mats/Base.usda" "$PROJ/Sources/Scenes/BaseMaterials.usda"
mkdir -p "$PROJ/Sources/Scenes/0" && cp "$WORK/mats/0/"*.usdz "$PROJ/Sources/Scenes/0/"
cat > "$PROJ/BaseMaterials.spatialproject" <<'JSON'
{
  "projectName": "BaseMaterials",
  "projectPath": ".",
  "scene": "Sources/Scenes/BaseMaterials.usda",
  "sceneCount": 1,
  "version": 1
}
JSON
echo 'sceneFile = Sources/Scenes/BaseMaterials.usda' > "$PROJ/ModelView"
echo '{ "components": [] }' > "$PROJ/.Component/component.json"
echo '{ "recentSceneFiles": ["Sources/Scenes/BaseMaterials.usda"], "lastOpenedScene": "Sources/Scenes/BaseMaterials.usda" }' \
  > "$PROJ/.ProjectData/UserSettings/history.json"

# 3. Build the AssetBundle — headless, no GUI, no MCP.
"$EXE" --project="$PROJ/BaseMaterials.spatialproject" --ide_noGraphics \
       --ide_buildOutputDir="$OUT" --ide_buildResultName="base_materials" \
       --ide_buildSpecialScenes="" --ide_resultCodeFile="$OUT/errcode.json" \
       --ide_buildAdditivePath=""

cat "$OUT/errcode.json"          # {"data":{"code":"0"},"type":1}  == success
cp "$OUT/base_materials.bundle" app/src/main/assets/base_materials.bundle
```

Key facts about that build invocation:

- The `--ide_*` flags are **not documented anywhere**. They were found in
  `Spatial Editor.app/Contents/MacOS/ide_build.sh`, a shell script the editor
  ships that the IDE integration itself uses to trigger builds. Read that file
  if these ever change; the full flag list also appears in the editor binary
  (`strings "$EXE" | grep -o 'ide_[a-zA-Z]*'`): `ide_buildAdditivePath`,
  `ide_buildOutputDir`, `ide_buildResultName`, `ide_buildSpecialScenes`,
  `ide_coverage`, `ide_debug`, `ide_down`, `ide_noGraphics`, `ide_pid`,
  `ide_preview`, `ide_qml`, `ide_resultCodeFile`.
- Success/failure is reported **only** via `--ide_resultCodeFile`
  (`{"data":{"code":"0"},...}` = OK). The process exits 0 either way and spews
  hundreds of harmless `Rendering_Err ... entity is not alive` lines in
  headless mode — those are **not** errors, ignore them. The one line worth
  grepping is `EditorBuildManager ... scene_export_bundle`.
- `--ide_buildSpecialScenes=""` means "all scenes". `ide_build.sh` maps the
  literal string `none` to empty, so either works.
- Build takes ~15 s for these four materials; the bundle is 25 MB.

### Adding a fifth material later

1. Get a material-library `.usdz` (a `Material`-only export with
   `/Root/material/M_<Name>`).
2. Drop it in `Sources/Scenes/0/` and add a prim to
   `Sources/Scenes/BaseMaterials.usda`:
   `def "<Name>" ( prepend references = @0/<Name>.usdz@ ) { }`
   — or, preferably, do it in the Spatial Editor GUI if the MCP/GUI route is
   working again by then, so the `.usda` stays editor-authored.
3. Re-run step 3 above, copy the bundle in, read the new path with the
   `AssetInfo.json` one-liner, add the enum case + path.

## What didn't work as the plan assumed

1. **The `spatial-editor` skill does not exist in the installed plugin.** The
   installed `pico-spatial-agentic-tools` is **v0.3.0**, whose skill set has no
   `spatial-editor` entry and whose `.mcp.json` declares only
   `pico-spatial-knowledge`. The `spatial-editor` skill / `pico-spatial-editor`
   MCP server / `ensure_editor_ready` / `pack_editor_bundle` tools described in
   the user-level `PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` belong to a **newer**
   plugin version that is not installed here. Installing it would also need a
   Claude Code restart before the skill/MCP became visible.
2. **The Spatial Editor build ships no MCP backend at all**, so even the
   CLI-level editor automation is unusable:
   - `pico-cli editor doctor` reports `editor.capabilities` and
     `editor.mcp-launcher` as **errors**: missing
     `Contents/Resources/plugins/mcp/launcher/run_mcp.sh`, missing MCP backend
     entry, missing bundled MCP Python runtime.
   - The documented fix (`pico-cli editor install -y`) was run twice. Both times
     it downloaded and verified the full 753 MB
     `spatial_editor_20260805_v6.0.0_mac.zip` from `beta_cn`, then failed with
     the *same* error — the shipped `.app` simply has no `Contents/Resources/plugins`
     directory. `CFBundleVersion` is 6.0.1 while the primer package metadata
     claims 6.0.0 (doctor flags this version conflict too).
   - Consequently `pico-cli editor bootstrap`, `pico-cli editor start`, and
     `pico-cli editor pack` all bail out. `editor pack` is a thin wrapper that
     drives `packEditorBundle` over the editor's MCP session and polls a pack
     task — with no MCP backend there is nothing to talk to, and no editor
     instance registry file is ever written to
     `~/Library/Application Support/Spatial Editor/mcp/instances/`.
   - **The GUI editor itself launches fine** (`editorLaunchable: true`), so a
     human can still do all of this interactively. Only headless/agent
     automation is blocked. If a future editor build restores the MCP plugin,
     prefer `pico-cli editor pack --project <proj> --target-app-root <repo>
     --bundle-name base_materials` over the raw `--ide_*` invocation — it writes
     the bundle *and* a `base_materials.scenes.json` sidecar, and it enforces
     that Spatial app projects write to `app/src/main/assets/`.
3. **No texture-resolution / size controls were found.** The plan wondered about
   import settings for these. There is no such knob on the headless build path,
   and the output is not obviously configurable: every texture in the bundle
   came out at a uniform ~2.50 MB (`1/3/4`, `9/10/11`, `16/17/18`, `23/24/25`
   `.texture.data` entries), i.e. the exporter transcodes all of them to one
   fixed compressed format/size regardless of source PNG dimensions. Total
   bundle: 25 MB for 4 materials → budget roughly **6 MB per PBR material**.
4. **`adb shell input tap` still cannot drive this app's spatial UI** —
   confirming `AGENTS.md` debugging note #5, and contradicting the assumption
   that flat `AttachmentPanel` buttons are tappable on this emulator. A tap at
   the on-screen centre of the `Wood_02` swatch (verified against a screenshot,
   and the display is 2160×2158 so screenshot pixels map 1:1 to touch
   coordinates) produced no selection change and no log line. The panel is
   composited by the spatial runtime; the app's Android touch surface does not
   correspond to where the panel appears in the rendered VR view.
   **Use this instead** — seed the persisted selection and relaunch:
   ```bash
   adb -s emulator-5554 shell "run-as tech.illusion.spacecube sh -c \
     'cat > /data/data/tech.illusion.spacecube/shared_prefs/spacecube_base_plate_material.xml'" <<'XML'
   <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
   <map>
       <string name="selected_material">WOOD_12</string>
   </map>
   XML
   ```
   (force-stop the app first, or it will overwrite the file on exit). On launch
   `GamePage`'s `initial` block resolves the persisted value twice — once as
   `attachTo`'s argument, once via `setBasePlateMaterial` right after — so this
   exercises the real swap path, `Entity.destroy()` included.

## Verification (2026-08-26, emulator-5554)

For each of the four materials: force-stop → seed pref → `logcat -c` → launch →
wait for the `initial: board build took` log anchor → screenshot + logcat grep.

| material | log | screenshot |
| --- | --- | --- |
| `WOOD_02` | 2 × `loaded WOOD_02 from BaseMaterials/Root/Wood_02/material/M_Wood_02` | light wood parquet base plate |
| `TILES_04` | 2 × `loaded TILES_04 from BaseMaterials/Root/Tiles_04/material/M_Tiles_04` | white terrazzo speckle |
| `WOOD_12` | 2 × `loaded WOOD_12 from BaseMaterials/Root/Wood_12/material/M_Wood_12` | dark walnut grain |
| `TRAVERTINE_09` | 2 × `loaded TRAVERTINE_09 from .../Travertine_09/material/M_Travertine_09` | blue/cream marble banding |

Screenshots: `.spatialsdk/task6-mat/1{0,1,2,3}-*.png` (untracked).
Zero `FATAL EXCEPTION`, zero `falling back to glass` warnings across all four
runs. Two loads per run is expected, not a bug — see the `initial` block above.

**`BoardCubeRenderer.setBasePlateMaterial` ran for real for the first time here
and did not crash.** Task 5's review flagged that its `Entity.destroy()` call is
`@MainThread`-enforced and had never executed on a device; it executed on every
one of these runs (the second load) with no main-thread violation. The only
`*MainThread*` line in logcat is an unrelated SDK message
(`JobSystem::initInMainThread() can only call one time!`) present on every
launch including glass.

Not yet verified: **swapping between two materials while the app is running**.
Every run above swapped exactly once, from the freshly built board. Tapping a
swatch mid-session goes through `LaunchedEffect(selectedBasePlateMaterial)` →
the same `setBasePlateMaterial`, so the code path is identical, but the
released-resource semantics of a *second* swap (destroying an entity that holds
a bundle-loaded `ShaderGraphMaterial`, then loading another) were not exercised.
That needs the user's own hands-on test on the physical headset.

## Regenerating the bundle

The editor project is **not** committed (it would add ~50 MB of `.usdz` payload
that the 25 MB bundle already contains). It is fully reproducible from
`~/Downloads/Base.usdz` with the script above. If that file is ever lost the
bundle cannot be rebuilt — consider archiving it somewhere durable.

The script above was re-run verbatim from an empty directory after this
document was written, and produced a byte-comparable bundle (25,355,470 vs
25,355,410 — the few bytes of drift are absolute build paths and timestamps
embedded in `AssetInfo.json`) with the exact same four material paths. It is
copy-pasteable as-is.
