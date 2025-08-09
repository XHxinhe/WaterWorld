package com.example.waterworld.commands;

import com.example.waterworld.processor.ChunkProcessor;
import com.example.waterworld.utils.WaterWorldUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class CommandRegistry {
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("waterworld")
                            .requires(source -> source.hasPermissionLevel(2))

                            .then(CommandManager.literal("status")
                                    .executes(context -> {
                                        return 1;
                                    })
                            )

                            .then(CommandManager.literal("clear")
                                    .executes(context -> {
                                        ChunkProcessor.PROCESSED_CHUNKS.clear();
                                        return 1;
                                    })
                            )

                            .then(CommandManager.literal("force")
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                        ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
                                        ServerWorld playerWorld = player.getServerWorld();

                                        // 强制处理玩家周围5x5区块
                                        for (int dx = -2; dx <= 2; dx++) {
                                            for (int dz = -2; dz <= 2; dz++) {
                                                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                                                if (playerWorld.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                                                    WorldChunk chunk = playerWorld.getChunk(chunkPos.x, chunkPos.z);
                                                    ChunkProcessor.fixBedrockLayer(playerWorld, chunk);
                                                    ChunkProcessor.thoroughFillChunk(playerWorld, chunk,
                                                            WaterWorldUtils.getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString()));
                                                    ChunkProcessor.clearAboveWaterLevel(playerWorld, chunk,
                                                            WaterWorldUtils.getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString()));
                                                    ChunkProcessor.clearWaterSurfaceBlocks(playerWorld, chunk,
                                                            WaterWorldUtils.getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString()));
                                                    ChunkProcessor.PROCESSED_CHUNKS.add(chunkPos);
                                                }
                                            }
                                        }
                                        return 1;
                                    })
                            )

                            .then(CommandManager.literal("ultradeepfill")
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                        ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
                                        ServerWorld playerWorld = player.getServerWorld();
                                        int waterLevel = WaterWorldUtils.getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString());

                                        // 超强力填充玩家周围7x7区块
                                        for (int dx = -3; dx <= 3; dx++) {
                                            for (int dz = -3; dz <= 3; dz++) {
                                                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                                                if (playerWorld.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                                                    WorldChunk chunk = playerWorld.getChunk(chunkPos.x, chunkPos.z);
                                                    ChunkProcessor.ultraDeepFill(playerWorld, chunk, waterLevel);
                                                }
                                            }
                                        }
                                        return 1;
                                    })
                            )

                            .then(CommandManager.literal("fixspot")
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                        ServerWorld world = player.getServerWorld();
                                        int waterLevel = WaterWorldUtils.getWaterLevelForDimension(world.getRegistryKey().getValue().toString());
                                        String dimensionId = world.getRegistryKey().getValue().toString();
                                        boolean isEnd = dimensionId.contains("end");

                                        // 获取玩家指向的方块位置
                                        BlockPos pos = player.getBlockPos().down();

                                        // 修复玩家位置周围的13x13x13方块
                                        for (int dx = -6; dx <= 6; dx++) {
                                            for (int dz = -6; dz <= 6; dz++) {
                                                for (int dy = -6; dy <= 6; dy++) {
                                                    BlockPos currentPos = pos.add(dx, dy, dz);
                                                    if (currentPos.getY() < waterLevel && currentPos.getY() > -55) {
                                                        BlockState state = world.getBlockState(currentPos);
                                                        if ((isEnd && state.isOf(Blocks.END_STONE)) ||
                                                                (!WaterWorldUtils.shouldPreserveBlock(dimensionId, state) &&
                                                                        (state.isAir() || state.isOf(Blocks.LAVA) ||
                                                                                (!state.isOf(Blocks.WATER) && state.getFluidState().isEmpty())))) {
                                                            world.setBlockState(currentPos, Blocks.WATER.getDefaultState(), 2 | 16);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        return 1;
                                    })
                            )

                            // 修复基岩层
                            .then(CommandManager.literal("fixbedrock")
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                        ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
                                        ServerWorld playerWorld = player.getServerWorld();

                                        // 修复玩家周围5x5区块的基岩层
                                        for (int dx = -2; dx <= 2; dx++) {
                                            for (int dz = -2; dz <= 2; dz++) {
                                                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                                                if (playerWorld.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                                                    WorldChunk chunk = playerWorld.getChunk(chunkPos.x, chunkPos.z);
                                                    ChunkProcessor.fixBedrockLayer(playerWorld, chunk);
                                                }
                                            }
                                        }
                                        return 1;
                                    })
                            )
            );
        });
    }
}