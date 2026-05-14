package com.example.soundattract.config;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.separate.*;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigHelper {

    public static void register(ForgeConfigRegistry registry) {
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, GeneralConfig.SPEC, "soundattract/general.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, StealthConfig.SPEC, "soundattract/stealth.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, GunsConfig.SPEC, "soundattract/guns.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, VoiceConfig.SPEC, "soundattract/voice.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, IntegrationConfig.SPEC, "soundattract/integration.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, com.example.soundattract.config.separate.ScentConfig.SPEC, "soundattract/scent.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, PerformanceConfig.SPEC, "soundattract/performance.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, RaidConfig.SPEC, "soundattract/raid.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, PathfindingConfig.SPEC, "soundattract/pathfinding.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.SERVER, SoundAttractConfig.SERVER_SPEC, "soundattract/server-rules.toml");

        Path oldConfigPath = FabricLoader.getInstance().getConfigDir().resolve(Soundattract.MOD_ID + "-common.toml");
        if (Files.exists(oldConfigPath)) {
            migrateOldConfig(oldConfigPath);
        }
    }

    private static void migrateOldConfig(Path oldPath) {
        Soundattract.LOGGER.info("Old configuration file found at {}. Skipping migration for now.", oldPath);
    }
}
