package com.chinaex123.shipping_box.dataGen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {

    /**
     * 构造函数，初始化方块标签提供器
     *
     * @param output 数据包输出位置
     * @param lookupProvider 注册表查找提供器
     * @param existingFileHelper 现有文件助手，用于验证文件存在性
     */
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, ShippingBox.MOD_ID, existingFileHelper);
    }

    /**
     * 添加方块标签定义
     * 在此方法中定义所有的方块标签及其包含的方块
     *
     * @param provider 注册表提供器，用于获取注册表信息
     */
    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        // 为添加到可被斧头挖掘标签中
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.SHIPPING_BOX.get());

        // 为添加需要铁等级的标签
        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.SHIPPING_BOX.get());
    }
}
