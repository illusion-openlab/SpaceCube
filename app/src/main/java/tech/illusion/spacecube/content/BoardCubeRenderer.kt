package tech.illusion.spacecube.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import kotlin.math.exp
import kotlinx.coroutines.yield
import tech.illusion.spacecube.game.GameSnapshot
import tech.illusion.spacecube.game.PieceType

// candyColorFor() now lives in CandyPieceColors.kt, shared with the 2D
// next-piece preview so the two can't drift apart.

private const val CELL_SIZE_M = 0.05f
private const val CELL_GAP_M = 0.006f
private const val CELL_STEP_M = CELL_SIZE_M + CELL_GAP_M
private const val GHOST_POOL_SIZE = 4

// How many locked-cube entities to create between yield() points in attachTo()'s
// hot loop. 1 (yield after every entity) rather than some batch size: on-device
// measurement (2026-08-12, under host memory pressure) showed a SINGLE createCube()
// round-trip occasionally costing close to a second, which alone approaches
// Android's ANR input-dispatch timeout - batching several before yielding would
// have reintroduced exactly the risk this loop exists to avoid. yield()'s own
// overhead is negligible next to that per-entity cost.
private const val ENTITY_CREATE_YIELD_INTERVAL = 1

// Every tetromino is exactly 4 cells.
private const val MAX_PIECE_CELLS = 4

// Movement easing for the falling piece (user report 2026-08-07: "下落物体在快速移动
// 时直接瞬移了，缺少了移动过渡"). Exponential ease, so the visual speed is the same at
// any frame rate. TAU is the time to cover ~63% of the remaining distance - small
// enough to still feel hand-attached, large enough to read as motion.
private const val MOVE_EASE_TAU_SECONDS = 0.055f

// Below this the cube is snapped onto its target, to stop an exponential ease from
// creeping toward it forever.
private const val SETTLE_EPSILON_M = 0.0005f

// A jump larger than this is treated as a teleport rather than a move, and snaps
// instead of gliding. Sized above one cell step so ordinary multi-cell fast drags
// still animate, but a spawn at the top of the board doesn't slide up the screen.
private const val SNAP_DISTANCE_M = CELL_STEP_M * 3f

// Depth of the playfield relative to the anchor. Stays at 0: it was briefly moved
// forward to 0.17 on a misreading of "游戏主面板可以往前靠近一点与游戏状态面板保持同一深度"
// - "主面板" there means the big centred card overlays (start screen / paused /
// game over), NOT the board. The user's correction: "你现在弄反了，反而将方块那一排弄到
// 前面去了". Leave the board alone; see MAIN_PANEL_Z_M in GamePage.kt.
private const val BOARD_Z_M = 0f
private const val GROUND_THICKNESS_M = 0.04f
private const val GROUND_MARGIN_CELLS = 0f

// 2026-08-07 frosted-jelly look (user request "方块做成磨砂透明，果冻状", chosen over a
// PBR/IBL glass material because that path needs an HDR environment cubemap this
// project doesn't have - with no lighting set up at all, PBR would render dark).
// UnlitMaterial has no roughness/scattering parameter, so "frosted" is suggested
// by opacity + the milkier candyJellyColorFor() palette rather than truly shaded.
// Deliberately kept fairly opaque: the 7 candy colors are how the player
// identifies pieces, and legibility degrades fast as stacked cubes see through
// each other.
private const val CUBE_JELLY_OPACITY = 0.80f
private const val GHOST_OPACITY = 0.30f

// Ghost piece / 落点投影 turned off 2026-08-07 per user request ("先不开启方块下落
// 位置投影"). Flip to true to bring it back: GameEngine still computes
// snapshot.ghostCells regardless, so nothing outside this file has to change.
// Worth revisiting once the frosted-jelly cubes have been played with - a
// translucent ghost behind translucent cubes may read differently than it did
// behind the original opaque ones.
private const val SHOW_GHOST_PIECE = false

// The 井体底座 / base plate's glass-look tuning (0.30 -> 0.48 -> 0.80 opacity per
// on-device feedback) now lives in BasePlateMaterialLoader.createGlassMaterial(),
// moved there verbatim since the loader owns every base-plate material build,
// glass included.

// All entities are added as children of an `anchor` entity (positioned once, in
// front of the user, by the caller) rather than directly via content.addEntity -
// this keeps every relative position below unchanged while letting the caller
// place the whole board anywhere in the Stage (origin is at the user's feet).
class BoardCubeRenderer(
    private val boardWidth: Int,
    private val boardHeight: Int,
) {
    private class Cube(val entity: ModelEntity, val material: UnlitMaterial)

    /**
     * A falling-piece cube, which unlike the locked grid owns a persistent identity
     * and therefore a position that can be animated toward a target.
     */
    private class MovingCube(val entity: ModelEntity, val material: UnlitMaterial) {
        var target = Vector3(0f, 0f, 0f)
        var current = Vector3(0f, 0f, 0f)
        var live = false
    }

    /**
     * One cube per grid cell, for LOCKED cells only. Each slot is pinned to its own
     * (row, col) forever, so its transform is written once at creation and never
     * again - locked blocks don't move, they only appear and disappear.
     */
    private lateinit var lockedCubes: List<Cube>

    /**
     * The falling piece's own cubes. Separate from [lockedCubes] precisely so the
     * piece can be *interpolated*: in the old single-grid design a "move" was just
     * different cells switching on and off, which is unanimatable by construction and
     * is why fast movement read as a teleport.
     */
    private lateinit var fallingCubes: List<MovingCube>
    private lateinit var ghostCubes: List<Cube>

    /**
     * Centre-to-centre distance between adjacent cells, in metres, before the
     * scene's own scale is applied.
     *
     * Exposed so gesture code can map hand travel onto board columns 1:1 - moving
     * a hand one rendered cell's worth should move the piece exactly one column.
     */
    val cellStepMeters: Float get() = CELL_STEP_M

    /** Depth of the playfield plane relative to the anchor. */
    val boardZ: Float get() = BOARD_Z_M

    /**
     * Z of the base plate's FAR edge - the side away from the user. Stage's +Z points
     * at the user, so "far" is NEGATIVE; the sign is the whole point of this property
     * existing (a near-edge version got the glass panel put on the wrong side once).
     * The plate is square in the XZ plane, so this is half the span used for its width.
     */
    val basePlateFarEdgeZ: Float
        get() = -(boardWidth + GROUND_MARGIN_CELLS * 2) * CELL_STEP_M / 2f

    /** Full width of the playfield in metres, outer cube edge to outer cube edge. */
    val boardSpanX: Float get() = (boardWidth - 1) * CELL_STEP_M + CELL_SIZE_M

    /** Full height of the playfield in metres, outer cube edge to outer cube edge. */
    val boardSpanY: Float get() = (boardHeight - 1) * CELL_STEP_M + CELL_SIZE_M

    /**
     * Y of the playfield's centre relative to the anchor.
     *
     * Not zero: [positionCube] lays rows out from `boardHeight / 2f` downward, which
     * leaves the occupied rows straddling the anchor asymmetrically. Exposed so
     * anything that needs to cover the playfield (e.g. the V2 control surface) can
     * line up with it instead of re-deriving this offset and drifting.
     */
    val boardCenterY: Float
        get() {
            val topEdge = (boardHeight / 2f) * CELL_STEP_M + CELL_SIZE_M / 2f
            val bottomEdge = (boardHeight / 2f - (boardHeight - 1)) * CELL_STEP_M - CELL_SIZE_M / 2f
            return (topEdge + bottomEdge) / 2f
        }

    /**
     * Signed Y offset (metres, negative) from the anchor to the base plate's centre.
     *
     * The anchor sits at the *middle* of the board, so a caller that wants the base
     * plate itself at a particular height - e.g. a fixed distance below the user's
     * head - has to offset the anchor by this much. Exposed so that math lives in
     * one place instead of being duplicated (and drifting) at the call site.
     */
    val basePlateOffsetY: Float
        get() {
            val lowestCubeCenterY = (boardHeight / 2f - (boardHeight - 1)) * CELL_STEP_M
            val lowestCubeBottomY = lowestCubeCenterY - CELL_SIZE_M / 2f
            return lowestCubeBottomY - GROUND_THICKNESS_M / 2f
        }

    /** True once [attachTo] has built the entity pools. */
    val isAttached: Boolean get() = ::lockedCubes.isInitialized

    /**
     * `suspend` so the per-cell loop can [yield] periodically - measured on-device
     * (2026-08-12, chasing "进入应用加载过程有点长"), each [createCube] round-trips
     * through the SDK's native scene service, at a cost severe enough under load to
     * both starve every other frame on the main thread AND trigger a real ANR (`am`
     * logged `handleAppException type=ANR` mid-loop, ~1 entity/sec instead of the
     * expected near-instant). Yielding only ONCE before this function - i.e. only
     * reordering the caller - was not enough: with `boardWidth * boardHeight` (180)
     * entities created in one uninterrupted burst, the Choreographer never got a
     * second chance to present a frame, so a caller-side loading panel would still
     * render invisible/frozen for the whole burst. Yielding every
     * [ENTITY_CREATE_YIELD_INTERVAL] entities here, inside the actual hot loop, is
     * the fix that generalizes regardless of how slow (or how loaded the host/device
     * is) any single entity creation turns out to be.
     */
    suspend fun attachTo(anchor: Entity, groundMaterial: Material) {
        val locked = ArrayList<Cube>(boardWidth * boardHeight)
        for (index in 0 until boardWidth * boardHeight) {
            val cube = createCube(anchor, BlendingMode.TRANSPARENT, opacity = CUBE_JELLY_OPACITY)
            // Pinned position, written once: index encodes the cell.
            cube.entity.components[TransformComponent::class.java]
                ?.setPosition(cellPosition(index / boardWidth, index % boardWidth))
            locked.add(cube)
            if (index % ENTITY_CREATE_YIELD_INTERVAL == 0) yield()
        }
        lockedCubes = locked
        fallingCubes = List(MAX_PIECE_CELLS) {
            val cube = createCube(anchor, BlendingMode.TRANSPARENT, opacity = CUBE_JELLY_OPACITY)
            MovingCube(cube.entity, cube.material)
        }
        // An empty pool makes render()'s ghost loop a no-op - no need to branch there.
        ghostCubes = if (SHOW_GHOST_PIECE) {
            List(GHOST_POOL_SIZE) { createCube(anchor, BlendingMode.TRANSPARENT, opacity = GHOST_OPACITY) }
        } else {
            emptyList()
        }
        attachGround(anchor, groundMaterial)
    }

    private var groundEntity: ModelEntity? = null
    private var groundAnchor: Entity? = null

    // null = the existing per-cube UnlitMaterial + setBaseColor() path below
    // (JELLY, the default - completely unchanged by this feature). Non-null =
    // a PBR PieceMaterial set is active; render() swaps each visible cell's
    // bound material to materials.getValue(type) via ModelComponent.materials[0]
    // instead of recoloring the cube's own private UnlitMaterial.
    private var pieceMaterials: Map<PieceType, Material>? = null

    /**
     * Sets which per-[PieceType] material set `render()` should bind cells to.
     * `null` reverts to the original jelly look (each cube's own private
     * `UnlitMaterial`, recolored via `setBaseColor` as this class has always
     * done). Safe to call before [attachTo] - `render()` itself is a no-op
     * until [isAttached].
     *
     * `ModelComponent.materials[0] = pbrMaterial` (in [bindCubeMaterial])
     * permanently detaches a cube's own private `UnlitMaterial` from its
     * bound slot 0 - recoloring that orphaned object afterward (the `else`
     * branch there) would no longer be visible. So a transition FROM a
     * non-null map BACK to null has to explicitly re-bind every pooled
     * entity's own material into slot 0 here, once, or the PBR look would
     * be stuck forever. A no-PBR-yet-to-no-PBR call (null -> null) or a
     * PBR-to-different-PBR call (non-null -> non-null) needs no rebind:
     * render()'s own per-frame bindCubeMaterial calls already handle those.
     */
    fun setPieceMaterials(materials: Map<PieceType, Material>?) {
        val wasPbr = pieceMaterials != null
        pieceMaterials = materials
        if (wasPbr && materials == null && isAttached) {
            lockedCubes.forEach { rebindOwnMaterial(it.entity, it.material) }
            fallingCubes.forEach { rebindOwnMaterial(it.entity, it.material) }
            ghostCubes.forEach { rebindOwnMaterial(it.entity, it.material) }
        }
    }

    /** Re-binds [entity]'s own private [material] into its ModelComponent's slot 0. */
    private fun rebindOwnMaterial(entity: ModelEntity, material: UnlitMaterial) {
        entity.components[ModelComponent::class.java]?.materials?.set(0, material)
    }

    private fun attachGround(anchor: Entity, material: Material) {
        groundAnchor = anchor
        groundEntity = createGroundEntity(material).also { anchor.addChild(it) }
    }

    private fun createGroundEntity(material: Material): ModelEntity {
        val groundSpan = (boardWidth + GROUND_MARGIN_CELLS * 2) * CELL_STEP_M
        val mesh = MeshResource.createBox(Vector3(groundSpan, GROUND_THICKNESS_M, groundSpan), cornerRadius = 0.01f)
        val entity = ModelEntity(mesh, material)
        entity.components[TransformComponent::class.java]?.setPosition(Vector3(0f, basePlateOffsetY, 0f))
        return entity
    }

    /**
     * Swaps the base plate's material by destroying and recreating the ground
     * entity (mesh/position unchanged). An in-place mutation path exists
     * (`ModelComponent.materials[0] = material`, confirmed in core-6.0.0 SDK
     * sources) but was not exercised here; destroy-and-recreate is what's
     * actually device-tested for this feature, so that's what this uses.
     * No-op if [attachTo] hasn't run yet.
     */
    fun setBasePlateMaterial(material: Material) {
        val anchor = groundAnchor ?: return
        groundEntity?.destroy()
        groundEntity = createGroundEntity(material).also { anchor.addChild(it) }
    }

    private fun createCube(anchor: Entity, blendingMode: BlendingMode, opacity: Float): Cube {
        val material = UnlitMaterial.create(blendingMode).apply { setOpacity(opacity) }
        val mesh = MeshResource.createBox(Vector3(CELL_SIZE_M, CELL_SIZE_M, CELL_SIZE_M), cornerRadius = 0.008f)
        val entity = ModelEntity(mesh, material).apply { enabled = false }
        anchor.addChild(entity)
        return Cube(entity, material)
    }

    /**
     * Binds [entity] to show [type]: either the shared, pre-tinted PBR
     * material for that type (in-place swap - see [pieceMaterials]'s KDoc
     * for why this doesn't destroy/recreate), or - the JELLY path,
     * unchanged from before this feature - recolors [fallbackMaterial]
     * (the entity's own private `UnlitMaterial`) via `setBaseColor`.
     */
    private fun bindCubeMaterial(entity: ModelEntity, fallbackMaterial: UnlitMaterial, type: PieceType) {
        val pbrMaterial = pieceMaterials?.get(type)
        if (pbrMaterial != null) {
            entity.components[ModelComponent::class.java]?.materials?.set(0, pbrMaterial)
        } else {
            fallbackMaterial.setBaseColor(candyJellyColorFor(type))
        }
    }

    /** Sets what SHOULD be on screen. Call whenever engine state changes. */
    fun render(snapshot: GameSnapshot) {
        // Belt-and-braces; callers should gate on isAttached so they don't mark the
        // revision consumed for a render that never happened.
        if (!isAttached) return
        for (row in 0 until boardHeight) {
            for (col in 0 until boardWidth) {
                val cube = lockedCubes[row * boardWidth + col]
                val lockedType = snapshot.boardCells[row][col]
                if (lockedType == null) {
                    cube.entity.enabled = false
                } else {
                    cube.entity.enabled = true
                    bindCubeMaterial(cube.entity, cube.material, lockedType)
                }
            }
        }

        // Sorted so cube i keeps meaning "the same relative cell" between frames. A
        // pure translation preserves that order exactly, which is what lets a sideways
        // or downward move interpolate smoothly. A rotation reshapes the set and may
        // reshuffle - acceptable, since a rotation is meant to read as instantaneous.
        val fallingCells = snapshot.fallingCells.sortedWith(compareBy({ it.first }, { it.second }))
        fallingCubes.forEachIndexed { index, cube ->
            val cell = fallingCells.getOrNull(index)
            if (cell == null) {
                cube.entity.enabled = false
                cube.live = false
            } else {
                val target = cellPosition(cell.first, cell.second)
                // Snap rather than glide when the jump is too big to be a move - a
                // freshly spawned piece would otherwise visibly slide up from wherever
                // the previous piece landed.
                val jumped = !cube.live || Vector3.distance(cube.current, target) > SNAP_DISTANCE_M
                cube.target = target
                if (jumped) {
                    cube.current = target
                    cube.entity.components[TransformComponent::class.java]?.setPosition(target)
                }
                cube.live = true
                cube.entity.enabled = true
                bindCubeMaterial(cube.entity, cube.material, snapshot.fallingType)
            }
        }

        val ghostList = snapshot.ghostCells.toList()
        ghostCubes.forEachIndexed { index, cube ->
            val cell = ghostList.getOrNull(index)
            if (cell == null) {
                cube.entity.enabled = false
            } else {
                cube.entity.enabled = true
                // Deliberately NOT bindCubeMaterial(): ghosts are built translucent
                // (BlendingMode.TRANSPARENT + GHOST_OPACITY, see createCube) to read as
                // a faint drop-preview, but PieceMaterialLoader's 7 PBR materials are
                // opaque - one per PieceType, no separate ghost variant (that would be a
                // Task-3-level loader change, out of scope here). Binding a ghost to the
                // same shared PBR material a locked/falling cube uses would make it
                // indistinguishable from an actually-placed block. So the ghost loop
                // always stays on the pre-existing jelly recolor path, regardless of
                // whether a PBR set is active elsewhere on the board. (Currently dormant
                // either way: SHOW_GHOST_PIECE is false, so ghostCubes is emptyList()
                // and this loop body never runs - but kept correct for if it's re-enabled.)
                cube.material.setBaseColor(candyJellyColorFor(snapshot.fallingType))
                cube.entity.components[TransformComponent::class.java]
                    ?.setPosition(cellPosition(cell.first, cell.second))
            }
        }
    }

    /**
     * Eases the falling cubes toward their targets. Call once per frame with the
     * elapsed time; this is what turns a discrete engine move into visible motion.
     */
    fun animate(deltaSeconds: Float) {
        if (!::fallingCubes.isInitialized || deltaSeconds <= 0f) return
        // Frame-rate independent exponential ease: same visual speed at any fps.
        val t = (1f - exp(-deltaSeconds / MOVE_EASE_TAU_SECONDS)).coerceIn(0f, 1f)
        fallingCubes.forEach { cube ->
            if (!cube.live) return@forEach
            val remaining = Vector3.distance(cube.current, cube.target)
            if (remaining <= SETTLE_EPSILON_M) {
                if (remaining > 0f) {
                    cube.current = cube.target
                    cube.entity.components[TransformComponent::class.java]?.setPosition(cube.target)
                }
                return@forEach
            }
            cube.current = Vector3.lerp(cube.current, cube.target, t)
            cube.entity.components[TransformComponent::class.java]?.setPosition(cube.current)
        }
    }

    private fun cellPosition(row: Int, col: Int): Vector3 = Vector3(
        (col - (boardWidth - 1) / 2f) * CELL_STEP_M,
        (boardHeight / 2f - row) * CELL_STEP_M,
        BOARD_Z_M,
    )
}
