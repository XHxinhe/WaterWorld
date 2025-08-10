package com.XHxinhe.waterworld.processor;

import com.XHxinhe.waterworld.utils.WaterWorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkProcessor {
    public static final Set<ChunkPos> PROCESSED_CHUNKS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (!PROCESSED_CHUNKS.contains(pos) && hasNearbyPlayer(world, pos, 3)) {
            world.getServer().execute(() -> {
                try {
                    if (isOldChunk(chunk)) {
                        processChunkLightweight(world, chunk);
                        PROCESSED_CHUNKS.add(pos);
                    }
                } catch (Exception e) {
                    // 错误处理保留但不输出日志
                }
            });
        }
    }

    public static void processNearbyChunks(MinecraftServer server) {
        if (server == null) return;

        try {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = player.getServerWorld();
                ChunkPos playerChunk = new ChunkPos(player.getBlockPos());

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                        if (!PROCESSED_CHUNKS.contains(chunkPos) && world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                            WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
                            if (isOldChunk(chunk)) {
                                processChunkLightweight(world, chunk);
                                PROCESSED_CHUNKS.add(chunkPos);
                                return; // 每次只处理一个
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 错误处理保留但不输出日志
        }
    }

    private static boolean hasNearbyPlayer(ServerWorld world, ChunkPos chunkPos, int radius) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
            int dx = Math.abs(playerChunk.x - chunkPos.x);
            int dz = Math.abs(playerChunk.z - chunkPos.z);
            if (dx <= radius && dz <= radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOldChunk(WorldChunk chunk) {
        int nonWaterBlocks = 0;
        int waterLevel = WaterWorldUtils.getWaterLevelForDimension(chunk.getWorld().getRegistryKey().getValue().toString());
        String dimensionId = chunk.getWorld().getRegistryKey().getValue().toString();
        boolean isEnd = dimensionId.contains("end");

        for (int x = 0; x < 16 && nonWaterBlocks < 10; x += 4) {
            for (int z = 0; z < 16 && nonWaterBlocks < 10; z += 4) {
                BlockPos pos = new BlockPos(chunk.getPos().getStartX() + x, waterLevel - 5, chunk.getPos().getStartZ() + z);
                BlockState state = chunk.getBlockState(pos);
                if (!state.isOf(Blocks.WATER) && !state.isAir() &&
                        !(isEnd && state.isOf(Blocks.END_STONE)) &&
                        !WaterWorldUtils.shouldPreserveBlock(dimensionId, state)) {
                    nonWaterBlocks++;
                }
            }
        }
        return nonWaterBlocks >= 5;
    }

    public static void processChunkLightweight(ServerWorld world, WorldChunk chunk) {
        String dimensionId = world.getRegistryKey().getValue().toString();
        int waterLevel = WaterWorldUtils.getWaterLevelForDimension(dimensionId);
        boolean isNether = dimensionId.contains("nether");
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // 处理基岩层
        fixBedrockLayer(world, chunk);

        // 简单处理：水平面以下全部设为水，以上全部设为空气
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 处理水下区域
                for (int y = world.getBottomY(); y < waterLevel; y++) {
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = world.getBlockState(pos);
                    if (!WaterWorldUtils.shouldPreserveBlock(dimensionId, state)) {
                        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                    }
                }

                // 处理水面及以上区域
                for (int y = waterLevel; y < waterLevel + 5; y++) {
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                    }
                }
            }
        }

        // 处理地狱维度70层以上的方块
        if (isNether) {
            clearNetherAboveY70(world, chunk);
        }
    }

    private static void fixBedrockLayer(ServerWorld world, WorldChunk chunk) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        String dimensionId = world.getRegistryKey().getValue().toString();
        boolean isOverworld = !dimensionId.contains("nether") && !dimensionId.contains("end");

        if (isOverworld) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = -64; y <= -55; y++) {
                        pos.set(chunkX + x, y, chunkZ + z);
                        BlockState state = world.getBlockState(pos);
                        if (state.isOf(Blocks.BEDROCK)) {
                            world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                        }
                    }
                }
            }
        } else {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = -59; y <= -55; y++) {
                        pos.set(chunkX + x, y, chunkZ + z);
                        world.setBlockState(pos, Blocks.BEDROCK.getDefaultState(), 2 | 16);
                    }
                }
            }
        }
    }

    private static void clearNetherAboveY70(ServerWorld world, WorldChunk chunk) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 70; y < world.getTopY(); y++) {
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                    }
                }
            }
        }
    }
}