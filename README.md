# 📦 售货箱（Shipping Box）功能分类文档

[English](#english) | [中文](#中文)

---

# **English**

### Adding a Shipping Box for Item-to-Item Exchange

## I. Core Mechanics
- **Exchange Logic**
  - Define "exchange rules" through data packs
  - After placing items in the shipping box, exchange will occur at 6:00 the next day according to the rules set in the "exchange rules"

- **Output Dynamic Threshold Mode**
  - **Type**: `dynamic_pricing`
  - **Threshold array** `threshold`: Defines sales volume critical points for price changes (e.g., [64, 128, 256, 512])
  - **Value array** `value`: Corresponding output quantities after each threshold (e.g., [4, 3, 2, 1])
  - **Reset time** `day`: Number of days until threshold reset
    - `day = -1`: Sales count never resets, accumulates indefinitely
    - `day = 0`: Sales count automatically resets to 0 daily
    - `day = N` (N > 0): Sales count resets every N days
  - **Correspondence**: Threshold array and value array must correspond one-to-one
  - **Price Calculation Rules**:
    - Sales volume < minimum threshold → Use first tier price
    - Sales volume ≥ maximum threshold → Use last tier price
    - Sales volume between thresholds → Use corresponding tier price
  - **Statistics Scope**: Sales statistics shared among all players
  - Can be configured per item in JSON whether to enable
  - **Configuration Example**:
    ```json
    {
      "input": {
        "item": "minecraft:stone",
        "count": 1
      },
      "output": {
        "type": "dynamic_pricing",
        "item": "minecraft:diamond",
        "dynamic_properties": {
          "threshold": [64, 128, 256, 512],
          "value": [4, 3, 2, 1],
          "day": 3
        }
      }
    }
    ```

- **Output Weight Mode**
  - **Type**: `weight`
  - In this mode, item output randomly obtains an item based on weight
  - **Configuration Example**:
    ```json
    {
      "input": {
        "item": "minecraft:nether_star",
        "count": 1
      },
      "output": {
        "type": "weight",
        "items": [
          {"item": "minecraft:diamond", "count": 1, "weight": 1},
          {"item": "minecraft:emerald", "count": 2, "weight": 2},
          {"item": "minecraft:iron_ingot", "count": 5, "weight": 3},
          {"item": "minecraft:redstone", "count": 5, "weight": 3}
        ]
      }
    }
    ```

## II. User Interface & Interaction
- **JEI Integration**
  - Items automatically have exchange information, supporting JEI list display of item exchange information
- **Configuration Error Alerts**
  - In-game alerts when "exchange rules" are configured incorrectly

## III. Configuration Method
- **Rule File Path**
  - "Exchange rules" must be placed in the `data/shipping_box/recipe/recipe_manager/` folder
  - **File Format**
  - Files must be JSON, multiple JSON files are supported
  - **Rule Structure**: Use `"rules"` array containing multiple exchange rules
    ```json
      {
        "rules": [
          {
            "input": {
              "item": "minecraft:stone",
              "count": 1
            },
            "output": {
              "item": "minecraft:diamond",
              "count": 1
            }
          }
        ]
      }
      ```
### Item Attribute System

#### Selling Price Boost Attribute
- **Attribute Name**: `selling_price:selling_price_boost`
- **Default Value**: `0.0`
- **Maximum Value**: `10.0`
- **Function Description**: This attribute affects item selling price as a percentage, higher values yield more when selling
  - For example: `selling_price_boost = 0.5` means a 50% price increase
  - `selling_price_boost = 2.0` means a 200% price increase
- **Application Scope**: Applies to all exchangeable items, including:
  - Item-to-item exchange mode
  - Virtual currency exchange mode
- **Configuration Method**: Currently unobtainable in normal game modes

### Input Configuration Types
| Type               | Description                         | Example                                                                                  |
|--------------------|-------------------------------------|------------------------------------------------------------------------------------------|
| **Single Item**    | Single item as input                | `{"item": "minecraft:stone", "count": 1}`                                                |
| **Multiple Items** | Multiple items combination as input | `[{"item": "minecraft:emerald", "count": 1}, {"item": "minecraft:diamond", "count": 2}]` |
| **Tag**            | Use item tag as input               | `{"tag": "#minecraft:logs", "count": 1}`                                                 |

### Output Configuration Types
| Type                  | Description                          | Example                                                                   |
|-----------------------|--------------------------------------|---------------------------------------------------------------------------|
| **Single Item**       | Output single item                   | `{"item": "shipping_box:copper_creeper_coin", "count": 1}`                |
| **Weight Mode**       | Random output based on weight        | `{"type": "weight", "items": [...]}`                                      |
| **Dynamic Threshold** | Sales volume affects output quantity | `{"type": "dynamic_pricing", "item": "...", "dynamic_properties": {...}}` |

### Component System
- **Supports input/output data components**
- **Component Formats**:
  - **String Format**: `"components": "damage=100"`
  - **JSON Object Format (Recommended)**: `"components": {...}`

### Standard Component Examples
| Type               | String Format                                                          | JSON Object Format                                                                           |
|--------------------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| **Potion**         | `"{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}"`      | `{"potion_contents": {"potion": "minecraft:night_vision"}}`                                  |
| **Enchanted Item** | `"{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}"`       | `{"enchantments": {"levels": {"minecraft:sharpness": 3, "minecraft:unbreaking": 3}}}`        |
| **Enchanted Book** | `"{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}"` | `{"stored_enchantments": {"levels": {"minecraft:sharpness": 5, "minecraft:unbreaking": 3}}}` |

### Interval Format Description
Used for numerical range matching (such as durability, fish length, etc.):
| Format | Description | Example |
|--------|-------------|---------|
| `[min,max]` | Inclusive boundaries | `[40.0,50.0]` |
| `(min,max)` | Exclusive boundaries | `(40.0,50.0)` |
| `(min,max]` | Left-open, right-closed | `(40.0,50.0]` |
| `[min,max)` | Left-closed, right-open | `[40.0,50.0)` |

### Other Mod Component Examples
[MOD] Quality Food
```json
{
  "components": {
    "quality_food:quality": {
      "level": 3,
      "type": "quality_food:diamond"
    }
  }
}
```

[MOD] Tide
```json
{
  "components": {
    "tide:fish_length": "[40.0,50.0)",
    "tide:catch_timestamp": "[5000,6000]"
  }
}
```

## IV. Integrated Mod: ViScriptShop

### 1. Crawler Coin
- **Right-click**: Exchange for virtual currency based on the currency price displayed in the item tooltip
- **Sneak + Right-click**: Exchange one full stack

### 2. Secondary Coin Pouch
- **Right-click**: Convert physical currency or check balance
- **Sneak + Right-click**: Exchange physical currency from containers

### 3. Exchange Rule Extensions

#### Virtual Currency Exchange Mode
- **Identifier**: Replace `item` in output with `"coin": true`
- **Function**: Directly exchange input items for the mod's virtual currency
- **Amount**: `count` field specifies the amount of virtual currency to exchange
- **Basic Example**:
  ```json
  {
    "input": {
      "item": "minecraft:stone",
      "count": 1
    },
    "output": {
      "coin": true,
      "count": 10
    }
  }
  ```

#### Dynamic Threshold + Virtual Currency Exchange Mode
- **Function**: Sales volume affects the amount of virtual currency exchanged
- **Configuration**: `value` array specifies virtual currency amounts for corresponding price tiers
- **Example**:
  ```json
  {
    "input": {
      "item": "minecraft:cobblestone",
      "count": 1
    },
    "output": {
      "type": "dynamic_pricing",
      "coin": true,
      "dynamic_properties": {
        "threshold": [64, 128, 256, 512],
        "value": [10, 5, 3, 1],
        "day": 5
      }
    }
  }
  ```

---

# **中文**

### 添加一个用于物品兑换物品的售货箱

## 一、核心机制
- **兑换逻辑**
  - 通过数据包定义"兑换规则"
  - 将物品放入售货箱后，在第二天6:00会按照"兑换规则"内设置的规则进行兑换

- **输出动态阈值模式**
  - **类型**：`dynamic_pricing`（动态定价）
  - **阈值数组** `threshold`：定义价格变更的销量临界点（如 [64, 128, 256, 512]）
  - **价值数组** `value`：对应每个阈值后的输出数量（如 [4, 3, 2, 1]）
  - **重置时间** `day`：阈值重置所需天数
    - `day = -1`：销售计数永不重置，会一直累加
    - `day = 0`：每天自动重置销售计数为0
    - `day = N`（N > 0）：每N天重置一次销售计数
  - **对应关系**：阈值数组和价值数组必须一一对应
  - **价格计算规则**：
    - 销量 < 最小阈值 → 使用第一档价格
    - 销量 ≥ 最大阈值 → 使用最后一档价格
    - 销量介于阈值之间 → 使用对应档位价格
  - **统计范围**：所有玩家共享销售统计
  - 可在JSON中为每个物品配置是否启用
  - **配置示例**：
    ```json
    {
      "input": {
        "item": "minecraft:stone",
        "count": 1
      },
      "output": {
        "type": "dynamic_pricing",
        "item": "minecraft:diamond",
        "dynamic_properties": {
          "threshold": [64, 128, 256, 512],
          "value": [4, 3, 2, 1],
          "day": 3
        }
      }
    }
    ```

- **输出权重模式**
  - **类型**：`weight`（权重模式）
  - 在这个模式下，物品输出会根据权重来随机获得一个物品
  - **配置示例**：
    ```json
    {
      "input": {
        "item": "minecraft:nether_star",
        "count": 1
      },
      "output": {
        "type": "weight",
        "items": [
          {"item": "minecraft:diamond", "count": 1, "weight": 1},
          {"item": "minecraft:emerald", "count": 2, "weight": 2},
          {"item": "minecraft:iron_ingot", "count": 5, "weight": 3},
          {"item": "minecraft:redstone", "count": 5, "weight": 3}
        ]
      }
    }
    ```

## 二、用户界面与交互
- **JEI集成**
  - 物品自动有兑换信息，支持jei列表显示物品的兑换信息
- **配置错误提醒**
  - "兑换规则"配置错误时会在游戏内提醒

## 三、配置方式
- **规则文件路径**
  - 需要将“兑换规则”放入到`data/shipping_box/recipe/recipe_manager/`文件夹内
  - **文件格式**
  - 文件必须是json，可以有多个json
  - **规则结构**：使用`"rules"`数组包含多条兑换规则
    ```json
      {
        "rules": [
          {
            "input": {
              "item": "minecraft:stone",
              "count": 1
            },
            "output": {
              "item": "minecraft:diamond",
              "count": 1
            }
          }
        ]
      }
      ```
### 物品属性系统

#### 售价加成属性
- **属性名称**：`selling_price:selling_price_boost`
- **默认值**：`0.0`
- **最大值**：`10.0`
- **功能说明**：该属性以百分比形式影响物品售价，数值越高，出售所得越多
  - 例如：`selling_price_boost = 0.5` 表示售价提升 50%
  - `selling_price_boost = 2.0` 表示售价提升 200%
- **应用范围**：适用于所有可兑换物品，包括：
  - 物品兑换物品模式
  - 虚拟货币兑换模式
- **配置方式**：暂时无法在常规模式下获得

### 输入配置类型
| 类型      | 说明         | 示例                                                                                       |
|---------|------------|------------------------------------------------------------------------------------------|
| **单物品** | 单个物品作为输入   | `{"item": "minecraft:stone", "count": 1}`                                                |
| **多物品** | 多个物品组合作为输入 | `[{"item": "minecraft:emerald", "count": 1}, {"item": "minecraft:diamond", "count": 2}]` |
| **标签**  | 使用物品标签作为输入 | `{"tag": "#minecraft:logs", "count": 1}`                                                 |

### 输出配置类型
| 类型       | 说明       | 示例                                                                        |
|----------|----------|---------------------------------------------------------------------------|
| **单物品**  | 输出单个物品   | `{"item": "shipping_box:copper_creeper_coin", "count": 1}`                |
| **权重模式** | 按权重随机输出  | `{"type": "weight", "items": [...]}`                                      |
| **动态阈值** | 销量影响输出数量 | `{"type": "dynamic_pricing", "item": "...", "dynamic_properties": {...}}` |

### 组件系统
- **支持输入/输出数据组件**
- **组件格式**：
  - **字符串格式**：`"components": "damage=100"`
  - **JSON对象格式（推荐）**：`"components": {...}`

### 标准组件示例
| 类型       | 字符串格式                                                                  | JSON对象格式                                                                                     |
|----------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| **药水**   | `"{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}"`      | `{"potion_contents": {"potion": "minecraft:night_vision"}}`                                  |
| **附魔物品** | `"{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}"`       | `{"enchantments": {"levels": {"minecraft:sharpness": 3, "minecraft:unbreaking": 3}}}`        |
| **附魔书**  | `"{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}"` | `{"stored_enchantments": {"levels": {"minecraft:sharpness": 5, "minecraft:unbreaking": 3}}}` |

### 区间格式说明
用于数值范围匹配（如耐久度、鱼长度等）：
| 格式 | 说明 | 示例 |
|------|------|------|
| `[min,max]` | 包含边界 | `[40.0,50.0]` |
| `(min,max)` | 排除边界 | `(40.0,50.0)` |
| `(min,max]` | 左开右闭 | `(40.0,50.0]` |
| `[min,max)` | 左闭右开 | `[40.0,50.0)` |

### 其他模组组件示例
[MOD]Quality Food
```json
{
  "components": {
    "quality_food:quality": {
      "level": 3,
      "type": "quality_food:diamond"
    }
  }
}
```

[MOD]潮汐(Tide)
```json
{
  "components": {
    "tide:fish_length": "[40.0,50.0)",
    "tide:catch_timestamp": "[5000,6000]"
  }
}
```

## 四、联动模组：ViScriptShop

### 1. 爬爬币
- **右键**：根据物品提示显示的货币价格兑换虚拟货币
- **潜行右键**：换取一组

### 2. 次元钱袋
- **右键**：转换实体货币或查询余额
- **潜行右键**：兑换容器内的实体货币

### 3. 兑换规则扩展

#### 虚拟货币兑换模式
- **标识**：在output中将`item`替换为`"coin": true`
- **功能**：直接将输入物品兑换为模组的虚拟货币
- **金额**：`count`字段指定兑换的虚拟货币数量
- **基础示例**：
  ```json
  {
    "input": {
      "item": "minecraft:stone",
      "count": 1
    },
    "output": {
      "coin": true,
      "count": 10
    }
  }
  ```

#### 动态阈值 + 虚拟货币兑换模式
- **功能**：销量影响虚拟货币的兑换数量
- **配置**：`value`数组指定对应价格区间的虚拟货币数量
- **示例**：
  ```json
  {
    "input": {
      "item": "minecraft:cobblestone",
      "count": 1
    },
    "output": {
      "type": "dynamic_pricing",
      "coin": true,
      "dynamic_properties": {
        "threshold": [64, 128, 256, 512],
        "value": [10, 5, 3, 1],
        "day": 5
      }
    }
  }
  ```

---

## License | 许可证

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

本项目采用MIT许可证 - 详情请参阅 [LICENSE](LICENSE) 文件。