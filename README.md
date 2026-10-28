# Spire Ledger（尖塔账本）

《杀戮尖塔 1》的本地记录与复盘 Mod。打开账本就能看当前局或当前存档位的最近一局，悬停楼层查看选择和资源变化，也可以查看个人统计与标准化率、导出统计长图，或重打已保存的回合。数据保存在本机，不上传。

当前发布版：**[v0.5.0-beta.3](https://github.com/Yongzhaooo/spire-ledger/releases/tag/v0.5.0-beta.3)**（内测版，2026-10-29）。`main` 与 beta.3 内容一致，新增的标准化率、四类导出长图和总览区间仍在等待手动回归；测试项已登记为 [issue](https://github.com/Yongzhaooo/spire-ledger/issues)。

## 安装与升级

1. 从 [Releases](https://github.com/Yongzhaooo/spire-ledger/releases) 下载 `SpireLedger.jar`。
2. 退出游戏和 ModTheSpire，把文件放到游戏目录的 `mods/`，替换旧版同名文件。已有账本记录会保留。
3. 在 ModTheSpire 勾选 BaseMod、StSLib 和 Spire Ledger，再启动游戏。
4. 在 Mod 设置中确认“本地尖塔账本记录”（English: `Local Spire Ledger recording`）已开启。需要重打时，再开启“回合快照记录（练习/重打）”（English: `Turn checkpoint recording (practice/replay)`），进入新战斗生成快照。

基础环境：游戏 `12-18-2022`、ModTheSpire `3.30.3`、BaseMod `5.56.0`、StSLib `2.12.0`。

不要同时启用旧版 STS1 State Notes 或单独的 SaveStateMod。账本已内嵌所需组件，重复加载会导致启动失败。

## 怎么用

从主菜单的“尖塔账本”或局内顶栏图标打开。顶部可以直接切换“最近一局”“历史对局”“个人统计”，关闭账本就回到游戏。

- **看一局**：路线按幕排列。鼠标悬停楼层看详情，点击固定；下方查看遗物和牌组，滚轮翻看卡牌。没有可重打快照时，页面会区分“当时 Mod 组合不支持”“快照在出牌后才观察到”和“本层没有快照附件”。
- **看统计**：选择职业、进阶和日期。放弃局、操作来源、最少样本量等条件放在“更多筛选”中。统计按当前存档位分开。总览显示平均终局牌组、平均得分、商店/事件/每局删牌，并附 95% 区间：胜率用 Wilson 区间，均值用 ±1.96 标准误。
- **导出统计图**：进入“个人统计 → 导出图”，先选内容：卡牌抓率、火堆行动、涅奥选项或 Boss 遗物。卡牌再选幕；卡牌、涅奥和 Boss 遗物可选“一般率 / 标准率”，火堆可选“行动次数 / 标准化倍”。点“导出本幕 PNG”或“导出长图 PNG”后，每类各写一张长图；标准率可用时同时写一般率与标准率两张。导出后可点“打开文件夹”。图片附带筛选条件、样本数和口径，同目录的 `metadata.json` 保留同一份数据。
- **重打回合**：点击战斗楼层，选一个已有快照的回合。当前战斗用“重打此回合”；历史记录用“练习此回合”，会打开独立练习窗口。

一般率等于被选次数除以出现次数。卡牌分档 T0–T6 按 90%、75%、60%、45%、30%、15% 分界；从未选取和样本不足单独显示。标准率采用 TinyCat 的 Plackett-Luce 同屏对手对齐（`standardized-v1`）：每个奖励屏是一个选择集，跳过与歌唱碗作为同屏对手但不单独报告，报告合计 100% 的强度份额。份额描述的是你在强、弱候选之间的相对选择，不是卡牌强度，也不能与一般率相减；无法拟合的幕显示“—”。

火堆标准化倍 = 该条目占比 ÷ 随机基线（100 ÷ 平均候选池）。候选池按 TinyCat 口径，把每张可升级牌各算一个选项，加上可用的抽烟/举重/挖掘/回忆，不含睡觉，也不把锻造整体算作一个选项；战锤和咖啡杯会移除对应选项，第三幕为进第四幕而拿的回忆是固定成本，不计入。涅奥与 Boss 遗物的标准率只使用带完整选项记录的局。

导出图中的“跳过”用空白卡图占位；涅奥使用游戏内选项名称和空白卡图占位；Boss 遗物使用游戏内遗物图片，跳过同样留白。所有图片只在运行时用本机游戏资源渲染，不打包游戏素材。

## 重打与兼容性

在基础环境上，支持以下可选 Mod 的指定版本：

| Mod | 版本 |
|---|---|
| SuperFastMode | 1.0.8，已测 10 倍速 |
| STS1 Combat Solver | 0.9.6 |
| Colored Map | 2.4.1 |
| Colored Powertips | 2.3.0 |
| Highlight Path | 0.0.3 |

八个 Mod 同时启用时，已在原生游戏中验证回合录制、当前战斗重打和独立练习。重打会停止求解器自动执行、清空旧结果，恢复后可以手动操作或重新计算。其他 Mod 或版本仍可记录，但不提供回合恢复。

正常记录和浏览不会主动推进游戏随机数。测试中，快照录制、界面导航和 PNG 导出前后的游戏 RNG 状态一致。只有你主动重打时，才会把战斗和随机数恢复到所选快照。求解器的静态扫描仍可能因为内嵌 SaveState 的恢复代码显示 RNG 警告。

旧局没有保存的回合快照无法补建。独立练习使用隔离环境，不计入正式统计。

## 数据与统计范围

记录保存在 `~/.spire-ledger/runs/`，导出图片在 `~/.spire-ledger/exports/`。Windows 的 `~` 通常是 `C:\Users\<用户名>`。升级时保留整个 `.spire-ledger` 目录即可。

个人统计读取游戏目录 `runs/` 下的原版 `.run` 和账本记录，按对局关联，避免重复计算。原版 `.run` 先按严格 UTF-8 解码，失败时回退 GBK（中文 Windows 的默认编码与 TinyCat 一致），并去掉 UTF-8 BOM；两种编码的旧局都能读取。逐层复盘页目前只展示账本记录；仅有原版 `.run` 的旧局可参与统计，但没有账本的完整决策详情与回合快照。缺少存档位信息的旧账本记录单独放在“旧记录：存档未知”。

“操作来源未核实”表示无法确认动作来自人还是自动化，不表示幕数或奖励来源丢失。原版 `.run` 没有这项信息；与求解器同装也不等于已识别每个自动化动作。未知来源默认可参与统计，已知自动化选择会排除。

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
- 标准率与一般率并排检查：份额合计 100%、无法拟合显示“—”、跳过/歌唱碗只出现在一般率。
- 总览五个新均值与 95% 区间在中文/英文、1024×768 与 1920×1080 下不溢出。
- 新加入的四个可选 Mod 组合下，回合录制、重打与快照提示。
- GBK 编码的旧 `.run` 与 UTF-8 BOM 文件都能读取，中文事件名不丢失。

原生探针由程序驱动，尚未完成整局手玩回归和实体手柄验证。标准化数字是个人选择行为的描述，不是卡牌强弱结论。

遇到问题时，按 `Ctrl+Shift+R` 生成诊断报告，文件位于 `~/.spire-ledger/bug-reports/`。提交 issue 时附上版本、Mod 列表、复现步骤和截图；也可以先看[已关闭问题](https://github.com/Yongzhaooo/spire-ledger/issues?q=is%3Aissue+state%3Aclosed)。

- [手动测试清单](docs/testing.md)
- [产品定义](docs/product.md)
- [结构与路线](docs/architecture.md)
- [统计口径](docs/metrics.md)

## 从源码构建

需要支持 `javac --release 8` 的 JDK、本机游戏及上述依赖，还需要 SaveStateMod 1.3.0 的 JAR。脚本不会下载或分发游戏本体。

```powershell
./build.ps1 -Game '<游戏目录>' -SaveStateSource '<SaveStateMod-1.3.0.jar 的完整路径>' -Check
```

输出为 `build/SpireLedger.jar`。原生游戏测试入口见 `integration-stats-ui.ps1`、`integration-stats-options.ps1` 和 `integration-replay.ps1`；真实对局布局测试依赖维护者本地记录。总览与 Boss 标准率另有独立 Python 校验：

```powershell
javac -encoding UTF-8 --release 8 -cp "build\gson;build\stats-classes" -d build\dump test\StatsDump.java
java -cp "build\gson;build\stats-classes;build\dump" spireledger.stats.StatsDump "<游戏目录>\runs" 0 build\dump-out
python test/check_overview.py --runs "<游戏目录>\runs" --slot 0 --java build/dump-out/overview.tsv --ids build/dump-out/overview-ids.tsv
python test/check_boss_standardized.py --runs "<游戏目录>\runs" --slot 0 --java build/dump-out/boss-shares.tsv
```

仓库不附带游戏本体、SaveStateMod 或构建好的 jar；协作者测试时使用维护者提供的构建，或按上面的命令自行构建。

许可：自有代码 GPL-3.0（见 [LICENSE](LICENSE)）；内嵌第三方组件见 [NOTICE.txt](NOTICE.txt)。
