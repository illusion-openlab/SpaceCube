package tech.illusion.spacecube.content

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.StageStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import tech.illusion.spacecube.game.DIFFICULTY_BUNDLE_KEY
import tech.illusion.spacecube.game.Difficulty
import tech.illusion.spacecube.game.PieceType
import tech.illusion.spacecube.game.SharedPreferencesBasePlateMaterialStore
import tech.illusion.spacecube.game.SharedPreferencesHighScoreStore
import tech.illusion.spacecube.game.SharedPreferencesPieceMaterialStore

private const val CONFIG_LOG_TAG = "SpaceCubeConfig"

// 窗口本地坐标（米），原点在 Volumetric 窗口中心。这三个值是"看起来对"的初值，
// 标注为门禁 B 可调项：透视截图判不出精确 dp，第 1 轮只核对"造型完整落在窗口内、
// 不与卡片重叠、不被窗口边界截断"，具体偏移量按截图再微调。
private const val CONFIG_CARD_Y_M = 0.10f
private const val CONFIG_CARD_Z_M = -0.05f
private const val PREVIEW_ROOT_Y_M = -0.20f
private const val PREVIEW_ROOT_Z_M = 0.12f

private val START_BUTTON_WIDTH = 200.dp

/**
 * 配置窗口（默认空间容器）的内容。
 *
 * 这里承接了原先 `GamePage` 里 `start_screen` / `appearance_settings` 两个面板的全部
 * 内容，外加一个随机 tetromino 的 3D 预览。点「开始游戏」才 `openStage` 进全沉浸。
 *
 * **为什么难度走 Bundle 而不是 `GameSettings` 单例**：两个容器各绑一个 Activity，
 * 无法确定同进程；真跨进程时单例会静默退回默认值。`openStage(bundle = …)` 是官方
 * 的跨容器传参通道。
 *
 * **为什么 `launching` 不由 `sceneReady` 驱动**：棋盘在 Stage 那一侧建，这边根本看不到
 * 它的进度。按下开始就置 `true`，一直到窗口重新拿回焦点（= 从游戏返回了）才置回。
 */
@Composable
fun ConfigPage() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()

    val highScoreStore = remember { SharedPreferencesHighScoreStore(context) }
    val basePlateMaterialStore = remember { SharedPreferencesBasePlateMaterialStore(context) }
    val pieceMaterialStore = remember { SharedPreferencesPieceMaterialStore(context) }
    val baseMaterialsBundle = remember { BaseMaterialsBundle() }
    val basePlateMaterialLoader = remember { BasePlateMaterialLoader(baseMaterialsBundle) }
    val pieceMaterialLoader = remember { PieceMaterialLoader(baseMaterialsBundle) }
    val preview = remember { PiecePreviewRenderer() }
    // 预览的 4 个方块与底板都挂在它下面，位置只在这一处给。
    val previewRoot = remember { Entity() }

    var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var selectedBasePlateMaterial by remember { mutableStateOf(basePlateMaterialStore.get()) }
    var selectedPieceMaterial by remember { mutableStateOf(pieceMaterialStore.get()) }
    var showAppearanceSettings by remember { mutableStateOf(false) }
    var showGameplayInfo by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf(false) }
    var highScore by remember { mutableStateOf(highScoreStore.highScore()) }
    var previewPieceType by remember { mutableStateOf(PieceType.entries.random()) }

    // 窗口重新获得焦点 = 刚从游戏返回（或首帧）。这是唯一可靠的"我回来了"信号：
    // Stage 那边写完最高分就 closeStage 了，没有任何跨容器回调能通知这边刷新。
    val isFocused by LocalSpatialContainerStateManager.current.isFocused
    LaunchedEffect(isFocused) {
        if (!isFocused) return@LaunchedEffect
        highScore = highScoreStore.highScore()
        previewPieceType = PieceType.entries.random()
        launching = false
        Log.i(CONFIG_LOG_TAG, "config window focused: highScore=$highScore preview=$previewPieceType")
    }

    DisposableEffect(Unit) {
        onDispose {
            // AssetBundle 是 Closeable，且这边和 GamePage 各持有自己的一份
            // BaseMaterialsBundle（两个容器是两套独立的 Compose 组合）。
            baseMaterialsBundle.close()
        }
    }

    // 预览方块的材质跟随「方块材质」选择。isAttached 守卫和 GamePage 里的两个
    // 同类 effect 同理：这个 effect 在首次组合时就会跑一遍，而 SpatialView 的
    // initial 可能还没建出实体。
    LaunchedEffect(selectedPieceMaterial) {
        if (!preview.isAttached) return@LaunchedEffect
        preview.setPieceMaterial(pieceMaterialLoader.load(selectedPieceMaterial).getValue(previewPieceType))
    }

    LaunchedEffect(selectedBasePlateMaterial) {
        if (!preview.isAttached) return@LaunchedEffect
        preview.setPlateMaterial(basePlateMaterialLoader.load(selectedBasePlateMaterial))
    }

    // 重摇到新形状时，方块位置要跟着改；材质也要重新按新 type 取一次
    // （PieceMaterialLoader 返回的是 Map<PieceType, Material>）。
    LaunchedEffect(previewPieceType) {
        if (!preview.isAttached) return@LaunchedEffect
        preview.setPieceType(previewPieceType)
        preview.setPieceMaterial(pieceMaterialLoader.load(selectedPieceMaterial).getValue(previewPieceType))
    }

    fun startGame() {
        // 双保险：按钮本身已经 enabled = !launching，这里再拦一次，避免快速连点
        // 在同一帧内发出两次 openStage。
        if (launching) return
        launching = true
        showGameplayInfo = false
        showAppearanceSettings = false
        scope.launch {
            val result = navigator.openStage(
                id = GAME_STAGE_ID,
                // Mixed：虚拟内容始终渲染，环境光完全来自真实房间的 VST，不需要自备
                // skybox/IBL。这是改造前默认 Stage 用的同一个 style（manifest 里的
                // pico.spatial.stage.style="1"），迁到非默认 Stage 后只能在这里给：
                // Stage() 这个 DSL 函数没有 style 参数。
                style = StageStyle.Mixed,
                bundle = Bundle().apply { putString(DIFFICULTY_BUNDLE_KEY, selectedDifficulty.name) },
            )
            // 结果必须记日志：openStage 失败在画面上和"棋盘还在建"完全一样，
            // 没有日志就分不出是哪一种。
            Log.i(CONFIG_LOG_TAG, "openStage($GAME_STAGE_ID) -> $result")
        }
    }

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        initial = { content, attachments ->
            content.addEntity(previewRoot)
            previewRoot.components[TransformComponent::class.java]
                ?.setPosition(Vector3(0f, PREVIEW_ROOT_Y_M, PREVIEW_ROOT_Z_M))

            fun attach(id: String, position: Vector3) {
                attachments.entity(id = id)?.apply {
                    components[TransformComponent::class.java]?.setPosition(position)
                    // AttachmentPanel 只有被显式加进渲染树才可见 —— attachments.entity(id)
                    // 本身不会添加它。这是本项目踩过的第 2 号已知坑。
                    content.addEntity(this)
                }
            }

            // 先绑 2D 面板再 yield，让卡片有机会先画出来，然后才去建预览实体。
            // 预览只有 5 个实体（远不是棋盘那 184 个），但材质加载要开 ~25MB 的
            // AssetBundle，第一次可能是秒级。
            attach("config_card", Vector3(0f, CONFIG_CARD_Y_M, CONFIG_CARD_Z_M))
            attach("appearance_settings", Vector3(0f, CONFIG_CARD_Y_M, CONFIG_CARD_Z_M))
            yield()

            val pieceMaterials = pieceMaterialLoader.load(selectedPieceMaterial)
            preview.attachTo(
                parent = previewRoot,
                type = previewPieceType,
                pieceMaterial = pieceMaterials.getValue(previewPieceType),
                plateMaterial = basePlateMaterialLoader.load(selectedBasePlateMaterial),
            )
            Log.i(CONFIG_LOG_TAG, "preview attached: type=$previewPieceType")
        },
        attachments = {
            AttachmentPanel(id = "config_card") {
                if (!showAppearanceSettings) {
                    Box {
                        CandyCard(modifier = Modifier.align(Alignment.Center)) {
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { showGameplayInfo = true },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        size = ButtonDefaults.Small,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CandyAccentMint.copy(alpha = GAMEPLAY_BUTTON_CONTAINER_ALPHA),
                                            contentColor = CandyAccentMintInk,
                                        ),
                                        leadingIcon = { GameplayButtonBadge() },
                                    ) { Text("玩法") }
                                }
                                Text(
                                    text = "空间方块",
                                    color = CandyCardInk,
                                    style = PicoTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "历史最高 $highScore",
                                    color = CandyCardInkDim,
                                    style = PicoTheme.typography.bodyMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf(Difficulty.SLOW to "慢", Difficulty.NORMAL to "中", Difficulty.FAST to "快")
                                        .forEach { (difficulty, label) ->
                                            Button(
                                                onClick = { selectedDifficulty = difficulty },
                                                colors = candyButtonColors(primary = difficulty == selectedDifficulty),
                                            ) { Text(label) }
                                        }
                                }
                                Button(
                                    onClick = { showAppearanceSettings = true },
                                    colors = candyButtonColors(primary = false),
                                ) { Text("外观设置") }
                                Text(
                                    text = "旋转：注视方块，双击旋转方块",
                                    color = CandyCardInkDim,
                                    style = PicoTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "移动：手指捏合移动控制方块方向",
                                    color = CandyCardInkDim,
                                    style = PicoTheme.typography.bodySmall,
                                )
                                Button(
                                    modifier = Modifier.width(START_BUTTON_WIDTH),
                                    enabled = !launching,
                                    onClick = { startGame() },
                                    colors = candyButtonColors(primary = !launching),
                                ) {
                                    Text(if (launching) "加载中" else "开始游戏")
                                }
                            }
                        }

                        if (showGameplayInfo) {
                            GameplayInfoOverlay(onDismiss = { showGameplayInfo = false })
                        }
                    }
                }
            }
            AttachmentPanel(id = "appearance_settings") {
                if (showAppearanceSettings) {
                    CandyCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "外观设置",
                                color = CandyCardInk,
                                style = PicoTheme.typography.titleMedium,
                            )
                            BasePlateMaterialPicker(
                                selected = selectedBasePlateMaterial,
                                onSelect = { material ->
                                    selectedBasePlateMaterial = material
                                    basePlateMaterialStore.set(material)
                                },
                            )
                            PieceMaterialPicker(
                                selected = selectedPieceMaterial,
                                onSelect = { material ->
                                    selectedPieceMaterial = material
                                    pieceMaterialStore.set(material)
                                },
                            )
                            Button(
                                onClick = { showAppearanceSettings = false },
                                colors = candyButtonColors(primary = true),
                            ) { Text("完成") }
                        }
                    }
                }
            }
        },
    )
}
