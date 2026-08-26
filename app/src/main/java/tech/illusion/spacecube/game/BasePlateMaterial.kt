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
