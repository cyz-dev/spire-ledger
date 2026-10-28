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
- 已审核的同装 Mod：BaseMod、StSLib、SuperFastMode 1.0.8、求解器 `sts1solver`。求解器的执行动作经 `withAutomation` 标为 `known_automation`；求解器 0.9.6 同装时支持重打，回退前清空旧结果并停止自动操作。其他未审核 Mod 一律记 `unknown`。
- SuperFastMode 1.0.8 为可选的已审核加速 Mod。记录与回合附件读取均接受它；练习子进程复制已加载的 jar 及 `SuperFastModeConfig.properties`。快照不保存插值或速度设置，沿用现有动作队列稳定边界。人工操作仍须经过原生 UI 提交路径。

### 构建档

`full`（默认，含 `replay`）；`no-replay`（剔除 `replay` 与 SaveState，同一 modid），作为上游拒绝授权时的退路，平时不发布。

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

自有代码 GPL-3.0；改造 SaveState 仍内嵌主 jar，NOTICE 写明它不在 GPL 授权范围内。用户已知这一许可冲突并接受，延续 2026-10-14 State Notes 的同类决定。

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
- **M4 CI 与发布**：自托管 runner 工作流；发布由用户逐次授权。不可逆点：modid `spire-ledger` 与记录 schema 公开后只能兼容扩展。 `no-replay` 构建档要求回合重打可剔除，届时拆出 `spireledger.record` 与 `spireledger.replay`（proposed）。

State Notes 仓库在 M0 完成后冻结为历史，不再发布。

## Open decisions

- 已决定（2026-10-25）：State Notes 旧记录留在 `~/.sts1-state-notes/` 原地只读，新 Mod 同时读旧 schema `sts1-state-v1`；跨项目一侧，SlaySpire1RL `tools/state_runs/import_run.py` 同时接受两个 schema 名（SlaySpire1RL 3d1002a）。每局摘要是统计索引的内部机制，见 M1。
- 是否保留 State Notes 的 Git 历史迁入（历史中含内部证据路径与记录，影响日后公开）。blocks: M0。

### 楼层悬停复盘与求解器同装（2026-10-26）

`HistoryScreen` 继续负责异步读取、分支选择和重打入口。`RunReviewModel` 只把所选路径的 JSON 投影成楼层与牌组，不读取游戏对象；`RunReview` 负责路线、悬停和固定详情，其状态在切换对局时重置。界面只读取卡牌和遗物库已有纹理，不构造或升级卡牌，不调用游戏随机数。

`ReplayEnvironment` 统一录制与附件读取的依赖版本边界。`SolverReplay` 在复盘打开时暂停求解器更新并隐藏其面板；当前战斗回退前通过求解器自身取消入口使旧回复失效、清空旧路线并停止自动操作。恢复或回滚后同步求解器的玩家引用，防止换成恢复后的玩家对象时重新开启自动执行。独立练习仍使用基础依赖和可选 SuperFastMode，正式局的求解器不被带进练习。

### 默认最近一局与抓率图（2026-10-26）

导航状态由 `HistoryScreen` 统一管理。首次索引完成后，只在当前存档位选择最近记录，优先当前局；用户主动切到历史或统计后，异步索引不得把页面拉回去。三个顶部入口随时可达，关闭直接回到游戏。统计保留筛选，详细诊断的返回只收起诊断。

`stats.PickTiers` 只对现有 `Metrics` 的 `pick:<act>:<card>` 指标分档，不重复计算抓率。`PickRateChart` 使用同一份指标条目渲染预览和离屏 PNG；只读游戏已有卡图，不构造卡牌或调用 RNG。导出由玩家点击触发，输出到 `~/.spire-ledger/exports/` 的新目录，包含 PNG 和 `metadata.json`。每页最多 18 行，失败清理本次新建产物并恢复渲染状态。既有导出不覆盖，源记录不变。

### 标准化率与四类长图（2026-10-29）

`stats.Standardized` 负责卡牌、涅奥和 Boss 遗物的 Plackett-Luce 点估计（`standardized-v1`）：选择集、先验和 MM 迭代与 TinyCat 对齐，`SKIP` 只作对手。`stats.Campfire` 从原版 `.run` 逐层重放牌组，恢复每次火堆的候选池与遗物可用性，输出 `Run.Campfire`；`Metrics.campfires()` 按幕聚合成行动次数、候选池、睡觉和 `campfire_pref` 标准化倍（`campfire-v1`）。`Metrics` 另新增 `score` 与三类删牌均值，`Metric` 保存平方和用于区间。

`StatsScreen` 用 `chartKind`、`rateMenu`、`chartCampfireRate` 三个下拉切换四类内容和两种口径；总览区间由内核 `Metric.interval()` 计算（胜率 Wilson，均值 ±1.96 SE），界面只决定哪些行显示、被结果筛选隐藏或样本不足的行不显示区间；离线 `test/StatsDump.java` 读同一个方法。`Standardized` 与 `PickTiers` 的 Jenks 按公式在本仓库重写，参数取自 TinyCat 文档，不沿用其代码（2026-10-28）。`PickRateChart` 的导出按内容写长图：卡牌 `act-N[-standardized].png`，其余 `<kind>[-standardized].png`；`writeTiled` 把超过 GPU 纹理上限的图按 4096px 高的带渲染到同一 `Pixmap`，再写单个 PNG，避免火堆标准率（约 16500px）被截断。导出目录、`metadata.json` 和失败清理沿用旧路径，不覆盖既有导出。

`Stats.readNative` 先严格 UTF-8、再 GBK、最后系统默认解码原版 `.run`，并去掉 BOM；与 TinyCat 在中文 Windows 上的读取方式一致。
