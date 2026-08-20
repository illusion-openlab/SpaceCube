package tech.illusion.spacecube.game

data class FallingPiece(
    val type: PieceType,
    val localCells: List<Pair<Int, Int>>,
    val anchorRow: Int,
    val anchorCol: Int,
) {
    fun absoluteCells(): List<Pair<Int, Int>> =
        localCells.map { (row, col) -> (anchorRow + row) to (anchorCol + col) }

    fun movedBy(deltaRow: Int, deltaCol: Int): FallingPiece =
        copy(anchorRow = anchorRow + deltaRow, anchorCol = anchorCol + deltaCol)

    fun rotatedClockwise(): FallingPiece =
        copy(localCells = rotateCellsClockwise(localCells, type.boxSize))

    fun rotatedCounterClockwise(): FallingPiece =
        copy(localCells = rotateCellsCounterClockwise(localCells, type.boxSize))

    companion object {
        fun spawn(type: PieceType, boardWidth: Int): FallingPiece =
            FallingPiece(
                type = type,
                localCells = type.spawnCells,
                anchorRow = 0,
                anchorCol = (boardWidth - type.boxSize) / 2,
            )
    }
}
