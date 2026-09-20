package tech.illusion.spacecube.game

/**
 * 配置窗口 3D 预览里一个方块相对预览组原点的偏移，单位是**格**（乘以渲染器的格距才是米）。
 *
 * X 相对该方块自身的包围盒居中，所以不管哪种形状看起来都水平居中。
 * Y 从**底行**往上数，底行恒为 0 —— 这样 7 种方块都落在同一个高度的底板上，
 * 底板可以固定在一个 Y 值，不必随方块类型上下移动。
 */
data class PreviewCell(val x: Float, val y: Float)

/**
 * 把 [PieceType.spawnCells] 排布成配置窗口预览用的偏移。
 *
 * [PieceType.spawnCells] 是 boxSize×boxSize 盒子里的 (row, col)，这个盒子**不紧贴**形状
 * ——I 的盒子是 4×4 但只有一行有格子——所以先裁到形状自己的包围盒，再水平居中、垂直落底。
 * `spawnCells` 的 row 0 是**顶**行（row 越大越靠下），所以要对 maxRow 做一次翻转才能得到
 * 「距底行多少行」。
 */
fun previewCellOffsets(type: PieceType): List<PreviewCell> {
    val cells = type.spawnCells
    val maxRow = cells.maxOf { it.first }
    val minCol = cells.minOf { it.second }
    val maxCol = cells.maxOf { it.second }
    val centreCol = (minCol + maxCol) / 2f
    return cells.map { (row, col) ->
        PreviewCell(x = col - centreCol, y = (maxRow - row).toFloat())
    }
}
