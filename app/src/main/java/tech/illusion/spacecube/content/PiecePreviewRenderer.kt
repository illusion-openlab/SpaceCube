package tech.illusion.spacecube.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector3
import tech.illusion.spacecube.game.PieceType
import tech.illusion.spacecube.game.previewCellOffsets

// 与棋盘同尺度（BoardCubeRenderer 的 CELL_SIZE_M / CELL_GAP_M），这样配置窗口里
// 看到的方块大小和进游戏后看到的是一回事，不会有"预览比实际大/小"的错位感。
private const val PREVIEW_CELL_SIZE_M = 0.05f
private const val PREVIEW_CELL_GAP_M = 0.006f
private const val PREVIEW_CELL_STEP_M = PREVIEW_CELL_SIZE_M + PREVIEW_CELL_GAP_M
private const val PREVIEW_CELL_CORNER_RADIUS_M = 0.008f

// 每个 tetromino 恒为 4 格，所以池子固定 4 个实体，换形状只是改这 4 个的位置。
private const val PREVIEW_PIECE_CELLS = 4

// 最宽的是 I（4 格）。底板按最宽的算，这样随机到哪种形状都不会探出底板。
private const val PREVIEW_PLATE_WIDTH_CELLS = 4
private const val PREVIEW_PLATE_MARGIN_M = 0.02f
private const val PREVIEW_PLATE_THICKNESS_M = 0.02f
private const val PREVIEW_PLATE_CORNER_RADIUS_M = 0.01f

/**
 * 配置窗口里那一个随机俄罗斯方块的 3D 预览：4 个方块 + 脚下一块薄底板。
 *
 * 和 [BoardCubeRenderer] 刻意分开而不是复用它：那个类为 10×18 的棋盘、落子动画、
 * 幽灵方块、按 (row, col) 钉死的池子而生，这里只要 4 个静态方块。复用会把两边的
 * 约束绑在一起，而这两组约束没有任何交集。
 *
 * 材质切换有意用了两种不同手法，跟 [BoardCubeRenderer] 的既有做法保持一致：
 * 方块走 `ModelComponent.materials[0]` 原地换绑（实体池化、不销毁），底板走
 * 销毁重建（[BoardCubeRenderer.setBasePlateMaterial] 就是这么做的，且销毁实体会
 * 顺带释放它持有的材质资源）。
 *
 * **不做动画**：`withFrameNanos` 在 WindowContainer 里实测只触发一次，靠它驱动的
 * 旋转/浮动一帧都不会跑。真要动得另起 `delay` 循环，本轮不做。
 */
internal class PiecePreviewRenderer {
    private var parent: Entity? = null
    private var cubes: List<ModelEntity> = emptyList()
    private var plate: ModelEntity? = null
    private var cubeMesh: MeshResource? = null

    var isAttached = false
        private set

    /**
     * 建出 4 个方块与底板并挂到 [parent] 下。只创建 5 个实体，不需要像
     * [BoardCubeRenderer.attachTo] 那样在循环里 `yield()`。
     */
    fun attachTo(parent: Entity, type: PieceType, pieceMaterial: Material, plateMaterial: Material) {
        this.parent = parent
        // 4 个方块几何相同，mesh 建一次共享 —— 和 BoardCubeRenderer 的 L1 同一个理由。
        val mesh = MeshResource.createBox(
            Vector3(PREVIEW_CELL_SIZE_M, PREVIEW_CELL_SIZE_M, PREVIEW_CELL_SIZE_M),
            cornerRadius = PREVIEW_CELL_CORNER_RADIUS_M,
        )
        cubeMesh = mesh
        cubes = List(PREVIEW_PIECE_CELLS) {
            ModelEntity(mesh, pieceMaterial).also { parent.addChild(it) }
        }
        plate = createPlate(plateMaterial).also { parent.addChild(it) }
        isAttached = true
        setPieceType(type)
    }

    /** 换一种形状：4 个实体不变，只重写它们的位置。 */
    fun setPieceType(type: PieceType) {
        if (!isAttached) return
        val offsets = previewCellOffsets(type)
        cubes.forEachIndexed { index, entity ->
            val cell = offsets[index]
            entity.components[TransformComponent::class.java]?.setPosition(
                Vector3(cell.x * PREVIEW_CELL_STEP_M, cell.y * PREVIEW_CELL_STEP_M, 0f)
            )
        }
    }

    /** 方块材质原地换绑，不销毁实体。 */
    fun setPieceMaterial(material: Material) {
        if (!isAttached) return
        cubes.forEach { entity ->
            entity.components[ModelComponent::class.java]?.materials?.set(0, material)
        }
    }

    /** 底板销毁重建 —— 与 [BoardCubeRenderer.setBasePlateMaterial] 同一手法。 */
    fun setPlateMaterial(material: Material) {
        val parent = this.parent ?: return
        plate?.destroy()
        plate = createPlate(material).also { parent.addChild(it) }
    }

    private fun createPlate(material: Material): ModelEntity {
        val width = PREVIEW_PLATE_WIDTH_CELLS * PREVIEW_CELL_STEP_M + PREVIEW_PLATE_MARGIN_M * 2f
        val depth = PREVIEW_CELL_STEP_M + PREVIEW_PLATE_MARGIN_M * 2f
        val mesh = MeshResource.createBox(
            Vector3(width, PREVIEW_PLATE_THICKNESS_M, depth),
            cornerRadius = PREVIEW_PLATE_CORNER_RADIUS_M,
        )
        val entity = ModelEntity(mesh, material)
        // 底行方块的中心在 y = 0，所以它的底面在 -CELL_SIZE/2；底板顶面贴着它，
        // 于是底板中心再往下半个板厚。这个值对 7 种形状都一样 —— previewCellOffsets
        // 保证了底行恒在 y = 0。
        entity.components[TransformComponent::class.java]?.setPosition(
            Vector3(0f, -PREVIEW_CELL_SIZE_M / 2f - PREVIEW_PLATE_THICKNESS_M / 2f, 0f)
        )
        return entity
    }
}
