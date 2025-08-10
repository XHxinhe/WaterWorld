package com.XHxinhe.waterworld.mixin;

import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultBiomeFeatures;
import net.minecraft.world.gen.feature.VegetationPlacedFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DefaultBiomeFeatures.class)
public class DefaultBiomeFeaturesMixin {

    // 移除沼泽中的睡莲
    @Inject(method = "addSwampFeatures", at = @At("HEAD"), cancellable = true)
    private static void removeWaterlily(GenerationSettings.LookupBackedBuilder builder, CallbackInfo ci) {
        System.out.println("=======================================");
        System.out.println("WATERWORLD MOD: REMOVING WATERLILIES!!!");
        System.out.println("=======================================");

        ci.cancel();

        // 手动添加沼泽特性，但不包括睡莲
        builder.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                VegetationPlacedFeatures.TREES_SWAMP);
        builder.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                VegetationPlacedFeatures.FLOWER_SWAMP);
        builder.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                VegetationPlacedFeatures.PATCH_GRASS_NORMAL);
        builder.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                VegetationPlacedFeatures.PATCH_DEAD_BUSH);
        // 这里不添加睡莲 PATCH_WATERLILY
        builder.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                VegetationPlacedFeatures.BROWN_MUSHROOM_SWAMP);
        builder.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                VegetationPlacedFeatures.RED_MUSHROOM_SWAMP);
    }

    // 移除冰山
    @Inject(method = "addIcebergs", at = @At("HEAD"), cancellable = true)
    private static void removeIcebergs(GenerationSettings.LookupBackedBuilder builder, CallbackInfo ci) {
        System.out.println("=======================================");
        System.out.println("WATERWORLD MOD: REMOVING ICEBERGS!!!");
        System.out.println("=======================================");

        ci.cancel();
    }
}