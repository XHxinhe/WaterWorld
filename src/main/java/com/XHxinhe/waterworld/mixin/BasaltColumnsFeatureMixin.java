package com.XHxinhe.waterworld.mixin;

import net.minecraft.world.gen.feature.BasaltColumnsFeature;
import net.minecraft.world.gen.feature.BasaltColumnsFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BasaltColumnsFeature.class)
public class BasaltColumnsFeatureMixin {

    // 完全禁用玄武岩柱子的生成
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void onGenerate(FeatureContext<BasaltColumnsFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
        // 直接返回true，表示生成"成功"，但实际上不生成任何东西
        cir.setReturnValue(true);
        cir.cancel();
    }
}