package com.example.soundattract.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import com.example.soundattract.SoundAttractMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

public class SoundAttractConfig {

    private static final int CURRENT_CONFIG_VERSION = 7;

    public record SoundDefaultEntry(double range, double weight) {

    }
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        final org.apache.commons.lang3.tuple.Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = specPair.getLeft();
        COMMON_SPEC = specPair.getRight();
    }

    public static Set<Identifier> SOUND_ID_WHITELIST_CACHE = new HashSet<>();
    public static Map<Identifier, SoundDefaultEntry> SOUND_DEFAULT_ENTRIES_CACHE = new HashMap<>();
    public static Set<Identifier> CUSTOM_LIQUID_BLOCKS_CACHE = new HashSet<>();
    public static Set<Identifier> CUSTOM_WOOL_BLOCKS_CACHE = new HashSet<>();
    public static Set<Identifier> CUSTOM_SOLID_BLOCKS_CACHE = new HashSet<>();
    public static Set<Identifier> CUSTOM_NON_SOLID_BLOCKS_CACHE = new HashSet<>();
    public static Set<Identifier> CUSTOM_THIN_BLOCKS_CACHE = new HashSet<>();
    public static Set<Identifier> CUSTOM_AIR_BLOCKS_CACHE = new HashSet<>();
    public static Set<Identifier> NON_BLOCKING_VISION_ALLOW_CACHE = new HashSet<>();
    public static double TACZ_RELOAD_RANGE_CACHE = 10.0;
    public static double TACZ_RELOAD_WEIGHT_CACHE = 1.0;
    public static double TACZ_SHOOT_RANGE_CACHE = 140.0;
    public static double TACZ_SHOOT_WEIGHT_CACHE = 15;
    public static boolean TACZ_ENABLED_CACHE = false;
    public static final Map<Identifier, Pair<Double, Double>> TACZ_GUN_SHOOT_DB_CACHE = new HashMap<>();
    public static final Map<Identifier, Pair<Double, Double>> TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new HashMap<>();
    public static final Map<String, Double> TACZ_MUZZLE_FLASH_REDUCTION_CACHE = new HashMap<>();
    public static boolean POINT_BLANK_ENABLED_CACHE = false;
    public static double POINT_BLANK_RELOAD_RANGE_CACHE = 10.0;
    public static double POINT_BLANK_RELOAD_WEIGHT_CACHE = 1.0;
    public static double POINT_BLANK_SHOOT_RANGE_CACHE = 140.0;
    public static double POINT_BLANK_SHOOT_WEIGHT_CACHE = 15.0;
    public static final Map<Identifier, Double> POINT_BLANK_ATTACHMENT_REDUCTION_CACHE = new HashMap<>();
    public static final Map<Identifier, Double> POINT_BLANK_GUN_RANGE_CACHE = new HashMap<>();
    public static final Map<String, Double> POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE = new HashMap<>();
    public static List<com.example.soundattract.config.MobProfile> SPECIAL_MOB_PROFILES_CACHE = Collections.emptyList();
    public static List<com.example.soundattract.config.PlayerProfile> SPECIAL_PLAYER_PROFILES_CACHE = Collections.emptyList();

    private static <T> void moveConfigValue(com.electronwill.nightconfig.core.UnmodifiableConfig config, String oldPath, ModConfigSpec.ConfigValue<T> newValue) {
        if (config.contains(oldPath)) {
            T oldValue = config.get(oldPath);
            newValue.set(oldValue);
            SoundAttractMod.LOGGER.info("Migrated config value from '{}' to '{}'", oldPath, String.join(".", newValue.getPath()));
        }
    }

    private static void migrateConfig(int oldVersion) {
        if (oldVersion >= CURRENT_CONFIG_VERSION) {
            return;
        }

        SoundAttractMod.LOGGER.info("Migrating SoundAttract config from version {} to {}", oldVersion, CURRENT_CONFIG_VERSION);
        com.electronwill.nightconfig.core.UnmodifiableConfig config = COMMON_SPEC.getValues();

        if (oldVersion < 1) {
            SoundAttractMod.LOGGER.info("Performing migration for config version 0 -> 1");

            moveConfigValue(config, "general.scanCooldownTicks", COMMON.scanCooldownTicks);
            moveConfigValue(config, "general.minTpsForScanCooldown", COMMON.minTpsForScanCooldown);
            moveConfigValue(config, "general.maxTpsForScanCooldown", COMMON.maxTpsForScanCooldown);
            moveConfigValue(config, "general.raycastCacheTtlTicks", COMMON.raycastCacheTtlTicks);
            moveConfigValue(config, "general.raycastCacheMaxEntries", COMMON.raycastCacheMaxEntries);
            moveConfigValue(config, "general.raycastCacheCleanupIntervalTicks", COMMON.raycastCacheCleanupIntervalTicks);
        }

        if (oldVersion < 2) {
            SoundAttractMod.LOGGER.info("Performing migration for config version 1 -> 2");

            moveConfigValue(config, "vision.visionPassThroughBlocks", COMMON.nonBlockingVisionAllowList);
            moveConfigValue(config, "muffling.specialMobProfilesRaw", COMMON.specialMobProfilesRaw);
            moveConfigValue(config, "muffling.specialPlayerProfilesRaw", COMMON.specialPlayerProfilesRaw);

        }

        if (oldVersion < 3) {
            SoundAttractMod.LOGGER.info("Performing migration for config version 2 -> 3");
            try {
                Object edgeSectorsObj = config.get("general.numEdgeSectors");
                if (edgeSectorsObj instanceof Number && ((Number) edgeSectorsObj).intValue() == 8) {
                    COMMON.numEdgeSectors.set(4);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v3: Could not read/write general.numEdgeSectors: {}", e.toString());
            }
            try {
                Object edgePerSectorObj = config.get("general.edgeMobsPerSector");
                if (edgePerSectorObj instanceof Number && ((Number) edgePerSectorObj).intValue() == 4) {
                    COMMON.edgeMobsPerSector.set(1);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v3: Could not read/write general.edgeMobsPerSector: {}", e.toString());
            }
        }

        if (oldVersion < 4) {
            SoundAttractMod.LOGGER.info("Performing migration for config version 3 -> 4 (append Point Blank defaults if missing)");
            try {
                List<String> wl = new java.util.ArrayList<>(COMMON.soundIdWhitelist.get().stream().map(String::valueOf).toList());
                boolean wlChanged = false;
                String pbSoundId = "pointblank:gun_action";
                if (!wl.isEmpty() && wl.stream().noneMatch(pbSoundId::equals)) {
                    wl.add(pbSoundId);
                    wlChanged = true;
                }
                if (wlChanged) {
                    COMMON.soundIdWhitelist.set(wl);
                    SoundAttractMod.LOGGER.info("Migration v4: Added '{}' to soundIdWhitelist.", pbSoundId);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v4: Could not update soundIdWhitelist: {}", e.toString());
            }

            try {
                List<String> defs = new java.util.ArrayList<>(COMMON.rawSoundDefaults.get().stream().map(String::valueOf).toList());
                String pbDefault = "pointblank:gun_action;15;5";
                boolean hasPb = defs.stream().anyMatch(s -> s.startsWith("pointblank:gun_action;"));
                if (!hasPb) {
                    defs.add(pbDefault);
                    COMMON.rawSoundDefaults.set(defs);
                    SoundAttractMod.LOGGER.info("Migration v4: Appended '{}' to soundDefaults.", pbDefault);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v4: Could not update soundDefaults: {}", e.toString());
            }
        }

        if (oldVersion < 5) {
            SoundAttractMod.LOGGER.info("Performing migration for config version 4 -> 5 (populate Point Blank lists if empty)");
            try {
                List<String> v = new java.util.ArrayList<>(COMMON.pointBlankAttachmentReductions.get().stream().map(String::valueOf).toList());
                if (v.isEmpty()) {
                    v = new java.util.ArrayList<>(Arrays.asList(
                            "pointblank:ar_suppressor;40.0",
                            "pointblank:ar_suppressor_tan;40.0",
                            "pointblank:xm7_suppressor;40.0",
                            "pointblank:ak_suppressor;40.0",
                            "pointblank:smg_suppressor;40.0",
                            "pointblank:rf_suppressor;40.0",
                            "pointblank:hp_suppressor;40.0",
                            "pointblank:sg_suppressor;40.0"
                    ));
                    COMMON.pointBlankAttachmentReductions.set(v);
                    SoundAttractMod.LOGGER.info("Migration v5: Populated pointBlankAttachmentReductions with {} defaults.", v.size());
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v5: Could not update pointBlankAttachmentReductions: {}", e.toString());
            }

            try {
                List<String> v = new java.util.ArrayList<>(COMMON.pointBlankGunRanges.get().stream().map(String::valueOf).toList());
                if (v.isEmpty()) {
                    v = new java.util.ArrayList<>(Arrays.asList(
                            "pointblank:glock17;128.0",
                            "pointblank:glock18;128.0",
                            "pointblank:m9;128.0",
                            "pointblank:m1911a1;128.0",
                            "pointblank:tti_viper;128.0",
                            "pointblank:p30l;128.0",
                            "pointblank:mk23;128.0",
                            "pointblank:deserteagle;140.0",
                            "pointblank:rhino;138.0",
                            "pointblank:m4a1;118.0",
                            "pointblank:m4a1mod1;118.0",
                            "pointblank:star15;90.0",
                            "pointblank:m4sopmodii;118.0",
                            "pointblank:m16a1;90.0",
                            "pointblank:hk416;118.0",
                            "pointblank:scarl;72.0",
                            "pointblank:xm7;118.0",
                            "pointblank:g36c;132.0",
                            "pointblank:g36k;132.0",
                            "pointblank:aug;118.0",
                            "pointblank:g41;118.0",
                            "pointblank:ak74;128.0",
                            "pointblank:ak12;128.0",
                            "pointblank:an94;128.0",
                            "pointblank:ar57;128.0",
                            "pointblank:xm29;128.0",
                            "pointblank:mp5;128.0",
                            "pointblank:mp7;128.0",
                            "pointblank:ro635;119.0",
                            "pointblank:ump45;128.0",
                            "pointblank:vector;90.0",
                            "pointblank:p90;128.0",
                            "pointblank:m950;128.0",
                            "pointblank:tmp;128.0",
                            "pointblank:sl8;128.0",
                            "pointblank:mk14ebr;128.0",
                            "pointblank:uar10;156.0",
                            "pointblank:g3;128.0",
                            "pointblank:wa2000;128.0",
                            "pointblank:xm3;128.0",
                            "pointblank:c14;128.0",
                            "pointblank:l96a1;128.0",
                            "pointblank:ballista;128.0",
                            "pointblank:gm6lynx;128.0",
                            "pointblank:m590;128.0",
                            "pointblank:m870;128.0",
                            "pointblank:spas12;128.0",
                            "pointblank:m1014;128.0",
                            "pointblank:citoricxs;128.0",
                            "pointblank:hs12;128.0",
                            "pointblank:lamg;128.0",
                            "pointblank:mk48;128.0",
                            "pointblank:m249;128.0",
                            "pointblank:m32mgl;128.0",
                            "pointblank:smaw;128.0",
                            "pointblank:at4;128.0",
                            "pointblank:javelin;200.0",
                            "pointblank:m134minigun;156.0",
                            "pointblank:aughbar;128.0",
                            "pointblank:aa12;128.0",
                            "pointblank:ak47;128.0"
                    ));
                    COMMON.pointBlankGunRanges.set(v);
                    SoundAttractMod.LOGGER.info("Migration v5: Populated pointBlankGunRanges with {} defaults.", v.size());
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v5: Could not update pointBlankGunRanges: {}", e.toString());
            }

            try {
                List<String> v = new java.util.ArrayList<>(COMMON.pointBlankMuzzleFlashReductions.get().stream().map(String::valueOf).toList());
                if (v.isEmpty()) {
                    v = new java.util.ArrayList<>(Arrays.asList(
                            "pointblank:ar_suppressor;90.0",
                            "pointblank:ar_suppressor_tan;90.0",
                            "pointblank:xm7_suppressor;90.0",
                            "pointblank:ak_suppressor;90.0",
                            "pointblank:smg_suppressor;90.0",
                            "pointblank:rf_suppressor;90.0",
                            "pointblank:hp_suppressor;90.0",
                            "pointblank:sg_suppressor;90.0"
                    ));
                    COMMON.pointBlankMuzzleFlashReductions.set(v);
                    SoundAttractMod.LOGGER.info("Migration v5: Populated pointBlankMuzzleFlashReductions with {} defaults.", v.size());
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v5: Could not update pointBlankMuzzleFlashReductions: {}", e.toString());
            }
        }

        if (oldVersion < 7) {
            SoundAttractMod.LOGGER.info("Performing migration for config version 6 -> 7 (ensure flee-from-unseen-attacker toggle exists).");
            try {
                boolean current = COMMON.enableFleeFromUnseenAttackerGoal.get();
                COMMON.enableFleeFromUnseenAttackerGoal.set(current);
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Migration v7: Unable to access enableFleeFromUnseenAttackerGoal; using default.", e);
            }
        }

        COMMON.configVersion.set(CURRENT_CONFIG_VERSION);
        SoundAttractMod.LOGGER.info("Config migration complete. New schema version: {}. Saving config...", CURRENT_CONFIG_VERSION);
        COMMON_SPEC.save();
    }

    public static final Map<Identifier, Integer> customArmorColors = new ConcurrentHashMap<>();
    public static Set<String> ATTRACTED_ENTITY_TYPES_CACHE = new HashSet<>();

    public static void parseAndCacheCustomArmorColors() {
        customArmorColors.clear();
        if (COMMON == null || COMMON.customArmorColors == null) {
            if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("SoundAttractConfig: COMMON or customArmorColors is null, skipping custom armor color parsing.");
            }
            return;
        }

        List<? extends String> configList = COMMON.customArmorColors.get();
        if (configList == null || configList.isEmpty()) {
            if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("SoundAttractConfig: No custom armor colors defined in config.");
            }
            return;
        }

        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Parsing {} custom armor color entries...", configList.size());
        }

        for (String entry : configList) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String[] parts = entry.split(";");
            if (parts.length == 2) {
                String itemId = parts[0].trim();
                String colorHex = parts[1].trim();
                try {
                    Identifier loc = Identifier.tryParse(itemId);
                    if (loc == null) {
                        SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid Identifier for custom armor color: {}", itemId);
                        continue;
                    }
                    if (!colorHex.startsWith("#") || colorHex.length() != 7) {
                        SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid hex color format '{}' for item {}. Expected #RRGGBB.", colorHex, itemId);
                        continue;
                    }
                    int color = Integer.parseInt(colorHex.substring(1), 16);
                    customArmorColors.put(loc, color);
                    if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("SoundAttractConfig: Added custom armor color: {} -> #{}", itemId, Integer.toHexString(color).toUpperCase());
                    }
                } catch (NumberFormatException e) {
                    SoundAttractMod.LOGGER.warn("SoundAttractConfig: Could not parse color hex '{}' for item {}: {}", colorHex, itemId, e.getMessage());
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.error("SoundAttractConfig: Unexpected error parsing custom armor color entry '{}': {}", entry, e.getMessage());
                }
            } else {
                SoundAttractMod.LOGGER.warn("SoundAttractConfig: Malformed custom armor color entry: '{}'. Expected format: 'modid:item_id;#RRGGBB'", entry);
            }
        }
        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Finished parsing custom armor colors. {} entries loaded.", customArmorColors.size());
        }
    }

    public static void parseAndCacheNonBlockingVisionAllowList() {
        NON_BLOCKING_VISION_ALLOW_CACHE.clear();
        if (COMMON == null || COMMON.nonBlockingVisionAllowList == null) {
            if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("SoundAttractConfig: COMMON or nonBlockingVisionAllowList is null, skipping parsing.");
            }
            return;
        }

        List<? extends String> cfg = COMMON.nonBlockingVisionAllowList.get();
        if (cfg == null || cfg.isEmpty()) {
            return;
        }

        for (String entry : cfg) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            try {
                Identifier loc = Identifier.tryParse(entry.trim());
                if (loc != null) {
                    NON_BLOCKING_VISION_ALLOW_CACHE.add(loc);
                } else {
                    SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid Identifier in nonBlockingVisionAllowList: {}", entry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("SoundAttractConfig: Error parsing nonBlockingVisionAllowList entry '{}': {}", entry, e.getMessage());
            }
        }

        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Loaded {} entries into NON_BLOCKING_VISION_ALLOW_CACHE.", NON_BLOCKING_VISION_ALLOW_CACHE.size());
        }
    }

    public static class Common {

        public final ModConfigSpec.IntValue configVersion;
        public final ModConfigSpec.BooleanValue debugLogging;
        public final ModConfigSpec.BooleanValue enableFleeFromUnseenAttackerGoal;
        public final ModConfigSpec.BooleanValue enableRaycastCache;
        public final ModConfigSpec.BooleanValue edgeMobSmartBehavior;
        public final ModConfigSpec.IntValue soundLifetimeTicks;
        public final ModConfigSpec.DoubleValue arrivalDistance;
        public final ModConfigSpec.DoubleValue mobMoveSpeed;
        public final ModConfigSpec.IntValue maxSoundsTracked;
        public final ModConfigSpec.DoubleValue soundSwitchRatio;
        public final ModConfigSpec.DoubleValue soundNoveltyBonusWeight;
        public final ModConfigSpec.IntValue soundNoveltyTimeTicks;
        public final ModConfigSpec.DoubleValue largeSoundRangeThreshold;

        public final ModConfigSpec.IntValue initialGroupComputationDelay;
        public final ModConfigSpec.IntValue workerThreads;
        public final ModConfigSpec.IntValue workerTaskBudgetMs;
        public final ModConfigSpec.DoubleValue cooldownTicksPerMob;
        public final ModConfigSpec.IntValue soundScoringSubmitCooldownTicks;
        public final ModConfigSpec.IntValue asyncResultTtlTicks;
        public final ModConfigSpec.IntValue groupUpdateInterval;
        public final ModConfigSpec.IntValue scanCooldownTicks;
        public final ModConfigSpec.DoubleValue minTpsForScanCooldown;
        public final ModConfigSpec.DoubleValue maxTpsForScanCooldown;
        public final ModConfigSpec.IntValue raycastCacheTtlTicks;
        public final ModConfigSpec.IntValue raycastCacheMaxEntries;
        public final ModConfigSpec.IntValue raycastCacheCleanupIntervalTicks;

        public final ModConfigSpec.BooleanValue enableTaskQueue;
        public final ModConfigSpec.IntValue maxSoundEvalsPerTick;
        public final ModConfigSpec.IntValue resultFreshnessTicks;

        public final ModConfigSpec.IntValue maxGroupSize;
        public final ModConfigSpec.DoubleValue leaderGroupRadius;
        public final ModConfigSpec.DoubleValue groupDistance;
        public final ModConfigSpec.DoubleValue leaderSpacingMultiplier;
        public final ModConfigSpec.IntValue numEdgeSectors;
        public final ModConfigSpec.IntValue edgeMobsPerSector;
        public final ModConfigSpec.DoubleValue groupSprintMultiplier;
        public final ModConfigSpec.DoubleValue leaderReturnArrivalDistance;
        public final ModConfigSpec.IntValue raidCountdownTicks;
        public final ModConfigSpec.IntValue maxLeaders;

        public final ModConfigSpec.ConfigValue<List<? extends String>> attractedEntities;
        public final ModConfigSpec.ConfigValue<List<? extends String>> specialMobProfilesRaw;
        public final ModConfigSpec.ConfigValue<List<? extends String>> specialPlayerProfilesRaw;

        public final ModConfigSpec.ConfigValue<List<? extends String>> soundIdWhitelist;
        public final ModConfigSpec.ConfigValue<List<? extends String>> rawSoundDefaults;
        public final ModConfigSpec.DoubleValue minSoundLevelForPlayer;
        public final ModConfigSpec.DoubleValue minSoundLevelForMob;

        public final ModConfigSpec.BooleanValue enableBlockMuffling;
        public final ModConfigSpec.IntValue maxMufflingBlocksToCheck;
        public final ModConfigSpec.DoubleValue mufflingFactorWool;
        public final ModConfigSpec.DoubleValue mufflingFactorSolid;
        public final ModConfigSpec.DoubleValue mufflingFactorNonSolid;
        public final ModConfigSpec.DoubleValue mufflingFactorThin;
        public final ModConfigSpec.DoubleValue mufflingFactorLiquid;
        public final ModConfigSpec.DoubleValue mufflingFactorAir;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customThinBlocks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customLiquidBlocks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customAirBlocks;

        public final ModConfigSpec.BooleanValue enableStealthMechanics;
        public final ModConfigSpec.IntValue stealthCheckInterval;
        public final ModConfigSpec.IntValue stealthGracePeriodTicks;
        public final ModConfigSpec.DoubleValue minStealthDetectionRange;
        public final ModConfigSpec.DoubleValue maxStealthDetectionRange;
        public final ModConfigSpec.DoubleValue noLineOfSightFactor;
        public final ModConfigSpec.ConfigValue<List<? extends String>> nonBlockingVisionAllowList;

        public final ModConfigSpec.DoubleValue standingDetectionRangePlayer;
        public final ModConfigSpec.DoubleValue sneakingDetectionRangePlayer;
        public final ModConfigSpec.DoubleValue crawlingDetectionRangePlayer;

        public final ModConfigSpec.IntValue neutralLightLevel;
        public final ModConfigSpec.DoubleValue lightLevelSensitivity;
        public final ModConfigSpec.DoubleValue minLightFactor;
        public final ModConfigSpec.DoubleValue maxLightFactor;
        public final ModConfigSpec.IntValue lightSampleRadiusHorizontal;
        public final ModConfigSpec.IntValue lightSampleRadiusVertical;
        public final ModConfigSpec.DoubleValue rainStealthFactor;
        public final ModConfigSpec.DoubleValue thunderStealthFactor;

        public final ModConfigSpec.DoubleValue movementStealthPenalty;
        public final ModConfigSpec.DoubleValue stationaryStealthBonusFactor;
        public final ModConfigSpec.DoubleValue movementThreshold;
        public final ModConfigSpec.DoubleValue invisibilityStealthFactor;

        public final ModConfigSpec.BooleanValue enableCamouflage;
        public final ModConfigSpec.BooleanValue enableHeldItemPenalty;
        public final ModConfigSpec.DoubleValue heldItemPenaltyFactor;
        public final ModConfigSpec.BooleanValue enableEnchantmentPenalty;
        public final ModConfigSpec.DoubleValue armorEnchantmentPenaltyFactor;
        public final ModConfigSpec.DoubleValue heldItemEnchantmentPenaltyFactor;

        public final ModConfigSpec.ConfigValue<List<? extends String>> camouflageArmorItems;
        public final ModConfigSpec.BooleanValue requireFullSetForCamouflageBonus;
        public final ModConfigSpec.DoubleValue fullArmorStealthBonus;
        public final ModConfigSpec.DoubleValue helmetCamouflageEffectiveness;
        public final ModConfigSpec.DoubleValue chestplateCamouflageEffectiveness;
        public final ModConfigSpec.DoubleValue leggingsCamouflageEffectiveness;
        public final ModConfigSpec.DoubleValue bootsCamouflageEffectiveness;
        public final ModConfigSpec.DoubleValue maxCamouflageEffectivenessCap;
        public final ModConfigSpec.BooleanValue allowPartialBonusIfFullSetRequired;

        public final ModConfigSpec.BooleanValue enableEnvironmentalCamouflage;
        public final ModConfigSpec.DoubleValue environmentalCamouflageMaxEffectiveness;
        public final ModConfigSpec.IntValue environmentalCamouflageColorMatchThreshold;
        public final ModConfigSpec.BooleanValue environmentalCamouflageOnlyDyedLeather;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customArmorColors;
        public final ModConfigSpec.IntValue envColorSampleRadius;
        public final ModConfigSpec.IntValue envColorSampleYOffsetStart;
        public final ModConfigSpec.IntValue envColorSampleYOffsetEnd;
        public final ModConfigSpec.BooleanValue enableEnvironmentalMismatchPenalty;
        public final ModConfigSpec.DoubleValue environmentalMismatchPenaltyFactor;
        public final ModConfigSpec.IntValue environmentalMismatchThreshold;

        public final ModConfigSpec.BooleanValue enableTaczIntegration;
        public final ModConfigSpec.DoubleValue taczReloadRange;
        public final ModConfigSpec.DoubleValue taczReloadWeight;
        public final ModConfigSpec.DoubleValue taczShootRange;
        public final ModConfigSpec.DoubleValue taczShootWeight;
        public final ModConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels;
        public final ModConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions;
        public final ModConfigSpec.DoubleValue taczAttachmentReductionDefault;
        public final ModConfigSpec.DoubleValue gunshotBaseDetectionRange;
        public final ModConfigSpec.IntValue gunshotDetectionDurationTicks;
        public final ModConfigSpec.ConfigValue<List<? extends String>> taczMuzzleFlashReductions;

        public final ModConfigSpec.BooleanValue enableVoiceChatIntegration;
        public final ModConfigSpec.IntValue voiceChatWhisperRange;
        public final ModConfigSpec.IntValue voiceChatNormalRange;
        public final ModConfigSpec.DoubleValue voiceChatWeight;
        public final ModConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap;

        public final ModConfigSpec.BooleanValue enablePointBlankIntegration;
        public final ModConfigSpec.DoubleValue pointBlankReloadRange;
        public final ModConfigSpec.DoubleValue pointBlankReloadWeight;
        public final ModConfigSpec.DoubleValue pointBlankShootRange;
        public final ModConfigSpec.DoubleValue pointBlankShootWeight;
        public final ModConfigSpec.ConfigValue<List<? extends String>> pointBlankAttachmentReductions;
        public final ModConfigSpec.ConfigValue<List<? extends String>> pointBlankGunRanges;
        public final ModConfigSpec.ConfigValue<List<? extends String>> pointBlankMuzzleFlashReductions;

        public final ModConfigSpec.ConfigValue<List<? extends String>> fovOverrides;
        public final ModConfigSpec.ConfigValue<List<? extends String>> fovExclusionList;

        public final ModConfigSpec.BooleanValue enableBlockBreaking;
        public final ModConfigSpec.DoubleValue blockBreakTimeMultiplier;
        public final ModConfigSpec.BooleanValue blockBreakToolOnly;
        public final ModConfigSpec.BooleanValue blockBreakProperToolOnly;
        public final ModConfigSpec.BooleanValue blockBreakProperToolRequired;
        public final ModConfigSpec.IntValue blockBreakMaxY;
        public final ModConfigSpec.BooleanValue blockBreakBlacklistTileEntities;
        public final ModConfigSpec.BooleanValue blockBreakListAsWhitelist;
        public final ModConfigSpec.ConfigValue<List<? extends String>> blockBreakBlockList;

        public final ModConfigSpec.BooleanValue enableTeleportToSound;
        public final ModConfigSpec.DoubleValue teleportChance;
        public final ModConfigSpec.IntValue teleportCooldownTicks;
        public final ModConfigSpec.ConfigValue<String> teleportCanTeleportTag;
        public final ModConfigSpec.ConfigValue<String> teleportCanBeTeleportedTag;

        public final ModConfigSpec.BooleanValue enablePickUpAndThrowToSound;
        public final ModConfigSpec.DoubleValue pickUpChance;
        public final ModConfigSpec.IntValue pickUpCooldownTicks;
        public final ModConfigSpec.IntValue pickUpMinDistanceToPickUp;
        public final ModConfigSpec.IntValue pickUpMaxDistanceToThrow;
        public final ModConfigSpec.DoubleValue pickUpSpeedModifier;
        public final ModConfigSpec.ConfigValue<String> pickUpCanPickUpTag;
        public final ModConfigSpec.ConfigValue<String> pickUpCanBePickedUpTag;

        public final ModConfigSpec.BooleanValue enableXrayTargeting;
        public final ModConfigSpec.ConfigValue<String> xrayApplyTag;
        public final ModConfigSpec.BooleanValue xrayRequireBetterNearby;
        public final ModConfigSpec.ConfigValue<String> xrayBetterNearbyTag;
        public final ModConfigSpec.IntValue xrayMinRange;
        public final ModConfigSpec.IntValue xrayMaxRange;
        public final ModConfigSpec.DoubleValue xrayChance;

        public Common(ModConfigSpec.Builder builder) {
            builder.comment("Internal config version. Do not change.").push("version");
            configVersion = builder.defineInRange("configVersion", 0, 0, CURRENT_CONFIG_VERSION);
            builder.pop();

            builder.comment("Sound Attract Mod Configuration").push("general");
            debugLogging = builder.comment("Enable debug logging for troubleshooting.")
                    .define("debugLogging", false);
            enableFleeFromUnseenAttackerGoal = builder.comment("Enable the FleeFromUnseenAttackerGoal that makes mobs flee after being hurt by an attacker they cannot see.")
                    .define("enableFleeFromUnseenAttackerGoal", true);
            enableRaycastCache = builder.comment("Enable caching for raycast results to improve performance. Disable if experiencing issues with sound obstruction detection.")
                    .define("enableRaycastCache", true);
            edgeMobSmartBehavior = builder.comment("Enables smarter behavior for mobs at the edge of their hearing range (e.g. pathing closer to investigate further)")
                    .define("edgeMobSmartBehavior", false);
            soundLifetimeTicks = builder.comment("How long a sound event remains 'interesting' to a mob, in ticks (20 ticks = 1 second).")
                    .defineInRange("soundLifetimeTicks", 600, 20, 1000000);
            arrivalDistance = builder.comment("How close a mob needs to get to a sound source to consider it 'reached'.")
                    .defineInRange("arrivalDistance", 6.0, 1.0, 100.0);
            mobMoveSpeed = builder.comment("Base speed multiplier for mobs moving towards a sound.")
                    .defineInRange("mobMoveSpeed", 1.15, 0.1, 3.0);
            maxSoundsTracked = builder.comment("Maximum number of sounds any single mob can track simultaneously.")
                    .defineInRange("maxSoundsTracked", 10, 1, 1000000);
            largeSoundRangeThreshold = builder.comment("Range threshold above which sounds are treated as 'large range' and checked across the whole dimension cache.")
                    .defineInRange("largeSoundRangeThreshold", 64.0, 1.0, 2048.0);
            enableTaskQueue = builder.comment("Enable server-thread task queue to process sound evaluations with a per-tick budget (safer than multi-threading).")
                    .define("enableTaskQueue", true);
            maxSoundEvalsPerTick = builder.comment("Maximum number of sound evaluations processed per server tick. Increase for better responsiveness, decrease for performance.")
                    .defineInRange("maxSoundEvalsPerTick", 200, 1, 1000000);
            resultFreshnessTicks = builder.comment("How long (in ticks) a cached evaluation result is considered fresh for consumers like AttractionGoal.")
                    .defineInRange("resultFreshnessTicks", 5, 1, 200);
            maxGroupSize = builder.comment("Maximum number of mobs allowed in a group for group AI behavior. Default: 64")
                    .defineInRange("maxGroupSize", 64, 1, 128);
            leaderGroupRadius = builder.comment("Radius (in blocks) used to group mobs under a leader for group AI behavior. Default: 64.0")
                    .defineInRange("leaderGroupRadius", 64.0, 1.0, 128.0);
            groupDistance = builder.comment("Maximum distance (in blocks) for mobs to consider themselves part of a group for group behaviors. Used in AI such as FollowLeaderGoal.")
                    .defineInRange("groupDistance", 128.0, 1.0, 128.0);
            soundSwitchRatio = builder.comment(
                    "Switching threshold factor (0.0–1.0]. A mob will switch if newWeight > currentWeight × soundSwitchRatio.",
                    "Example: 0.5 means a new sound beating 70% of the current weight will trigger a switch (more eager switching).",
                    "Set closer to 1.0 for conservative switching; closer to 0.0 for very eager switching.")
                    .defineInRange("soundSwitchRatio", 0.5, 0.0, 1.0);
            soundNoveltyBonusWeight = builder.comment("A small weight bonus given to very new sounds to make mobs more likely to switch to them.",
                    "This helps break ties and makes mobs seem more 'alert' to new threats.",
                    "Set to 0.0 to disable.")
                    .defineInRange("soundNoveltyBonusWeight", 9.5, 0.0, 10.0);
            soundNoveltyTimeTicks = builder.comment("How long (in ticks) a sound is considered 'new' for the novelty bonus to apply.",
                    "20 ticks = 1 second.")
                    .defineInRange("soundNoveltyTimeTicks", 100, 1, 200);
            leaderSpacingMultiplier = builder.comment("Multiplier for spacing between mob leaders in a group. Default: 1.0")
                    .defineInRange("leaderSpacingMultiplier", 1.0, 0.1, 10.0);
            numEdgeSectors = builder.comment("Number of edge sectors for group detection (AI). Default: 4")
                    .defineInRange("numEdgeSectors", 4, 1, 64);
            edgeMobsPerSector = builder.comment("Maximum number of edge mobs to select per sector. Default: 1")
                    .defineInRange("edgeMobsPerSector", 1, 1, 64);
            groupSprintMultiplier = builder.comment("Sprint speed multiplier used when followers rally/advance during RAID and when edge mobs return to the leader.")
                    .defineInRange("groupSprintMultiplier", 1.1, 1.0, 5.0);
            leaderReturnArrivalDistance = builder.comment("Distance (in blocks) within which an edge mob considers itself 'returned' to its leader.")
                    .defineInRange("leaderReturnArrivalDistance", 2.0, 0.5, 16.0);
            raidCountdownTicks = builder.comment("Countdown duration (in ticks) before a RAID advances to the target. 20 ticks = 1 second.")
                    .defineInRange("raidCountdownTicks", 100, 20, 72000);
            builder.comment("Performance-tuning options. Adjust these to balance responsiveness and server load.").push("performance");

            initialGroupComputationDelay = builder.comment(
                    "Delay in ticks before the first mob group computation is run after server startup.",
                    "This helps prevent lag on world load by giving the server time to stabilize.",
                    "20 ticks = 1 second. Default: 50 (2.5 seconds)"
            ).defineInRange("initialGroupComputationDelay", 50, 0, 72000);

            workerThreads = builder.comment(
                    "Number of background worker threads used for off-thread computations (e.g., group building).",
                    "Increase for large servers; decrease if you observe contention."
            ).defineInRange("workerThreads", 2, 1, 64);

            workerTaskBudgetMs = builder.comment(
                    "Soft per-task time budget in milliseconds for worker computations before yielding.",
                    "Higher values allow more work per batch but can increase latency to apply results."
            ).defineInRange("workerTaskBudgetMs", 10, 1, 1000);

            scanCooldownTicks = builder.comment("Minimum time in ticks between mob scans for new sounds. Higher values can improve performance but reduce responsiveness.")
                    .defineInRange("scanCooldownTicks", 25, 1, 1000000);

            cooldownTicksPerMob = builder.comment("How many ticks to add to the base scan cooldown for each active mob.",
                    "This directly controls how much the cooldown increases with more mobs.",
                    "A higher value means more cooldown per mob, slowing down scans more aggressively.",
                    "Example: 0.25 means 100 mobs will add (100 * 0.15) = 15 ticks to the base cooldown.")
                    .defineInRange("cooldownTicksPerMob", 0.15, 0.0, 10.0);

            minTpsForScanCooldown = builder.comment("TPS below which scanCooldownTicks is dynamically increased to save performance. Set to 0 to disable.")
                    .defineInRange("minTpsForScanCooldown", 15.0, 0.0, 20.0);

            maxTpsForScanCooldown = builder.comment("TPS above which scanCooldownTicks is dynamically decreased (down to its minimum defined value). Set to 21 to disable.")
                    .defineInRange("maxTpsForScanCooldown", 19.0, 0.0, 21.0);
            groupUpdateInterval = builder.comment(
                    "Time in ticks between mob group updates. Lower values are more responsive but cost more performance."
            ).defineInRange("groupUpdateInterval", 40, 10, 600);

            soundScoringSubmitCooldownTicks = builder.comment(
                    "Cooldown in ticks before a mob can submit a new sound for async scoring.",
                    "Prevents spamming the async queue. Higher values reduce load but may delay reactions."
            ).defineInRange("soundScoringSubmitCooldownTicks", 10, 0, 200);

            asyncResultTtlTicks = builder.comment(
                    "Time-to-live in ticks for async sound scoring results.",
                    "Results older than this are discarded. Prevents mobs from acting on very old data."
            ).defineInRange("asyncResultTtlTicks", 100, 20, 1200);

            raycastCacheTtlTicks = builder.comment("Time-to-live (in ticks) for entries in the raycast cache. Older entries are evicted during periodic cleanup.")
                    .defineInRange("raycastCacheTtlTicks", 600, 1, 1000000);
            raycastCacheMaxEntries = builder.comment("Maximum number of entries to keep in the raycast cache. When exceeded, oldest entries are evicted.")
                    .defineInRange("raycastCacheMaxEntries", 5000, 100, 2000000);
            raycastCacheCleanupIntervalTicks = builder.comment("Interval (in ticks) at which the raycast cache performs cleanup and eviction.")
                    .defineInRange("raycastCacheCleanupIntervalTicks", 200, 1, 1000000);

            maxLeaders = builder.comment("Maximum number of group leaders tracked for AI grouping. Default: 16")
                    .defineInRange("maxLeaders", 16, 1, 64);
            builder.pop();

            builder.push("Mobs");
            attractedEntities = builder.comment("List of mobs that will be attracted to sounds. Example: ['minecraft:zombie', 'minecraft:skeleton'].")
                    .defineList("attractedEntities", Arrays.asList(
                            "minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned",
                            "minecraft:endermite", "minecraft:evoker", "minecraft:guardian", "minecraft:hoglin", "minecraft:husk",
                            "minecraft:magma_cube", "minecraft:phantom", "minecraft:piglin", "minecraft:piglin_brute",
                            "minecraft:pillager", "minecraft:ravager", "minecraft:shulker", "minecraft:silverfish",
                            "minecraft:skeleton", "minecraft:slime", "minecraft:spider", "minecraft:stray",
                            "minecraft:vex", "minecraft:vindicator", "minecraft:witch",
                            "minecraft:wither_skeleton", "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager",
                            "minecraft:bogged", "minecraft:parched", "minecraft:zombie_horse", "minecraft:zombie_nautilus",
                            "minecraft:camel_husk", "minecraft:breeze", "minecraft:enderman", "minecraft:warden"
                    ), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            builder.pop();

            builder.push("Sounds White List");
            soundIdWhitelist = builder.comment("If not empty, only sound event IDs in this list will be considered by mobs.")
                    .defineList("soundIdWhitelist", Arrays.asList(
                            "tacz:gun_shoot",
                            "tacz:gun_reload",
                            "pointblank:gun_action",
                            "gcaa:item.g19.fire",
                            "minecraft:item.crossbow.shoot",
                            "minecraft:item.crossbow.loading_start",
                            "minecraft:item.crossbow.loading_middle",
                            "minecraft:item.crossbow.loading_end",
                            "minecraft:item.crossbow.quick_charge_1",
                            "minecraft:item.crossbow.quick_charge_2",
                            "minecraft:item.crossbow.quick_charge_3",
                            "minecraft:entity.arrow.shoot",
                            "minecraft:item.shield.block",
                            "minecraft:block.lever.click",
                            "minecraft:block.wooden_trapdoor.open",
                            "minecraft:block.wooden_trapdoor.close",
                            "minecraft:block.bamboo_wood_trapdoor.open",
                            "minecraft:block.bamboo_wood_trapdoor.close",
                            "minecraft:block.cherry_wood_trapdoor.open",
                            "minecraft:block.cherry_wood_trapdoor.close",
                            "minecraft:block.iron_trapdoor.open",
                            "minecraft:block.iron_trapdoor.close",
                            "minecraft:block.wooden_door.open",
                            "minecraft:block.wooden_door.close",
                            "minecraft:block.bamboo_wood_door.open",
                            "minecraft:block.bamboo_wood_door.close",
                            "minecraft:block.cherry_wood_door.open",
                            "minecraft:block.cherry_wood_door.close",
                            "minecraft:block.iron_door.open",
                            "minecraft:block.iron_door.close",
                            "minecraft:block.fence_gate.open",
                            "minecraft:block.fence_gate.close",
                            "minecraft:block.piston.extend",
                            "minecraft:block.piston.contract",
                            "minecraft:block.dispenser.dispense",
                            "minecraft:block.dispenser.launch",
                            "minecraft:block.anvil.land",
                            "minecraft:block.anvil.use",
                            "minecraft:block.anvil.destroy",
                            "minecraft:block.sand.fall",
                            "minecraft:block.gravel.fall",
                            "minecraft:block.grass.break",
                            "minecraft:block.scaffolding.break",
                            "tacz:target_block_hit",
                            "minecraft:entity.boat.paddle_water",
                            "minecraft:ambient.underwater.enter",
                            "minecraft:ambient.underwater.exit",
                            "minecraft:block.chest.open",
                            "minecraft:block.chest.close",
                            "minecraft:block.barrel.open",
                            "minecraft:block.barrel.close",
                            "minecraft:block.ender_chest.open",
                            "minecraft:block.ender_chest.close",
                            "minecraft:block.shulker_box.open",
                            "minecraft:block.shulker_box.close",
                            "minecraft:block.bell.use",
                            "minecraft:block.bell.resonate",
                            "minecraft:block.furnace.fire_crackle",
                            "minecraft:entity.generic.explode",
                            "minecraft:entity.firework_rocket.launch",
                            "minecraft:entity.firework_rocket.blast",
                            "minecraft:entity.firework_rocket.large_blast",
                            "minecraft:entity.player.hurt",
                            "parcool:grabbing",
                            "parcool:wallrun_and_running",
                            "parcool:jumping",
                            "parcool:sliding_1",
                            "parcool:sliding_2",
                            "parcool:sliding_3",
                            "parcool:roll_and_dodge",
                            "parcool:landing",
                            "minecraft:random/anvil_land",
                            "entity/leashknot/place1",
                            "minecraft:entity.player.sprint",
                            "minecraft:entity.player.jump",
                            "minecraft:entity.player.sneak",
                            "tacz:gun",
                            "soundattract:voice_chat",
                            "musketmod:musket_fire",
                            "musketmod:blunderbuss_fire",
                            "musketmod:pistol_fire",
                            "cgm:item.shotgun.fire",
                            "cgm:item.shotgun.silenced_fire",
                            "cgm:item.shotgun.enchanted_fire",
                            "cgm:item.shotgun.cock",
                            "cgm:item.rifle.fire",
                            "cgm:item.rifle.silenced_fire",
                            "cgm:item.rifle.enchanted_fire",
                            "cgm:item.rifle.cock",
                            "cgm:item.pistol.fire",
                            "cgm:item.pistol.silenced_fire",
                            "cgm:item.pistol.enchanted_fire",
                            "cgm:item.pistol.reload",
                            "cgm:item.pistol.cock",
                            "cgm:item.assault_rifle.fire",
                            "cgm:item.assault_rifle.silenced_fire",
                            "cgm:item.assault_rifle.enchanted_fire",
                            "cgm:item.assault_rifle.cock",
                            "cgm:item.grenade_launcher.fire",
                            "cgm:item.bazooka.fire",
                            "cgm:item.mini_gun.fire",
                            "cgm:item.mini_gun.enchanted_fire",
                            "cgm:item.machine_pistol.fire",
                            "cgm:item.machine_pistol.silenced_fire",
                            "cgm:item.machine_pistol.enchanted_fire",
                            "cgm:item.heavy_rifle.fire",
                            "cgm:item.heavy_rifle.silenced_fire",
                            "cgm:item.heavy_rifle.enchanted_fire",
                            "cgm:item.heavy_rifle.cock",
                            "cgm:item.grenade.pin",
                            "cgm:entity.stun_grenade.explosion",
                            "cgm:entity.stun_grenade.ring",
                            "scguns:item.makeshift_rifle.cock",
                            "scguns:item.pistol.cock",
                            "scguns:item.flamethrower.reload",
                            "scguns:item.gauss.reload",
                            "scguns:item.pistol.reload",
                            "scguns:item.airgun.fire",
                            "scguns:item.beam.fire",
                            "scguns:item.blackpowder.fire",
                            "scguns:item.boomstick.fire",
                            "scguns:item.brass_pistol.fire",
                            "scguns:item.brass_revolver.fire",
                            "scguns:item.brass_shotgun.fire",
                            "scguns:item.bruiser.fire",
                            "scguns:item.combat_shotgun.fire",
                            "scguns:item.cowboy.fire",
                            "scguns:item.flamethrower.fire_2",
                            "scguns:item.gauss.fire",
                            "scguns:item.greaser_smg.fire",
                            "scguns:item.gyrojet.fire",
                            "scguns:item.heavy_rifle.fire",
                            "scguns:item.heavier_rifle.fire",
                            "scguns:item.iron_pistol.fire",
                            "scguns:item.iron_rifle.fire",
                            "scguns:item.makeshift_rifle.fire",
                            "scguns:item.plasma.fire",
                            "scguns:item.raygun.fire",
                            "scguns:item.rocket.fire",
                            "scguns:item.rocket_rifle.fire",
                            "scguns:item.rusty_gnat.fire",
                            "scguns:item.scrapper.fire",
                            "scguns:item.sculk.fire",
                            "scguns:item.shock.fire",
                            "scguns:item.shulker.fire",
                            "scguns:item.scorched_sniper.fire",
                            "scguns:item.scorched_rifle.fire",
                            "scguns:item.shock.silenced_fire",
                            "scguns:item.bruiser.silenced_fire",
                            "scguns:item.makeshift_rifle.silenced_fire",
                            "scguns:item.combat_shotgun.silenced_fire",
                            "scguns:item.rusty_gnat.silenced_fire",
                            "scguns:item.shock.fire",
                            "scguns:item.scorched_sniper.fire",
                            "scguns:item.scorched_rifle.fire",
                            "scguns:item.bruiser.fire",
                            "scguns:item.flamethrower.fire_2",
                            "scguns:item.blackpowder.fire",
                            "scguns:item.gyrojet.fire",
                            "scguns:item.boomstick.fire",
                            "scguns:item.brass_shotgun.fire",
                            "scguns:item.brass_revolver.fire",
                            "scguns:item.shulker.fire",
                            "scguns:item.iron_rifle.fire",
                            "scguns:item.combat_shotgun.fire",
                            "scguns:item.beam.fire",
                            "scguns:item.airgun.fire",
                            "scguns:item.heavier_rifle.fire",
                            "scguns:item.iron_pistol.fire",
                            "scguns:item.gauss.fire",
                            "scguns:item.heavy_rifle.fire",
                            "scguns:item.greaser_smg.fire",
                            "scguns:item.plasma.fire",
                            "scguns:item.rusty_gnat.fire",
                            "scguns:item.cowboy.fire",
                            "scguns:item.scrapper.fire",
                            "scguns:item.sculk.fire",
                            "scguns:item.raygun.fire",
                            "scguns:item.rocket_rifle.fire"
                    ),
                            obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            minSoundLevelForPlayer = builder.comment("Minimum sound level (0.0-1.0) for player-emitted sounds to be considered. Higher values mean only louder sounds are tracked.")
                    .defineInRange("minSoundLevelForPlayer", 0.1, 0.0, 1.0);
            minSoundLevelForMob = builder.comment("Minimum sound level (0.0-1.0) for mob-emitted sounds to be considered.")
                    .defineInRange("minSoundLevelForMob", 0.15, 0.0, 1.0);
            builder.pop();

            builder.comment("Default Sound Properties").push("sound_defaults");
            rawSoundDefaults = builder.comment(
                    "List of default sound properties. Format: 'sound_id;range;weight'",
                    "Example: 'minecraft:item.crossbow.shoot;16.0;4.0'")
                    .defineList("soundDefaults",
                            Arrays.asList(
                                    "gcaa:item.g19.fire;160;10",
                                    "pointblank:gun_action;15;5",
                                    "minecraft:item.crossbow.shoot;16;4",
                                    "minecraft:item.crossbow.loading_start;6;2",
                                    "minecraft:item.crossbow.loading_middle;6;2",
                                    "minecraft:item.crossbow.loading_end;6;2",
                                    "minecraft:item.crossbow.quick_charge_1;6;2",
                                    "minecraft:item.crossbow.quick_charge_2;6;2",
                                    "minecraft:item.crossbow.quick_charge_3;6;2",
                                    "minecraft:entity.arrow.shoot;14;4",
                                    "minecraft:item.shield.block;12;3",
                                    "minecraft:block.lever.click;5;3",
                                    "minecraft:block.wooden_trapdoor.open;8;3",
                                    "minecraft:block.wooden_trapdoor.close;8;3",
                                    "minecraft:block.bamboo_wood_trapdoor.open;10;3",
                                    "minecraft:block.bamboo_wood_trapdoor.close;10;3",
                                    "minecraft:block.cherry_wood_trapdoor.open;10;3",
                                    "minecraft:block.cherry_wood_trapdoor.close;10;3",
                                    "minecraft:block.iron_trapdoor.open;15;4",
                                    "minecraft:block.iron_trapdoor.close;15;4",
                                    "minecraft:block.wooden_door.open;12;4",
                                    "minecraft:block.wooden_door.close;12;4",
                                    "minecraft:block.bamboo_wood_door.open;15;4",
                                    "minecraft:block.bamboo_wood_door.close;15;4",
                                    "minecraft:block.cherry_wood_door.open;15;4",
                                    "minecraft:block.cherry_wood_door.close;15;4",
                                    "minecraft:block.iron_door.open;20;5",
                                    "minecraft:block.iron_door.close;20;5",
                                    "minecraft:block.fence_gate.open;10;3",
                                    "minecraft:block.fence_gate.close;10;3",
                                    "minecraft:block.piston.extend;20;4",
                                    "minecraft:block.piston.contract;20;4",
                                    "minecraft:block.dispenser.dispense;12;4",
                                    "minecraft:block.dispenser.launch;12;4",
                                    "minecraft:block.anvil.land;25;5",
                                    "minecraft:block.anvil.use;25;5",
                                    "minecraft:block.anvil.destroy;25;5",
                                    "minecraft:block.sand.fall;6;3",
                                    "minecraft:block.gravel.fall;6;3",
                                    "minecraft:block.grass.break;3;2",
                                    "minecraft:block.scaffolding.break;3;2",
                                    "tacz:target_block_hit;6;3",
                                    "minecraft:entity.boat.paddle_water;8;3",
                                    "minecraft:ambient.underwater.enter;4;2",
                                    "minecraft:ambient.underwater.exit;4;2",
                                    "minecraft:block.chest.open;5;2",
                                    "minecraft:block.chest.close;5;2",
                                    "minecraft:block.barrel.open;5;2",
                                    "minecraft:block.barrel.close;5;2",
                                    "minecraft:block.ender_chest.open;6;2",
                                    "minecraft:block.ender_chest.close;6;2",
                                    "minecraft:block.shulker_box.open;6;2",
                                    "minecraft:block.shulker_box.close;6;2",
                                    "minecraft:block.bell.use;30;5",
                                    "minecraft:block.bell.resonate;15;4",
                                    "minecraft:block.furnace.fire_crackle;8;3",
                                    "minecraft:entity.generic.explode;50;7",
                                    "minecraft:entity.firework_rocket.launch;10;3",
                                    "minecraft:entity.firework_rocket.blast;20;5",
                                    "minecraft:entity.firework_rocket.large_blast;30;6",
                                    "musketmod:musket_fire;155;8",
                                    "musketmod:blunderbuss_fire;154;7",
                                    "musketmod:pistol_fire;164;5",
                                    "cgm:item.shotgun.fire;156;15",
                                    "cgm:item.shotgun.silenced_fire;131;13",
                                    "cgm:item.shotgun.enchanted_fire;156;15",
                                    "cgm:item.shotgun.cock;90;6",
                                    "cgm:item.rifle.fire;162;16",
                                    "cgm:item.rifle.silenced_fire;137;13",
                                    "cgm:item.rifle.enchanted_fire;162;16",
                                    "cgm:item.rifle.cock;90;6",
                                    "cgm:item.pistol.fire;164;16",
                                    "cgm:item.pistol.silenced_fire;139;13",
                                    "cgm:item.pistol.enchanted_fire;164;16",
                                    "cgm:item.pistol.reload;85;7",
                                    "cgm:item.pistol.cock;90;6",
                                    "cgm:item.assault_rifle.fire;159;16",
                                    "cgm:item.assault_rifle.silenced_fire;134;13",
                                    "cgm:item.assault_rifle.enchanted_fire;159;16",
                                    "cgm:item.assault_rifle.cock;90;6",
                                    "cgm:item.grenade_launcher.fire;172;17",
                                    "cgm:item.bazooka.fire;184;17",
                                    "cgm:item.mini_gun.fire;180;17",
                                    "cgm:item.mini_gun.enchanted_fire;180;17",
                                    "cgm:item.machine_pistol.fire;160;16",
                                    "cgm:item.machine_pistol.silenced_fire;135;13",
                                    "cgm:item.machine_pistol.enchanted_fire;160;16",
                                    "cgm:item.heavy_rifle.fire;165;16",
                                    "cgm:item.heavy_rifle.silenced_fire;140;13",
                                    "cgm:item.heavy_rifle.enchanted_fire;165;16",
                                    "cgm:item.heavy_rifle.cock;90;6",
                                    "cgm:item.grenade.pin;72;6",
                                    "cgm:entity.stun_grenade.explosion;175;18",
                                    "cgm:entity.stun_grenade.ring;104;10",
                                    "scguns:item.makeshift_rifle.cock;10;1.0",
                                    "scguns:item.pistol.cock;5;0.5",
                                    "scguns:item.flamethrower.reload;10;1.0",
                                    "scguns:item.gauss.reload;25;2.5",
                                    "scguns:item.pistol.reload;10;1.0",
                                    "scguns:item.airgun.fire;80;8.0",
                                    "scguns:item.beam.fire;100;10.0",
                                    "scguns:item.blackpowder.fire;115;11.5",
                                    "scguns:item.boomstick.fire;120;12.0",
                                    "scguns:item.brass_pistol.fire;105;10.5",
                                    "scguns:item.brass_revolver.fire;110;11.0",
                                    "scguns:item.brass_shotgun.fire;110;11.0",
                                    "scguns:item.bruiser.fire;115;11.5",
                                    "scguns:item.combat_shotgun.fire;120;12.0",
                                    "scguns:item.cowboy.fire;110;11.0",
                                    "scguns:item.flamethrower.fire_2;95;9.5",
                                    "scguns:item.gauss.fire;130;13.0",
                                    "scguns:item.greaser_smg.fire;110;11.0",
                                    "scguns:item.gyrojet.fire;115;11.5",
                                    "scguns:item.heavy_rifle.fire;120;12.0",
                                    "scguns:item.heavier_rifle.fire;125;12.5",
                                    "scguns:item.iron_pistol.fire;110;11.0",
                                    "scguns:item.iron_rifle.fire;120;12.0",
                                    "scguns:item.makeshift_rifle.fire;110;11.0",
                                    "scguns:item.plasma.fire;115;11.5",
                                    "scguns:item.raygun.fire;110;11.0",
                                    "scguns:item.rocket.fire;140;14.0",
                                    "scguns:item.rocket_rifle.fire;135;13.5",
                                    "scguns:item.rusty_gnat.fire;105;10.5",
                                    "scguns:item.scrapper.fire;110;11.0",
                                    "scguns:item.sculk.fire;100;10.0",
                                    "scguns:item.shock.fire;95;9.5",
                                    "scguns:item.shulker.fire;105;10.5",
                                    "scguns:item.scorched_sniper.fire;130;13.0",
                                    "scguns:item.scorched_rifle.fire;125;12.5",
                                    "scguns:item.shock.silenced_fire;45;4.5",
                                    "scguns:item.bruiser.silenced_fire;55;5.5",
                                    "scguns:item.makeshift_rifle.silenced_fire;50;5.0",
                                    "scguns:item.combat_shotgun.silenced_fire;60;6.0",
                                    "scguns:item.rusty_gnat.silenced_fire;45;4.5",
                                    "scguns:item.shock.fire;155;15.5",
                                    "scguns:item.scorched_sniper.fire;190;19.0",
                                    "scguns:item.scorched_rifle.fire;185;18.5",
                                    "scguns:item.bruiser.fire;175;17.5",
                                    "scguns:item.flamethrower.fire_2;155;15.5",
                                    "scguns:item.blackpowder.fire;175;17.5",
                                    "scguns:item.gyrojet.fire;175;17.5",
                                    "scguns:item.boomstick.fire;180;18.0",
                                    "scguns:item.brass_shotgun.fire;170;17.0",
                                    "scguns:item.brass_revolver.fire;170;17.0",
                                    "scguns:item.shulker.fire;165;16.5",
                                    "scguns:item.iron_rifle.fire;180;18.0",
                                    "scguns:item.combat_shotgun.fire;180;18.0",
                                    "scguns:item.beam.fire;160;16.0",
                                    "scguns:item.airgun.fire;140;14.0",
                                    "scguns:item.heavier_rifle.fire;185;18.5",
                                    "scguns:item.iron_pistol.fire;170;17.0",
                                    "scguns:item.gauss.fire;190;19.0",
                                    "scguns:item.heavy_rifle.fire;180;18.0",
                                    "scguns:item.greaser_smg.fire;170;17.0",
                                    "scguns:item.plasma.fire;175;17.5",
                                    "scguns:item.rusty_gnat.fire;165;16.5",
                                    "scguns:item.cowboy.fire;170;17.0",
                                    "scguns:item.scrapper.fire;170;17.0",
                                    "scguns:item.sculk.fire;160;16.0",
                                    "scguns:item.raygun.fire;170;17.0",
                                    "scguns:item.rocket_rifle.fire;195;19.5",
                                    "scguns:item.makeshift_rifle.cock;10;1.0", "scguns:item.pistol.cock;5;0.5", "scguns:item.flamethrower.reload;10;1.0", "scguns:item.gauss.reload;25;2.5", "scguns:item.pistol.reload;10;1.0", "scguns:item.airgun.fire;80;8.0", "scguns:item.beam.fire;100;10.0", "scguns:item.blackpowder.fire;115;11.5", "scguns:item.boomstick.fire;120;12.0", "scguns:item.brass_pistol.fire;105;10.5", "scguns:item.brass_revolver.fire;110;11.0", "scguns:item.brass_shotgun.fire;110;11.0", "scguns:item.bruiser.fire;115;11.5", "scguns:item.combat_shotgun.fire;120;12.0", "scguns:item.cowboy.fire;110;11.0", "scguns:item.flamethrower.fire_2;95;9.5", "scguns:item.gauss.fire;130;13.0", "scguns:item.greaser_smg.fire;110;11.0", "scguns:item.gyrojet.fire;115;11.5", "scguns:item.heavy_rifle.fire;120;12.0", "scguns:item.heavier_rifle.fire;125;12.5", "scguns:item.iron_pistol.fire;110;11.0", "scguns:item.iron_rifle.fire;120;12.0", "scguns:item.makeshift_rifle.fire;110;11.0", "scguns:item.plasma.fire;115;11.5", "scguns:item.raygun.fire;110;11.0", "scguns:item.rocket.fire;140;14.0", "scguns:item.rocket_rifle.fire;135;13.5", "scguns:item.rusty_gnat.fire;105;10.5", "scguns:item.scrapper.fire;110;11.0", "scguns:item.sculk.fire;100;10.0", "scguns:item.shock.fire;95;9.5", "scguns:item.shulker.fire;105;10.5", "scguns:item.scorched_sniper.fire;130;13.0", "scguns:item.scorched_rifle.fire;125;12.5", "scguns:item.shock.silenced_fire;45;4.5", "scguns:item.bruiser.silenced_fire;55;5.5", "scguns:item.makeshift_rifle.silenced_fire;50;5.0", "scguns:item.combat_shotgun.silenced_fire;60;6.0", "scguns:item.rusty_gnat.silenced_fire;45;4.5", "scguns:item.shock.fire;155;15.5", "scguns:item.scorched_sniper.fire;190;19.0", "scguns:item.scorched_rifle.fire;185;18.5", "scguns:item.bruiser.fire;175;17.5", "scguns:item.flamethrower.fire_2;155;15.5", "scguns:item.blackpowder.fire;175;17.5", "scguns:item.gyrojet.fire;175;17.5", "scguns:item.boomstick.fire;180;18.0", "scguns:item.brass_shotgun.fire;170;17.0", "scguns:item.brass_revolver.fire;170;17.0", "scguns:item.shulker.fire;165;16.5", "scguns:item.iron_rifle.fire;180;18.0", "scguns:item.combat_shotgun.fire;180;18.0", "scguns:item.beam.fire;160;16.0", "scguns:item.airgun.fire;140;14.0", "scguns:item.heavier_rifle.fire;185;18.5", "scguns:item.iron_pistol.fire;170;17.0", "scguns:item.gauss.fire;190;19.0", "scguns:item.heavy_rifle.fire;180;18.0", "scguns:item.greaser_smg.fire;170;17.0", "scguns:item.plasma.fire;175;17.5", "scguns:item.rusty_gnat.fire;165;16.5", "scguns:item.cowboy.fire;170;17.0", "scguns:item.scrapper.fire;170;17.0", "scguns:item.sculk.fire;160;16.0", "scguns:item.raygun.fire;170;17.0", "scguns:item.rocket_rifle.fire;195;19.5"
                            ),
                            obj -> obj instanceof String && ((String) obj).split(";").length == 3);
            builder.pop();

            builder.comment("====================================================================",
                    " Sound Attract Mod - Stealth & Detection Configuration",
                    "====================================================================").push("sound_attract_main");
            builder.comment("--- Field of View Settings ---").push("fov");
            fovOverrides = builder.comment(
                    "A list of custom FOV (Field of View) overrides for specific mobs.",
                    "This gives you direct control over the vision cone for any mob.",
                    "Format: \"modid:mob_id, horizontal_fov, vertical_fov\"",
                    "SPECIAL VALUE: A horizontal FOV of 360 or more grants the mob 360-degree vision (omni-directional).",
                    "Any mob NOT in this list will use the default FOV (200 horizontal, 135 vertical)."
            )
                    .defineList("customFovOverrides",
                            List.of(
                                    "minecraft:spider, 360.0, 360.0",
                                    "minecraft:cave_spider, 360.0, 360.0",
                                    "minecraft:phantom, 200.0, 280.0",
                                    "minecraft:vex, 200.0, 280.0",
                                    "minecraft:allay, 200.0, 280.0",
                                    "minecraft:bat, 20.0, 20.0",
                                    "minecraft:parrot, 200.0, 280.0",
                                    "minecraft:ghast, 200.0, 280.0",
                                    "minecraft:blaze, 200.0, 280.0",
                                    "minecraft:axolotl, 270.0, 90.0",
                                    "minecraft:camel, 270.0, 90.0",
                                    "minecraft:chicken, 270.0, 90.0",
                                    "minecraft:cow, 270.0, 90.0",
                                    "minecraft:donkey, 270.0, 90.0",
                                    "minecraft:goat, 270.0, 90.0",
                                    "minecraft:horse, 270.0, 90.0",
                                    "minecraft:mule, 270.0, 90.0",
                                    "minecraft:mooshroom, 270.0, 90.0",
                                    "minecraft:panda, 270.0, 90.0",
                                    "minecraft:pig, 270.0, 90.0",
                                    "minecraft:rabbit, 270.0, 90.0",
                                    "minecraft:sheep, 270.0, 90.0",
                                    "minecraft:sniffer, 270.0, 90.0",
                                    "minecraft:strider, 270.0, 90.0",
                                    "minecraft:turtle, 270.0, 90.0",
                                    "minecraft:villager, 270.0, 90.0",
                                    "minecraft:wandering_trader, 270.0, 90.0",
                                    "minecraft:slime, 270.0, 120.0",
                                    "minecraft:magma_cube, 270.0, 120.0",
                                    "minecraft:cod, 300.0, 100.0",
                                    "minecraft:pufferfish, 300.0, 100.0",
                                    "minecraft:salmon, 300.0, 100.0",
                                    "minecraft:squid, 300.0, 100.0",
                                    "minecraft:glow_squid, 300.0, 100.0",
                                    "minecraft:tadpole, 300.0, 100.0",
                                    "minecraft:tropical_fish, 300.0, 100.0",
                                    "minecraft:cat, 140.0, 140.0",
                                    "minecraft:ocelot, 140.0, 140.0",
                                    "minecraft:wolf, 140.0, 140.0",
                                    "minecraft:polar_bear, 140.0, 140.0",
                                    "minecraft:fox, 140.0, 140.0",
                                    "minecraft:frog, 140.0, 140.0",
                                    "minecraft:zombie, 200.0, 135.0",
                                    "minecraft:husk, 200.0, 135.0",
                                    "minecraft:drowned, 200.0, 135.0",
                                    "minecraft:skeleton, 200.0, 135.0",
                                    "minecraft:stray, 200.0, 135.0",
                                    "minecraft:pillager, 200.0, 135.0",
                                    "minecraft:vindicator, 200.0, 135.0",
                                    "minecraft:evoker, 200.0, 135.0",
                                    "minecraft:witch, 200.0, 135.0",
                                    "minecraft:piglin, 200.0, 135.0",
                                    "minecraft:piglin_brute, 200.0, 135.0",
                                    "minecraft:iron_golem, 200.0, 135.0",
                                    "minecraft:creeper, 90.0, 90.0",
                                    "minecraft:enderman, 180.0, 240.0",
                                    "minecraft:guardian, 320.0, 180.0",
                                    "minecraft:elder_guardian, 320.0, 180.0",
                                    "minecraft:ravager, 160.0, 100.0",
                                    "minecraft:hoglin, 160.0, 100.0",
                                    "minecraft:zoglin, 160.0, 100.0",
                                    "minecraft:shulker, 270.0, 45.0"
                            ),
                            obj -> obj instanceof String);

            fovExclusionList = builder.comment(
                    "A list of mobs that will COMPLETELY IGNORE the FOV system.",
                    "Use this for bosses or mobs from other mods with special AI that might break.",
                    "Format: \"modid:mob_id\"",
                    "Note: Certain vanilla mobs like the Warden are always excluded for stability and cannot be removed."
            )
                    .defineList("fovExclusionList",
                            List.of(
                                    "minecraft:warden"
                            ),
                            obj -> obj instanceof String);
            nonBlockingVisionAllowList = builder.comment(
                    "Blocks in this allowlist are treated as see-through for line-of-sight checks (e.g., modded glass).",
                    "Format: ['modid:block_id']"
            )
                    .defineList("nonBlockingVisionAllowList", Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);

            builder.pop();

            builder.comment("General Stealth System Settings").push("general_stealth_settings");
            enableStealthMechanics = builder.comment("Master switch for all custom stealth mechanics. If false, mobs use vanilla detection (modified only by maxStealthDetectionRange if set).")
                    .define("enableStealthMechanics", true);
            stealthCheckInterval = builder.comment("How often (in ticks) the server checks ongoing stealth situations (e.g., for grace period). Lower is more responsive but higher performance cost.", "20 ticks = 1 second.")
                    .defineInRange("stealthCheckInterval", 40, 5, 100);
            stealthGracePeriodTicks = builder.comment("How long (in ticks) a mob will keep targeting a player after losing direct detection (due to stealth) before giving up.", "Set to 0 for no grace period (immediate de-aggro if stealth conditions met).")
                    .defineInRange("stealthGracePeriodTicks", 10, 0, 200);
            builder.pop();

            builder.comment("Base detection ranges for players based on their stance.", "These are modified by all other factors (light, camo, etc.).").push("player_stance_detection_ranges");
            standingDetectionRangePlayer = builder.comment("Base detection range (in blocks) when a player is standing.").defineInRange("standingDetectionRangePlayer", 32.0, 0.0, 128.0);
            sneakingDetectionRangePlayer = builder.comment("Base detection range (in blocks) when a player is sneaking (crouching).").defineInRange("sneakingDetectionRangePlayer", 12.0, 0.0, 128.0);
            crawlingDetectionRangePlayer = builder.comment("Base detection range (in blocks) when a player is crawling (e.g., in a 1-block high gap).")
                    .defineInRange("crawlingDetectionRangePlayer", 4.0, 0.0, 128.0);
            builder.pop();

            builder.comment("How environmental conditions affect stealth.").push("environmental_factors");
            builder.comment("Light level effects on detection.").push("light_level");
            neutralLightLevel = builder.comment("The light level (0-15) considered neutral (no bonus or penalty to detection).")
                    .defineInRange("neutralLightLevel", 7, 0, 15);
            lightLevelSensitivity = builder.comment("Modifier strength per point of light difference from 'neutralLightLevel'. Higher = more impact.", "Positive values increase detection in bright light / decrease in dark. Negative values would invert this.")
                    .defineInRange("lightLevelSensitivity", 0.3, 0.0, 0.5);
            minLightFactor = builder.comment("Minimum multiplier that can be applied due to light levels (e.g., 0.2 for max 80% range reduction in total darkness).")
                    .defineInRange("minLightFactor", 0.2, 0.01, 1.0);
            maxLightFactor = builder.comment("Maximum multiplier that can be applied due to light levels (e.g., 2.0 for max 100% range increase in full brightness).")
                    .defineInRange("maxLightFactor", 3.0, 1.0, 5.0);
            lightSampleRadiusHorizontal = builder.comment("Horizontal radius (blocks) around player to sample for average/effective light level.")
                    .defineInRange("lightSampleRadiusHorizontal", 2, 0, 5);
            lightSampleRadiusVertical = builder.comment("Vertical radius (blocks) around player to sample for average/effective light level.")
                    .defineInRange("lightSampleRadiusVertical", 1, 0, 3);
            builder.pop();

            builder.comment("Weather effects on detection.").push("weather");
            rainStealthFactor = builder.comment("Detection range multiplier when raining (e.g., 0.8 for 20% range reduction).")
                    .defineInRange("rainStealthFactor", 0.8, 0.1, 1.0);
            thunderStealthFactor = builder.comment("Detection range multiplier when thundering (overrides rain factor if active).")
                    .defineInRange("thunderStealthFactor", 0.6, 0.1, 1.0);
            builder.pop();
            builder.pop();

            builder.comment("How player actions affect their detectability.").push("player_actions");
            builder.comment("Movement effects on detection.").push("movement");
            movementStealthPenalty = builder.comment("Detection range multiplier when player is moving (not sneaking/crawling). >1.0 means easier to detect.", "Set to 1.0 for no penalty.")
                    .defineInRange("movementStealthPenalty", 1.2, 1.0, 3.0);
            stationaryStealthBonusFactor = builder.comment("Detection range multiplier if player is NOT moving above threshold (e.g. 0.8 for 20% harder to detect).", "Set to 1.0 for no bonus when stationary. Applies unless sprinting/crawling.")
                    .defineInRange("stationaryStealthBonusFactor", 0.8, 0.1, 1.0);
            movementThreshold = builder.comment("Squared distance threshold to consider a player as 'moving' per stealth check interval.")
                    .defineInRange("movementThreshold", 0.003, 0.0001, 0.1);
            builder.pop();

            builder.comment("Invisibility potion effect.").push("invisibility");
            invisibilityStealthFactor = builder.comment("Detection range multiplier when player has Invisibility effect (e.g., 0.1 for 90% range reduction).")
                    .defineInRange("invisibilityStealthFactor", 0.1, 0.0, 1.0);
            builder.pop();
            builder.pop();

            builder.comment("Settings for item, armor and environmental camouflage.").push("camouflage_system");
            enableCamouflage = builder.comment("Master switch for all camouflage effects (item-based and environmental).")
                    .define("enableCamouflage", true);
            enableHeldItemPenalty = builder.comment("Enable to penalize players for holding items in their hands, making them more detectable.")
                    .define("enableHeldItemPenalty", true);
            heldItemPenaltyFactor = builder.comment("Factor by which detection range is multiplied if the player is holding any item in main or off-hand (e.g., 1.1 = 10% more detectable per occupied hand). This is applied before enchantment penalties on held items.")
                    .defineInRange("heldItemPenaltyFactor", 1.1, 1.0, 2.0);
            enableEnchantmentPenalty = builder.comment("Enable to penalize players for wearing enchanted armor or holding enchanted items.")
                    .define("enableEnchantmentPenalty", true);
            armorEnchantmentPenaltyFactor = builder.comment("Factor by which detection range is multiplied for *each* piece of visibly enchanted armor (not concealed) (e.g., 1.05 = 5% more detectable per piece).")
                    .defineInRange("armorEnchantmentPenaltyFactor", 1.15, 1.0, 2.0);
            heldItemEnchantmentPenaltyFactor = builder.comment("Factor by which detection range is multiplied if a visibly enchanted item (not concealed) is held in main or off-hand (e.g., 1.1 = 10% more detectable per enchanted held item).")
                    .defineInRange("heldItemEnchantmentPenaltyFactor", 1.15, 1.0, 2.0);
            builder.comment("Camouflage provided by wearing specific armor items.").push("item_camouflage");
            camouflageArmorItems = builder.comment("List of item IDs (e.g., 'modid:godly_helmet') basically a set that will work everywhere.")
                    .defineList("camouflageArmorItems", java.util.Collections.emptyList(),
                            obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            requireFullSetForCamouflageBonus = builder.comment("If true, 'fullArmorStealthBonus' applies only if wearing a complete set of 4 armor pieces, ALL of which are from 'camouflageArmorItems'.", "If false, benefits are gained per piece (see per-slot effectiveness) or via 'fullArmorStealthBonus' if a full set of *any* 4 listed items is worn.")
                    .define("requireFullSetForCamouflageBonus", false);
            fullArmorStealthBonus = builder.comment("Stealth effectiveness factor (0.0 to 1.0) if wearing a 'full set' of listed camouflage items.", "Final range *= (1.0 - bonus). E.g., 0.2 = 20% detection range reduction.")
                    .defineInRange("fullArmorStealthBonus", 0.85, 0.0, 1.0);
            helmetCamouflageEffectiveness = builder.comment("Effectiveness of a listed helmet if per-piece bonuses apply.")
                    .defineInRange("helmetCamouflageEffectiveness", 0.15, 0.0, 1.0);
            chestplateCamouflageEffectiveness = builder.comment("Effectiveness of a listed chestplate if per-piece bonuses apply.")
                    .defineInRange("chestplateCamouflageEffectiveness", 0.30, 0.0, 1.0);
            leggingsCamouflageEffectiveness = builder.comment("Effectiveness of a listed leggings if per-piece bonuses apply.")
                    .defineInRange("leggingsCamouflageEffectiveness", 0.25, 0.0, 1.0);
            bootsCamouflageEffectiveness = builder.comment("Effectiveness of a listed boots if per-piece bonuses apply.")
                    .defineInRange("bootsCamouflageEffectiveness", 0.15, 0.0, 1.0);
            maxCamouflageEffectivenessCap = builder.comment("Maximum total effectiveness (0.0 to 1.0) from all item camouflage sources (full set or sum of pieces). Prevents range from becoming too small.")
                    .defineInRange("maxCamouflageEffectivenessCap", 0.85, 0.0, 0.99);
            allowPartialBonusIfFullSetRequired = builder.comment("If 'requireFullSetForCamouflageBonus' is TRUE, but player isn't wearing a full set of listed items, should per-piece bonuses still apply for the listed items they ARE wearing?")
                    .define("allowPartialBonusIfFullSetRequired", true);
            builder.pop();

            builder.comment("Camouflage based on matching armor color to the surrounding environment.").push("environmental_camouflage");
            enableEnvironmentalCamouflage = builder.comment("Enable camouflage based on armor color matching the environment.")
                    .define("enableEnvironmentalCamouflage", true);
            enableEnvironmentalMismatchPenalty = builder.comment("If true, significantly mismatched armor/environment colors will INCREASE detection range.")
                    .define("enableEnvironmentalMismatchPenalty", true);
            environmentalCamouflageMaxEffectiveness = builder.comment("Maximum effectiveness factor (0.0 to 1.0) if armor color perfectly matches environment.", "Final range *= (1.0 - effectiveness).")
                    .defineInRange("environmentalCamouflageMaxEffectiveness", 0.70, 0.0, 1.0);
            environmentalCamouflageColorMatchThreshold = builder.comment("Tolerance for color matching (sum of absolute RGB differences). Lower = stricter match needed.")
                    .defineInRange("environmentalCamouflageColorMatchThreshold", 90, 0, 765);
            environmentalMismatchPenaltyFactor = builder.comment("Detection range multiplier if armor color SIGNIFICANTLY mismatches the environment (e.g., 1.5 for 50% INCREASED range).", "Applies if color difference exceeds 'environmentalMismatchThreshold'. Set to 1.0 to disable penalty.")
                    .defineInRange("environmentalMismatchPenaltyFactor", 1.3, 1.0, 3.0);
            environmentalMismatchThreshold = builder.comment("Color difference threshold beyond which the 'environmentalMismatchPenaltyFactor' applies.", "Should be greater than 'environmentalCamouflageColorMatchThreshold'. E.g., if match threshold is 90, mismatch could be 200.")
                    .defineInRange("environmentalMismatchThreshold", 100, 0, 765);
            environmentalCamouflageOnlyDyedLeather = builder.comment("If true, only dyed leather armor contributes its color. If false, uses 'customArmorColors' for non-leather/undyed items.")
                    .define("environmentalCamouflageOnlyDyedLeather", false);
            customArmorColors = builder.comment("Map of item ID to average hex color (e.g., 'minecraft:iron_chestplate;#A0A0A0').", "Used for environmental camouflage if 'environmentalCamouflageOnlyDyedLeather' is false.")
                    .defineList("customArmorColors", Arrays.asList(
                            "minecraft:leather_helmet;#804F27",
                            "minecraft:leather_chestplate;#804F27",
                            "minecraft:leather_leggings;#804F27",
                            "minecraft:leather_boots;#804F27",
                            "minecraft:chainmail_helmet;#58585A",
                            "minecraft:chainmail_chestplate;#58585A",
                            "minecraft:chainmail_leggings;#58585A",
                            "minecraft:chainmail_boots;#58585A",
                            "minecraft:iron_helmet;#CACACA",
                            "minecraft:iron_chestplate;#CACACA",
                            "minecraft:iron_leggings;#CACACA",
                            "minecraft:iron_boots;#CACACA",
                            "minecraft:golden_helmet;#F5E54C",
                            "minecraft:golden_chestplate;#F5E54C",
                            "minecraft:golden_leggings;#F5E54C",
                            "minecraft:golden_boots;#F5E54C",
                            "minecraft:diamond_helmet;#39D5CD",
                            "minecraft:diamond_chestplate;#39D5CD",
                            "minecraft:diamond_leggings;#39D5CD",
                            "minecraft:diamond_boots;#39D5CD",
                            "minecraft:netherite_helmet;#403B3B",
                            "minecraft:netherite_chestplate;#403B3B",
                            "minecraft:netherite_leggings;#403B3B",
                            "minecraft:netherite_boots;#403B3B",
                            "minecraft:turtle_helmet;#7B8834"
                    ), obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_.-]+:[a-z0-9/._-]+;#[0-9a-fA-F]{6}$"));
            envColorSampleRadius = builder.comment("Radius (blocks) around player to sample for average environmental color.")
                    .defineInRange("envColorSampleRadius", 1, 0, 3);
            envColorSampleYOffsetStart = builder.comment("Starting Y-offset relative to player pos for env color sampling (e.g., 0 for player's feet level).")
                    .defineInRange("envColorSampleYOffsetStart", 0, -2, 2);
            envColorSampleYOffsetEnd = builder.comment("Ending Y-offset relative to player pos for env color sampling (e.g., -1 for blocks at feet and one below). Must be <= YOffsetStart.")
                    .defineInRange("envColorSampleYOffsetEnd", -1, -2, 2);
            builder.pop();
            builder.pop();

            builder.comment("Absolute min/max detection ranges after all modifiers are applied.").push("detection_range_limits");
            minStealthDetectionRange = builder.comment("The absolute minimum detection range (in blocks). Player cannot be harder to detect than this, regardless of modifiers.", "Set > 0 to prevent mobs from being completely blind unless intended by other mechanics.")
                    .defineInRange("minStealthDetectionRange", 0.5, 0.0, 64.0);
            maxStealthDetectionRange = builder.comment("Maximum range (in blocks) at which a mob can detect a player under optimal conditions (e.g., clear line of sight, high light level).")
                    .defineInRange("maxStealthDetectionRange", 64.0, 1.0, 256.0);
            noLineOfSightFactor = builder.comment("Detection range multiplier when the mob does not have a direct line of sight to the player. 0.0 means cannot detect without LOS.")
                    .defineInRange("noLineOfSightFactor", 0.0, 0.0, 1.0);

            builder.pop();

            builder.comment("Tacz Integration Configuration").push("tacz");
            enableTaczIntegration = builder.comment("Enable Tacz gun integration").define("enableTaczIntegration", true);
            taczReloadRange = builder.comment("Tacz reload sound range (fallback, calculated as shootDb/20.0 for known guns)").defineInRange("taczReloadRange", 9, 1.0, 128.0);
            taczReloadWeight = builder.comment("Tacz reload sound weight (fallback, calculated as (shootDb/10.0)/2.0 for known guns)").defineInRange("taczReloadWeight", 1.0, 0.0, 10.0);
            taczShootRange = builder.comment("Tacz shoot sound range (fallback, calculated as db for known guns)").defineInRange("taczShootRange", 140.0, 1.0, 256.0);
            taczShootWeight = builder.comment("Tacz shoot sound weight (fallback, calculated as db/10.0 for known guns)").defineInRange("taczShootWeight", 15.0, 0.0, 10.0);
            taczGunShootDecibels = builder.comment("Tacz gun shoot decibels. Format: 'modid:item;decibels'. Example: 'tacz:akm;120.0'")
                    .defineList("taczGunShootDecibels", Arrays.asList(
                            "suffuse:aks74u;157.0", "suffuse:python;155.0", "suffuse:tec9;160.0",
                            "suffuse:tt33;158.0", "tacz:deagle_golder;164.0", "suffuse:tti2011;158.0",
                            "tacz:m1911;157.0", "suffuse:trapper50cal;172.0", "tacz:deagle;164.0",
                            "tacz:cz75;157.0", "tacz:p320;157.0", "suffuse:viper2011;158.0",
                            "tacz:m700;160.0", "tacz:m107;171.0", "tacz:m95;172.0",
                            "tacz:ai_awp;170.0", "suffuse:aw50;173.0", "suffuse:gm6;172.0",
                            "suffuse:m200;173.0", "suffuse:xm7;165.0", "suffuse:qbu191;164.0",
                            "suffuse:n4;161.0", "suffuse:qbz951;160.0", "suffuse:ash12;165.0",
                            "suffuse:qbz951s;160.0", "suffuse:qbz192;159.0",
                            "suffuse:an94;161.0", "tacz:sks_tactical;159.0", "tacz:ak47;159.0",
                            "tacz:type_81;158.0", "tacz:qbz_95;160.0", "tacz:hk416d;161.0",
                            "tacz:m4a1;159.0", "tacz:m16a1;159.0", "tacz:hk_g3;161.0",
                            "tacz:m16a4;159.0", "tacz:mk14;162.0", "tacz:scar_l;161.0",
                            "tacz:scar_h;162.0", "tacz:aug;160.0", "tacz:db_short;165.0",
                            "tacz:db_long;166.0", "tacz:m870;165.0", "tacz:aa12;161.0",
                            "tacz:ump45;158.0", "tacz:hk_mp5a5;158.0",
                            "tacz:uzi;157.0", "suffuse:pp19;157.0", "tacz:vector45;158.0",
                            "tacz:p90;156.0", "tacz:rpg7;180.0", "tacz:m320;172.0",
                            "suffuse:m79;172.0", "suffuse:pkp;165.0", "tacz:m249;165.0",
                            "tacz:rpk;164.0", "tacz:minigun:165.0", "tacz:g36k;135.0",
                            "tacz:spr15hb;140.0", "tacz:springfield1873:161.0", "tacz:b93r;125.0",
                            "tacz:glock_17;125.0"
                    ), obj -> {
                        if (!(obj instanceof String str)) {
                            return false;
                        }
                        String[] parts = str.split(";", 2);
                        if (parts.length != 2) {
                            return false;
                        }
                        try {
                            Double.parseDouble(parts[1]);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    });
            taczAttachmentReductions = builder.comment("Tacz attachment sound reduction. Format: 'modid:item;reduction'. Example: 'tacz:suppressor;15.0'")
                    .defineList("taczAttachmentReductions", Arrays.asList(
                            "tacz:muzzle_brake_cthulhu;-3.0",
                            "tacz:muzzle_brake_pioneer;-3.0",
                            "tacz:muzzle_brake_cyclone_d2;-3.0",
                            "tacz:muzzle_brake_trex;-5.0",
                            "tacz:muzzle_silencer_mirage;35.0",
                            "tacz:muzzle_silencer_vulture;45.0",
                            "tacz:muzzle_silencer_knight_qd;40.0",
                            "tacz:muzzle_silencer_ursus;30.0",
                            "tacz:muzzle_silencer_ptilopsis;30.0",
                            "tacz:muzzle_silencer_phantom_s1;30.0",
                            "tacz:muzzle_compensator_trident;-2.0",
                            "tacz:deagle_golden_long_barrel;20.0"
                    ), obj -> {
                        if (!(obj instanceof String str)) {
                            return false;
                        }
                        String[] parts = str.split(";", 2);
                        if (parts.length != 2) {
                            return false;
                        }
                        try {
                            Double.parseDouble(parts[1]);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    });
            taczAttachmentReductionDefault = builder.comment("Default reduction value for Tacz attachments if the attachment id is not in the list.")
                    .defineInRange("taczAttachmentReductionDefault", 20.0, -300.0, 300.0);
            gunshotBaseDetectionRange = builder.comment("The base visual detection range (in blocks) when a gunshot occurs, before muzzle attachments are factored in.")
                    .defineInRange("gunshotBaseDetectionRange", 128.0, 16.0, 512.0);
            gunshotDetectionDurationTicks = builder.comment("How long (in ticks) the increased detection from a gunshot lasts. 20 ticks = 1 second.")
                    .defineInRange("gunshotDetectionDurationTicks", 60, 1, 200);
            taczMuzzleFlashReductions = builder.comment("Tacz attachment VISUAL FLASH reduction. A positive value reduces flash range, a negative value INCREASES it (e.g., for muzzle brakes)., Format: 'modid:item;reduction_amount'")
                    .defineList("taczMuzzleFlashReductions", Arrays.asList(
                            "tacz:muzzle_silencer_mirage;100.0",
                            "tacz:muzzle_silencer_vulture;110.0",
                            "tacz:muzzle_silencer_knight_qd;105.0",
                            "tacz:muzzle_silencer_ursus;90.0",
                            "tacz:muzzle_silencer_ptilopsis;90.0",
                            "tacz:muzzle_silencer_phantom_s1;90.0",
                            "tacz:muzzle_brake_cthulhu;-10.0",
                            "tacz:muzzle_brake_pioneer;-10.0",
                            "tacz:muzzle_brake_cyclone_d2;-10.0",
                            "tacz:muzzle_brake_trex;-15.0",
                            "tacz:muzzle_compensator_trident;-5.0"
                    ), obj -> {
                        if (!(obj instanceof String str)) {
                            return false;
                        }
                        String[] parts = str.split(";", 2);
                        if (parts.length != 2) {
                            return false;
                        }
                        try {
                            Double.parseDouble(parts[1]);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    });

            builder.pop();

            builder.push("Simple VC");
            enableVoiceChatIntegration = builder.comment("Enable voice chat integration").define("enableVoiceChatIntegration", true);
            voiceChatWhisperRange = builder.comment("Voice chat whisper range").defineInRange("voiceChatWhisperRange", 4, 1, 64);
            voiceChatNormalRange = builder.comment("Voice chat normal range").defineInRange("voiceChatNormalRange", 32, 1, 128);
            voiceChatWeight = builder.comment("Voice chat weight").defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
            voiceChatDbThresholdMap = builder.comment(
                    "Mapping from normalized dB thresholds to range multipliers for SVC.",
                    "Normalized dB is in [0..127], where 0 = silence and 127 = max peak (0 dBFS).",
                    "Each entry format: 'threshold:multiplier'. Entries are evaluated from highest threshold to lowest.",
                    "Defaults replicate the built-in behavior: >=50 -> 1.0, >=30 -> 0.7, >=10 -> 0.3.")
                    .defineList("voiceChatDbThresholdMap",
                            Arrays.asList("110:2.0", "90:1.8", "75:1.5", "50:1.0", "30:0.7", "10:0.3", "0:0.05"),
                            obj -> {
                                if (!(obj instanceof String s)) {
                                    return false;
                                }
                                String[] parts = s.split(":");
                                if (parts.length != 2) {
                                    return false;
                                }
                                try {
                                    Double.parseDouble(parts[0]);
                                    Double.parseDouble(parts[1]);
                                    return true;
                                } catch (Exception e) {
                                    return false;
                                }
                            });
            builder.pop();

            builder.comment("Point Blank Integration Configuration").push("pointblank");
            enablePointBlankIntegration = builder.comment("Enable Vic's Point Blank gun integration").define("enablePointBlankIntegration", true);
            pointBlankReloadRange = builder.comment("Point Blank reload sound range (fallback)").defineInRange("pointBlankReloadRange", 10.0, 1.0, 128.0);
            pointBlankReloadWeight = builder.comment("Point Blank reload sound weight (fallback)").defineInRange("pointBlankReloadWeight", 1.0, 0.0, 10.0);
            pointBlankShootRange = builder.comment("Point Blank shoot sound range (fallback)").defineInRange("pointBlankShootRange", 140.0, 1.0, 256.0);
            pointBlankShootWeight = builder.comment("Point Blank shoot sound weight (fallback)").defineInRange("pointBlankShootWeight", 15.0, 0.0, 10.0);
            pointBlankAttachmentReductions = builder.comment("Point Blank attachment sound reduction. Format: 'modid:item;reduction'. Example: 'pointblank:suppressor;15.0'")
                    .defineList("pointBlankAttachmentReductions", Arrays.asList(
                            "pointblank:ar_suppressor;40.0",
                            "pointblank:ar_suppressor_tan;40.0",
                            "pointblank:xm7_suppressor;40.0",
                            "pointblank:ak_suppressor;40.0",
                            "pointblank:smg_suppressor;40.0",
                            "pointblank:rf_suppressor;40.0",
                            "pointblank:hp_suppressor;40.0",
                            "pointblank:sg_suppressor;40.0"
                    ), obj -> {
                        if (!(obj instanceof String str)) return false;
                        String[] parts = str.split(";", 2);
                        if (parts.length != 2) return false;
                        try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                    });
            pointBlankGunRanges = builder.comment("Point Blank gun sound ranges. Format: 'modid:item;range'. Example: 'pointblank:akm;157.0'")
                    .defineList("pointBlankGunRanges", Arrays.asList(
                            "pointblank:glock17;128.0",
                            "pointblank:glock18;128.0",
                            "pointblank:m9;128.0",
                            "pointblank:m1911a1;128.0",
                            "pointblank:tti_viper;128.0",
                            "pointblank:p30l;128.0",
                            "pointblank:mk23;128.0",
                            "pointblank:deserteagle;140.0",
                            "pointblank:rhino;138.0",
                            "pointblank:m4a1;118.0",
                            "pointblank:m4a1mod1;118.0",
                            "pointblank:star15;90.0",
                            "pointblank:m4sopmodii;118.0",
                            "pointblank:m16a1;90.0",
                            "pointblank:hk416;118.0",
                            "pointblank:scarl;72.0",
                            "pointblank:xm7;118.0",
                            "pointblank:g36c;132.0",
                            "pointblank:g36k;132.0",
                            "pointblank:aug;118.0",
                            "pointblank:g41;118.0",
                            "pointblank:ak74;128.0",
                            "pointblank:ak12;128.0",
                            "pointblank:an94;128.0",
                            "pointblank:ar57;128.0",
                            "pointblank:xm29;128.0",
                            "pointblank:mp5;128.0",
                            "pointblank:mp7;128.0",
                            "pointblank:ro635;119.0",
                            "pointblank:ump45;128.0",
                            "pointblank:vector;90.0",
                            "pointblank:p90;128.0",
                            "pointblank:m950;128.0",
                            "pointblank:tmp;128.0",
                            "pointblank:sl8;128.0",
                            "pointblank:mk14ebr;128.0",
                            "pointblank:uar10;156.0",
                            "pointblank:g3;128.0",
                            "pointblank:wa2000;128.0",
                            "pointblank:xm3;128.0",
                            "pointblank:c14;128.0",
                            "pointblank:l96a1;128.0",
                            "pointblank:ballista;128.0",
                            "pointblank:gm6lynx;128.0",
                            "pointblank:m590;128.0",
                            "pointblank:m870;128.0",
                            "pointblank:spas12;128.0",
                            "pointblank:m1014;128.0",
                            "pointblank:citoricxs;128.0",
                            "pointblank:hs12;128.0",
                            "pointblank:lamg;128.0",
                            "pointblank:mk48;128.0",
                            "pointblank:m249;128.0",
                            "pointblank:m32mgl;128.0",
                            "pointblank:smaw;128.0",
                            "pointblank:at4;128.0",
                            "pointblank:javelin;200.0",
                            "pointblank:m134minigun;156.0",
                            "pointblank:aughbar;128.0",
                            "pointblank:aa12;128.0",
                            "pointblank:ak47;128.0"
                    ), obj -> {
                        if (!(obj instanceof String str)) return false;
                        String[] parts = str.split(";", 2);
                        if (parts.length != 2) return false;
                        try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                    });
            pointBlankMuzzleFlashReductions = builder.comment("Point Blank attachment VISUAL FLASH reduction. Positive reduces flash range, negative increases it. Format: 'modid:item;reduction_amount'")
                    .defineList("pointBlankMuzzleFlashReductions", Arrays.asList(
                            "pointblank:ar_suppressor;90.0",
                            "pointblank:ar_suppressor_tan;90.0",
                            "pointblank:xm7_suppressor;90.0",
                            "pointblank:ak_suppressor;90.0",
                            "pointblank:smg_suppressor;90.0",
                            "pointblank:rf_suppressor;90.0",
                            "pointblank:hp_suppressor;90.0",
                            "pointblank:sg_suppressor;90.0"
                    ), obj -> {
                        if (!(obj instanceof String str)) return false;
                        String[] parts = str.split(";", 2);
                        if (parts.length != 2) return false;
                        try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                    });
            builder.pop();

            builder.comment("Muffling settings for different block types.").push("muffling");
            enableBlockMuffling = builder.comment("Enable/disable block muffling effects on sound range/weight.")
                    .define("enableBlockMuffling", true);
            maxMufflingBlocksToCheck = builder.comment("Maximum number of blocks to check for muffling between sound source and mob. Higher values are more accurate but more performance intensive.")
                    .defineInRange("maxMufflingBlocksToCheck", 16, 8, 256);
            mufflingFactorWool = builder.comment("Sound muffling factor for wool blocks. Default: 0.15")
                    .defineInRange("mufflingFactorWool", 0.15, 0.0, 1.0);
            mufflingFactorSolid = builder.comment("Sound muffling factor for solid blocks. Default: 0.35")
                    .defineInRange("mufflingFactorSolid", 0.35, 0.0, 1.0);
            mufflingFactorNonSolid = builder.comment("Sound muffling factor for non-solid blocks. Default: 0.7")
                    .defineInRange("mufflingFactorNonSolid", 0.7, 0.0, 1.0);
            mufflingFactorThin = builder.comment("Sound muffling factor for thin blocks (e.g., carpets, panes). Default: 0.9")
                    .defineInRange("mufflingFactorThin", 0.9, 0.0, 1.0);
            mufflingFactorLiquid = builder.comment("Sound muffling factor for liquid blocks. Default: 0.5")
                    .defineInRange("mufflingFactorLiquid", 0.5, 0.0, 1.0);
            mufflingFactorAir = builder.comment("Sound muffling factor for air blocks. Default: 1.0")
                    .defineInRange("mufflingFactorAir", 1.0, 0.0, 1.0);
            customWoolBlocks = builder.comment("List of custom wool block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customWoolBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            customSolidBlocks = builder.comment("List of custom solid block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customSolidBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            customNonSolidBlocks = builder.comment("List of custom non-solid block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customNonSolidBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            customThinBlocks = builder.comment("List of custom thin block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customThinBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            customLiquidBlocks = builder.comment("List of custom liquid block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customLiquidBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            customAirBlocks = builder.comment("List of custom air block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customAirBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && Identifier.tryParse((String) obj) != null);
            builder.pop();

            builder.push("profiles");

            specialMobProfilesRaw = builder.comment(
                    "List of special mob profiles. Each profile is a string with 5 parts separated by ';'.",
                    "Format: profileName;mobId;nbtMatcher;soundOverridesString;detectionOverridesString",
                    "- profileName: A unique name (e.g., 'AlphaZombie').",
                    "- mobId: Mob's resource location (e.g., 'minecraft:zombie'). Make sure it is in attractedEntities. Use '*' or empty to match any mob if NBT is specific.",
                    "- nbtMatcher: Any compound NBT (e.g., '{IsAlpha:1b}'). Leave empty for no NBT matching.",
                    "- soundOverrides: Comma-separated 'soundId:range:weight' (e.g., 'minecraft:entity.player.hurt:30.0:2.0,minecraft:block.chest.open:25.0:1.5'). Leave empty for no overrides, and make sure the sound IDs are in the soundIdWhitelist.",
                    "- detectionOverrides: Comma-separated 'stanceName:value' (e.g., 'standing:50.0,sneaking:25.0'). Stances: standing, sneaking, crawling. Leave empty for no overrides.",
                    "Example: AlphaZombie;minecraft:zombie;{IsAlpha:1b};minecraft:entity.player.hurt:30.0:2.0;standing:50.0,sneaking:25.0",
                    "To add more profiles, just add more strings to the list."
            ).defineList(
                    "specialMobProfilesRaw",
                    Arrays.asList(
                            "GreedyGoblin;minecraft:piglin;;minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0;standing:40.0,sneaking:20.0,crawling:10.0",
                            "SmartZombie;minecraft:zombie;{IsAlpha:1b};minecraft:entity.player.hurt:30.0:2.0;standing:80.0,sneaking:45.0,crawling:15.0",
                            "InsaneVillager;minecraft:villager;;minecraft:wooden_door.open:25.0:1.5,minecraft:block.barrel.open:20.0:1.0;"
                    ),
                    obj -> obj instanceof String
            );

            specialPlayerProfilesRaw = builder.comment(
                    "List of special player profiles. Each string has 3 parts separated by ';'.",
                    "Format: profileName;nbtMatcher;detectionOverridesString",
                    "- profileName: A unique name (e.g., 'FelineOrigin').",
                    "- nbtMatcher: Any compound NBT to match on the player (capabilities included). Use valid SNBT. Keys containing ':' must be quoted (e.g., '{\"ForgeCaps\":{\"origins:origins\":{\"Origins\":{\"origins:origin\":\"origins:feline\"}}}}'). Leave empty to match all players.",
                    "- detectionOverridesString: Comma-separated 'stanceName:value' (e.g., 'standing:40.0,sneaking:20.0,crawling:10.0'). Valid stances: standing, sneaking, crawling.",
                    "Example: FelineOrigin;{\"ForgeCaps\":{\"origins:origins\":{\"Origins\":{\"origins:origin\":\"origins:feline\"}}}};standing:24.0,sneaking:10.0,crawling:3.0"
            ).defineList(
                    "specialPlayerProfilesRaw",
                    Arrays.asList(
                            "FelineOrigin;{\"ForgeCaps\":{\"origins:origins\":{\"Origins\":{\"origins:origin\":\"origins:feline\"}}}};standing:24.0,sneaking:10.0,crawling:3.0",
                            "DinosaurHatched;{\"ForgeCaps\":{\"fossil:player\":{\"HatchedDinosaur\":1b}}};standing:50.0"
                    ),
                    obj -> obj instanceof String
            );

            builder.pop();

            builder.push("enhanced_ai_integration");
            enableBlockBreaking = builder.comment("Enable mobs to break blocks that obstruct pathing when stuck.")
                    .define("enableBlockBreaking", false);
            blockBreakTimeMultiplier = builder.comment("Global multiplier for block breaking time. Higher means slower breaking. Set to -1 to mirror EnhancedAI when available.")
                    .defineInRange("blockBreakTimeMultiplier", 1.5, -1.0, 100.0);
            blockBreakToolOnly = builder.comment("Only allow breaking if the mob is holding any tool in main hand.")
                    .define("blockBreakToolOnly", false);
            blockBreakProperToolOnly = builder.comment("Only allow breaking if the mob is holding a proper tool for the block (faster if true).")
                    .define("blockBreakProperToolOnly", false);
            blockBreakProperToolRequired = builder.comment("Require a proper tool to break the block at all.")
                    .define("blockBreakProperToolRequired", false);
            blockBreakMaxY = builder.comment("Maximum Y level where mobs are allowed to break blocks (safety).")
                    .defineInRange("blockBreakMaxY", 256, -2032, 4064);
            blockBreakBlacklistTileEntities = builder.comment("Prevent breaking blocks that have block entities (e.g., chests) to avoid grief.")
                    .define("blockBreakBlacklistTileEntities", true);
            blockBreakListAsWhitelist = builder.comment("Treat blockBreakBlockList as a whitelist (true) or blacklist (false).")
                    .define("blockBreakListAsWhitelist", false);
            blockBreakBlockList = builder.comment("List of blocks (or block tags ending with '*') affected by block breaking rules.")
                    .defineList("blockBreakBlockList", List.of(), obj -> obj instanceof String);

            enableTeleportToSound = builder.comment("Enable special AI: mobs in teleportCanTeleportTag can teleport allies to sound positions.")
                    .define("enableTeleportToSound", false);
            teleportChance = builder.comment("Chance [0..1] for a teleporter mob to attempt teleport when evaluating goals. EnhancedAI difficulty is used if present.")
                    .defineInRange("teleportChance", 0.35, 0.0, 1.0);
            teleportCooldownTicks = builder.comment("Cooldown (ticks) after teleport completes. EnhancedAI value is used if present.")
                    .defineInRange("teleportCooldownTicks", 300, 0, 72000);
            teleportCanTeleportTag = builder.comment("EntityType tag whose entries can perform teleport-to-sound behavior.")
                    .define("teleportCanTeleportTag", "enhancedai:mobs/teleport_to_target/can_teleport");
            teleportCanBeTeleportedTag = builder.comment("EntityType tag whose entries can be teleported toward sounds.")
                    .define("teleportCanBeTeleportedTag", "enhancedai:mobs/teleport_to_target/can_be_teleported");

            enablePickUpAndThrowToSound = builder.comment("Enable special AI: mobs in pickUpCanPickUpTag can throw allies toward sound positions.")
                    .define("enablePickUpAndThrowToSound", false);
            pickUpChance = builder.comment("Chance [0..1] a mob attempts pick-up-and-throw. EnhancedAI difficulty is used if present.")
                    .defineInRange("pickUpChance", 0.05, 0.0, 1.0);
            pickUpCooldownTicks = builder.comment("Cooldown (ticks) after throwing. EnhancedAI value is used if present.")
                    .defineInRange("pickUpCooldownTicks", 600, 0, 72000);
            pickUpMinDistanceToPickUp = builder.comment("Minimum blocks from sound before attempting to pick up a mob.")
                    .defineInRange("pickUpMinDistanceToPickUp", 5, 0, 1024);
            pickUpMaxDistanceToThrow = builder.comment("Maximum distance to sound at which the mob will release/throw.")
                    .defineInRange("pickUpMaxDistanceToThrow", 24, 0, 1024);
            pickUpSpeedModifier = builder.comment("Speed modifier applied while approaching pick-up target.")
                    .defineInRange("pickUpSpeedModifier", 1.25, 0.0, 10.0);
            pickUpCanPickUpTag = builder.comment("EntityType tag whose entries can perform pick-up-and-throw.")
                    .define("pickUpCanPickUpTag", "enhancedai:mobs/pick_up_and_throw/can_pick_up");
            pickUpCanBePickedUpTag = builder.comment("EntityType tag whose entries can be picked up and thrown.")
                    .define("pickUpCanBePickedUpTag", "enhancedai:mobs/pick_up_and_throw/can_be_picked_up");

            enableXrayTargeting = builder.comment("Enable XRAY targeting compat: mobs in xrayApplyTag gain through-wall detection.")
                    .define("enableXrayTargeting", false);
            xrayApplyTag = builder.comment("EntityType tag eligible for XRAY detection.")
                    .define("xrayApplyTag", "enhancedai:mobs/targeting/apply_xray");
            xrayRequireBetterNearby = builder.comment("Require mobs to also be in better-nearby tag for XRAY.")
                    .define("xrayRequireBetterNearby", false);
            xrayBetterNearbyTag = builder.comment("EntityType tag for EnhancedAI better nearby targeting.")
                    .define("xrayBetterNearbyTag", "enhancedai:mobs/targeting/better_nearby_targeting");
            xrayMinRange = builder.comment("Minimum XRAY follow range (fallback when EnhancedAI absent).")
                    .defineInRange("xrayMinRange", 16, 0, 128);
            xrayMaxRange = builder.comment("Maximum XRAY follow range (fallback when EnhancedAI absent). 0 disables.")
                    .defineInRange("xrayMaxRange", 24, 0, 128);
            xrayChance = builder.comment("Chance [0..1] fallback XRAY range is applied when EnhancedAI absent.")
                    .defineInRange("xrayChance", 0.5, 0.0, 1.0);

            builder.pop();

        }

    }

    public static void bakeConfig() {
        int currentVersion = COMMON.configVersion.get();
        if (currentVersion < CURRENT_CONFIG_VERSION) {
            migrateConfig(currentVersion);
        }

        if (COMMON == null) {
            SoundAttractMod.LOGGER.warn("SoundAttractConfig.COMMON is null during bakeConfig. Skipping cache population.");
            return;
        }

        SOUND_ID_WHITELIST_CACHE.clear();
        COMMON.soundIdWhitelist.get().forEach(idStr -> {
            Identifier loc = Identifier.tryParse(idStr);
            if (loc != null) {
                SOUND_ID_WHITELIST_CACHE.add(loc);
            } else {
                SoundAttractMod.LOGGER.warn("Invalid Identifier in soundIdWhitelist: {}", idStr);
            }
        });
        ATTRACTED_ENTITY_TYPES_CACHE.clear();
        if (COMMON.attractedEntities != null) {
            COMMON.attractedEntities.get().forEach(id -> ATTRACTED_ENTITY_TYPES_CACHE.add(id.toString()));
        }

        SOUND_DEFAULT_ENTRIES_CACHE.clear();
        if (COMMON.rawSoundDefaults != null) {
            COMMON.rawSoundDefaults.get().forEach(entry -> {
                String[] parts = entry.split(";");
                if (parts.length == 3) {
                    Identifier soundId = Identifier.tryParse(parts[0]);
                    if (soundId == null) {
                        SoundAttractMod.LOGGER.warn("Invalid sound ID in sound default entry: {}", entry);
                        return;
                    }
                    try {
                        double range = Double.parseDouble(parts[1]);
                        double weight = Double.parseDouble(parts[2]);
                        SOUND_DEFAULT_ENTRIES_CACHE.put(soundId, new SoundDefaultEntry(range, weight));
                    } catch (NumberFormatException e) {
                        SoundAttractMod.LOGGER.warn("Could not parse range/weight for sound default entry: {}", entry, e);
                    }
                } else {
                    SoundAttractMod.LOGGER.warn("Malformed sound default entry (expected sound;range;weight): {}", entry);
                }
            });
        }

        CUSTOM_LIQUID_BLOCKS_CACHE.clear();
        if (COMMON.customLiquidBlocks != null) {
            COMMON.customLiquidBlocks.get().forEach(id -> CUSTOM_LIQUID_BLOCKS_CACHE.add(Identifier.parse(id)));
        }
        CUSTOM_WOOL_BLOCKS_CACHE.clear();
        if (COMMON.customWoolBlocks != null) {
            COMMON.customWoolBlocks.get().forEach(id -> CUSTOM_WOOL_BLOCKS_CACHE.add(Identifier.parse(id)));
        }
        CUSTOM_SOLID_BLOCKS_CACHE.clear();
        if (COMMON.customSolidBlocks != null) {
            COMMON.customSolidBlocks.get().forEach(id -> CUSTOM_SOLID_BLOCKS_CACHE.add(Identifier.parse(id)));
        }
        CUSTOM_NON_SOLID_BLOCKS_CACHE.clear();
        if (COMMON.customNonSolidBlocks != null) {
            COMMON.customNonSolidBlocks.get().forEach(id -> CUSTOM_NON_SOLID_BLOCKS_CACHE.add(Identifier.parse(id)));
        }
        CUSTOM_THIN_BLOCKS_CACHE.clear();
        if (COMMON.customThinBlocks != null) {
            COMMON.customThinBlocks.get().forEach(id -> CUSTOM_THIN_BLOCKS_CACHE.add(Identifier.parse(id)));
        }
        CUSTOM_AIR_BLOCKS_CACHE.clear();
        if (COMMON.customAirBlocks != null) {
            COMMON.customAirBlocks.get().forEach(id -> CUSTOM_AIR_BLOCKS_CACHE.add(Identifier.parse(id)));
        }
        parseAndCacheNonBlockingVisionAllowList();

        com.example.soundattract.StealthDetectionEvents.resetXrayCache();

        TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && COMMON.enableTaczIntegration.get();
        TACZ_RELOAD_RANGE_CACHE = COMMON.taczReloadRange.get();
        TACZ_RELOAD_WEIGHT_CACHE = COMMON.taczReloadWeight.get();
        TACZ_SHOOT_RANGE_CACHE = COMMON.taczShootRange.get();
        TACZ_SHOOT_WEIGHT_CACHE = COMMON.taczShootWeight.get();
        TACZ_GUN_SHOOT_DB_CACHE.clear();
        List<? extends String> rawShoot = COMMON.taczGunShootDecibels.get();
        for (String raw : rawShoot) {
            try {
                String[] parts = raw.split(";", 2);
                Identifier rl = Identifier.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null && db >= 0) {
                    double weight = db / 10.0;
                    TACZ_GUN_SHOOT_DB_CACHE.put(rl, Pair.of(db, weight));
                }
            } catch (Exception e) {
            }
        }

        TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
        List<? extends String> rawAtt = COMMON.taczAttachmentReductions.get();
        for (String raw : rawAtt) {
            try {
                String[] parts = raw.split(";", 2);
                Identifier rl = Identifier.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null) {
                    TACZ_ATTACHMENT_REDUCTION_DB_CACHE.put(rl, Pair.of(db, 0.0));
                }
            } catch (Exception e) {
            }
        }

        TACZ_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        List<? extends String> rawFlashAtt = COMMON.taczMuzzleFlashReductions.get();
        for (String raw : rawFlashAtt) {
            try {
                String[] parts = raw.split(";", 2);
                Identifier rl = Identifier.tryParse(parts[0]);
                double reductionValue = Double.parseDouble(parts[1]);
                if (rl != null) {
                    TACZ_MUZZLE_FLASH_REDUCTION_CACHE.put(rl.toString(), reductionValue);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse muzzle flash reduction entry: {}", raw, e);
            }
        }

        POINT_BLANK_ENABLED_CACHE = ModList.get().isLoaded("pointblank") && COMMON.enablePointBlankIntegration.get();
        POINT_BLANK_RELOAD_RANGE_CACHE = COMMON.pointBlankReloadRange.get();
        POINT_BLANK_RELOAD_WEIGHT_CACHE = COMMON.pointBlankReloadWeight.get();
        POINT_BLANK_SHOOT_RANGE_CACHE = COMMON.pointBlankShootRange.get();
        POINT_BLANK_SHOOT_WEIGHT_CACHE = COMMON.pointBlankShootWeight.get();
        POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.clear();
        if (COMMON.pointBlankAttachmentReductions != null) {
            for (String raw : COMMON.pointBlankAttachmentReductions.get()) {
                try {
                    String[] parts = raw.split(";", 2);
                    Identifier rl = Identifier.tryParse(parts[0]);
                    double reduction = Double.parseDouble(parts[1]);
                    if (rl != null) {
                        POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.put(rl, reduction);
                    }
                } catch (Exception ignored) {}
            }
        }
        POINT_BLANK_GUN_RANGE_CACHE.clear();
        if (COMMON.pointBlankGunRanges != null) {
            for (String raw : COMMON.pointBlankGunRanges.get()) {
                try {
                    String[] parts = raw.split(";", 2);
                    Identifier rl = Identifier.tryParse(parts[0]);
                    double range = Double.parseDouble(parts[1]);
                    if (rl != null) {
                        POINT_BLANK_GUN_RANGE_CACHE.put(rl, range);
                    }
                } catch (Exception ignored) {}
            }
        }
        POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        if (COMMON.pointBlankMuzzleFlashReductions != null) {
            for (String raw : COMMON.pointBlankMuzzleFlashReductions.get()) {
                try {
                    String[] parts = raw.split(";", 2);
                    Identifier rl = Identifier.tryParse(parts[0]);
                    double reduction = Double.parseDouble(parts[1]);
                    if (rl != null) {
                        POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.put(rl.toString(), reduction);
                    }
                } catch (Exception ignored) {}
            }
        }

        parseAndCacheCustomArmorColors();

        SPECIAL_MOB_PROFILES_CACHE = new ArrayList<>();
        if (COMMON.specialMobProfilesRaw != null) {
            List<String> rawProfiles = new ArrayList<>(COMMON.specialMobProfilesRaw.get());
            for (String profileString : rawProfiles) {
                String[] parts = profileString.split(";", -1);

                if (parts.length < 1 || parts[0].trim().isEmpty()) {
                    SoundAttractMod.LOGGER.warn("Skipping mob profile with empty or missing name: '{}'", profileString);
                    continue;
                }
                String profileName = parts[0].trim();

                if (parts.length < 5) {
                    SoundAttractMod.LOGGER.warn("Skipping malformed mob profile string for '{}' (expected 5 parts, got {}): '{}'", profileName, parts.length, profileString);
                    continue;
                }

                Identifier mobId = null;
                CompoundTag nbtMatcher = null;
                Map<Identifier, MobProfile.SoundOverride> soundOverrides = new HashMap<>();
                Map<PlayerStance, Double> detectionOverrides = new HashMap<>();

                String mobIdString = parts[1].trim();
                if (!mobIdString.isEmpty() && !mobIdString.equals("*")) {
                    mobId = Identifier.tryParse(mobIdString);
                    if (mobId == null) {
                        SoundAttractMod.LOGGER.warn("Invalid mob ID '{}' for profile '{}'. This part of the matching will be ignored.", mobIdString, profileName);
                    }
                }

                String nbtMatcherString = parts[2].trim();
                if (!nbtMatcherString.isEmpty()) {
                    try {
                        nbtMatcher = net.minecraft.nbt.NbtUtils.snbtToStructure(nbtMatcherString);
                    } catch (CommandSyntaxException e) {
                        SoundAttractMod.LOGGER.warn("Invalid NBT matcher string '{}' for profile '{}': {}. NBT matching will be skipped for this profile.", nbtMatcherString, profileName, e.getMessage());
                    }
                }

                String soundOverridesString = parts[3].trim();
                if (!soundOverridesString.isEmpty()) {
                    for (String raw : soundOverridesString.split(",")) {
                        raw = raw.trim();
                        if (raw.isEmpty()) {
                            continue;
                        }
                        try {
                            MobProfile.SoundOverride so = MobProfile.SoundOverride.fromString(raw, profileName);
                            if (so != null) {
                                soundOverrides.put(so.soundId(), so);
                            }
                        } catch (IllegalArgumentException e) {
                            SoundAttractMod.LOGGER.warn(
                                    "Malformed sound override entry '{}' for profile '{}': {}. Skipping.",
                                    raw, profileName, e.getMessage()
                            );
                        }
                    }
                }

                String detectionOverridesString = parts[4].trim();
                if (!detectionOverridesString.isEmpty()) {
                    for (String detectionOverrideEntry : detectionOverridesString.split(",")) {
                        String[] doParts = detectionOverrideEntry.trim().split(":");
                        if (doParts.length == 2) {
                            try {
                                PlayerStance stance = PlayerStance.valueOf(doParts[0].trim().toUpperCase(Locale.ROOT));
                                double value = Double.parseDouble(doParts[1].trim());
                                detectionOverrides.put(stance, value);
                            } catch (IllegalArgumentException e) {
                                SoundAttractMod.LOGGER.warn("Invalid player stance or value in detection override '{}' for profile '{}'. Valid stances: STANDING, SNEAKING, CRAWLING. Skipping this override.", detectionOverrideEntry.trim(), profileName, e);
                            }
                        } else {
                            SoundAttractMod.LOGGER.warn("Malformed detection override entry '{}' for profile '{}'. Expected 'stanceName:value'. Skipping this override.", detectionOverrideEntry.trim(), profileName);
                        }
                    }
                }

                MobProfile profile = new com.example.soundattract.config.MobProfile(profileName, mobId == null ? "*" : mobId.toString(), nbtMatcher == null ? null : nbtMatcher.toString(),
                        soundOverrides.values().stream().map(so -> new com.example.soundattract.config.MobProfile.SoundOverride(so.soundId(), so.range(), so.weight())).toList(),
                        detectionOverrides
                );
                SPECIAL_MOB_PROFILES_CACHE.add(profile);
                if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached mob profile: {}", profileName);
                }
            }
        }
        SPECIAL_PLAYER_PROFILES_CACHE = new ArrayList<>();
        if (COMMON.specialPlayerProfilesRaw != null) {
            List<String> rawPlayerProfiles = new ArrayList<>(COMMON.specialPlayerProfilesRaw.get());
            for (String profileString : rawPlayerProfiles) {
                String[] parts = profileString.split(";", -1);

                if (parts.length < 1 || parts[0].trim().isEmpty()) {
                    SoundAttractMod.LOGGER.warn("Skipping player profile with empty or missing name: '{}'", profileString);
                    continue;
                }
                String profileName = parts[0].trim();

                if (parts.length < 3) {
                    SoundAttractMod.LOGGER.warn("Skipping malformed player profile string for '{}' (expected 3 parts, got {}): '{}'", profileName, parts.length, profileString);
                    continue;
                }

                String nbtMatcherString = parts[1].trim();
                java.util.Map<PlayerStance, Double> detectionOverrides = new java.util.HashMap<>();

                String detectionOverridesString = parts[2].trim();
                if (!detectionOverridesString.isEmpty()) {
                    for (String detectionOverrideEntry : detectionOverridesString.split(",")) {
                        String[] doParts = detectionOverrideEntry.trim().split(":");
                        if (doParts.length == 2) {
                            try {
                                PlayerStance stance = PlayerStance.valueOf(doParts[0].trim().toUpperCase(java.util.Locale.ROOT));
                                double value = Double.parseDouble(doParts[1].trim());
                                detectionOverrides.put(stance, value);
                            } catch (IllegalArgumentException e) {
                                SoundAttractMod.LOGGER.warn("Invalid player stance or value in detection override '{}' for player profile '{}'. Valid stances: STANDING, SNEAKING, CRAWLING. Skipping this override.", detectionOverrideEntry.trim(), profileName, e);
                            }
                        } else {
                            SoundAttractMod.LOGGER.warn("Malformed detection override entry '{}' for player profile '{}'. Expected 'stanceName:value'. Skipping this override.", detectionOverrideEntry.trim(), profileName);
                        }
                    }
                }

                com.example.soundattract.config.PlayerProfile profile = new com.example.soundattract.config.PlayerProfile(profileName, nbtMatcherString.isEmpty() ? null : nbtMatcherString, detectionOverrides);
                SPECIAL_PLAYER_PROFILES_CACHE.add(profile);
                if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached player profile: {}", profileName);
                }
            }
        }
        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "SoundAttractConfig: Baked config. {} special mob profiles loaded. {} sound defaults. {} whitelist.",
                    SPECIAL_MOB_PROFILES_CACHE.size(),
                    SOUND_DEFAULT_ENTRIES_CACHE.size(),
                    SOUND_ID_WHITELIST_CACHE.size()
            );
        }
    }

    public static MobProfile getMatchingProfile(Mob mob) {
        if (SPECIAL_MOB_PROFILES_CACHE == null || SPECIAL_MOB_PROFILES_CACHE.isEmpty()) {
            return null;
        }
        for (MobProfile profile : SPECIAL_MOB_PROFILES_CACHE) {
            if (profile.matches(mob)) {
                return profile;
            }
        }
        return null;
    }

    public static PlayerProfile getMatchingPlayerProfile(Player player) {
        if (SPECIAL_PLAYER_PROFILES_CACHE == null || SPECIAL_PLAYER_PROFILES_CACHE.isEmpty()) {
            return null;
        }
        for (PlayerProfile profile : SPECIAL_PLAYER_PROFILES_CACHE) {
            if (profile.matches(player)) {
                return profile;
            }
        }
        return null;
    }
}
