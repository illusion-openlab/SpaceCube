package tech.illusion.spacecube.game

enum class PieceType(val boxSize: Int, val spawnCells: List<Pair<Int, Int>>) {
    I(4, listOf(1 to 0, 1 to 1, 1 to 2, 1 to 3)),
    O(2, listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)),
    T(3, listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2)),
    S(3, listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1)),
    Z(3, listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2)),
    L(3, listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2)),
    J(3, listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2)),
}

/**
 * Rotates cell offsets 90 degrees clockwise inside their boxSize x boxSize bounding box.
 * No wall kicks: callers are responsible for rejecting a rotation that collides.
 */
fun rotateCellsClockwise(cells: List<Pair<Int, Int>>, boxSize: Int): List<Pair<Int, Int>> =
    cells.map { (row, col) -> col to (boxSize - 1 - row) }

/** The exact inverse of [rotateCellsClockwise]: rotating by this then by clockwise (or vice versa) is a no-op. */
fun rotateCellsCounterClockwise(cells: List<Pair<Int, Int>>, boxSize: Int): List<Pair<Int, Int>> =
    cells.map { (row, col) -> (boxSize - 1 - col) to row }
