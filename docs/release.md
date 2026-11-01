# 发布前检查（发布者）

这个文件给打包和发版的人看。版本号和发布由维护者决定（AGENTS.md 第 2 节第 8 条）。

## 前提

发布包不带任何图片，也不下载。卡图、遗物图和遭遇图都在运行时用玩家本机的游戏资源绘制；遭遇图的做法见 [architecture.md](architecture.md) 的 `EncounterArt` 一段和 [issues/2026-11-01-monster-images.md](issues/2026-11-01-monster-images.md)。

## 发版前逐项确认

| 检查 | 怎么确认 | 不通过会怎样 |
|---|---|---|
| jar 里没有图片、没有 `monster-images/` 目录 | `build.ps1` 打完包会自动检查，命中就失败 | 构建失败；不要绕过这段检查，也不要手动 `jar cf` |
| 离线断言与统计导出探针通过 | `build.ps1 -Check`；`integration-stats-options.ps1` 在中文 1024×768 和 1920×1080 下各跑一次，结果里 `encounter_art_drawn` 应接近全部 | 统计或遭遇图回归 |
| 图片不进仓库 | `src/`、`viewer/`、`samples/` 里不提交 png | 污染仓库，资源目录整体复制进 `$classes` 时会被闸门拦下 |
| `ModTheSpire.json` 和 `Recorder.VERSION` 一致 | 发版提交单独一笔，两处同时改 | 记录里写的版本与 Mod 版本不符 |
