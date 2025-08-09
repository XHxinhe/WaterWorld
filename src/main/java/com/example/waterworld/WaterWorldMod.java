package com.example.waterworld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaterWorldMod implements ModInitializer {
    public static final String MOD_ID = "waterworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Set<ChunkPos> PROCESSED_CHUNKS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MinecraftServer serverInstance = null;
    private Timer nearbyProcessor = null;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
        registerCommands();
    }

    private void onServerStarted(MinecraftServer server) {
        this.serverInstance = server;

        this.nearbyProcessor = new Timer("WaterWorld-NearbyProcessor", true);
        this.nearbyProcessor.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (serverInstance != null) {
                    serverInstance.execute(() -> processNearbyChunks());
                }
            }
        }, 10000, 30000);
    }

    private void onChunkLoad(ServerWorld world, WorldChunk chunk) {
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

    private void processNearbyChunks() {
        if (this.serverInstance == null) return;

        try {
            for (ServerPlayerEntity player : this.serverInstance.getPlayerManager().getPlayerList()) {
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

    private boolean hasNearbyPlayer(ServerWorld world, ChunkPos chunkPos, int radius) {
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

    private boolean isOldChunk(WorldChunk chunk) {
        int nonWaterBlocks = 0;
        int waterLevel = getWaterLevelForDimension(chunk.getWorld().getRegistryKey().getValue().toString());
        String dimensionId = chunk.getWorld().getRegistryKey().getValue().toString();
        boolean isEnd = dimensionId.contains("end");

        for (int x = 0; x < 16 && nonWaterBlocks < 10; x += 4) {
            for (int z = 0; z < 16 && nonWaterBlocks < 10; z += 4) {
                BlockPos pos = new BlockPos(chunk.getPos().getStartX() + x, waterLevel - 5, chunk.getPos().getStartZ() + z);
                BlockState state = chunk.getBlockState(pos);
                // 在末地，末地石也应该被计入非水方块
                if (!state.isOf(Blocks.WATER) && !state.isAir() &&
                        !(isEnd && state.isOf(Blocks.END_STONE)) &&
                        !shouldPreserveBlock(dimensionId, state)) {
                    nonWaterBlocks++;
                }
            }
        }
        return nonWaterBlocks >= 5;
    }

    private static void processChunkLightweight(ServerWorld world, WorldChunk chunk) {
        String dimensionId = world.getRegistryKey().getValue().toString();
        int waterLevel = getWaterLevelForDimension(dimensionId);

        // 处理基岩层
        fixBedrockLayer(world, chunk);

        // 填充水下区域
        thoroughFillChunk(world, chunk, waterLevel);

        // 清理水面上的区域
        clearAboveWaterLevel(world, chunk, waterLevel);

        // 清理水面上的睡莲、冰块等
        clearWaterSurfaceBlocks(world, chunk, waterLevel);
    }

    // 修改后的方法：将基岩也替换为水（主世界）
    private static void fixBedrockLayer(ServerWorld world, WorldChunk chunk) {
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

    // 清理水面上的睡莲、冰块等方块
    private static void clearWaterSurfaceBlocks(ServerWorld world, WorldChunk chunk, int waterLevel) {
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

    private static void thoroughFillChunk(ServerWorld world, WorldChunk chunk, int waterLevel) {
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
                            (!shouldPreserveBlock(dimensionId, state) &&
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
                            (!shouldPreserveBlock(dimensionId, state) && !state.isOf(Blocks.WATER))) {
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

    private static void ultraDeepFill(ServerWorld world, WorldChunk chunk, int waterLevel) {
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

                    if (!shouldPreserveBlock(dimensionId, state)) {
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
                            !shouldPreserveBlock(dimensionId, state)) {
                        world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2 | 16);
                    }
                }
            }
        }

        // 清理水面上的睡莲、冰块等
        clearWaterSurfaceBlocks(world, chunk, waterLevel);
    }

    private static void clearAboveWaterLevel(ServerWorld world, WorldChunk chunk, int waterLevel) {
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
                            (!shouldPreserveBlock(dimensionId, currentState) && isTerrainBlock(currentState))) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                    }
                }
            }
        }
    }

    private static boolean isTerrainBlock(BlockState state) {
        return state.isOf(Blocks.STONE) || state.isOf(Blocks.DIRT) ||
                state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.SAND) ||
                state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.COBBLESTONE) ||
                state.isOf(Blocks.LAVA) || state.isOf(Blocks.DEEPSLATE) ||
                state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.GRANITE) ||
                state.isOf(Blocks.DIORITE) || state.isOf(Blocks.CLAY) ||
                state.isOf(Blocks.TUFF) || state.isOf(Blocks.CALCITE) ||
                state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) ||
                state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.LILY_PAD) ||
                state.isOf(Blocks.END_STONE);
    }

    private void registerCommands() {
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
                                        PROCESSED_CHUNKS.clear();
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
                                                    fixBedrockLayer(playerWorld, chunk);
                                                    thoroughFillChunk(playerWorld, chunk, getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString()));
                                                    clearAboveWaterLevel(playerWorld, chunk, getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString()));
                                                    clearWaterSurfaceBlocks(playerWorld, chunk, getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString()));
                                                    PROCESSED_CHUNKS.add(chunkPos);
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
                                        int waterLevel = getWaterLevelForDimension(playerWorld.getRegistryKey().getValue().toString());

                                        // 超强力填充玩家周围7x7区块
                                        for (int dx = -3; dx <= 3; dx++) {
                                            for (int dz = -3; dz <= 3; dz++) {
                                                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                                                if (playerWorld.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                                                    WorldChunk chunk = playerWorld.getChunk(chunkPos.x, chunkPos.z);
                                                    ultraDeepFill(playerWorld, chunk, waterLevel);
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
                                        int waterLevel = getWaterLevelForDimension(world.getRegistryKey().getValue().toString());
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
                                                                (!shouldPreserveBlock(dimensionId, state) &&
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
                                                    fixBedrockLayer(playerWorld, chunk);
                                                }
                                            }
                                        }
                                        return 1;
                                    })
                            )
            );
        });
    }

    // 供 Mixin 使用的静态方法
    public static int getWaterLevelForDimension(String dimensionId) {
        if (dimensionId.contains("nether")) {
            return 70;
        } else if (dimensionId.contains("end")) {
            return 67;
        } else {
            return 60;
        }
    }

    // 修改后的方法：主世界不再保留基岩
    public static boolean shouldPreserveBlock(String dimensionId, BlockState state) {
        boolean isOverworld = !dimensionId.contains("nether") && !dimensionId.contains("end");

        // 主世界不保留基岩
        if (state.isOf(Blocks.BEDROCK) && isOverworld) {
            return false;
        }

        // 其他维度保留基岩
        if (state.isOf(Blocks.BEDROCK) && !isOverworld) {
            return true;
        }

        if (state.isOf(Blocks.SPAWNER) || state.isOf(Blocks.CHEST) ||
                state.isOf(Blocks.END_PORTAL_FRAME) || state.isOf(Blocks.END_PORTAL)) {
            return true;
        }

        if (dimensionId.contains("end")) {
            // 不再保留末地石，只保留黑曜石和传送门
            return state.isOf(Blocks.OBSIDIAN);
        }

        if (dimensionId.contains("nether")) {
            return state.isOf(Blocks.NETHER_BRICKS) ||
                    state.isOf(Blocks.NETHER_BRICK_FENCE) ||
                    state.isOf(Blocks.SOUL_SAND);
        }

        return false;
    }
}