package com.XHxinhe.waterworld.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public class WaterWorldUtils {
    // 获取不同维度的水位高度
    public static int getWaterLevelForDimension(String dimensionId) {
        if (dimensionId.contains("nether")) {
            return 70;
        } else if (dimensionId.contains("end")) {
            return 67;
        } else {
            return 60;
        }
    }

    // 判断是否应该保留方块
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

    // 判断是否为地形方块
    public static boolean isTerrainBlock(BlockState state) {
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
}