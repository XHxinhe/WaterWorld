package com.XHxinhe.waterworld.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 水世界模组的主菜单文本添加
 * 在主菜单左下角添加模组信息，位于原版版本文本上方
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    // 引用原版淡入动画相关字段
    @Final
    @Shadow
    private boolean doBackgroundFade;

    @Shadow
    private long backgroundFadeStart;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            at = @At("TAIL"),
            method = "render"
    )
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 自定义文本内容
        String customText = "只有水的世界生存 - 探索无尽海洋";

        // 计算与原版动画同步的淡入效果
        float f = this.doBackgroundFade ? (float)(Util.getMeasuringTimeMs() - this.backgroundFadeStart) / 1000.0F : 1.0F;
        float g = this.doBackgroundFade ? MathHelper.clamp(f - 1.0F, 0.0F, 1.0F) : 1.0F;
        int l = MathHelper.ceil(g * 255.0F) << 24;

        // 绘制自定义文本，位置在原版版本文本上方
        context.drawTextWithShadow(
                this.textRenderer,
                customText,
                2,  // x坐标，左对齐
                this.height - 20,  // y坐标，在原版文本上方
                16777215 | l  // 白色 + 动态透明度
        );
    }
}