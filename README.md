# Spire Ledger（尖塔账本）

《杀戮尖塔 1》的本地记录与复盘 Mod。打开账本就能看当前局或当前存档位的最近一局，悬停楼层查看选择和资源变化，也可以查看个人统计、导出抓率图，或重打已保存的回合。数据保存在本机，不上传。

当前版本：**[v0.5.0-beta.2](https://github.com/Yongzhaooo/spire-ledger/releases/tag/v0.5.0-beta.2)**（内测版，2026-10-27）。

## 安装与升级

1. 从 [Releases](https://github.com/Yongzhaooo/spire-ledger/releases) 下载 `SpireLedger.jar`。
2. 退出游戏和 ModTheSpire，把文件放到游戏目录的 `mods/`，替换旧版同名文件。已有账本记录会保留。
3. 在 ModTheSpire 勾选 BaseMod、StSLib 和 Spire Ledger，再启动游戏。
4. 在 Mod 设置中确认 `Local Spire Ledger recording` 已开启。需要重打时，再开启 `Turn checkpoint recording (practice/replay)`，进入新战斗生成快照。

基础环境：游戏 `12-18-2022`、ModTheSpire `3.30.3`、BaseMod `5.56.0`、StSLib `2.12.0`。

不要同时启用旧版 STS1 State Notes 或单独的 SaveStateMod。账本已内嵌所需组件，重复加载会导致启动失败。

## 怎么用

从主菜单的“尖塔账本”或局内顶栏图标打开。顶部可以直接切换“最近一局”“历史对局”“个人统计”，关闭账本就回到游戏。

- **看一局**：路线按幕排列。鼠标悬停楼层看详情，点击固定；下方查看遗物和牌组，滚轮翻看卡牌。
- **看统计**：选择职业、进阶和日期。放弃局、操作来源、最少样本量等条件放在“更多筛选”中。统计按当前存档位分开。
- **导出抓率图**：进入“个人统计 → 抓率图”，选幕后点击“导出本幕 PNG”。导出后可点“打开文件夹”。图片附带筛选条件、样本数和口径，并保留同份数据的 JSON。
- **重打回合**：点击战斗楼层，选一个已有快照的回合。当前战斗用“重打此回合”；历史记录用“练习此回合”，会打开独立练习窗口。

抓率等于被选次数除以出现次数。T0 到 T6 按 90%、75%、60%、45%、30%、15% 分界；从未选取和样本不足单独显示。这是个人选择习惯，不是卡牌强度排名，也不是标准化抓率模型。

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

个人统计读取游戏目录 `runs/` 下的原版 `.run` 和账本记录，按对局关联，避免重复计算。逐层复盘页目前只展示账本记录；仅有原版 `.run` 的旧局可参与统计，但没有账本的完整决策详情与回合快照。缺少存档位信息的旧账本记录单独放在“旧记录：存档未知”。

“操作来源未核实”表示无法确认动作来自人还是自动化，不表示幕数或奖励来源丢失。原版 `.run` 没有这项信息；与求解器同装也不等于已识别每个自动化动作。未知来源默认可参与统计，已知自动化选择会排除。

## 这次更新与验证

`beta.2` 增加了最近一局直达、按幕悬停复盘和实际抓率 PNG 导出，修复了结束后统计延迟、主菜单放弃、历史跨存档位和中断标签，并调整数值排序、年月区间及操作来源提示。

已通过离线断言、1024×768 和 1920×1080 原生界面测试，以及求解器配合 SuperFastMode 十倍速的重打测试。原生探针由程序驱动，尚未完成整局手玩回归和实体手柄验证。标准化抓率、其他 Tierlist 和 CSV 导出仍未实现。

遇到问题时，按 `Ctrl+Shift+R` 生成诊断报告，文件位于 `~/.spire-ledger/bug-reports/`。提交 issue 时附上版本、Mod 列表、复现步骤和截图。

- [手动测试清单](docs/testing.md)
- [产品定义](docs/product.md)
- [结构与路线](docs/architecture.md)
- [统计口径](docs/metrics.md)

## 从源码构建

需要支持 `javac --release 8` 的 JDK、本机游戏及上述依赖，还需要 SaveStateMod 1.3.0 的 JAR。脚本不会下载或分发游戏本体。

```powershell
./build.ps1 -Game '<游戏目录>' -SaveStateSource '<SaveStateMod-1.3.0.jar 的完整路径>' -Check
```

输出为 `build/SpireLedger.jar`。原生游戏测试入口见 `integration-stats-ui.ps1` 和 `integration-replay.ps1`；真实对局布局测试依赖维护者本地记录。

许可：自有代码 GPL-3.0（见 [LICENSE](LICENSE)）；内嵌第三方组件见 [NOTICE.txt](NOTICE.txt)。
