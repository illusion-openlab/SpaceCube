# Piece material picker — remove per-type color tint

Status: approved by user 2026-08-26, ready for implementation planning.

Amends `docs/superpowers/specs/2026-08-26-piece-material-picker-design.md`
(shipped, `main` commit `2e6d715`). Supersedes that spec's §2 and the
relevant parts of §6; everything else in the original spec (UX, persistence,
`BoardCubeRenderer`'s `ModelComponent.materials[0]` swap mechanism, UI,
non-goals) is unchanged and not repeated here.

## 1. Why

The shipped feature tints each of the 4 PBR materials per piece type via the
Shader Graph's `color_tint` parameter, so all 7 types stay color-distinct
under any material — this was the original spec's whole premise (§2: "the
game becomes unplayable by sight" otherwise). After trying it on the real
headset, the user's verdict: the forced tint on top of a wood/tile/stone
texture looks bad. Direct feedback, not a hypothesis — remove it.

## 2. What changes

When a PBR material is selected, all 7 piece types now share **one**
untinted material instance — the material's natural PBR look, no
`color_tint` call at all. `PieceMaterialLoader.load()` still returns
`Map<PieceType, Material>` (zero change to `BoardCubeRenderer`, which just
looks up by type as before), but for the 4 PBR options every key now maps to
the *same* loaded `Material` object instead of 7 independently-tinted ones.

Confirmed from the existing on-device probe evidence: an untouched
`color_tint` reads back as `Color3(1,1,1)` (white = full pass-through), so
simply omitting the `setParameter` call is sufficient — no explicit "reset
to neutral" step needed.

**JELLY is unchanged.** It's a plain-color `UnlitMaterial`, not a texture
being tinted — not what the feedback was about. Still 7 candy-colored cubes
built exactly as `createCube()` always has.

**Not changing:** `PieceMaterialPicker.kt` (swatch UI, thumbnails,
selection/persistence), the appearance-settings panel, ghost-cube handling
(already bypasses the PBR path entirely, per the final-review fix wave).

## 3. Consequence: this simplifies the loader, not just the visual

The original design's whole point in doing **7 separate**
`loadFromAssetBundle` calls against the same bundle path (rather than one
shared instance) was to let each carry its own tint — independently verified
twice on-device this session (`PieceMaterialLoader.kt`'s KDoc, Task 3 and the
final-review fix wave's Step 0 probe) specifically because that independence
was the load-bearing assumption the whole per-type-tint scheme depended on.

Without per-type tinting, that reason is gone: **one load per PBR selection,
not seven.** `PieceMaterialLoader.load()`'s PBR branch becomes a single
`ShaderGraphMaterial.loadFromAssetBundle(bundle, path)` call, with the
resulting `Material` used as the value for all 7 `PieceType` keys in the
returned map.

This is a real simplification, not incidental:
- One `loadFromAssetBundle` call instead of seven per selection.
- One material handle created per PBR browse instead of seven — the
  previously-documented "no release path" gap (`PieceMaterialLoader.kt`
  KDoc, "KNOWN GAP") shrinks 7x in practice (still not closed — still worth
  documenting — just a smaller leak).
- The 7-independent-instances verification this session did twice (Task 3's
  single-instance probe, then the final-review fix wave's mandatory
  2-instance probe before any other fix could proceed) is no longer
  load-bearing for this feature going forward. **Keep, don't delete, the
  historical record of that verification** in the KDoc — replace the
  "why this matters" framing with a short note that per-type tinting was
  tried, verified working, and removed for visual reasons on 2026-08-26, so
  a future reader doesn't rediscover the same need for verification only to
  re-add a design that was deliberately reverted.

## 4. Testing & verification

- Existing unit tests for `PieceMaterialStore`/`PieceMaterial` are unaffected
  (persistence and enum values don't change).
- No new persistence, no new UI — no new automated test surface.
- Device verification: confirm a PBR selection still loads and renders (no
  crash, no fallback-to-jelly warning) with the simplified single-load path,
  same seed-`SharedPreferences`-and-relaunch technique used throughout this
  feature. The *visible* in-gameplay result (falling/locked pieces showing
  the natural material color) remains outside this project's automated
  tooling, same pre-existing limitation as the original feature — the user's
  own real-headset check that prompted this change is the real verification
  for the visual outcome.

## 5. Non-goals

- No change to the base-plate material picker (`BasePlateMaterialLoader`,
  `BasePlateMaterialPicker`) — untouched, still fully independent.
- No change to which 4 PBR materials are offered, their thumbnails, or the
  picker UI's layout.
- No new per-type distinction mechanism (e.g. different materials per piece
  type) — explicitly decided against; shape + the always-candy-colored
  "next piece" preview panel are the identification channel once a PBR
  material is selected.
