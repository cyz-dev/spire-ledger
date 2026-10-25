# Spire Ledger M1 字段缺口表

Last confirmed: not yet confirmed
Architecture: [architecture.md](architecture.md) · Product spec: [product.md](product.md) · Metrics contract: [metrics.md](metrics.md)（待主线程据本表起草）

本表是 M1 的调研产物（proposed）：列出 TinyCat 全部指标与 M2、M3 点名指标需要的字段，实测原版 `.run` 的覆盖率，对照 Spire Ledger 当前记录，给出缺口、单位和来源优先级。不含实现方案。所有数字来自文末列出的脚本，样本是本机 `D:\SteamLibrary\steamapps\common\SlayTheSpire\runs` 下的 **762** 个 `.run`。

## 1. 结论摘要

1. **本机 762 局没有任何 RunHistoryPlus 字段。** RunHistoryPlus 上游源码写入 16 个 `.run` 字段，本机 0 个出现（见第 3 节）。TinyCat 依赖的 `neow_bonuses_skipped_log`、`neow_costs_skipped_log`、`neow_bonus_log`、`shop_contents`、`potion_use_per_floor`、`score_breakdown`、`improvable_cards` 等在本机历史局上覆盖率都是 0%。M1 补的字段只对新录制的局生效，历史局无法回填；product.md 中“旧 `.run` 里有 RunHistoryPlus 字段时照读”在本机数据上不会触发。
2. **RunHistoryPlus 独有字段里，真正必须由 M1 新增的只有两类：尼奥四选项与代价、商店候选。** 其余要么原版已有替代（药水用 `potions_floor_usage`，有效商店数用 `path_per_floor` 的 `$`，击杀 Boss 数用 `damage_taken`），要么 Ledger 现有快照已能还原（`neow_bonus_log` 的效果可由尼奥决策前后的牌组差得到，`improvable_cards` 对应牌实例的 `misc`），要么 TinyCat 之外没有指标需要（吃一堑、炼制药水等 Meta 成长杂项，需用户决定是否做）。
3. **`gold_gained` 可直接取原版静态字段 `CardCrawlGame.goldGained`**。字节码核验：它只在 `AbstractPlayer.gainGold` 增加（`NEGATIVE MONEY` 与 Ectoplasm 分支提前返回，不计入），新局重置，读档从 `SaveFile.gold_gained` 恢复。原版 `.run` 不写它。
4. **architecture.md 的“305 局可从分数明细取精确金币”夹具在本机不可复现**：`score_breakdown` 本机 0/762。TinyCat 的 `validate_gold_estimate.py` 也说明只有触发金币成就的局才有该条。M2 的“真值金币夹具”需要 M1 之后新录的 `gold_gained` 局自建，或向 TinyCat 作者取数据（未核实其来源）。
5. **product.md 写“每层开始时的 HP 来自楼层快照”，现状做不到。** `floor` 记录要等 `Snapshot.settled()` 为真才写（`StateMod.java:111,117`；`Snapshot.java:51-53` 要求房间不在战斗阶段）。本机 39 局的战斗层 `floor` 记录 `room_phase` 全是 `COMPLETE`（普通 382/382、精英 212/212、Boss 77/77），即战后状态。第一层入层 HP（TinyCat 的 `floor0_hp`）没有记录，开局只有尼奥之前的 `coverage` 记录（`StateMod.java:78`）。
6. **“是否通过第三幕”能从原版 `.run` 稳定得到。** 规则“胜利，或 `damage_taken` 出现第四幕遭遇，或击败第三幕 Boss 的场数达标（A20 两场，其余一场）”在本机得 315 局，字段覆盖 762/762；与第四幕死亡局数的对照见第 7 节（本机 49 局有 `killed_by` 加 1 局无 `killed_by`，共 50，与主线程一致）。
7. **本机原版 `.run` 的 Boss 层选牌缺项。** 第一、二幕 Boss 战胜 1049 场，其中只有 819 场（78.1%）在该层有 `card_choices` 记录，有记录的里面仅 23 条是跳过。缺的 230 场与 RunHistoryPlus `BossCardSkippedRunHistoryPatch` 的用途一致（原版离开 Boss 奖励时不记跳过），但缺失原因本表未直接验证。原版 `.run` 的卡牌抓率因此在 Boss 层偏高；Ledger 对这类离开已有钩子（`Hooks.java:62-70`）。
8. TinyCat 文档对“被跳过尼奥选项的代价”自相矛盾（第 9 节），RunHistoryPlus 源码写的是 `drawback.name()`，倾向代价有记录。M1 无论如何记录完整四选项。

## 2. 样本范围（本机 762 局）

| 项 | 实测 | 可识别字段 |
|---|---|---|
| 总数 / 角色 | 762；Watcher 456、Ironclad 263、Silent 32、Defect 11 | `character_chosen` 100% |
| 进阶 | A20 587（77.0%）；A1–A19 169；非进阶 6 | `is_ascension_mode`、`ascension_level` 100% |
| 每日挑战 | 1 局（目录 `DAILY`，`is_daily`=true，`daily_mods` 有值，`special_seed` 非空）；目录与 `is_daily` 0 处不一致 | `is_daily`、`daily_mods`；`daily_mods` 仅 1/762 出现 |
| 无尽 | 0 | `is_endless` |
| 自定义模式 | 0 | `is_trial`（原版字段，值 0/762）。自定义模式是否置 `is_trial` 未核实 |
| 指定种子 | `chose_seed`=true 6 局；`special_seed` 非空 1 局（即每日局） | `chose_seed`、`seed_played`。`seed_source_timestamp` 非零 568 局，与“是否指定种子”无对应，不要用来判断 |
| 存档位 | 目录 `2_IRONCLAD` 69、`2_DEFECT` 1，共 70 局（A20 57、A1 13）；无 `0_`、`1_` 目录；其余 691 局在无前缀目录 | **`.run` 内没有存档位字段**，只能从目录名识别。Ledger 侧可取 `CardCrawlGame.saveSlot`（静态字段，javap 核验存在），目前未记录 |
| 完成状态 | 胜 265；有 `killed_by` 的死亡 452；既非胜也无 `killed_by` 45（其中 `floor_reached`=0 的 25 局） | 原版没有放弃标志；StatsIndex 把其中 43 局判为 `incomplete`、2 局因末层 HP≤0 判为 `death`（`StatsIndex.java:28-29`），TinyCat 把这 45 局全部算放弃 |
| 来源覆盖 | `relic_stats` 676 局（88.7%，缺的 86 局集中在 2024-05、2024-11/12）；`basemod:card_modifiers` 676 局；Ledger（State Notes）记录 39 局，其中 33 局能按 `play_id` 对上原版 `.run` | |

## 3. RunHistoryPlus 写入字段与本机覆盖

来源：`modargo/RunHistoryPlus`（GitHub，最近推送 2025-12-12）各补丁文件中的 `CtField.make(...)` 与 `addData(...)`。仓库根目录只有 `pom.xml` 和 `src`，没有 README 与 LICENSE，所以本表只读字段名，不引用其代码。原版字段集合用 `Metrics.gatherAllData` 的字符串常量核验：本机 53 个顶层键中 51 个在其中，其余两个是 `relic_stats`（Relic Stats）与 `basemod:card_modifiers`（按键名前缀判断为 BaseMod，未读其源码）。

| RunHistoryPlus 字段 | TinyCat 是否使用（代码位置） | 本机覆盖 | 处置 |
|---|---|---|---|
| `neow_bonuses_skipped_log`、`neow_costs_skipped_log` | 是，尼奥选取率与 Plackett-Luce（`Run.java:154-157`、`ClassStat.java:603-607`） | 0/762 | **M1 新增**（尼奥四选项与代价） |
| `shop_contents`（每店 `floor`、`relics`、`cards`、`potions`，无价格） | 是，有效商店数（`ClassStat.java:1944`） | 0/762 | **M1 新增**（商店候选，含价格） |
| `neow_bonus_log`（`cardsObtained/Upgraded/Removed/Transformed`、`relicsObtained`、金币与血量变化） | 是，尼奥删牌来源、火堆偏好重建（`ClassStat.java:1009,1408,1476`） | 0/762 | 由尼奥决策的前后快照还原，见第 5 节 |
| `potion_use_per_floor` | 是，药水使用（`Run.java:164-169`） | 0/762 | 原版 `potions_floor_usage` 可替代（只缺药水名） |
| `score_breakdown` | 是，击杀敌人与 Boss 数（`Run.java:716-735`）；金币真值夹具 | 0/762 | Boss 数用 `damage_taken`；敌人数用 `CardCrawlGame.monstersSlain`（可选）；金币用 `gold_gained` |
| `improvable_cards` | 是，Meta 成长（`Run.java:208-223`） | 0/762 | Ledger 牌快照已含每张牌的 `misc`（`Snapshot.java:21`） |
| `potions_obtained_alchemize`、`lesson_learned_per_floor` | 是，Meta 成长（`MetaScaling.java:31,33`） | 0/762 | 不建议记录，需用户决定（本机卡组含吃一堑 98 局，炼制药水 0 局） |
| `floor_exit_playtime`、`rewards_skipped`、`falling_options_log`、`green_key_taken_log`、`blue_key_relic_skipped_log`、`potion_discard_per_floor`、`potions_obtained_entropic_brew` | 否 | 0/762 | 不需要 |

## 4. 主表

列说明。**单位**：局、战斗（每场遭遇一个样本）、选择次数（每个决策或每次提供一个样本）、楼层、事件次数。**覆盖**：本机 762 局中必需键存在的局数，不含“键存在但为空”的情形。**精度**：精确、估算、不可得，写 M1 之后新录局的状态，括号内写本机历史局的状态。**来源优先级**遵循三条规则：局终结果与身份（`victory`、`killed_by`、`playtime`、`score`、`play_id`）以原版 `.run` 为先；局内瞬时事实与候选以 Ledger 记录为先；RunHistoryPlus 只作旧局补充，排在最后。同一字段多来源且同级事实冲突时标记冲突，不参与计算（product.md 来源合并）。

### 4.1 局级与筛选

| 指标 | 单位 | 需要的字段 | `.run` 覆盖 | RHP | Ledger 现状 | 缺口与补法 | 精度 | 来源优先级 |
|---|---|---|---|---|---|---|---|---|
| 胜率（心脏通关）、场次 | 局 | `victory`、`damage_taken`（含 `The Heart`） | 762（100%）；胜 265，其中心脏 255 | 否 | `game_play_id` 记录含 `victory`、`heart_kill`（`StateMod.java:98`）；StatsIndex 同样从 `damage_taken` 判心脏（`StatsIndex.java:30`） | 无缺口 | 精确 | `.run` > Ledger `game_play_id` |
| 通过第三幕（新事实） | 局 | 见第 7 节：`victory`、`damage_taken`、`floor_reached`、`ascension_level` | 762 | 否 | `Snapshot.capture` 有 `act`、`floor`（`Snapshot.java:26`），无显式事实；`Hooks.java:68-71` 已钩第三幕出口 | 记录显式 `act3_cleared`（第 5.4 节） | 精确 | Ledger > `.run`（推导） |
| 最快通关、连胜 | 局 | `playtime`、`timestamp`、`victory`、`character_chosen` | 762 | 否 | 无 | 无缺口 | 精确 | `.run` |
| 总楼层、最高分 | 局 | `floor_reached`、`score` | 762 | 否 | 无 | 无缺口 | 精确 | `.run` |
| 击杀 Boss 数 | 局 | 原法 `score_breakdown`；替代 `damage_taken`+`path_per_floor` | 原法 0；替代 762 | `score_breakdown` | 无 | 以 `damage_taken` 中的 Boss 战减去死在 Boss 的一场 | 精确（历史局亦可） | `.run`（推导） |
| 击杀敌人数 | 局 | `score_breakdown` | 0 | `score_breakdown` | 无 | 可选：结局记 `CardCrawlGame.monstersSlain`；非必需 | 新局精确，（历史局不可得） | Ledger > RHP |
| 平均卡组大小 | 局 | `master_deck` | 762 | 否 | 结局快照有牌组（`Snapshot.java:31`） | 无缺口 | 精确 | `.run` |
| 结果分类（胜、死、放弃、未完成） | 局 | `victory`、`killed_by`、末层 HP | 胜死可判 717；45 局无法判定 | 否 | `ending.outcome` 仅在死亡与胜利界面写（`Hooks.java:87-92`），菜单放弃只落到 `session_closed` 中断 | 记录“放弃”事件需要独立来源（未核实 `AbandonRun` 路径）；否则旧局的 45 局在筛选里为“未知” | 死、胜精确；放弃不可得 | `.run`（胜死）> Ledger（放弃、未完成） |
| 筛选项（进阶、职业、日期、种子、每日、无尽、存档位） | 局 | 见第 2 节 | 762；存档位只能取目录名 | 否 | 元数据有 `session_kind`、`dataset_origin`；无存档位 | 记录 `saveSlot` | 精确（存档位在 `.run` 只来自路径） | `.run` 字段 > 目录名；存档位 Ledger > 目录名 |

### 4.2 卡牌、尼奥、Boss 遗物

| 指标 | 单位 | 需要的字段 | `.run` 覆盖 | RHP | Ledger 现状 | 缺口与补法 | 精度 | 来源优先级 |
|---|---|---|---|---|---|---|---|---|
| 卡牌抓率（第一幕 / 第一幕后） | 选择次数（每次奖励屏每张被提供的牌） | `card_choices`（`floor`、`picked`、`not_picked`）、歌唱碗 | 762；Boss 层缺 230/1049 | 否（三个补丁只补跳过记录） | `decision` 含全部候选与跳过、歌唱碗、最终离开（`StateMod.java:82-93`，`Hooks.java:38-71`）；StatsIndex 仅统计 `COMBAT_REWARD`（`StatsIndex.java:58`） | 无缺口；注意本机历史 Ledger 局 851/851 个决策的 `action_source` 都是 `unknown` | 精确（`.run` 局 Boss 层偏高） | Ledger > `.run` |
| 标准化抓率（Plackett-Luce）、Tierlist | 选择次数（整屏为一个选择集） | 同上，需整屏 | 同上 | 否 | 同上，候选完整 | 无缺口 | 模型值 | Ledger > `.run` |
| 卡组卡牌胜率 | 张次 | `master_deck`、`victory` | 762 | 否 | 结局牌组 | 无缺口 | 精确 | `.run` |
| 尼奥选中次数、胜率 | 局 | `neow_bonus`、`neow_cost` | 757（99.3%，5 局为空）；代价非 NONE 272 | 否 | 无尼奥选项记录；仅 4/851 个决策是尼奥的选牌（`Snapshot.java:43`） | 见第 5.2 节 | 精确 | `.run` ≥ Ledger；两者冲突标记冲突 |
| 尼奥选取率、尼奥强度（PL、并列） | 选择次数（每局四选一） | `neow_bonuses_skipped_log`、`neow_costs_skipped_log` | 0 | 有 | 无 | **M1 新增**尼奥四选项与代价（第 5.2 节） | 新局精确，（历史局不可得） | Ledger > RHP |
| 尼奥开局删牌/变牌/升级/获得 | 局内事件 | `neow_bonus_log` | 0 | 有 | 牌组差可得（尼奥决策前后快照，含实例 ID） | 尼奥决策的 `after` 在效果落定后取 | 精确（变牌只知旧牌去、新牌来，不知对应） | Ledger > RHP |
| Boss 遗物选取率、胜率、换遗物 | 选择次数 / 局 | `boss_relics`、`relics`、`event_choices`、`neow_bonus` | 762；`boss_relics` 非空 612 | 否 | 遗物获得只记 `mutation_pending` 与 `build_change`（`Hooks.java:78-86`），无 Boss 遗物候选 | 候选已在 `.run`；不建议在 M1 重复记录 | 精确 | `.run` |

### 4.3 战斗、HP、药水

| 指标 | 单位 | 需要的字段 | `.run` 覆盖 | RHP | Ledger 现状 | 缺口与补法 | 精度 | 来源优先级 |
|---|---|---|---|---|---|---|---|---|
| 受到伤害 | 战斗 | `damage_taken` | 762（非空 737） | 否 | 无 | 无缺口 | 精确 | `.run` |
| 实际战损、有无回复分裂、怪物危险度 | 战斗 | `damage_taken`、`current_hp_per_floor`、第一层入层 HP | 非第一层 762；`floor0_hp` 0 | 否 | `floor` 记录战斗层为战后状态（实测，第 1 节第 5 条）；开局无入层 HP | **补入层 HP**（第 5.5 节）；缺失时第一层沿用 TinyCat 估算并标估算 | 新局精确，（历史局估算） | Ledger 入层记录 > `.run` 推导 |
| 每幕总战损 | 局（每局每幕一个样本） | 上项加 `event_choices.damage_taken`、`path_per_floor` | 762 | 否 | 同上 | 同上 | 同上 | 同上 |
| 遭遇死亡率、地精大块头致死 | 战斗 | `killed_by`、`damage_taken` | 死亡局 452/452 有 `killed_by` | 否 | 无 | 无缺口 | 精确 | `.run` |
| 各幕存活率、幕划分 | 局 | `damage_taken` 中 Boss 层、`path_per_floor`、`floor_reached` | 762 | 否 | 快照 `act` 是游戏的真实幕号（`Snapshot.java:26`），TinyCat 靠 Boss 层推断 | 无缺口 | 精确 | Ledger `act` > `.run` 推断 |
| 药水使用 | 战斗 | 原法 `potion_use_per_floor`；替代 `potions_floor_usage`（非空 441） | 原法 0；替代 762 | 有 | StatsIndex 两者都读（`StatsIndex.java:35-36`） | 不需补；按药水名统计才需要 RHP | 精确（数量），药水名（历史局不可得） | `.run` 数量；药水名 RHP |

### 4.4 事件、遗物、金币、删牌、火堆、商店、Meta

| 指标 | 单位 | 需要的字段 | `.run` 覆盖 | RHP | Ledger 现状 | 缺口与补法 | 精度 | 来源优先级 |
|---|---|---|---|---|---|---|---|---|
| 事件胜率、事件战损 | 事件次数 | `event_choices` | 762 | 否 | `floor` 记录有事件类名（`Snapshot.java:35`） | 无缺口 | 精确 | `.run` |
| 问号数、事件数 | 楼层 | `path_taken`、`event_choices` | 762 | 否 | 无 | 无缺口 | 精确 | `.run` |
| 遗物胜率、购买遗物胜率 | 局（持有局） | `relics`、`items_purchased` | 762 | 否 | 快照有遗物 ID 与计数，无获得楼层（`Snapshot.java:32`） | 无缺口；火堆可选条件所需获得楼层用 `relics_obtained`（覆盖 705，92.5%） | 精确 | `.run` |
| 遗物效果量（M3） | 局、战斗、回合（按 Relic Stats 口径） | `relic_stats`、`relics` | 676（88.7%） | 否（Relic Stats 写入） | 无 | 由 `RelicStatsTable` 子任务处理，本表只列存在 | 精确（读取值），未收录遗物显示缺失 | `.run` |
| 平均累计获得金币 | 局 | 精确：`gold_gained`；估算：`gold_per_floor`、`neow_bonus`、`neow_cost`、`relics`（MawBank 94 局） | 精确 0；估算输入 762 | `score_breakdown` 仅金币成就局 | 快照只有当前金币（`Snapshot.java:29`） | **M1 新增 `gold_gained`**（第 5.1 节） | 新局精确，（历史局估算） | Ledger `gold_gained` > RHP 成就值 > 估算 |
| 删牌（商店、事件、尼奥、火堆、空鸟笼） | 局内事件次数 | `items_purged`、`event_choices.cards_removed`、`neow_bonus_log`、`campfire_choices`、`relics` | 四来源 762；尼奥一项 0；空鸟笼 90 局需手补 +2 | 尼奥一项 | 牌组差（`StatsIndex.java:56` 的 observed 路径） | 尼奥一项由决策前后快照补；空鸟笼删牌可由牌组差得 | 新局精确，（历史局尼奥删牌不可得，空鸟笼估算） | 商店、事件、火堆 `.run`；尼奥 Ledger > RHP |
| 火堆行动 | 选择次数 | `campfire_choices` | 762（非空 712） | 否 | `mutation_pending`（网格选择，`Hooks.java:72-77`）含选前牌组 | 无缺口 | 精确 | `.run` |
| 火堆偏好 W、选项权重、统一榜 | 选择次数（每次锻造） | `master_deck`、`card_choices`、`items_purchased`、`event_choices`、`neow_bonus_log`、`campfire_choices`、`relics_obtained` | 重建需要的尼奥一项 0；A 口径 762 | 尼奥一项 | 锻造的网格选择记 `mutation_pending.before`（含牌组），可直接取当时候选 | W 在 Ledger 局可免重建；历史局沿用 TinyCat 重建并标重建覆盖。火堆房 `floor` 记录时点不统一（`COMPLETE` 212、`INCOMPLETE` 25），不可当入层牌组 | 新局精确，（历史局估算，星盘、空鸟笼、潘多拉有残差） | Ledger > `.run` 重建 |
| 商店购买（卡、遗物、药水、删牌） | 选择次数（每次购买） | `items_purchased`、`item_purchase_floors`、`items_purged`、`items_purged_floors` | 762（有购买 663 局） | 否 | 无 | 无缺口；购买率分母需下一行 | 精确 | `.run` |
| 有效商店数 | 楼层 | 原法 `shop_contents`；替代 `path_per_floor` 的 `$` | 原法 0；替代 762（683 局共 2495 次） | `shop_contents` | `floor` 记录有 `ShopRoom`（`Snapshot.java:34`） | 不需补；`$` 是进入次数，与 RHP“有效商店”是否一致未核实 | 精确 | `.run` `$` > Ledger > RHP |
| 商店候选、购买率（新） | 选择次数（每个被提供商品） | 商店卡、遗物、药水、删牌价与是否购买 | 0 | 仅名称无价格 | 无 | **M1 新增**（第 5.3 节） | 新局精确，（历史局不可得） | Ledger > RHP |
| Meta：最大遗物、金币、删牌、血上限 | 局 | `relics`、`gold`、`items_purged`、`max_hp_per_floor` | 762 | 否 | 无 | 无缺口 | 精确 | `.run` |
| Meta：仪式匕首、遗传算法 | 局 | `improvable_cards` | 0（本机相关卡组仅 5 局） | 有 | 牌 `misc` 已记（`Snapshot.java:21`） | 无缺口（新局） | 新局精确 | Ledger > RHP |
| Meta：灼热攻击 | 局 | `master_deck` 的 `+N` 后缀 | 762（3 局有该牌） | 否 | 牌 `upgrades` | 无缺口 | 精确 | `.run` |
| Meta：炼制药水、吃一堑 | 局 | `potions_obtained_alchemize`、`lesson_learned_per_floor` | 0（吃一堑 98 局、炼制药水 0 局持有） | 有 | 无 | 需用户决定是否保留这两项；不做则整项不显示 | 不可得 | RHP |

### 4.5 来源与展示

| 指标 | 单位 | 需要的字段 | `.run` 覆盖 | Ledger 现状 | 缺口 | 精度 | 来源优先级 |
|---|---|---|---|---|---|---|---|
| 求解器来源标记（M3） | 选择次数 | 每个决策的 `action_source` | 不适用 | 已有（`StateMod.java:50-60`）；本机历史 39 局 851/851 为 `unknown` | 旧记录来源只能显示为“来源不明”覆盖情况 | 新局精确 | Ledger |
| 覆盖率显示 | 与被覆盖指标同单位 | 摘要中的字段族标记 | — | 无 | 摘要需带覆盖标记（第 6 节） | 精确 | 摘要 |
| 关联与冲突 | 局 | `play_id` | 762（无重复） | 元数据 `play_id` 初始为 `null`，在 `Metrics` 落盘后写入（`StateMod.java:69,98`）；39 局中 33 局对上 | 未关联的局保留独立身份（product.md） | 精确 | `.run` `play_id` 与 Ledger 一致才合并 |

## 5. M1 必须新增的记录字段

下列项只记调研结论，不含实现。所有增量只增不改：新增键与新记录类型，旧读取方（`StatsIndex.java:58` 只认 `COMBAT_REWARD`，只接受 `card`、`skip`、`bowl` 三种候选）会忽略未知类型。

### 5.1 `gold_gained`

- **来源**：`CardCrawlGame.goldGained`（`public static int`）。字节码核验：仅 `AbstractPlayer.gainGold` 递增，且先于 Ectoplasm 与非正数分支返回；`CardCrawlGame` 新局清零、读档从 `SaveFile.gold_gained` 取值；`SaveFile` 写盘时写入。`cardcrawl` 全部 2306 个类中，直接写 `AbstractPlayer.gold` 的只有 `CardCrawlGame`（读档）和 `TestTrial`。
- **schema 增量**：快照对象（`Snapshot.capture` 输出，所有 `before`、`after`、`state` 共用）增加整数 `gold_gained`。起点是新局 0，计入所有 `gainGold` 调用，不计商店花费与失去。
- **缺失处理**：没有该键的记录显示“金币：估算”，沿用 TinyCat 的 `gold_per_floor` 公式并标估算（TinyCat 在其数据上的实测：平均绝对误差 38，最大 160，见 `STATE-NOTES-AND-GOLD.md` 第 4 节）。读档局取存档恢复值，记录上不推断。其他 Mod 直接写 `player.gold` 的情形未核实。

### 5.2 尼奥选项与代价

- **来源**：`NeowEvent.rewards`（私有 `ArrayList<NeowReward>`）。`blessing()` 构造四个 `NeowReward(0..3)` 并把 `optionLabel` 显示在四个按钮上，随后 `screenNum=3`；`buttonEffect` 在 `screenNum==3` 时按钮号 0..3 激活对应奖励。`NeowReward.type`、`drawback`、`optionLabel` 是公有字段，枚举取值有 19 种奖励类型与 5 种代价（`NONE`、`TEN_PERCENT_HP_LOSS`、`NO_GOLD`、`CURSE`、`PERCENT_DAMAGE`）。时机：点击前读 `rewards`，效果落定后取 `after`（沿用现有 `settle` 路径）。RunHistoryPlus 取同一时机。
- **schema 增量**：复用 `decision`，新增 `source: "NEOW_OPTION"` 与候选类型 `neow_option`，候选带 `slot`、`reward_type`、`drawback`、`label`（本地化原文，含数值）；选中项写入 `candidate_id`；`before` 与 `after` 沿用快照（含血量、金币、牌组、遗物，用于还原代价量值与 `neow_bonus_log` 等价信息）。`miniBlessing`、每日与无尽变体用 `variant` 标出。
- **缺失处理**：无 `NEOW_OPTION` 决策的局，选取率与强度显示“未记录”并计入覆盖率；选中次数与胜率仍来自 `.run`。`.run` 中存在 RunHistoryPlus 的 skipped 日志时按旧局读取，排在 Ledger 之后。

### 5.3 商店候选

- **来源**：`Merchant` 构造时调用 `AbstractDungeon.shopScreen.init(cards1, cards2)`（字节码核验）。`ShopScreen.coloredCards`、`colorlessCards` 公有（`AbstractCard.price` 公有）；`relics`（`StoreRelic`：`relic`、`price`、`isPurchased`）和 `potions`（`StorePotion`：`potion`、`price`、`isPurchased`）为私有；删牌有 `purgeAvailable` 与静态 `purgeCost`、`actualPurgeCost`。购买入口：`StoreRelic.purchaseRelic`、`StorePotion.purchasePotion`（公有），`ShopScreen.purchaseCard` 与 `purchasePurge`（私有）。RunHistoryPlus 在 `nextRoomTransition` 进入商店房时读 `relics`、`potions`，不记价格。
- **schema 增量**：新记录类型 `shop_offer`（入店：楼层、商品种类、ID、升级、价格、删牌价与可用性）和 `shop_result`（离店：已购商品 ID、实付价、前后金币）。
- **缺失处理**：无 `shop_offer` 的局，购买率与“提供次数”显示“未记录”，购买次数仍来自 `.run`。
- **时机**：会员卡、信使、微笑面具的折扣在 `init` 之后是否已生效，未核实；价格取 `open()` 之后更稳（见第 8 节）。

### 5.4 第三幕通过事实

- **来源**：`Hooks.java:68-71` 已钩 `ProceedButton.goToTreasureRoom`（第一、二幕 Boss 出口）、`goToDoubleBoss`（A20 第三幕第一个 Boss 之后）、`goToVictoryRoomOrTheDoor`（第三幕最终 Boss 出口）、`goToTrueVictoryRoom`（心脏）。这些出口是游戏自己的阶段判定，比从战斗日志推导更稳。
- **schema 增量**：`ending` 与摘要增加 `act3_cleared`（true、false、unknown）及 `act3_cleared_floor`。
- **缺失处理**：旧记录按第 7 节规则从 `.run` 推导；两者冲突标记冲突。

### 5.5 入层 HP 与第一层 HP

- **来源**：product.md 要求“进入房间、房间效果生效之前”的 HP。现有 `floor` 记录在战斗房间是战后。`AbstractDungeon.nextRoomTransition` 是 RunHistoryPlus 取楼层退出时间与商店内容的时点；`AbstractPlayer.preBattlePrep` 已被 `TurnRecorder` 钩住（`Hooks.java:22-24`），但回合记录默认关闭（`TurnRecorder.java:11`）。第一层入层 HP 等于尼奥效果落定后的 HP，可取尼奥决策的 `after`。
- **schema 增量**：新记录类型 `floor_enter`（`act`、`floor`、`room`、`hp`、`max_hp`、`gold`、`gold_gained`）。
- **缺失处理**：无 `floor_enter` 的局，战损显示“第一层估算”，沿用 TinyCat 回退（`floor1HP + floor1Damage`，铁甲再减 6）。

### 5.6 由主表推出的其余项（均为可选或需用户决定）

- `saveSlot`：写入元数据，使存档位筛选不依赖目录名。
- 结局计数器：`monstersSlain`、`elites1/2/3Slain`、`cardsPurged`、`potionsBought`（均为 `CardCrawlGame` 公有静态，javap 核验存在）。非必需。
- `killed_by`：`DeathScreen` 构造参数含 `MonsterGroup`（`Hooks.java:87-89` 已钩，目前未取），可使无 `.run` 的 Ledger 局也能判死因。非必需。
- 放弃事件：现状只有 `session_closed` 中断。

## 6. 每局摘要应包含的字段

按 M2、M3 指标反推，目标是统计索引不读 `events.jsonl` 与 `run.json`。摘要只存事实，不存比率（architecture.md 不变量“派生数字不落盘”；该约束与摘要的关系见第 8 节）。

| 字段组 | 内容 | 供给的指标 |
|---|---|---|
| 标识与失效 | `run_id`、`play_id`（可为空）、`schema`、`summary_version`、`mod_version`、`game_version`、`session_kind`、`dataset_origin`、`saveSlot`；源文件的最后 `seq`、字节数与修改时间 | 来源合并、缓存失效 |
| 局级事实 | 职业、进阶、种子、每日、无尽、开始与结束时间、`outcome`、`heart_kill`、`act3_cleared`、`floor_reached`、到达的最大幕 | 胜率、各幕存活、筛选 |
| 路径与分支 | 分支数、读档次数、最终路径是否可解（`pathExcluded`）、分支到 `seq` 上限的映射、签名冲突标记 | 读档局口径（`StatsIndex.java:46-49`） |
| 决策表 | 每个已确认决策一行：`seq`、幕、楼层、`source`、`action_source`、状态、选中候选、候选压缩列表（牌 ID 加升级、`skip`、`bowl`、尼奥选项、商店商品加价格与是否购买）、决策前 HP、最大 HP、金币、`gold_gained` | 抓率、标准化抓率、尼奥选取率、商店购买率、求解器来源 |
| 逐层行 | 楼层、幕、房间类、入层与战后 HP、最大 HP、金币、`gold_gained` | 战损、金币曲线、商店访问 |
| 构筑 | 终局牌组（ID、升级、`misc`）、遗物 ID 与首次出现楼层；`build_change` 压缩行（楼层、房间、原因、状态、增减升级的牌 ID） | 卡组胜率、持有胜率、删牌与升级来源、火堆偏好 |
| 覆盖标记 | `has_gold_gained`、`has_neow_options`、`has_shop_offers`、`has_floor_enter`、`turn_recording`、`linked_native` | 覆盖率、缺失与估算标注 |

大小实测（本机 39 局 State Notes 记录）：`run.json` 中位 1,063,918 字节、最大 2,493,037 字节。只含决策表（候选 ID 压缩）、逐层行、终局牌组与遗物的下界摘要，中位 5,301 字节、最大 10,534 字节，约为原文件的 0.5%。该下界不含 M1 新增字段，也未在千局夹具上测读取耗时（M1 验收项，交用户定上限）。

## 7. 胜负事实：是否通过第三幕与第四幕死亡

规则（只用原版 `.run` 字段）：满足任一条即“通过第三幕”。

1. `victory` 为真。
2. `damage_taken` 出现第四幕遭遇（`Shield and Spear`、`The Heart`）。
3. 第三幕 Boss（`Time Eater`、`Awakened One`、`Donu and Deca`）按楼层去重后，击败场数达标：A20 两场，其余一场；死在该 Boss 的一场不计。

本机实测（`act3.txt`）：

| 项 | 局数 |
|---|---|
| 第三幕 Boss 战场数分布（按局，去重楼层） | 0 场 384、1 场 102、2 场 276 |
| 规则 1 或 2 | 314 |
| 规则 3 | 315 |
| 两者不一致 | 1 局：A20、无 `killed_by`、`floor_reached`=54，击败两个第三幕 Boss，但 `damage_taken` 没有第四幕战斗 |
| 第四幕死亡，有 `killed_by` | 49（`The Heart` 45、`Shield and Spear` 4）；`damage_taken` 有第四幕遭遇与 `floor_reached`≥53 两条判据结果相同 |
| 上述不一致的那一局（无 `killed_by`，`floor_reached`=54） | 并入后共 50 局，与主线程测得的 50 局一致 |
| 胜利 | 265，其中心脏 255、非心脏 10（`floor_reached` 51 有 7 局、52 有 3 局） |

结论：字段覆盖 762/762，用三条取并集在本机稳定，无一局需要用 Ledger 补；在“无 `killed_by` 且非胜利”的 45 局中，只有 1 局通过第三幕。需要注意：第四幕死亡且无 `killed_by` 的局不能只看 `killed_by`；A20 的“两场”是进阶规则，非 A20 局只需一场，本机非 A20 的第三幕 Boss 战场数分布已含在上表合计里，但未单独核对（见第 8 节）。心脏击杀沿用 `victory` 加 `The Heart` 的判法（255/265）。

## 8. 未核实项与假设

1. **RunHistoryPlus 字段表来自上游源码，不是 README**（README 接口 404，仓库无 LICENSE）。TinyCat 当年使用的 RunHistoryPlus 版本未核实。
2. **TinyCat 文档自相矛盾**：`CORE-LOGIC.md:160`、`:438` 与 `CLAUDE.md:93` 称 `neow_costs_skipped_log` 恒为 `NONE`；`ALGORITHM-AUDIT.md:107`（2026-10-25 17:26）更正为 1026 局中 562 条带代价，并撤销了 17:06 的改法（`:109`）。RunHistoryPlus 源码写 `drawback.name()`，与更正一致。本机无数据可验。
3. **305 局真值夹具的位置**：未在本仓库或 `sts1/data` 中找到（一次 120 秒的全库搜索超时，无结果；`data/datasets/megacrit-metrics-2020` 未扫描）。假设它来自 TinyCat 作者的数据。
4. **`gold_gained` 的边界**：其他 Mod 直接写 `player.gold` 的情形、Ectoplasm 局的预期值、读档时 `SaveFile.gold_gained` 与当前金币的一致性，均只做了字节码层面核验，未做游戏内实测。
5. **尼奥变体**：`miniBlessing`、`dailyBlessing`、`endlessBlight` 的候选数与 `rewards` 是否填充未逐条核对。本机 20 种 `neow_bonus` 取值都属于标准四选项，5 局为空。尼奥代价的具体数值（`PERCENT_DAMAGE` 等）假设可由 `drawback`、`label` 和前后快照还原，未核对游戏公式。
6. **商店取值时机**：`init` 后折扣与价格是否已定未核实；假设 `open()` 之后读取更稳。
7. **`is_trial` 是否标记自定义模式**未核实；本机自定义局为 0，无法反证。
8. **放弃局**：原版是否在菜单放弃时写 `.run`、是否与存档退出区分，未核实。本机 45 局无 `killed_by` 的非胜利局中 25 局 `floor_reached`=0。
9. **Boss 层选牌缺项原因**：230/1049 的缺失只与 RunHistoryPlus 补丁的用途吻合，未做原版字节码核对；商店事件与图书馆两处补丁仅依据文件名。
10. **非 A20 局的第三幕 Boss 数**：假设非 A20 只有一个第三幕 Boss，本机 102 局只有 1 场与之相容，但未按进阶分层核对。
11. **摘要与“派生数字不落盘”**：假设摘要只存事实和压缩后的决策行，不算违反该不变量，与 `run.json` 同属“事件的派生视图”（`Recorder.java:129` 注释）。需主线程确认。
12. **`first-floor` 入层 HP 的等价性**：假设尼奥决策 `after` 的 HP 等于 TinyCat 的 `floor0_hp`（后者在第 1 层、游戏时间 300 秒内取值，`STATE-NOTES-AND-GOLD.md` 第 3 节），未对拍。
13. 本表不覆盖 Relic Stats 的 `relic_stats` 语义（另一子任务），只列其存在。本机样本以 Watcher 和 Ironclad 为主（合计 94.4%），Silent 与 Defect 的指标样本量会很小。

## 9. 脚本与中间产物

目录：`C:\Users\CYZ\AppData\Local\Temp\claude\E--Projects-Opensource-sts1\3fa794b7-8370-4933-b9c2-9a06d42c2874\scratchpad\child-m1gaps\`（均用 `python -I -X utf8` 运行）。

| 脚本 | 输出 | 内容 |
|---|---|---|
| `survey.py` | `survey.txt` | 762 局顶层键出现率、嵌套键、进阶与角色分布 |
| `survey2.py` | `survey2.txt` | 取值分布、`path_per_floor`、尼奥取值、`relic_stats` 月份、`$` 次数、`play_id` |
| `coverage.py` | `coverage.txt` | 每个指标的必需键覆盖；Metrics 字符串常量核验；RunHistoryPlus 字段的本机出现数 |
| `outcome.py` | `outcome.txt` | 胜、死、未判定与放弃相关数 |
| `act3.py` | `act3.txt` | 样本范围（每日、无尽、自定义、种子、存档位）、第三幕通过规则、第四幕死亡 |
| `bosscards.py` | `bosscards.txt` | Boss 层 `card_choices` 缺项 |
| `ledger.py`、`ledger2.py` | `ledger.txt`、`ledger2.txt` | 39 局 State Notes 记录的类型、决策来源、`floor` 记录时点、与 `play_id` 关联 |
| `summary_size.py` | `summary_size.txt` | 摘要下界大小 |
| `goldwrite.py` | `goldwrite.txt` | 反编译核验谁直接写 `AbstractPlayer.gold` |

字节码核验用 `javap -c -p` 对 `desktop-1.0.jar` 中的 `CardCrawlGame`、`AbstractPlayer`、`SaveFile`、`NeowEvent`、`NeowReward`、`ShopScreen`、`Merchant`、`StoreRelic`、`StorePotion`、`Metrics`；RunHistoryPlus 源文件缓存在 `rhp\`。
