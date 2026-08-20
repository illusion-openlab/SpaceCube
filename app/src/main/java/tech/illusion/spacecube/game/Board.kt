package tech.illusion.spacecube.game

class Board(val width: Int = 8, val height: Int = 14) {
    private var cells: Array<Array<PieceType?>> = Array(height) { arrayOfNulls(width) }

    fun cellAt(row: Int, col: Int): PieceType? = cells[row][col]

    private fun isInsideBounds(row: Int, col: Int): Boolean =
        row in 0 until height && col in 0 until width

    private fun isCellFree(row: Int, col: Int): Boolean =
        isInsideBounds(row, col) && cells[row][col] == null

    fun canPlace(cells: List<Pair<Int, Int>>): Boolean =
        cells.all { (row, col) -> isCellFree(row, col) }

    fun lock(cells: List<Pair<Int, Int>>, type: PieceType) {
        cells.forEach { (row, col) -> this.cells[row][col] = type }
    }

    fun fullRows(): List<Int> =
        (0 until height).filter { row -> (0 until width).all { col -> cells[row][col] != null } }

    fun clearRows(rows: List<Int>) {
        val rowsToClear = rows.toSet()
        val remainingRows = (0 until height).filter { it !in rowsToClear }.map { cells[it] }
        val clearedRows = List(rows.size) { arrayOfNulls<PieceType>(width) }
        val newRows = clearedRows + remainingRows
        for (row in 0 until height) {
            cells[row] = newRows[row]
        }
    }

    fun clearAll() {
        cells = Array(height) { arrayOfNulls(width) }
    }

    fun rows(): List<List<PieceType?>> = cells.map { it.toList() }
}
