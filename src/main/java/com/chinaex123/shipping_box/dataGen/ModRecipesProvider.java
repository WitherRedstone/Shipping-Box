package com.chinaex123.shipping_box.dataGen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.ModBlocks;
import com.chinaex123.shipping_box.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipesProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    protected void buildRecipes(@NotNull RecipeOutput recipeOutput) {

        // 售货箱
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        ModBlocks.SHIPPING_BOX.get())
                .pattern("CBC")
                .pattern("BAB")
                .pattern("CBC")
                .define('A', Items.CRAFTING_TABLE)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', Tags.Items.GEMS_DIAMOND)
                .unlockedBy("has_shipping_box", has(Items.DIAMOND))
                .save(recipeOutput);


        /*===================== 爬爬币 无序 =====================*/
        // 爬爬币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        ModItems.IRON_CREEPER_COIN.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.COPPER_CREEPER_COIN)
                .unlockedBy("has_iron_creeper_coin", has(ModItems.COPPER_CREEPER_COIN))
                .save(recipeOutput);
        // 金爬爬币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        ModItems.GOLD_CREEPER_COIN.get())
                .pattern("AA ")
                .pattern("   ")
                .pattern("   ")
                .define('A', ModItems.IRON_CREEPER_COIN)
                .unlockedBy("has_gold_creeper_coin", has(ModItems.IRON_CREEPER_COIN))
                .save(recipeOutput);
        // 钻石爬爬币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        ModItems.DIAMOND_CREEPER_COIN.get())
                .pattern("AA ")
                .pattern("AA ")
                .pattern("   ")
                .define('A', ModItems.GOLD_CREEPER_COIN)
                .unlockedBy("has_diamond_creeper_coin", has(ModItems.DIAMOND_CREEPER_COIN))
                .save(recipeOutput);
        // 下界合金爬爬币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        ModItems.NETHERITE_CREEPER_COIN.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.DIAMOND_CREEPER_COIN)
                .unlockedBy("has_netherite_creeper_coin", has(ModItems.NETHERITE_CREEPER_COIN))
                .save(recipeOutput);

        // 混沌立方爬爬币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,
                        ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.NETHERITE_CREEPER_COIN)
                .unlockedBy("has_symbols_chaos_creeper_coin", has(ModItems.SYMBOLS_CHAOS_CREEPER_COIN))
                .save(recipeOutput);


        /*===================== 爬爬币 有序 =====================*/
        // 铜爬爬币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,
                        ModItems.COPPER_CREEPER_COIN.get(), 8)
                .requires(ModItems.IRON_CREEPER_COIN.get())
                .unlockedBy("has_copper_creeper_coin", has(ModItems.IRON_CREEPER_COIN.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "copper_creeper_coin_shapeless"));
        // 铁爬爬币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,
                        ModItems.IRON_CREEPER_COIN.get(), 2)
                .requires(ModItems.GOLD_CREEPER_COIN.get())
                .unlockedBy("has_iron_creeper_coin_shapeless", has(ModItems.GOLD_CREEPER_COIN.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "iron_creeper_coin_shapeless"));
        // 金爬爬币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,
                        ModItems.GOLD_CREEPER_COIN.get(), 4)
                .requires(ModItems.DIAMOND_CREEPER_COIN.get())
                .unlockedBy("has_gold_creeper_coin_shapeless", has(ModItems.DIAMOND_CREEPER_COIN.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "gold_creeper_coin_shapeless"));
        // 钻石爬爬币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,
                        ModItems.DIAMOND_CREEPER_COIN.get(), 8)
                .requires(ModItems.NETHERITE_CREEPER_COIN.get())
                .unlockedBy("has_diamond_creeper_coin_shapeless", has(ModItems.NETHERITE_CREEPER_COIN.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "diamond_creeper_coin_shapeless"));
        // 下界合金爬爬币
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,
                        ModItems.NETHERITE_CREEPER_COIN.get(), 8)
                .requires(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get())
                .unlockedBy("has_symbols_chaos_creeper_coin_shapeless", has(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "netherite_creeper_coin_shapeless"));
    }
}
