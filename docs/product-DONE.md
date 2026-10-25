# Spire Ledger product spec: resolved items

从 [product.md](product.md) 移出的已结案条目，原文保留。

## 2026-10-25

- Open question：“State Notes 现有本机记录和 SlaySpire1RL `tools/state_runs/import_run.py` 的迁移方式。blocks: 改名落地。”
  结案：旧记录原地只读、新 Mod 兼容读取旧 schema；`import_run.py` 同时接受两个 schema 名（用户决定，M0 落地，提交 b0274ce、SlaySpire1RL 3d1002a）。条款已并入 product.md“记录”一节。

## 2026-10-25 Codex 对抗审阅修订前的原文

以下条款被 [decisions/2026-10-25-review.md](decisions/2026-10-25-review.md) 的“规格审阅”一节改写或移出，原文保留：

- 记录：“每次选择确认前记录完整牌组（逐实例）、资源、遗物和全部候选，包括战后选牌、商店候选和尼奥的全部选项及代价；跳过、歌唱碗和普通关闭分别记录。（商店候选与尼奥选项为新增，proposed）”
- 记录：“资源快照包含累计获得金币；每层开始时的 HP 来自楼层快照，不靠估算。”
- 记录：“记录写在本机 `~/.spire-ledger/runs/<run_id>/`，每局另有一个小型摘要供统计索引使用。异常退出后已封口的分段仍可读取。”（摘要属于内部机制，移入 architecture.md）
- 记录：“……SlaySpire1RL `import_run.py` 同时接受两个 schema 名。（用户决定，2026-10-25）”（跨项目交付，移入 architecture.md）
- 统计：“结果筛选取值为全部、仅胜、仅败。“胜”默认指击杀心脏，可切换为第三幕通关即算胜；放弃局是否计入由单独开关决定，计入时归入“败”；未完成局不计入。按胜负筛选时胜率类指标显示为“—”，其余指标照常计算。（proposed，2026-10-25 协作者提出）”
- 统计：“数据来自本 Mod 的记录和原版 `.run`。……两种来源按 play_id 关联。”
- 统计：“遗物统计：`.run` 里有 Relic Stats 写入的 `relic_stats` 时照读，按遗物汇总每局、每场战斗、每回合的效果量（如燃烧之血回血、金神像金币），并与持有该遗物的胜率并列显示；运行时不依赖 Relic Stats，也不重写它的局内计数。各遗物裸值的含义按 Relic Stats（MIT）源码整理成对照表，NOTICE 署名。（proposed，2026-10-25 用户提出）”
- 统计：“每个比率同时显示分子和分母；缺失字段不当作 0，显示字段覆盖率；估算值明确标注。”
- 统计：“排名（Tierlist、标准化抓率）只描述玩家自己的选择行为，不给推荐理由，也不判断卡牌强弱。（proposed）”
- 复盘：“……附件兼容按回合适配器标识、内嵌 SaveState 标识与快照 schema 判断，与 jar 版本无关，统计或界面更新不使已录附件失效。（用户决定，2026-10-25；M0 已实现）”
- 错误报告：“沿用 State Notes：快捷键或设置页生成本地 JSON 错误报告，去除账号、令牌和本地路径，不上传。”
