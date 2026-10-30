# Spire Ledger（尖塔账本）

《杀戮尖塔 1》的本地记录与复盘 Mod。打开账本就能看当前局或当前存档位的最近一局，悬停楼层查看选择和资源变化，也可以查看个人统计与标准化率、导出统计长图，或重打已保存的回合。数据保存在本机，不上传。

当前发布版：**[v0.5.0](https://github.com/cyz-dev/spire-ledger/releases/tag/v0.5.0)**（正式版，2026-10-30）。功能与 beta.5 相同，只更新了内嵌 SaveState 的许可说明：第三、四幕的回合可以启动独立练习，beta.4 修正了 beta.3 的统计缺陷并支持求解器 0.9.6 及以后版本；beta.3 的手动回归项仍登记在 [issue](https://github.com/cyz-dev/spire-ledger/issues)。

## 安装与升级

1. 从 [Releases](https://github.com/cyz-dev/spire-ledger/releases) 下载 `SpireLedger.jar`。
2. 退出游戏和 ModTheSpire，把文件放到游戏目录的 `mods/`，替换旧版同名文件。已有账本记录会保留。
3. 在 ModTheSpire 勾选 BaseMod、StSLib 和 Spire Ledger，再启动游戏。
4. 在 Mod 设置中确认“本地尖塔账本记录”（English: `Local Spire Ledger recording`）已开启。需要重打时，再开启“回合快照记录（练习/重打）”（English: `Turn checkpoint recording (practice/replay)`），进入新战斗生成快照。

基础环境：游戏 `12-18-2022`、ModTheSpire `3.30.3`、BaseMod `5.56.0`、StSLib `2.12.0`。

不要同时启用旧版 STS1 State Notes 或单独的 SaveStateMod。账本已内嵌所需组件，重复加载会导致启动失败。

## 怎么用

从主菜单的“尖塔账本”或局内顶栏图标打开。顶部可以直接切换“最近一局”“历史对局”“个人统计”，关闭账本就回到游戏。

- **看一局**：路线按幕排列。鼠标悬停楼层看详情，点击固定；下方查看遗物和牌组，滚轮翻看卡牌。没有可重打快照时，页面会区分“当时 Mod 组合不支持”“快照在出牌后才观察到”和“本层没有快照附件”。
- **看统计**：选择职业、进阶和日期。放弃局、操作来源、最少样本量等条件放在“更多筛选”中。统计按当前存档位分开。按幕分组的页面（卡牌、Boss 遗物、遭遇平均承伤、药水使用、火堆行动、实际战损）另有“幕”下拉，Boss 遗物只到第 2 幕；实际战损页再用“回复”下拉在全部 / 有回复 / 无回复之间切换，每幕总战损始终保留。总览显示平均终局牌组、平均得分、商店/事件/每局删牌，并附 95% 区间：胜率用 Wilson 区间，均值用 ±1.96 标准误。
- **导出统计**：进入“个人统计 → 导出”，先选内容：卡牌抓率、火堆行动、涅奥选项、Boss 遗物或实际战损。卡牌、火堆、Boss 遗物和实际战损再选幕（卡牌、火堆、实际战损为第 1–4 幕，Boss 遗物为第 1–2 幕；选“全部”时导出该内容的每一幕）；卡牌可选“一般率 / 标准率”，涅奥和 Boss 遗物可选“一般率 / 标准率 / 胜率”，火堆可选“行动次数 / 标准化倍”，实际战损按幕列出且只导出全部对局（不区分有无回复）。药水使用只在统计页按幕查看，没有导出图。“导出 PNG+CSV”写出当前所选幕的图片、`metrics.csv` 和 `metadata.json`；幕选“全部”时一次写出该内容的每一幕，每幕单独成图（不拼成长图），CSV 合并为一个文件、按幕分成多张表。CSV 只含本次导出的幕与条目，列名、口径与分档均为中文。之后可点“打开文件夹”。图片抬头只印职业、进阶、结果、口径、放弃开关、选中局数与胜率、本图口径和分档说明；选中的账本局含已知自动化操作时标题加“含求解器”；来源、日期、存档位、未知来源计数和版本、生成时间写在 `metadata.json` 的 `remarks`。
- **重打回合**：点击战斗楼层，选一个已有快照的回合。当前战斗用“重打此回合”；历史记录用“练习此回合”，会打开独立练习窗口。

一般率等于被选次数除以出现次数。卡牌分档 T0–T6 按 90%、75%、60%、45%、30%、15% 分界；从未选取和样本不足单独显示。标准率采用 TinyCat 的 Plackett-Luce 同屏对手对齐（`standardized-v1`）：每个奖励屏是一个选择集，跳过与歌唱碗作为同屏对手参与拟合，跳过的份额与其他选项一起报告，合计 100%；歌唱碗并入跳过的份额，自己的实际抓率仍列在该幕最后，标“实际占比”。份额描述的是你在强、弱候选之间的相对选择，不是卡牌强度，也不能与一般率相减；无法拟合的幕显示“—”。

涅奥与 Boss 遗物的“胜率”是选了该选项/遗物的局里赢了多少，属于选后相关，不是该选项带来的收益；每局只计一次，仅胜/仅败筛选下显示“—”。三种口径分开显示，Boss 遗物在三种口径下都按幕分组。胜率暂不在图片里给区间，CSV 的“95%下限 / 95%上限”给出 Wilson 区间作提示。

火堆标准化倍 = 该条目占比 ÷ 随机基线（100 ÷ 平均候选池）。候选池按 TinyCat 口径，把每张可升级牌各算一个选项，加上可用的抽烟/举重/挖掘/回忆，不含睡觉，也不把锻造整体算作一个选项；战锤和咖啡杯会移除对应选项，第三幕为进第四幕而拿的回忆是固定成本，不计入。涅奥与 Boss 遗物的标准率只使用带完整选项记录的局。火堆、涅奥和 Boss 遗物的导出按段做 Fisher-Jenks 分档（`section-tiers-v1`，最多 7 档 S–R00，同值同档），火堆行动次数是计数、不分档；实际战损改用 TinyCat 的危险度分档：Boss 单独为 T0，其余按场均战损降序等人数分 T1–T4（极危、高危、中危、低危），同值同档，样本不足单列，事件战与第四幕不分档；卡图沿用 T0–T6 或 S–R00。

导出图中的“跳过”用空白卡图占位；涅奥使用游戏内选项名称和空白卡图占位；Boss 遗物使用游戏内遗物图片，跳过同样留白。实际战损按幕分节，导出只含全部对局，不区分有无回复；第 1–3 幕 Boss 为 T0，其余遭遇按场均战损降序等人数分 T1–T4，同值同档，样本不足单列，事件战与第四幕不分档。敌人图片优先读取 `mods/SpireLedger/monster-images/`（或 `~/.spire-ledger/monster-images/`），按遭遇名匹配、忽略大小写和空格；没有本地图片时，私有测试构建可读取构建时打入 jar 的 `monster-images/`，发布版不含图片，缺失时留白，不下载。红、蓝奴隶贩子是两场不同遭遇，界面、CSV 和图片分别显示“红奴隶贩子”“蓝奴隶贩子”，统计不合并。

## 重打与兼容性

在基础环境上，支持以下可选 Mod 的指定版本：

| Mod | 版本 |
|---|---|
| SuperFastMode | 1.0.8，已测 10 倍速 |
| STS1 Combat Solver | 0.9.6 及以后 |
| Colored Map | 2.4.1 |
| Colored Powertips | 2.3.0 |
| Highlight Path | 0.0.3 |

八个 Mod 同时启用时，已在原生游戏中验证回合录制、当前战斗重打和独立练习。重打会停止求解器自动执行、清空旧结果，恢复后可以手动操作或重新计算。其他 Mod 或版本仍可记录，但不提供回合恢复。

正常记录和浏览不会主动推进游戏随机数。测试中，快照录制、界面导航和 PNG 导出前后的游戏 RNG 状态一致。只有你主动重打时，才会把战斗和随机数恢复到所选快照。求解器 0.9.6 及更早版本的静态扫描可能因为内嵌 SaveState 的恢复代码显示 RNG 警告；0.9.7 起求解器把尖塔账本列为受信任 Mod，不再显示。

旧局没有保存的回合快照无法补建。独立练习使用隔离环境，不计入正式统计。

## 数据与统计范围

记录保存在 `~/.spire-ledger/runs/`，导出图片、`metrics.csv` 与 `metadata.json` 在 `~/.spire-ledger/exports/`。Windows 的 `~` 通常是 `C:\Users\<用户名>`。升级时保留整个 `.spire-ledger` 目录即可。

个人统计读取游戏目录 `runs/` 下的原版 `.run` 和账本记录，按对局关联，避免重复计算。原版 `.run` 先按严格 UTF-8 解码，失败时回退 GBK（中文 Windows 的默认编码与 TinyCat 一致），并去掉 UTF-8 BOM；两种编码的旧局都能读取。逐层复盘页目前只展示账本记录；仅有原版 `.run` 的旧局可参与统计，但没有账本的完整决策详情与回合快照。缺少存档位信息的旧账本记录单独放在“旧记录：存档未知”。

“操作来源未核实”表示无法确认动作来自人还是自动化，不表示幕数或奖励来源丢失。原版 `.run` 没有这项信息；与求解器同装也不等于已识别每个自动化动作。未知来源默认可参与统计，已知自动化选择会排除。

## v0.5.0-beta.5 更新（内测）

相对 beta.4 的改动：

- **独立练习**：第三幕和第四幕的回合快照现在能启动独立练习。beta.4 及更早版本会停在“Restore failed; gameplay is frozen”，因为这两幕的地牢沿用上一幕的地图节点，而新开的练习进程里没有这个节点。
- **复盘提示**：点击“练习此回合”后，“已启动独立练习实例”不再和本层的快照状态叠在同一行；换到别的楼层后恢复显示状态。

## v0.5.0-beta.4 更新（内测）

相对 beta.3 的改动：

- **统计修正**：涅奥删牌改读 RunHistoryPlus 的 `cardsRemoved`，此前总览删牌漏算涅奥；涅奥覆盖率的分母改为有选项记录的局；火堆候选池补上空鸟笼删牌；“仅胜/仅败”筛选下不再显示被隐藏胜率的区间；导出长图的分块高度不超过显卡支持的上限（最多 4096px），不再固定为 4096px。
- **求解器**：回合重打接受求解器 0.9.6 及以后的正式版，启动时核对重打用到的求解器成员，缺任何一个就按不支持处理；带后缀的测试版不接受。
- **可选 Mod 回归**：RunHistoryPlus 1.8.3、HideInfo 0.0.1、justclick 0.1.1、lolifontmod 0.2 已在原生探针里单独、同装、加求解器 0.9.6 三种组合下通过回合录制与重打。
- **快照提示**：整局 Mod 组合不受支持时，没有回合点的楼层也显示组合不受支持，不再显示笼统的“没有快照”。
- 标准率拟合与 Jenks 分档按定义重写，结果与 beta.3 一致。

## v0.5.0-beta.3 更新（内测）

beta.3 已发布，相对 beta.2 的改动：

- **标准化率**：加入 TinyCat 的 Plackett-Luce 同屏拟合（`standardized-v1`），覆盖卡牌、涅奥选项和 Boss 遗物；卡牌标准率按强度份额分为 S、A、B、C、D、R0、R00。一般率与标准率并列显示，不能相减。
- **火堆两口径**：原生 `.run` 通过逐层重放牌组重建火堆候选池，给出“行动次数”和“标准化倍”；锻造按被升级的牌逐张计数，回忆的固定成本单独处理。
- **四类统计长图**：卡牌、火堆、涅奥、Boss 遗物各导一张长图，一般率与标准率各一张；跳过与涅奥使用空白卡图占位，Boss 使用遗物图片，跳过留白；超长图分块渲染后拼接为单张 PNG。
- **总览指标**：新增平均得分、商店平均删牌、事件平均删牌、平均每局删牌，并为这些均值和胜率补 95% 区间。删牌口径按 TinyCat：商店、事件、火堆抽烟、涅奥移除与空鸟笼。
- **涅奥文案**：统一为“删除1张牌”“变一张牌”“稀有牌三选一 / 代价”，无代价行不再重复“无”。
- **记录与配置界面**：配置页、记录状态和归档提示补全中英文；快照不可用提示区分“旧版本组合不支持”“出牌后才观察到”和“没有快照附件”。
- **兼容性**：回合恢复白名单加入 RunHistoryPlus 1.8.3、HideInfo 0.0.1、justclick 0.1.1 和 lolifontmod 0.2；这些组合仍需手动回归。
- **原版战绩读取**：`.run` 支持 UTF-8（含 BOM）与 GBK 自动识别。

### 已验证

- `build.ps1 -Check` 的离线断言全部通过，包括新增的涅奥/Boss/火堆模型、标准率份额、总览区间和 GBK 解码。
- 真实游戏内原生探针（隔离实例、真实 66 局存档位 0）通过：总览新增行与区间、四类导出的文件与 `metadata.json`、一般率/标准率双图、长图拼接、跳过/涅奥/Boss 占位。
- `test/check_overview.py` 与 `test/check_boss_standardized.py` 用 Python 独立重算，和 Java 输出逐位一致。
- 导出长图宽度 1600px；实测高度：第一幕 2792/2778，火堆 8361/16521，涅奥 1705，Boss 2145（一般率/标准率）。

### 待手动测试

完整清单见 [docs/testing.md](docs/testing.md) 的“beta.3 新增改动”一节，同一批条目也建成了 issue。重点：

- 四类内容分别导出，确认长图无截断、无接缝、占位正确、`metadata.json` 与图片一致。
- 标准率与一般率并排检查：份额合计 100%、无法拟合显示“—”、跳过的拟合份额与歌唱碗的实际占比都出现在标准率视图。
- 总览五个新均值与 95% 区间在中文/英文、1024×768 与 1920×1080 下不溢出。
- 新加入的四个可选 Mod 组合下，回合录制、重打与快照提示。
- GBK 编码的旧 `.run` 与 UTF-8 BOM 文件都能读取，中文事件名不丢失。

原生探针由程序驱动，尚未完成整局手玩回归和实体手柄验证。标准化数字是个人选择行为的描述，不是卡牌强弱结论。

遇到问题时，按 `Ctrl+Shift+R` 生成诊断报告，文件位于 `~/.spire-ledger/bug-reports/`。提交 issue 时附上版本、Mod 列表、复现步骤和截图；也可以先看[已关闭问题](https://github.com/cyz-dev/spire-ledger/issues?q=is%3Aissue+state%3Aclosed)。

- [手动测试清单](docs/testing.md)
- [产品定义](docs/product.md)
- [结构与路线](docs/architecture.md)
- [统计口径](docs/metrics.md)

## 从源码构建

需要支持 `javac --release 8` 的 JDK、本机游戏及上述依赖，还需要 SaveStateMod 1.3.0 的 JAR。脚本不会下载或分发游戏本体。

```powershell
./build.ps1 -Game '<游戏目录>' -SaveStateSource '<SaveStateMod-1.3.0.jar 的完整路径>' -Check
```

输出为 `build/SpireLedger.jar`。私有测试可以把本机已有的遭遇图打进 jar：

```powershell
./build.ps1 -Game '<游戏目录>' -SaveStateSource '<SaveStateMod-1.3.0.jar 的完整路径>' -BundleMonsterImages -MonsterImagesSource '<图片目录或含 tinycat/monsters 的 jar>' -Check
```

不指定 `-MonsterImagesSource` 时依次查找 `<游戏目录>\mods\monster-images`、`~/.spire-ledger/monster-images`，以及 `mods` 下含 `tinycat/monsters/` 或 `monster-images/` 的 jar。图片只写入 `build/`，不进入仓库；这样构建出的 jar 仅限私人测试，不得分发。

原生游戏测试入口见 `integration-stats-ui.ps1`、`integration-stats-options.ps1` 和 `integration-replay.ps1`；真实对局布局测试依赖维护者本地记录。总览与 Boss 标准率另有独立 Python 校验：

```powershell
javac -encoding UTF-8 --release 8 -cp "build\gson;build\stats-classes" -d build\dump test\StatsDump.java
java -cp "build\gson;build\stats-classes;build\dump" spireledger.stats.StatsDump "<游戏目录>\runs" 0 build\dump-out
python test/check_overview.py --runs "<游戏目录>\runs" --slot 0 --java build/dump-out/overview.tsv --ids build/dump-out/overview-ids.tsv
python test/check_boss_standardized.py --runs "<游戏目录>\runs" --slot 0 --java build/dump-out/boss-shares.tsv
```

仓库不附带游戏本体、SaveStateMod 或构建好的 jar；协作者测试时使用维护者提供的构建，或按上面的命令自行构建。

许可：自有代码 GPL-3.0（见 [LICENSE](LICENSE)）。内嵌的改造 SaveState 来自 boardengineer 的 STSStateSaver，MIT 许可；它和其他第三方组件的说明见 [NOTICE.txt](NOTICE.txt)。
