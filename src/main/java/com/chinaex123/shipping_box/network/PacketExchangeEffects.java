package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.config.CommonConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 兑换特效包 **/
public record PacketExchangeEffects(int amount) implements CustomPacketPayload {
    public static final Type<PacketExchangeEffects> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "exchange_effects")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketExchangeEffects> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, PacketExchangeEffects::amount,
                    PacketExchangeEffects::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理兑换特效数据包
     * <p>
     * 当客户端收到此数据包时，会在玩家位置生成魔法阵粒子特效。
     * 使用 enqueueWork 确保特效在主线程中执行，以保证粒子生成的安全性。
     *
     * @param packet 包含特效数据的数据包实例（此处未使用）
     * @param context 网络上下文，提供玩家信息和任务调度方法
     */
    public static void handle(PacketExchangeEffects packet, IPayloadContext context) {
        // 检查配置是否启用特效
        if (!CommonConfig.ENABLE_EXCHANGE_EFFECTS.get()) {
            return; // 特效已禁用，直接返回
        }

        // 将特效生成任务加入主线程队列执行
        // 粒子生成必须在渲染线程（主线程）中进行，避免并发问题
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                spawnMagicCircleEffects(player);
            }
        });
    }

    /**
     * 在玩家周围生成魔法阵特效
     * <p>
     * 通过大量螺旋向内运动的粒子创建爆发式的视觉特效。
     * 使用 END_ROD（末影烛）和 TOTEM_OF_UNDYING（不死图腾）粒子营造魔法氛围，
     * 并使用 WAX_ON（上蜡）粒子模拟金币飘落的效果。
     *
     * @param player 玩家实例，作为特效中心点
     */
    private static void spawnMagicCircleEffects(Player player) {
        RandomSource random = player.level().random;
        double centerX = player.getX();
        double centerY = player.getY();
        double centerZ = player.getZ();

        // 烟花绽放效果：从玩家脚底向外绽放
        // 第一阶段：从脚底向上喷射的金色火花
        for (int i = 0; i < 150; i++) {
            // 从脚底中心开始
            double startX = centerX + (random.nextDouble() - 0.5) * 0.3;
            double startY = centerY + random.nextDouble() * 0.5;
            double startZ = centerZ + (random.nextDouble() - 0.5) * 0.3;

            // 向外上方扩散的速度
            double speed = 0.3 + random.nextDouble() * 0.4;
            double angle = random.nextDouble() * 2 * Math.PI;
            double horizontalSpeed = speed * 0.7;
            double verticalSpeed = speed * 0.8 + random.nextDouble() * 0.3;

            double motionX = Math.cos(angle) * horizontalSpeed;
            double motionY = verticalSpeed;
            double motionZ = Math.sin(angle) * horizontalSpeed;

            // 使用金黄色的上蜡粒子作为主色调
            player.level().addParticle(ParticleTypes.WAX_ON, startX, startY, startZ, motionX, motionY, motionZ);

            // 混合一些闪烁的末影烛粒子增加亮度
            if (i % 5 == 0) {
                player.level().addParticle(ParticleTypes.END_ROD, startX, startY, startZ, motionX, motionY, motionZ);
            }
        }

        // 第二阶段：在空中绽放的圆形光环
        for (int ring = 0; ring < 4; ring++) {
            int particlesPerRing = 100;
            double ringRadius = 1.8 + ring * 0.6;

            for (int i = 0; i < particlesPerRing; i++) {
                double angle = (i / (double) particlesPerRing) * 2 * Math.PI;

                // 添加螺旋效果：每个光环旋转一定角度
                double spiralOffset = ring * 0.5;
                angle += spiralOffset;

                // 光环在玩家上方不同高度绽放，形成层次感
                double startY = centerY + 1.2 + ring * 0.4;

                // 光环起始位置稍微向内收缩，然后向外扩散
                double startX = centerX + Math.cos(angle) * ringRadius * 0.2;
                double startZ = centerZ + Math.sin(angle) * ringRadius * 0.2;

                // 更强的水平向外扩散速度，让光环更明显
                double motionX = Math.cos(angle) * 0.25;
                double motionY = 0.08 + random.nextDouble() * 0.12;
                double motionZ = Math.sin(angle) * 0.25;

                // 交错使用不同粒子类型，营造多彩效果
                if (i % 3 == 0) {
                    // 主色调：金色光环
                    player.level().addParticle(ParticleTypes.TOTEM_OF_UNDYING, startX, startY, startZ, motionX, motionY, motionZ);
                } else if (i % 3 == 1) {
                    // 亮白色点缀
                    player.level().addParticle(ParticleTypes.END_ROD, startX, startY, startZ, motionX, motionY, motionZ);
                } else {
                    // 金黄色闪光
                    player.level().addParticle(ParticleTypes.WAX_ON, startX, startY, startZ, motionX, motionY, motionZ);
                }
            }
        }

        // 第三阶段：散落的光点和余烬
        for (int i = 0; i < 100; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * 2.5;

            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + 1.5 + random.nextDouble() * 1.5;
            double z = centerZ + Math.sin(angle) * radius;

            // 缓慢下落并散开
            double motionX = (random.nextDouble() - 0.5) * 0.1;
            double motionY = -0.05 - random.nextDouble() * 0.1;
            double motionZ = (random.nextDouble() - 0.5) * 0.1;

            // 混合使用多种粒子
            if (i % 4 == 0) {
                player.level().addParticle(ParticleTypes.WAX_OFF, x, y, z, motionX, motionY, motionZ);
            } else {
                player.level().addParticle(ParticleTypes.GLOW, x, y, z, motionX, motionY, motionZ);
            }
        }
    }
}