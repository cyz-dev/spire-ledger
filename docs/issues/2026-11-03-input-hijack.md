# 第三方 mod 劫持鼠标输入时，历史/统计界面的筛选下拉点不开

跟踪 issue：[#26](https://github.com/cyz-dev/spire-ledger/issues/26)。

## 现象

有玩家反馈：打开「个人统计 / 历史对局」界面后，点击「职业」等筛选下拉没有反应，不弹出选项列表（本应在「职业：全部」下展开战士 / 猎人 / 机器人 / 观者四个选项）。但作者本机测试一切正常。

## 根因（已定位，非本 Mod 逻辑 bug）

筛选下拉用的是游戏原生 `DropdownMenu`（`com.megacrit.cardcrawl.screens.options.DropdownMenu`），打开菜单的判定是 `selectionBox.hb` 的 `hb.hovered && InputHelper.justClickedLeft`。任何在 Spire Ledger 之前消费或改写 `InputHelper.justClickedLeft` 的第三方 mod，都会让这个判定永远不成立，表现为「点了没反应」。

反馈玩家环境里启用了一批额外 mod（含一个会全局挂接鼠标点击 / 拖拽 / 右键面板的调试型 mod）。这类 mod 会抢先消费 `InputHelper` 的点击标志，导致本 Mod 的下拉打不开。**本 Mod 自己的职业列表（硬编码四个职业）和点击逻辑本身没有问题。**

## 触发条件

- 与「会劫持鼠标点击输入」的第三方 mod 同时启用；
- 该 mod 在帧循环里抢在 Spire Ledger 之前改写了 `InputHelper.justClickedLeft` / 命中框状态；
- 表现为历史界面的「职业 / 进阶 / 结果」、统计界面的各类下拉都无法展开（不限于职业一项）。

## 建议方向（待维护者决定）

1. **提示报告**：当检测到下拉无法打开（或输入被外部消费）时，在界面里给出一条提示，例如「检测到第三方 mod 抢占了鼠标输入，筛选下拉可能无法使用」，并引导用户提交诊断报告（现有 `spire-ledger-report-*.json` 已含 mod 列表，可直接定位是哪类 mod）。
2. **防御性兼容**：为下拉增加一条不依赖 `InputHelper.justClickedLeft` 的备用点击路径（如改用 `justReleasedClickLeft` 或自行维护命中框判定），降低对第三方 mod 消费输入标志的敏感度。收益有限，需评估是否引入新问题。
3. **仅在报告里记录**：不做代码改动，只在诊断报告 / 文档里把「与劫持输入的 mod 冲突」列为已知限制，让遇到的人能自行排查。

## 没有采纳的选项

| 选项 | 不采纳的理由 |
|---|---|
| 点名某个具体 mod（如 loadout）为冲突源 | 劫持输入是通用机制，任何「拖拽选择 / 右键面板 / 全局鼠标钩子」类 mod 都可能触发；点名会让用户误以为只有它冲突，且无法覆盖同类 mod |
| 直接改成自行实现的下拉控件 | 重写游戏原生 `DropdownMenu` 成本高、且要同步维护滚动/手柄/命中框语义，风险大于收益 |

## 关联

- 与「练习实例 mod 加载」无关（那是练习窗口的 mod 集合问题，见 #25）；本 issue 是主界面输入被第三方 mod 干扰。
- 反馈环境：`spire-ledger 0.6.0-beta.1`，同时启用 base/stslib/RunResumer/loadout/quickrestart/RunHistoryPlus/sts1solver/superfastmode。
