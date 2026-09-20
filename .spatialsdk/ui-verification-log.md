# UI Verification Log

## Round 1 — 2026-08-12, hide V1 picker + default to V2

**Change under test**

1. `strings.xml` `app_name` → 妙妙方块
2. `GamePage.kt` `controlScheme` initial value → `V2_SYSTEM_GESTURE`
3. `GamePage.kt` start-screen control-scheme picker UI deleted
4. **Regression fix** `GamePage.kt` `SpatialView(initial = …)` now derives
   `v2ControlPlane.enabled` from `controlScheme` instead of hardcoding `false`
5. **Regression fix** `GamePage.kt` scene-drag anchor reset moved above the
   `!pieceControlEnabled` bail-out

**Build:** `./gradlew assembleDebug` BUILD SUCCESSFUL, zero warnings.
Requires JDK 17 — the machine's default JDK 25.0.2 fails with the bare message
`* What went wrong: 25.0.2`. Prefix with
`JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home`.

**Device:** real headset `PB3B4XJGL2090011G` (no emulator was used this round).

**Blocked before install:** first launch attempt was rejected by the platform —
`ActivityTaskManager: current full space don't allow start fullscreen`, because
`com.illusion.tossar` was left holding full space by an earlier session. No session
held the device lock, so it was force-stopped and ours launched.

### Result: PARTIAL — runtime signal PASS, screenshot NOT CAPTURED

| Check | Status | Evidence |
|---|---|---|
| App name shows 妙妙方块 | PASS | launcher DB: `Item@…('tech.illusion.spacecube','妙妙方块',…)` |
| Glass plane enabled at launch (was the bug) | PASS | logcat `SpaceCubeV2Gesture: control scheme = V2_SYSTEM_GESTURE, plane enabled = true` |
| No crash on launch | PASS | pid 13778 alive; no `AndroidRuntime`/`FATAL` from the app |
| Start screen has no 操作方式 row | **NOT VERIFIED** | screenshot unavailable |
| Glass plane visible behind the well | **NOT VERIFIED** | screenshot unavailable |
| 开始游戏 / 慢中快 buttons still respond (starvation check) | **NOT VERIFIED** | needs interaction |
| 暂停 reachable mid-play while detectors armed | **NOT VERIFIED** | needs interaction |
| Board does not teleport on pause after a pinch held through 开始游戏 | **NOT VERIFIED** | needs interaction |

**Why the screenshot failed:** `mScreenState=OFF` — the headset display is off
(off-head / idle). `screencap` returns a 50-byte non-PNG, and hand tracking reports
`supportState=NOT_IN_FULL_SPACE, state=PENDING`. The app process is subsequently killed.
This is a device-state limitation, not a tooling or app fault. Re-run the capture with
the headset awake and worn.

**Retrieval disclosure:** `pico-spatial-knowledge` MCP could not be queried — it reports
"Knowledge graph not configured (current directory is not a pico-cli setup project)".
SDK claims were validated against the 6.0.0 `-sources.jar` in the Gradle cache instead
(`spatialBom = 6.0.0`).

## Round 2 — 2026-08-12, gesture hint text on start screen

**Change under test:** two `Text` lines added to `start_screen`'s `CandyCard` Column,
between the difficulty row and 开始游戏, per design-contract.md v2 增量:
"旋转：注视方块，双击旋转方块" / "移动：手指捏合移动控制方块方向".

**Build:** `assembleDebug` BUILD SUCCESSFUL. **Device:** `emulator-5554`, installed
+ launched for the user to try directly (not stopped afterward, per their request
"安装到模拟器上供我体验").

**Result: PASS.** Screenshot `.spatialsdk/ui-verify/r2-start.png`:
- Both lines render, correct text, correct order (旋转 above 移动)
- Positioned exactly between the 慢/中/快 row and 开始游戏, matching contract
- Color reads as dim brown (CandyCardInkDim), not black
- Card auto-grew to fit, no clipping/overflow
- No leftover "操作方式" row
- 开始游戏 and difficulty buttons still visible/unobstructed

## Round 3 — 2026-08-12, loading state on start screen (v3 增量)

**Change under test:** `attach("start_screen")` moved before the ~185-entity board
build in `initial`, plus a `yield()`; new `sceneReady` state gates the difficulty
row / gesture hints / 开始游戏 button behind a "加载中…" placeholder until
`renderer.attachTo()` finishes.

### First attempt (r3-loading.png / r3-ready.png): INCONCLUSIVE, surfaced a real bug

Host (`vm_stat`) was under severe memory pressure (11GB compressor, ~100-200MB
physical free) during this attempt. Entity creation in `attachTo()`'s loop, normally
assumed cheap, measured at ~1 entity/second on-device via the native
`SpatialPack_SceneInspector` log tag - by the time my script's bounded wait expired
only 29 of ~185 entities existed, and logcat recorded a genuine
`handleAppException type=ANR` for `tech.illusion.spacecube` mid-build. Both
screenshots this attempt showed nothing but the passthrough room - window paint
(6.2s) and the first entities both landed after my screenshot timings, and the
process was killed by my own `app stop` before `attachTo()` finished. **Root cause
identified from this evidence**: a single `yield()` before the loop only lets
*one* frame through; with `initial` otherwise fully synchronous, ~185 entities at
this measured per-entity cost starves the main thread long enough to ANR
regardless of the caller-side reorder.

**Fix applied** (`BoardCubeRenderer.attachTo`): made the function `suspend`,
rewrote the `List(n) { … }` construction as an explicit loop, and inserted
`yield()` after every single entity (not batched - see `ENTITY_CREATE_YIELD_INTERVAL`
KDoc for why batching was rejected: a single entity alone measured close to the
ANR input-dispatch timeout under load).

### Second attempt (r4-state.png): fix validated by logs, screenshot confounded by another session

- `initial: board build took 69741ms` logged successfully - the loop completed
  end-to-end this time (still very slow - host was still under pressure - but
  crucially **no ANR was logged for `tech.illusion.spacecube` this round**,
  confirming the per-entity yield stops the watchdog from firing even at this
  degraded speed.
- The screenshot itself (`r4-state.png`) is **not usable as UI evidence**: it
  shows a system "**SpaceSnake isn't responding**" dialog, i.e. a different app
  from a different concurrent session on this shared emulator, hung and covering
  the whole screen. Not touched (not our session's app to dismiss). This also
  means the emulator's overall distress this round was at least partly caused by
  *another session's* misbehaving app, not solely by `SpaceCube`.

**Result: code fix CONFIRMED via logs (no ANR, build completes); UI appearance of
the loading state / ready state is UNVERIFIED BY SCREENSHOT** - blocked twice now
by host/emulator conditions outside this change (memory pressure, a different
session's hung app). Per the 2-round cap and "don't grind on a busy shared
resource," stopping here rather than attempting a third capture immediately.
**Recommended**: re-run just the screenshot check (no code change expected) once
the shared emulator is idle / host memory pressure has cleared.

### Third attempt (r5-ready.png) and fourth (r6-ready.png): PASS

r5, taken immediately on detecting the `board build took` log line, still shows
another session's "SpaceSnake isn't responding" dialog on top - but it's
**semi-transparent**, and through it: "空间方块" title, "历史最高 0", and
**"加载中…"** are all legible. This confirms the loading state renders correctly
even though `sceneReady` had technically just flipped - Compose's own
recomposition of the panel texture lagged a beat behind the state write under
this host's continued slowness (a verification-script timing gap, not an app bug).

r6 added an 8s buffer after the same log marker before capturing. Same other-session
dialog still overlays (semi-transparent, not touched), but through it the **fully
ready state** is confirmed:
- "空间方块" + "历史最高 0"
- 慢/中/快 difficulty row
- "旋转：注视方块，双击旋转方块" then "移动：手指捏合移动控制方块方向" (correct order,
  correct wording, both legible)
- "开始游戏" button (green)
- No "加载中…" residue, card not clipped

**Result (v3): PASS.** Both contracted states (加载中 and 就绪) verified by screenshot;
`initial: board build took <N>ms` logs confirm completion; no
`tech.illusion.spacecube` ANR in any of rounds 2-4 after the per-entity yield fix
(only round 1's pre-fix attempt ANR'd). The recurring "SpaceSnake isn't responding"
dialog across r4/r5/r6 is a different concurrent session's app on this shared
emulator and was correctly left untouched throughout, per
[[shared-emulator-across-sessions]].

## Round 4 — 2026-08-12, v4 增量: loading state folded into the start button

**Change under test**: replaced v3's separate "加载中…" placeholder with a single
button that reads "加载中" (muted `candyButtonColors(primary=false)`, `enabled=false`)
while `!sceneReady` and "开始游戏" (mint, `enabled=true`) once ready - difficulty row
and gesture hints now always visible, per user request + reference screenshot
("加载中应该同样以这个窗口显示，不过游戏开始改为加载中，并且无法进行点击").

**r7-loading.png / r7-ready.png**: both landed AFTER `sceneReady` flipped (host load
had eased - board build finished within the 8s pre-screenshot delay this time), so
both show the identical ready state: title, 历史最高, 慢/中/快 (中 selected), both
gesture-hint lines, green "开始游戏" button. Confirms the ready branch renders
exactly per contract, cleanly (no other session's dialog this round).

**r8-loading.png**: retried with tighter timing (screenshot fired the instant the
`initial: attaching start_screen before board build` log line appears, which
precedes `renderer.attachTo()` entirely, so `sceneReady` is guaranteed false at that
point) - screenshot still shows only empty passthrough. The window's own first-paint
apparently hadn't happened yet at that instant (matches the ~6s launch-to-visible
gap measured in Round 3); host load fluctuated too much across attempts to pin the
now-narrow loading window with simple sleep-based timing.

**Result: ready state PASS (clean screenshot). Loading-state button styling
("加载中" + muted color + disabled) NOT confirmed by screenshot this round** -
stopped after 2 lock rounds rather than continue grinding the shared emulator per
[[shared-emulator-across-sessions]]. Confidence is still high without it: the
change is a plain three-way ternary keyed on the same `sceneReady` boolean whose
transitions are already confirmed correct via logs (this round) and via an
equivalent branch's screenshot (Round 3 r5, same condition, same position, only the
v3 text-line presentation differs from v4's button-label presentation); the "muted"
button style itself (`candyButtonColors(primary = false)`) is the exact style
already visually confirmed correct on every unselected 慢/快 difficulty button in
every screenshot this session. Re-run the loading screenshot on request if stronger
evidence is wanted.

**Key SDK fact established this round (worth keeping):**
`SpatialView`'s `initial` is a **suspend** lambda that the SDK runs via
`scope.launch { … }` on `Dispatchers.Main` from inside `AndroidView`'s `update` block
(foundation-6.0.0-sources `SpatialView.kt:86, 93, 139-146`). It therefore lands on a
*later* main-thread message than the composition's effect dispatch — so a
`LaunchedEffect` declared earlier in the same composable generally runs **first** and
`initial` overwrites it. Never let `initial` hardcode a value that an effect also owns.
If ordering must be guaranteed, use `SpatialView`'s `update` parameter, documented to run
after `initial`.

---

## v6 门禁 B（2026-09-20）：配置窗口 + 按需 Stage

APK：`app/build/outputs/apk/debug/app-debug.apk`（commit 1659625）
设备：`emulator-5554`（AVD `PICO_6.0`，2160x2158 @ density 320）

### 第 1 轮：配置窗口 —— 通过

截图：`.spatialsdk/ui-verify/v6-r1-1-default.png`、`probe-1..5.png`
日志：`.spatialsdk/ui-verify/v6-r1-logcat.txt`

| 核对项 | 结论 | 证据 |
|---|---|---|
| 是窗口不是全沉浸 | ✅ | 容器日志 `name=SpaceCubeConfigWindow, t=window_group, wcStyle=2`，系统为它建了 caption bar（`requestCreateCaptionBar`） |
| manifest 尺寸生效 | ✅ | `size(1800, 1520, 1200)` @ density 320 = 900×760×600 dp，与 manifest 逐字一致 |
| config_card 七项元素齐全、顺序不变 | ✅ | 截图：玩法徽标 / 空间方块 / 历史最高 / 慢-中-快（中为主色）/ 外观设置 / 两行手势说明 / 开始游戏 |
| 恰好一个 tetromino、完整不被截断、不与卡片重叠 | ✅ | 5 次采样各 4 个方块，形状与日志 `preview attached: type=X` 一致 |
| 底板渲染出来 | ✅ | 截图可见白色薄板。**计划里担心的「更宽的板可能整个不渲染」没有发生** |
| 方块材质跟随类型 | ✅ | 见下方色相表 |
| 无崩溃 | ✅ | logcat 中 `FATAL EXCEPTION`/`AndroidRuntime` 计数为 0 |

**颜色核对（取预览区最饱和像素的色相，与形状无关的口径）：**

| 样本 | 日志类型 | 实测色相 | 期望色相 | 结论 |
|---|---|---|---|---|
| probe-1 | I | 189.5 | 191.5 | ✅ |
| probe-2/3 | T | 336.0 | 339.6 | ✅ |
| probe-4 | S | 147.8 | 152.0 | ✅ |
| probe-5 | O | 49.0 | 44.9 | ✅ |

> **一次自我纠错，记下来免得重犯**：最初用固定像素坐标采样，得出「O 是白的、材质没生效」的结论，还量到 R-B=+4。那是错的 —— 固定坐标在方块形状变化后就切到了高光面/背景。O 的 jelly 色 R=1.0/G=0.85 本就是极浅的奶油黄，在灰白房间里肉眼也接近白。改用「最饱和像素的色相」后七种色全部正确。这正是本项目已记录过的「阈值掩膜会随改色静默失效」陷阱。

**时序**：冷启动首帧 `FirstFrameRelease(Displayed)` 在启动后 **39 秒**，预览实体在 **43 秒**才创建。第一次用固定 `sleep 14` 截到的是**空房间**。改为轮询应用自己的 `preview attached` 日志锚点后才拿到有效画面。

### 第 2 轮：进入游戏 —— 不可判定（模拟器无输入通路）

**结论：无法在模拟器上点击配置窗口的任何 SpatialUI 控件。** 三种方式独立尝试并全部失败：

1. `adb shell input tap 1102 1139`（坐标取自截图里薄荷色按钮的像素质心）——
   事件**确实到达应用**：`ViewRootImpl( 6967): onInputEvent: MotionEvent ACTION_DOWN x=1102.0 y=1139.0`，
   随后 ACTION_UP 同点。但没有任何 `openStage` 日志。3D 截图的屏幕坐标与
   AttachmentPanel 的命中测试空间不是同一个坐标系。
2. `adb shell uiautomator dump` —— 产出 441 字节，只有一个 `bounds=[0,0][0,0]` 的节点。
   SpatialUI 内容不在标准视图/无障碍树里，无法定位控件。
3. `adb shell input keyevent`（TAB / DPAD_DOWN ×14 / DPAD_CENTER / ENTER）——
   Compose 焦点遍历无响应，无 `openStage`。

**因此以下各项仍未验证，必须在真机上或由人在模拟器 GUI 窗口里手动点击完成：**

| 未验证项 | 为什么重要 |
|---|---|
| `openStage` 的 `OpenStageResult` | 整个进入游戏的路径 |
| 配置窗口在 60–95s 棋盘构建期间**是否仍然可见** | 「加载期窗口留守显示加载中」整个方案压在这条假设上；不成立要退回「Stage 内挂加载卡片」 |
| `minimizeWindowContainer(...)` 的返回值 | 截图判不出，只能看日志 |
| `restoreWindowContainer(...)` + `closeStage()` 返回路径 | 顺序错了就再也回不到窗口 |
| `board build phases: material=…/mesh=…/entity=…/addChild=…` | **L1 共享 mesh 的效果完全未测**，总构建时间也拿不到 |
| score_hud / next_piece / pause_button 是否零尺寸 | Task 7 review 提出的 watch item |

**要完成第 2 轮，需要人工执行**：在模拟器 GUI 窗口里（或真机上）点一次「开始游戏」，然后：

```bash
ADB=/Users/zohar/Library/PICO/sdk/6.0/emulator/system-images/platform-tools/adb
"$ADB" logcat -d -v time | grep -a -E "/SpaceCubeConfig\(|/SpaceCubeHandGesture\(|/SpaceCubeBoardBuild\("
"$ADB" exec-out screencap -p > .spatialsdk/ui-verify/v6-r2-3-ingame.png
```

注意读设备日志一律加 `grep -a`；另外 tag `SpaceCubeConfig` 与系统的 `SpaceCubeConfigWindow`
是子串关系，必须用 `/SpaceCubeConfig\(` 这种带分隔符的写法，否则会被系统日志淹没。
