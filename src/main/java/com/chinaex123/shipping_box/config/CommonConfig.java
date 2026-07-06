package com.chinaex123.shipping_box.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 售货箱模组通用配置
 * <p>
 * 使用 NeoForge 的 ModConfigSpec 系统定义和管理模组的所有配置项。
 * 配置文件生成在 {@code config/shipping_box-common.toml} 中。
 * <p>
 * 当前支持的配置项：
 * <ul>
 *   <li>exchangeTime - 每日兑换时间（以游戏刻为单位）</li>
 *   <li>enableVirtualCurrency - 是否启用内置虚拟货币余额系统</li>
 *   <li>enableExchangeEffects - 是否启用兑换成功粒子特效</li>
 *   <li>enableTransactionLogging - 是否启用交易日志记录</li>
 * </ul>
 */
public class CommonConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue EXCHANGE_TIME;
    public static final ModConfigSpec.BooleanValue ENABLE_VIRTUAL_CURRENCY;
    public static final ModConfigSpec.BooleanValue ENABLE_EXCHANGE_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_TRANSACTION_LOGGING;

    static {
        BUILDER.push("GlobalConfiguration");
        BUILDER.comment("全局配置");
        EXCHANGE_TIME = BUILDER
                .comment("每天售货箱进行兑换的时间（以 tick 为单位）")
                .defineInRange("exchangeTime", 0, 0, 23999);
        ENABLE_VIRTUAL_CURRENCY = BUILDER
                .comment("是否启用模组内置虚拟货币余额",
                        "默认开启，用于在外部商店联动不可用时提供内置余额",
                        "关闭后：虚拟货币兑换规则不会执行，硬币和次元钱袋不会转换为内置余额")
                .define("enableVirtualCurrency", true);
        ENABLE_EXCHANGE_EFFECTS = BUILDER
                .comment("是否启用兑换成功时的粒子特效",
                        "默认关闭，开启后会在兑换成功时播放烟花特效")
                .define("enableExchangeEffects", false);
        ENABLE_TRANSACTION_LOGGING = BUILDER
                .comment("是否启用交易日志记录",
                        "默认关闭，开启后会将所有交易记录到 config/shipping_box/logs/ 目录下")
                .define("enableTransactionLogging", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
