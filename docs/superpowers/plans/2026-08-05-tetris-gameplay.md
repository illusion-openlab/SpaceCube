# SpaceCube Tetris Gameplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full MVP gameplay described in [`docs/superpowers/specs/2026-08-05-spacecube-design.md`](../specs/2026-08-05-spacecube-design.md) inside the existing `GameWindow` (Volumetric) window, replacing the current placeholder content in `GamePage.kt`.

**Architecture:** A pure-Kotlin, framework-free game-logic package (`tech.illusion.spacecube.game`) implements the grid, pieces, scoring and the `GameEngine` state machine — fully unit-testable with plain JUnit, no Android/emulator required. A thin rendering/input layer in `tech.illusion.spacecube.content` drives that engine from a Compose game loop, renders the board as a pool of reusable ECS `ModelEntity` cubes inside the existing `SpatialView`, and exposes score/next-piece/pause UI through `AttachmentPanel`s, following the SpatialUI-only rule already established in this project.

**Tech Stack:** Kotlin, Jetpack Compose (SpatialUI only — no Material), PICO Spatial SDK 6.0.0 ECS (`ModelEntity`, `MeshResource.createBox`, `UnlitMaterial`), JUnit4 for unit tests, `androidx.test:core` + AndroidJUnit4 for the one instrumented test.

## Global Constraints

- Grid is **8 columns × 14 rows** (`Board(width = 8, height = 14)`), single depth layer — no independent Z placement axis (per spec §3).
- 7 piece types (I/O/T/S/Z/L/J), 7-bag randomizer, rotation is **Z-axis / in-plane only**, no wall kicks (per spec §3, §4).
- Movement: horizontal hand-drag → left/right; downward hand-drag → accelerated fall (soft-drop). Rotation is a **separate control** from the drag pad, never a tap gesture layered on the same surface (per spec §4, confirmed in brainstorming).
- Difficulty presets SLOW/NORMAL/FAST set the *base* fall interval; fall speed also auto-increases every 10 lines cleared (per spec §5).
- Candy color palette (exact hex, from the approved visual concept, spec §7.1): I `#52D1E8`, O `#FFD24C`, T `#FF6FA0`, S `#57E0A0`, Z `#FF5D5D`, L `#FFA23C`, J `#8B93F8`.
- All 2D UI uses SpatialUI (`com.pico.spatial.ui.design.*`) wrapped in `PicoTheme` — **no Material/Material3**, per `AGENTS.md`. Run `spatial-ui-design-style`'s `verify-design-style.sh` on every task that touches Compose UI.
- SDK is pinned to `spatialBom = "6.0.0"` (just migrated). All Spatial SDK API calls in this plan were verified against the `agent-vault` 6.0 API reference, not guessed.
- Build with `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew <task>` (JDK 25 is the system default and is incompatible with this Gradle/AGP).
- **Known scope reduction:** background music is descoped from this plan — it needs an actual audio asset file, which isn't available. Only SFX (via `android.media.ToneGenerator`, no asset needed) is implemented. Flag this to the user; do not fake a toggle that does nothing.

---

## Task 1: PieceType and box-rotation math

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/PieceType.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/PieceTypeTest.kt`

**Interfaces:**
- Produces: `enum class PieceType(val boxSize: Int, val spawnCells: List<Pair<Int, Int>>)` with values `I, O, T, S, Z, L, J`; top-level `fun rotateCellsClockwise(cells: List<Pair<Int, Int>>, boxSize: Int): List<Pair<Int, Int>>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PieceTypeTest {
    @Test
    fun `rotating the O piece four times returns to the original cell set`() {
        var cells = PieceType.O.spawnCells
        repeat(4) { cells = rotateCellsClockwise(cells, PieceType.O.boxSize) }
        assertEquals(PieceType.O.spawnCells.toSet(), cells.toSet())
    }

    @Test
    fun `rotating the I piece once turns it vertical`() {
        val rotated = rotateCellsClockwise(PieceType.I.spawnCells, PieceType.I.boxSize)
        assertEquals(setOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), rotated.toSet())
    }

    @Test
    fun `every piece returns to its original cell set after four rotations`() {
        for (type in PieceType.values()) {
            var cells = type.spawnCells
            repeat(4) { cells = rotateCellsClockwise(cells, type.boxSize) }
            assertEquals("failed for $type", type.spawnCells.toSet(), cells.toSet())
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.PieceTypeTest"`
Expected: FAIL to compile — `PieceType` and `rotateCellsClockwise` are unresolved references.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.PieceTypeTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/PieceType.kt app/src/test/java/tech/illusion/spacecube/game/PieceTypeTest.kt
git commit -m "feat(game): add PieceType shapes and box rotation math"
```

---

## Task 2: 7-bag piece randomizer

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/PieceBag.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/PieceBagTest.kt`

**Interfaces:**
- Consumes: `PieceType` (Task 1).
- Produces: `class PieceBag(random: kotlin.random.Random = kotlin.random.Random)` with `fun next(): PieceType`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package tech.illusion.spacecube.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PieceBagTest {
    @Test
    fun `each bag of seven draws contains every piece type exactly once`() {
        val bag = PieceBag(Random(42))
        val drawn = List(7) { bag.next() }
        assertEquals(PieceType.values().toSet(), drawn.toSet())
        assertEquals(7, drawn.size)
    }

    @Test
    fun `drawing fourteen pieces produces two complete bags`() {
        val bag = PieceBag(Random(7))
        val drawn = List(14) { bag.next() }
        assertEquals(PieceType.values().toSet(), drawn.subList(0, 7).toSet())
        assertEquals(PieceType.values().toSet(), drawn.subList(7, 14).toSet())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.PieceBagTest"`
Expected: FAIL to compile — `PieceBag` is an unresolved reference.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package tech.illusion.spacecube.game

class PieceBag(private val random: kotlin.random.Random = kotlin.random.Random) {
    private var bag: MutableList<PieceType> = mutableListOf()

    fun next(): PieceType {
        if (bag.isEmpty()) {
            bag = PieceType.values().toMutableList()
            bag.shuffle(random)
        }
        return bag.removeAt(bag.size - 1)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.PieceBagTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/PieceBag.kt app/src/test/java/tech/illusion/spacecube/game/PieceBagTest.kt
git commit -m "feat(game): add 7-bag piece randomizer"
```

---

## Task 3: Board grid state

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/Board.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/BoardTest.kt`

**Interfaces:**
- Consumes: `PieceType` (Task 1).
- Produces: `class Board(width: Int = 8, height: Int = 14)` with `val width`, `val height`, `fun cellAt(row: Int, col: Int): PieceType?`, `fun canPlace(cells: List<Pair<Int, Int>>): Boolean`, `fun lock(cells: List<Pair<Int, Int>>, type: PieceType)`, `fun fullRows(): List<Int>`, `fun clearRows(rows: List<Int>)`, `fun clearAll()`, `fun rows(): List<List<PieceType?>>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    @Test
    fun `a newly created board has no full rows`() {
        val board = Board(width = 4, height = 4)
        assertEquals(emptyList<Int>(), board.fullRows())
    }

    @Test
    fun `a row filled across its full width is detected as full`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(3 to 0, 3 to 1, 3 to 2, 3 to 3), PieceType.O)
        assertEquals(listOf(3), board.fullRows())
    }

    @Test
    fun `clearing a row shifts every row above it down by one`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(2 to 0), PieceType.T)
        board.lock(listOf(3 to 0, 3 to 1, 3 to 2, 3 to 3), PieceType.O)
        board.clearRows(listOf(3))
        assertEquals(PieceType.T, board.cellAt(3, 0))
        assertEquals(null, board.cellAt(2, 0))
    }

    @Test
    fun `canPlace rejects cells outside bounds or already occupied`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(0 to 0), PieceType.I)
        assertFalse(board.canPlace(listOf(0 to 0)))
        assertFalse(board.canPlace(listOf(0 to 4)))
        assertFalse(board.canPlace(listOf(-1 to 0)))
        assertTrue(board.canPlace(listOf(0 to 1)))
    }

    @Test
    fun `clearAll empties every cell`() {
        val board = Board(width = 4, height = 4)
        board.lock(listOf(0 to 0, 1 to 1), PieceType.S)
        board.clearAll()
        assertEquals(emptyList<Int>(), board.fullRows())
        assertEquals(null, board.cellAt(0, 0))
    }

    @Test
    fun `rows returns an immutable snapshot with the board dimensions`() {
        val board = Board(width = 4, height = 3)
        board.lock(listOf(1 to 2), PieceType.Z)
        val snapshot = board.rows()
        assertEquals(3, snapshot.size)
        assertEquals(4, snapshot[1].size)
        assertEquals(PieceType.Z, snapshot[1][2])
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.BoardTest"`
Expected: FAIL to compile — `Board` is an unresolved reference.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.BoardTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/Board.kt app/src/test/java/tech/illusion/spacecube/game/BoardTest.kt
git commit -m "feat(game): add Board grid state with lock/clear/full-row detection"
```

---

## Task 4: FallingPiece (position + rotation state)

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/FallingPiece.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/FallingPieceTest.kt`

**Interfaces:**
- Consumes: `PieceType`, `rotateCellsClockwise` (Task 1).
- Produces: `data class FallingPiece(val type: PieceType, val localCells: List<Pair<Int, Int>>, val anchorRow: Int, val anchorCol: Int)` with `fun absoluteCells(): List<Pair<Int, Int>>`, `fun movedBy(deltaRow: Int, deltaCol: Int): FallingPiece`, `fun rotatedClockwise(): FallingPiece`, and `companion object { fun spawn(type: PieceType, boardWidth: Int): FallingPiece }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class FallingPieceTest {
    @Test
    fun `spawn centers the piece horizontally on the board`() {
        val piece = FallingPiece.spawn(PieceType.T, boardWidth = 8)
        assertEquals(2, piece.anchorCol)
        assertEquals(0, piece.anchorRow)
    }

    @Test
    fun `movedBy shifts every absolute cell by the same delta`() {
        val piece = FallingPiece.spawn(PieceType.O, boardWidth = 8).movedBy(deltaRow = 1, deltaCol = 1)
        assertEquals(setOf(1 to 4, 1 to 5, 2 to 4, 2 to 5), piece.absoluteCells().toSet())
    }

    @Test
    fun `rotatedClockwise applies the box rotation to local cells only`() {
        val piece = FallingPiece.spawn(PieceType.I, boardWidth = 8).rotatedClockwise()
        assertEquals(setOf(0 to 2, 1 to 2, 2 to 2, 3 to 2), piece.localCells.toSet())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.FallingPieceTest"`
Expected: FAIL to compile — `FallingPiece` is an unresolved reference.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.FallingPieceTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/FallingPiece.kt app/src/test/java/tech/illusion/spacecube/game/FallingPieceTest.kt
git commit -m "feat(game): add FallingPiece position and rotation"
```

---

## Task 5: Scoring, level, and fall-speed curve

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/Scoring.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/ScoringTest.kt`

**Interfaces:**
- Produces: `enum class Difficulty(val baseIntervalMs: Long)` with values `SLOW(1000L), NORMAL(700L), FAST(450L)`; `object Scoring` with `fun pointsForClearedLines(lineCount: Int, level: Int): Int`, `fun levelForLinesCleared(totalLinesCleared: Int): Int`, `fun fallIntervalMs(difficulty: Difficulty, level: Int): Long`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test
    fun `clearing more lines at once scores more points per line`() {
        assertEquals(100, Scoring.pointsForClearedLines(1, level = 1))
        assertEquals(300, Scoring.pointsForClearedLines(2, level = 1))
        assertEquals(500, Scoring.pointsForClearedLines(3, level = 1))
        assertEquals(800, Scoring.pointsForClearedLines(4, level = 1))
    }

    @Test
    fun `points scale with the current level`() {
        assertEquals(200, Scoring.pointsForClearedLines(1, level = 2))
    }

    @Test
    fun `level increases every ten cleared lines`() {
        assertEquals(1, Scoring.levelForLinesCleared(0))
        assertEquals(1, Scoring.levelForLinesCleared(9))
        assertEquals(2, Scoring.levelForLinesCleared(10))
        assertEquals(3, Scoring.levelForLinesCleared(25))
    }

    @Test
    fun `fall interval speeds up with level but never drops below the floor`() {
        assertEquals(700L, Scoring.fallIntervalMs(Difficulty.NORMAL, level = 1))
        assertEquals(640L, Scoring.fallIntervalMs(Difficulty.NORMAL, level = 2))
        assertEquals(120L, Scoring.fallIntervalMs(Difficulty.NORMAL, level = 20))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.ScoringTest"`
Expected: FAIL to compile — `Scoring` and `Difficulty` are unresolved references.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package tech.illusion.spacecube.game

enum class Difficulty(val baseIntervalMs: Long) {
    SLOW(1000L),
    NORMAL(700L),
    FAST(450L),
}

object Scoring {
    private const val MIN_INTERVAL_MS = 120L
    private const val INTERVAL_STEP_MS = 60L
    private const val LINES_PER_LEVEL = 10
    private val POINTS_PER_LINE_COUNT = mapOf(1 to 100, 2 to 300, 3 to 500, 4 to 800)

    fun pointsForClearedLines(lineCount: Int, level: Int): Int {
        val basePoints = POINTS_PER_LINE_COUNT[lineCount] ?: 0
        return basePoints * level
    }

    fun levelForLinesCleared(totalLinesCleared: Int): Int =
        1 + totalLinesCleared / LINES_PER_LEVEL

    fun fallIntervalMs(difficulty: Difficulty, level: Int): Long =
        maxOf(MIN_INTERVAL_MS, difficulty.baseIntervalMs - (level - 1) * INTERVAL_STEP_MS)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.ScoringTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/Scoring.kt app/src/test/java/tech/illusion/spacecube/game/ScoringTest.kt
git commit -m "feat(game): add scoring, level, and fall-speed curve"
```

---

## Task 6: GameEngine state machine

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/GameEngine.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/GameEngineTest.kt`

**Interfaces:**
- Consumes: `Board`, `PieceBag`, `PieceType`, `FallingPiece`, `Difficulty`, `Scoring` (Tasks 1-5).
- Produces:
  - `enum class GameState { PLAYING, PAUSED, GAME_OVER }`
  - `enum class GameEvent { PIECE_LOCKED, LINES_CLEARED, GAME_OVER }`
  - `data class GameSnapshot(val boardCells: List<List<PieceType?>>, val fallingCells: Set<Pair<Int, Int>>, val fallingType: PieceType, val ghostCells: Set<Pair<Int, Int>>, val nextType: PieceType, val score: Int, val level: Int, val state: GameState)`
  - `class GameEngine(board: Board = Board(), bag: PieceBag = PieceBag())` with `var fallBoostActive: Boolean`, `fun start(difficulty: Difficulty)`, `fun moveLeft(): Boolean`, `fun moveRight(): Boolean`, `fun rotate(): Boolean`, `fun pause()`, `fun resume()`, `fun tick()`, `fun currentFallIntervalMs(): Long`, `fun snapshot(): GameSnapshot`, `fun drainEvents(): List<GameEvent>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package tech.illusion.spacecube.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun `moveLeft and moveRight shift the falling piece within bounds`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val startCol = engine.snapshot().fallingCells.minOf { it.second }
        engine.moveRight()
        assertEquals(startCol + 1, engine.snapshot().fallingCells.minOf { it.second })
        engine.moveLeft()
        assertEquals(startCol, engine.snapshot().fallingCells.minOf { it.second })
    }

    @Test
    fun `moveLeft repeatedly stops at the left wall instead of going out of bounds`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        repeat(10) { engine.moveLeft() }
        assertEquals(0, engine.snapshot().fallingCells.minOf { it.second })
    }

    @Test
    fun `tick moves the falling piece down by one row until it lands`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val startRow = engine.snapshot().fallingCells.minOf { it.first }
        engine.tick()
        assertEquals(startRow + 1, engine.snapshot().fallingCells.minOf { it.first })
    }

    @Test
    fun `piece locks onto the floor and a new falling piece spawns at the top`() {
        // height = 6: every piece's spawn orientation occupies local rows 0-1, so the
        // lock triggers on exactly the (height - 1)th tick (5th here) — one tick more
        // would already move the freshly-spawned replacement piece down by one row.
        val engine = GameEngine(board = Board(width = 8, height = 6), bag = PieceBag(Random(1)))
        repeat(5) { engine.tick() }
        val snapshot = engine.snapshot()
        assertTrue(snapshot.boardCells.last().any { it != null })
        assertEquals(0, snapshot.fallingCells.minOf { it.first })
    }

    @Test
    fun `pausing stops tick from moving the falling piece`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        engine.pause()
        val before = engine.snapshot().fallingCells
        engine.tick()
        assertEquals(before, engine.snapshot().fallingCells)
    }

    @Test
    fun `fallBoostActive shortens the effective fall interval`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val normalInterval = engine.currentFallIntervalMs()
        engine.fallBoostActive = true
        assertTrue(engine.currentFallIntervalMs() < normalInterval)
    }

    @Test
    fun `stacking pieces to the top of a short board ends the game`() {
        val engine = GameEngine(board = Board(width = 8, height = 4), bag = PieceBag(Random(1)))
        repeat(40) { engine.tick() }
        assertEquals(GameState.GAME_OVER, engine.snapshot().state)
    }

    @Test
    fun `locking a piece emits PIECE_LOCKED and draining clears the queue`() {
        val engine = GameEngine(board = Board(width = 8, height = 4), bag = PieceBag(Random(1)))
        repeat(6) { engine.tick() }
        val events = engine.drainEvents()
        assertTrue(events.contains(GameEvent.PIECE_LOCKED))
        assertEquals(emptyList<GameEvent>(), engine.drainEvents())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.GameEngineTest"`
Expected: FAIL to compile — `GameEngine` is an unresolved reference.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package tech.illusion.spacecube.game

enum class GameState { PLAYING, PAUSED, GAME_OVER }

enum class GameEvent { PIECE_LOCKED, LINES_CLEARED, GAME_OVER }

data class GameSnapshot(
    val boardCells: List<List<PieceType?>>,
    val fallingCells: Set<Pair<Int, Int>>,
    val fallingType: PieceType,
    val ghostCells: Set<Pair<Int, Int>>,
    val nextType: PieceType,
    val score: Int,
    val level: Int,
    val state: GameState,
)

class GameEngine(
    private val board: Board = Board(),
    private val bag: PieceBag = PieceBag(),
) {
    private var difficulty: Difficulty = Difficulty.NORMAL
    private lateinit var current: FallingPiece
    private lateinit var next: PieceType
    private var linesCleared = 0
    private var score = 0
    private var state = GameState.PLAYING
    private val pendingEvents = mutableListOf<GameEvent>()
    var fallBoostActive = false

    init {
        start(Difficulty.NORMAL)
    }

    fun start(difficulty: Difficulty) {
        this.difficulty = difficulty
        board.clearAll()
        linesCleared = 0
        score = 0
        state = GameState.PLAYING
        fallBoostActive = false
        pendingEvents.clear()
        current = FallingPiece.spawn(bag.next(), board.width)
        next = bag.next()
    }

    fun level(): Int = Scoring.levelForLinesCleared(linesCleared)

    fun currentFallIntervalMs(): Long {
        val base = Scoring.fallIntervalMs(difficulty, level())
        return if (fallBoostActive) maxOf(30L, base / 6) else base
    }

    fun moveLeft(): Boolean = tryMove(deltaRow = 0, deltaCol = -1)
    fun moveRight(): Boolean = tryMove(deltaRow = 0, deltaCol = 1)

    private fun tryMove(deltaRow: Int, deltaCol: Int): Boolean {
        if (state != GameState.PLAYING) return false
        val moved = current.movedBy(deltaRow, deltaCol)
        if (board.canPlace(moved.absoluteCells())) {
            current = moved
            return true
        }
        return false
    }

    fun rotate(): Boolean {
        if (state != GameState.PLAYING) return false
        val rotated = current.rotatedClockwise()
        if (board.canPlace(rotated.absoluteCells())) {
            current = rotated
            return true
        }
        return false
    }

    fun pause() {
        if (state == GameState.PLAYING) state = GameState.PAUSED
    }

    fun resume() {
        if (state == GameState.PAUSED) state = GameState.PLAYING
    }

    fun tick() {
        if (state != GameState.PLAYING) return
        val dropped = current.movedBy(1, 0)
        if (board.canPlace(dropped.absoluteCells())) {
            current = dropped
            return
        }
        lockCurrentPiece()
    }

    private fun lockCurrentPiece() {
        board.lock(current.absoluteCells(), current.type)
        pendingEvents.add(GameEvent.PIECE_LOCKED)
        val cleared = board.fullRows()
        if (cleared.isNotEmpty()) {
            board.clearRows(cleared)
            score += Scoring.pointsForClearedLines(cleared.size, level())
            linesCleared += cleared.size
            pendingEvents.add(GameEvent.LINES_CLEARED)
        }
        val spawned = FallingPiece.spawn(next, board.width)
        next = bag.next()
        if (!board.canPlace(spawned.absoluteCells())) {
            state = GameState.GAME_OVER
            pendingEvents.add(GameEvent.GAME_OVER)
            return
        }
        current = spawned
    }

    private fun ghostCells(): Set<Pair<Int, Int>> {
        var candidate = current
        while (board.canPlace(candidate.movedBy(1, 0).absoluteCells())) {
            candidate = candidate.movedBy(1, 0)
        }
        return candidate.absoluteCells().toSet()
    }

    fun snapshot(): GameSnapshot = GameSnapshot(
        boardCells = board.rows(),
        fallingCells = current.absoluteCells().toSet(),
        fallingType = current.type,
        ghostCells = ghostCells(),
        nextType = next,
        score = score,
        level = level(),
        state = state,
    )

    fun drainEvents(): List<GameEvent> {
        val copy = pendingEvents.toList()
        pendingEvents.clear()
        return copy
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.GameEngineTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/GameEngine.kt app/src/test/java/tech/illusion/spacecube/game/GameEngineTest.kt
git commit -m "feat(game): add GameEngine state machine with event queue"
```

---

## Task 7: High-score persistence

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/HighScoreStore.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/InMemoryHighScoreStoreTest.kt`
- Test: `app/src/androidTest/java/tech/illusion/spacecube/game/SharedPreferencesHighScoreStoreTest.kt`
- Modify: `gradle/libs.versions.toml` (add `androidx.test:core`)
- Modify: `app/build.gradle.kts` (add `androidTestImplementation(libs.androidx.test.core)`)

**Interfaces:**
- Produces: `interface HighScoreStore { fun lastScore(): Int; fun highScore(): Int; fun recordScore(score: Int) }`, `class InMemoryHighScoreStore : HighScoreStore`, `class SharedPreferencesHighScoreStore(context: Context) : HighScoreStore`.

- [ ] **Step 1: Write the failing unit test**

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryHighScoreStoreTest {
    @Test
    fun `recording a score updates last score and raises high score only on a new best`() {
        val store = InMemoryHighScoreStore()
        store.recordScore(120)
        assertEquals(120, store.lastScore())
        assertEquals(120, store.highScore())
        store.recordScore(80)
        assertEquals(80, store.lastScore())
        assertEquals(120, store.highScore())
        store.recordScore(200)
        assertEquals(200, store.lastScore())
        assertEquals(200, store.highScore())
    }
}
```

- [ ] **Step 2: Run the unit test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.InMemoryHighScoreStoreTest"`
Expected: FAIL to compile — `InMemoryHighScoreStore` is an unresolved reference.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package tech.illusion.spacecube.game

import android.content.Context

interface HighScoreStore {
    fun lastScore(): Int
    fun highScore(): Int
    fun recordScore(score: Int)
}

class InMemoryHighScoreStore : HighScoreStore {
    private var last = 0
    private var best = 0

    override fun lastScore(): Int = last
    override fun highScore(): Int = best

    override fun recordScore(score: Int) {
        last = score
        if (score > best) best = score
    }
}

class SharedPreferencesHighScoreStore(context: Context) : HighScoreStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastScore(): Int = prefs.getInt(KEY_LAST_SCORE, 0)
    override fun highScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    override fun recordScore(score: Int) {
        val newHigh = maxOf(score, highScore())
        prefs.edit()
            .putInt(KEY_LAST_SCORE, score)
            .putInt(KEY_HIGH_SCORE, newHigh)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "spacecube_scores"
        private const val KEY_LAST_SCORE = "last_score"
        private const val KEY_HIGH_SCORE = "high_score"
    }
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest --tests "tech.illusion.spacecube.game.InMemoryHighScoreStoreTest"`
Expected: PASS (1 test)

- [ ] **Step 5: Add the `androidx.test:core` dependency**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
androidxTestCore = "1.6.1"
```

Add to `[libraries]`:

```toml
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
```

In `app/build.gradle.kts`, add alongside the other `androidTestImplementation` lines:

```kotlin
androidTestImplementation(libs.androidx.test.core)
```

- [ ] **Step 6: Write the failing instrumented test**

```kotlin
package tech.illusion.spacecube.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesHighScoreStoreTest {
    @Test
    fun recordedScorePersistsAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("spacecube_scores", Context.MODE_PRIVATE).edit().clear().apply()

        SharedPreferencesHighScoreStore(context).recordScore(150)
        val reloaded = SharedPreferencesHighScoreStore(context)

        assertEquals(150, reloaded.lastScore())
        assertEquals(150, reloaded.highScore())
    }
}
```

- [ ] **Step 7: Build and run the instrumented test on-device (requires a connected PICO device or emulator)**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew connectedAndroidTest --tests "tech.illusion.spacecube.game.SharedPreferencesHighScoreStoreTest"`
Expected: PASS. If no device is connected, note this explicitly instead of claiming it passed — see the plan-wide note on device availability.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/tech/illusion/spacecube/game/HighScoreStore.kt app/src/test/java/tech/illusion/spacecube/game/InMemoryHighScoreStoreTest.kt app/src/androidTest/java/tech/illusion/spacecube/game/SharedPreferencesHighScoreStoreTest.kt
git commit -m "feat(game): add high-score persistence"
```

---

## Task 8: Render the board and falling piece as ECS cubes

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt` (replace the placeholder body)

**Interfaces:**
- Consumes: `GameSnapshot`, `GameEngine`, `PieceType` (Tasks 1, 6).
- Produces: `class BoardCubeRenderer(boardWidth: Int, boardHeight: Int)` with `fun attachTo(content: com.pico.spatial.core.container.SpatialViewEntityManager)` and `fun render(snapshot: GameSnapshot)`.

This task has no automated test — it's ECS/Compose rendering glue that only proves itself on-device. Verify with build + install + launch + visual check, matching the flow already established in `AGENTS.md`.

- [ ] **Step 1: Implement `BoardCubeRenderer`**

`MeshResource.createBox`, `UnlitMaterial.create(blendingMode)`, `.setBaseColor`/`.setOpacity`, `ModelEntity(mesh, material)`, `entity.enabled`, and `content.addEntity(entity)` were all verified against the SDK 6.0 `agent-vault` API reference (`com.pico.spatial.core.ecs.resource.md`, `com.pico.spatial.core.container.md`) before writing this task.

```kotlin
package tech.illusion.spacecube.content

import com.pico.spatial.core.container.SpatialViewEntityManager
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import tech.illusion.spacecube.game.GameSnapshot
import tech.illusion.spacecube.game.PieceType

internal fun candyColorFor(type: PieceType): Color4 = when (type) {
    PieceType.I -> Color4(0x52 / 255f, 0xD1 / 255f, 0xE8 / 255f, 1f)
    PieceType.O -> Color4(0xFF / 255f, 0xD2 / 255f, 0x4C / 255f, 1f)
    PieceType.T -> Color4(0xFF / 255f, 0x6F / 255f, 0xA0 / 255f, 1f)
    PieceType.S -> Color4(0x57 / 255f, 0xE0 / 255f, 0xA0 / 255f, 1f)
    PieceType.Z -> Color4(0xFF / 255f, 0x5D / 255f, 0x5D / 255f, 1f)
    PieceType.L -> Color4(0xFF / 255f, 0xA2 / 255f, 0x3C / 255f, 1f)
    PieceType.J -> Color4(0x8B / 255f, 0x93 / 255f, 0xF8 / 255f, 1f)
}

private const val CELL_SIZE_M = 0.05f
private const val CELL_GAP_M = 0.006f
private const val CELL_STEP_M = CELL_SIZE_M + CELL_GAP_M
private const val GHOST_POOL_SIZE = 4

class BoardCubeRenderer(
    private val boardWidth: Int,
    private val boardHeight: Int,
) {
    private class Cube(val entity: ModelEntity, val material: UnlitMaterial)

    private lateinit var solidCubes: List<Cube>
    private lateinit var ghostCubes: List<Cube>

    fun attachTo(content: SpatialViewEntityManager) {
        solidCubes = List(boardWidth * boardHeight) { createCube(content, BlendingMode.OPAQUE, opacity = 1f) }
        ghostCubes = List(GHOST_POOL_SIZE) { createCube(content, BlendingMode.TRANSPARENT, opacity = 0.35f) }
    }

    private fun createCube(content: SpatialViewEntityManager, blendingMode: BlendingMode, opacity: Float): Cube {
        val material = UnlitMaterial.create(blendingMode).apply { setOpacity(opacity) }
        val mesh = MeshResource.createBox(Vector3(CELL_SIZE_M, CELL_SIZE_M, CELL_SIZE_M), cornerRadius = 0.008f)
        val entity = ModelEntity(mesh, material).apply { enabled = false }
        content.addEntity(entity)
        return Cube(entity, material)
    }

    fun render(snapshot: GameSnapshot) {
        for (row in 0 until boardHeight) {
            for (col in 0 until boardWidth) {
                val cube = solidCubes[row * boardWidth + col]
                val lockedType = snapshot.boardCells[row][col]
                val fallingHere = (row to col) in snapshot.fallingCells
                val type = lockedType ?: snapshot.fallingType.takeIf { fallingHere }
                if (type == null) {
                    cube.entity.enabled = false
                } else {
                    cube.entity.enabled = true
                    cube.material.setBaseColor(candyColorFor(type))
                    positionCube(cube.entity, row, col)
                }
            }
        }
        val ghostList = snapshot.ghostCells.toList()
        ghostCubes.forEachIndexed { index, cube ->
            val cell = ghostList.getOrNull(index)
            if (cell == null) {
                cube.entity.enabled = false
            } else {
                cube.entity.enabled = true
                cube.material.setBaseColor(candyColorFor(snapshot.fallingType))
                positionCube(cube.entity, cell.first, cell.second)
            }
        }
    }

    private fun positionCube(entity: ModelEntity, row: Int, col: Int) {
        entity.components[TransformComponent::class.java]?.setPosition(
            Vector3(
                (col - (boardWidth - 1) / 2f) * CELL_STEP_M,
                (boardHeight / 2f - row) * CELL_STEP_M,
                0f,
            )
        )
    }
}
```

- [ ] **Step 2: Wire the renderer and a tick loop into `GamePage.kt`**

Replace the entire contents of `GamePage.kt`. `SpatialView`'s `initial = { content, attachments -> ... }` lambda and `LocalSpatialNavigator` follow the same pattern already used successfully elsewhere in this project.

```kotlin
package tech.illusion.spacecube.content

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay
import tech.illusion.spacecube.game.Difficulty
import tech.illusion.spacecube.game.GameEngine

@Composable
fun GamePage() {
    val engine = remember { GameEngine().apply { start(Difficulty.NORMAL) } }
    val renderer = remember { BoardCubeRenderer(boardWidth = 8, boardHeight = 14) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }

    LaunchedEffect(engine) {
        while (true) {
            delay(engine.currentFallIntervalMs())
            engine.tick()
            snapshot = engine.snapshot()
            renderer.render(snapshot)
        }
    }

    SpatialView(
        modifier = Modifier.size(560.dp),
        initial = { content, _ -> renderer.attachTo(content) },
        attachments = {},
    )
}
```

`Difficulty.NORMAL` is a placeholder default for this task only — Task 13 introduces `GameSettings` (the difficulty hand-off from `HomePage`) and replaces this hardcoded value with `GameSettings.difficulty`.

- [ ] **Step 3: Run the SpatialUI design-style verifier**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS` (0 errors, 0 warnings).

- [ ] **Step 4: Build, install, and launch**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew installDebug
adb shell am start -n tech.illusion.spacecube/.platform.LaunchActivity
```

Expected: build succeeds; if a PICO device is connected, install/launch succeed and opening the game window (once Task 13 wires the button) shows an 8×14 grid of candy-colored cubes with one falling piece visible. If no PICO device is connected in this session, say so explicitly instead of claiming the visual result was confirmed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat(content): render the falling-block board as pooled ECS cubes"
```

---

## Task 9: Score HUD, next-piece preview, and pause button panels

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `GameSnapshot` (Task 6).

This task follows the `AttachmentPanel` pattern already used in the current placeholder `GamePage.kt` (`attachments.entity(id = ...)` positioning). No automated test — verify visually per Task 8's Step 4 flow.

- [ ] **Step 1: Add the HUD, next-piece, and pause panels to `GamePage.kt`**

```kotlin
package tech.illusion.spacecube.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay
import tech.illusion.spacecube.game.Difficulty
import tech.illusion.spacecube.game.GameEngine

@Composable
fun GamePage() {
    val engine = remember { GameEngine().apply { start(Difficulty.NORMAL) } }
    val renderer = remember { BoardCubeRenderer(boardWidth = 8, boardHeight = 14) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }

    LaunchedEffect(engine) {
        while (true) {
            delay(engine.currentFallIntervalMs())
            engine.tick()
            snapshot = engine.snapshot()
            renderer.render(snapshot)
        }
    }

    SpatialView(
        modifier = Modifier.size(560.dp),
        initial = { content, attachments ->
            renderer.attachTo(content)
            attachments.entity(id = "score_hud")?.components[TransformComponent::class.java]
                ?.setPosition(Vector3(-0.18f, 0.42f, 0f))
            attachments.entity(id = "next_piece")?.components[TransformComponent::class.java]
                ?.setPosition(Vector3(0.24f, 0.15f, 0f))
        },
        attachments = {
            AttachmentPanel(id = "score_hud") {
                Box(Modifier.size(120.dp, 56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "SCORE ${snapshot.score}",
                        color = PicoTheme.colorScheme.labelPrimary,
                        style = PicoTheme.typography.labelMedium,
                    )
                }
            }
            AttachmentPanel(id = "next_piece") {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NEXT\n${snapshot.nextType.name}",
                        color = PicoTheme.colorScheme.labelPrimary,
                        style = PicoTheme.typography.labelSmall,
                    )
                }
            }
        },
    )
}
```

- [ ] **Step 2: Run the SpatialUI design-style verifier**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS`

- [ ] **Step 3: Build and visually verify**

Run the Task 8 Step 4 build/install/launch sequence again. Expected: the score updates as the game runs, and a "NEXT <piece letter>" label is visible. Report explicitly if no device is available to confirm visually.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat(content): add score HUD and next-piece preview panels"
```

---

## Task 10: Gesture input — drag pad for movement/soft-drop, separate rotate button

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `GameEngine.moveLeft/moveRight/rotate`, `fallBoostActive` (Task 6).

Per the approved spec, rotation must be a **separate control** from the drag surface, not a tap gesture layered on top of it (avoids ambiguous gesture arbitration between drag and tap on the same pointer). This uses plain `androidx.compose.foundation.gestures.detectDragGestures` — not the Spatial-specific `detectSpatialDragGesture` — since the input surface is ordinary 2D `AttachmentPanel` content; PICO's platform already translates hand-tracking/controller pointing into standard pointer events on 2D UI (the same mechanism buttons already rely on).

- [ ] **Step 1: Add the drag pad and rotate button to `GamePage.kt`**

```kotlin
package tech.illusion.spacecube.content

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.delay
import tech.illusion.spacecube.game.Difficulty
import tech.illusion.spacecube.game.GameEngine

private const val CELL_DRAG_WIDTH_DP = 32f

@Composable
private fun DragPad(engine: GameEngine, modifier: Modifier = Modifier) {
    val cellPx = with(LocalDensity.current) { CELL_DRAG_WIDTH_DP.dp.toPx() }
    var horizontalAccumulator by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier.pointerInput(engine) {
            detectDragGestures(
                onDragStart = { horizontalAccumulator = 0f },
                onDragEnd = { engine.fallBoostActive = false },
                onDragCancel = { engine.fallBoostActive = false },
                onDrag = { change, dragAmount ->
                    change.consume()
                    horizontalAccumulator += dragAmount.x
                    while (horizontalAccumulator >= cellPx) {
                        engine.moveRight()
                        horizontalAccumulator -= cellPx
                    }
                    while (horizontalAccumulator <= -cellPx) {
                        engine.moveLeft()
                        horizontalAccumulator += cellPx
                    }
                    engine.fallBoostActive = dragAmount.y > 0f
                },
            )
        },
    )
}

@Composable
fun GamePage() {
    val engine = remember { GameEngine().apply { start(Difficulty.NORMAL) } }
    val renderer = remember { BoardCubeRenderer(boardWidth = 8, boardHeight = 14) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }

    LaunchedEffect(engine) {
        while (true) {
            delay(engine.currentFallIntervalMs())
            engine.tick()
            snapshot = engine.snapshot()
            renderer.render(snapshot)
        }
    }

    SpatialView(
        modifier = Modifier.size(560.dp),
        initial = { content, attachments ->
            renderer.attachTo(content)
            attachments.entity(id = "score_hud")?.components[TransformComponent::class.java]
                ?.setPosition(Vector3(-0.18f, 0.42f, 0f))
            attachments.entity(id = "next_piece")?.components[TransformComponent::class.java]
                ?.setPosition(Vector3(0.24f, 0.15f, 0f))
            attachments.entity(id = "drag_pad")?.components[TransformComponent::class.java]
                ?.setPosition(Vector3(0f, 0f, 0.05f))
            attachments.entity(id = "rotate_button")?.components[TransformComponent::class.java]
                ?.setPosition(Vector3(0.24f, -0.35f, 0f))
        },
        attachments = {
            AttachmentPanel(id = "score_hud") {
                Box(Modifier.size(120.dp, 56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "SCORE ${snapshot.score}",
                        color = PicoTheme.colorScheme.labelPrimary,
                        style = PicoTheme.typography.labelMedium,
                    )
                }
            }
            AttachmentPanel(id = "next_piece") {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NEXT\n${snapshot.nextType.name}",
                        color = PicoTheme.colorScheme.labelPrimary,
                        style = PicoTheme.typography.labelSmall,
                    )
                }
            }
            AttachmentPanel(id = "drag_pad") {
                DragPad(engine = engine, modifier = Modifier.size(400.dp, 500.dp))
            }
            AttachmentPanel(id = "rotate_button") {
                Button(onClick = { engine.rotate() }) {
                    Text("旋转")
                }
            }
        },
    )
}
```

- [ ] **Step 2: Run the SpatialUI design-style verifier**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS`

- [ ] **Step 3: Build, install, and manually verify on-device**

Run the Task 8 Step 4 sequence. On-device, drag horizontally over the board to move the piece left/right, drag downward to speed up the fall, and tap "旋转" to rotate. Report explicitly if no device is available to confirm this interactively.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat(content): add drag-pad movement/soft-drop and a separate rotate button"
```

---

## Task 11: Pause overlay and Game Over overlay

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `GameEngine.pause/resume/start`, `GameState` (Task 6); `LocalSpatialNavigator` (already used in `HomePage.kt`).

- [ ] **Step 1: Add pause toggle, pause overlay, and game-over overlay**

Add a "暂停" button next to the rotate button and two new `AttachmentPanel`s that only render their content when `snapshot.state` matches, reusing `GameState` from Task 6:

```kotlin
            AttachmentPanel(id = "pause_button") {
                Button(onClick = {
                    if (snapshot.state == tech.illusion.spacecube.game.GameState.PAUSED) engine.resume() else engine.pause()
                    snapshot = engine.snapshot()
                }) {
                    Text(if (snapshot.state == tech.illusion.spacecube.game.GameState.PAUSED) "继续" else "暂停")
                }
            }
            AttachmentPanel(id = "pause_overlay") {
                if (snapshot.state == tech.illusion.spacecube.game.GameState.PAUSED) {
                    Box(Modifier.size(280.dp, 160.dp), contentAlignment = Alignment.Center) {
                        Text("已暂停", color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.titleMedium)
                    }
                }
            }
            AttachmentPanel(id = "game_over_overlay") {
                if (snapshot.state == tech.illusion.spacecube.game.GameState.GAME_OVER) {
                    val navigator = com.pico.spatial.ui.platform.containers.LocalSpatialNavigator.current
                    Box(Modifier.size(320.dp, 220.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.layout.Column {
                            Text("Game Over  分数 ${snapshot.score}", color = PicoTheme.colorScheme.labelPrimary)
                            Button(onClick = {
                                engine.start(tech.illusion.spacecube.game.Difficulty.NORMAL)
                                snapshot = engine.snapshot()
                            }) { Text("再来一局") }
                            Button(onClick = { navigator.closeWindowContainer() }) { Text("返回主页") }
                        }
                    }
                }
            }
```

Add the matching `attachments.entity(id = ...)` position calls for `pause_button`, `pause_overlay`, and `game_over_overlay` in `initial`, positioned like the other panels (e.g. `pause_button` near `rotate_button`, the two overlays centered at `Vector3(0f, 0f, 0.08f)`).

Score persistence on `GAME_OVER` is wired in Task 13, once `HighScoreStore` is available in this composable — this task only adds the overlay UI and does not touch the tick loop's event handling.

**Interfaces (extended):**
- Produces (for Task 13 to consume): the `GameState.GAME_OVER` branch added to `game_over_overlay` in this task; Task 13 is responsible for calling `HighScoreStore.recordScore(snapshot.score)` when `engine.drainEvents()` contains `GameEvent.GAME_OVER` in the tick loop.

- [ ] **Step 2: Run the SpatialUI design-style verifier**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS`

- [ ] **Step 3: Build, install, and manually verify**

Run the Task 8 Step 4 sequence. Pause/resume with the button; let the board fill up (or drag pieces into a bad stack deliberately) to confirm the Game Over overlay appears with "再来一局"/"返回主页". Report explicitly if no device is available.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat(content): add pause and game-over overlays"
```

---

## Task 12: Sound effects via ToneGenerator

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/GameSoundEffects.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `GameEvent` (Task 6).
- Produces: `class GameSoundEffects` with `fun play(events: List<GameEvent>)`, `fun release()`.

**Explicit scope note:** background music is *not* implemented in this task — it needs an actual audio asset file, which isn't available in this project. Only SFX (no asset needed, uses `android.media.ToneGenerator`) ships. Say this plainly when reporting the task; don't add a music toggle that silently does nothing.

- [ ] **Step 1: Implement `GameSoundEffects`**

`android.media.ToneGenerator` is a stock Android framework API (not Spatial-SDK-specific) — `TONE_PROP_BEEP`/`TONE_PROP_ACK`/`TONE_PROP_NACK` are standard proprietary tone constants.

```kotlin
package tech.illusion.spacecube.content

import android.media.AudioManager
import android.media.ToneGenerator
import tech.illusion.spacecube.game.GameEvent

class GameSoundEffects {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)

    fun play(events: List<GameEvent>) {
        events.forEach { event ->
            when (event) {
                GameEvent.PIECE_LOCKED -> toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                GameEvent.LINES_CLEARED -> toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                GameEvent.GAME_OVER -> toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 400)
            }
        }
    }

    fun release() {
        toneGenerator.release()
    }
}
```

- [ ] **Step 2: Wire it into `GamePage.kt`'s tick loop and dispose it on leaving composition**

In `GamePage`, add:

```kotlin
    val soundEffects = remember { GameSoundEffects() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { soundEffects.release() }
    }
```

And inside the `LaunchedEffect` loop, right after `engine.tick()`:

```kotlin
            soundEffects.play(engine.drainEvents())
```

(Task 13 later extends this same call site to also hook `GameEvent.GAME_OVER` into `HighScoreStore.recordScore` — sound and persistence are independent concerns reacting to the same drained event list, but `drainEvents()` must only be called once per tick since it empties the queue.)

- [ ] **Step 3: Run the SpatialUI design-style verifier**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS`

- [ ] **Step 4: Build, install, and manually verify**

Run the Task 8 Step 4 sequence. On-device, confirm a short beep on each piece landing, a distinct tone on a line clear, and a longer tone on game over. Report explicitly if no device is available.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GameSoundEffects.kt app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat(content): add tone-based SFX for lock/line-clear/game-over (no background music: no audio asset available)"
```

---

## Task 13: HomePage difficulty picker, last/high score, and the settings hand-off

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/GameSettings.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/HomePage.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt` (use `GameSettings.difficulty` instead of the hardcoded `Difficulty.NORMAL` from Task 8, and hook `HighScoreStore.recordScore`)

**Interfaces:**
- Consumes: `Difficulty` (Task 5), `HighScoreStore`/`SharedPreferencesHighScoreStore` (Task 7), `GameEngine.drainEvents`/`GameEvent.GAME_OVER` (Tasks 6, 11).
- Produces: `object GameSettings { var difficulty: Difficulty }`.

- [ ] **Step 1: Add the settings hand-off object**

```kotlin
package tech.illusion.spacecube.game

object GameSettings {
    var difficulty: Difficulty = Difficulty.NORMAL
}
```

- [ ] **Step 2: Add a difficulty picker and score display to `HomePage.kt`**

Add three `Button`s for SLOW/NORMAL/FAST (highlighting the selected one via `ButtonDefaults.buttonColors(containerColor = ...)`, per the `spatial-ui-design-style` builtins reference — no `SegmentControl` risk, reusing the already-proven `Button` component) and read scores from `SharedPreferencesHighScoreStore`:

```kotlin
package tech.illusion.spacecube.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import tech.illusion.spacecube.GAME_WINDOW_ID
import tech.illusion.spacecube.R
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import tech.illusion.spacecube.game.Difficulty
import tech.illusion.spacecube.game.GameSettings
import tech.illusion.spacecube.game.SharedPreferencesHighScoreStore

@Composable
fun HomePage() {
    val navigator = LocalSpatialNavigator.current
    val context = LocalContext.current
    val highScoreStore = remember { SharedPreferencesHighScoreStore(context) }
    var selectedDifficulty by remember { mutableStateOf(GameSettings.difficulty) }

    Column(modifier = Modifier.fillMaxSize().padding(64.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.hello),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.titleLarge.copy(fontSize = 64.sp),
                textAlign = TextAlign.Center
            )
            SpatialView(
                modifier = Modifier.size(360.dp),
                initial = { content, _ ->
                    val model = Entity.loadSuspend(uriString = "asset://box.usdz").apply {
                        content.addEntity(this)
                        components[TransformComponent::class.java]?.apply {
                            setEulerAngles(EulerAngles(90f, 0f, 0f))
                            setPosition(Vector3(0f, 0f, -.2f))
                        }
                    }
                }
            )
        }

        Text(
            text = "上一局 ${highScoreStore.lastScore()}  ·  历史最高 ${highScoreStore.highScore()}",
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            listOf(Difficulty.SLOW to "慢", Difficulty.NORMAL to "中", Difficulty.FAST to "快").forEach { (difficulty, label) ->
                val isSelected = difficulty == selectedDifficulty
                Button(
                    onClick = {
                        selectedDifficulty = difficulty
                        GameSettings.difficulty = difficulty
                    },
                    colors = if (isSelected) {
                        ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillPrimary)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillSecondary)
                    },
                ) { Text(label) }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            modifier = Modifier.padding(bottom = 32.dp),
            onClick = { navigator.openWindowContainer(GAME_WINDOW_ID) }
        ) {
            Text("开始游戏")
        }
    }
}
```

- [ ] **Step 3: Use `GameSettings.difficulty` in `GamePage.kt` and record the score on game over**

In `GamePage.kt`, change:

```kotlin
val engine = remember { GameEngine().apply { start(Difficulty.NORMAL) } }
```

to:

```kotlin
val context = androidx.compose.ui.platform.LocalContext.current
val highScoreStore = remember { tech.illusion.spacecube.game.SharedPreferencesHighScoreStore(context) }
val engine = remember { GameEngine().apply { start(GameSettings.difficulty) } }
```

and in the `LaunchedEffect` loop, after `soundEffects.play(engine.drainEvents())`, change it to capture the drained events once and react to both:

```kotlin
            val events = engine.drainEvents()
            soundEffects.play(events)
            if (events.contains(tech.illusion.spacecube.game.GameEvent.GAME_OVER)) {
                highScoreStore.recordScore(snapshot.score)
            }
```

(Remove the now-redundant standalone `soundEffects.play(engine.drainEvents())` line from Task 12 — `drainEvents()` empties the queue, so it must only be called once per tick.)

Also update the "再来一局" button added in Task 11, which currently restarts with a hardcoded placeholder:

```kotlin
engine.start(tech.illusion.spacecube.game.Difficulty.NORMAL)
```

to:

```kotlin
engine.start(GameSettings.difficulty)
```

- [ ] **Step 4: Run the SpatialUI design-style verifier**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS`

- [ ] **Step 5: Build, install, and manually verify end-to-end**

Run the Task 8 Step 4 sequence. On-device: pick a difficulty on the home page, start the game, confirm the fall speed matches the preset, play until Game Over, return to the home page, and confirm the score line now shows the just-played score and an updated high score if it was a new best. Report explicitly if no device is available.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/GameSettings.kt app/src/main/java/tech/illusion/spacecube/content/HomePage.kt app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat(content): add difficulty picker on HomePage and persist score on game over"
```

---

## Task 14: Full-suite regression pass and AGENTS.md update

**Files:**
- Modify: `AGENTS.md`

**Interfaces:** none — this task only verifies and documents.

- [ ] **Step 1: Run the entire unit test suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest`
Expected: PASS — all tests from Tasks 1-7 (27 unit tests total: 3 + 2 + 6 + 3 + 4 + 8 + 1 = 27, adjust if any task's test count changed during implementation).

- [ ] **Step 2: Run the design-style verifier over the whole module**

Run: `bash $(find ~/.claude/plugins/cache -path "*spatial-ui-design-style/scripts/verify-design-style.sh" | head -1) app/src/main/java`
Expected: `result: PASS`

- [ ] **Step 3: Full build, install, and on-device playtest**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew installDebug
adb shell am start -n tech.illusion.spacecube/.platform.LaunchActivity
```

Play a full round covering: difficulty selection, move/rotate/soft-drop, at least one line clear, pause/resume, and a game over followed by "再来一局" and "返回主页". If no PICO device is connected in this session, state that plainly and list this as the one remaining unverified item rather than claiming it passed.

- [ ] **Step 4: Update `AGENTS.md`**

Replace the "What this project currently does" and "Most natural next evolution paths" sections to describe the now-implemented gameplay (grid, pieces, scoring, difficulty, persistence, SFX) instead of the placeholder, and move "V2 关卡系统" and "background music" (blocked on an audio asset) into the next-evolution list.

- [ ] **Step 5: Commit**

```bash
git add AGENTS.md
git commit -m "docs: update AGENTS.md for the implemented Tetris gameplay"
```
