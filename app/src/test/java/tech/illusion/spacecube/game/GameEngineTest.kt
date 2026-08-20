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
    fun `moveDown shifts the falling piece down by one row`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val startRow = engine.snapshot().fallingCells.minOf { it.first }
        engine.moveDown()
        assertEquals(startRow + 1, engine.snapshot().fallingCells.minOf { it.first })
    }

    @Test
    fun `moveDown repeatedly stops at the floor instead of going out of bounds`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        repeat(20) { engine.moveDown() }
        assertTrue(engine.snapshot().fallingCells.maxOf { it.first } < 14)
    }

    @Test
    fun `rotateClockwise then rotateCounterClockwise returns the piece to its original cells`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val original = engine.snapshot().fallingCells
        engine.rotateClockwise()
        engine.rotateCounterClockwise()
        assertEquals(original, engine.snapshot().fallingCells)
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

    // pieceId is what lets an in-flight gesture tell "still my piece" from "a new one
    // spawned under my hand". These three tests pin both halves of that contract:
    // it must NOT move for ordinary play, and it MUST move the instant control is lost.

    @Test
    fun `pieceId is stable across moves and rotations of the same piece`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val id = engine.pieceId
        engine.moveLeft()
        engine.moveRight()
        engine.moveDown()
        engine.rotateClockwise()
        assertEquals(id, engine.pieceId)
    }

    @Test
    fun `pieceId changes when the falling piece locks`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val id = engine.pieceId
        // Drop until the piece lands and the next one spawns.
        repeat(20) { engine.tick() }
        assertTrue(engine.pieceId != id)
    }

    @Test
    fun `pieceId changes on restart`() {
        val engine = GameEngine(board = Board(width = 8, height = 14), bag = PieceBag(Random(1)))
        val id = engine.pieceId
        engine.start(Difficulty.NORMAL)
        assertTrue(engine.pieceId != id)
    }
}
