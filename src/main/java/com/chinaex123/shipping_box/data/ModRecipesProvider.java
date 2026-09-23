package com.chinaex123.shipping_box.data;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModBlocks;
import com.chinaex123.shipping_box.init.ModItems;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipesProvider extends RecipeProvider {
    private final HolderLookup.Provider provider;
    private final RecipeOutput output;

    public ModRecipesProvider(HolderLookup.Provider provider, RecipeOutput output) {
        super(provider, output);
        this.provider = provider;
        this.output = output;
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
            super(packOutput, lookupProvider);
        }

        @Override
        protected @NotNull RecipeProvider createRecipeProvider(HolderLookup.Provider provider, RecipeOutput output) {
            return new ModRecipesProvider(provider, output);
        }

        @Override
        public @NotNull String getName() {
            return ShippingBox.MOD_ID;
        }
    }

    @Override
    protected void buildRecipes() {
        HolderGetter<Item> itemRegistryLookup = this.registries.lookupOrThrow(Registries.ITEM);

        // 售货箱
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModBlocks.SHIPPING_BOX.get())
                .pattern("DBD")
                .pattern("CAC")
                .pattern("DBD")
                .define('A', ItemTags.LOGS)
                .define('B', Tags.Items.GEMS_DIAMOND)
                .define('C', Items.CRAFTING_TABLE)
                .define('D', Tags.Items.GEMS_EMERALD)
                .unlockedBy("has_shipping_box", has(Items.DIAMOND))
                .save(output);
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModBlocks.AUTO_SHIPPING_BOX.get())
                .pattern("BBB")
                .pattern("BAB")
                .pattern("BBB")
                .define('A', ModBlocks.SHIPPING_BOX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_auto_shipping_box", has(ModBlocks.SHIPPING_BOX))
                .save(output);

        // 次元钱袋
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModItems.DIMENSIONAL_POUCH.get())
                .pattern("BCB")
                .pattern("CAC")
                .pattern("BCB")
                .define('A', Tags.Items.GEMS_QUARTZ)
                .define('B', Tags.Items.GEMS_AMETHYST)
                .define('C', Tags.Items.GEMS_EMERALD)
                .unlockedBy("has_dimensional_pouch", has(Items.QUARTZ))
                .save(output);

        /*===================== 爬爬币 有序 =====================*/
        // 爬爬币
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModItems.IRON_CREEPER_COIN.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.COPPER_CREEPER_COIN)
                .unlockedBy("has_iron_creeper_coin", has(ModItems.COPPER_CREEPER_COIN))
                .save(output);
        // 金爬爬币
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModItems.GOLD_CREEPER_COIN.get())
                .pattern("AA ")
                .pattern("   ")
                .pattern("   ")
                .define('A', ModItems.IRON_CREEPER_COIN)
                .unlockedBy("has_gold_creeper_coin", has(ModItems.IRON_CREEPER_COIN))
                .save(output);
        // 钻石爬爬币
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModItems.DIAMOND_CREEPER_COIN.get())
                .pattern("AA ")
                .pattern("AA ")
                .pattern("   ")
                .define('A', ModItems.GOLD_CREEPER_COIN)
                .unlockedBy("has_diamond_creeper_coin", has(ModItems.DIAMOND_CREEPER_COIN))
                .save(output);
        // 下界合金爬爬币
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModItems.NETHERITE_CREEPER_COIN.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.DIAMOND_CREEPER_COIN)
                .unlockedBy("has_netherite_creeper_coin", has(ModItems.NETHERITE_CREEPER_COIN))
                .save(output);

        // 混沌立方爬爬币
        ShapedRecipeBuilder.shaped(itemRegistryLookup, RecipeCategory.MISC, ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.NETHERITE_CREEPER_COIN)
                .unlockedBy("has_symbols_chaos_creeper_coin", has(ModItems.SYMBOLS_CHAOS_CREEPER_COIN))
                .save(output);


        /*===================== 爬爬币 无序 =====================*/
        // 铜爬爬币
        ShapelessRecipeBuilder.shapeless(itemRegistryLookup, RecipeCategory.MISC, ModItems.COPPER_CREEPER_COIN.get(), 8)
                .requires(ModItems.IRON_CREEPER_COIN.get())
                .unlockedBy("has_copper_creeper_coin", has(ModItems.IRON_CREEPER_COIN.get()))
                .save(output, String.valueOf(Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "copper_creeper_coin_shapeless")));
        // 铁爬爬币
        ShapelessRecipeBuilder.shapeless(itemRegistryLookup, RecipeCategory.MISC, ModItems.IRON_CREEPER_COIN.get(), 2)
                .requires(ModItems.GOLD_CREEPER_COIN.get())
                .unlockedBy("has_iron_creeper_coin_shapeless", has(ModItems.GOLD_CREEPER_COIN.get()))
                .save(output, String.valueOf(Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "iron_creeper_coin_shapeless")));
        // 金爬爬币
        ShapelessRecipeBuilder.shapeless(itemRegistryLookup, RecipeCategory.MISC, ModItems.GOLD_CREEPER_COIN.get(), 4)
                .requires(ModItems.DIAMOND_CREEPER_COIN.get())
                .unlockedBy("has_gold_creeper_coin_shapeless", has(ModItems.DIAMOND_CREEPER_COIN.get()))
                .save(output, String.valueOf(Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "gold_creeper_coin_shapeless")));
        // 钻石爬爬币
        ShapelessRecipeBuilder.shapeless(itemRegistryLookup, RecipeCategory.MISC, ModItems.DIAMOND_CREEPER_COIN.get(), 8)
                .requires(ModItems.NETHERITE_CREEPER_COIN.get())
                .unlockedBy("has_diamond_creeper_coin_shapeless", has(ModItems.NETHERITE_CREEPER_COIN.get()))
                .save(output, String.valueOf(Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "diamond_creeper_coin_shapeless")));
        // 下界合金爬爬币
        ShapelessRecipeBuilder.shapeless(itemRegistryLookup, RecipeCategory.MISC, ModItems.NETHERITE_CREEPER_COIN.get(), 8)
                .requires(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get())
                .unlockedBy("has_symbols_chaos_creeper_coin_shapeless", has(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get()))
                .save(output, String.valueOf(Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "netherite_creeper_coin_shapeless")));
    }
}
