# Spire Ledger architecture: removed items

从 [architecture.md](architecture.md) 移出的条目，原文保留。

## 2026-10-30

- 构建档：“`full`（默认，含 `replay`）；`no-replay`（剔除 `replay` 与 SaveState，同一 modid），作为上游拒绝授权时的退路，平时不发布。”
- M4：“`no-replay` 构建档要求回合重打可剔除，届时拆出 `spireledger.record` 与 `spireledger.replay`（proposed）。”

  结案：上游 STSStateSaver 2026-10-29 加了 MIT 许可证（`a762bc1cae`），这一退路不再需要，用户决定删除（2026-10-30）。该构建档从未实现，代码无改动。

## 2026-11-01

- 遭遇图（PR #20 版本）：“实际战损的遭遇图只从本 Mod 自己的两个目录读（游戏自带的怪物素材是 Spine 图集，不能直接当立绘）：`~/.spire-ledger/monster-images`（`PickRateChart.imageDir()`，玩家图片层，优先）与 `mods/SpireLedger/monster-images`、`mods/spire-ledger/monster-images`（Mod 目录）。**不读其他 Mod 的目录，也不读游戏根目录**：曾经把 `mods/TinyCat/monster-images` 放进来，等于把自己绑在另一个 Mod 的安装状态上，TinyCat 改名、卸载或换版本都会让图突然消失，且用户无法判断图到底来自谁；现在只认自己的目录，缺图就留白，`ensureImageDir()` 在索引时建好空文件夹并写 `README.txt`（文件名规则、支持格式、不打包声明），让空白框有可执行的下一步。文件名归一化规则：去扩展名、转小写、去掉非字母数字，并**容忍开头的数字前缀**（`2 Fungi Beasts.png` 与 `Fungi Beasts.png` 指向同一个遭遇），这样玩家从任何来源拿到的图都能直接放进去，但匹配逻辑本身不依赖任何第三方 Mod。找不到就画空白占位，**不下载、不打包任何图片**（版权原因见 [issues/2026-11-01-monster-images.md](issues/2026-11-01-monster-images.md)）。整个过程不构造卡牌、不调用 RNG。”
- 遭遇图（PR #20 版本）：“遭遇图按文件名匹配（去除非字母数字、忽略大小写），只查 `~/.spire-ledger/monster-images` 与 `mods/SpireLedger/monster-images`、`mods/spire-ledger/monster-images`（不查任何其他 Mod 目录），都没有时用空白卡框并在预览页脚提示图片层位置，jar 不打包任何遭遇图；`metadata.json` 在实际战损页额外写 `image_dir`、`image_source`，一个遭遇图都没找到时再写 `image_missing`；纹理按遭遇缓存，关闭统计页时释放。”

  结案：用户认可接手 PR #20，并要求修好遭遇图。只读玩家自备图片会让未放图的玩家看到全空白，改为用本机游戏的怪物资源在运行时绘制，自备图片作为替换（2026-11-01）。
