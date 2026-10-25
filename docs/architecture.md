# Spire Ledger architecture

Last confirmed: not yet confirmed
Product spec: [product.md](product.md)

## Target structure

一个 ModTheSpire Mod，modid `spire-ledger`，产出 `SpireLedger.jar`。源码在独立仓库 `sts1/spire-ledger`。模块边界由包保证，Mod 边界只有一个。

| 包 | 职责 | 改动节奏 | 环境要求 | 状态 |
|---|---|---|---|---|
| `record` | 唯一的局内事实写者：决策锚点与全部候选（含商店、尼奥选项及代价）、`gold_gained`、楼层快照、来源标记；每局另写摘要 | 慢；记录 schema 只增不改 | 宽，标注来源 | `~/.spire-ledger/runs/<run_id>/` |
| `stats` | 读本 Mod 记录与原版 `.run`（旧局的 RunHistoryPlus 字段与 Relic Stats 的 `relic_stats` 可选读取），按 play_id 合并成统一的单局模型，每个字段标明精确、估算或缺失；指标、幕划分、排名算法都是纯函数 | 中 | 纯 Java，不依赖游戏类，离线可测 | 只派生；缓存按文件修改时间失效 |
| `views` | 游戏内统计、历史、复盘界面；CSV 与 Tierlist 图片导出 | 中 | 游戏 UI | 导出目录 |
| `replay` | 回合快照采集与恢复，内嵌改造后的 SaveState | 慢 | 严格；兼容键 = 保存实现标识 + 快照 schema | 回合附件 |

### 接口与不变量

- 记录 schema 是 `record` 与 `stats`、离线 `review.html`、SlaySpire1RL 导入工具之间的唯一接口。`stats` 不引用 `record` 的内部类。
- 只有 `record` 写局内事实；派生数字不落盘（导出文件除外）。
- 打开统计或复盘不改变局内状态、不推进 RNG。
- 读取方不能阻断写入：Windows 上读句柄会让原子替换失败，写入必须对此有界重试，失败不得停掉整局记录。
- 已审核的同装 Mod：BaseMod、StSLib、求解器 `sts1solver`。求解器的执行动作经 `withAutomation` 标为 `known_automation`；求解器加载时 `replay` 关闭。其他 Mod 一律记 `unknown`。

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
- 统计真值夹具：305 局能从分数明细取精确累计金币的真实 `.run`；与 TinyCat 4.5.6 同批逐表对数时，每处差异必须归入已登记缺陷或口径决定，未归类即失败。

## Route

各里程碑均为 (proposed)。

- **M0 迁入与修复**：State Notes 代码迁入并改名（modid、包名、数据目录、schema 名、jar 名）；修写入锁冲突；重打兼容键与 jar 版本解绑。验收：`-Check` 全过；锁冲突测试转绿；一次 replay 探针通过。不可逆点：无（未公开发布）。
- **M1 记录补字段与摘要**：先定 `docs/metrics.md` 指标合同（M2、M3 全部指标的统计单位、分母、缺失规则、来源优先级），再据此出字段缺口表；补 `gold_gained`、尼奥选项与代价、商店候选、第三幕通过事实，以及 TinyCat 指标清单中 RunHistoryPlus 独有且需要的字段；每局摘要。同时修三处现有缺陷：缺少 `run.json` 的记录目录在历史里不可见（`HistoryIndex.java:70`）；诊断报告的异常消息和堆栈里的令牌没有去除（`DiagnosticReport.java:47-48`）；统计筛选没有排除未完成局（`StatsIndex.java:70`）。验收：记录检查；三处缺陷各有一个反例夹具转绿；千局夹具上的冷启动、筛选和记录停顿实测值交给用户定上限。
- **M2 统计内核**：统一单局模型；State Notes 现有指标与 TinyCat 核心指标（卡牌抓率、遭遇战损、金币）迁入。验收：真值金币夹具；与 TinyCat 逐表对数；结果筛选（全部、仅胜、仅败）× 放弃开关 × 两种胜负口径的全部组合各有离线断言，夹具含心脏胜、第三幕胜、第四幕死亡、放弃和缺少胜负事实的旧局；多来源合并在打乱读取顺序后结果不变。
- **M3 其余指标与导出**：Plackett-Luce、Jenks、怪物危险度、尼奥；遗物效果量（读 `relic_stats`，遗物语义对照表覆盖本机样本出现的全部遗物，未收录的遗物显示为缺失而非 0）；CSV 与 Tierlist 图片；求解器同装来源标记（含求解器仓库一侧的 `withAutomation` 调用）。
- **M4 CI 与发布**：自托管 runner 工作流；发布由用户逐次授权。不可逆点：modid `spire-ledger` 与记录 schema 公开后只能兼容扩展。

State Notes 仓库在 M0 完成后冻结为历史，不再发布。

## Open decisions

- 已决定（2026-10-25）：State Notes 旧记录留在 `~/.sts1-state-notes/` 原地只读，新 Mod 同时读旧 schema `sts1-state-v1`；跨项目一侧，SlaySpire1RL `tools/state_runs/import_run.py` 同时接受两个 schema 名（SlaySpire1RL 3d1002a）。每局摘要是统计索引的内部机制，见 M1。
- 是否保留 State Notes 的 Git 历史迁入（历史中含内部证据路径与记录，影响日后公开）。blocks: M0。
