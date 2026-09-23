package com.chinaex123.shipping_box.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CommonConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue EXCHANGE_TIME;
    public static final ModConfigSpec.BooleanValue ENABLE_EXCHANGE_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_TRANSACTION_LOGGING;

    static {
        BUILDER.comment("通用配置").push("Common Config");
        EXCHANGE_TIME = BUILDER
                .comment("每天售货箱进行兑换的时间（以 tick 为单位）")
                .comment("Daily vending crate exchange time (in ticks)")
                .defineInRange("exchangeTime", 0, 0, 23999);
        ENABLE_EXCHANGE_EFFECTS = BUILDER
                .comment("是否启用兑换成功时的粒子特效")
                .comment("Enable particle effects on successful exchange")
                .define("enableExchangeEffects", false);
        ENABLE_TRANSACTION_LOGGING = BUILDER
                .comment("是否启用交易日志记录，默认关闭，开启后会将所有交易记录到 config/shipping_box/logs/ 目录下")
                .comment("Enable trade logging. Default: disabled. When enabled, all trades are logged to the config/shipping_box/logs/ directory.下")
                .define("enableTransactionLogging", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
