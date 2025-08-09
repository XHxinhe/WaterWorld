package com.XHxinhe.waterworld.mixin;

import net.minecraft.world.gen.carver.CarverConfig;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredCarver.class)
public class ConfiguredCarverMixin<C extends CarverConfig> {
    @Inject(method = "carve", at = @At("HEAD"), cancellable = true)
    private void disableAllCaves(CallbackInfoReturnable<Boolean> cir) {
        // 直接禁用所有洞穴生成
        cir.setReturnValue(false);
    }
}