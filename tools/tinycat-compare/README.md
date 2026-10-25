# TinyCat 对数工具

M2 验收“与 TinyCat 4.5.6 同批逐表对数，每处差异必须归入已登记缺陷或口径决定，未归类即失败”（[architecture.md](../../docs/architecture.md) 测试与 CI）的两件工具：

- `TinyCatBaselineExport.java` + `export-baseline.ps1`：用未修改的 TinyCat jar 离线读本机 `.run`，按 TinyCat 默认口径算出每张表，导出为基线 JSON。
- `compare.py` + `mapping.json` + `rules.json`：把 Spire Ledger 的指标导出对齐到 TinyCat 的表，逐表逐行比对，每处差异归入 (a) 已登记口径决定、(b) 已登记缺陷或 (c) 未归类。

本目录只有本项目自己写的代码和数据。TinyCat（MPL-2.0）的源码不复制进来，驱动只在运行时链接 TinyCat 构建出的 jar。`mapping.json` 里的两张别名表是游戏 ID 与显示名的对应事实，来源是 TinyCat 的 `CardNameNormalizer` 和 `EncounterStats.getNameReplacementMap`。

## 1. 导出 TinyCat 基线

1. 把 TinyCat 仓库复制到私有目录再构建，不在原仓库里构建。本机只有 JDK 21，Gradle 8.0 wrapper 跑不起来，改用已缓存的 Gradle 9.8：

   ```powershell
   $env:STEAMAPPS='D:/SteamLibrary/steamapps'; $env:STS_INSTALL='D:/SteamLibrary/steamapps/common/SlayTheSpire'
   & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.8.0-bin\<hash>\gradle-9.8.0\bin\gradle.bat" buildJAR --no-daemon
   ```

2. 把 `runs/` 复制到一个工作目录（TinyCat 经 `Gdx.files.local("runs/...")` 找 `.tinycat` 状态文件，所以驱动必须以含 `runs/` 的目录为工作目录），然后导出：

   ```powershell
   .\export-baseline.ps1 -TinyCatJar <copy>\build\libs\TinyCat_V4.5.6.jar -GameRoot <dir-with-runs> `
     -OutDir E:\Projects\Opensource\sts1\data\runs\spire-ledger-m2-20261025\tinycat-baseline -Commit <tinycat-commit> -Slots 0,2
   ```

驱动做的事与游戏内统计页相同：按存档位筛目录（`RunHistoryManager.loadData` 的规则），`Run.fromFile` 解析，`Run.valid` 过滤，按时间排序；对四个职业各建一个 `ClassStat(runs, false)`，全职业建 `ClassStat(runs, true)`；再用 `TierListBuilder` 生成 Tierlist 与 Plackett-Luce 标准化率。筛选取统计页默认值：全部月份、包含放弃局、种子局全部、最小样本 0。

TinyCat 的默认口径写死在 `Run.valid`：只收 A20，排除每日、特殊和无尽局，排除含 `:` 的模组内容和黑名单内容，排除没有 `neow_bonus` 的局。“全部进阶”在 TinyCat 里不存在。

### 离线算不了的部分

| 内容 | 原因 | 最接近的离线替代 |
|---|---|---|
| 怪物危险度梯度（`MonsterTierBuilder`） | 直接读 `tinycat.runHistoryManager`，要先跑 `CardCrawlGame` 初始化 | 基线里的 `averageActualHPLoss`（同一份逐场实际战损的均值与场数）；中位数、P90 没有导出 |
| 卡图、遗物图、梯度图、xlsx | 需要游戏贴图、字体和统计页对象 | 不需要，数字都在基线行里 |
| Tierlist 里诅咒、状态牌的剔除 | `isCurseOrStatus` 依赖已初始化的 `CardLibrary`，离线一律不剔除 | 本机基线里 `tier:cards_*` 没有诅咒或状态牌，结果不受影响 |
| `.tinycat` 精确金币与第一层 HP | 本机 `runs/` 没有 `.tinycat` 文件（`meta.tinycat_state_files_found`=0），游戏内同样走估算 | 无需替代 |

### 基线格式（`meta.format = "tinycat-baseline"`）

```json
{"meta": {"format": "tinycat-baseline", "format_version": 1, "source": "TinyCat 4.5.6", "tinycat_commit": "...",
          "filters": {"save_slot": 0, "...": "..."}, "runs_scanned": 762, "runs_included": 528,
          "excluded_by_reason": {"not_a20": 161}, "excluded_runs": {"not_a20": ["IRONCLAD/1700000000.run"]},
          "run_ids": {"ALL": ["IRONCLAD/1700000000.run"], "IRONCLAD": []}},
 "rows": [{"table": "cardPicksAct1", "scope": "ALL", "dims": {"key": "Bash"}, "metric": "rate",
           "value": 0.25, "numerator": 3, "denominator": 12, "sample_size": 12}]}
```

- `table` 是 `ClassStat` 的公开字段名，Tierlist 为 `tier:<分组键>`。`scope` 为 `ALL`、`IRONCLAD`、`THE_SILENT`、`DEFECT`、`WATCHER`。
- `dims.act`：按幕的表用 `"1"`–`"4"`；长度 5 的数组里 `"0"` 是四幕合计。`dims.key` 是条目名：卡牌为去掉升级的 ID（TinyCat 已做名称归一），尼奥为 `BONUS / COST`，其余为原样名称。
- `metric`：`rate` 的 `numerator`/`denominator` 是 TinyCat `Rate` 的 `win` 与 `win+loss`，`value` 为比值（0–1）；`mean` 的 `numerator` 是总和、`denominator` 与 `sample_size` 是样本数；`value` 是标量；`choice_sets` 是 Plackett-Luce 选择集个数；Tierlist 另有 `tier`、`standardized`、`standardized_low/high`、`tie_group`、`p_rank_one`。
- 各表 `win/loss` 的含义按 TinyCat：抓率表是选中与未选中，胜率表是心脏通关局与其他局，火堆和商店表是按胜负拆开的次数（比较时只用合计）。

## 2. Spire Ledger 导出格式（内核子任务按此实现）

```json
{"meta": {"format": "spire-ledger-metrics", "format_version": 1, "metrics_version": "metrics-v1", "source": "Spire Ledger <version>",
          "filters": {"tinycat_alignment": true, "save_slot": 0, "ascension": [20], "modes": "exclude_daily_special_endless",
                      "include_abandons": true, "seed_filter": "all", "win_definition": "heart_kill", "sources": ["native"]},
          "run_ids": {"ALL": ["IRONCLAD/1700000000.run"], "IRONCLAD": ["IRONCLAD/1700000000.run"], "THE_SILENT": [], "DEFECT": [], "WATCHER": []}},
 "rows": [{"metric": "pick", "scope": "ALL", "dims": {"act": "1", "card": "Bash"},
           "value": 0.25, "numerator": 3, "denominator": 12, "sample_size": 12, "status": "ok"}]}
```

`meta.filters` 必须逐项等于上面的对齐取值（`save_slot` 等于基线的存档位），否则工具报错退出 2。含义：

- 样本范围对齐 TinyCat（metrics.md 第 2 节“对数时必须先把本项目的筛选设成同一范围”）：只取 A20；排除每日、特殊、无尽；存档位 0 只读不带 `0_`/`1_`/`2_` 前缀的目录；放弃局计入且计为败；胜负按击杀心脏。
- `sources: ["native"]`：只用原版 `.run`，不并入 Ledger 记录，避免来源合并带来的差异。
- `run_ids` 必填，按 scope 列出参与统计的局，ID 用 `runs/` 下的相对路径 `目录/文件名.run`。工具先比对局集合：多出或缺少的局各算一处差异，表名 `_runs`，`dims.reason` 是 TinyCat 排除它的原因。想先只看指标差异，可以让导出命令接受一个局清单，直接用基线的 `meta.run_ids`。

每行字段：`metric`、`scope`、`dims`，以及 `value`、`numerator`、`denominator`、`sample_size`、`status`（metrics.md 第 8 节的取值）。均值类指标的 `numerator` 是总和，`denominator` 是样本数。`gold_gained` 另带 `estimated`、`exact`（估算与精确局数，第 1.4 节）。缺失写 `null`，不写 0。

| `metric` | `dims` | 比较的字段 | 对应 TinyCat 表 |
|---|---|---|---|
| `win` | — | `numerator`=心脏胜局，`denominator`=胜+败 | `numVictory`、`numDeath` |
| `time` | `result`: `all` / `win` | `numerator`=Σplaytime；`win` 行另比 `value`、`denominator` | `playTime`、`averageWinningTime` |
| `deck_size`、`gold_gained` | — | `value`、`denominator`；金币另比 `estimated`、`exact` | `averageDeckSize`、`averageGold`、`goldEstimatedRuns`、`goldExactRuns` |
| `pick` | `act`、`card` | `numerator`、`denominator` | `cardPicksAct1`、`Act2`、`Act3_4`（工具合并 3、4 幕）、`AfterAct1`（合并 2–4 幕） |
| `skip`、`bowl` | `act` | 同上 | 上述表的 `SKIP`、`Singing Bowl` 行 |
| `holding` | `card` | 同上 | `cardWinRate` |
| `neow_related` | `neow`（`BONUS / COST`） | 同上 | `neowWinRate` |
| `boss_pick`、`boss_related` | `act`、`relic`（跳过为 `SKIP`） | 同上 | `bossRelicPickRateAct1/2`、`bossRelicWinRateAct1/2` |
| `damage`、`hp_loss`、`hp_loss_noheal`、`hp_loss_heal`、`encounter_potions` | `act`、`encounter` | `value`、`denominator`（场数） | `averageDamageTaken`、`averageActualHPLoss`、`averageNoHealActualHPLoss`、`averageHealActualHPLoss`、`averagePotionUse` |
| `act_hp_loss` | `act` | `value`、`denominator`（局数） | `averageAllHPLoss` |
| `death` | `act`、`encounter` | `numerator`=死亡场数，`denominator`=场数 | `encounterDeadRate` |
| `act_survival` | `act` | `numerator`、`denominator` | `survivalRatePerAct` |
| `event_related` | `act`（1–3）、`event`（事件名，不分选项） | `numerator`、`denominator` | `eventWinRateAct1–3` |
| `purges` | `source`（`SHOP`、`EVENT`、`NEOW`、`CAMPFIRE`、`EMPTY_CAGE`、`ALL`）；商店另有 `source`=`SHOP` 加 `act` | `value`（次数） | `purgesBySource`、`shopPurges` 等、`totalCardsPurged`、商店表的“商店删牌” |
| `campfire` | `act`、`action`（`REST`、`RECALL`、`PURGE`、`LIFT`、`DIG`、`SMITH`、`TOTAL`） | `value`（次数） | `campfireActions*`、`campfireVisits` |
| `upgrade` | `source`=`campfire`、`act`、`card` | `value`（次数） | `campfireActions*` 的卡牌行 |
| `purchase` | `act`、`item` | `value`（次数） | `shopActions*` 的商品行 |
| `shop_visits` | `act` | `value`（次数） | `shopActions*` 的“商店总数” |

- `act` 一律是字符串：`"1"`–`"4"`，`"0"` 表示四幕合计。幕划分按 metrics.md 6.4 的 Boss 楼层规则，与 TinyCat 4.5.6 代码一致。
- `card`、`encounter`、`item` 用 `.run` 里的原始 ID（卡牌去掉 `+N`），工具按 `mapping.json` 的别名表换成 TinyCat 的名称；药水按 metrics.md 6.5 归一成无空格 ID。
- 比率或均值行在样本为 0 时可以省略；全部比较字段都是 0 或 `null` 的行与缺行视为相同。
- 表中没有的 Ledger 指标不影响结果，只在报告的 `ledger_only_metrics` 里列出。
- 火堆的 `SMITH` 合计行不参与比较：TinyCat 把带 `data` 的锻造拆成按卡牌的行（`campfireActionStats`），这些行与 `upgrade` 比。`shopActionsTotal` 的“商店删牌”对应 `purges` 里不带 `act` 的 `SHOP` 行（全部幕合计）。

## 3. 比对与归类

```powershell
python -I compare.py <baseline.json> <ledger-or-baseline.json> [--report report.json]
```

第二个输入是 TinyCat 基线时逐行比对全部字段（用于自检）；是 Ledger 导出时按 `mapping.json` 对齐后只比映射里列出的字段。退出码：0 没有未归类项；1 有 (c)；2 输入、映射或规则无效。

- `mapping.json`：每个 TinyCat 表（按需细到条目键）必须恰好命中一个条目。不比较的条目自带归类：(a)、(b) 必须带出处；(c) 表示合同里没有登记，直接算失败。基线里出现映射没有覆盖的表或指标，同样算 (c)。
- `rules.json`：每条规则写明 TinyCat 表名清单（不接受通配）、字段、方向条件（`any`、`tinycat_ge_ledger`、`tinycat_le_ledger`、`row_only_in_tinycat`、`row_only_in_ledger`、`tinycat_zero`），以及出处：文档路径、节号和一段原文。工具启动时在仓库文档里逐字查找原文，找不到就拒绝运行，所以合同改动后失效的规则会立刻暴露。按顺序取第一条命中的规则。

## 4. 自检

```powershell
python -I selftest.py <baseline.json>
```

依次检查：基线与自身比对零差异；把基线按映射反投成 Ledger 格式再比回来，零数值差异（含 3、4 幕与 2–4 幕的合并路径），且没有合同未登记的 TinyCat 表，退出 0；在投影里分别埋入持牌胜率按局计、删掉 Boss 遗物 `SKIP` 行、改动铁甲战士平均卡组大小，三处分别落入 (a)、(b)、(c)；通配表名和找不到原文的规则被拒绝（退出 2）。
