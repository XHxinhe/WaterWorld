package com.XHxinhe.waterworld.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 水世界模组的窗口标题修改
 * 在原版Minecraft窗口标题后添加水世界模组的信息
 */
@Mixin(MinecraftClient.class)
@Environment(EnvType.CLIENT)
public class MinecraftClientMixin {

    @Inject(
            method = "getWindowTitle",
            at = @At(value = "TAIL"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true
    )
    private void injectedGetWindowTitle(CallbackInfoReturnable<String> cir, StringBuilder stringBuilder) {
        // 在窗口标题后添加水世界模组信息
        stringBuilder.append(" - 探索无尽海洋 (Water World) by XHxinhe(欣訸睡不够）");

        // 设置返回值为修改后的标题
        cir.setReturnValue(stringBuilder.toString());
    }
}