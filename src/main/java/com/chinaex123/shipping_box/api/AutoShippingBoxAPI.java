package com.chinaex123.shipping_box.api;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;

import java.util.Set;

public class AutoShippingBoxAPI {
    /**
     * 检查指定槽位是否包含已兑换的物品
     */
    public static boolean isSlotExchanged(AutoShippingBoxBlockEntity box, int slot) {
        return box.isSlotExchanged(slot);
    }

    /**
     * 获取所有已兑换的槽位
     */
    public static Set<Integer> getExchangedSlots(AutoShippingBoxBlockEntity box) {
        return box.getExchangedSlots();
    }
}
