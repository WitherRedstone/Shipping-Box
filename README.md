<!-- omit in toc -->
<div align="center">

# 📦 Shipping Box · 售货箱

*Place it. Ship it. Collect the rewards.*

*放下箱子，寄出物品，坐等收菜。*

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen?style=flat-square)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.2-blue?style=flat-square)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

</div>

---

<!-- omit in toc -->
## 📖 目录 · Table of Contents

- [这是什么？· What is this?](#-这是什么--what-is-this)
- [快速上手 · Getting Started](#-快速上手--getting-started)
  - [玩家篇 · For Players](#-玩家篇--for-players)
  - [服主篇 · For Server Owners](#-服主篇--for-server-owners)
- [游戏内容 · Gameplay](#-游戏内容--gameplay)
  - [售货箱 · Shipping Box](#-售货箱--shipping-box)
  - [自动售货箱 · Auto Shipping Box](#-自动售货箱--auto-shipping-box)
  - [爬爬币 · Creeper Coins](#-爬爬币--creeper-coins)
  - [次元钱袋 · Dimensional Pouch](#-次元钱袋--dimensional-pouch)
  - [售价加成 · Selling Price Boost](#-售价加成--selling-price-boost)
- [兑换模式 · Exchange Modes](#-兑换模式--exchange-modes)
- [命令 · Commands](#-命令--commands)
- [联动模组 · Mod Integrations](#-联动模组--mod-integrations)
- [配置 · Configuration](#-配置--configuration)
- [License](#license)

---

# 🇬🇧 English

## 🤔 What is this?

**Shipping Box** adds an automated trading system to Minecraft. Place items in a box, go do something else, and when the sun rises at 6:00 AM — your items have been magically exchanged for rewards.

- 🎯 **No redstone, no machines** — just place and wait
- 💰 **Built-in virtual currency** — earn coins and spend them
- 📈 **Dynamic economy** — prices shift with supply and demand
- 🎲 **Weighted loot tables** — random rewards with adjustable odds
- 🌱 **Seasonal pricing** — crops worth more in-season (with Ecliptic Seasons)
- 🖥️ **Visual rule editor** — edit exchange rules right in your browser
- 🔧 **Data pack driven** — fully customizable exchange rates

## 🏃 Quick Start

### 🎮 For Players

1. **Get a Shipping Box** — craft it or ask your server admin
2. **Open it** — right-click to access the 54-slot inventory
3. **Put items in** — deposit items that have exchange rules configured
4. **Wait** — each day at 6:00 AM, your items are exchanged
5. **Collect** — come back to find new items waiting for you!

> 💡 Hover over any item in your inventory — if it's exchangeable, the tooltip will show you what you'll get!

### 🛠️ For Server Owners

1. Drop the mod into `mods/`
2. Configure exchange rules in `data/shipping_box/exchange_rules/` (data pack)
3. Or use the **visual editor**:
   ```
   /shipping_box editor cache_icons
   /shipping_box web
   ```
4. Tweak settings in `config/shipping_box-common.toml`

## 🎮 Gameplay

### 📬 Shipping Box

The basic 54-slot shipping box. Place it anywhere — on a wall or on the ground. All players can use it, but each player sees only their own deposited items. Hopper input is blocked to prevent accidental insertion.

### 🔒 Auto Shipping Box

A player-bound variant. The first player to open it becomes the owner — no one else can access it. The binding survives being broken and placed again. Perfect for player shops or personal storage.

### 🪙 Creeper Coins

Physical coins that convert into your **built-in virtual currency balance**. Right-click to exchange one coin, sneak + right-click to exchange a stack.

| Coin | Value |
|------|-------|
| Copper Creeper Coin | 1◎ |
| Iron Creeper Coin | 8◎ |
| Gold Creeper Coin | 16◎ |
| Diamond Creeper Coin | 64◎ |
| Emerald Creeper Coin | 256◎ |
| Netherite Creeper Coin | 512◎ |
| Chaos Symbol Creeper Coin | 4096◎ |

### 👛 Dimensional Pouch

Converts physical coins in your inventory to virtual balance with a single click.
- **Right-click** — convert coins in your inventory
- **Sneak + Right-click** — convert coins in a container you're pointing at

### ⚡ Selling Price Boost

A player attribute (`selling_price:selling_price_boost`) that increases all exchange output by a percentage. Higher boost = more items from every exchange.

## 📊 Exchange Modes

| Mode | Description | Example |
|------|-------------|---------|
| **Simple** | Fixed input → fixed output | 1 Stone → 1 Diamond |
| **Coin** | Input items → virtual currency | 1 Dirt → 10◎ |
| **Dynamic Pricing** | Output decreases as more items are sold globally | 1 Cobblestone → decreasing◎ |
| **Weight** | Random reward from a weighted pool | Nether Star → random treasure |
| **Seasonal** | Price changes with the seasons | Carrots sell for +30% in winter |

> 🔍 **Precision Matching** — rules match the most specific item first. A rule with component requirements beats a plain item rule, and a plain item rule beats a tag match.

## 📋 Commands

> All commands require OP level 2+

| Command | Description |
|---------|-------------|
| `/shipping_box force_exchange` | Force-exchange the box you're looking at right now |
| `/shipping_box rules count` | Show how many rules are loaded |
| `/shipping_box rules list [page]` | Browse all rules (5 per page) |
| `/shipping_box web` | Open the visual rule editor in your browser |
| `/shipping_box editor cache_icons` | Build item icon cache for the editor |
| `/shipping_box editor cache_icons force` | Force rebuild icon cache |
| `/shipping_box editor cache_status` | Check icon cache progress |
| `/shipping_box editor cache_clear` | Clear icon cache |

## 🔗 Mod Integrations

### 🌱 Ecliptic Seasons *(optional)*

Crops with seasonal tags earn a bonus when sold in their preferred season — and take a penalty when out of season. Configure per-rule with the `ecliptic_seasons` output type.

### 📜 KubeJS *(optional)*

When KubeJS is installed, exchange rules saved via the web editor go to `kubejs/data/shipping_box/exchange_rules/` and trigger `kubejs reload server_scripts` automatically.

## ⚙️ Configuration

Edit `config/shipping_box-common.toml`:

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `exchangeTime` | int (0–23999) | `0` | Exchange trigger time in ticks (0 = 6:00 AM) |
| `enableVirtualCurrency` | bool | `true` | Enable the built-in virtual currency system |
| `enableExchangeEffects` | bool | `false` | Firework particles on successful exchange |
| `enableTransactionLogging` | bool | `false` | Log all transactions to `config/shipping_box/logs/` |

---

# 🇨🇳 中文

## 🤔 这是什么？

**售货箱**为 Minecraft 添加了一套自动化交易系统。把物品放入箱子，该干嘛干嘛，次日清晨 **6:00**——你的物品已经变魔法似的换成了奖励。

- 🎯 **无需红石，无需机器**——放下箱子等着就行
- 💰 **内置虚拟货币**——赚币、花币一气呵成
- 📈 **动态经济**——供需影响价格，全服联动
- 🎲 **权重随机**——像抽奖一样的随机奖励
- 🌱 **季节定价**——应季作物更值钱（需节气模组）
- 🖥️ **可视化编辑**——在浏览器里写兑换规则
- 🔧 **数据包驱动**——兑换表完全可自定义

## 🏃 快速上手

### 🎮 玩家篇

1. **获取售货箱**——合成或找服主领取
2. **打开**——右键打开 54 格的箱子界面
3. **放东西**——放入有兑换规则的物品
4. **等**——每天 6:00 自动兑换
5. **拿**——回来打开箱子，奖励已经在里面了！

> 💡 把鼠标悬停在物品上——如果有兑换规则，提示框会告诉你能换到什么！

### 🛠️ 服主篇

1. 把模组丢进 `mods/`
2. 在 `data/shipping_box/exchange_rules/` 里配置规则（数据包）
3. 或者用**可视化编辑器**：
   ```
   /shipping_box editor cache_icons
   /shipping_box web
   ```
4. 在 `config/shipping_box-common.toml` 调整设置

## 🎮 游戏内容

### 📬 售货箱

基础 54 格售货箱。放墙上、放地上都行。所有人都能用，但**每个人只能看到自己存入的物品**。禁止漏斗输入，防止误操作。

### 🔒 自动售货箱

绑定玩家的高级售货箱。谁先打开就归谁——其他人打不开。被挖掉再放置也**不掉绑定**。适合做玩家商店或个人仓库。

### 🪙 爬爬币

实体硬币，右键兑换为**内置虚拟货币余额**。右键换一枚，潜行右键换一组。

| 硬币 | 价值 |
|------|------|
| 铜爬爬币 | 1◎ |
| 铁爬爬币 | 8◎ |
| 金爬爬币 | 16◎ |
| 钻石爬爬币 | 64◎ |
| 绿宝石爬爬币 | 256◎ |
| 下界合金爬爬币 | 512◎ |
| 混沌立方爬爬币 | 4096◎ |

### 👛 次元钱袋

一键把背包里的硬币转为虚拟余额。
- **右键**——转换背包内硬币
- **潜行右键**——转换指向容器内的硬币

### ⚡ 出售价格加成

一个玩家属性（`selling_price:selling_price_boost`），按百分比增加所有兑换产出。加成越高，每次兑换获得的物品越多。

## 📊 兑换模式

| 模式 | 效果 | 例子 |
|------|------|------|
| **基础兑换** | 固定换固定 | 1 石头 → 1 钻石 |
| **虚拟货币** | 物品 → 虚拟币 | 1 泥土 → 10◎ |
| **动态定价** | 全服卖越多，产出越少 | 1 圆石 → 递减◎ |
| **权重随机** | 从奖池里随机抽 | 下界之星 → 随机宝藏 |
| **季节定价** | 应季涨，过季跌 | 胡萝卜冬天 +30% |

> 🔍 **精准匹配**——最具体的规则优先匹配。带组件条件的 > 精确物品 > 标签匹配。

## 📋 命令

> 需要 OP 2 级以上

| 命令 | 效果 |
|------|------|
| `/shipping_box force_exchange` | 立即强制兑换你看着的售货箱 |
| `/shipping_box rules count` | 查看加载了多少条规则 |
| `/shipping_box rules list [页数]` | 分页查看规则（每页 5 条） |
| `/shipping_box web` | 在浏览器中打开可视化编辑器 |
| `/shipping_box editor cache_icons` | 构建图标缓存 |
| `/shipping_box editor cache_icons force` | 强制重建图标缓存 |
| `/shipping_box editor cache_status` | 查看图标缓存进度 |
| `/shipping_box editor cache_clear` | 清除图标缓存 |

## 🔗 联动模组

### 🌱 节气联动 *(可选)*

带季节标签的作物在当季出售有加成，过季有减益。通过 `ecliptic_seasons` 输出类型配置。

### 📜 KubeJS *(可选)*

安装 KubeJS 后，Web 编辑器保存的规则会写入 `kubejs/data/shipping_box/exchange_rules/` 并自动执行 `/kubejs reload server_scripts`。

## ⚙️ 配置

编辑 `config/shipping_box-common.toml`：

| 设置 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `exchangeTime` | int (0–23999) | `0` | 兑换触发时间 tick（0=6:00） |
| `enableVirtualCurrency` | bool | `true` | 启用内置虚拟货币系统 |
| `enableExchangeEffects` | bool | `false` | 兑换成功时播放烟花粒子 |
| `enableTransactionLogging` | bool | `false` | 交易日志记录到 `config/shipping_box/logs/` |

---

<br>
<div align="center">

**[CurseForge](https://www.curseforge.com/minecraft/mc-mods/shipping-box)** · **[GitHub](https://github.com/WitherRedstone/Shipping-Box)**

Made with 💚 by [Wither_Redstone](https://github.com/WitherRedstone)

<br>

## License

**MIT** — see [LICENSE](LICENSE)

</div>
