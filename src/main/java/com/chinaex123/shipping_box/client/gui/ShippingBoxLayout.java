package com.chinaex123.shipping_box.client.gui;

/**
 * 售货箱 GUI 布局常量。
 * <p>
 * 贴图 780×1016（4x），GUI 195×254。
 * Slot GUI 坐标 = (PS 单元坐标 + 4) / 4。
 * <p>
 * 此类定义了售货箱 GUI 界面的所有布局常量，包括：
 * - 贴图尺寸和缩放比例；
 * - 箱子存储区域的位置和大小；
 * - 玩家背包和快捷栏的位置。
 * <p>
 * 所有坐标值都经过像素缩放计算，适配高清贴图。
 */
public final class ShippingBoxLayout {

    /**
     * 私有构造函数，防止实例化。
     * 此类仅包含静态常量，不应被实例化。
     */
    private ShippingBoxLayout() {}

    /** 贴图原始宽度（4 倍高清） */
    public static final int TEXTURE_WIDTH = 780;

    /** 贴图原始高度（4 倍高清） */
    public static final int TEXTURE_HEIGHT = 1016;

    /** GUI 显示宽度 = 原始宽度 / 4 = 195px */
    public static final int IMAGE_WIDTH = TEXTURE_WIDTH / 4;   // 195

    /** GUI 显示高度 = 原始高度 / 4 = 254px */
    public static final int IMAGE_HEIGHT = TEXTURE_HEIGHT / 4; // 254

    /** 每个物品槽位的像素步长（间距） */
    public static final int SLOT_STEP = 18;

    /** 物品栏列数（标准箱子为 9 列） */
    public static final int CHEST_COLS = 9;

    /** 物品栏行数（6 行 = 54 格，即双箱子大小） */
    public static final int CHEST_ROWS = 6;

    /** 售货箱总槽位数 = 9 × 6 = 54 格 */
    public static final int CHEST_SLOT_COUNT = CHEST_COLS * CHEST_ROWS;

    /**
     * 售货箱存储区域起始 X 坐标。
     * 计算公式：(64 + 4) / 4 = 17
     * 其中 64 是贴图中存储区域的左上角 X 像素坐标，4 是缩放偏移量。
     */
    public static final int CHEST_START_X = 17;

    /**
     * 售货箱存储区域起始 Y 坐标。
     * 计算公式：(120 + 4) / 4 = 31
     * 其中 120 是贴图中存储区域的左上角 Y 像素坐标，4 是缩放偏移量。
     */
    public static final int CHEST_START_Y = 31;

    /**
     * 玩家背包区域起始 X 坐标。
     * 与售货箱存储区域左对齐。
     * 计算公式：(64 + 4) / 4 = 17
     */
    public static final int PLAYER_INV_START_X = 17;

    /**
     * 玩家背包区域起始 Y 坐标。
     * 计算公式：(624 + 4) / 4 = 157
     * 其中 624 是贴图中玩家背包区域的左上角 Y 像素坐标，4 是缩放偏移量。
     */
    public static final int PLAYER_INV_START_Y = 157;

    /**
     * 玩家快捷栏起始 X 坐标。
     * 与售货箱存储区域左对齐。
     * 计算公式：(64 + 4) / 4 = 17
     */
    public static final int HOTBAR_START_X = 17;

    /**
     * 玩家快捷栏起始 Y 坐标。
     * 计算公式：(872 + 4) / 4 = 219
     * 其中 872 是贴图中玩家快捷栏区域的左上角 Y 像素坐标，4 是缩放偏移量。
     */
    public static final int HOTBAR_START_Y = 219;
}