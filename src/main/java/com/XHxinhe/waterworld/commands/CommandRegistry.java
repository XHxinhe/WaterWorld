package com.XHxinhe.waterworld.commands;

import com.XHxinhe.waterworld.processor.ChunkProcessor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class CommandRegistry {
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("waterworld")
                            .requires(source -> source.hasPermissionLevel(2))

                            // 其他现有命令...

                            // 新增命令: /waterworld fortress
                            // 功能: 在玩家当前位置生成地狱堡垒，并确保内部没有水
                            .then(CommandManager.literal("fortress")
                                    .executes(context -> generateFortress(context.getSource(), null, 0))
                                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                            .executes(context -> generateFortress(
                                                    context.getSource(),
                                                    BlockPosArgumentType.getBlockPos(context, "pos"),
                                                    0
                                            ))
                                            .then(CommandManager.argument("depth", IntegerArgumentType.integer(1, 50))
                                                    .executes(context -> generateFortress(
                                                            context.getSource(),
                                                            BlockPosArgumentType.getBlockPos(context, "pos"),
                                                            IntegerArgumentType.getInteger(context, "depth")
                                                    ))
                                            )
                                    )
                                    .then(CommandManager.argument("depth", IntegerArgumentType.integer(1, 50))
                                            .executes(context -> generateFortress(
                                                    context.getSource(),
                                                    null,
                                                    IntegerArgumentType.getInteger(context, "depth")
                                            ))
                                    )
                            )
            );
        });
    }

    // 其他现有方法...

    /**
     * 生成地狱堡垒并确保内部没有水
     * @param source 命令源
     * @param pos 可选的生成位置，如果为null则使用玩家位置
     * @param depth 生成深度，0表示在当前位置生成，正数表示在水下多少格生成
     * @return 命令执行结果
     */
    private static int generateFortress(ServerCommandSource source, BlockPos pos, int depth) {
        try {
            ServerWorld world = source.getWorld();
            String dimensionId = world.getRegistryKey().getValue().toString();

            // 检查是否在地狱维度
            if (!dimensionId.contains("nether")) {
                source.sendFeedback(() -> Text.literal("此命令只能在地狱维度使用！"), false);
                return 0;
            }

            // 获取生成位置
            BlockPos genPos;
            if (pos != null) {
                genPos = pos;
            } else if (source.getEntity() instanceof ServerPlayerEntity) {
                // 修复：使用正确的方法获取BlockPos
                Vec3d sourcePos = source.getPosition();
                genPos = new BlockPos((int)sourcePos.x, (int)sourcePos.y, (int)sourcePos.z);
            } else {
                source.sendFeedback(() -> Text.literal("必须指定生成位置或由玩家执行此命令！"), false);
                return 0;
            }

            // 如果指定了深度，调整生成位置
            final int finalDepth = depth; // 使变量成为final
            if (depth > 0) {
                genPos = genPos.down(depth);
                source.sendFeedback(() -> Text.literal("将在水下 " + finalDepth + " 格处生成地狱堡垒"), false);
            }

            // 使用Minecraft的place命令生成堡垒
            String placeCommand = String.format("place structure minecraft:fortress %d %d %d",
                    genPos.getX(), genPos.getY(), genPos.getZ());

            // 执行生成命令
            source.getServer().getCommandManager().executeWithPrefix(source, placeCommand);

            // 为lambda表达式创建final变量
            final BlockPos finalGenPos = genPos;

            // 延迟一个tick后处理水方块
            source.getServer().execute(() -> {
                // 处理生成的堡垒内部的水方块
                replaceWaterInFortress(world, finalGenPos, 50); // 50格半径应该足够覆盖整个堡垒

                source.sendFeedback(() -> Text.literal("已生成地狱堡垒并移除内部的水方块！"), true);
            });

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("生成地狱堡垒时发生错误: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 替换指定区域内的水方块为空气，并特别处理烈焰人刷怪笼周围
     * @param world 世界
     * @param center 中心位置
     * @param radius 半径
     */
    private static void replaceWaterInFortress(ServerWorld world, BlockPos center, int radius) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int count = 0;
        int spawnerCount = 0;

        // 获取地狱堡垒的典型高度范围
        int minY = Math.max(center.getY() - 10, world.getBottomY());
        int maxY = Math.min(center.getY() + 30, world.getTopY());

        // 首先找到所有的刷怪笼
        List<BlockPos> spawners = new ArrayList<>();

        // 第一遍扫描：找出所有刷怪笼
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isOf(Blocks.SPAWNER)) {
                        // 在地狱堡垒中的刷怪笼通常是烈焰人刷怪笼
                        spawners.add(new BlockPos(pos)); // 保存刷怪笼位置
                        spawnerCount++;
                    }
                }
            }
        }

        final int finalSpawnerCount = spawnerCount; // 使变量成为final
        System.out.println("在地狱堡垒中找到 " + finalSpawnerCount + " 个刷怪笼");

        // 优先处理刷怪笼周围的水
        for (BlockPos spawnerPos : spawners) {
            clearWaterAroundSpawner(world, spawnerPos, 5);
        }

        // 第二遍扫描：清除其余堡垒内部的水方块
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    // 检查是否是水方块
                    if (state.isOf(Blocks.WATER)) {
                        // 检查是否在堡垒内部
                        if (isNearNetherBricks(world, pos)) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                            count++;
                        }
                    }
                }
            }
        }

        final int finalCount = count; // 使变量成为final
        System.out.println("已替换地狱堡垒内的 " + finalCount + " 个水方块为空气");
    }

    /**
     * 清除刷怪笼周围指定半径内的所有水方块
     * @param world 世界
     * @param spawnerPos 刷怪笼位置
     * @param radius 清除半径
     */
    private static void clearWaterAroundSpawner(ServerWorld world, BlockPos spawnerPos, int radius) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int waterBlocksCleared = 0;

        // 遍历刷怪笼周围的所有方块
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // 计算与刷怪笼的距离
                    double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

                    if (distance <= radius) {
                        pos.set(spawnerPos.getX() + dx, spawnerPos.getY() + dy, spawnerPos.getZ() + dz);
                        BlockState state = world.getBlockState(pos);

                        // 替换水方块为空气
                        if (state.isOf(Blocks.WATER)) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
                            waterBlocksCleared++;
                        }
                    }
                }
            }
        }

        final int finalWaterBlocksCleared = waterBlocksCleared; // 使变量成为final
        final BlockPos finalSpawnerPos = spawnerPos; // 使变量成为final
        System.out.println("已清除刷怪笼(" + finalSpawnerPos.getX() + "," + finalSpawnerPos.getY() + "," + finalSpawnerPos.getZ() +
                ")周围 " + finalWaterBlocksCleared + " 个水方块");

        // 额外确保刷怪笼下方有足够的空间生成烈焰人
        for (int y = 1; y <= 3; y++) {
            BlockPos below = spawnerPos.down(y);
            BlockState state = world.getBlockState(below);

            if (state.isOf(Blocks.WATER)) {
                world.setBlockState(below, Blocks.AIR.getDefaultState(), 2 | 16);
            }
        }
    }

    /**
     * 检查指定位置周围是否有地狱砖，用于判断是否是堡垒内部
     * @param world 世界
     * @param pos 位置
     * @return 是否靠近地狱砖
     */
    private static boolean isNearNetherBricks(ServerWorld world, BlockPos pos) {
        // 检查周围6个方向
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) { // 只检查相邻的6个方块
                        BlockPos checkPos = pos.add(dx, dy, dz);
                        BlockState state = world.getBlockState(checkPos);

                        if (state.isOf(Blocks.NETHER_BRICKS) ||
                                state.isOf(Blocks.NETHER_BRICK_FENCE) ||
                                state.isOf(Blocks.NETHER_BRICK_STAIRS) ||
                                state.isOf(Blocks.NETHER_BRICK_WALL)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}