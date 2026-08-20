package tech.illusion.spacecube.game

/**
 * Which input scheme drives the falling piece.
 *
 * Originally existed so the two could be A/B compared on-device without a rebuild (user
 * request 2026-08-07: "我现在想做个V2版本的操作控制系统比较"). That comparison is over:
 * as of 2026-08-12 ("把V1操作隐藏，默认使用V2操作") [V2_SYSTEM_GESTURE] is the fixed default
 * and the start-screen picker is gone, so switching now means editing the initialiser in
 * `GamePage`. Exactly one is ever active - both driving the engine at once would double
 * every move.
 *
 * [label] is consequently unused by any current caller. It is kept deliberately, so that
 * restoring a picker stays a UI-only change.
 *
 * Only *piece* control differs. Scene manipulation (positioning/rotating/scaling
 * the board while not playing) always uses hand tracking regardless.
 */
enum class ControlScheme(val label: String) {
    /** Polls HandTrackingProvider and derives pinch/drag/double-pinch by hand. */
    V1_HAND_TRACKING("V1 手势"),

    /** The SDK's own spatial gesture detectors on the glass plane behind the well. */
    V2_SYSTEM_GESTURE("V2 系统手势"),
}
