# Spire Ledger architecture

Last confirmed: not yet confirmed
Product spec: [product.md](product.md)

## Target structure

一个 ModTheSpire Mod，modid `spire-ledger`，产出 `SpireLedger.jar`。源码在独立仓库 `sts1/spire-ledger`。模块边界由包保证，Mod 边界只有一个。现有代码仍在平铺的 `spireledger` 包里，各包在第一次需要边界的里程碑拆出（见 Route），不做一次性搬迁。

| 包 | 职责 | 改动节奏 | 环境要求 | 状态 |
|---|---|---|---|---|
| `record` | 唯一的局内事实写者：决策锚点与全部候选（含商店、尼奥选项及代价）、`gold_gained`、楼层快照、来源标记；每局另写摘要 | 慢；记录 schema 只增不改 | 宽，标注来源 | `~/.spire-ledger/runs/<run_id>/` |
| `stats` | 读本 Mod 记录与原版 `.run`（旧局的 RunHistoryPlus 字段与 Relic Stats 的 `relic_stats` 可选读取），按 play_id 合并成统一的单局模型，每个字段标明精确、估算或缺失；指标、幕划分、排名算法都是纯函数 | 中 | 纯 Java，不依赖游戏类，离线可测 | 只派生；缓存按文件修改时间失效 |
| `views` | 游戏内统计、历史、复盘界面；CSV 与 Tierlist 图片导出 | 中 | 游戏 UI | 导出目录 |
| `replay` | 回合快照采集与恢复，内嵌改造后的 SaveState | 慢 | 严格；兼容键 = 保存实现标识 + 快照 schema | 回合附件 |

### 接口与不变量

- 记录 schema 是 `record` 与 `stats`、离线 `review.html`、SlaySpire1RL 导入工具之间的唯一接口。`stats` 不引用 `record` 的内部类。
- 只有 `record` 写局内事实；派生数字不落盘（导出文件除外）。每局摘要是记录事实的投影，不含任何计算出的指标；它带指向源记录的有效性标记，过期或缺失时从源记录重建，不被信任为真值。
- 打开统计或复盘不改变局内状态、不推进 RNG。
- 读取方不能阻断写入：Windows 上读句柄会让原子替换失败，写入必须对此有界重试，失败不得停掉整局记录。
- 已审核的同装 Mod：BaseMod、StSLib、SuperFastMode 1.0.8、求解器 `sts1solver`；另有 ColoredMap 2.4.1、coloredPowertips 2.3.0、HighlightPath 0.0.3，以及 2026-10-28 经原生探针回归的 RunHistoryPlus 1.8.3、HideInfo 0.0.1、justclick 0.1.1、lolifontmod 0.2，与 2026-11-01 按“不改战斗状态、不进快照字段”加入的 CardImageReplacer 2.0（只接受这些精确版本）。求解器的执行动作经 `withAutomation` 标为 `known_automation`；求解器 0.9.6 及以后同装时支持重打，回退前清空旧结果并停止自动操作。版本只设下限，`SolverReplay.contract()` 在运行时核对账本调用和 patch 的求解器成员（`instance`、`cancel(boolean)`、`stopAll()`、`runAutoPlayer`、`receivePostUpdate`、`receivePostRender`），缺任何一个就按不支持处理；带后缀的测试版不算更新（2026-10-28）。其他未审核 Mod 一律记 `unknown`。
- SuperFastMode 1.0.8 为可选的已审核加速 Mod。记录与回合附件读取均接受它；练习子进程复制已加载的 jar 及 `SuperFastModeConfig.properties`。快照不保存插值或速度设置，沿用现有动作队列稳定边界。人工操作仍须经过原生 UI 提交路径。

### 构建档

只有一个构建档，含 `replay` 与内嵌 SaveState。原先规划的 `no-replay` 档已删除（用户决定，2026-10-30），记录见 [architecture-DONE.md](architecture-DONE.md)。

### 状态归属

| 状态 | 位置 | 归属 |
|---|---|---|
| 玩家记录、练习实例、错误报告 | 玩家本机 `~/.spire-ledger/` | 玩家 |
| 源码、测试、小型合同样例 | 本仓库 | 本项目 |
| 构建产物 | `build/`（忽略） | 可重建 |
| 原生验收证据 | `sts1/data/runs/spire-ledger-*`，不进 Git | 本项目，批次封存后只读 |
| SaveStateMod 原始 JAR | `sts1/data/cache/state-notes/`（沿用） | 外部输入，SHA256 固定 |

## Approach

### 一个 Mod 而不是两个（2026-10-25）

两个 Mod（记录重打 + 统计）的唯一收益是统计迭代不连带记录版本，但这来自“附件绑 jar 版本”的旧规则；改成按保存实现与快照 schema 判断后，一个 Mod 也没有这个问题。拆分则要多维护跨 Mod 合同、观察者白名单和 RNG 对照测试。用户决定自建仓库、自己发布后，所有权不再构成拆分理由。两轮审阅（对抗、第一性原理）的处置记录见 [decisions/2026-10-25-review.md](decisions/2026-10-25-review.md)。

### TinyCat 只取知识，不取代码

TinyCat 约 1.8 万行中约 37% 是值得重写的领域知识（幕划分、杀心、火堆分母、删牌来源、编码兼容、Plackett-Luce、尼奥强度、Jenks 分档、社区中文名、怪物危险度），其余是导出、图片、测试 Mod 和界面脚手架。重写进 `stats` 内核，不复制 MPL 文件。它的 `CORE-LOGIC.md`、`ALGORITHM-AUDIT.md` 和对数脚本作为验收参考。

### 删除的机制

`.tinycat` 状态文件与 TinyCatTrackerTest（`record` 直接记录累计金币和楼层 HP）；运行时依赖 RunHistoryPlus；`ClassStat` 与 State Notes `StatsIndex` 两套口径并存；xlsx 导出。

### 许可（用户决定，2026-10-25）

自有代码 GPL-3.0；改造 SaveState 仍内嵌主 jar。上游 2026-10-29 加了 MIT 许可证（`a762bc1cae`），与 GPL-3.0 兼容，原先的许可冲突已解除；NOTICE 保留其版权行与 MIT 原文。

### 测试与 CI

- 快层（每次提交）：编译、`stats` 内核断言、记录合同样例往返、写入锁冲突复现测试。无需启动游戏。
- 慢层（发布前）：隔离原版实例的原生探针，沿用 State Notes `integration*.ps1` 的方式；含求解器同装探针。
- 复用：求解器 `mod-pair` 用的自托管 Windows runner（持有私有游戏 JAR），为本项目另建工作流，不挂在求解器的原生构建上；底座的“门禁不自动更新金标准”“结果签名对基线”两条纪律。
- 不复用：底座 hook/quick/full 三层与 ROG 队列，规模不匹配。
- 统计真值夹具：TinyCat 文档里的“305 局分数明细精确金币”来自其作者的 1,113 局数据，本机 762 局没有 `score_breakdown`，无法复现。金币真值改用 M1 之后新录的局自建（`CardCrawlGame.goldGained` 由 record 落盘），如能取得那批数据再追加。与 TinyCat 4.5.6 同批逐表对数时，每处差异必须归入已登记缺陷或口径决定，未归类即失败。字段来源与覆盖实测见 [m1-field-gaps.md](m1-field-gaps.md)。

## Route

各里程碑均为 (proposed)。

- **M0 迁入与修复**：State Notes 代码迁入并改名（modid、包名、数据目录、schema 名、jar 名）；修写入锁冲突；重打兼容键与 jar 版本解绑。验收：`-Check` 全过；锁冲突测试转绿；一次 replay 探针通过。不可逆点：无（未公开发布）。
- **M1 记录补字段与摘要**（2026-10-25 完成；Meta 杂项待用户决定）：先定 `docs/metrics.md` 指标合同（M2、M3 全部指标的统计单位、分母、缺失规则、来源优先级），再据此出字段缺口表；补 `gold_gained`、尼奥选项与代价、商店候选、第三幕通过事实，以及 TinyCat 指标清单中 RunHistoryPlus 独有且需要的字段；每局摘要。同时修三处现有缺陷：缺少 `run.json` 的记录目录在历史里不可见（`HistoryIndex.java:70`）；诊断报告的异常消息和堆栈里的令牌没有去除（`DiagnosticReport.java:47-48`）；统计筛选没有排除未完成局（`StatsIndex.java:70`）。验收：记录检查；三处缺陷各有一个反例夹具转绿；千局夹具上的冷启动、筛选和记录停顿实测作为回归基线（用户决定不设硬上限，见 decisions “M1 性能实测”）。
- **M2 统计内核**：统一单局模型，新建 `spireledger.stats` 包，编译时只给 Gson、不给游戏 jar，以此强制“不依赖游戏类”（proposed）；State Notes 现有指标与 TinyCat 核心指标（卡牌抓率、遭遇战损、金币）迁入。验收：与 TinyCat 逐表对数（2026-10-25 两个存档位均为 0 处未归类）；真值金币核对与千局性能复测不作为维护者验收项，交给有兴趣的测试者按 [testing.md](testing.md) C 节自测（用户决定，2026-10-25）；结果筛选（全部、仅胜、仅败）× 放弃开关 × 两种胜负口径的全部组合各有离线断言，夹具含心脏胜、第三幕胜、第四幕死亡、放弃和缺少胜负事实的旧局；多来源合并在打乱读取顺序后结果不变。另需修正：第三幕不带钥匙通关时游戏构造的是 `DeathScreen`，现有记录的 `outcome` 记成 `death`，M2 必须以 `act3_cleared`（M1 已落盘）判定这类局，不能信 `outcome`。菜单放弃同样构造 `DeathScreen`。2026-10-25 起记录侧按成因写结局：菜单放弃记 `abandon`，无钥匙通过第三幕记 `victory`，并在结局里加 `outcome_basis`；没有该字段的旧记录，`death` 仍不可信，未关联 `.run` 时按缺失处理。
- **M3 其余指标与导出**：Plackett-Luce、Jenks、怪物危险度、尼奥；遗物效果量（读 `relic_stats`，遗物语义对照表覆盖本机样本出现的全部遗物，未收录的遗物显示为缺失而非 0）；CSV 与 Tierlist 图片；求解器同装来源标记（含求解器仓库一侧的 `withAutomation` 调用）。 界面与导出代码随之移入 `spireledger.views`（proposed）。
- **M4 CI 与发布**：自托管 runner 工作流；发布由用户逐次授权。不可逆点：modid `spire-ledger` 与记录 schema 公开后只能兼容扩展。

State Notes 仓库在 M0 完成后冻结为历史，不再发布。

## Open decisions

- 已决定（2026-10-25）：State Notes 旧记录留在 `~/.sts1-state-notes/` 原地只读，新 Mod 同时读旧 schema `sts1-state-v1`；跨项目一侧，SlaySpire1RL `tools/state_runs/import_run.py` 同时接受两个 schema 名（SlaySpire1RL 3d1002a）。每局摘要是统计索引的内部机制，见 M1。
- 是否保留 State Notes 的 Git 历史迁入（历史中含内部证据路径与记录，影响日后公开）。blocks: M0。

### 楼层悬停复盘与求解器同装（2026-10-26）

`HistoryScreen` 继续负责异步读取、分支选择和重打入口。`RunReviewModel` 只把所选路径的 JSON 投影成楼层与牌组，不读取游戏对象；`RunReview` 负责路线、悬停和固定详情，其状态在切换对局时重置。界面只读取卡牌和遗物库已有纹理，不构造或升级卡牌，不调用游戏随机数。

`ReplayEnvironment` 统一录制与附件读取的依赖版本边界。`SolverReplay` 在复盘打开时暂停求解器更新并隐藏其面板；当前战斗回退前通过求解器自身取消入口使旧回复失效、清空旧路线并停止自动操作。恢复或回滚后同步求解器的玩家引用，防止换成恢复后的玩家对象时重新开启自动执行。独立练习仍使用基础依赖和可选 SuperFastMode，正式局的求解器不被带进练习。

### 求解器回合重打合同 v1（2026-10-28，与求解器会话握手确认）

账本与求解器都是本项目的 Mod，互相认可。验收看合同，不看 jar 哈希或逐版本重验；sha256 只用来记录测的是哪个构建。

- **成员**：`SolverMod.instance`（static）、`runAutoPlayer`、`cancel(boolean)`、`stopAll()`、`receivePostUpdate()`、`receivePostRender(SpriteBatch)`。
- **行为**：账本重打前依次调用 `cancel(false)` 和 `stopAll()`，之后旧计划清空，`auto` 与 `runAuto` 都关闭，进入暂停，回复代次严格增大，晚到的旧回复作废。恢复后账本把 `runAutoPlayer` 设为新的玩家对象，求解器不会因为换了玩家对象而重新开启整局自动。
- **账本侧**：`ReplayEnvironment` 对 `sts1solver` 只设下限 0.9.6+，带后缀的测试版不算更新；`SolverReplay.contract()` 在运行时核对成员，缺一个就把录制与重打判为不支持。账本发版前跑原生同装探针（`testing.md`）。
- **求解器侧**：`overlay/test/RngModsCheck.java`（受信任列表含 `spire-ledger`）和 `overlay/test/ReplayContractCheck.java`（成员与上述行为）在每次 `overlay/build.py` 时运行；落地于 sts1-combat-solver `release/v0.9.7` 的 136ddfa，随 v0.9.7 合入 main。第 5 条行为测的是判定函数 `SolverMod.rearms`，不是整条 update 链。
- **改动规则**：任何一边要改成员或语义，先在对方仓库提 issue 或通知对方，合同版本号加一；不允许静默改名。

### 默认最近一局与抓率图（2026-10-26）

导航状态由 `HistoryScreen` 统一管理。首次索引完成后，只在当前存档位选择最近记录，优先当前局；用户主动切到历史或统计后，异步索引不得把页面拉回去。三个顶部入口随时可达，关闭直接回到游戏。统计保留筛选，详细诊断的返回只收起诊断。

`stats.PickTiers` 只对现有 `Metrics` 的 `pick:<act>:<card>` 指标分档，不重复计算抓率。`PickRateChart` 使用同一份指标条目渲染预览和离屏 PNG；卡图与遗物图只读游戏已有资源。实际战损的遭遇图由 `EncounterArt` 在运行时用玩家本机游戏的怪物资源绘制：`MonsterHelper.getEncounter(key)` 生成该遭遇的怪物组，按 `AbstractMonster.render` 的本体部分（骨骼动画或贴图，六火亡魂另画 `HexaghostBody`，不画血条和意图）在离屏缓冲里画两遍，黑底一遍、白底一遍，由两遍之差还原透明度，再裁到实际像素。生成怪物会在地牢随机数上掷血量和组员，所以期间用临时随机数替换 `AbstractDungeon` 的全部随机数字段，结束后放回，浏览统计不推进对局随机数（`StatsOptionsProbe` 用小鬼群核对）。游戏不认识的遭遇返回道歉史莱姆，按无图处理，留空白卡框。绘制保存并恢复当前帧缓冲、视口和画笔矩阵，所以导出分块渲染时也能调用；预览每帧最多新画 2 个遭遇，导出一次画完。`~/.spire-ledger/monster-images`（`PickRateChart.imageDir()`）与 `mods/SpireLedger/monster-images`、`mods/spire-ledger/monster-images` 里与遭遇同名的 png/jpg 优先，作为玩家的替换图；不读其他 Mod 的目录。文件名归一化：去扩展名、转小写、去掉非字母数字，容忍开头的人数前缀。jar 不带任何图片，`build.ps1` 打包后检查，命中图片即失败。卡图与遗物图不构造卡牌、不调用 RNG。（用户决定，2026-11-01）导出由玩家点击触发，输出到 `~/.spire-ledger/exports/` 的新目录，包含所选幕的 PNG、`metrics.csv` 与 `metadata.json`；幕下拉选“全部”时按幕各写一组图，CSV 每个幕一张表。每页最多 18 行，失败清理本次新建产物并恢复渲染状态。既有导出不覆盖，源记录不变。

### 标准化率与四类长图（2026-10-29）

`stats.Standardized` 负责卡牌、涅奥和 Boss 遗物的 Plackett-Luce 点估计（`standardized-v2`）：选择集、先验和 MM 迭代与 TinyCat 对齐，`SKIP` 与其他选项一起参与拟合并报告份额。`stats.Campfire` 从原版 `.run` 逐层重放牌组，恢复每次火堆的候选池与遗物可用性，输出 `Run.Campfire`；`Metrics.campfires()` 按幕聚合成行动次数、候选池、睡觉和 `campfire_pref` 标准化倍（`campfire-v1`）。`Metrics` 另新增 `score` 与三类删牌均值，`Metric` 保存平方和用于区间。

`StatsScreen` 用 `chartKind` 选择五类内容，口径菜单随内容切换（卡牌和火堆两种，涅奥与 Boss 遗物在 2026-10-30 起为一般率/标准率/胜率三种，共用 `choiceRateMenu`，实际战损没有口径菜单）；总览区间由内核 `Metric.interval()` 计算（胜率 Wilson，均值 ±1.96 SE），界面只决定哪些行显示、被结果筛选隐藏或样本不足的行不显示区间；离线 `test/StatsDump.java` 读同一个方法。`Standardized` 与 `PickTiers` 的 Jenks 按公式在本仓库重写，参数取自 TinyCat 文档，不沿用其代码（2026-10-28）。`PickRateChart` 的导出按内容与幕写长图：卡牌 `act-N[-standardized].png`，火堆/实际战损/Boss 遗物 `<kind>-actN[-standardized|-win].png`（涅奥没有幕，`neow[-standardized|-win].png`）；同一目录写 `metrics.csv` 与 `metadata.json`。`StatsScreen` 只提供“导出 PNG+CSV”一个按钮，`PickRateChart.exportActs()` 按幕下拉决定范围：选具体幕只写该幕（Boss 遗物只有第 1、2 幕），选“全部”展开到该内容的全部幕，每幕单独成图，CSV 合并为一个文件。`writeTiled` 把超过 GPU 纹理上限的图按 4096px 高的带渲染到同一 `Pixmap`，再写单个 PNG，避免火堆标准率（约 16500px）被截断。导出目录、`metadata.json` 和失败清理沿用旧路径，不覆盖既有导出。

### 三口径分离、分段分档与 CSV（2026-10-30）

涅奥与 Boss 遗物的选取列表不再混入胜率：`Metrics` 的 `neow:*`/`boss:<act>:*` 只进一般率，`standardized-v2` 份额只进标准率，胜率走 `related:neow:*`/`related:boss:<act>:*` 的独立口径（每局只计一次，胜负未知进 `excluded_count`；仅胜/仅败筛选下由 `Metric.shown()` 统一判为 `hidden_by_result_filter`，界面列表显示“—”）。`StatsScreen` 的三口径只在所选口径下取对应前缀；Boss 在三种口径下都按幕插入表头。卡牌标准率把跳过纳入拟合与 S–R00 分档，歌唱碗并入跳过，另用 `PickTiers.actual()` 列实际占比；一般率仍把它们并入候选。“跳过与歌唱碗”页（`StatsScreen.skipPage()`）在 2026-10-31 删除：`resetCategory()` 的卡牌与构筑从 6 项回到 5 项，`skip:`、`bowl:` 只在卡牌页的一般率里作为普通候选行、在标准率里作为 `SKIP` 份额与“实际占比”行出现，不再有单独的按前缀排序分支。

非卡牌导出与预览共用 `PickRateChart.buildSections()`，每个分节调用 `PickTiers.sectionTiers()`：只对达到最少样本量的条目做 Fisher-Jenks（`naturalTiers(values,7)`，最多 7 档，等值同档），其余标 `PickTiers.LOW`，单独列在该分节末尾（标准率按该条目的出现次数判断）；档位标签 S–R00，分节规则行给出本次的数值范围；行动次数是计数、不分档，火堆标准化倍里的睡觉是占比、和倍数分开不分档（`section-tiers-v1`）。`stats.Csv.body()` 把当前筛选的同一份 `Map<String,Metric>` 写成固定列；`Table.addModel()` 写模型行（标准率份额、火堆标准化倍），数值来自模型，分子分母和区间留空，样本与状态取支撑该条目的观测指标（UTF-8 BOM、RFC 4180），被筛选隐藏的数字留空并保留 `status`，所有能形成区间的比率与均值都写 95% 区间；界面与图片只给总览的胜率与五个均值显示区间。实际战损是第五类内容：`PickRateChart.hpLoss()` 按幕建节，每节先放 `act_hp_loss:<act>` 的每局均值，再按场均战损降序列出 `hp_loss:` 行（导出只含全部对局，不含有无回复拆分）。2026-10-31 起第 1–3 幕改用 TinyCat 危险度分档（`PickTiers.dangerTiers()`，Boss 由 `Metric.boss` 判断，即地图 `B` 节点的战斗）：Boss 单独为 T0，其余遭遇按场均战损降序等人数切 T1–T4（极危、高危、中危、低危），同值同档，样本不足单列；事件战和第四幕不分档，`section_tiers` 只记录 T0–T4。遭遇图的来源见上文 `EncounterArt`；`metadata.json` 在实际战损页额外写 `image_dir`、`image_source`；纹理按遭遇键缓存（`2 Louse` 与 `3 Louse` 分开），关闭统计页时释放。导出只有一个“导出 PNG+CSV”按钮：幕下拉选具体幕时写该幕的图片、该幕的 `metrics.csv` 和 `metadata.json`；选“全部”时展开到该内容的全部幕，每幕单独成图，CSV 合并为一个文件、按幕分成多张表；所选范围没有任何行时 `exportable()` 为假，不建空目录。`metadata.json` 含 `views`、`win_captions`、`section_tier_version`、`all_acts` 与 `acts`（实际战损不写分档/标准率字段）。`Csv` 为 v2：15 个中文列（表、幕、口径、分档、条目、数值、单位、分子、分母、样本、状态、95% 下限、95% 上限、含估算、未知不计），每张表第一列重复表名，空行分隔；行只来自本次导出的幕与条目，比率与区间写百分比。

### 卡牌屏的口径（2026-11-01）

`Native.read` 对 `card_choices` 的**每一条**建一个 `Run.Screen`，只在 `picked` 为空或 `floor` 缺失时跳过，不再按“该层有战斗记录”过滤（旧代码用 `fightFloors.contains(floor)`，把事件、商店、火堆和涅奥来源的屏整类丢掉，无色牌因此全部缺席）。`act` 由 `act(bounds,floor)` 推出，第 0 层（涅奥开局选牌）落在第 1 幕。`kind` 仍由 `picked` 判定 `skip`/`bowl`/`card`；`.run` 不记录歌唱碗是否在售，`bowlOffered` 留 `null`，这些屏不进歌唱碗率。`Metrics.screens()` 的奖励覆盖率仍按“有战斗且已收到奖励屏的战斗 / 应有奖励的战斗”计算，新增的非战斗屏不改变它的分子分母。Ledger 记录层只有 `COMBAT_REWARD` 候选，schema 只增不改（§2.5），所以账本局的屏少于同一局的原版 `.run`，合并时按局逐个取，不做补偿。

### 幕下拉与回复下拉（2026-10-31）

`Metrics` 新增 `damage_act:<act>:<encounter>`、`death_act:<act>:<encounter>`、`encounter_potions_act:<act>:<encounter>` 与 `potions_act:<act>`，作为原有总键的按幕补充，CSV 键只增不改；`potions_act` 把逐层药水记录按该层战斗所在幕归并，战斗外的使用只留在总 `potions`。`death_act` 的死亡事实仍沿用 `outcome=death`、`floor=floor_reached`、`encounter=killed_by`，只按该场战斗所在幕分键。`Native` 读取药水时优先用 RunHistoryPlus 的逐层 `potion_use_per_floor`（第 i 项对应第 i+1 层，每层最多 9 瓶），原版 `potions_floor_usage` 只记录最后使用的楼层，仅在没有 RHP 字段时回退。`StatsScreen.actShown()` 决定当前页是否显示幕下拉：卡牌、Boss 遗物、遭遇平均承伤、药水、火堆和实际战损；Boss 遗物只存在第 1、2 幕，用只有三行的 `bossActMenu`，其余用五行的 `actMenu`。`hiddenRow()` 处理实际战损的回复视图：全部只留基础 `hp_loss:` 行，有回复/无回复只留对应分组，`act_hp_loss:` 在三种视图都保留。`campfirePage()` 同样遵守幕筛选；切换分类或重置回到“全部”。导出页的幕选择独立于统计页的幕/回复筛选：`chartAct`（卡牌、火堆、实际战损，第 1–4 幕）与 `chartBossAct`（Boss 遗物，第 1–2 幕）只决定“导出 PNG+CSV”的幕，`PickRateChart.hpLoss()` 只用 `hp_loss:`，不含有无回复拆分；涅奥没有幕，不显示幕下拉。

### 商店与事件页的按幕拆分（2026-11-01）

`Native.acts()` 原本已按幕记录 `purchase:<item>` 与 `event:<name>`（分别供 `Export` 的 `purchase` 行与 `event_related` 行使用），现在再补两类按幕键：`eventchoice:<name> / <choice>` 与 `remove:shop:<card>`。两者都不改原有键——CSV 的 `event_related` 仍按事件名聚合（`Export` 只认 `event:` 前缀），`purge:SHOP` 仍是删牌总数，所以导出粒度不变。`remove:shop:` 由 `items_purged` 与 `items_purged_floors` 逐条配对得出，此前只按数量累计。

`Metrics.countsByAct()` 读 `Native.ACTS`，把这三类键翻成界面的按幕指标 `purchase:<act>:<item>`、`remove:shop:<act>:<card>`、`event:<act>:<name> / <choice>`，并对事件补 `related:event:<act>:…` 的选后胜率；`act` 为 0（楼层字段缺失）的条目只留在无幕总计里，构成记录（Ledger）没有楼层，也只进总计。

`StatsScreen` 侧：`actShown()` 把“战斗与资源”的第 4 项（商店购买与删除）与第 6 项（事件选择）纳入，共用 `actMenu`；`actOf()` 与 `metricGroup()` 认得这三类带幕键（`isAct()` 只认单个数字 0–4，事件名不会被当成幕号），`actHeader()` 给出“第 N 幕 · 商店购买与删除 / 事件选择”，`label()` 在显示前剥掉幕号。排序沿用 `metricOrder` 的组序，因此不带幕的总计排在各幕之前。两页都不进导出：`PickRateChart` 的 `chartKind` 只有五类内容，`metrics.csv` 的 `purchase` 行本来就有 `act` 维度。

事件页另有一个口径下拉 `eventRateMenu`（次数 / 胜率），与涅奥、Boss 遗物的 `choiceRateMenu` 同构但只有两档（事件没有标准化拟合）。`mode()` 在事件页返回 `eventMode==1?2:0`，`rebuild()` 据此二选一地往 `prefixes` 里放 `event:` 或 `related:event:`——两种单位不同（事件次数 / 局）的行因此不会同时出现。`visibleMenus()` 与 `layout()` 把下拉放在第 1 行中间那格（分类在左、幕在右），`changedSelectionTo()` 收到该菜单时重置分页并重建。

### 回合快照的抓取时机（2026-11-01）

`TurnRecorder.observe()` 原本用 `TurnAdapter.stable()` 判断“能不能抓”，而 `stable()` = `!HistoryScreen.active() && restoreReady()`，那 15 项条件是为**恢复**服务的（`phase==WAITING_ON_USER`、`endTurnButton.enabled`、四类特效队列全空等）。抓取需要的条件比恢复少得多，用恢复的条件去等，就等到了玩家已经出完牌的时刻；`lastKey` 一旦落盘（无论 `recorded` 还是 `missing`）又锁死该回合，不再重试。

现在拆成两套：

- `restoreReady()` 原样保留，只给 `TurnAdapter.restore()` 用——恢复前必须完全静止。
- `TurnAdapter.capturable()` 给抓取用：战斗房间、无屏幕、`cardQueue` / `actions` / `preTurnActions` 为空、`currentAction` 为空、`turnHasEnded` 为假、`player.isEndingTurn` 为假。去掉 `phase`、`endTurnButton.enabled` 和特效清空这三项瞬态条件，它们正是把抓取拖到出牌之后的原因。

`observe()` 改用 `capturable()`，并且不再因为“本回合已经出过牌”就放弃：只要 `compatible()` 就抓一份，`played` 记 `cardsPlayedThisTurn.size()`（0 = 干净的回合起点，N = 已出 N 张）。原来的 `damageReceivedThisTurn` / `totalDiscardedThisTurn` 判据删除——回合开始结算的中毒、荆棘伤害和开局弃牌会把它们置位，但与“玩家是否出牌”无关。没有抓到时的原因由 `TurnRecorder.blocker` 上报，历史页在提示后附加“当前停在：…”。

### S/L 分支、内容去重与存储配额（2026-11-01）

`Recorder` 每次进程启动都新建 `branch`，`lastKey` 只在内存里，于是每次 S/L 都换分支、把整场重记一遍；单点约 155 KB，`MAX_POINTS=10000` 折合每局约 1.5 GB。S/L 的每一次尝试都是“同一状态、不同动作、不同结果”的配对样本，属于训练数据，不能用“清空重记”糊掉，所以改四件事：

- **分支树**：`Recorder` 新增 `branchParent`、`branchParentSnapshot`、`branchReason`。构造时读 `replay/manifest.json` 里最后一条 `availability="recorded"` 的快照当父节点，理由 `process_restart`；`forkBranch()`（回合重打）理由 `in_combat_turn_rollback`。条目带 `branch_parent` / `branch_parent_snapshot` / `branch_reason`。父指针指向**最近一份真正落盘的**快照，不指向 `duplicate` 条目（那种条目没有文件，指过去就断了链）。
- **S/L 判定**：`Hooks` 给 `SaveAndContinue.loadSaveFile` 打前置补丁置 `pendingLoad`，`battle()` 据此决定身份：
  - 恢复（SaveState 回放）沿用快照里的 `combat_id`；
  - 同进程读档、楼层没变 → 沿用内存里的 `combat_id`；
  - **跨进程**读档（退出游戏再进）：内存全清，从 manifest 的 `last_combat: {combat_id, floor}` 取回；楼层一致就沿用，楼层变了就新开。这张表跟着每次写盘一起落，不额外加文件。
  - 新战斗（没有读档）一律新 `combat_id`。
  - 已知局限：同一楼层打两场（只有 Mod 才可能）会被当成同一场，因为判据只有楼层。
- **内容去重**：写入前对快照状态主体算 SHA-256，规则只有一份，在 `SnapshotShape` 里——它同时是“恢复后是否还原成了同一局面”的比对面。剔除 `identity` 与表现类字段（`hb` 系列、`draw_x/draw_y`、`dialog_x/dialog_y`、`anim_x/anim_y`、`intent_alpha`/`intent_alpha_target`/`intent_offset_x`、`reticle_alpha`/`reticle_rendered`、`previous_screen_name`、`uuid`、`wait_timer`、`hovered`、`just_hovered`），死亡怪物的 `death_timer` 夹到 0。**`cur_map_node_state.taken` 故意保留**：首次进入是 `false`、读档回来是 `true`，这是地图状态不是表现，按它分开存才不会把地图节点还原成未访问。
  - 两张表以前各写一份、已经漂移：`reticle_rendered` 只在比对面被当成表现字段，于是第 20 层第 1 回合的 6 份快照被算成 2 个 blob，白占 155 KB。现在编译期只有一张 `SnapshotShape.VISUAL`，`TurnFilesCheck` 逐个字段断言“只差这个字段的两块板必须同哈希”。
  - 规则收紧后哈希值会变：升级前写入的 `content_sha256` 按旧规则算，升级后同一局面会重新落一份（每局最多一次），旧 blob 仍可正常读取。
  - manifest 的 `blobs` 表按内容寻址；内容已存在就复用同一个 `turns/<uuid>.json`，条目记 `availability="duplicate"` + `duplicate_of`，`snapshot_id` 仍每点唯一。
- **配额**：单局 `BUDGET_BYTES = 512 MB`（可用 `-Dspireledger.turnBudgetMb` 覆盖）。超预算按 `captured_at` 从旧到新淘汰**最老的分支**，当前分支不动；条目改 `availability="evicted"` 并清空 `relative_path`，随后删除不再被引用的 blob。淘汰不删记录，训练侧能看出“这里曾有数据、内容已回收”，界面只列 `recorded`。

### 恢复后的可见性与历史页的两个下拉（2026-11-03）

**黑幕遮住战场。** 练习实例进入战斗后立刻恢复，而此时房间还在跑入场淡入；`readyAfterRestore()` 清空了特效队列，淡入于是被截断在中间，世界层带着一层黑幕继续渲染，而 HUD、手牌、结束回合按钮画在它上面。玩家看到的就是“场地是空的、敌人没显示，但能操作”。

改两处：`ReplayLauncher.checkPracticeStartup()` 在第 1 步到第 2 步之间等 `AbstractDungeon.fadeColor.a` 归零再恢复，不再在淡入中途动手；`readyAfterRestore()` 兜底把 `isFadingIn`、`waitingOnFadeOut` 置假并把 `fadeColor.a` 归零。实测同一快照：修复前恢复后 0.6 s 的截图整屏黑（亮度 0），1.5 s 时世界层仍是暗的；修复后 0.6 s 的截图就是完整战场。探针按截图采样平均亮度，要求恢复后的首帧亮度大于 10/255（整屏黑接近 0，正常战斗画面约 22）。

**“尝试”下拉画不出来。** `RunReview.render()` 只画了 `turnMenu`，`attemptMenu` 只在 `update()` 里更新、从未渲染；`DropdownMenu` 的位置是在 `render()` 里算出来的，所以“尝试”下拉既看不见也点不中。现在两个下拉并排画在 y=0.27 一行（`turnMenu` 在 x=0.70，`attemptMenu` 在 x=0.53），打开的那个最后画，列表不再压住“重打此回合”按钮。

### 四行导出抬头与求解器标记（2026-10-31）

`PickRateChart.captions()` 固定 4 行：`head()` 2 行（职业、进阶、结果、胜负口径、放弃开关；选中局数与胜率、最少出现次数）与 `rule()` 2 行（本图口径、分档说明），不再拼 `versions()` 与导出时间。日期范围、存档位、标准模式、种子局、来源与 SL 路径、未知操作来源计数、排除的已知自动化局数、口径版本和导出时间由 `remarks()` 按字段写进 `metadata.json`；实际战损另记导出范围与图片来源，涅奥与 Boss 遗物另记区间说明。抬头行数 4 与每行 60 个汉字的上限由原生探针 `StatsOptionsProbe` 断言行数与每行长度（它跑在真实游戏里，能直接读 `captions`），新增字段先过 metrics.md 第 8 节的“图片必需”表，放不下就进 `remarks`。`ascensionLabel()` 在筛选为“全部”时按选中局写出实际范围（如“全部（实际 A20）”）。胜率取 `win` 指标的分子分母，与界面一致。标题由 `solverMark()` 追加“ · 含求解器”：`Run.automation` 是 `Ledger` 在任一确认决策的 `action_source=known_automation` 时写下的 `Fact`，未关联的原版 `.run` 不写该事实，因此不会误标。


`Stats.readNative` 先严格 UTF-8、再 GBK、最后系统默认解码原版 `.run`，并去掉 BOM；与 TinyCat 在中文 Windows 上的读取方式一致。
