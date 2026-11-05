# 卡牌屏还开着就点地图走进下一层，这一屏选牌整屏丢失

原版游戏就有的行为，不是本 Mod 引入的，但会让记录少一屏奖励。

## 现象

打赢一场战斗、卡牌屏给出三张牌之后，不选牌也不按跳过，直接按 M 打开地图点下一层的节点，游戏会正常走进下一场战斗，这一屏选牌在记录里完全消失：既没有 `decision`，也没有 `floor` 里的牌组变化。

2026-11-04 的诊断报告（`spire-ledger-report-20261104-221714-77376d2e.json`）里有直接证据：第 2 层结束时牌组 12 张，第 3 层 `floor_enter` 也是 12 张，两个 `floor` 记录之间没有任何 `decision`。

## 根因（已定位，看的是原版 `desktop-1.0.jar` 字节码）

- 地图点击走 `MapRoomNode` → `AbstractDungeon.nextRoomTransitionStart()`（只做淡出和隐藏继续按钮），随后才进 `AbstractDungeon.nextRoomTransition(SaveFile)`。
- `nextRoomTransition(SaveFile)` 的第一件事就是 `AbstractDungeon.combatRewardScreen.clear()`，之前不做任何“未领取奖励”的处理。原版自己也不记录这一屏，所以这是原版的行为。
- 本 Mod 原来只有两条记跳过的路：`ProceedButton.update` 里 `closeCurrentScreen` 的 `Hooks$FinalAbandon`，以及跳过键 `SkipCardButton` / `CardRewardScreen.skippedCards`。点地图两条都不走。
- 而且 `StateMod.abandon()` 还有 `AbstractDungeon.screen == COMBAT_REWARD` 的前置判断；点地图那一刻 `screen` 已经是 `MAP`（卡牌屏还开着时是 `CARD_REWARD`），于是即使用继续按钮也会漏。

## 修法

`StateMod.leaveRoom()` 挂在 `nextRoomTransition` 的前缀上，正好在原版 `clear()` 之前，所以在这里把还挂在 `combatRewardScreen.rewards` 里的 `CARD` 奖励补记成跳过：

- `candidates` 用 reward item 自己的 `cards`，也就是当时给出的三张牌；`source` 仍是 `COMBAT_REWARD`。
- 决策里多一个 `bypassed`：`room_exit`（离层）或 `proceed_button`（继续按钮）。这是新增字段，schema 只增不改。
- 已经点过牌、歌唱碗或跳过键的屏由 `StateMod.seen` 与该决策的 `reward_key` 挡住，不会重记。

按 metrics.md 5.1，跳过本来就算一屏奖励，所以这屏进奖励屏分母、进跳过率，不再被当成“缺失”。

## 补写进原版 `.run`

只补账本还不够：原版 `.run` 的 `card_choices` 里同样缺这一屏，别的工具（RunHistoryPlus、TinyCat、原版战绩页）读这局时看不到这一层选过牌，卡牌 RNG 那一段就断了。

原版只在两处调 `RewardItem.recordCardSkipMetrics()`：`ProceedButton.update` 里离屏前对每个 `CARD` 奖励各调一次（字节码偏移 689），以及跳过键。点地图两条都不走，所以 `.run` 里就是缺的（docs/m1-field-gaps.md 第 7 条说的 Boss 层缺项是同一类问题）。

修法：`StateMod.flushRewards()` 补记账本之后再调一次原版这个方法（`vanillaSkip()`）。这个方法只做一件事——把 `{picked:"SKIP", not_picked:[三张牌], floor:当前层}` 追加到 `CardCrawlGame.metricData.card_choices`，所以效果就是 `.run` 里多一条这一层选过牌、跳过、候选是这三张。

去重：继续按钮那条路原版已经写过，`vanillaSkip()` 先扫 `card_choices`，同层、`picked=SKIP`、`not_picked` 集合相同就返回；点过牌或歌唱碗的屏已经从 `rewards` 里摘掉，不会走到这里。

## 验证

- `integration.ps1 -BypassOnly -WaitForExit`：原生探针把卡牌屏打开、`screen` 改成 `MAP`，再走真实的 `nextRoomTransition`，断言多出一条 `skip` 决策、`bypassed=room_exit`、候选是给出的牌加跳过、并且能落定。**已通过**：隔离实例里 `probe-result.json` 为 `{"status":"pass","evidence":"real nextRoomTransition out of an open CardRewardScreen while the map screen is up"}`，决策是 `candidate_id=skip`、`bypassed=room_exit`、`candidates=[card:0, skip]`，随后 `result: confirmed`。
- 同一次探针还断言了 `.run` 侧：`CardCrawlGame.metricData.card_choices` 在那次离层后必须多出一条 `picked=SKIP`、`not_picked` 含当次候选的记录，否则判失败。
- 手测：docs/testing.md A15。

## 没有采纳的选项

| 选项 | 不采纳的理由 |
|---|---|
| 只在 `CardRewardScreen.open` 处截获 | 那一刻还没有“玩家会不会选”的结果，得一直挂着一个未完成状态；而且只挂着奖励列表、没进卡牌屏的情形拿不到 |
| 继续把这种屏当“缺失” | 奖励屏分母会少一屏，跳过率偏高；原版同样场景本身也不记，合并 `.run` 时口径会分裂 |
| 接管 `nextRoomTransition` / 改原版 `clear()` 的顺序 | 与原版房间切换（存读档、淡出、Boss 退出）耦合太紧，收益只是省一次前缀判断 |

## 关联

- 快捷键 `Ctrl+Shift+R` 的“按了没反应”是另一件事：`DiagnosticReport.pollShortcut()` 在 `CardCrawlGame.render` 前缀里轮询 `Gdx.input`，只有游戏窗口在前台时才会收到按键；同一份日志里 22:17:24 和 22:17:29 的 `PAUSE()` 说明当时窗口正在切走。现在窗口失焦（`CardCrawlGame.pause`）会重新武装快捷键，并且“上一份报告还在写”时会在设置页状态行给出提示，不再静默忽略。
