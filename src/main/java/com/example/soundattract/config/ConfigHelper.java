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


    private static final int CURRENT_SCHEMA_VERSION = 7;

    public static void register() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(SoundAttractMod.MOD_ID + "-common.toml");


        updateAndMigrateConfig(configPath);


        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SoundAttractConfig.COMMON_SPEC, SoundAttractMod.MOD_ID + "-common.toml");
    }
    private static void repairTruncatedListsAndEnsurePointBlank(CommentedFileConfig config) {
        boolean truncated = false;
        try {
            final String wlPath = "Sounds White List.soundIdWhitelist";
            java.util.List<Object> wl = config.get(wlPath);
            if (wl != null && wl.size() == 1 && "pointblank:gun_action".equals(String.valueOf(wl.get(0)))) {
                config.remove(wlPath);
                truncated = true;
                SoundAttractMod.LOGGER.info("Repair: detected truncated soundIdWhitelist; restoring defaults.");
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("Repair: failed checking soundIdWhitelist for truncation", e);
        }

        try {
            final String defPath = "sound_defaults.soundDefaults";
            java.util.List<Object> defs = config.get(defPath);
            if (defs != null && defs.size() == 1 && "pointblank:gun_action;15;5".equals(String.valueOf(defs.get(0)))) {
                config.remove(defPath);
                truncated = true;
                SoundAttractMod.LOGGER.info("Repair: detected truncated soundDefaults; restoring defaults.");
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("Repair: failed checking soundDefaults for truncation", e);
        }

        if (truncated) {
            try {
                SoundAttractConfig.COMMON_SPEC.correct(config);
                SoundAttractMod.LOGGER.info("Repair: defaults restored for truncated lists.");
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Repair: failed restoring defaults after truncation fix", e);
            }
        }

        try {
            final String wlPath = "Sounds White List.soundIdWhitelist";
            java.util.List<Object> wl = config.get(wlPath);
            if (wl == null) {
                wl = new java.util.ArrayList<>();
                config.set(wlPath, wl);
            }
            String pbWl = "pointblank:gun_action";
            if (!wl.contains(pbWl)) {
                wl.add(pbWl);
                SoundAttractMod.LOGGER.info("Repair: appended '{}' to {}.", pbWl, wlPath);
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("Repair: failed ensuring pointblank in soundIdWhitelist", e);
        }

        try {
            final String defPath = "sound_defaults.soundDefaults";
            java.util.List<Object> defs = config.get(defPath);
            if (defs == null) {
                defs = new java.util.ArrayList<>();
                config.set(defPath, defs);
            }
            String pbDef = "pointblank:gun_action;15;5";
            if (!defs.contains(pbDef)) {
                defs.add(pbDef);
                SoundAttractMod.LOGGER.info("Repair: appended '{}' to {}.", pbDef, defPath);
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("Repair: failed ensuring pointblank in soundDefaults", e);
        }
    }

    private static void updateAndMigrateConfig(Path path) {
        SoundAttractMod.LOGGER.debug("Checking config file at {} for corrections and migrations.", path);
        final CommentedFileConfig configData = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();

        configData.load();

        int originalVersion = configData.getOptionalInt("internal.configSchemaVersion").orElse(0);
        if (!SoundAttractConfig.COMMON_SPEC.isCorrect(configData)) {
            SoundAttractMod.LOGGER.info("Configuration file is missing values. Correcting and saving...");
            SoundAttractConfig.COMMON_SPEC.correct(configData);
            configData.save();
            SoundAttractMod.LOGGER.info("Configuration file updated successfully.");
        } else {
            SoundAttractMod.LOGGER.debug("Configuration file is up to date.");
        }
        repairTruncatedListsAndEnsurePointBlank(configData);
        migrateConfig(configData, originalVersion);

        configData.close();
    }

    private static void migrateConfig(CommentedFileConfig config, int originalVersion) {
        if (originalVersion < CURRENT_SCHEMA_VERSION) {
            SoundAttractMod.LOGGER.info("Old config version ({}) detected. Migrating to version {}...", originalVersion, CURRENT_SCHEMA_VERSION);

            if (originalVersion < 1) {
            }
            if (originalVersion < 3) {
                renameKey(config, "muffling.specialMobProfilesRaw", "profiles.specialMobProfilesRaw");
                renameKey(config, "muffling.specialPlayerProfilesRaw", "profiles.specialPlayerProfilesRaw");
            }

            if (originalVersion < 6) {
                boolean correctedTruncatedLists = false;
                try {
                    final String wlPath = "Sounds White List.soundIdWhitelist";
                    java.util.List<Object> wl = config.get(wlPath);
                    if (wl != null && wl.size() == 1 && "pointblank:gun_action".equals(String.valueOf(wl.get(0)))) {
                        config.remove(wlPath);
                        correctedTruncatedLists = true;
                        SoundAttractMod.LOGGER.info("Migration v6: detected truncated soundIdWhitelist from v4.1.3; restoring defaults.");
                    }
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.warn("Migration v6: failed checking soundIdWhitelist for truncation", e);
                }

                try {
                    final String defPath = "sound_defaults.soundDefaults";
                    java.util.List<Object> defs = config.get(defPath);
                    if (defs != null && defs.size() == 1 && "pointblank:gun_action;15;5".equals(String.valueOf(defs.get(0)))) {
                        config.remove(defPath);
                        correctedTruncatedLists = true;
                        SoundAttractMod.LOGGER.info("Migration v6: detected truncated soundDefaults from v4.1.3; restoring defaults.");
                    }
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.warn("Migration v6: failed checking soundDefaults for truncation", e);
                }

                if (correctedTruncatedLists) {
                    try {
                        SoundAttractConfig.COMMON_SPEC.correct(config);
                        SoundAttractMod.LOGGER.info("Migration v6: defaults restored for truncated lists.");
                    } catch (Exception e) {
                        SoundAttractMod.LOGGER.warn("Migration v6: failed restoring defaults after truncation fix", e);
                    }
                }

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
