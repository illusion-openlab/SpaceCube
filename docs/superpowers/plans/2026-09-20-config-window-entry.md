# 配置窗口入口 + 按需全沉浸 Stage 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 SpaceCube 的默认空间容器从全沉浸 `Stage` 改成 Volumetric 配置窗口，窗口内摆一个随机俄罗斯方块 3D 预览，点「开始游戏」才 `openStage` 进全沉浸。

**Architecture:** 默认容器改为 `DefaultWindowContainer`（manifest 配 Volumetric），游戏 `Stage` 降级为 DSL 声明的非默认容器，靠 `LocalSpatialNavigator.openStage(id, style, bundle)` 按需打开。难度经 `openStage` 的 `Bundle` 跨容器传递；棋盘就绪后 Stage 侧 `minimizeWindowContainer` 收起配置窗口；返回时先 `restoreWindowContainer` 再 `closeStage`（顺序不可交换）。

**Tech Stack:** Kotlin / Jetpack Compose / PICO Spatial SDK 6.0.0（`spatialBom 6.0.0`）/ SpatialUI（`com.pico.spatial.ui.design.*`）/ JUnit4。

## Global Constraints

- **UI 一律 SpatialUI + `PicoTheme`**，禁止 `androidx.compose.material` / `material3` / `MaterialTheme`。本项目另有自己的 `CandyCard` 卡片体系（`content/CandyPanelTheme.kt`），新 UI 复用它，不新增色值。
- **构建必须用 JDK 17**：`JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew …`。本机默认是 JDK 25，与当前 Gradle/AGP 不兼容。**不要把 JDK 路径写死**（Homebrew 升级会让写死的路径失效，且 `JAVA_HOME` 指向不存在的目录不报错，Gradle 会静默回退到新 JDK）。
- **`game/` 包保持框架无关**：不得引入任何 `com.pico.*` 或 `android.*` 导入，否则 JVM 单测跑不起来。
- **契约基准**：`.spatialsdk/design-contract.md` v6 增量。文案一律逐字沿用现有实现，本次**无新增文案**。
- **容器 id 必须与 manifest 一致**：`CONFIG_WINDOW_ID = "SpaceCubeConfigWindow"`、`GAME_STAGE_ID = "SpaceCubeStage"`。
- **不得发明 API**。以下签名已核对自 SDK 6.0.0 sources jar，照抄即可：
  - `fun SpatialAppScope.DefaultWindowContainer(content: @Composable WindowContainerScope.() -> Unit)`（`com.pico.spatial.ui.foundation.dsl`，**无任何属性参数**，属性只能写 manifest）
  - `fun SpatialAppScope.Stage(id: String, immersion: Immersion? = null, brightness: Brightness? = null, upperLimbRenderMode: UpperLimbRenderMode? = null, targetActivity: Class<out ComponentActivity>? = null, content: @Composable StageScope.() -> Unit)`（同包，**没有 `style` 参数** —— style 只能经 `openStage(style = …)` 或 manifest 给）
  - `suspend fun openStage(id: String, style: StageStyle? = null, bundle: Bundle? = null, upperLimbRenderMode: UpperLimbRenderMode = UpperLimbRenderMode.Default): OpenStageResult`
  - `suspend fun closeStage()`
  - `fun minimizeWindowContainer(id: String, tag: String? = null): Boolean`
  - `fun restoreWindowContainer(id: String, tag: String? = null): Boolean`
  - `sealed class OpenStageResult { object Allowed; object NotAllowed; class Error(val code: Int, val reason: String) }`
  - `import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator`
  - `import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager`（注意：**不是** `.containers` 子包）
  - `SpatialContainerScope.bundle: Bundle?`（`StageScope` / `WindowContainerScope` 都继承它）
- **`restoreWindowContainer` 和 `minimizeWindowContainer` 的 KDoc 都写着 "can only be used when there is a stage open"**。返回路径必须 **先 restore、后 closeStage**，写反了没有任何 API 能把最小化的窗口叫回来。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `app/src/main/java/tech/illusion/spacecube/game/PiecePreviewLayout.kt` | **新增**。纯函数：把 `PieceType.spawnCells` 换算成预览用的居中/落底偏移。框架无关，可单测 |
| `app/src/main/java/tech/illusion/spacecube/game/GameSettings.kt` | **改**。新增 `DIFFICULTY_BUNDLE_KEY` 与 `parseDifficulty`。框架无关，可单测 |
| `app/src/main/java/tech/illusion/spacecube/content/Containers.kt` | **新增**。两个容器 id 常量，manifest 与 DSL 的唯一真源 |
| `app/src/main/java/tech/illusion/spacecube/content/GameplayInfo.kt` | **新增**。从 `GamePage.kt` 原样搬出「玩法」按钮徽标、说明弹层与全部文案常量，可见性 `private` → `internal`，好让 `ConfigPage` 也能用 |
| `app/src/main/java/tech/illusion/spacecube/content/PiecePreviewRenderer.kt` | **新增**。配置窗口里那 4 个方块 + 1 块底板的 ECS 构建与材质切换 |
| `app/src/main/java/tech/illusion/spacecube/content/ConfigPage.kt` | **新增**。配置窗口的全部内容与状态，以及 `openStage` 调用 |
| `app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt` | **改**。分阶段计时埋点 + L1 共享 mesh |
| `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt` | **改**。删开始界面/外观设置/玩法弹层；读 bundle；就绪后 minimize；返回/退出走 restore+closeStage |
| `app/src/main/java/tech/illusion/spacecube/Main.kt` | **改**。双容器声明 |
| `app/src/main/AndroidManifest.xml` | **改**。stage meta → windowcontainer meta |
| `app/src/test/java/tech/illusion/spacecube/game/PiecePreviewLayoutTest.kt` | **新增** |
| `app/src/test/java/tech/illusion/spacecube/game/GameSettingsTest.kt` | **新增** |

`game/` 包一行业务逻辑都不动（`Board`/`GameEngine`/`PieceBag`/`Scoring`/`FallingPiece`/`PieceType`），既有的 38 个单测必须原样通过。Task 1 会把总数加到 47 —— **从 Task 2 起，「不回归」的基准就是 47**。

---

### Task 1: 纯逻辑 —— 预览体布局与难度跨容器解析

这是整个改动里唯一能用 JVM 单测覆盖的部分，先做，后面的 UI/容器代码直接调用它。

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/game/PiecePreviewLayout.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/game/GameSettings.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/PiecePreviewLayoutTest.kt`
- Test: `app/src/test/java/tech/illusion/spacecube/game/GameSettingsTest.kt`

**Interfaces:**
- Consumes: `PieceType.spawnCells: List<Pair<Int, Int>>`（(row, col)，row 0 是**顶**行）、`Difficulty`（`SLOW`/`NORMAL`/`FAST`）
- Produces:
  - `data class PreviewCell(val x: Float, val y: Float)`
  - `fun previewCellOffsets(type: PieceType): List<PreviewCell>` —— 恒返回 4 个；x 相对包围盒水平居中；y 从底行起算、底行恒为 `0f`
  - `const val DIFFICULTY_BUNDLE_KEY: String`
  - `fun parseDifficulty(name: String?): Difficulty`

- [ ] **Step 1: 写失败的测试 —— 预览布局**

创建 `app/src/test/java/tech/illusion/spacecube/game/PiecePreviewLayoutTest.kt`：

```kotlin
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
```

- [ ] **Step 2: 跑测试，确认它因为「函数不存在」而失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests '*PiecePreviewLayoutTest*'
```

Expected: 编译失败，报 `Unresolved reference: previewCellOffsets`。

- [ ] **Step 3: 写最小实现**

创建 `app/src/main/java/tech/illusion/spacecube/game/PiecePreviewLayout.kt`：

```kotlin
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
```

- [ ] **Step 4: 跑测试，确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests '*PiecePreviewLayoutTest*'
```

Expected: PASS，5 个测试全绿。

- [ ] **Step 5: 写失败的测试 —— 难度跨容器解析**

创建 `app/src/test/java/tech/illusion/spacecube/game/GameSettingsTest.kt`：

```kotlin
package tech.illusion.spacecube.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsTest {
    @Test
    fun `every difficulty round-trips through its own name`() {
        for (difficulty in Difficulty.values()) {
            assertEquals("round trip for $difficulty", difficulty, parseDifficulty(difficulty.name))
        }
    }

    @Test
    fun `a missing name falls back to normal`() {
        assertEquals(Difficulty.NORMAL, parseDifficulty(null))
    }

    @Test
    fun `an unrecognised name falls back to normal`() {
        assertEquals(Difficulty.NORMAL, parseDifficulty("TURBO"))
    }

    @Test
    fun `an empty name falls back to normal`() {
        assertEquals(Difficulty.NORMAL, parseDifficulty(""))
    }
}
```

- [ ] **Step 6: 跑测试，确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests '*GameSettingsTest*'
```

Expected: 编译失败，报 `Unresolved reference: parseDifficulty`。

- [ ] **Step 7: 写最小实现**

把 `app/src/main/java/tech/illusion/spacecube/game/GameSettings.kt` 整个替换成：

```kotlin
package tech.illusion.spacecube.game

object GameSettings {
    var difficulty: Difficulty = Difficulty.NORMAL
}

/**
 * 配置窗口经 `openStage` 的 `Bundle` 把难度交给游戏 Stage 时用的 key。
 *
 * 用 Bundle 而不是直接写 [GameSettings] 单例：两个容器各自绑一个 Activity，
 * 无法确定它们一定同进程；真跨进程时单例会**静默**退回默认值，是一种不报错的错误。
 */
const val DIFFICULTY_BUNDLE_KEY = "spacecube.difficulty"

/**
 * 把 Bundle 里带过来的难度名解析回 [Difficulty]。
 *
 * 名字缺失或不认识时退回 [Difficulty.NORMAL] 而不是抛异常：这个值跨了容器
 * （可能还跨进程）边界，"以中等速度开局" 比 "启动即崩" 是更好的失败方式。
 */
fun parseDifficulty(name: String?): Difficulty =
    Difficulty.entries.firstOrNull { it.name == name } ?: Difficulty.NORMAL
```

- [ ] **Step 8: 跑全部单测，确认新旧都通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
```

Expected: PASS。原有 38 个 + 新增 9 个 = 47 个测试全绿。若数量对不上，说明有测试被 Gradle 判成 up-to-date 跳过了，加 `--rerun-tasks` 复核一次。

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/tech/illusion/spacecube/game/PiecePreviewLayout.kt \
        app/src/main/java/tech/illusion/spacecube/game/GameSettings.kt \
        app/src/test/java/tech/illusion/spacecube/game/PiecePreviewLayoutTest.kt \
        app/src/test/java/tech/illusion/spacecube/game/GameSettingsTest.kt
git commit -m "feat: add preview layout math and cross-container difficulty parsing"
```

---

### Task 2: BoardCubeRenderer —— 分阶段计时埋点 + L1 共享 mesh

这一步同时回答「60–95s 花在哪」和「能不能砍」。SDK 没有可单测的替身，验收靠**编译通过 + 47 个单测不回归**（38 个原有 + Task 1 新增的 9 个），真实数字在 Task 9 的门禁 B 第 2 轮从 logcat 取。

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt`

**Interfaces:**
- Consumes: 无新增
- Produces: logcat tag `SpaceCubeBoardBuild` 下一条形如
  `board build phases: material=…ms mesh=…ms entity=…ms addChild=…ms cubes=…` 的汇总日志

- [ ] **Step 1: 加日志 tag 与 import**

在 `BoardCubeRenderer.kt` 顶部 import 区加上（文件目前没有 import `android.util.Log`）：

```kotlin
import android.util.Log
```

在 `private const val CELL_SIZE_M = 0.05f` 上方加：

```kotlin
// 冷启动构建耗时的分阶段埋点（2026-09-20）。AGENTS.md 记了整体 60-95s，但
// 从没量过是 material / mesh / entity / addChild 哪一步占大头，于是"怎么优化"
// 一直只能猜。这条日志把它变成可引用的实测数字。
private const val BOARD_BUILD_LOG_TAG = "SpaceCubeBoardBuild"
```

- [ ] **Step 2: 在类里加计时字段与共享 mesh 字段**

在 `BoardCubeRenderer` 类内、`private var groundEntity: ModelEntity? = null` 附近加：

```kotlin
// createCube() 的四步各自累计耗时，attachTo() 开头清零、结尾打一条汇总。
private var materialNanos = 0L
private var meshNanos = 0L
private var entityNanos = 0L
private var addChildNanos = 0L
private var cubesCreated = 0

// L1（2026-09-20）：184 个池化方块几何完全相同，box mesh 只建一次然后共享，
// 而不是每个方块重建一次。SDK mesh 指南（spatial-sdk_resource-management_mesh.md
// L243-244）原话就是 "avoid diversifying meshes or materials as much as possible"。
// 共享是安全的：这些方块是池化的，从创建到进程结束都不会被 destroy，所以不存在
// 某个兄弟实体被销毁时把共享 mesh 一起释放掉的情况（会被 destroy 的只有底板，
// 而底板用的是另一个尺寸的 mesh，不走这里）。
// 要回滚：把 cubeMesh() 调用换回原来的 MeshResource.createBox(...) 内联写法即可。
private var sharedCubeMesh: MeshResource? = null

private fun cubeMesh(): MeshResource =
    sharedCubeMesh ?: MeshResource.createBox(
        Vector3(CELL_SIZE_M, CELL_SIZE_M, CELL_SIZE_M),
        cornerRadius = 0.008f,
    ).also { sharedCubeMesh = it }
```

- [ ] **Step 3: 改 createCube() 走共享 mesh 并计时**

把 `createCube()` 整个替换成：

```kotlin
private fun createCube(anchor: Entity, blendingMode: BlendingMode, opacity: Float): Cube {
    val t0 = System.nanoTime()
    val material = UnlitMaterial.create(blendingMode).apply { setOpacity(opacity) }
    val t1 = System.nanoTime()
    val mesh = cubeMesh()
    val t2 = System.nanoTime()
    val entity = ModelEntity(mesh, material).apply { enabled = false }
    val t3 = System.nanoTime()
    anchor.addChild(entity)
    val t4 = System.nanoTime()
    materialNanos += t1 - t0
    meshNanos += t2 - t1
    entityNanos += t3 - t2
    addChildNanos += t4 - t3
    cubesCreated++
    return Cube(entity, material)
}
```

- [ ] **Step 4: attachTo() 开头清零、结尾汇总**

在 `suspend fun attachTo(anchor: Entity, groundMaterial: Material) {` 的第一行加：

```kotlin
    materialNanos = 0L
    meshNanos = 0L
    entityNanos = 0L
    addChildNanos = 0L
    cubesCreated = 0
```

在同一函数末尾、`attachGround(anchor, groundMaterial)` 之后加：

```kotlin
    Log.i(
        BOARD_BUILD_LOG_TAG,
        "board build phases: material=${materialNanos / 1_000_000}ms " +
            "mesh=${meshNanos / 1_000_000}ms " +
            "entity=${entityNanos / 1_000_000}ms " +
            "addChild=${addChildNanos / 1_000_000}ms " +
            "cubes=$cubesCreated",
    )
```

- [ ] **Step 5: 编译 + 跑单测，确认无回归**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL，47 个单测全绿。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/BoardCubeRenderer.kt
git commit -m "perf: share one box mesh across pooled cubes and instrument board build phases"
```

---

### Task 3: 把「玩法」说明层抽成共享文件

`GameplayInfoOverlay` 等目前是 `GamePage.kt` 里的 `private`，配置窗口用不到。整体搬到独立文件并改 `internal`。**纯搬运，不改任何一个字的文案、色值、尺寸。**

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/GameplayInfo.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Produces（供 `ConfigPage` 与 `GamePage` 共用）：
  - `internal const val GAMEPLAY_BUTTON_CONTAINER_ALPHA: Float`
  - `internal fun GameplayButtonBadge()`（`@Composable`）
  - `internal fun BoxScope.GameplayInfoOverlay(onDismiss: () -> Unit)`（`@Composable`）

- [ ] **Step 1: 建新文件并搬运**

创建 `app/src/main/java/tech/illusion/spacecube/content/GameplayInfo.kt`，package 为 `tech.illusion.spacecube.content`，把 `GamePage.kt` 中**从 `private const val GAMEPLAY_BUTTON_CONTAINER_ALPHA` 那一行起、到 `GameplayInfoOverlay` 函数的收尾 `}` 为止**（含中间的 `GAMEPLAY_BADGE_FILL_ALPHA`、`GAMEPLAY_SCRIM_ALPHA`、`GAMEPLAY_OVERLAY_CARD_WIDTH`、`GAMEPLAY_OVERLAY_SCROLL_MAX_HEIGHT`、`GAMEPLAY_INFO_INTRO_TEXT`、`GAMEPLAY_INFO_CONTROLS_LINES`、`GAMEPLAY_INFO_CONTROLLER_LINES`、`GAMEPLAY_INFO_RULES_LINES`、`GameplayButtonBadge`、`GameplayInfoSection`、`GameplayInfoOverlay` 及它们各自上方的全部注释/KDoc）整段剪切过来。

改动只有两类，**不许有第三类**：
1. 三个对外的符号 `GAMEPLAY_BUTTON_CONTAINER_ALPHA`、`GameplayButtonBadge`、`GameplayInfoOverlay` 的 `private` 改 `internal`。其余常量与 `GameplayInfoSection` **保持 `private`**（只在本文件内用）。
2. 补齐新文件需要的 import。

新文件需要的 import 清单（照抄）：

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
```

- [ ] **Step 2: 清理 GamePage.kt**

删掉刚搬走的那一整段。**先不要动 `GamePage.kt` 里对它们的调用**（第 1464 行附近的 `GAMEPLAY_BUTTON_CONTAINER_ALPHA` / `GameplayButtonBadge()`、第 1531 行附近的 `GameplayInfoOverlay(...)`）——同包 `internal` 可见，调用处一个字都不用改。

删完后用 Kotlin 编译器帮你找残留：`GamePage.kt` 里若还有只被搬走的代码用到的 import（例如 `rememberScrollState`、`verticalScroll`、`CircleShape`、`heightIn`、`size`），会变成"未使用的 import"警告而不是错误。**不要凭眼睛猜**，跑 Step 3 之后按编译器的警告删。

- [ ] **Step 3: 编译，确认零错误**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。若报 `Unresolved reference`，说明有常量/函数漏搬或可见性还是 `private`。

- [ ] **Step 4: 删除 GamePage.kt 里编译器报出的未使用 import**

再跑一次 Step 3 确认仍然 BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GameplayInfo.kt \
        app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "refactor: extract the gameplay-info overlay so both containers can use it"
```

---

### Task 4: PiecePreviewRenderer —— 配置窗口里的 3D 方块

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/PiecePreviewRenderer.kt`

**Interfaces:**
- Consumes: `previewCellOffsets(type): List<PreviewCell>`、`PreviewCell(x, y)`（Task 1）
- Produces:
  - `internal class PiecePreviewRenderer`
  - `fun attachTo(parent: Entity, type: PieceType, pieceMaterial: Material, plateMaterial: Material)`
  - `fun setPieceType(type: PieceType)`
  - `fun setPieceMaterial(material: Material)`
  - `fun setPlateMaterial(material: Material)`
  - `val isAttached: Boolean`

- [ ] **Step 1: 写整个文件**

创建 `app/src/main/java/tech/illusion/spacecube/content/PiecePreviewRenderer.kt`：

```kotlin
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
```

- [ ] **Step 2: 编译**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。这个文件此刻还没有调用方，只验证它自己能编过。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/PiecePreviewRenderer.kt
git commit -m "feat: add the config window's single-piece 3D preview renderer"
```

---

### Task 5: ConfigPage —— 配置窗口的内容与状态

**Files:**
- Create: `app/src/main/java/tech/illusion/spacecube/content/Containers.kt`
- Create: `app/src/main/java/tech/illusion/spacecube/content/ConfigPage.kt`

**Interfaces:**
- Consumes: `PiecePreviewRenderer`（Task 4）、`GameplayButtonBadge`/`GameplayInfoOverlay`/`GAMEPLAY_BUTTON_CONTAINER_ALPHA`（Task 3）、`DIFFICULTY_BUNDLE_KEY`（Task 1）、既有的 `CandyCard`/`candyButtonColors`/`BasePlateMaterialPicker`/`PieceMaterialPicker`/三个 `SharedPreferences*Store`/两个 `*MaterialLoader`/`BaseMaterialsBundle`
- Produces: `fun ConfigPage()`（`@Composable`）、`const val CONFIG_WINDOW_ID`、`const val GAME_STAGE_ID`

- [ ] **Step 1: 写容器 id 常量文件**

创建 `app/src/main/java/tech/illusion/spacecube/content/Containers.kt`：

```kotlin
package tech.illusion.spacecube.content

/**
 * 配置窗口（默认空间容器）的 id。
 *
 * **必须与 `AndroidManifest.xml` 里 `pico.spatial.windowcontainer.id` 的值逐字一致** ——
 * `minimizeWindowContainer(id)` / `restoreWindowContainer(id)` 就是按这个字符串定位窗口的，
 * 对不上不会报错，只会静默什么都不发生。
 */
const val CONFIG_WINDOW_ID = "SpaceCubeConfigWindow"

/**
 * 游戏 Stage 的 id。非默认 Stage 只需在 `mainApp` 的 DSL 里 `Stage(id = …)` 声明，
 * 不必写进 manifest（官方文档 declare-a-stage 明说"can also be declared in
 * AndroidManifest.xml **as needed**"）。
 */
const val GAME_STAGE_ID = "SpaceCubeStage"
```

- [ ] **Step 2: 写 ConfigPage.kt**

创建 `app/src/main/java/tech/illusion/spacecube/content/ConfigPage.kt`：

```kotlin
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
```

- [ ] **Step 3: 编译**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。若 `AttachmentPanel` 报未解析，检查它是不是 `SpatialView` 的 `attachments` lambda 作用域内的成员 —— 参照 `GamePage.kt` 里同名调用的写法。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/Containers.kt \
        app/src/main/java/tech/illusion/spacecube/content/ConfigPage.kt
git commit -m "feat: add the config window page with difficulty, appearance and 3D preview"
```

---

### Task 6: 容器翻转 —— manifest + Main.kt

这一步之后 App 就是"先窗口后 Stage"了。**已知中间态**：`GamePage` 此刻还带着自己的 `start_screen`，所以进 Stage 后会再看到一层开始界面。Task 7 才删。这个中间态是可编译、可运行的，刻意留成独立一步，好让容器翻转本身能被单独 review 和回滚。

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/tech/illusion/spacecube/Main.kt`
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`（只加形参，不动逻辑）

**Interfaces:**
- Consumes: `CONFIG_WINDOW_ID` / `GAME_STAGE_ID`（Task 5）、`ConfigPage()`（Task 5）
- Produces: `fun GamePage(bundle: Bundle?)`

- [ ] **Step 1: 改 manifest**

把 `app/src/main/AndroidManifest.xml` 里 `<activity android:name=".platform.LaunchActivity" …>` 内的这两条连同它们上方那段 "Full-immersion DefaultStage configuration" 注释一起删掉：

```xml
<meta-data android:name="pico.spatial.stage.id" android:value="SpaceCubeStage"/>
<meta-data android:name="pico.spatial.stage.style" android:value="1" />
```

换成：

```xml
<!-- 默认空间容器 = Volumetric 配置窗口（2026-09-20，契约 v6）。
     此前这里是全沉浸 DefaultStage；游戏本体仍跑在 Stage 里，只是降级成了
     由「开始游戏」按钮 openStage 打开的非默认 Stage（id 在 Main.kt 的 DSL 里声明，
     非默认 Stage 不需要 manifest 条目）。
     2026-08-06 之所以迁去 Stage，是因为窗口 chrome 会抢走游戏中的拖拽/点击手势 ——
     那个理由只对"游戏进行中"成立，配置页只有离散点击，所以不冲突。 -->

<!-- 必填：窗口唯一 id。必须与 content/Containers.kt 的 CONFIG_WINDOW_ID 逐字一致 -->
<meta-data android:name="pico.spatial.windowcontainer.id" android:value="SpaceCubeConfigWindow" />

<!-- 窗口形态：'1' = Planar，'2' = Volumetric。
     选 Volumetric：Planar 的深度硬上限是 640dp，3D 方块会被压成浮雕甚至被系统截断；
     官方设计文档也明说"3D 内容是主体时用 Volumetric"。 -->
<meta-data android:name="pico.spatial.windowcontainer.style" android:value="2" />

<!-- 宽x高x深，单位 dp。容纳 CandyCard + 前下方一个 tetromino。门禁 B 第 1 轮截图后可微调 -->
<meta-data android:name="pico.spatial.windowcontainer.defaultsize" android:value="900x760x600" />

<!-- 关掉系统毛玻璃底板：CandyCard 自带不透明底色，再叠一层半透明就是两个共面
     半透明层，是真机摩尔纹的已知诱因。主窗口的毛玻璃只能在 manifest 关，
     DSL 侧没有对应参数。 -->
<meta-data android:name="pico.spatial.windowcontainer.materialbackground" android:value="0" />
```

**不要**设置 `volumebasepanel`：保持默认（交互时显示基座），基座是用户找到窗口边缘去拖动/缩放的视觉依据。

- [ ] **Step 2: 改 Main.kt**

把 `app/src/main/java/tech/illusion/spacecube/Main.kt` 整个替换成：

```kotlin
package tech.illusion.spacecube

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import tech.illusion.spacecube.content.ConfigPage
import tech.illusion.spacecube.content.GAME_STAGE_ID
import tech.illusion.spacecube.content.GamePage

// 默认空间容器是 Volumetric 配置窗口（属性见 AndroidManifest.xml —— DefaultWindowContainer
// 这个 DSL 函数没有任何属性参数，属性只能写 manifest）。游戏本体是一个非默认 Stage：
// 只在这里声明 id 和内容，style 在 ConfigPage 调 openStage 时给（Stage() 没有 style 参数）。
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme { ConfigPage() }
        }
        Stage(id = GAME_STAGE_ID) {
            // bundle 来自 StageScope（继承 SpatialContainerScope），承载 openStage 传来的难度。
            PicoTheme { GamePage(bundle) }
        }
    }
```

- [ ] **Step 3: 给 GamePage 加形参（本步只加形参，不改逻辑）**

`GamePage.kt`：
- 加 import：`import android.os.Bundle`
- 把 `fun GamePage() {` 改成 `fun GamePage(bundle: Bundle?) {`
- 在 `var selectedDifficulty by remember { mutableStateOf(GameSettings.difficulty) }` 这一行**上方**插入：

```kotlin
    // 难度由配置窗口经 openStage 的 Bundle 送进来。解析失败退回 NORMAL —— 见
    // parseDifficulty 的 KDoc。不需要在这里写回 GameSettings：startGame() 本来就会写，
    // 而 startGame() 是唯一一个把难度交给 GameEngine 的地方。
    val stageDifficulty = remember(bundle) { parseDifficulty(bundle?.getString(DIFFICULTY_BUNDLE_KEY)) }
```

并把下一行改成：

```kotlin
    var selectedDifficulty by remember { mutableStateOf(stageDifficulty) }
```

加 import：

```kotlin
import tech.illusion.spacecube.game.DIFFICULTY_BUNDLE_KEY
import tech.illusion.spacecube.game.parseDifficulty
```

- [ ] **Step 4: 编译 + 单测**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL，47 个单测全绿。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/AndroidManifest.xml \
        app/src/main/java/tech/illusion/spacecube/Main.kt \
        app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat: make the config window the default container and the game an on-demand Stage"
```

---

### Task 7: GamePage 瘦身 + 进入/返回时序接线

`started` 这个变量的处置是整个改动里最容易读歧义的一处，逐条写死，**不要自由发挥**。

**Files:**
- Modify: `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`

**Interfaces:**
- Consumes: `CONFIG_WINDOW_ID`（Task 5）、`LocalSpatialNavigator`
- Produces: 无（`GamePage(bundle)` 签名不变）

**改动明细表** —— 每一行都要落实：

| 原有用法 | 改为 |
|---|---|
| `AttachmentPanel(id = "start_screen") { … }` 整块 | **删除** |
| `AttachmentPanel(id = "appearance_settings") { … }` 整块 | **删除** |
| `initial` 里 `attach("start_screen", …)` / `attach("appearance_settings", …)` | **删除**（`attach("exit_confirm_overlay", …)` **保留**，Back 键随时可能触发） |
| `var started by remember { mutableStateOf(false) }` | **删除这个变量** |
| `var showAppearanceSettings` / `var showGameplayInfo` | **删除**（两者都迁去了 `ConfigPage`） |
| `score_hud` / `next_piece` / `pause_button` / `pause_overlay` / `game_over_overlay` 里的 `if (started …)` | 改成 `if (sceneReady …)`，其余条件原样保留 |
| `val isPlaying = started && snapshot.state == GameState.PLAYING` | `val isPlaying = sceneReady && snapshot.state == GameState.PLAYING` |
| `LaunchedEffect(engine, started)` 的 key 与 `if (!started) return@LaunchedEffect` | 改为 `LaunchedEffect(engine, sceneReady)` 与 `if (!sceneReady) return@LaunchedEffect` |
| `fun startGame()` | **保留**，但删掉里面的 `showGameplayInfo = false` 和 `started = true` 两行 |
| `OnBackPressedCallback` 里的 `showGameplayInfo` / `showAppearanceSettings` 两个分支 | **删除** |
| `OnBackPressedCallback` 里 `pausedForExitConfirm = started && …` | `pausedForExitConfirm = sceneReady && …` |
| `game_over_overlay` 的「返回开始画面」`onClick = { started = false }` | 改为 restore + closeStage（见 Step 5） |
| `exit_confirm_overlay` 的「退出」`onClick = { activity?.finish() }` | 改为同一个 restore + closeStage |
| `LaunchedEffect(selectedBasePlateMaterial)` 与 `LaunchedEffect(selectedPieceMaterial)` 两个 effect | **删除**。Stage 里已经没有外观设置面板，没有任何东西能在 Stage 存活期间改这两个值，这两个 effect 及 `initial` 里配套的"若已变则重新解析"分支都成了死代码 |
| `initial` 里 `if (selectedBasePlateMaterial != basePlateMaterialAtAttach) { … }` 与 `if (selectedPieceMaterial != initialPieceMaterial) { … }` 两个分支 | **删除**（连同上面那条） |
| `var selectedBasePlateMaterial by remember { mutableStateOf(store.get()) }` | 改为 `val selectedBasePlateMaterial = remember { basePlateMaterialStore.get() }`；`selectedPieceMaterial` 同理 |
| `game_over_overlay` 的「再来一局」`onClick = { startGame() }` | **不改，保留**。`startGame()` 仍然存在，删掉的只是它里面写 `started` 的那一行。留在 Stage 内直接重开，不回窗口、不重新构建棋盘 —— 这是门禁 A 拍板的行为，别顺手把它也改成 `returnToConfigWindow()` |
| `HandGestureController(… isPlaying = isPlaying …)` | **不改**。场景自由拖拽本来就由 `isPlaying` 取反门控，`isPlaying` 的定义已按上面改成基于 `sceneReady`，于是"加载中 / 暂停中 / 游戏结束都能拖"自动成立 |

- [ ] **Step 1: 在 GamePage 顶部拿到 navigator 与协程作用域**

在 `val context = LocalContext.current` 下方加：

```kotlin
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
```

加 import：

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.launch
```

- [ ] **Step 2: 加"回到配置窗口"的统一出口**

在 `fun startGame()` 定义的**上方**加：

```kotlin
    // 回到配置窗口的唯一出口。顺序不可交换：restoreWindowContainer 的 KDoc 写着
    // "can only be used when there is a stage open"，先 closeStage 的话，最小化的
    // 配置窗口就再也没有 API 能叫回来了。
    // 两个返回值都记日志：这两步失败在画面上和"什么都没发生"一模一样，截图判不出来。
    fun returnToConfigWindow() {
        val restored = navigator.restoreWindowContainer(CONFIG_WINDOW_ID)
        Log.i(HAND_GESTURE_LOG_TAG, "restoreWindowContainer($CONFIG_WINDOW_ID) -> $restored")
        scope.launch {
            navigator.closeStage()
            Log.i(HAND_GESTURE_LOG_TAG, "closeStage() returned")
        }
    }
```

- [ ] **Step 3: 按上面的明细表逐行改 GamePage**

逐条落实，一条都不要漏。`startGame()` 改完应该是：

```kotlin
    fun startGame() {
        GameSettings.difficulty = selectedDifficulty
        engine.start(selectedDifficulty)
        snapshot = engine.snapshot()
        // 不显式 render()：engine.start() 会 bump revision，帧循环下一帧就会画。
    }
```

- [ ] **Step 4: 在 initial 末尾自动开局并收起配置窗口**

在 `initial` lambda 的**最末尾**（`attach("game_over_overlay", …)` 之后）加：

```kotlin
            // Stage 一打开就是"要玩"，不再有开始界面态：棋盘建好就直接开局。
            startGame()
            // 然后才收起配置窗口。整个 60-95s 的构建期间窗口一直留在那儿显示
            // "加载中"，用户看着熟悉的卡片而不是空房间 —— 这是门禁 A 拍板的方案。
            // 返回值必须记日志：截图判不出 minimize 到底成没成功。
            val minimized = navigator.minimizeWindowContainer(CONFIG_WINDOW_ID)
            Log.i(HAND_GESTURE_LOG_TAG, "minimizeWindowContainer($CONFIG_WINDOW_ID) -> $minimized")
```

- [ ] **Step 5: 改两个返回出口**

`game_over_overlay` 里：

```kotlin
                                Button(
                                    onClick = { returnToConfigWindow() },
                                    colors = candyButtonColors(primary = false),
                                ) { Text("返回开始画面") }
```

`exit_confirm_overlay` 里，把 `onClick = { activity?.finish() }` 改成：

```kotlin
                                    onClick = { returnToConfigWindow() },
```

同时把该面板的说明文案从「当前进度将不会保存」**保持原样**（契约 v6 增量 4：本次无新增/修改文案）。

- [ ] **Step 6: 编译 + 单测**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL，47 个单测全绿。若报 `started` 未解析，说明明细表里还有一处没改到 —— 用 `grep -n 'started' app/src/main/java/tech/illusion/spacecube/content/GamePage.kt` 兜一遍，应该零命中。

- [ ] **Step 7: 确认 Material 禁令仍然成立**

```bash
grep -rn "androidx.compose.material\|MaterialTheme" app/src/main/java/ || echo "clean: no Material/Material3"
```

Expected: 打印 `clean: no Material/Material3`。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/tech/illusion/spacecube/content/GamePage.kt
git commit -m "feat: drop the in-Stage start screen and wire the window/Stage round trip"
```

---

### Task 8: 门禁 B 第 1 轮 —— 配置窗口截图验证

> **两轮通用的 `adb tap` 警告**（本项目实测，别重新踩）：
> - 模拟器上手势验证不可靠，`adb shell input tap` 能摸到输入管线，但**点不准按钮**，而且**有可能把沉浸态关掉**。
> - 所以坐标不要凭窗口尺寸推算，一律从**上一张截图里量**。
> - 每次 tap 之后都要用日志确认它真的生效了（配置页看 `SpaceCubeConfig`，进游戏看 `SpaceCubeBoardBuild`），**不要只靠下一张截图判断**。
> - tap 连点两次没反应就停手，记「不可判定（模拟器点击不可靠）」，不要反复试着占设备。


契约 v6 增量 6 的第 1 轮。**锁内只跑命令，不做任何分析。**

**Files:**
- Modify: `.spatialsdk/ui-verification-log.md`（追加本轮记录）

- [ ] **Step 1: 锁外准备**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
ls -l app/build/outputs/apk/debug/app-debug.apk
grep -rn "applicationId" app/build.gradle.kts
```

确认 APK 绝对路径与 `applicationId`（应为 `tech.illusion.spacecube`）。本会话若还没跑过环境预检，先按 `pico-env-doctor` 做一次；已跑过就复用结果。

本轮检查清单（来自契约 v6 增量 6，2 张图）：
1. 启动默认态：是**窗口**不是全沉浸；config_card 六项元素齐全；预览**恰好一个** tetromino（4 个方块）完整落在窗口内、不被截断、不与卡片重叠；底板在其正下方。
2. 外观设置切材质：面板正常弹出、两个 Picker 齐全；关闭后预览方块材质肉眼可辨地变了。

- [ ] **Step 2: 锁内（目标 90s，硬上限 120s）**

```bash
LOCK=".claude/skills/spatial-design-first-build/scripts/device-lock.sh"
OWNER="SpaceCube-configwindow-r1"   # 跨 Bash 调用必须是同一个字符串，不要用 $$
trap '"$LOCK" release "$OWNER" >/dev/null 2>&1' EXIT INT TERM
"$LOCK" acquire "$OWNER" 120 || echo "device busy"
```

`acquire` 退出码 1 = 等待超时：**不要死等、不要重试**，报告"设备被占用，UI 验证已跳过"并结束本任务。

抢到锁后严格按序，中间不做分析：

```bash
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch tech.illusion.spacecube
sleep 8
adb exec-out screencap -p > .spatialsdk/ui-verify/v6-r1-1-default.png
# 点「外观设置」并切一个方块材质 —— 坐标按上一步截图里按钮的实际位置定
adb shell input tap <x> <y>
sleep 2
adb exec-out screencap -p > .spatialsdk/ui-verify/v6-r1-2-appearance.png
pico-cli app stop tech.illusion.spacecube
"$LOCK" release "$OWNER"
```

用 `adb exec-out screencap` 而不是 `pico-cli capture`：本项目实测 `pico-cli capture` 拍不到空间内容。

- [ ] **Step 3: 锁外比对**

读两张 PNG，按 Step 1 的检查清单逐项核对，产出 Blocker / Major / 不可判定分级。**判定只依据截图**；此刻可以查代码，但只用于定位修复点。

同时抓日志交叉核对（`grep` 读设备日志**一律加 `-a`**，否则控制字节会让 grep 把 logcat 当二进制静默跳过，输出为空会被误判成"应用没打日志"）：

```bash
adb logcat -d -v time | grep -a "SpaceCubeConfig"
```

期望看到 `config window focused: highScore=… preview=…` 与 `preview attached: type=…`，且两处的 type 一致。

- [ ] **Step 4: 记录**

把轮次、时间、截图路径、差异清单、结论追加到 `.spatialsdk/ui-verification-log.md`，然后提交：

```bash
git add .spatialsdk/ui-verification-log.md
git commit -m "docs: record gate B round 1 (config window) verification"
```

---

### Task 9: 门禁 B 第 2 轮 —— 进入游戏 + 取冷启动实测数字

单独一轮，预算拉到 120s 硬顶。棋盘构建本身就要 60–95s，**很可能撞上限**。

**Files:**
- Modify: `.spatialsdk/ui-verification-log.md`
- Modify: `AGENTS.md`（把实测数字写进去）

- [ ] **Step 1: 锁内**

```bash
LOCK=".claude/skills/spatial-design-first-build/scripts/device-lock.sh"
OWNER="SpaceCube-configwindow-r2"
trap '"$LOCK" release "$OWNER" >/dev/null 2>&1' EXIT INT TERM
"$LOCK" acquire "$OWNER" 120 || echo "device busy"

pico-cli app launch tech.illusion.spacecube
sleep 6
adb shell input tap <开始游戏按钮的 x> <y>   # 坐标从第 1 轮的 v6-r1-1-default.png 里量
sleep 100                     # 覆盖 60-95s 的棋盘构建
adb exec-out screencap -p > .spatialsdk/ui-verify/v6-r2-3-ingame.png
pico-cli app stop tech.illusion.spacecube
"$LOCK" release "$OWNER"
```

超过 120s 硬上限：立即 `app stop` + `release`，本轮记"验证失败（超时）"，**不占着设备继续挣扎**。

- [ ] **Step 2: 锁外读日志**

```bash
adb logcat -d -v time | grep -a -E "SpaceCubeBoardBuild|SpaceCubeConfig|SpaceCubeHandGesture"
```

必须找到并记录这四条（截图判不出其中任何一条）：
1. `openStage(SpaceCubeStage) -> Allowed`
2. `board build phases: material=…ms mesh=…ms entity=…ms addChild=…ms cubes=…` ← **L1 效果的判据**
3. `initial: board build took …ms` ← 与改动前 AGENTS.md 记的 60–95s 对比
4. `minimizeWindowContainer(SpaceCubeConfigWindow) -> true`

- [ ] **Step 3: 核对截图**

`v6-r2-3-ingame.png` 应显示：已进入全沉浸（无窗口 chrome）；棋盘 + score_hud + next_piece + pause_button 正常；**配置窗口不可见**。

注意本项目实测截图可能是 ≥6s 的陈旧帧，必须和上面的日志时间戳交叉核对，不能只看图。

- [ ] **Step 4: 判定 L1，并决定要不要做 L2**

> **判据陷阱（Task 2 的 review 发现的，别踩）**：L1 已经落地，所以 `mesh=` 这一项**必然接近 0** ——
> 184 次调用里只有第一次真的建 mesh，其余都是命中缓存。**不能拿 `mesh=` 小来证明"mesh 不是瓶颈"，
> 也不能拿它来证明 L1 有效**，那是循环论证。

- **L1 是否有效，唯一判据是总构建时间**：把日志里的 `initial: board build took Xms` 和 AGENTS.md 记录的
  改动前基线 60–95s 比。明显低于 60s → L1 有效，记录节省量；仍在 60–95s 区间 → mesh 创建从来就不是大头，
  L1 无害但也无用，如实写。
- 总时间没降的话，看余下三项谁是大头：
  - `material=` 占大头 → 给用户提 L2（184 个 `UnlitMaterial` 改成 7 个按 `PieceType` 共享），**但不要自行开工**：
    L2 会动到已经过真机验证的 JELLY 渲染路径，属于契约 v6 增量 5 明确排除的范围，要重新走门禁 A。
  - `entity=` / `addChild=` 占大头 → L1/L2 都治不了根，如实说明，L3（`MeshInstancesResource`）才是方向，
    同样需要单独走门禁 A。

- [ ] **Step 5: 更新文档并提交**

把实测数字写进 `AGENTS.md` 的 "Verified so far"（替换掉那三处"60–95s"的经验描述），把本轮记录追加到 `.spatialsdk/ui-verification-log.md`：

```bash
git add AGENTS.md .spatialsdk/ui-verification-log.md
git commit -m "docs: record gate B round 2 and the measured board-build phase breakdown"
```

---

## 修复循环

门禁 B 报告里若有 Blocker / Major：**锁外**定位并改代码 → 重新编译 → 回到对应轮次重排队。**最多 2 轮**，第 2 轮后仍不过就停下来，输出差异清单与已尝试的修复，交用户决策。

## 已知风险与退路

| 风险 | 征兆 | 退路 |
|---|---|---|
| Stage 打开后配置窗口**不可见** —— "加载期窗口留守"整个方案压在这条假设上 | 点开始后立刻变空房间，而不是继续看到"加载中"卡片 | Stage 内挂一张"加载中"卡片（`sceneReady` 为 false 时渲染），并把 `minimizeWindowContainer` 提到 `openStage` 之后立刻调用 |
| `closeStage()` 后被 restore 的窗口没回前台 / 没重新拿到焦点 | 返回后最高分不刷新、预览方块不重摇 | 检查 `restoreWindowContainer` 的返回值；若返回 `true` 但焦点没变，改用 `LocalLifecycleOwner` 的 `ON_RESUME` 代替 `isFocused` 作为刷新信号 |
| 两个容器**不同进程** | `GameSettings.difficulty` 在 Stage 侧读出来永远是 NORMAL | 已经预防：难度走 Bundle。这条只影响日志解读，不影响功能 |
| 预览底板**不渲染** | 4 个方块在，底板不见 | 本项目 AGENTS.md 调试笔记第 3 条：更宽/更远的 ECS 实体可能整个不可见，而更窄的正常。把 `PREVIEW_PLATE_MARGIN_M` 降到 0，让底板正好等于方块的占地面积 |
| L1 共享 mesh 导致渲染异常 | 方块不显示 / 显示错乱 | 一行回滚：`cubeMesh()` 换回内联的 `MeshResource.createBox(...)` |
| `openStage` 被连点触发两次 | 日志里出现两条 `openStage(...) ->` | `launching` 已在 `startGame()` 开头拦了一道；若仍出现，把 `launching` 的写入移到 `scope.launch` 之外的同步路径最前面 |
