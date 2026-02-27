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
- 支持输入/输出数据组件，输入组件支持nbt区间
- 组件有两种格式，字符串格式和JSON对象格式，推荐用JSON对象格式
- 关于json内部格式和描述都在下面的代码中

======================================================================
# 文件格式
1.物品 ↔ 物品
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

2.多物品 → 物品
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

3.标签 → 物品，输出不支持标签
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

4.物品 ↔ 简单组件物品
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
1.药水组件
```
字符串格式
"components": "{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}"

JSON对象格式
"components": {
  "potion_contents": {
    "potion": "minecraft:night_vision"
  }
}
```

2.附魔物品
```
字符串格式
"components": "{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}"

JSON对象格式
"components": {
  "enchantments": {
    "levels": {
      "minecraft:sharpness": 3,
      "minecraft:unbreaking": 3
    }
  }
}
```

3.附魔书
```
字符串格式
"components": "{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}"

JSON对象格式
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

2.[MOD]潮汐2
- 区间格式说明
[min,max] - 包含边界的区间（大于等于min且小于等于max）
(min,max) - 排除边界的区间（大于min且小于max）
(min,max] - 左开右闭区间（大于min且小于等于max）
[min,max) - 左闭右开区间（大于等于min且小于max）
```
"components": {
  "tide:fish_length": "[40.0,50.0]",
  "tide:catch_timestamp": "[5000,6000]"
}
```