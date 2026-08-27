package tech.illusion.spacecube.game

/**
 * Visual material for the falling/locked tetromino pieces. Purely cosmetic
 * - does not affect gameplay, piece shapes, or scoring.
 *
 * JELLY is the original look (see `BoardCubeRenderer`'s per-cube
 * `UnlitMaterial` + `candyJellyColorFor()`) and the default for a fresh
 * install. The other four reuse the same Spatial Editor Shader Graph
 * materials shipped for `BasePlateMaterial` (see
 * docs/superpowers/specs/2026-08-26-piece-material-picker-design.md), used
 * untinted (as of 2026-08-26, see
 * docs/superpowers/specs/2026-08-26-piece-material-untinted-design.md) - so
 * all 7 piece types render identically under a PBR selection; see
 * `PieceMaterialLoader`'s KDoc for why. Piece-type identification under a
 * PBR material relies on shape and the "next piece" preview panel, not
 * color on the pieces themselves.
 */
enum class PieceMaterial {
    JELLY,
    WOOD_02,
    TILES_04,
    WOOD_12,
    TRAVERTINE_09,
}
