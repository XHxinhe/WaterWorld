package com.example.waterworld.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.block.Blocks;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public class CopperRecipeProvider extends FabricRecipeProvider {
    public CopperRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> exporter) {
        // 铜锭合成铜块 (2x2 -> 1)
        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, Blocks.COPPER_BLOCK)
                .pattern("##")
                .pattern("##")
                .input('#', Items.COPPER_INGOT)
                .criterion(hasItem(Items.COPPER_INGOT), conditionsFromItem(Items.COPPER_INGOT))
                // 使用与原版完全相同的ID
                .offerTo(exporter, new Identifier("minecraft", "copper_block"));

        // 铜块分解为铜锭 (1 -> 4)
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, Items.COPPER_INGOT, 4)
                .input(Blocks.COPPER_BLOCK)
                .group("copper_ingot")
                .criterion(hasItem(Blocks.COPPER_BLOCK), conditionsFromItem(Blocks.COPPER_BLOCK))
                // 使用与原版完全相同的ID
                .offerTo(exporter, new Identifier("minecraft", "copper_ingot_from_copper_block"));

        // 涂蜡铜块分解为铜锭 (1 -> 4)
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, Items.COPPER_INGOT, 4)
                .input(Blocks.WAXED_COPPER_BLOCK)
                .group("copper_ingot")
                .criterion(hasItem(Blocks.WAXED_COPPER_BLOCK), conditionsFromItem(Blocks.WAXED_COPPER_BLOCK))
                .offerTo(exporter, new Identifier("minecraft", "copper_ingot_from_waxed_copper_block"));
    }
}