# 实际战损页没有遭遇图（需要玩家自备本地图片）

状态：已定方案，代码已改，待游戏内验收（W16）。
提出：2026-11-01。相关规格：[product.md](../product.md)「实际战损图的遭遇图」、[architecture.md](../architecture.md)、[testing.md](../testing.md) W7 / W16。

## 现象

“战斗与资源 → 实际战损”和它的导出图里，每个遭遇只有一张空白卡框，没有怪物像。新装用户第一反应是“图挂了”，因为界面上没有任何地方说明这里本来就需要自己放图。

## 原因

1. 游戏自带的怪物素材是 **Spine 图集**（`.atlas` + 分块贴图），不是可直接当立绘用的单张 PNG；把它们拼回一张图需要额外渲染步骤，且受 `TexturePacker` 的裁剪与旋转影响，拼出来和游戏内观感不一致。
2. 游戏本体美术（怪物、卡牌、遗物）版权属于 Mega Crit。Mod 读取玩家本机已安装游戏里的资源属于正常使用；但把这些图**打进 jar 分发**是另一回事。这个仓库是公开的 GPL-3.0 项目，往里放游戏美术资产，等于让仓库自己承担侵权风险——Mega Crit 对同人 Mod 一向宽松，但“宽松”不等于“授权分发美术资源”，一旦被投诉，首当其冲的是仓库和发布包，而不是某个玩家。所以发布包不打图，也不在运行时下载。
3. 之前为了图省事，把 TinyCat 的图片目录当成图源之一（`mods/TinyCat/monster-images`）。这等于把一个可选功能绑死在另一个 Mod 的安装状态上：TinyCat 改名、换版本、被卸载，图就集体消失；玩家也分不清图来自谁、出问题该找谁。这不符合“只依赖自己的数据”的边界。

## 决定

- 图源只有本 Mod 自己的两个目录，且优先玩家自己的：
  1. `~/.spire-ledger/monster-images/`（`PickRateChart.imageDir()`，玩家图片层，换 Mod 版本不丢）
  2. `mods/SpireLedger/monster-images/` 与 `mods/spire-ledger/monster-images/`（跟着 Mod 目录走，适合整包分发）
- **不读任何其他 Mod 的目录**（含 TinyCat、游戏根目录 `monster-images`），TinyCat 装不装对本功能没有任何影响。
- **首次进入实际战损页时自动创建空图片层**并写入 `README.txt`（命名规则、支持 png/jpg、子目录递归、不打包声明）。空文件夹本身就是“这里需要放图”的提示，比只画空白框更容易被发现。
- 缺图时：预览页脚提示图片层路径；`metadata.json` 写 `image_dir` / `image_source`，一个图都没找到时再写 `image_missing`。导出的图不因缺图而失败。
- 匹配规则对玩家友好、但不依赖任何第三方约定：去扩展名、转小写、去掉非字母数字，容忍文件名开头的人数前缀（`2 Fungi Beasts.png` 与 `Fungi Beasts.png` 指向同一遭遇）。`Gremlin Nob.png`、`gremlin-nob.png` 都能对上。

## 没有采纳的选项

| 选项 | 不采纳的理由 |
|---|---|
| 把 64 张遭遇图打进 jar | 公开仓库 + 发布包分发游戏美术，版权风险由仓库承担；且会随游戏版本变动而过时 |
| 运行时从游戏资源里拼 Spine 立绘 | 依赖内部图集布局，版本一变就碎；渲染成本高，还要处理裁剪/旋转 |
| 首次运行时联网下载图包 | 需要托管、需要授权，仍然是在分发美术资源；离线玩家与网络受限环境直接不可用 |
| 直接读 TinyCat 的目录 | 把可选功能绑在另一个 Mod 上；对方目录结构变化即失效；责任边界不清 |
| 只在文档里写“请自备图片” | 玩家不会去翻文档；空文件夹 + 页脚提示 + `README.txt` 三处可见才算“提示到位” |

## 验收

- W16：只有 TinyCat 目录时不出图（证明不读别人的目录）；把图放进 `~/.spire-ledger/monster-images` 后重新进入该页出图；两个目录都没有时自动建目录 + `README.txt` + 空白占位。
- `StatsOptionsProbe` 断言：`PickRateChart.imageDir()` 等于 `~/.spire-ledger/monster-images`；`ensureImageDir()` 建出目录；发布 jar 内不带 `monster-images/bundled.txt`。

## 本地现状（2026-11-01）

本机已把 64 张遭遇图复制到 `~/.spire-ledger/monster-images/`；真实 64 种遭遇、30393 场战斗实测 100% 命中。这些图**不入 git**，发布包不含。
