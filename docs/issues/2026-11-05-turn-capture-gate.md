# 回合快照的捕获门用错了：把恢复门当捕获门，一局只录到 1–2 条

0.6.0-beta.3 里 `TurnRecorder.observe()` 直接把 `TurnAdapter.stable()`（= `restoreReady()`）当捕获门。`restoreReady()` 是“把快照写回游戏”的条件，比“复制一份状态”严格得多，于是玩家能操作的回合里它几乎不成立，`observe()` 静默返回。

## 现象与数据

同一台机器、同一套 mod（BaseMod 5.56.0、StSLib 2.12.0、RunHistoryPlus 1.8.3、SuperFastMode 1.0.8、HideInfo 0.0.1、lolifontmod 0.2、CardImageReplacer 2.0）：

| 构建 | 局 | 战斗数 | `turn_snapshot` recorded | missing |
|---|---|---|---|---|
| PR #23（`mod_version` 仍写 beta.2） | 42daf9e7 | 6 | 48 | 0 |
| PR #23 | cf56e9da | 7 | 78 | 0 |
| PR #23 | 6ca6d455 | 7 | 40 | 0 |
| 0.6.0-beta.3 | 864f629c | 6 | 1 | 1 |
| 0.6.0-beta.3 | ef47346a | 2 | 0 | 2 |
| 0.6.0-beta.3 | e38b5954 | 1 | 0 | 1 |

beta.3 那局 6 场战斗只建立了 2 个 `combat_id`，一条事件落在 turn 1（`recorded`），另一条落在 turn 1 且 `missing / first_action_or_opening_discard_already_observed`，其余战斗一条事件都没有。楼层存档正常（10 层 10 个 `floors/*.autosave`），所以问题只在回合层。

## 根因

`restoreReady()` 要求 `turnStartCurrentAction == null`、`monsterQueue` 空、四组 effect 全空、`phase == WAITING_ON_USER`、`endTurnButton.enabled`。这些条件用来判断“现在能不能把快照写回去”，在玩家真正可以操作的帧里常常有一项不成立。再叠加 `observe()` 里“已经出过牌就记 `missing`”的规则，结果就是：要么一条不写，要么写一条 `missing`。

## 修法

- 捕获改用 `TurnAdapter.capturable()`：战斗房间、无界面、`cardQueue / actions / preTurnActions / currentAction` 空、`!turnHasEnded`、`!isEndingTurn`。`restoreReady()` 保留给恢复路径（`TurnAdapter.restore` 的“等空闲回合”判断）。
- 出过牌也照样捕获，用 `played` 与 `reason`（`player_turn_ready` / `mid_turn_after_N_cards`）标出这一个是回合开头还是回合中途；`missing` 不再用作“出过牌”的同义词。
- `TurnRecorder.blocker` 记录最后一次停下来原因，历史页在“这层没有快照”时把它翻译成一句人能看懂的话，不再只有“无法观测”。
- 同一场战斗在 S/L 与重启后保留 `combat_id`：读档前置 `pendingLoad`（`Hooks$SaveLoaded`），`battle()` 用 `TurnRecorder.nextCombat()` 判定“读档回到原战斗 / 换层是新战斗”，重启时从 `manifest.last_combat` 读回；`Recorder` 记 `branchParent` / `branchParentSnapshot` / `branchReason`，`TurnFiles.publish()` 维护 `last_combat`。

## 与既有决定的关系

`docs/architecture.md` 的 P4 原本把“放宽录制条件、在出牌后补录回合中途快照”列为**不采纳**，理由是“重打会落在回合中间、不再检查进行中的特效”。上面的数据显示这条决定的实际后果是一局只录到 1 条：能重打的回合反而没了。本改动撤销该决定，保留的约束是“动作队列与手牌队列必须为空、没有打开界面”，只放弃“特效队列必须为空、回合阶段必须是 WAITING_ON_USER、结束回合按钮必须可用”这三项；落点由 `played` / `reason` 标明。需要维护者确认后再定稿。

## 没有采纳的选项

| 选项 | 不采纳的理由 |
|---|---|
| 只放宽 `restoreReady()`，让它对捕获也宽松 | 恢复路径必须严格，放宽会同时放松“能不能写回去”的判断；两个用途本来就该有两个门 |
| 等回合稳定后再补录，但不标 `played` | 玩家与复盘都分不清这个点是回合开头还是中途，重打会莫名其妙少一张牌 |
| 把 `missing` 保留成“出过牌”的标签 | 会继续把“录到了”和“没录到”混在一起，统计与覆盖率无从计算 |
