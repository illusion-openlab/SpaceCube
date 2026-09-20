# 配置窗口入口 + 按需全沉浸 Stage — 设计说明

日期：2026-09-20
设计契约：`.spatialsdk/design-contract.md` v6 增量（门禁 A 已确认）

## 1. 问题

SpaceCube 当前的默认空间容器是全沉浸 `Stage`——应用一启动就直接接管整个视野，开始界面只是 Stage 里的一个 UI 状态（`started == false`）。用户要求：默认不进全沉浸，先给一个窗口作配置页，页内摆放 3D 俄罗斯方块造型，点"开始"才进全沉浸。

## 2. 背景：为什么当初改成了 Stage，以及为什么现在可以改回来

2026-08-06 从 Volumetric 窗口迁到 Stage，唯一理由是**窗口 chrome（随注视移动的标题栏）持续截走游戏中的拖拽/点击手势**，导致核心交互不可靠。

该理由只对**游戏进行中**成立。配置页只有离散点击，没有连续手势。因此本设计不推翻那次决策：游戏本体仍跑在无 chrome 的 Stage 里，只是这个 Stage 从"默认容器"降级为"按需打开的非默认容器"。

## 3. 容器架构

```
应用启动
  └─ DefaultWindowContainer "SpaceCubeConfigWindow"  (Volumetric, 900x760x600dp)
       ├─ SpatialView
       │    ├─ AttachmentPanel "config_card"          ← 现 start_screen 的全部内容
       │    ├─ AttachmentPanel "appearance_settings"  ← 原样搬运
       │    └─ ECS: previewAnchor
       │           ├─ 4 × preview cube   (随机 1 个 PieceType)
       │           └─ 1 × preview base plate
       └─ 点"开始游戏"
            └─ openStage("SpaceCubeStage", Mixed, bundle)   [suspend]
                 └─ Stage "SpaceCubeStage" (非默认，DSL 声明)
                      └─ GamePage —— 棋盘 + 所有游戏面板（无开始界面态）
```

`Main.kt`：

```kotlin
fun mainApp(scope: SpatialAppScope) = with(scope) {
    DefaultWindowContainer { PicoTheme { ConfigPage() } }
    Stage(id = STAGE_ID) { PicoTheme { GamePage(bundle) } }
}
```

`AndroidManifest.xml`：删除 `pico.spatial.stage.id` / `pico.spatial.stage.style` 两条 meta-data，替换为 `pico.spatial.windowcontainer.{id,style,defaultsize,materialbackground}`（取值见契约 v6 增量 1）。`materialbackground="0"` 是刻意的：CandyCard 自带不透明底，再叠系统毛玻璃会形成两个共面半透明层，是真机摩尔纹的已知诱因。

## 4. 跨容器状态

两个容器是两套独立的 Compose 组合，甚至可能是两个进程。状态通道必须显式设计：

| 数据 | 方向 | 通道 | 理由 |
|---|---|---|---|
| 难度 | 窗口 → Stage | `openStage(bundle = Bundle().apply { putString(KEY_DIFFICULTY, d.name) })`，Stage 侧从 `StageScope.bundle` 读 | 官方 documented 通道。**不**用 `GameSettings` 单例——若 Stage 跑在独立进程，单例会静默失效并回落到"中"难度，是典型的无报错错误 |
| 两种材质 | 双向持久 | 已有的 SharedPreferences store，两侧各读各的 | 已存在，零新增机制 |
| 历史最高分 | Stage → 窗口 | Stage 写 prefs；窗口监听 `LocalSpatialContainerStateManager.current.isFocused` 由 `false → true` 时重读 | 无跨容器回调机制；焦点是唯一可靠的"我回来了"信号 |

同一个 `isFocused` 转换还负责重摇 `previewPieceType`，让每次从游戏返回都换一个方块。

## 5. 进入 / 返回时序

**进入**

1. 点"开始游戏" → `launching = true`，按钮变"加载中"并禁用
2. `scope.launch { navigator.openStage(STAGE_ID, StageStyle.Mixed, bundle) }`
3. Stage 打开，`GamePage` 的 `SpatialView.initial` 开始构建棋盘（60–95s）；**配置窗口全程留守可见**，用户看着熟悉的"加载中"而不是空房间
4. `sceneReady` 后，**Stage 侧**调 `navigator.minimizeWindowContainer(CONFIG_WINDOW_ID)` 收起窗口

第 4 步不需要跨容器回调：`minimizeWindowContainer(id)` 由 Stage 自己发起，此时 Stage 开着，API 前置条件满足。

**返回（游戏结束"返回开始画面" / 退出确认的"退出"）**

```kotlin
navigator.restoreWindowContainer(CONFIG_WINDOW_ID)   // 必须在 closeStage 之前
navigator.closeStage()                               // suspend
```

顺序不可交换：`restoreWindowContainer` 的 KDoc 明确 "can only be used when there is a stage open"。先关 Stage 就再也没有 API 能把最小化的窗口叫回来。

"退出"按钮的语义同时改变：由 `activity?.finish()`（杀掉整个 App）改为回到配置窗口。

**"再来一局"**留在 Stage 内直接重开，不回窗口、不重新构建棋盘。

## 6. 配置页的 3D 预览

- `previewPieceType = PieceType.entries.random()`，创建时摇一次，每次从游戏返回重摇
- 4 个 cube，尺度与棋盘一致（`0.05m` 单元 + `0.006m` 间隙），按 `spawnCells` 裁剪到占位包围盒后居中
- 下方一块薄片底板
- 方块材质跟随 `selectedPieceMaterial`、底板材质跟随 `selectedBasePlateMaterial`，**实时重建** —— 这让"外观设置"第一次有了真实预览

只有 5 个实体，即使按最悲观的 ~0.5s/实体也就 ~2.5s，藏在用户读卡片的时间里。

**不做动画**：`withFrameNanos` 在 `WindowContainer` 里实测只触发一次（项目已有记录），要动就得另起 `delay` 循环，本轮 YAGNI。

## 7. 冷启动 60–95s：构成、能不能提前、怎么砍

### 构成

`BoardCubeRenderer.attachTo()` 的 184 次循环（180 locked + 4 falling，ghost 池关闭），每次 `createCube()`：

```kotlin
val material = UnlitMaterial.create(blendingMode).apply { setOpacity(opacity) }   // 每方块一个新材质
val mesh = MeshResource.createBox(Vector3(0.05,0.05,0.05), cornerRadius = 0.008f) // 每方块一个新网格，184 个参数完全相同
val entity = ModelEntity(mesh, material)
anchor.addChild(entity)
```

代码里已有的实测注释：单次 `createCube()` 往返在宿主内存压力下偶尔接近 1 秒。所以这是 **184 次 ECS 实体往返**，不是资源加载。

### 能不能提前布置

**不能**（就 184 次创建而言）。实体必须建在 Stage 自己的 `SpatialContent` 上，而该 content 在 `openStage` 之前根本不存在。

同进程内唯一能提前的是 `BaseMaterialsBundle` 的 ~25MB AssetBundle 加载——而配置页的 3D 预览本来就要用它。**这份预热是白送的，不需要额外代码。**

### 怎么砍（本轮只做 L1 + 埋点）

| | 做法 | 风险 | 本轮 |
|---|---|---|---|
| L1 | 184 次参数相同的 `createBox` → 建 1 个 mesh 复用 | 低，一行 hoist，可秒回滚 | **做** |
| L2 | 184 个 `UnlitMaterial` → 7 个（按 `PieceType`）；PBR 路径已经是这个形状，`bindCubeMaterial` 照抄 | 中，动到已验证的 JELLY 渲染路径 | 看 L1 数字再定 |
| L3 | `MeshInstancesResource` 批量实例化 | 高，重写渲染 + `customFloatData` + ShaderGraph 材质，与材质选择器冲突 | 不做 |

SDK mesh 文档原话：「避免为每个实例创建不同的 mesh / material」、「Prefer batch creation: use `MeshInstancesResource.create(name, list)` ... avoiding the performance overhead caused by repeatedly calling `add` in a loop」。L3 长期看是终局，但不该和容器改造搅在一起。

**埋点**：`attachTo()` 内对 material / mesh / entity / addChild 四步分别累计耗时，构建结束打一条汇总日志。门禁 B 第 2 轮本来就要进游戏、本来就要读 logcat，数字是白拿的。拿到数字再决定 L2/L3，不靠猜。

## 8. 文件变更

| 文件 | 动作 |
|---|---|
| `app/src/main/AndroidManifest.xml` | 改：stage meta → windowcontainer meta |
| `Main.kt` | 改：双容器声明 |
| `content/ConfigPage.kt` | **新增**：窗口内容、配置状态、`openStage` 调用、`isFocused` 刷新 |
| `content/PiecePreviewRenderer.kt` | **新增**：随机 tetromino + 底板的构建与材质切换 |
| `content/GamePage.kt` | 改，详见下表 |
| `content/BoardCubeRenderer.kt` | 改：分阶段计时埋点 + L1 共享 mesh |
| `GameplayInfoOverlay` / `BasePlateMaterialPicker` / `PieceMaterialPicker` / `CandyPanelTheme` / `NextPiecePreview` | 不改，被 `ConfigPage` 复用 |
| `game/**` | **一行不动**，38 个单测应原样通过 |

`GamePage.kt` 的改动明细（`started` 的处置是这次最容易读歧义的一处，逐条写死）：

| 原有用法 | 改为 |
|---|---|
| `AttachmentPanel("start_screen")` 整块 | **删除**（内容迁往 `ConfigPage` 的 `config_card`） |
| `var started by remember { mutableStateOf(false) }` | **删除这个变量**。Stage 一旦打开就是"要玩" |
| `score_hud` / `next_piece` / `pause_button` / `pause_overlay` / `game_over_overlay` 的 `if (started)` 门控 | 改为 `if (sceneReady)` —— 语义等价（棋盘建好前这些面板既不该渲染也不该吃输入），且 `sceneReady` 本来就存在 |
| `startGame()` 函数 | **保留**。"再来一局"仍调它；此外 `sceneReady` 变 true 后自动调一次，取代原先由"开始游戏"按钮触发的那次 |
| `started = false`（"返回开始画面"按钮） | 改为 `restoreWindowContainer(CONFIG_WINDOW_ID)` + `closeStage()` |
| `activity?.finish()`（退出确认的"退出"） | 同上，改为回配置窗口 |
| `showAppearanceSettings` / `showGameplayInfo` / 两个面板 | **删除**（迁往 `ConfigPage`）。`showExitConfirm` 与 `exit_confirm_overlay` 保留在 Stage |
| 自由拖拽门控 `!started` | 改为 `!isPlaying` |
| 难度来源 `GameSettings.difficulty`（由开始界面写入） | 改为从 `StageScope.bundle` 读，并在 `GamePage` 内部写回 `GameSettings.difficulty` 供 `GameEngine` 使用 |

## 9. 待验证假设（代码无法自证）

1. Stage 打开后、`minimizeWindowContainer` 之前，配置窗口是否仍然可见？"加载期窗口留守"整个方案建立在这个假设上。若不可见，退回备选：Stage 内挂"加载中"卡片。
2. `closeStage()` 之后被 restore 的窗口是否确实回前台并重新获得焦点（进而触发最高分刷新与重摇方块）？
3. 非默认 Stage 与配置窗口是否同进程？（已因此改用 bundle 传难度，但仍需日志确认。）
4. L1 是否真的有改善？

## 10. 验证

见契约 v6 增量 6：两轮门禁 B（配置窗口 2 张截图 / 进入游戏 1 张截图），以及必须做的 logcat 交叉核对项——`minimize`/`restore` 的 `Boolean` 返回值和 `attachTo` 耗时汇总是截图判不出来的，只能靠日志。

读设备日志一律 `grep -a`，否则控制字节会让 grep 把 logcat 当二进制静默跳过；模拟器视觉取证用 `adb screencap`（`pico-cli capture` 拍不到空间内容），并注意截图可能是 ≥6s 的陈旧帧。
