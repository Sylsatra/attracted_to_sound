package com.example.soundattract.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class ConfigHelper {


    private static final int CURRENT_SCHEMA_VERSION = 6;

    public static void register() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(SoundAttractMod.MOD_ID + "-common.toml");


        updateAndMigrateConfig(configPath);


        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SoundAttractConfig.COMMON_SPEC, SoundAttractMod.MOD_ID + "-common.toml");
    }

    private static void updateAndMigrateConfig(Path path) {
        SoundAttractMod.LOGGER.debug("Checking config file at {} for corrections and migrations.", path);
        final CommentedFileConfig configData = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();

        configData.load();


        migrateConfig(configData);



        if (!SoundAttractConfig.COMMON_SPEC.isCorrect(configData)) {
            SoundAttractMod.LOGGER.info("Configuration file is missing values. Correcting and saving...");
            SoundAttractConfig.COMMON_SPEC.correct(configData);
            configData.save();
            SoundAttractMod.LOGGER.info("Configuration file updated successfully.");
        } else {
            SoundAttractMod.LOGGER.debug("Configuration file is up to date.");
        }
        configData.close();
    }

    private static void migrateConfig(CommentedFileConfig config) {

        int configVersion = config.getOptionalInt("internal.configSchemaVersion").orElse(0);

        if (configVersion < CURRENT_SCHEMA_VERSION) {
            SoundAttractMod.LOGGER.info("Old config version ({}) detected. Migrating to version {}...", configVersion, CURRENT_SCHEMA_VERSION);


            if (configVersion < 1) {


            }
            if (configVersion < 3) {
                renameKey(config, "muffling.specialMobProfilesRaw", "profiles.specialMobProfilesRaw");
                renameKey(config, "muffling.specialPlayerProfilesRaw", "profiles.specialPlayerProfilesRaw");
            }

            if (configVersion < 6) {
                try {
                    final String wlPath = "Sounds White List.soundIdWhitelist";
                    java.util.List<Object> wl = config.get(wlPath);
                    if (wl == null) {
                        wl = new java.util.ArrayList<>();
                        config.set(wlPath, wl);
                    }
                    String gunAction = "pointblank:gun_action";
                    if (!wl.contains(gunAction)) {
                        wl.add(gunAction);
                        SoundAttractMod.LOGGER.info("Migration v6: appended '{}' to {}.", gunAction, wlPath);
                    }
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.warn("Migration v6: failed updating soundIdWhitelist", e);
                }

                try {
                    final String defPath = "sound_defaults.soundDefaults";
                    java.util.List<Object> defs = config.get(defPath);
                    if (defs == null) {
                        defs = new java.util.ArrayList<>();
                        config.set(defPath, defs);
                    }
                    String defEntry = "pointblank:gun_action;15;5";
                    if (!defs.contains(defEntry)) {
                        defs.add(defEntry);
                        SoundAttractMod.LOGGER.info("Migration v6: appended '{}' to {}.", defEntry, defPath);
                    }
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.warn("Migration v6: failed updating soundDefaults", e);
                }
            }

            config.set("internal.configSchemaVersion", CURRENT_SCHEMA_VERSION);
            SoundAttractMod.LOGGER.info("Config migration complete.");
        }
    }
    private static void renameKey(CommentedConfig config, String oldPath, String newPath) {
        if (config.contains(oldPath)) {
            Object value = config.get(oldPath);
            config.set(newPath, value);
            config.remove(oldPath);
            SoundAttractMod.LOGGER.debug("Migrated config key '{}' to '{}'.", oldPath, newPath);
        }
    }
}
