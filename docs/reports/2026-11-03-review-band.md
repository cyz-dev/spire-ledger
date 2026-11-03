# 复盘血条、底部控制带与 S/L 上限（2026-11-03）

分支 `replay-turns`，基线 `main` = 0.6.0-beta.2。用户报了三件事：复盘/练习里玩家血条压在腿上、`39/73` 的 `7` 被角色挡住；复盘底部「第几次」挤在左下角、整体不好看；反复 S/L 时分支会无限长，而且两次完全相同的快照不该存两份。

## 1. 结论

| 项 | 结果 |
|---|---|
| 血条压在腿上 | 根因是 pop-in 未收敛（`hbYOffset` 停在 +60 px），恢复后归零；探针断言 `hbar_offset = 0.0`、`hbar_alpha = 1.0` |
| 底部控制带 | 回合条在左、保存状态居中、`‹ 第 N 次 / 共 M 次 ›` 在右；不再有下拉压住按钮 |
| 同板去重 | 重复到达不再新增快照点，只把 `attempts` 加一（原来会多出一条 `duplicate` 点，占 `MAX_POINTS`） |
| 内存上限 | 每 `(楼层, 战斗, 回合)` 最多 8 份不同棋盘（保留第一份 + 最近 7 份），超出的按最老回收；单局预算 512 MB 不变 |
| 离线断言 | `build.ps1 -Check` 10 项全 PASS（`TurnFilesCheck` 新增去重与上限断言） |
| 原生探针 | `-LaunchPractice` PASS、`-ReplayCurrent` PASS（含新的尝试步进断言） |
| 部署 | `mods/SpireLedger.jar` 938,982 字节；仓库内 jar 0 张图片 |
| PR | 未提交（等用户确认） |

## 2. 血条：停在入场动画中途

### 2.1 事实

同一场复盘，玩家血条整条穿过小腿，`39/73` 的 `7` 被腿挡住；正常游戏里血条整条在脚下。截图里怪物血条正常，说明不是全局缩放。

`AbstractCreature.renderHealth()` 的定位是 `hb.y + hbYOffset`，而 `hbYOffset` 只有 `updateHbPopInAnimation()` 在 `hbShowTimer > 0` 时才会从构造函数初值 `HB_Y_OFFSET_DIST * 5`（1080 下 60 px）插值到 0。练习实例走的是 `recreateCharacter` → 恢复 → 进战斗，`showHealthBar()` 的 pop-in 从没跑过，`hbShowTimer` 一直是 0，于是血条永远停在「高出 60 px」的位置。`hbAlpha` 被快照里的 0 覆盖，只靠 `updateHbAlpha()` 每帧拉回 1，所以血条可见、位置错。

### 2.2 修法

`TurnAdapter.readyAfterRestore()` 末尾调用 `settleHealthBars()`：对玩家和场上每只怪物把 `hbShowTimer`、`hbYOffset` 归零，`hbAlpha`、`healthHideTimer` 置 1。恢复出来的棋盘是「这一回合已经打完」的静止状态，血条本该在终点位置。

### 2.3 证据

练习子进程的探针输出（`inst-ce653abf/replay-probe-result.json`）：

```
player_bar {"hbar_offset": 0.0, "hbar_timer": 0.0, "hbar_alpha": 1.0,
            "hb_bottom": 216.0, "bar_bottom": 216.0, "draw_y": 226.0}
monster[0].bar {"hbar_offset": 0.0, "hbar_timer": 0.0, "hbar_alpha": 1.0,
            "hb_bottom": 232.0, "bar_bottom": 232.0, "draw_y": 226.0}
frame_luminance {"restored-frame.png": 40.6, "mid-frame.png": 39.1}
```

`settled-frame.png` 里 `80/80` 完整可读、整条在脚下，怪物 `47/53` 同样完整。`checkRestoredBoard()` 现在有三条新断言：`|hbYOffset| < 0.001`、`hbAlpha > 0.99`、`bar_bottom <= draw_y`。

## 3. 底部控制带

### 3.1 问题

第一版把「尝试」下拉画在左侧详情列表下面（`x=0.06, y=0.150`），和「关闭账本」在同一条竖直线上；回合条从中线 `x=0.266` 开始，右边留出 20% 空屏；状态行「已保存 N 个回合」浮在控制带下方，和格子挤在同一高度。截图里就是「左下角一个孤零零的下拉 + 中间三格 + 一片空」。

### 3.2 设计

- 一条统一控制带（`y = 0.146–0.182`，宽 `0.228–0.944`），顶端一条 1 px 分隔线。
- 左：`回合` 标签（`x=0.245`）+ 翻页箭头 + 最多 10 个回合格子（`0.290–0.588`）。
- 中：保存状态右对齐到 `x=0.628`（原来是单独一行，现在填进带子的空段）。
- 右：`‹ 第 N 次 / 共 M 次 ›` 步进控件（箭头 `0.726` / `0.892`），只有 1 次尝试时不画箭头。
- 多次尝试的回合在格子底部画一条金色下划线（原来是一个点，看起来像标点）。
- 垂直方向一律 `renderFontCentered` 到带子中线 `y=0.165`，不再出现「文字压在带子下边缘」。

尝试按时间排序（`foldTurns` 改成 oldest→newest），默认选中最新一次；`selectAttempt(int)` 做夹取，右箭头往「更晚」走，语义和 `第几次` 一致。

### 3.3 证据

`replay-turn-menu.png` / `replay-attempt-menu.png`：

| 状态 | 带子内容 |
|---|---|
| 单次尝试 | `Turn  1 2 3 | Saved 3 turns · replay stops the solver first | Attempt 1` |
| 两次尝试 | `Turn  1 2 3 | Saved 3 turns … | ‹  Attempt 2 of 2  ›` |

探针断言：步进热区 `x=929.28`（1280 宽，右侧），`inside=true`；`selectAttempt(before-1)` 与回退都改变选中项；原来断言「attempts dropdown 在屏内」的检查换成这两条。

## 4. S/L 世界线的去重与上限

### 4.1 同板不再新增点

`TurnFiles.publish()` 命中相同内容哈希时，原来会 `entries.add(ref)` 写一条 `availability=duplicate` 的指针行。一局 BOSS 战反复 S/L 到同一块板，manifest 会长出几十条指向同一份 blob 的行，`MAX_POINTS=10000` 也被这些行吃掉。现在命中时只更新原点的 `attempts`（+1）、`last_seen_at`、`last_branch_id`，manifest 行数不变；`events.jsonl` 里的 `turn_snapshot` 事件照旧，分支树和训练样本的「到达过」信息不丢。

### 4.2 每回合上限

新增 `TurnFiles.TURN_ATTEMPTS`（默认 8，`-Dspireledger.turnAttemptCap` 可调）：按 `(combat_id, floor, turn)` 归组，超过上限时淘汰中间最老的几份，**保留第一份**（回合起点基线）和最近 7 份；被淘汰的点标 `evicted`、删掉对应 blob（无其他引用时），但 `attempts` 计数保留。淘汰和新增在同一次 manifest 原子写里落盘。

### 4.3 断言

`TurnFilesCheck` 新增：

- 同一块板两次 → manifest 只有 1 条点、`attempts=2`、磁盘 1 个文件、`duplicate_of` 指向它。
- 同一回合写 12 份不同棋盘 → 保留 8 条 `recorded` + 4 条 `evicted`，`captured_at=100`（第一份）与最后一份都在，磁盘文件数 8。

## 5. 顺带修的构建问题

本机 `JAVA_HOME` 是 JDK 8（IBM J9），`javac --release 8` 不存在，`build.ps1`、`adapt-savestate.ps1`、`integration-replay.ps1` 都会在第一步失败。三处改成先探 `javac -version`，JDK 8 时用 `-source 1.8 -target 1.8`，其余保持 `--release 8`。探测用 `$ErrorActionPreference='Continue'` 局部降级，避免 `2>&1` 把原生 stderr 当终止错误。

另外：`build.ps1` 的默认 `-Game` 是 `D:\SteamLibrary\...`，本机在 `C:\Program Files (x86)\Steam\...`；`-SaveStateSource` 默认缓存路径不存在，用工坊 `2489671162/SaveStateMod.jar`（sha256 `5aab52…`，与 `AdaptSaveState.SOURCE_SHA` 一致）。

## 6. 未做 / 待手测

- W13（重打两次看回合条与尝试步进）、W14（S/L 后同板只计一次 + 上限回收）、W15（练习窗口血条在脚下）仍需真人在 1080p/720p 各看一遍。
- `MAX_POINTS` 仍是硬上限，到顶后拒绝新点并记 `failed`；旧局（已有 `duplicate` 行的 manifest）不迁移，读的时候按 `recorded` 过滤，界面不受影响。
- 淘汰只按「最老」判定，没有做「同一回合内按局面差异聚类保留多样性」的更聪明策略；8 份的选择是经验值，后续可用真实 S/L 数据再调。
