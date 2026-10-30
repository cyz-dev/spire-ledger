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
- 已审核的同装 Mod：BaseMod、StSLib、SuperFastMode 1.0.8、求解器 `sts1solver`；另有 ColoredMap 2.4.1、coloredPowertips 2.3.0、HighlightPath 0.0.3，以及 2026-10-28 经原生探针回归的 RunHistoryPlus 1.8.3、HideInfo 0.0.1、justclick 0.1.1、lolifontmod 0.2（只接受这些精确版本）。求解器的执行动作经 `withAutomation` 标为 `known_automation`；求解器 0.9.6 及以后同装时支持重打，回退前清空旧结果并停止自动操作。版本只设下限，`SolverReplay.contract()` 在运行时核对账本调用和 patch 的求解器成员（`instance`、`cancel(boolean)`、`stopAll()`、`runAutoPlayer`、`receivePostUpdate`、`receivePostRender`），缺任何一个就按不支持处理；带后缀的测试版不算更新（2026-10-28）。其他未审核 Mod 一律记 `unknown`。
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

`stats.PickTiers` 只对现有 `Metrics` 的 `pick:<act>:<card>` 指标分档，不重复计算抓率。`PickRateChart` 使用同一份指标条目渲染预览和离屏 PNG；卡图与遗物图只读游戏已有资源，实际战损优先读玩家放在本地 `monster-images` 目录的遭遇图（游戏自带的怪物素材是 Spine 图集，不能直接当立绘），本地没有时回退到私有测试构建打入 jar 的 `monster-images/<key>.png`，不构造卡牌或调用 RNG。导出由玩家点击触发，输出到 `~/.spire-ledger/exports/` 的新目录，包含所选幕的 PNG、`metrics.csv` 与 `metadata.json`；幕下拉选“全部”时按幕各写一组图，CSV 每个幕一张表。每页最多 18 行，失败清理本次新建产物并恢复渲染状态。既有导出不覆盖，源记录不变。

### 标准化率与四类长图（2026-10-29）

`stats.Standardized` 负责卡牌、涅奥和 Boss 遗物的 Plackett-Luce 点估计（`standardized-v1`）：选择集、先验和 MM 迭代与 TinyCat 对齐，`SKIP` 与其他选项一起参与拟合并报告份额。`stats.Campfire` 从原版 `.run` 逐层重放牌组，恢复每次火堆的候选池与遗物可用性，输出 `Run.Campfire`；`Metrics.campfires()` 按幕聚合成行动次数、候选池、睡觉和 `campfire_pref` 标准化倍（`campfire-v1`）。`Metrics` 另新增 `score` 与三类删牌均值，`Metric` 保存平方和用于区间。

`StatsScreen` 用 `chartKind` 选择五类内容，口径菜单随内容切换（卡牌和火堆两种，涅奥与 Boss 遗物在 2026-10-30 起为一般率/标准率/胜率三种，共用 `choiceRateMenu`，实际战损没有口径菜单）；总览区间由内核 `Metric.interval()` 计算（胜率 Wilson，均值 ±1.96 SE），界面只决定哪些行显示、被结果筛选隐藏或样本不足的行不显示区间；离线 `test/StatsDump.java` 读同一个方法。`Standardized` 与 `PickTiers` 的 Jenks 按公式在本仓库重写，参数取自 TinyCat 文档，不沿用其代码（2026-10-28）。`PickRateChart` 的导出按内容与幕写长图：卡牌 `act-N[-standardized].png`，火堆/实际战损/Boss 遗物 `<kind>-actN[-standardized|-win].png`（涅奥没有幕，`neow[-standardized|-win].png`）；同一目录写 `metrics.csv` 与 `metadata.json`。`StatsScreen` 只提供“导出 PNG+CSV”一个按钮，`PickRateChart.exportActs()` 按幕下拉决定范围：选具体幕只写该幕（Boss 遗物只有第 1、2 幕），选“全部”展开到该内容的全部幕，每幕单独成图，CSV 合并为一个文件。`writeTiled` 把超过 GPU 纹理上限的图按 4096px 高的带渲染到同一 `Pixmap`，再写单个 PNG，避免火堆标准率（约 16500px）被截断。导出目录、`metadata.json` 和失败清理沿用旧路径，不覆盖既有导出。

### 三口径分离、分段分档与 CSV（2026-10-30）

涅奥与 Boss 遗物的选取列表不再混入胜率：`Metrics` 的 `neow:*`/`boss:<act>:*` 只进一般率，`standardized-v1` 份额只进标准率，胜率走 `related:neow:*`/`related:boss:<act>:*` 的独立口径（每局只计一次，胜负未知进 `excluded_count`；仅胜/仅败筛选下由 `Metric.shown()` 统一判为 `hidden_by_result_filter`，界面列表显示“—”）。`StatsScreen` 的三口径只在所选口径下取对应前缀；Boss 在三种口径下都按幕插入表头。卡牌标准率把跳过纳入拟合与 S–R00 分档，歌唱碗并入跳过，另用 `PickTiers.actual()` 列实际占比；一般率仍把它们并入候选。

非卡牌导出与预览共用 `PickRateChart.buildSections()`，每个分节调用 `PickTiers.naturalTiers(values,7)`（Fisher-Jenks，最多 7 档，等值同档），档位标签 S–R00，分节规则行给出本次的数值范围；低于最少样本量的条目不参与断点，单独列在该分节末尾（标准率按该条目的出现次数判断），行动次数是计数、不分档（`section-tiers-v1`）。`stats.Csv.body()` 把当前筛选的同一份 `Map<String,Metric>` 写成固定列（UTF-8 BOM、RFC 4180），被筛选隐藏的数字留空并保留 `status`，所有能形成区间的比率与均值都写 95% 区间；界面与图片只给总览的胜率与五个均值显示区间。实际战损是第五类内容：`PickRateChart.hpLoss()` 按幕建节，每节先放 `act_hp_loss:<act>` 的每局均值，再按场均战损降序列出 `hp_loss:` 行（导出只含全部对局，不含有无回复拆分）。2026-10-31 起第 1–3 幕改用 TinyCat 危险度分档：Boss 单独为 T0，其余遭遇按场均战损降序等人数切 T1–T4（极危、高危、中危、低危），同值同档，样本不足单列；事件战和第四幕不分档，`section_tiers` 只记录 T0–T4。遭遇图按文件名匹配（去除非字母数字、忽略大小写），依次查 `mods/SpireLedger/monster-images`、`mods/spire-ledger/monster-images`、`monster-images` 与 `~/.spire-ledger/monster-images`，都没有时读 jar 内 `monster-images/<key>.png`（只有 `build.ps1 -BundleMonsterImages` 的私有构建才打包，并写入 `bundled.txt` 标记），仍缺失则用空白卡框；纹理按遭遇缓存，关闭统计页时释放。私有打包步骤从本地目录或含 `tinycat/monsters/` 的 jar 读取图片，只写 `build/`，发布构建不含图片。导出只有一个“导出 PNG+CSV”按钮：幕下拉选具体幕时写该幕的图片、该幕的 `metrics.csv` 和 `metadata.json`；选“全部”时展开到该内容的全部幕，每幕单独成图，CSV 合并为一个文件、按幕分成多张表。`metadata.json` 含 `views`、`win_captions`、`section_tier_version`、`all_acts` 与 `acts`（实际战损不写分档/标准率字段）。`Csv` 为 v2：15 个中文列（表、幕、口径、分档、条目、数值、单位、分子、分母、样本、状态、95% 下限、95% 上限、含估算、未知不计），每张表第一列重复表名，空行分隔；行只来自本次导出的幕与条目，比率与区间写百分比。

### 幕下拉与回复下拉（2026-10-31）

`Metrics` 新增 `damage_act:<act>:<encounter>`、`death_act:<act>:<encounter>`、`encounter_potions_act:<act>:<encounter>` 与 `potions_act:<act>`，作为原有总键的按幕补充，CSV 键只增不改；`potions_act` 把逐层药水记录按该层战斗所在幕归并，战斗外的使用只留在总 `potions`。`death_act` 的死亡事实仍沿用 `outcome=death`、`floor=floor_reached`、`encounter=killed_by`，只按该场战斗所在幕分键。`Native` 读取药水时优先用 RunHistoryPlus 的逐层 `potion_use_per_floor`（第 i 项对应第 i+1 层，每层最多 9 瓶），原版 `potions_floor_usage` 只记录最后使用的楼层，仅在没有 RHP 字段时回退。`StatsScreen.actShown()` 决定当前页是否显示幕下拉：卡牌、Boss 遗物、遭遇平均承伤、药水、火堆和实际战损；Boss 遗物只存在第 1、2 幕，用只有三行的 `bossActMenu`，其余用五行的 `actMenu`。`hiddenRow()` 处理实际战损的回复视图：全部只留基础 `hp_loss:` 行，有回复/无回复只留对应分组，`act_hp_loss:` 在三种视图都保留。`campfirePage()` 同样遵守幕筛选；切换分类或重置回到“全部”。导出页的幕选择独立于统计页的幕/回复筛选：`chartAct`（卡牌、火堆、实际战损，第 1–4 幕）与 `chartBossAct`（Boss 遗物，第 1–2 幕）只决定“导出 PNG+CSV”的幕，`PickRateChart.hpLoss()` 只用 `hp_loss:`，不含有无回复拆分；涅奥没有幕，不显示幕下拉。

### 导出备注精简与求解器标记（2026-10-31）

`PickRateChart.captions()` 只写两行公共备注（职业、进阶、结果、口径、放弃开关；选中局数、胜率与分子分母、最少出现次数）、本图口径一行和分档说明；日期、来源、存档位、未知操作来源计数、排除的自动化局数、口径版本和生成时间移到 `metadata.json` 的 `remarks()`，实际战损另记导出范围、图片来源与是否内置图片，涅奥与 Boss 遗物另记区间说明。`ascensionLabel()` 在筛选为“全部”时按选中局写出实际范围（如“全部（实际 A20）”）。胜率取 `win` 指标的分子分母，与界面一致。标题由 `solverMark()` 追加“ · 含求解器”：`Run.automation` 是 `Ledger` 在任一确认决策的 `action_source=known_automation` 时写下的 `Fact`，未关联的原版 `.run` 不写该事实，因此不会误标。


`Stats.readNative` 先严格 UTF-8、再 GBK、最后系统默认解码原版 `.run`，并去掉 BOM；与 TinyCat 在中文 Windows 上的读取方式一致。
