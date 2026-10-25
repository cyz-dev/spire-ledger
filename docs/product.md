# Spire Ledger product spec

Last confirmed: not yet confirmed
Architecture: [architecture.md](architecture.md)

## Purpose

Spire Ledger（尖塔账本）是《杀戮尖塔 1》的本地记录、统计与复盘 Mod，由用户维护和发布，GPL 开源。它把每局的事实如实记下来，汇总成玩家自己的统计和 Tierlist，并支持按层复盘和回合重打。它独立、离线，不依赖网络、求解器或训练环境，也能和用户的求解器 Mod 同装使用。

它接替 State Notes（`sts1/sts1-state-notes`，0.4.0 未公开发布）：State Notes 的记录、复盘与重打能力迁入本项目并改名。TinyCat（MI，MPL-2.0）只作为统计口径与领域知识的参考，不并入代码。

## Expectations

### 安装与运行

- 玩家放入一个 `SpireLedger.jar`，在 ModTheSpire 中勾选 BaseMod、StSLib 和 Spire Ledger 即可使用。
- 支持与求解器 Mod（`sts1solver`）同装：记录和统计照常工作，求解器执行的动作标为 `known_automation`，人的操作仍为 `human`；求解器加载时回合重打保持关闭。（用户决定，2026-10-25）
- 加载其他未审核的 Mod 时照常记录，操作来源记为 `unknown`，回合重打拒绝启用。
- 同时加载原装 SaveStateMod 时拒绝启动并提示移除。

### 记录

- 每次选择确认前记录完整牌组（逐实例）、资源、遗物和全部候选，包括战后选牌、商店候选和尼奥的全部选项及代价；跳过、歌唱碗和普通关闭分别记录。（商店候选与尼奥选项为新增，proposed）
- 资源快照包含累计获得金币；每层开始时的 HP 来自楼层快照，不靠估算。
- 构筑变化以前后锚点表示，不猜原因。SL 记为新分支并关联父检查点。
- 记录写在本机 `~/.spire-ledger/runs/<run_id>/`，每局另有一个小型摘要供统计索引使用。异常退出后已封口的分段仍可读取。

### 统计

- 覆盖全部进阶，进阶作为筛选项；另可按职业、日期、结果、是否计入放弃、种子局、最小样本量筛选。（用户决定，2026-10-25）
- 数据来自本 Mod 的记录和原版 `.run`。旧 `.run` 里有 RunHistoryPlus 写入的字段时照读，但运行时不依赖 RunHistoryPlus。两种来源按 play_id 关联。
- 每个比率同时显示分子和分母；缺失字段不当作 0，显示字段覆盖率；估算值明确标注。
- 排名（Tierlist、标准化抓率）只描述玩家自己的选择行为，不给推荐理由，也不判断卡牌强弱。（proposed）
- 打开或关闭统计界面不改变局内状态，也不推进 RNG。

### 导出

- CSV 表格与 Tierlist 图片。（用户决定，2026-10-25）
- 图片只使用玩家本机游戏资源在运行时渲染，不打包游戏素材或第三方图片。

### 复盘与回合重打

沿用 State Notes 0.4.0 的行为：单局按楼层复盘；开启回合快照后可在当前战斗回退到所选回合，或从历史附件启动隔离的练习实例。恢复失败回滚，回滚失败冻结。附件兼容按保存实现与快照 schema 判断，统计或界面更新不应使已录附件失效。（兼容规则调整为 proposed）

### 错误报告

沿用 State Notes：快捷键或设置页生成本地 JSON 错误报告，去除账号、令牌和本地路径，不上传。

### 发布与许可

- 由用户发布；仓库 GitHub 私有（2026-10-25），公开时间由用户决定。
- 自有代码 GPL-3.0。内嵌的改造 SaveState 仍在主 jar 内，它的上游没有许可证，不在 GPL 授权范围内；NOTICE 写明署名、来源和修改摘要，上游 issue #13 回复后更新。（用户决定，2026-10-25）

## Non-goals

- 卡牌推荐、强弱评分或推荐理由。
- 上传、遥测或云同步。
- 产出训练数据或修改任何策略；记录能否进入训练由 RL 项目单独决定。
- xlsx 导出。
- 支持自定义角色、卡牌或怪物的保证。

## Open questions

- 发布渠道（Steam Workshop、GitHub Release 或两者）与公开源码时间。blocks: 发布。
- State Notes 现有本机记录和 SlaySpire1RL `tools/state_runs/import_run.py` 的迁移方式。blocks: 改名落地。
