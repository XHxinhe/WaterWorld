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

        final ChunkPos finalPos = pos;
        final WorldChunk finalChunk = chunk;
        final ServerWorld finalWorld = world;

        if (!PROCESSED_CHUNKS.contains(pos) && hasNearbyPlayer(world, pos, 3)) {
            world.getServer().execute(() -> {
                try {
                    if (isOldChunk(finalChunk)) {
                        processChunkLightweight(finalWorld, finalChunk);
                        PROCESSED_CHUNKS.add(finalPos);
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
                // 在末地，末地石也应该被计入非水方块
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

        // 处理基岩层
        fixBedrockLayer(world, chunk);

        // 填充水下区域
        thoroughFillChunk(world, chunk, waterLevel);

        // 清理水面上的区域
        clearAboveWaterLevel(world, chunk, waterLevel);

        // 清理水面上的睡莲、冰块等
        clearWaterSurfaceBlocks(world, chunk, waterLevel);
    }

    public static void fixBedrockLayer(ServerWorld world, WorldChunk chunk) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        String dimensionId = world.getRegistryKey().getValue().toString();
        boolean isOverworld = !dimensionId.contains("nether") && !dimensionId.contains("end");

        // 如果是主世界，将基岩层替换为水
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
            // 其他维度保持基岩层不变
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

    public static void clearWaterSurfaceBlocks(ServerWorld world, WorldChunk chunk, int waterLevel) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // 清理水面上的方块
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 检查水面上的方块
                pos.set(chunkX + x, waterLevel, chunkZ + z);
                BlockState state = world.getBlockState(pos);

                // 移除睡莲、冰块等
                if (!state.isOf(Blocks.AIR) && !state.isOf(Blocks.WATER)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                }

                // 确保水面是水
                pos.set(chunkX + x, waterLevel - 1, chunkZ + z);
                state = world.getBlockState(pos);
                if (!state.isOf(Blocks.WATER)) {
                    world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                }
            }
        }
    }

    public static void thoroughFillChunk(ServerWorld world, WorldChunk chunk, int waterLevel) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        String dimensionId = world.getRegistryKey().getValue().toString();
        boolean isEnd = dimensionId.contains("end");
        boolean isOverworld = !dimensionId.contains("nether") && !dimensionId.contains("end");
        int bedrockTopY = isOverworld ? -64 : -55; // 主世界从最底层开始填充

        // 第一步：先用石头填充所有非固体方块
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bedrockTopY + 1; y < waterLevel; y++) { // 从基岩层上方开始
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    // 在末地，末地石也应该被替换
                    if ((isEnd && state.isOf(Blocks.END_STONE)) ||
                            (!WaterWorldUtils.shouldPreserveBlock(dimensionId, state) &&
                                    (state.isAir() || state.getFluidState().isStill() || !state.isSolid()))) {
                        world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2 | 16);
                    }
                }
            }
        }

        // 第二步：将所有石头和普通方块转换为水
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bedrockTopY + 1; y < waterLevel; y++) { // 从基岩层上方开始
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    // 在末地，末地石也应该被替换为水
                    if ((isEnd && state.isOf(Blocks.END_STONE)) ||
                            (!WaterWorldUtils.shouldPreserveBlock(dimensionId, state) && !state.isOf(Blocks.WATER))) {
                        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                    }
                }
            }
        }

        // 第三步：确保没有空气泡
        for (int i = 0; i < 3; i++) {
            boolean foundAir = false;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = bedrockTopY + 1; y < waterLevel; y++) { // 从基岩层上方开始
                        pos.set(chunkX + x, y, chunkZ + z);
                        BlockState state = world.getBlockState(pos);

                        if (state.isAir() || state.isOf(Blocks.LAVA) || (isEnd && state.isOf(Blocks.END_STONE))) {
                            world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                            foundAir = true;
                        }
                    }
                }
            }

            if (!foundAir) break;
        }
    }

    public static void ultraDeepFill(ServerWorld world, WorldChunk chunk, int waterLevel) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        String dimensionId = world.getRegistryKey().getValue().toString();
        boolean isOverworld = !dimensionId.contains("nether") && !dimensionId.contains("end");
        int bedrockTopY = isOverworld ? -64 : -55; // 主世界从最底层开始填充
        boolean isEnd = dimensionId.contains("end");

        // 先修复基岩层
        fixBedrockLayer(world, chunk);

        // 将水下所有内容替换为固体石头
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bedrockTopY + 1; y < waterLevel; y++) { // 从基岩层上方开始
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = world.getBlockState(pos);

                    if (!WaterWorldUtils.shouldPreserveBlock(dimensionId, state)) {
                        world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2 | 16);
                    }
                }
            }
        }

        // 再将石头全部替换为水，除了保留方块
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bedrockTopY + 1; y < waterLevel; y++) { // 从基岩层上方开始
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState state = world.getBlockState(pos);

                    // 在末地，末地石也应该被替换为水
                    if ((isEnd && state.isOf(Blocks.END_STONE)) ||
                            !WaterWorldUtils.shouldPreserveBlock(dimensionId, state)) {
                        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                    }
                }
            }
        }

        // 清理水面上的睡莲、冰块等
        clearWaterSurfaceBlocks(world, chunk, waterLevel);
    }

    public static void clearAboveWaterLevel(ServerWorld world, WorldChunk chunk, int waterLevel) {
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        int topY = Math.min(world.getTopY(), waterLevel + 30);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        String dimensionId = world.getRegistryKey().getValue().toString();
        boolean isEnd = dimensionId.contains("end");

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = waterLevel; y < topY; y++) {
                    pos.set(chunkX + x, y, chunkZ + z);
                    BlockState currentState = chunk.getBlockState(pos);

                    // 在末地，末地石也应该被清除
                    if ((isEnd && currentState.isOf(Blocks.END_STONE)) ||
                            (!WaterWorldUtils.shouldPreserveBlock(dimensionId, currentState) &&
                                    WaterWorldUtils.isTerrainBlock(currentState))) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                    }
                }
            }
        }
    }
}