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
