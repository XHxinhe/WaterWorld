package com.example.waterworld.mixin;

import com.example.waterworld.WaterWorldMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

    @Inject(
            method = "buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("TAIL")
    )
    private void floodTheWorld(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk, CallbackInfo ci) {
        String dimensionId = region.toServerWorld().getRegistryKey().getValue().toString();
        int waterLevel = WaterWorldMod.getWaterLevelForDimension(dimensionId);

        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        int topY = region.getTopY() - 1;
        int bottomY = region.getBottomY();
        boolean isOverworld = !dimensionId.contains("nether") && !dimensionId.contains("end");

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // 处理整个区块
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 确保-64层是基岩（仅主世界）
                if (isOverworld) {
                    mutablePos.set(chunkX + x, -64, chunkZ + z);
                    chunk.setBlockState(mutablePos, Blocks.BEDROCK.getDefaultState(), false);
                }

                // 1. 水面以下全部强制替换为水（除了-64层的基岩）
                for (int y = bottomY; y < waterLevel; y++) {
                    mutablePos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = chunk.getBlockState(mutablePos);

                    // 使用新的shouldPreserveBlock方法，传递位置参数
                    if (WaterWorldMod.shouldPreserveBlock(dimensionId, state)) {
                        continue;
                    }

                    // 无论是什么方块，都替换为水
                    chunk.setBlockState(mutablePos, Blocks.WATER.getDefaultState(), false);
                }

                // 2. 水面以上全部清空为空气
                for (int y = waterLevel; y <= topY; y++) {
                    mutablePos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = chunk.getBlockState(mutablePos);

                    // 使用新的shouldPreserveBlock方法，传递位置参数
                    if (WaterWorldMod.shouldPreserveBlock(dimensionId, state)) {
                        continue;
                    }

                    chunk.setBlockState(mutablePos, Blocks.AIR.getDefaultState(), false);
                }
            }
        }

        // 记录此区块已被处理
        WaterWorldMod.PROCESSED_CHUNKS.add(chunk.getPos());
    }
}