package com.chinaex123.shipping_box.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ExchangeRule {

    private List<InputItem> inputs;
    private OutputItem output;

    public List<InputItem> getInputs() {
        return inputs;
    }

    public void setInputs(List<InputItem> inputs) {
        this.inputs = inputs;
    }

    public OutputItem getOutputItem() {
        return output;
    }

    public void setOutput(OutputItem output) {
        this.output = output;
    }

    public static class InputItem {
        private String item;  // 物品ID
        private String tag;   // 标签ID
        private String nbt;   // NBT数据
        private int count = 1;

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getNbt() {
            return nbt;
        }

        public void setNbt(String nbt) {
            this.nbt = nbt;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        /**
         * 检查物品堆是否匹配此输入要求
         * 支持物品ID和标签两种匹配方式
         */
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }

            // 如果定义了标签
            if (tag != null && !tag.isEmpty()) {
                try {
                    // 移除#前缀并解析标签ID
                    String tagIdStr = tag.startsWith("#") ? tag.substring(1) : tag;
                    ResourceLocation tagId = ResourceLocation.tryParse(tagIdStr);

                    if (tagId != null) {
                        TagKey<Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagId);
                        return stack.is(itemTag);
                    }
                } catch (Exception e) {
                    // 标签解析失败
                    return false;
                }
            }
            // 如果定义了物品ID
            else if (item != null && !item.isEmpty()) {
                ResourceLocation itemId = ResourceLocation.tryParse(item);
                if (itemId != null) {
                    Item requiredItem = BuiltInRegistries.ITEM.get(itemId);
                    return stack.is(requiredItem);
                }
            }

            return false;
        }
    }

    public static class OutputItem {
        private String item;
        private int count = 1;

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        /**
         * 获取输出物品堆
         */
        public ItemStack getResultStack() {
            ResourceLocation itemId = ResourceLocation.tryParse(item);
            if (itemId != null) {
                Item resultItem = BuiltInRegistries.ITEM.get(itemId);
                return new ItemStack(resultItem, count);
            }
            return ItemStack.EMPTY;
        }
    }
}
