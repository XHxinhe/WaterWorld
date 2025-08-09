package com.example.waterworld;

import com.example.waterworld.commands.CommandRegistry;
import com.example.waterworld.processor.ChunkProcessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

public class WaterWorldMod implements ModInitializer {
    public static final String MOD_ID = "waterworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private MinecraftServer serverInstance = null;
    private Timer nearbyProcessor = null;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing WaterWorld Mod");

        // 注册事件监听器
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerChunkEvents.CHUNK_LOAD.register(ChunkProcessor::onChunkLoad);

        // 注册命令
        CommandRegistry.registerCommands();
    }

    private void onServerStarted(MinecraftServer server) {
        this.serverInstance = server;

        this.nearbyProcessor = new Timer("WaterWorld-NearbyProcessor", true);
        this.nearbyProcessor.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (serverInstance != null) {
                    serverInstance.execute(() -> ChunkProcessor.processNearbyChunks(serverInstance));
                }
            }
        }, 10000, 30000);
    }
}