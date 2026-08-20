package tech.illusion.spacecube.game

/**
 * Sentinel for "this input session isn't bound to a falling piece yet".
 *
 * Lives here rather than in either control scheme because both V1 and V2 compare
 * against [GameEngine.pieceId], and a per-scheme copy would be two definitions of the
 * same idea. Never a real id: [GameEngine.pieceId] starts at 0 and only counts up.
 */
internal const val NO_PIECE_ID = -1

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

    /**
     * Bumped on every state change (move, rotation, tick, restart).
     *
     * Lets the renderer notice "something changed" from its own frame loop instead of
     * every input path having to remember to trigger a redraw - which is exactly what
     * went wrong before: gesture-driven moves updated the engine but nothing redrew
     * until the next gravity tick, so several moves surfaced at once and read as a
     * teleport.
     */
    var revision: Int = 0
        private set

    /**
     * Identity of the piece the player currently controls. Bumped whenever that piece
     * stops being controllable - locked by gravity, or replaced by a restart.
     *
     * Input controllers capture this when a gesture begins and abandon the gesture if
     * it changes, so a pinch/drag still being held when a piece locks cannot carry
     * over and shove the freshly spawned piece around. Deliberately separate from
     * [revision], which changes on every move: identity and "something changed" are
     * different questions, and reusing one counter for both would make every sideways
     * move look like a new piece.
     */
    var pieceId: Int = 0
        private set

    init {
        start(Difficulty.NORMAL)
    }

    fun start(difficulty: Difficulty) {
        this.difficulty = difficulty
        board.clearAll()
        linesCleared = 0
        score = 0
        state = GameState.PLAYING
        pendingEvents.clear()
        current = FallingPiece.spawn(bag.next(), board.width)
        next = bag.next()
        pieceId++
        revision++
    }

    fun level(): Int = Scoring.levelForLinesCleared(linesCleared)

    fun currentFallIntervalMs(): Long = Scoring.fallIntervalMs(difficulty, level())

    fun moveLeft(): Boolean = tryMove(deltaRow = 0, deltaCol = -1)
    fun moveRight(): Boolean = tryMove(deltaRow = 0, deltaCol = 1)
    fun moveDown(): Boolean = tryMove(deltaRow = 1, deltaCol = 0)

    private fun tryMove(deltaRow: Int, deltaCol: Int): Boolean {
        if (state != GameState.PLAYING) return false
        val moved = current.movedBy(deltaRow, deltaCol)
        if (board.canPlace(moved.absoluteCells())) {
            current = moved
            revision++
            return true
        }
        return false
    }

    fun rotateClockwise(): Boolean {
        if (state != GameState.PLAYING) return false
        val rotated = current.rotatedClockwise()
        if (board.canPlace(rotated.absoluteCells())) {
            current = rotated
            revision++
            return true
        }
        return false
    }

    fun rotateCounterClockwise(): Boolean {
        if (state != GameState.PLAYING) return false
        val rotated = current.rotatedCounterClockwise()
        if (board.canPlace(rotated.absoluteCells())) {
            current = rotated
            revision++
            return true
        }
        return false
    }

    fun pause() {
        if (state == GameState.PLAYING) {
            state = GameState.PAUSED
            revision++
        }
    }

    fun resume() {
        if (state == GameState.PAUSED) {
            state = GameState.PLAYING
            revision++
        }
    }

    fun tick() {
        if (state != GameState.PLAYING) return
        val dropped = current.movedBy(1, 0)
        if (board.canPlace(dropped.absoluteCells())) {
            current = dropped
            revision++
            return
        }
        lockCurrentPiece()
        revision++
    }

    private fun lockCurrentPiece() {
        board.lock(current.absoluteCells(), current.type)
        // Bumped here, at the moment the piece stops being controllable, rather than
        // beside the spawn below - the game-over path returns without spawning, and an
        // in-flight gesture must be invalidated in that case too.
        pieceId++
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
