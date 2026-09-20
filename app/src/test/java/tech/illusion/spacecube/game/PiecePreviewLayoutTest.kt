package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiecePreviewLayoutTest {
    @Test
    fun `every piece yields four cells whose bottom row sits at zero`() {
        for (type in PieceType.values()) {
            val cells = previewCellOffsets(type)
            assertEquals("cell count for $type", 4, cells.size)
            assertEquals("bottom row for $type", 0f, cells.minOf { it.y }, 1e-4f)
        }
    }

    @Test
    fun `every piece is horizontally centred on its own bounding box`() {
        for (type in PieceType.values()) {
            val cells = previewCellOffsets(type)
            // 居中等价于「最左 + 最右 == 0」，对奇数宽和偶数宽都成立。
            assertEquals(
                "horizontal centre for $type",
                0f,
                cells.minOf { it.x } + cells.maxOf { it.x },
                1e-4f,
            )
        }
    }

    @Test
    fun `the I piece is a flat four-wide bar`() {
        val cells = previewCellOffsets(PieceType.I)
        assertEquals(listOf(-1.5f, -0.5f, 0.5f, 1.5f), cells.map { it.x }.sorted())
        assertTrue("I piece must be one row tall", cells.all { it.y == 0f })
    }

    @Test
    fun `the O piece is a two by two square`() {
        val cells = previewCellOffsets(PieceType.O)
        assertEquals(setOf(-0.5f, 0.5f), cells.map { it.x }.toSet())
        assertEquals(setOf(0f, 1f), cells.map { it.y }.toSet())
    }

    @Test
    fun `the T piece has its stem on the top row`() {
        val cells = previewCellOffsets(PieceType.T)
        // spawnCells 的 row 0 是顶行，翻转后应该落在 y = 1 这一行，且只有一个。
        assertEquals(1, cells.count { it.y == 1f })
        assertEquals(3, cells.count { it.y == 0f })
    }
}
