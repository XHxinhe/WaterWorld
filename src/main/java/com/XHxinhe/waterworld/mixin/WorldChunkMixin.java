package com.XHxinhe.waterworld.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public class WorldChunkMixin {

    @Shadow @Final private World world;

    @Inject(method = "setLoadedToWorld", at = @At("RETURN"))
    private void onChunkLoadToWorld(boolean loaded, CallbackInfo ci) {
        // 只有在服务器端且区块被加载时才处理
        if (loaded && world instanceof ServerWorld) {
            // 这里不做具体处理，让 Fabric API 事件处理
            // 这个 Mixin 主要是作为备份，确保区块被正确标记为已加载
        }
    }
}