# 练习结束后应接着地图继续打，而不是从头开始

## 现象

从历史对局点「练习此回合」后，打完当前战斗（或结束回合），练习窗口会**回到该局的起点**重新开始，而不是「打完这一层 → 回到地图 → 点下一个节点继续推进」。

参考 Steam 工坊 mod **Run Resumer**（id 3127212809）：它给每一层存一份完整存档，在 Run History 界面右键「Continue」就能从那一层继续玩下去。我们的 Spire Ledger 已经记录了楼层（`floor` / `floor_enter` / `map` 事件），数据层面完全够用，缺的只是「把练习从『战斗内棋盘恢复』扩展成『从地图继续推进』」这条恢复路径。

## 现状与根因

练习实例的恢复链路（`ReplayLauncher.checkPracticeStartup()`）只做了一件事：`recreateCharacter` → `new Exordium(p, new ArrayList<>())`（**空地图**）→ `TurnAdapter.restore(回合快照)`。也就是说，它只能把玩家还原到**某个战斗回合的棋盘**，永远停留在那一场战斗里，没有「房间结算 → 回地图 → 下一节点」的出路。

而我们其实已经记录了足以重建地图的数据：

- `map` 事件：整张地图的节点图，每个节点带 `x` / `y` / `room`（MonsterRoom / EventRoom / ShopRoom / RestRoom / TreasureRoom / Boss…）和 `edges`（可走路径）、`burning`（燃烧精英）；
- `floor_enter` 事件：每层进入时的 `x` / `y` / `room` / `hp` / `max_hp` / `gold`；
- `floor` 事件：每层的 `deck` / `relics` / `potions` / `act` / `class`，足够还原玩家状态。

## 缺口（为什么现在做不了）

1. **快照里没有地图**。`cur_map_node_state` 只有当前节点标记（`taken` / `highlighted` / `has_emerald_key` …），没有整张地图的 `MapRoomNode` 拓扑。完整拓扑只在 `map` 事件里。
2. **恢复目标是「战斗内」，不是「地图上」**。练习实例启动时构造的是空地图，`currMapNode` 指向一个空的 `EmptyRoom`，恢复后直接进战斗。
3. **房间类型重建**。要把 `map` 事件里的 `room` 字符串还原成可玩的 `AbstractRoom`（`MonsterRoom` 需要怪物组、`EventRoom` 需要事件、`ShopRoom` 需要货架），而快照的 SaveState 无法编码「没有怪物的 `MonsterRoom`」（`ReplayLauncher.java` 已有注释点明这一点）。Run Resumer 是每层存完整存档才绕过的。

## 建议方向

两种做法，从易到难：

- **A. 楼层存档（对齐 Run Resumer）**：在每层进入/结束时额外存一份「地图态」快照（含 `map` 拓扑 + 玩家状态 + `currMapNode` 位置），练习时用它重建地图并停在「地图界面」，让玩家点下一节点继续。代价是每层多一份存档（Run Resumer 一个 50 层的局约 1 MB，可接受）。
- **B. 纯记录重建**：只靠 `map` + `floor_enter` 事件重建地图和房间，不额外存存档。更省空间，但要把 `room` 字符串映射回可玩房间，边界情况（事件房间的事件 id、商店货架、精英燃烧、Boss 前后状态）多，容易漏。

两者都要求练习实例的「恢复完成」状态从「战斗内」扩展为「可选：战斗内 / 地图上」，并在历史界面加一个「从此层继续」的入口（现在只有「练习此回合」）。

## 关联

- 这是产品定位的 bug：用户期望「练习完接着打」，而不是「每次从该回合的棋盘重来一遍」。
- 语言/读 mod 的问题（练习窗口英文、不加载玩家 mod）已在 `e751092` 修复，与本 issue 无关。
