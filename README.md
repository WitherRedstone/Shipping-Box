# Shipping Box | 售货箱

[English](#english) | [中文](#中文) | [Json](#Json)

---

# **English**

### Add a Shipping Bin for Item-to-Item Exchange
- Define "exchange rules" through data packs
- Place items into the shipping bin, and at 6:00 the next day, exchanges will be processed according to the rules set in the "exchange rules";
- Items automatically display exchange information, supporting JEI list display of item exchange details;
- If the "exchange rules" are configured incorrectly, an in-game reminder will be displayed.

### Integrated Mods
- **ViScriptShop**
  - Right-click with Creeper Coin to exchange for virtual currency based on the currency price shown in the item tooltip; sneak right-click to exchange a full stack;
  - Right-click with "Secondary Currency Pouch" to convert physical currency or check balance; sneak right-click to exchange physical currency from containers;
  - In "exchange rules", if the item in output is replaced with "coin": true, the input item will be directly exchanged for the mod's virtual currency.

### Usage Instructions
- Place "exchange rules" in the data/shipping_box/recipe/recipe_manager/ folder;
- Supports any item as input/output with customizable quantities. Files must be in JSON format, multiple JSON files allowed;
- Support input/output data components. Input components support NBT ranges, and output items support weight configuration;
- Components have two formats: string format and JSON object format; JSON object format is recommended;

---

# **中文**

### 添加一个用于物品兑换物品的售货箱
- 通过数据包定义"兑换规则"
- 将物品放入售货箱后，在第二天6:00会按照"兑换规则"内设置的规则进行兑换；
- 物品自动有兑换信息，支持jei列表显示物品的兑换信息；
- "兑换规则"配置错误时会在游戏内提醒。
- 拥有动态阈值系统，物品销量达阈值后，物品的价值依照JSON配置的数量进行逐步递减，可在JSON中为每个物品配置是否启用

### 联动模组
- **ViScriptShop**
  - 右键爬爬币会根据物品提示显示的货币价格兑换虚拟货币，潜行右键换取一组；
  - 手持"次元钱袋"右键转换实体货币或查询余额，潜行右键兑换容器内的实体货币；
  - 在“兑换规则”中，若将 output 中的 item 替换为 "coin": true，则输入物品将被直接兑换为模组的虚拟货币。

### 使用方法
- 需要将“兑换规则”放入到data/shipping_box/recipe/recipe_manager/文件夹内；
- 支持任意物品作为输入/输出，且数量可自定义。 文件必须是json，可以有多个json；
- 支持输入/输出数据组件，输入组件支持nbt区间，输出物品支持权重；
- 组件有两种格式，字符串格式和JSON对象格式，推荐用JSON对象格式；

### 动态阈值机制说明
- 类型：dynamic_pricing（动态定价）
- 阈值数组 threshold：定义价格变更的销量临界点（如 [100, 500, 1000]）
- 价值数组 value：对应每个阈值后的输出数量（如 [5, 3, 1]）
- 重置时间 day：阈值重置所需天数
  - day = -1：销售计数永不重置，会一直累加
  - day = 0：每天自动重置销售计数为0
  - day = N（N > 0）：每N天重置一次销售计数
- 阈值数组和价值数组必须一一对应
- 规则：
  - 销量 < 最小阈值 → 使用第一档价格
  - 销量 ≥ 最大阈值 → 使用最后一档价格
  - 销量介于阈值之间 → 使用对应档位价格
- 范围：所有玩家共享销售统计
- 示例：
```
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
      "day":3
    }
  }
}
```

---

# Json

1.物品 ↔ 物品/Item → Item
```
"rules": [
  {
    "input": {
      "item": "minecraft:stone",
      "count": 1
    },
    "output": {
      "item": "shipping_box:copper_creeper_coin",
      "count": 1
    }
  }
}
```

2.多物品 → 物品/Multiple Items → Item
```
"rules": [
  {
    "input": [
      {
        "item": "minecraft:emerald",
        "count": 1
      },
      {
        "item": "minecraft:diamond",
        "count": 2
      }
    ],
    "output": {
      "item": "shipping_box:copper_creeper_coin",
      "count": 1
    }
  }
}
```

3.标签 → 物品，输出不支持标签/Tag → item, tags are not supported for output
```
"rules": [
  {
    "input": {
      "tag": "#minecraft:logs",
      "count": 1
    },
    "output": {
      "item": "shipping_box:copper_creeper_coin",
      "count": 2
    }
  }
}
```

4.物品 ↔ 权重物品/Item → Items weight
```
"rules": [
  {
    {
      "input": {
        "item": "minecraft:nether_star",
        "count": 1
      },
      "output": {
        "type": "weight",
        "items": [
          {"item": "minecraft:diamond","count": 1,"weight": 1},
          {"item": "minecraft:emerald","count": 2,"weight": 2},
          {"item": "minecraft:iron_ingot","count": 5,"weight": 3},
          {"item": "minecraft:redstone","count": 5,"weight": 3}
        ]
      }
    }
  }
}
```

5.物品 ↔ 简单组件物品/Item → Simple Component Item
```
"rules": [
  {
    "input": {
      "item": "minecraft:iron_ingot",
      "count": 1
    },
    "output": {
      "item": "minecraft:iron_sword",
      "components": "damage=100",
      "count": 1
    }
  }
}
```

## 特殊组件
1.药水组件/Potion Item
```
字符串格式/String Format
"components": "{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}"

JSON对象格式/JSON Object Format
"components": {
  "potion_contents": {
    "potion": "minecraft:night_vision"
  }
}
```

2.附魔物品/Enchanted Item
```
字符串格式/String Format
"components": "{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}"

JSON对象格式/JSON Object Format
"components": {
  "enchantments": {
    "levels": {
      "minecraft:sharpness": 3,
      "minecraft:unbreaking": 3
    }
  }
}
```

3.附魔书/Enchanted Book
```
字符串格式/String Format
"components": "{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}"

JSON对象格式/JSON Object Format
"components": {
  "stored_enchantments": {
    "levels": {
      "minecraft:sharpness": 5,
      "minecraft:unbreaking": 3
    }
  }
}
```

## 其他模组特殊组件
1.[MOD]Quality Food
```
"components": {
  "quality_food:quality": {
    "level": 3,
    "type": "quality_food:diamond"
  }
}
```

2.[MOD]Tide2/潮汐2
#### 区间格式说明/Interval Format Description

- [min,max] - 包含边界的区间（大于等于min且小于等于max）
- (min,max) - 排除边界的区间（大于min且小于max）
- (min,max] - 左开右闭区间（大于min且小于等于max）
- [min,max) - 左闭右开区间（大于等于min且小于max）


- [min,max] - Closed interval (greater than or equal to min and less than or equal to max)  
- (min,max) - Open interval (greater than min and less than max)  
- (min,max] - Left-open, right-closed interval (greater than min and less than or equal to max)  
- [min,max) - Left-closed, right-open interval (greater than or equal to min and less than max)
```
"components": {
  "tide:fish_length": "[40.0,50.0)",
  "tide:catch_timestamp": "[5000,6000]"
}
```

## 模组联动专有
ViScriptShop
- 当在output中将item替换成"coin": true时，将进入虚拟货币兑换模式；
- 直接把输入物品兑换成模组的虚拟货币。
- 输出的count为兑换的虚拟货币金额


- In "exchange rules", when the `item` in `output` is replaced with `"coin": true`, the virtual currency exchange mode is activated.
- The input item will be directly exchanged for the mod's virtual currency.
- The `count` in the output specifies the amount of virtual currency to be exchanged.
```
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


---

## License | 许可证

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

本项目采用MIT许可证 - 详情请参阅 [LICENSE](LICENSE) 文件。