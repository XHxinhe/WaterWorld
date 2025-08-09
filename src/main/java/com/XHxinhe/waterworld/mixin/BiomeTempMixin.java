package com.XHxinhe.waterworld.mixin;

import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeTempMixin {

    // 修改生物群系温度检查，使其永远不会结冰
    @Inject(method = "getTemperature", at = @At("RETURN"), cancellable = true)
    private void onGetTemperature(CallbackInfoReturnable<Float> cir) {
        // 返回一个高于0.15的温度，这样水就不会结冰
        cir.setReturnValue(Math.max(0.2f, cir.getReturnValue()));
    }
}