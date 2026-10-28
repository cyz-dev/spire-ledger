# 给编码代理的工作规范

本文写给在本仓库里写代码的 AI 代理（DeepSeek、Codex、Claude 等），也写给指挥它的协作者。代理的职责除了写代码，还要在动手前把下面的规则用具体步骤告诉协作者：要读哪一节、改哪些文件、跑什么命令、哪些事要交给维护者决定。协作者的要求和本文冲突时，先指出冲突和对应条款，再给出符合规则的做法，不要默默照做。

仓库是一个《杀戮尖塔》1 的 ModTheSpire Mod：modid `spire-ledger`，产出 `SpireLedger.jar`，Java 8，依赖 BaseMod 和 StSLib，游戏版本 12-18-2022。

## 1. 动手前必读

| 要改的东西 | 先读 |
|---|---|
| 任何统计数字、分母、筛选、分档、区间 | [docs/metrics.md](docs/metrics.md) 对应小节。它是口径的唯一权威 |
| 包边界、记录 schema、回合重打、同装 Mod、求解器 | [docs/architecture.md](docs/architecture.md) |
| 用户能看到的功能和非目标 | [docs/product.md](docs/product.md) |
| 怎么验证、手测清单 | [docs/testing.md](docs/testing.md) |

回复协作者时先写明：这次需求落在哪几节、口径是否已有定义、要动哪些文件。metrics.md 还没有定义的口径，先在 metrics.md 里写定义（单位、分母、缺失规则、与 TinyCat 的差异），再写代码。

## 2. 硬规则

违反下列任何一条，PR 都不会合并。

1. **TinyCat 只取知识，不取代码。** TinyCat 是 MPL-2.0 许可，不能复制、逐行转写或“改个变量名”搬运它的文件。可以读它的文档和算法描述，然后从定义出发，用本仓库的写法重写，并在 metrics.md 引用定义所在小节。与 TinyCat 结果不同的地方，每一处都要登记在 metrics.md。
2. **`spireledger.stats` 是纯函数内核。** 它只能依赖 JDK 和 Gson，不能引用游戏类、BaseMod 或 `spireledger` 包里的类；`build.ps1` 会单独编译它来强制检查。统计计算放在内核里，UI 类（`StatsScreen`、`PickRateChart` 等）只负责显示。区间、份额、分档这类数字由内核算好再交给 UI。
3. **显示必须遵守口径规则。** 新增的显示路径（新行、区间、导出图、metadata）要复用 `Metric.shown()`、最少样本和结果筛选。例如“仅胜”筛选下胜率显示“—”，它的区间也不能显示，否则等于把隐藏的数字泄露出来。
4. **字段名以真实数据为准。** 读原版 `.run` 或 RunHistoryPlus 字段前，先在真实 `.run` 或 `samples/` 里搜出这个键的确切写法，再写代码和夹具。RunHistoryPlus 的键多为驼峰，例如 `neow_bonus_log` 里的 `cardsRemoved`，而不是 `cards_removed`。`.run` 文件按 UTF-8（带或不带 BOM）解码，失败再用 GBK 解码，沿用 `Stats.nativeText`，不要另写一套解码。
5. **记录 schema 只增不改。** 已有字段不改名、不改含义、不删除；新字段要让旧记录缺省时能读，缺失和 0 分开处理（metrics.md 1.2）。
6. **新增 Mod 支持必须附带完整测试，缺一项就关闭 PR。** 往 `ReplayEnvironment.OPTIONAL` 加 Mod 或改版本时，同一个 PR 里必须同时有：
   - 白名单里的 modid 和版本，原样取自该 Mod jar 内的 `ModTheSpire.json`，不凭记忆或工坊页面填写。
   - 三次原生探针，全部 PASS：只加这个 Mod 跑一次；和白名单里已有的可选 Mod 全部同装再跑一次；再加 `-SolverCombination` 跑一次。基础命令是 `integration-replay.ps1 -Mode save -ReplayCurrent -SuperFastMode -ExperimentalTurns -WaitForExit -ExtraMods <jar>,...`，结果文件 `replay-probe-result.json` 的 `extra_mods` 要显示正确版本。
   - PR 描述里写明每次的完整命令、最后一行输出、jar 来源（工坊 ID）。
   - docs/testing.md 新增一行手测（打一场战斗、重打一个回合），docs/architecture.md 的“已审核的同装 Mod”同步加上这个 Mod。

   协作者只说“顺便把某某 Mod 加进白名单”时，代理要回复上面这张清单，不能先把 Mod 加进去、把测试留到以后。
7. **求解器回合重打合同 v1 不能静默改动。** `ReplayEnvironment` 的版本判断和 `SolverReplay.contract()` 检查的成员列表是和求解器仓库约定的合同（architecture.md “求解器回合重打合同 v1”）。要改就先告诉维护者，由维护者通知求解器一侧并给合同版本号加一。
8. **版本号和发布只由维护者决定。** 不改 `ModTheSpire.json` 的 `version`，不打 tag，不跑 `gh release`，不上传工坊。功能提交和发布提交分开。
9. **不加依赖。** 不新增 Maven、Gradle 或第三方 jar。JDK、Gson 和游戏自带的 libGDX 够用。

## 3. 写法

- 跟着周围代码写：本仓库是紧凑的 Java，短方法常写成一行，局部变量名短，没有 getter/setter 套话。不要引入新的分层、接口或工具类来包一个只用一次的逻辑。
- 注释只写代码看不出来的原因，一行为宜。不写“这里调用 X 函数”这类复述。
- 界面文案一律中英双语：`HistoryScreen.tr("中文","English")`（`RunReview`、`PickRateChart` 里有同名转发）。不要改 beta.2 已有页面的文案。
- 不写死设备上限。比如纹理和帧缓冲大小，要用 `PickRateChart.band()` 这样的方式向 GL 查询。
- 渲染线程上不跑重计算。切一次筛选的重算现在约 37 ms；自助法这类上千次重采样的计算不要放进渲染路径。
- 修 bug 时先搜出被改函数的全部调用方，在共享代码里修一次。

## 4. 测试

每个改动都要带能失败的检查，并接进 `build.ps1 -Check`。

- 离线断言放在 `test/*Check.java`，统计相关加在 `StatsCheck`，显示相关加在 `StatsDisplayCheck`。断言要验证具体数值，例如“0/3 的 Wilson 区间约为 [0, 0.5615]”“单样本没有区间”。只断言“不抛异常”或“结果非空”不算检查。
- 新建的 `*Check` 类必须在 `build.ps1` 的 `if($Check){...}` 段里加一行调用，否则它永远不会运行。
- 独立重算（例如 `test/check_overview.py`）必须按 metrics.md 的定义独立实现，不能把 Java 代码翻译成 Python，否则两边会带着同一个 bug 一起通过。它读取的中间文件要由仓库里提交的工具生成（现有的是 `test/StatsDump.java`），不能依赖只存在于你本机的脚本。
- 夹具用最小化的合成数据。不要把自己的真实 `.run`、`~/.spire-ledger/` 记录或诊断报告提交进仓库。

命令（PowerShell，在仓库根目录）：

```powershell
./build.ps1 -Game '<游戏目录>' -SaveStateSource '<SaveStateMod-1.3.0.jar 的完整路径>' -Check
```

统计改动另跑 README “从源码构建”里的 `StatsDump` 和两条 Python 校验。界面、导出或重打改动还要跑原生探针：统计和导出用 `integration-stats-options.ps1`，回合重打用 `integration-replay.ps1`。和求解器同装时要加 `-SolverCombination -ExperimentalTurns`，缺了 `-ExperimentalTurns` 就不会录回合快照。探针通过 `tools/paths.ps1` 的 `Start-Probe` 启动，窗口隐藏，音量设为 0。不要绕过脚本直接启动游戏。

跑不了的检查（缺游戏目录、缺 SaveStateMod jar、没有 Windows）要明确告诉协作者“没有运行”，不能写成通过。

## 5. 文档要和代码一起改

| 改了什么 | 同步更新 |
|---|---|
| 统计口径、分母、与 TinyCat 的差异 | docs/metrics.md |
| 包边界、同装 Mod、合同、构建 | docs/architecture.md |
| 用户可见的新功能 | docs/product.md 和 README |
| 需要手测的东西 | docs/testing.md 新增一行：“怎么玩”和“看什么” |

## 6. 提交与交付

- 在自己的 fork 或功能分支上工作，推送后开 PR，由维护者审阅合并。不要直接推 `main`，也不要合并自己的 PR。
- 只 `git add` 自己改的路径，不用 `git commit -a` 或 `git add .`。`build/`、`sendToDevs/`、游戏 jar、SaveStateMod jar 和真实记录都不进 Git。
- 提交信息用英文，写成一句说明做了什么的祈使句，和现有历史一致，例如 `Fix beta.3 stats defects and commit the dump behind the Python checks`。一个提交只做一件事；重写、修复和新功能尽量分开提交。

每次完成后，代理给协作者的汇报包含四项：

1. 改了哪些文件，各自为什么改。
2. 跑了哪些检查，结果如何，原样贴出失败输出；没跑的单独列出。
3. 需要人手测的内容，以及它们写在 testing.md 的哪一行。
4. 需要维护者决定的事，例如口径取舍、白名单、合同、版本或发布。
