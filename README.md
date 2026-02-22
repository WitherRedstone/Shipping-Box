# English

- Add a shipping box for exchanging items.
- Define exchange rules through data packs
- After placing items in the shipping box, they will be exchanged at 6:00 the next day according to the rules set in the "exchange rules".
- Items automatically have exchange information, supporting JEI list display of item exchange information.

## Instructions
- The "exchange rules" need to be placed in the darta/shipping_box/recipe_manager folder.
- All formats support input and output quantities. The input and output items can be any items; these are just examples. The files must be in JSON format, and there can be multiple JSON files.
- The following is the internal JSON format, with all descriptions in the code.

\=====================================================================

# 中文

## 添加一个用于物品兑换物品的售货箱
- 通过数据包定义"兑换规则"
- 将物品放入售货箱后，在第二天6:00会按照"兑换规则"内设置的规则进行兑换
- 物品自动有兑换信息，支持jei列表显示物品的兑换信息

## 使用方法
- 需要将“兑换规则”放入到darta/shipping\_box/recipe\_manager文件夹内
- 所有格式都支持输入输出数量，输入输出物品可以是任意物品，这里只是示例。 文件必须是json文件，可以有多个json文件
- 关于json内部格式和描述都在上面的代码中

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
      "count": 1,
      "components": "damage=100"
    }
  }
}
```

5.物品 ↔ 药水物品/Item → Potion Item
```
"rules": [
  {
    "input": {
      "item": "minecraft:glass_bottle",
      "count": 1
    },
    "output": {
      "item": "minecraft:potion",
      "components": "{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}",
      "count": 1
    }
  }
}
```

6.物品 ↔ 附魔物品/Item → Enchanted Item
```
"rules": [
  {
    "input": {
      "item": "minecraft:golden_sword",
      "count": 1
    },
    "output": {
      "item": "minecraft:golden_sword",
      "components": "{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}",
      "count": 1
    }
  },
  {
    "input": {
      "item": "minecraft:diamond_sword",
      "count": 1
    },
    "output": {
      "item": "minecraft:diamond_sword",
      "components": "{\"enchantments\":{\"levels\":{\"minecraft:sharpness\":5,\"minecraft:unbreaking\":1}}}",
      "count": 1
    }
  }
}
```

7.物品 ↔ 附魔书/Item → Enchanted Book
```
"rules": [
  {
    "input": {
      "item": "minecraft:book",
      "count": 1
    },
    "output": {
      "item": "minecraft:enchanted_book",
      "components": "{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}",
      "count": 1
    }
  }
}
```