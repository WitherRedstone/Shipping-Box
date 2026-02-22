package com.chinaex123.shipping_box.dataGen;

import com.chinaex123.shipping_box.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
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
                .pattern("BBB")
                .pattern("BAB")
                .pattern("BBB")
                .define('A', Items.CRAFTING_TABLE)
                .define('B', Tags.Items.GEMS_DIAMOND)
                .unlockedBy("has_shipping_box", has(Items.DIAMOND))
                .save(recipeOutput);

    }
}
