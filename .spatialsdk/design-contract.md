# 设计契约

## 0. 元信息

- 应用名 / applicationId：妙妙方块 / tech.illusion.spacecube
- 设计源类型：现有实现的裁剪（无新增视觉，仅隐藏既有元素）
- 设计源位置：`app/src/main/java/tech/illusion/spacecube/content/GamePage.kt`（现状代码即基准）
- 契约版本：v7（v2–v7 增量见文末，v1 内容原样保留作历史记录）
- **容器架构自 v6 起变更**：默认容器由全沉浸 Stage 改为 Volumetric WindowContainer，游戏改为按需打开的非默认 Stage。下方第 1 节「面板清单」中"容器类型不变"的表述自 v6 起失效，以 v6 增量的面板清单为准。
- 用户确认时间：v1 已确认（2026-08-11 会话）；v2 已确认并已验证（2026-08-12 会话，见 ui-verification-log.md Round 2）；v3 待确认；v5（开始界面"玩法"入口按钮与说明弹层）已通过门禁 A 用户确认（2026-08-26 会话），本次为门禁 B 实现；v6（默认容器改为配置窗口 + 按需 Stage）已通过门禁 A 用户确认（2026-09-20 会话）

## 1. 面板清单

| 面板 id | 容器类型 | 面板尺寸 | 层级/父面板 | 出现时机 |
|---|---|---|---|---|
| start_screen | WindowContainer (AttachmentPanel) | wrap（CandyCard 自适应） | 根（anchor 子面板） | `!started` 时显示，改动前后不变 |

容器类型不变，本次改动不涉及容器架构。

## 2. 元素表（仅列本次改动涉及的元素，其余维持现状）

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| control_scheme_label（"操作方式"文案） | Text | start_screen/CandyCard/Column | — | — | wrap×wrap | - | - | onSurfaceVariant(CandyCardInkDim) | labelSmall | - | **删除，不再渲染** |
| control_scheme_row（V1/V2 两个按钮） | Row of Button | start_screen/CandyCard/Column | — | — | wrap×wrap | - | 上下与相邻元素的 spacedBy(14.dp) 保持 | primary/onSurface(candyButtonColors) | labelLarge(按钮默认) | 按钮默认圆角 | **删除，不再渲染** |

删除后 Column 内 `spacedBy(14.dp)` 自动收紧，"开始游戏"按钮上移紧贴难度选择按钮组，无需额外调整间距。

## 3. 状态清单

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| controlScheme（内部状态，非可见元素） | 初始值 | 由 `V1_HAND_TRACKING` 改为 `V2_SYSTEM_GESTURE`，游戏内手势输入方式默认走 V2 系统手势（面板背后的玻璃交互面） |

## 4. 文案清单

无新增文案；删除"操作方式"标签文案及 V1/V2 两个按钮的文案（"V1 手势"/"V2 系统手势"）。

## 5. 不做清单（YAGNI 边界）

- 不删除 `ControlScheme.V1_HAND_TRACKING` 枚举值、`HandGestureController` 及其手势指示器逻辑——保留作为隐藏的调试/回退路径，仅不再提供 UI 入口
- 不新增任何设置项、开关或调试菜单来重新暴露 V1
- 不改动难度选择、开始游戏按钮、分数/下一个方块/暂停等其它面板

## 6. 截图验证计划

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 启动默认态（开始界面） | 启动 App，等待渲染 | start_screen 卡片整体：确认无"操作方式"文案与 V1/V2 按钮行，难度按钮组与"开始游戏"按钮间距正常 |
| 2 | 游戏进行中 | 点击"开始游戏"后等待渲染 | 确认默认 V2 系统手势生效（背后玻璃交互面可用），无 V1 遗留 UI 残留 |

---

## v2 增量（2026-08-12）：开始界面新增手势操作说明

### 增量 0. 元信息

- 设计源类型：文字 PRD（"介绍简单的手势操作：移动、旋转"），无 Figma/图片输入
- 设计源位置：本节即设计源（纯文案+排版，复用现有 CandyCard 视觉体系，无新色值/新组件）
- 用户确认时间：（待确认）

### 增量 1. 面板清单

不新增面板，仍是 `start_screen`（WindowContainer/AttachmentPanel），容器类型不变。

### 增量 2. 元素表

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| gesture_hint_rotate | Text | start_screen/CandyCard/Column | — | — | wrap×wrap | - | - | CandyCardInkDim（沿用既有 dim 角色，非新色值） | bodySmall | - |
| gesture_hint_move | Text | start_screen/CandyCard/Column | — | — | wrap×wrap | - | - | CandyCardInkDim | bodySmall | - |

三者作为 Column 内一个整体插入位置：紧接难度选择 `Row` 之后、"开始游戏" `Button` 之前——正是原先"操作方式"选择器被删除后空出来的槛位，同一处 `spacedBy(14.dp)` 间距沿用，不额外调参数。

### 增量 3. 状态清单

无新状态，纯静态说明文案，不随游戏状态变化。

### 增量 4. 文案清单（确切文案）

| 元素 id | 文案（用户 2026-08-12 定稿） |
|---|---|
| gesture_hint_rotate | 旋转：注视方块，双击旋转方块 |
| gesture_hint_move | 移动：手指捏合移动控制方块方向 |

显示顺序为「旋转」在上、「移动」在下，按用户给出的顺序原样保留。不再有单独的"操作提示"标题行——两条各自带冒号前缀已经自解释。

不提及软降（向下拖动）——用户明确只要求"移动、旋转"两项，遵循最小改动原则，不多加。

### 增量 5. 不做清单（YAGNI 边界）

- 不新增图标/动画/示意图，纯文字说明
- 不提及软降手势（下拖）、不提及 V1 手势（已隐藏，见 v1 契约）
- 不引入新颜色值，复用 `CandyCardInkDim` 现有角色
- 不做可关闭/可展开交互，固定展示

### 增量 6. 截图验证计划

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 启动默认态（开始界面） | 启动 App，等待渲染 | gesture_hint_rotate / gesture_hint_move 两行文案完整显示（"旋转：…"在上，"移动：…"在下）、位置在难度按钮组与"开始游戏"按钮之间、字体颜色为浅棕（CandyCardInkDim）非纯黑、卡片不因新增文字被撑破或裁切 |

---

## v3 增量（2026-08-12）：开始界面先出现、加载中态

### 增量 0. 元信息

- 用户诉求原话："当前进入应用加载过程有点长，能否先出现操作面板，显示加载中状态？"
- 设计源类型：无 Figma/图片，代码结构诉求 + 文字 PRD
- 根因（已读代码确认，非猜测）：`BoardCubeRenderer.attachTo()` 同步创建约 185 个
  entity/mesh/material（10×18 棋盘格 + 4 落子格 + 底座），且当前 `start_screen` 面板的
  `attach()` 调用排在这个循环**之后**才执行——`SpatialView(initial = …)` 是一个不会被打断的
  suspend 函数，中途没有任何挂起点，所以哪怕单纯把 `attach()` 挪到循环前面，主线程仍会在挪动后的那
  一行和循环之间连续跑完，Choreographer 拿不到机会画出"面板已经绑定但棋盘还没建好"这一帧。因此
  修复需要两步都做：**先 attach("start_screen")，再插入一个 `yield()` 挂起点，再执行
  `renderer.attachTo(anchor)`**。取证来源：PICO Spatial SDK 6.0 文档
  `spatial-sdk_content-layout-and-presentation_add-3d-content-to-spatialmodelview-and-spatialview.md`
  ——官方示例里 `attachments.entity(id)` 在 `initial` 一开始就可用，不依赖其它实体先创建好，
  且官方也在 `initial` 里用 `withContext(Dispatchers.IO)` 做过真正的挂起，证明 `initial`
  内部允许、也需要挂起点才能让中间状态被画出来。

### 增量 1. 面板清单

不新增面板，仍是 `start_screen`（WindowContainer/AttachmentPanel），容器类型不变。
其余四个面板（`score_hud`/`next_piece`/`pause_button`/`pause_overlay`/`game_over_overlay`）
均已有 `if (started)` 门槛，本增量不动它们——开始界面阶段它们本就不显示，与本次诉求无关。

### 增量 2. 元素表

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| loading_hint（新增） | Text | start_screen/CandyCard/Column | — | — | wrap×wrap | - | - | CandyCardInkDim（沿用现有角色） | bodyMedium | - |

标题"空间方块"、"历史最高 N"两行**保持始终显示**（不依赖棋盘实体，本身很轻，让用户第一时间看到界面有响应）。
难度选择 Row、旋转/移动手势提示两行、"开始游戏" Button——这四项**改为仅在 `sceneReady == true` 时渲染**，
加载中态下被 `loading_hint` 整体替换，不是叠加。

### 增量 3. 状态清单

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| sceneReady（内部状态，非可见元素，初始 `false`） | 加载中（false） | Column 内标题/最高分下方只显示 `loading_hint`；难度/手势提示/开始游戏均不渲染 |
| sceneReady | 就绪（true，`renderer.attachTo()` 完成后立即置位） | 恢复为 v2 增量确认过的完整内容（难度 Row + 两行手势提示 + 开始游戏按钮） |

### 增量 4. 文案清单

| 元素 id | 文案 |
|---|---|
| loading_hint | 加载中… |

### 增量 5. 不做清单（YAGNI 边界）

- 不加进度条/百分比/加载动画（spinner）——纯文字状态，够用即止
- 不给加载态设最短展示时长；实际多久就显示多久，不人为拉长
- 不改动 score_hud/next_piece/pause_button/pause_overlay/game_over_overlay 四个面板
- 不尝试"优化"185 个 entity 的创建速度本身（那是性能话题，需要另外测量，本次只是让加载过程可感知，不是让它变快）

### 增量 6. 截图/日志验证计划

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 加载中态 | 冷启动 App 后**立刻**截图（越快越好，若加载太快抓不到则以 logcat 时间戳佐证，明确标注"不可判定"） | start_screen 卡片可见、标题"空间方块"+历史最高可见、`loading_hint` "加载中…" 可见、难度/手势提示/开始游戏按钮**不可见** |
| 2 | 就绪态 | 等待渲染完成（默认 6 秒足够覆盖) | 难度 Row、两行手势提示、开始游戏按钮恢复显示，`loading_hint` 消失，与 v2 截图一致 |

日志核对：logcat 中 `attach("start_screen")` 前后 与 `renderer.attachTo()` 前后各打一条时间戳日志，
用真实耗时佐证"加载过程确实有感知长度"，而不是凭代码结构猜测。

---

## v4 增量（2026-08-12）：加载中态改为「同一张卡片，按钮变加载中」

### 增量 0. 元信息

- 用户诉求原话（配图为当前就绪态卡片截图）："加载中应该同样以这个窗口显示，不过游戏开始改为加载中，并且无法进行点击"
- 取代 v3 增量里"难度/手势提示被隐藏、单独一行'加载中…'"的方案

### 增量 1/2. 面板与元素表（对 v3 的修正）

不再有独立的 `loading_hint` 元素、不再整体隐藏难度 Row / 手势提示两行——**这三者改为始终显示**，
加载中与就绪态共用同一张卡片布局，唯一随 `sceneReady`变化的是最下方那个按钮：

| id | 类型 | 父容器 | 内容（!sceneReady） | 内容（sceneReady） | 颜色角色 | 可点击 |
|---|---|---|---|---|---|---|
| start_button | Button | start_screen/CandyCard/Column | 文案"加载中" | 文案"开始游戏" | `candyButtonColors(primary = sceneReady)`——加载中用现有的非主色（灰底，与未选中难度按钮同色系），就绪用现有主色（薄荷绿，与截图一致） | `enabled = sceneReady`；加载中态下点击不触发 `startGame()` |

标题/历史最高/难度 Row/旋转提示/移动提示——五者与 sceneReady 无关，加载中和就绪两态视觉完全一致。

### 增量 3. 状态清单

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| sceneReady=false | 加载中 | 卡片其余部分不变，仅 start_button 文案="加载中"、颜色=非主色（灰）、`enabled=false` |
| sceneReady=true | 就绪 | start_button 文案="开始游戏"、颜色=主色（薄荷绿）、`enabled=true`，点击调用 `startGame()` |

### 增量 4. 文案清单

| 元素 id | 文案 |
|---|---|
| start_button（加载中） | 加载中 |
| start_button（就绪） | 开始游戏（不变） |

### 增量 5. 不做清单

- 不新增视觉禁用效果（变灰蒙层/透明度降低等）——仅用已有的"非主色"按钮配色区分，不引入新样式
- 不在加载中态额外提示"请稍候"之类的辅助文案，按钮文案本身已经足够
- 难度 Row 在加载中态**不禁用**（用户没要求禁用它，选中的仍是 `selectedDifficulty` 的当前值，只是开始游戏按钮点不动）——若这与预期不符需要用户明确说明

### 增量 6. 截图验证计划

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 加载中态 | 冷启动后尽快截图 | 卡片完整（标题/最高分/难度/两行手势提示都在），最下方按钮文案="加载中"、灰色非主色调、视觉上和"慢/中/快"里未选中的按钮同色系 |
| 2 | 就绪态 | 等待 `board build took` 日志出现后截图 | 同一张卡片，按钮变回"开始游戏"、薄荷绿主色 |

---

## v5 增量（2026-08-26）：开始界面新增"玩法"入口按钮与说明弹层

> 版本号说明：文件头 `## 0. 元信息` 里的"契约版本：v3"落后于正文——正文实际已有 v2/v3/v4 三段增量（v4 见上）——本增量按正文最新的 v4 顺延编号为 v5；本次实现同时更正了文件头的版本号表述（详见文件头）。

### 增量 0. 元信息

- 用户诉求原话（转述）：为开始页新增一个"玩法"入口按钮 + 点击后弹出的玩法说明层（简介/操作/规则三段），作为门禁 A 的设计准备阶段产出。
- 设计源类型：文字 PRD + 已核实定稿文案（简介/操作/规则三段文字，见增量 4，逐字采用，不改写）。
- 设计源位置：本节即设计源。视觉体系上做了一处刻意分层：入口按钮复用项目既有的 CandyCard/candyButtonColors 奶油卡片体系配色角色（容器色 = CandyAccentMint 降 alpha 至 ~0.3，内容色 = CandyAccentMintInk 保持近满不透明，均为 `candyButtonColors(primary=true)` 分支所用的同一对角色，不新增色值）；弹层本体**同样采用 CandyCard**（而非玻璃 `backgroundMaterial`）——门禁 A 设计准备阶段曾建议用 `backgroundMaterial(enable=true, style=Material.Regular)` 制造差异化的玻璃层，但门禁 A 最终确认时改为"复用这个项目其它面板（结算面板/退出确认面板）已经在用的卡片视觉规范，哪个是这个项目的真实惯例就用哪个"——本项目里 `backgroundMaterial` 实际使用次数为 0，而 `pause_overlay`/`game_over_overlay`/`exit_confirm_overlay` 三个既有覆盖层全部用 CandyCard，因此最终实现选择 CandyCard，不引入项目里从未用过的玻璃层 API。
- API 取证：
  - `Button`（`com.pico.spatial.ui.design.Button`）签名核对自 SDK 6.0 `design-6.0.0-sources.jar`：`ButtonColors`/`ButtonSize` 构造函数均为 `internal`，只能经 `ButtonDefaults.buttonColors(containerColor, contentColor)` / `ButtonDefaults.Small` 等预设间接得到；`Button` **没有** `border`/`style` 参数，不支持传入描边，与本增量"不要描边"的最终规格天然一致。
  - `ButtonDefaults.Small`：`minWidth=69.dp, minHeight=40.dp`，圆角 `20.dp`（`cornerRadiusForButtonSize`），文字样式 `PicoTheme.typography.labelLarge`——用于入口按钮与弹层关闭按钮，未自定义 `ButtonSize`。
  - `PicoTheme.colorScheme` 实际字段（`fillPrimary/fillSecondary/fillTertiary/fillLight/labelPrimary/labelPrimaryLight/labelSecondary/labelTertiary/labelQuaternary/lightenHover/lightenPressed/error/alert/passable/interaction/dividerLine`），取自同版本 SDK 参考 `com.pico.spatial.ui.design.md`——**该 ColorScheme 里没有名为 `surface`/`scrim` 的字段**，本次实现选用 `fillPrimary`（`FillDarkerAlpha` 默认 token，语义偏"重要元素背景"但字面更"深"）而非 `fillSecondary`（`FillSemilightAlpha`，字面更"浅"）来做 scrim，理由是 scrim 的功能是压暗背景，`fillPrimary` 的默认 token 更深、更适合这个用途，即便它的语义描述（"小面积/重要元素"）和 scrim 的几何面积（大面积）不是字面对应——这一取值本增量按方向性最优选择落地，仍需真机/模拟器截图核对遮罩是否够暗、够自然（见增量 6）。
  - `Modifier.backgroundMaterial(enable, style)` 与 `Material.Regular`：签名核对自 SDK 6.0 `foundation-6.0.0-sources.jar`（`com.pico.spatial.ui.foundation.material.BackgroundMaterial.kt`），确认存在且签名如预期，但本次未采用（见上，改用 CandyCard）。
- 用户确认时间：已通过门禁 A 用户确认（2026-08-26 会话）；本次为门禁 B 实现落地。

### 增量 1. 面板清单

不新增面板，仍是 `start_screen`（AttachmentPanel，容器类型不变，不引入 SpatialPopup 等新容器组件）。

结构性变化（非新面板）：现有 `CandyCard { Column {...} }` 调用整体包进一个新的外层 `Box`（CandyCard 是这个 Box 的一个子项）。CandyCard 自身调用加了 `Modifier.align(Alignment.Center)`（CandyCard 组件本就接受 `modifier` 参数，这只是调用处新增一个对齐修饰符，不改 CandyCard 内部或 Column 任何代码/逻辑）——目的是避免弹层展开、外层 Box 因居中的 480dp 弹层卡片而变宽时，CandyCard 因为默认贴在 Box 左上角而在视觉上被"推"离开始页面板的真实锚点中心。弹层关闭态下 Box 尺寸等于 CandyCard 尺寸，这一改动不产生任何可见差异。

### 增量 2. 元素表

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 颜色角色 | 字体角色 | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|---|
| gameplay_button（"玩法"入口按钮，新增） | Button（`size = ButtonDefaults.Small`，无描边） | start_screen/Box(新增，包裹 CandyCard) | Alignment.TopEnd（相对新增外层 Box） | offset(x = 10.dp, y = -10.dp)，向外偏移形成"角标"，半覆在卡片圆角上 | Small 预设（min 69×40dp），紧凑按钮，非 200dp 宽的开始按钮规格 | Button 默认内边距 | 见"偏移"列 | containerColor = `CandyAccentMint.copy(alpha = 0.3f)`；contentColor = `CandyAccentMintInk`（近满不透明）——与"开始游戏"按钮就绪态同一对角色，容器降 alpha 做浅淡填充 | labelLarge（Small 预设文字样式） | Small 预设圆角（20.dp，胶囊感） |
| gameplay_button 前导图标（圆形"?"徽标，新增） | 自定义小 Box（圆形裁剪） | gameplay_button 的 `leadingIcon` 插槽 | — | — | 18.dp 直径圆 | — | — | 底色 `CandyAccentMint.copy(alpha = 0.68f)`；文字色 `CandyAccentMintInk`（该配色系里为填色圆点设计的深色 ink 角色） | labelSmall | 圆形（CircleShape） |
| gameplay_overlay_scrim（背景遮罩，新增） | Box | start_screen/Box(新增) | `BoxScope.matchParentSize()`（跟随外层 Box 实测尺寸） | 无 | matchParentSize，铺满整个开始页容器（覆盖 CandyCard 与 gameplay_button） | 无 | 无 | `PicoTheme.colorScheme.fillPrimary.copy(alpha = 0.7f)`；`pointerInput` + `detectTapGestures` 拦截点击并消费，`showGameplayInfo == true` 时渲染，层级在 CandyCard 与 gameplay_button 之上 | 不适用（无文字） | 无（矩形铺满） |
| gameplay_overlay_card（弹层卡片，新增） | CandyCard（复用项目既有卡片系统，非玻璃层） | start_screen/Box(新增) | `Alignment.Center`（相对新增外层 Box） | 无 | 宽 480.dp 固定；高度 wrap，标题行固定不滚动，其下内容区 `heightIn(max = 420.dp)` + `verticalScroll`，超出可滚动，不裁切文字 | CandyCard 自带 18.dp | 无 | CandyCard 自带背景（`CandyCardBackground` 奶油色） + 自身 `pointerInput`/`detectTapGestures` 消费点击，防止穿透到下方 scrim | 见下方子元素 | CandyCard 自带 20.dp 圆角 |
| gameplay_overlay_title_row（标题行，新增） | Row（`Modifier.fillMaxWidth()` + `Arrangement.SpaceBetween`，未使用 `weight`——`RowScope` 无 `weight` 扩展，改用撑满宽度 + SpaceBetween 达到两端对齐） | gameplay_overlay_card/Column（第一个子项，不参与滚动） | — | — | fillMaxWidth×wrap | 无额外 | 无 | 标题文本 `CandyCardInk` | `PicoTheme.typography.titleMedium`（对齐 exit_confirm_overlay 弹层标题同档字号） | — |
| gameplay_overlay_close_button（关闭按钮，新增） | Button（`size = ButtonDefaults.Small`，纯文字"×"，无图标资源） | gameplay_overlay_title_row（Row 右侧） | Row 内右对齐 + CenterVertically | 无 | Small 预设 | Button 默认 | 无 | `candyButtonColors(primary = false)`——与卡片内其余次要按钮（取消/返回开始画面）同色系，保持 CandyCard 体系内部一致 | labelLarge（Small 预设） | Small 预设圆角 |
| gameplay_overlay_section_intro/_controls/_rules（三段小节，新增） | Column（小标题 Text + 1~2 条正文 Text） | gameplay_overlay_card/Column 的可滚动子 Column（`heightIn(max=420dp) + verticalScroll`） | — | — | wrap×wrap，宽度撑满内容区，正文自动换行 | 三段间 `spacedBy(16.dp)`，段内小标题与正文间 `spacedBy(6.dp)` | 无 | 小标题（强调色）`CandyAccentMintInk`；正文 `CandyCardInkDim` | 小标题 `PicoTheme.typography.titleSmall`；正文 `PicoTheme.typography.bodyMediumMultiline` | — |

### 增量 3. 状态清单

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| showGameplayInfo（内部状态，新增，初始 `false`，与 `showAppearanceSettings`/`showExitConfirm` 同级声明） | 初始 / 关闭态 | `gameplay_overlay_scrim` 与 `gameplay_overlay_card` 不渲染；`gameplay_button` 正常显示可点 |
| showGameplayInfo = true（点击 `gameplay_button` 后写入） | 展开态 | `gameplay_overlay_scrim` + `gameplay_overlay_card` 渲染在 CandyCard 与 `gameplay_button` 之上；scrim 用 `pointerInput`+`detectTapGestures` 拦截并消费所有点击事件，防止穿透点亮下层"开始游戏"等按钮；点击 scrim 空白处（弹层卡片以外区域）或点击 `gameplay_overlay_close_button`，二者效果一致，都把状态写回 `false`；卡片自身也用 `pointerInput`+`detectTapGestures` 消费一次点击，防止卡片内部空白处的点击穿透到 scrim 误触发关闭 |

不影响 `started`/`sceneReady`/`showAppearanceSettings`/`showExitConfirm` 等既有状态的取值与判断逻辑；`start_screen` 现有显示门槛 `!started && !showExitConfirm && !showAppearanceSettings` 不变，`showGameplayInfo` 是这个门槛内部再叠加的一层子状态，不是并列的第四个门槛条件。与其它模态状态互斥：`OnBackPressedCallback` 中 `showGameplayInfo` 分支排在 `showAppearanceSettings` 分支之前（后开先关）；`startGame()` 内防御性强制 `showGameplayInfo = false`，确保任何进入 Playing 阶段的路径都不会带着这层浮层残留状态进入下一阶段（正常 UI 流程下 scrim 已经挡住了"开始游戏"按钮，这一步是防御性兜底，不是可通过 UI 触发的实际路径）。

### 增量 4. 文案清单（确切文案，用户已核实定稿，原样嵌入，未改写/未转述/未精简）

| 元素 id | 文案 |
|---|---|
| gameplay_button | 玩法 |
| gameplay_overlay_title_row（标题） | 玩法说明 |
| gameplay_overlay_close_button | × |
| gameplay_overlay_section_intro（小标题） | 简介 |
| gameplay_overlay_section_intro（正文） | 从空中的方块井顶端不断落下方块，你要用手势把它们移动、加速、旋转到合适的位置，拼满整行来消除得分。方块堆到井口顶端、新方块放不下的那一刻，这一局就结束——目标只有一个：把分数刷得尽量高。 |
| gameplay_overlay_section_controls（小标题） | 操作 |
| gameplay_overlay_section_controls（正文，条目 1） | 移动方块：对着方块捏合手指左、右、下移动，方块就跟着你的手滑动；碰到墙壁或者已经堆好的方块会自动停住，不会硬挤过去。 |
| gameplay_overlay_section_controls（正文，条目 2） | 旋转方块：对着方块快速捏合手指两下，方块顺时针转90度；转不过去的时候方块原地不动，方向固定只有顺时针一种。 |
| gameplay_overlay_section_rules（小标题） | 规则 |
| gameplay_overlay_section_rules（正文，条目 1） | 1. 填满一整行才会消除；一次锁定最多能同时清掉4行，被消掉的行整体消失，上面的方块整体下移补位。 |
| gameplay_overlay_section_rules（正文，条目 2） | 2. 暂停的时候，下落、移动、旋转全部冻结，恢复后从暂停那一刻接着玩，不会丢进度。 |

"操作"两条用 Text 列表呈现（原文本身已用"名词：说明"的结构自解释，不复刻图标——项目无书本/问号/操作类图标资源）；"规则"两条的编号 "1." "2." 是原文的一部分，原样保留，未额外转换成短横线/圆点前缀，也未去掉编号。关闭按钮文案由门禁 A 设计准备阶段的"关闭"二字改为最终确认的"×"字符（与增量 0 提到的"关闭按钮同样不用图标资源，用×字符"最终规格一致）。

### 增量 5. 不做清单（YAGNI 边界）

- 未改动 CandyCard 内 `Column` 现有六项元素（标题"空间方块"/"历史最高 N"/难度选择 Row/"外观设置"按钮/两行手势提示/`start_button`）的位置、顺序、逻辑、色值——只在外层新包一层 `Box`，`Column` 内部代码零改动（除 CandyCard 调用处新增的 `Modifier.align(Alignment.Center)`）
- 未新增图标资源/`Icon` 组件——"?"徽标与"×"关闭按钮均为纯文字/纯色块 + Text 实现，未引入新的图标文件或依赖
- 未改变 `start_screen` 的容器类型（仍是 AttachmentPanel），未引入 `SpatialPopup` 等新容器组件——弹层用同一 AttachmentPanel 内的 `Box` 叠加实现
- 未修改 `start_screen` 现有显示门槛 `!started && !showExitConfirm && !showAppearanceSettings`
- 未给 `gameplay_button` 或 `gameplay_overlay_close_button` 引入自定义 `ButtonSize`——一律用 `ButtonDefaults.Small` 预设
- 未给按钮传入 `border`/`Modifier.border(...)`——纯靠浅色底 + 鲜艳文字图标制造存在感，`Button` 组件本身也没有 border 参数
- 弹层三段文案（简介/操作/规则）未做任何删改精简，逐字对应增量 4 的定稿文案
- 未引入 `backgroundMaterial`/玻璃层——最终确认改用 CandyCard，理由见增量 0
- 未新增软降手势、V1 手势相关文案

### 增量 6. 截图验证计划

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 开始页默认态（弹层关闭） | 启动 App，等待 `sceneReady` | CandyCard 六项原有元素位置/文案不变；`gameplay_button`（"玩法"）出现在卡片右上角，容器色是主按钮同色系但明显更浅淡（半透明薄荷绿），文字/图标依然鲜明可辨，不与任何现有元素重叠、不遮挡标题文字，不因新增外层 Box 而导致卡片本体在屏幕上的位置发生可感知偏移 |
| 2 | 弹层展开态 | 点击/注视点击 `gameplay_button` | `gameplay_overlay_scrim` 铺满整个开始页容器并压暗背景（CandyCard 与 `gameplay_button` 均被半透明遮罩覆盖，肉眼可辨压暗效果）；`gameplay_overlay_card` 居中显示，标题"玩法说明"+"×"关闭按钮可见，简介/操作/规则三段文案完整、与增量 4 逐字一致、文字清晰可读；卡片内容超出可视高度时可滚动查看，文字不被裁切 |
| 3 | 点击空白关闭 | 弹层展开态下点击 scrim 空白处（弹层卡片以外区域） | 弹层与 scrim 消失，回到默认态；下层"开始游戏"等按钮未被误触发（即确认点击没有穿透 scrim） |
| 4 | 点击关闭按钮 | 弹层展开态下点击"×" | 效果与验证点 3 一致，弹层消失回到默认态 |
| 5 | 点击卡片内部空白处 | 弹层展开态下点击卡片内文字之间的空白区域（非关闭按钮） | 弹层保持展开，未被误关闭（验证卡片自身消费了点击，未穿透到 scrim） |

日志/交叉核对：本项目历史记录里模拟器截图可能是陈旧帧（实测差 ≥6s）、模拟器手势点击也不一定准，建议截图取证外，配合 `showGameplayInfo` 状态变化的日志时间戳做交叉核对，而不是单纯依赖截图判定弹层是否已经打开/关闭。scrim 的 `fillPrimary.copy(alpha=0.7f)` 取值以及"操作"两条是否需要更强的视觉分隔，均建议在真机/模拟器截图验证阶段确认，必要时调整 alpha 数值（不改变颜色角色本身）。

---

## v6 增量（2026-09-20）：默认容器改为 Volumetric 配置窗口，游戏改为按需打开的非默认 Stage

> 这是本契约第一次改动**容器架构**本身（此前 v2–v5 都只改 `start_screen` 内部元素）。文件头 `## 0. 元信息` 第 1 节「面板清单」里"容器类型不变"的表述自本增量起失效，以本增量的面板清单为准。

### 增量 0. 元信息

- 用户诉求原话：「针对SpaceCube项目，默认不要使用全沉浸状态，应用后打开后先是出现一个平面窗口作为配置窗口页，然后在里面摆放些基础的俄罗斯方块造型（3D模型），点击"开始"按钮后再进入全沉浸状态开始游戏」，以及后续追加的「次要项按照你的方案走，另外能否在进入游戏时就开始冷启动（冷启动有哪些内容，能否进行提前布置从而节约时间？），另外在配置页随机显示一个俄罗斯方块作为预览而不是展示所有俄罗斯方块的类型」。
- 设计源类型：文字 PRD / intent，无 Figma、无截图。视觉体系**完全复用**项目既有的 CandyCard 奶油卡片体系，不新增色值、不新增组件、不引入 `backgroundMaterial`。
- 设计源位置：本节即设计源；被搬运的 2D 元素以现状代码 `app/src/main/java/tech/illusion/spacecube/content/GamePage.kt` 的 `start_screen` / `appearance_settings` 面板为基准，逐项等价迁移。
- 与 2026-08-06「迁到 Stage」决策的关系：那次迁移的理由是**窗口 chrome（随注视移动的标题栏）抢走了游戏中的拖拽/点击手势**。该理由只对**游戏进行中**成立；配置页只有点击交互，没有连续手势，因此本增量不推翻那次决策——游戏本体仍然跑在无 chrome 的 Stage 里。
- 门禁 A 用户确认：已确认（2026-09-20 会话，四项关键选择由用户逐项拍板：Volumetric 窗口形态 / 内容全搬 + 3D 造型做材质实时预览 / 加载期窗口留守显示"加载中" / 返回走 closeStage）。

#### API 取证（全部核对自本机 `~/Library/PICO/sdk/6.0/agent-vault/`，非记忆、非推测）

| 事实 | 出处 |
|---|---|
| 默认容器只能有一个，WindowContainer 作默认容器时用 `DefaultWindowContainer {}` + `AndroidManifest.xml` 的 `pico.spatial.windowcontainer.*` meta-data 配置 | `documentation/spatial-sdk_spatial-container_manage-windowcontainers_declare-a-windowcontainer.md` |
| `windowcontainer.style` `"2"` = Volumetric；`defaultsize` 格式 `宽x高x深`，单位默认 dp，depth 仅 Volumetric 有效；`materialbackground` `"0"` = 关闭毛玻璃 | 同上，Property list |
| 非默认 Stage 用 `mainApp` DSL 的 `Stage(id = ...)` 声明即可，**不必**同时写进 manifest | `documentation/spatial-sdk_spatial-container_manage-stages_declare-a-stage.md` |
| `openStage(id, style, bundle, upperLimbRenderMode)` 是 **suspend** 函数，返回 `OpenStageResult`，`bundle` 是官方的跨容器传参通道 | `api-reference/com.pico.spatial.ui.platform.containers.md`（`SpatialNavigator`） |
| `closeStage()` 是 suspend 函数；同一时刻只能有一个 Stage 打开，因此不需要传 id | `documentation/spatial-sdk_spatial-container_manage-stages_open-or-close-a-stage.md` |
| `minimizeWindowContainer(id, tag)` / `restoreWindowContainer(id, tag)` 均存在，返回 `Boolean`，且 KDoc 明确 **"can only be used when there is a stage open"** | `api-reference/com.pico.spatial.ui.platform.containers.md` L128–184 |
| Volumetric 窗口同样用 `SpatialView` 承载 3D 场景，2D 文本面板经 `AttachmentPanel` 贴在 3D 空间的指定位置 | `documentation/spatial-tutorial_build-a-spatial-app-from-a-template_stage-2-add-3d-windows-and-models.md` |
| `LocalSpatialContainerStateManager.current.isFocused` 是 `State<Boolean>`，可用于监听窗口重新获得焦点 | `documentation/spatial-sdk_spatial-container_manage-windowcontainers_manage-the-lifecycle-and-state-of-a-windowcontainer.md` L69 |
| Mesh 使用建议明确「避免为每个实例创建不同的 mesh / material」，大量重复实例应优先 `MeshInstancesResource.create(name, list)` 批量创建 | `documentation/spatial-sdk_resource-management_mesh.md` L243–244 |

**由取证直接推出的一条硬约束**：`restoreWindowContainer` 只在 Stage 打开时可用，所以返回路径**必须先 restore 窗口、再 closeStage**，顺序不可交换——反了之后没有任何 API 能把最小化的配置窗口叫回来。

### 增量 1. 面板清单

**容器架构变更**：

| | 变更前 | 变更后 |
|---|---|---|
| 默认容器 | Stage（`pico.spatial.stage.id="SpaceCubeStage"`, `style="1"` Mixed） | **WindowContainer**（`pico.spatial.windowcontainer.id="SpaceCubeConfigWindow"`, `style="2"` Volumetric） |
| 游戏容器 | 即默认容器 | **非默认 Stage**，DSL `Stage(id = "SpaceCubeStage")` 声明，运行时 `openStage(id, StageStyle.Mixed, bundle)` 打开 |
| 开始界面 | Stage 内的 UI 状态（`started == false`） | 配置窗口内的常驻内容；Stage 内**不再有**开始界面状态 |

**面板清单**：

| 面板 id | 所属容器 | 面板尺寸 | 层级/父面板 | 出现时机 |
|---|---|---|---|---|
| config_card | 配置窗口（AttachmentPanel in SpatialView） | wrap（CandyCard 自适应） | SpatialView 根 | 常驻；仅 `showAppearanceSettings` 为真时隐藏自身。`showGameplayInfo` 为真时**卡片保持不动**，scrim + 说明卡片沿用 v5 的 Box 叠层写法叠在它上面（原表述“或 `showGameplayInfo` 为真时隐藏自身”与同格要求的 v5 叠层写法自相矛盾；实现按 v5 叠层执行，此处按实现更正文字） |
| appearance_settings | 配置窗口（AttachmentPanel） | wrap | SpatialView 根 | `showAppearanceSettings == true` |
| gameplay_overlay_scrim / _card | 配置窗口（config_card 面板内的 Box 叠层） | 见 v5 增量 2 | config_card 内 | `showGameplayInfo == true`，**行为与 v5 完全一致，逐字搬运** |
| piece_preview（3D，非面板） | 配置窗口 SpatialView 的 ECS 内容 | 4 个 cube + 1 块底板薄片 | SpatialView 根（挂在一个 `previewAnchor` Entity 下） | 常驻 |
| score_hud / next_piece / pause_button / pause_overlay / game_over_overlay / exit_confirm_overlay | 游戏 Stage（AttachmentPanel） | 不变 | 不变 | 不变 |
| ~~start_screen~~ | — | — | — | **删除**（内容整体迁往 config_card） |

配置窗口 manifest 属性：

| meta-data | 值 | 理由 |
|---|---|---|
| `pico.spatial.windowcontainer.id` | `SpaceCubeConfigWindow` | 唯一 id，`minimize`/`restore` 按它定位 |
| `pico.spatial.windowcontainer.style` | `2` | Volumetric。用户在门禁 A 明确选择：虽然口头说"平面窗口"，但需要 3D 方块有真实体积，Planar 的 640dp 深度上限会把造型压成浮雕并可能被系统截断 |
| `pico.spatial.windowcontainer.defaultsize` | `900x760x600` | dp。容纳 CandyCard + 前下方一个 tetromino；具体数值在门禁 B 第 1 轮截图后可微调 |
| `pico.spatial.windowcontainer.materialbackground` | `0` | CandyCard 自带不透明底色。再叠一层系统毛玻璃 = 两个共面半透明层，是真机摩尔纹的已知诱因（见项目记忆 basicsheet-preferred-over-handrolled-overlay）。主窗口的毛玻璃只能在 manifest 关，DSL 侧没有这个参数 |
| `pico.spatial.windowcontainer.volumebasepanel` | 不设置（用默认 `"0"`，交互时显示基座） | 基座是用户找到窗口边缘、拖动/缩放窗口的视觉依据，不主动关掉 |

### 增量 2. 元素表

config_card 内部的 2D 元素**逐项等价搬运自现 `start_screen`**，位置顺序、颜色角色、字体角色、尺寸全部不变，故不重复列举（见 v1/v2/v4/v5 增量的元素表）。搬运清单：`gameplay_button`（"玩法"）→ 标题"空间方块" → "历史最高 N" → 难度三连按钮 → "外观设置"按钮 → 两行手势说明 → `start_button`（"开始游戏"/"加载中"）。`appearance_settings` 面板内的两个 Picker 同样原样搬运。

本增量**新增**的元素：

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 颜色/材质角色 | 形状 |
|---|---|---|---|---|---|---|---|
| preview_anchor（实现中的常量与实体名为 `PREVIEW_ROOT_*` / `previewRoot`） | Entity（无渲染，仅做变换父节点） | SpatialView content 根 | 窗口中心 | `Vector3(0f, PREVIEW_ROOT_Y_M, PREVIEW_ROOT_Z_M)`，**as-built** `y = -0.20f`、`z = +0.12f`（窗口本地坐标，米；设计初值曾写 `y = -0.22f`，该值标注为门禁 B 可调项，门禁 B 第 1 轮已通过，故此处记录落地实值而非回改代码） | — | — | — |
| preview_cubes | 4 个 `ModelEntity`（`MeshResource.createBox`） | preview_anchor 子节点 | 按随机选中的 `PieceType.spawnCells` 裁剪到占位包围盒后居中排布 | 单元格 `PREVIEW_CELL_SIZE_M = 0.05f`，间隙 `0.006f`（与棋盘同尺度，便于直观对应） | 由当前选中的 `PieceMaterial` 决定：JELLY 走 `candyJellyColorFor(type)` + `UnlitMaterial`；其余 4 种走 `PieceMaterialLoader` 解析出的 PBR `Material`。**与"方块材质"选择实时联动** | 圆角立方体（`cornerRadius = 0.008f`，与棋盘一致） |
| preview_base_plate | 1 个 `ModelEntity`（薄片 box） | preview_anchor 子节点 | 位于 preview_cubes 正下方，顶面与方块底面贴合 | 宽深覆盖 tetromino 最大包围盒 + 少量余量，厚 `0.02f` | 由当前选中的 `BasePlateMaterial` 决定，走 `BasePlateMaterialLoader`。**与"底板材质"选择实时联动** | 圆角薄片（`cornerRadius = 0.01f`） |

坐标数值（`PREVIEW_ROOT_Y_M` / `PREVIEW_ROOT_Z_M` / 窗口 `defaultsize`）标注为**门禁 B 可调项**：透视截图判不出精确 dp，第 1 轮截图只核对「造型是否完整落在窗口内、是否与卡片重叠、是否被窗口边界截断」，不核对具体偏移量。

### 增量 3. 状态清单

| 状态 | 归属 | 取值与视觉变化 |
|---|---|---|
| `previewPieceType` | 配置窗口 | `PieceType.entries.random()`。配置页创建时摇一次；每次窗口 `isFocused` 由 `false → true`（即从游戏返回）重摇一次。**只展示一个**随机 tetromino，不展示全部 7 种 |
| `selectedDifficulty` / `selectedBasePlateMaterial` / `selectedPieceMaterial` / `showAppearanceSettings` / `showGameplayInfo` | 配置窗口 | 语义与现状一致，只是宿主从 `GamePage` 改为 `ConfigPage` |
| `highScore` | 配置窗口 | 从 SharedPreferences 读。窗口 `isFocused` 由 `false → true` 时**重读**（游戏结束后分数在 Stage 侧写入，窗口需要刷新才能看到） |
| `launching` | 配置窗口（新增） | `false`：`start_button` 显示"开始游戏"、可点；`true`：显示"加载中"、禁用。点击后立即置 `true`，直到 Stage 关闭回到窗口才置回 `false`。**取代**现状里由 `sceneReady` 驱动的同一视觉变化 |
| ~~`started`~~ / ~~`sceneReady` 对 `start_button` 的门控~~ | 游戏 Stage | **删除**。Stage 一旦打开就是"要玩"，不再有开始界面态。`sceneReady` 本身保留，改为触发"收起配置窗口"的信号 |
| 场景自由拖拽门控 | 游戏 Stage | 由 `!started` 改为 `!isPlaying`——加载中、暂停中、游戏结束态都能单手捏合拖动整个场景摆位（原本这个能力挂在开始界面上，开始界面迁走后需要新的归属） |

### 增量 4. 文案清单

**无新增文案。**全部文案原样搬运自现 `start_screen` / `appearance_settings` / v5 玩法弹层。唯一的文案**语义**调整：`start_button` 的"加载中"含义从「棋盘正在构建，还不能开始」变为「已按下开始，Stage 正在构建棋盘」——字面不变。

### 增量 5. 不做清单（YAGNI 边界）

- 不改 `game/` 包任何游戏逻辑（47 个单测应全部原样通过）
- 不改手势输入方案（V1/V2）、手柄输入、`HeadHeightCalibration`、`BoardCubeRenderer` 的渲染算法与动画缓动
- 不改任何材质资源、材质枚举、Picker 组件本身
- 不给配置页的 3D 造型加旋转/浮动动画——`withFrameNanos` 在 WindowContainer 里实测只触发一次（见项目记忆 withframenanos-dead-in-windowcontainer），要动就得另起 `delay` 循环，本增量不做
- 不展示全部 7 种 tetromino，只展示 1 个随机的
- 不做 `MeshInstancesResource` 批量实例化改造（L3）——文档确实推荐，但要重写渲染路径 + `customFloatData` + ShaderGraph 材质，与现有材质选择器冲突，单独开轮
- 不做共享 `UnlitMaterial` 改造（L2）——等 L1 的实测数字再定
- 不优化 AssetBundle 体积、不改 `BaseMaterialsBundle` 的 Mutex 结构
- 不新增色值、不新增组件、不引入 `backgroundMaterial`、不引入 Material/Material3

#### 本增量**包含**的性能改动（范围明确限定为两项）

1. **计时埋点**：`BoardCubeRenderer.attachTo()` 内对 `UnlitMaterial.create` / `MeshResource.createBox` / `ModelEntity()` / `addChild` 四步分别累计耗时，构建结束打一条汇总日志。目的是把"60–95s 到底花在哪"从注释里的一句经验描述变成可引用的实测数字。
2. **L1 共享 mesh**：`createCube()` 里 184 次参数完全相同的 `MeshResource.createBox(Vector3(0.05,0.05,0.05), cornerRadius=0.008f)` 提取为一次创建、全部复用。SDK mesh 文档明确建议「避免为每个实例创建不同的 mesh」。若实测无改善，一行回滚。

**预热的结论（回答用户"能否提前布置"）**：184 次实体创建**搬不动**——实体必须建在 Stage 自己的 `SpatialContent` 上，而该 content 在 `openStage` 之前不存在。同进程内唯一能提前的是 `BaseMaterialsBundle` 的 ~25MB AssetBundle 加载，而配置页的 3D 预览本来就要用它，**这份预热是白送的，不需要额外代码**。真正的时间大头只能靠"减少往返次数"（L1/L2/L3）而不是"提前做"。

### 增量 6. 截图验证计划

棋盘构建实测 60–95s，而门禁 B 的设备锁预算是 90s / 硬上限 120s，**一轮锁内装不下**，故拆两轮。

**第 1 轮（配置窗口，锁内预算 ~30s）**

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 启动默认态 | 启动 App，等待渲染 | 出现的是**窗口**不是全沉浸（可见窗口标题栏/基座）；config_card 六项原有元素齐全、顺序与现状一致；preview_cubes 显示**恰好一个** tetromino（4 个方块）且完整落在窗口内、未被边界截断、未与 CandyCard 重叠；preview_base_plate 在其正下方 |
| 2 | 外观设置切换材质 | 点"外观设置" → 切换一个"方块材质" → 点"完成" | appearance_settings 面板正常弹出且两个 Picker 齐全；关闭后 preview_cubes 的材质**肉眼可辨地变了**；preview_base_plate 同理（切底板材质时） |

**第 2 轮（进入游戏，单独一轮，预算拉到 120s 硬顶）**

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 3 | 游戏态 | 点"开始游戏" → 等待 `sceneReady` → 截图 | 已进入全沉浸（无窗口 chrome）；棋盘 + score_hud + next_piece + pause_button 正常；**配置窗口已不可见**（已被 `minimizeWindowContainer` 收起） |

第 2 轮很可能撞 120s 硬上限。撞了就**如实记"不可判定（超时）"**，立即 `app stop` + `release`，不占着设备硬撑。

**日志交叉核对（不可省略）**：本项目历史记录里模拟器截图可能是陈旧帧（实测差 ≥6s），且 `pico-cli capture` 拍不到空间内容、需改用 `adb screencap`。因此除截图外，必须用 logcat 锚点交叉核对（`grep` 读设备日志一律加 `-a`，否则控制字节会让 grep 把 logcat 当二进制静默跳过）：

- `openStage` 调用与返回的 `OpenStageResult`
- `attachTo` 的分阶段耗时汇总（同时是 L1 效果的判据）
- `minimizeWindowContainer(...)` / `restoreWindowContainer(...)` 的 `Boolean` 返回值 —— **这两条是 API 行为的唯一证据**，截图判不出
- `closeStage()` 前后的容器生命周期事件

**已知待验证假设（代码无法自证，必须靠运行时证据）**：

1. Stage 打开后、`minimizeWindowContainer` 之前，配置窗口是否仍然可见？整个"加载期窗口留守显示加载中"的方案建立在这个假设上。若实测不可见，退回备选：Stage 内挂一张"加载中"卡片。
2. `closeStage()` 之后，被 restore 的窗口是否确实回到前台并重新获得焦点（进而触发 `isFocused` 刷新最高分与重摇 `previewPieceType`）？
3. 非默认 Stage 是否与配置窗口跑在同一进程？若不同进程，`GameSettings` 单例会静默失效——本增量已因此改用 `openStage(bundle = ...)` 官方通道传难度，但该假设仍需日志确认。

---

## v7 增量（2026-09-21）：外观设置里两个材质选择器上下对调

### 增量 0. 元信息

- 用户诉求原话：「外观设置中方块材质与底座材质的顺序换一下」
- 设计源类型：一句明确的排序要求，无歧义，本节即设计源
- 用户确认：诉求本身已把结果完全指定（两者对调），未再单独回问；如与预期不符，一行即可回退

### 增量 1. 面板清单

无变化。仍是 `appearance_settings`（配置窗口内的 AttachmentPanel）。

### 增量 2. 元素表

| id | 变更 |
|---|---|
| `PieceMaterialPicker`（自带标题「方块材质」） | 由 Column 内第 2 个子项 → **第 1 个**（紧接「外观设置」标题之后） |
| `BasePlateMaterialPicker`（自带标题「底座材质」） | 由 Column 内第 1 个子项 → **第 2 个** |

`Column` 的 `spacedBy(14.dp)` 不变，两个 Picker 的尺寸、配色角色、字体角色、圆角均不变，「完成」按钮仍在最后。

### 增量 3. 状态清单

无变化。`selectedPieceMaterial` / `selectedBasePlateMaterial` 的读写与持久化逻辑一字未动，只是渲染顺序互换。

### 增量 4. 文案清单

**无新增、无修改。**两个标题「方块材质」「底座材质」本来就分别写在各自的 Picker 组件内部，本次未触碰这两个文件。

### 增量 5. 不做清单（YAGNI 边界）

- 不改两个 Picker 组件自身（`PieceMaterialPicker.kt` / `BasePlateMaterialPicker.kt` 零改动）
- 不调整间距、不加分隔线、不改标题层级
- 不改动两者的持久化 store 或联动预览的逻辑

### 增量 6. 截图验证计划

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 外观设置展开态 | 配置窗口点「外观设置」 | 自上而下依次为：标题「外观设置」→「方块材质」色板行 →「底座材质」色板行 →「完成」按钮 |

**模拟器无法完成此项**：本项目已实测模拟器上无任何输入通路可点到 SpatialUI 控件（`input tap` 到得了 `ViewRootImpl` 但点不中、`uiautomator dump` 为空树、按键焦点遍历无响应，见 v6 门禁 B 记录）。因此本增量的视觉核对只能在真机上完成，或由人在模拟器 GUI 窗口内手动点击。代码侧的可核对事实是：`ConfigPage.kt` 的 `appearance_settings` 面板内，`PieceMaterialPicker` 的调用现在排在 `BasePlateMaterialPicker` 之前。
