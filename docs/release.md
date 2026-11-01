# 发布前检查（发布者）

这个文件是给**打包和发版的人**看的，不是给玩家看的。玩家侧的提示在别处：实际战损页的页脚、`~/.spire-ledger/monster-images/README.txt`、`metadata.json` 的 `image_missing`。

## 一句话前提

**发布包不带任何遭遇图，也不下载。** 图的版权在 Mega Crit，仓库是公开的 GPL-3.0 项目；细节与五个被否掉的替代方案见 [issues/2026-11-01-monster-images.md](issues/2026-11-01-monster-images.md)。

## 发版前逐项确认

| 检查 | 怎么确认 | 不通过会怎样 |
|---|---|---|
| jar 里没有图片、没有 `monster-images/` 目录 | `build.ps1` 打完包会自动检查，命中就 throw，构建直接失败 | 构建失败；不要绕过这段检查，也不要手动 `jar cf` |
| 确认闸门本身没被改坏 | `jar tf build/SpireLedger.jar \| Select-String -Pattern '\.(png\|jpe?g)$\|monster-images/'` 应为空；临时往 `$classes` 塞一张图再构建，应报 `The jar must not ship images`（2026-11-01 实测通过） | 闸门失效，含图包可能被发出去 |
| 图片只放在数据目录，不进仓库 | 图应放 `~/.spire-ledger/monster-images/` 或 `mods/SpireLedger/monster-images/`；`src/`、`viewer/`、`samples/` 里不提交 png | `src/` 里的 png 不会进 jar（只编译 `.java`），但会污染仓库、且一旦有人把资源目录整体复制进 `$classes` 就会被闸门拦下 |
| 发布说明里写了「遭遇图需自备」 | 用下面的模板句子 | 新用户会以为图坏了，来开 issue |
| 工作坊/README 里给了图片层路径 | 同上 | 同上 |
| `ModTheSpire.json` 版本号是维护者定的 | 见 AGENTS.md 第 8 条，发版提交单独一笔 | 版本混乱 |

`-Check` 里的 `StatsOptionsProbe` 另有运行时断言：发布 jar 内查不到 `monster-images/bundled.txt`，`PickRateChart.imageDir()` 必须等于 `~/.spire-ledger/monster-images`，且不读任何其他 Mod 的目录。

## 发布说明模板

中文（贴进 release notes / 工坊描述）：

> 遭遇图需要自备：本 Mod 不打包、不下载任何游戏美术。第一次打开「实际战损」页会自动建好 `~/.spire-ledger/monster-images/`，把 PNG（文件名用遭遇名，如 `Gremlin Nob.png`）放进去即可；不放也能用，只是遭遇显示空白卡框。

English:

> Encounter art is player-supplied: this mod bundles and downloads no game art. Opening "Actual HP loss" once creates `~/.spire-ledger/monster-images/`; drop PNGs named after the encounter (e.g. `Gremlin Nob.png`) there. Without them the page still works, encounters just show a blank frame.

## 为什么不用「打包一下更省事」

- 公开仓库 + GPL-3.0 分发游戏美术，风险落在仓库和发布包上，不在玩家身上。
- 游戏怪物是 Spine 图集，不是现成 PNG；打包要自己截图/拼图，游戏更新就错位。
- 卡图、遗物图能显示，是因为运行时读玩家本机已加载的纹理，资产不经过发布包——这条路对怪物不成立。

## 如果以后要降低门槛

唯一可接受的方向：在游戏内加一个按钮，把玩家**本机已有**的图包复制进他自己的 `~/.spire-ledger/monster-images/`（只在本机复制，不随发布包分发）。做之前先在本 issue 里补方案，不要直接把别人的目录写进图源。
