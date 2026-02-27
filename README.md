# English

- Add a shipping box for exchanging items.
- Define exchange rules through data packs
- After placing items in the shipping box, they will be exchanged at 6:00 the next day according to the rules set in the "exchange rules".
- Items automatically have exchange information, supporting JEI list display of item exchange information.

## Instructions
- The "exchange rules" need to be placed in the data/shipping_box/recipe_manager folder.
- All formats support input and output quantities. The input and output items can be any items; these are just examples. The files must be in JSON format, and there can be multiple JSON files.
- The following is the internal JSON format, with all descriptions in the code.

\=====================================================================

# 中文

## 添加一个用于物品兑换物品的售货箱
- 通过数据包定义"兑换规则"
- 将物品放入售货箱后，在第二天6:00会按照"兑换规则"内设置的规则进行兑换
- 物品自动有兑换信息，支持jei列表显示物品的兑换信息

## 联动模组
### ViScriptShop
- 添加一个物品兑换虚拟货币的模组，需要依赖ViScriptShop模组
- 右键爬爬币会根据物品提示显示的货币价格兑换虚拟货币，潜行右键换取一组
- 手持次元钱袋右键转换实体货币或查询余额，潜行右键兑换容器内的实体货币

## 使用方法
- 需要将“兑换规则”放入到data/shipping\_box/recipe\_manager文件夹内
- 支持任意物品作为输入/输出，且数量可自定义。 文件必须是json，可以有多个json
- 支持输入/输出数据组件
- 关于json内部格式和描述都在下面的代码中

======================================================================
# 文件格式/File Format
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

4.物品 ↔ 简单组件物品/Item → Simple Component Item
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
字符串格式/String Format
"components": "{\"quality_food:quality\":{\"level\":3,\"type\":\"quality_food:diamond\"}}"

JSON对象格式/JSON Object Format
"components": {
  "quality_food:quality": {
    "level": 3,
    "type": "quality_food:diamond"
  }
}
```