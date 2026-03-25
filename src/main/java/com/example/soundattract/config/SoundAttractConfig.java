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
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import com.example.soundattract.config.separate.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.config.ModConfig;

public class SoundAttractConfig {

    public record SoundDefaultEntry(double range, double weight) {

    }
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final org.apache.commons.lang3.tuple.Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = specPair.getLeft();
        COMMON_SPEC = specPair.getRight();
    }

    public static Set<ResourceLocation> SOUND_ID_WHITELIST_CACHE = ConcurrentHashMap.newKeySet();
    public static Map<ResourceLocation, SoundDefaultEntry> SOUND_DEFAULT_ENTRIES_CACHE = new ConcurrentHashMap<>();
    public static final Set<ResourceLocation> DP_SOUND_WHITELIST_CACHE = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, SoundDefaultEntry> DP_SOUND_DEFAULTS_CACHE = new ConcurrentHashMap<>();
    public static Set<ResourceLocation> CUSTOM_LIQUID_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_WOOL_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_SOLID_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_NON_SOLID_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_THIN_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_AIR_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> NON_BLOCKING_VISION_ALLOW_CACHE = ConcurrentHashMap.newKeySet();
    public static double TACZ_RELOAD_RANGE_CACHE = 10.0;
    public static double TACZ_RELOAD_WEIGHT_CACHE = 1.0;
    public static double TACZ_SHOOT_RANGE_CACHE = 140.0;
    public static double TACZ_SHOOT_WEIGHT_CACHE = 15;
    public static boolean TACZ_ENABLED_CACHE = false;
    public static final Map<ResourceLocation, Pair<Double, Double>> TACZ_GUN_SHOOT_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<String, Double> TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> TACZ_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Pair<Double, Double>> DP_TACZ_GUN_SHOOT_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<String, Double> DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static double TACZ_ATTACHMENT_REDUCTION_DEFAULT_CACHE = 20.0;
    public static double TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE = 0.0;
    public static boolean POINT_BLANK_ENABLED_CACHE = false;
    public static double POINT_BLANK_RELOAD_RANGE_CACHE = 9.0;
    public static double POINT_BLANK_RELOAD_WEIGHT_CACHE = 1.0;
    public static double POINT_BLANK_SHOOT_RANGE_CACHE = 140.0;
    public static double POINT_BLANK_SHOOT_WEIGHT_CACHE = 15.0;
    public static final Map<ResourceLocation, Double> POINT_BLANK_GUN_RANGE_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> POINT_BLANK_ATTACHMENT_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static double POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT_CACHE = 20.0;
    public static final Map<ResourceLocation, Double> DP_POINT_BLANK_GUN_RANGE_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();


    public static List<com.example.soundattract.config.MobProfile2> SPECIAL_MOB_PROFILES_CACHE = new ArrayList<>();
    public static List<com.example.soundattract.config.PlayerProfile2> SPECIAL_PLAYER_PROFILES_CACHE = new ArrayList<>();
    public static List<com.example.soundattract.config.MobProfile2> DP_MOB_PROFILES_CACHE = new ArrayList<>();
    public static List<com.example.soundattract.config.PlayerProfile2> DP_PLAYER_PROFILES_CACHE = new ArrayList<>();
    public static final Map<ResourceLocation, Integer> customArmorColors = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Integer> DP_CUSTOM_ARMOR_COLORS = new ConcurrentHashMap<>();

    public static final Set<String> ATTRACTED_ENTITY_TYPES_CACHE = ConcurrentHashMap.newKeySet();
    public static final Set<ResourceLocation> STEALTH_BYPASS_ENTITY_TYPES_CACHE = ConcurrentHashMap.newKeySet();
    public static final Set<String> STEALTH_BYPASS_MOD_NAMESPACES_CACHE = ConcurrentHashMap.newKeySet();
    
    public static boolean PLAYER_ACTION_SOUNDS_ENABLED_CACHE = true;
    public static final Map<String, Integer> PLAYER_ACTION_RANGES_CACHE = new HashMap<>();
    public static final Map<String, Double> PLAYER_ACTION_WEIGHTS_CACHE = new HashMap<>();



    public static void parseAndCachePlayerActionConfig() {
        if (COMMON == null) return;
        PLAYER_ACTION_SOUNDS_ENABLED_CACHE = COMMON.enablePlayerActionSounds.get();
        PLAYER_ACTION_RANGES_CACHE.clear();
        PLAYER_ACTION_WEIGHTS_CACHE.clear();
        
        List<? extends String> ranges = COMMON.playerActionRanges.get();
        if (ranges != null) {
            for (String entry : ranges) {
                String[] parts = entry.split(";");
                if (parts.length == 2) {
                    try {
                        PLAYER_ACTION_RANGES_CACHE.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                    } catch (Exception e) {}
                }
            }
        }
        
        List<? extends String> weights = COMMON.playerActionWeights.get();
        if (weights != null) {
            for (String entry : weights) {
                String[] parts = entry.split(";");
                if (parts.length == 2) {
                    try {
                        PLAYER_ACTION_WEIGHTS_CACHE.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                    } catch (Exception e) {}
                }
            }
        }
        

        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.crawling"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.sneaking"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.walking"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.sprinting"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.sprint_jumping"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:virtual"));
    }

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
                    ResourceLocation loc = ResourceLocation.tryParse(itemId);
                    if (loc == null) {
                        SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid ResourceLocation for custom armor color: {}", itemId);
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

        boolean enableDataDriven = COMMON != null && COMMON.enableDataDriven != null && COMMON.enableDataDriven.get();
        String priority = COMMON != null && COMMON.datapackPriority != null ? COMMON.datapackPriority.get() : "datapack_over_config";

        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
        


        if (enableDataDriven && !DP_CUSTOM_ARMOR_COLORS.isEmpty()) {
            if (datapackOverConfig) {
                customArmorColors.clear();
                customArmorColors.putAll(DP_CUSTOM_ARMOR_COLORS);
            } else {
                for (Map.Entry<ResourceLocation, Integer> e : DP_CUSTOM_ARMOR_COLORS.entrySet()) {
                    customArmorColors.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Loaded {} entries into customArmorColors (after datapack merge).", customArmorColors.size());
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
                ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
                if (loc != null) {
                    NON_BLOCKING_VISION_ALLOW_CACHE.add(loc);
                } else {
                    SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid ResourceLocation in nonBlockingVisionAllowList: {}", entry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("SoundAttractConfig: Error parsing nonBlockingVisionAllowList entry '{}': {}", entry, e.getMessage());
            }
        }

        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Loaded {} entries into NON_BLOCKING_VISION_ALLOW_CACHE.", NON_BLOCKING_VISION_ALLOW_CACHE.size());
        }
    }

    public static boolean isStealthBypassed(Entity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId == null) {
            return false;
        }
        if (STEALTH_BYPASS_ENTITY_TYPES_CACHE.contains(typeId)) {
            return true;
        }
        String namespace = typeId.getNamespace();
        return namespace != null && STEALTH_BYPASS_MOD_NAMESPACES_CACHE.contains(namespace.toLowerCase(Locale.ROOT));
    }

    public static class Common {

        public final ForgeConfigSpec.BooleanValue debugLogging = GeneralConfig.DEBUG_LOGGING;
        public final ForgeConfigSpec.BooleanValue enableDataDriven = GeneralConfig.ENABLE_DATA_DRIVEN;
        public final ForgeConfigSpec.ConfigValue<String> datapackPriority = GeneralConfig.DATAPACK_PRIORITY;
        public final ForgeConfigSpec.BooleanValue enablePlayerActionSounds = GeneralConfig.ENABLE_PLAYER_ACTION_SOUNDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionRanges = GeneralConfig.PLAYER_ACTION_RANGES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionWeights = GeneralConfig.PLAYER_ACTION_WEIGHTS;
        public final ForgeConfigSpec.DoubleValue playerActionCheckRadius = GeneralConfig.PLAYER_ACTION_CHECK_RADIUS;

        public final ForgeConfigSpec.BooleanValue edgeMobSmartBehavior = RaidConfig.EDGE_MOB_SMART_BEHAVIOR;
        public final ForgeConfigSpec.BooleanValue enableFleeFromUnseenAttackerGoal = GeneralConfig.ENABLE_FLEE_FROM_UNSEEN_ATTACKER_GOAL;
        public final ForgeConfigSpec.IntValue soundLifetimeTicks = GeneralConfig.SOUND_LIFETIME_TICKS;
        public final ForgeConfigSpec.DoubleValue arrivalDistance = GeneralConfig.ARRIVAL_DISTANCE;
        public final ForgeConfigSpec.DoubleValue followLeaderSpreadOutDistance = RaidConfig.FOLLOW_LEADER_SPREAD_OUT_DISTANCE;
        public final ForgeConfigSpec.DoubleValue followLeaderMinSoundWeightToSpreadOut = GeneralConfig.FOLLOW_LEADER_MIN_SOUND_WEIGHT_TO_SPREAD_OUT;
        public final ForgeConfigSpec.DoubleValue highSoundWeightTargetOverride = GeneralConfig.HIGH_SOUND_WEIGHT_TARGET_OVERRIDE;
        public final ForgeConfigSpec.DoubleValue mobMoveSpeed = GeneralConfig.MOB_MOVE_SPEED;
        public final ForgeConfigSpec.IntValue maxSoundsTracked = PerformanceConfig.MAX_SOUNDS_TRACKED;
        public final ForgeConfigSpec.DoubleValue soundSwitchRatio = GeneralConfig.SOUND_SWITCH_RATIO;
        public final ForgeConfigSpec.DoubleValue soundNoveltyBonusWeight = GeneralConfig.SOUND_NOVELTY_BONUS_WEIGHT;
        public final ForgeConfigSpec.IntValue soundNoveltyTimeTicks = GeneralConfig.SOUND_NOVELTY_TIME_TICKS;

        public final ForgeConfigSpec.BooleanValue enableRaycastCache = PerformanceConfig.ENABLE_RAYCAST_CACHE;
        public final ForgeConfigSpec.IntValue raycastCacheTtlTicks = PerformanceConfig.RAYCAST_CACHE_TTL_TICKS;
        public final ForgeConfigSpec.IntValue raycastCacheMaxEntries = PerformanceConfig.RAYCAST_CACHE_MAX_ENTRIES;
        public final ForgeConfigSpec.IntValue scanCooldownTicks = PerformanceConfig.SCAN_COOLDOWN_TICKS;
        public final ForgeConfigSpec.DoubleValue cooldownTicksPerMob = PerformanceConfig.COOLDOWN_TICKS_PER_MOB;
        public final ForgeConfigSpec.DoubleValue minTpsForScanCooldown = PerformanceConfig.MIN_TPS_FOR_SCAN_COOLDOWN;
        public final ForgeConfigSpec.DoubleValue maxTpsForScanCooldown = PerformanceConfig.MAX_TPS_FOR_SCAN_COOLDOWN;
        public final ForgeConfigSpec.BooleanValue enableTieredStealthPerformance = PerformanceConfig.ENABLE_TIERED_STEALTH_PERFORMANCE;
        public final ForgeConfigSpec.DoubleValue stealthTierSkipExpensiveChecksTps = PerformanceConfig.STEALTH_TIER_SKIP_EXPENSIVE_CHECKS_TPS;
        public final ForgeConfigSpec.DoubleValue stealthTierCurrentTargetsOnlyTps = PerformanceConfig.STEALTH_TIER_CURRENT_TARGETS_ONLY_TPS;
        public final ForgeConfigSpec.DoubleValue stealthTierSharedRangeTps = PerformanceConfig.STEALTH_TIER_SHARED_RANGE_TPS;
        public final ForgeConfigSpec.DoubleValue stealthTierVanillaTps = PerformanceConfig.STEALTH_TIER_VANILLA_TPS;
        public final ForgeConfigSpec.DoubleValue stealthShareTargetToNearbyMobsRadius = PerformanceConfig.STEALTH_SHARE_TARGET_TO_NEARBY_MOBS_RADIUS;
        public final ForgeConfigSpec.IntValue soundScoringSubmitCooldownTicks = PerformanceConfig.SOUND_SCORING_SUBMIT_COOLDOWN_TICKS;
        public final ForgeConfigSpec.IntValue asyncResultTtlTicks = PerformanceConfig.ASYNC_RESULT_TTL_TICKS;

        public final ForgeConfigSpec.IntValue maxGroupSize = GeneralConfig.MAX_GROUP_SIZE;
        public final ForgeConfigSpec.DoubleValue leaderGroupRadius = GeneralConfig.LEADER_GROUP_RADIUS;
        public final ForgeConfigSpec.DoubleValue groupDistance = GeneralConfig.GROUP_DISTANCE;
        public final ForgeConfigSpec.DoubleValue leaderSpacingMultiplier = GeneralConfig.LEADER_SPACING_MULTIPLIER;
        public final ForgeConfigSpec.IntValue numEdgeSectors = GeneralConfig.NUM_EDGE_SECTORS;
        public final ForgeConfigSpec.IntValue groupUpdateInterval = GeneralConfig.GROUP_UPDATE_INTERVAL;
        public final ForgeConfigSpec.IntValue maxLeaders = GeneralConfig.MAX_LEADERS;
        public final ForgeConfigSpec.IntValue edgeMobsPerSector = RaidConfig.EDGE_MOBS_PER_SECTOR;
        public final ForgeConfigSpec.DoubleValue groupSprintMultiplier = RaidConfig.GROUP_SPRINT_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue leaderReturnArrivalDistance = RaidConfig.LEADER_RETURN_ARRIVAL_DISTANCE;
        public final ForgeConfigSpec.IntValue raidCountdownTicks = RaidConfig.RAID_COUNTDOWN_TICKS;
        public final ForgeConfigSpec.BooleanValue skipSoundScanWhenHasTarget = GeneralConfig.SKIP_SOUND_SCAN_WHEN_HAS_TARGET;
        public final ForgeConfigSpec.IntValue maxMufflingRaycastsPerTick = GeneralConfig.MAX_MUFFLING_RAYCASTS_PER_TICK;
        public final ForgeConfigSpec.BooleanValue raidLeaderOnlySoundScan = RaidConfig.RAID_LEADER_ONLY_SOUND_SCAN;
        public final ForgeConfigSpec.BooleanValue enableFlowField = PathfindingConfig.ENABLE_FLOW_FIELD;
        public final ForgeConfigSpec.IntValue flowFieldMobThreshold = PathfindingConfig.FLOW_FIELD_MOB_THRESHOLD;
        public final ForgeConfigSpec.IntValue moveToCooldownTicks = PathfindingConfig.MOVE_TO_COOLDOWN_TICKS;
        public final ForgeConfigSpec.DoubleValue moveToMinDelta = PathfindingConfig.MOVE_TO_MIN_DELTA;
        public final ForgeConfigSpec.IntValue moveToTeamBudgetPerTick = PathfindingConfig.MOVE_TO_TEAM_BUDGET_PER_TICK;
        public final ForgeConfigSpec.IntValue maxPathAttemptsPerTick = PathfindingConfig.MAX_PATH_ATTEMPTS_PER_TICK;
        public final ForgeConfigSpec.BooleanValue enableNodeRouter = PathfindingConfig.ENABLE_NODE_ROUTER;
        public final ForgeConfigSpec.BooleanValue enableSoundRaid = RaidConfig.ENABLE_SOUND_RAID;
        public final ForgeConfigSpec.BooleanValue enableScentRaid = RaidConfig.ENABLE_SCENT_RAID;
        public final ForgeConfigSpec.BooleanValue enableRaidReinforcements = RaidConfig.ENABLE_RAID_REINFORCEMENTS;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> raidReinforcementCaps = RaidConfig.RAID_REINFORCEMENT_CAPS;
        public final ForgeConfigSpec.IntValue raidMaxTotalMobs = RaidConfig.RAID_MAX_TOTAL_MOBS;
        public final ForgeConfigSpec.DoubleValue raidReinforcementDifficultyMultiplier = RaidConfig.RAID_REINFORCEMENT_DIFFICULTY_MULTIPLIER;
        public final ForgeConfigSpec.IntValue raidReinforcementInterval = RaidConfig.RAID_REINFORCEMENT_INTERVAL;
        public final ForgeConfigSpec.ConfigValue<String> raidEdgeDetectionAction = RaidConfig.RAID_EDGE_DETECTION_ACTION;
        public final ForgeConfigSpec.BooleanValue raidFollowerInheritTarget = RaidConfig.RAID_FOLLOWER_INHERIT_TARGET;
        public final ForgeConfigSpec.IntValue initialGroupComputationDelay = PerformanceConfig.INITIAL_GROUP_COMPUTATION_DELAY;

        public final ForgeConfigSpec.IntValue workerThreads = PerformanceConfig.WORKER_THREADS;
        public final ForgeConfigSpec.IntValue workerTaskBudgetMs = PerformanceConfig.WORKER_TASK_BUDGET_MS;

        public final ForgeConfigSpec.BooleanValue enableQuantifiedIntegration = IntegrationConfig.ENABLE_QUANTIFIED_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableSmartBrainLibIntegration = IntegrationConfig.ENABLE_SMART_BRAIN_LIB_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableCustomNpcsIntegration = IntegrationConfig.ENABLE_CUSTOM_NPCS_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableQuantifiedCacheIntegration = IntegrationConfig.ENABLE_QUANTIFIED_CACHE_INTEGRATION;
        public final ForgeConfigSpec.IntValue quantifiedCacheMemoryLimitMB = IntegrationConfig.QUANTIFIED_CACHE_MEMORY_LIMIT_MB;
        public final ForgeConfigSpec.BooleanValue disableQuantifiedCacheOnMemoryPressure = IntegrationConfig.DISABLE_QUANTIFIED_CACHE_ON_MEMORY_PRESSURE;
        public final ForgeConfigSpec.BooleanValue triggerQuantifiedCacheCleanupOnMemoryPressure = IntegrationConfig.TRIGGER_QUANTIFIED_CACHE_CLEANUP_ON_MEMORY_PRESSURE;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> attractedEntities = GeneralConfig.ATTRACTED_ENTITIES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> stealthBypassMobIds = GeneralConfig.STEALTH_BYPASS_MOB_IDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> stealthBypassModNamespaces = GeneralConfig.STEALTH_BYPASS_MOD_NAMESPACES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mobBlacklist = GeneralConfig.MOB_BLACKLIST;


        public final ForgeConfigSpec.ConfigValue<List<? extends String>> specialMobProfilesRaw = GeneralConfig.SPECIAL_MOB_PROFILES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> specialPlayerProfilesRaw = GeneralConfig.SPECIAL_PLAYER_PROFILES;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> soundIdWhitelist = GeneralConfig.SOUND_ID_WHITELIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> rawSoundDefaults = GeneralConfig.SOUND_DEFAULTS;


        public final ForgeConfigSpec.BooleanValue enableBlockMuffling = GeneralConfig.ENABLE_BLOCK_MUFFLING;
        public final ForgeConfigSpec.IntValue maxMufflingBlocksToCheck = PerformanceConfig.MAX_MUFFLING_BLOCKS_TO_CHECK;
        public final ForgeConfigSpec.DoubleValue mufflingFactorWool = GeneralConfig.MUFFLING_FACTOR_WOOL;
        public final ForgeConfigSpec.DoubleValue mufflingFactorSolid = GeneralConfig.MUFFLING_FACTOR_SOLID;
        public final ForgeConfigSpec.DoubleValue mufflingFactorNonSolid = GeneralConfig.MUFFLING_FACTOR_NON_SOLID;
        public final ForgeConfigSpec.DoubleValue mufflingFactorThin = GeneralConfig.MUFFLING_FACTOR_THIN;
        public final ForgeConfigSpec.DoubleValue mufflingFactorLiquid = GeneralConfig.MUFFLING_FACTOR_LIQUID;
        public final ForgeConfigSpec.DoubleValue mufflingFactorAir = GeneralConfig.MUFFLING_FACTOR_AIR;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks = GeneralConfig.CUSTOM_WOOL_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks = GeneralConfig.CUSTOM_SOLID_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks = GeneralConfig.CUSTOM_NON_SOLID_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customThinBlocks = GeneralConfig.CUSTOM_THIN_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customLiquidBlocks = GeneralConfig.CUSTOM_LIQUID_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customAirBlocks = GeneralConfig.CUSTOM_AIR_BLOCKS;

        public final ForgeConfigSpec.BooleanValue enableStealthMechanics = StealthConfig.ENABLE_STEALTH_MECHANICS;
        public final ForgeConfigSpec.IntValue stealthCheckInterval = StealthConfig.STEALTH_CHECK_INTERVAL;
        public final ForgeConfigSpec.IntValue stealthGracePeriodTicks = StealthConfig.STEALTH_GRACE_PERIOD_TICKS;
        public final ForgeConfigSpec.DoubleValue minStealthDetectionRange = StealthConfig.MIN_STEALTH_DETECTION_RANGE;
        public final ForgeConfigSpec.DoubleValue maxStealthDetectionRange = StealthConfig.MAX_STEALTH_DETECTION_RANGE;

        public final ForgeConfigSpec.DoubleValue standingDetectionRangePlayer = StealthConfig.STANDING_DETECTION_RANGE_PLAYER;
        public final ForgeConfigSpec.DoubleValue sneakingDetectionRangePlayer = StealthConfig.SNEAKING_DETECTION_RANGE_PLAYER;
        public final ForgeConfigSpec.DoubleValue crawlingDetectionRangePlayer = StealthConfig.CRAWLING_DETECTION_RANGE_PLAYER;

        public final ForgeConfigSpec.IntValue neutralLightLevel = StealthConfig.NEUTRAL_LIGHT_LEVEL;
        public final ForgeConfigSpec.DoubleValue lightLevelSensitivity = StealthConfig.LIGHT_LEVEL_SENSITIVITY;
        public final ForgeConfigSpec.DoubleValue minLightFactor = StealthConfig.MIN_LIGHT_FACTOR;
        public final ForgeConfigSpec.DoubleValue maxLightFactor = StealthConfig.MAX_LIGHT_FACTOR;
        public final ForgeConfigSpec.IntValue lightSampleRadiusHorizontal = StealthConfig.LIGHT_SAMPLE_RADIUS_HORIZONTAL;
        public final ForgeConfigSpec.IntValue lightSampleRadiusVertical = StealthConfig.LIGHT_SAMPLE_RADIUS_VERTICAL;
        public final ForgeConfigSpec.DoubleValue rainStealthFactor = StealthConfig.RAIN_STEALTH_FACTOR;
        public final ForgeConfigSpec.DoubleValue thunderStealthFactor = StealthConfig.THUNDER_STEALTH_FACTOR;

        public final ForgeConfigSpec.DoubleValue movementStealthPenalty = StealthConfig.MOVEMENT_STEALTH_PENALTY;
        public final ForgeConfigSpec.DoubleValue stationaryStealthBonusFactor = StealthConfig.STATIONARY_STEALTH_BONUS_FACTOR;
        public final ForgeConfigSpec.DoubleValue movementThreshold = StealthConfig.MOVEMENT_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue invisibilityStealthFactor = StealthConfig.INVISIBILITY_STEALTH_FACTOR;

        public final ForgeConfigSpec.BooleanValue enableCamouflage = StealthConfig.ENABLE_CAMOUFLAGE;
        public final ForgeConfigSpec.BooleanValue enableHeldItemPenalty = StealthConfig.ENABLE_HELD_ITEM_PENALTY;
        public final ForgeConfigSpec.DoubleValue heldItemPenaltyFactor = StealthConfig.HELD_ITEM_PENALTY_FACTOR;
        public final ForgeConfigSpec.BooleanValue enableEnchantmentPenalty = StealthConfig.ENABLE_ENCHANTMENT_PENALTY;
        public final ForgeConfigSpec.DoubleValue armorEnchantmentPenaltyFactor = StealthConfig.ARMOR_ENCHANTMENT_PENALTY_FACTOR;
        public final ForgeConfigSpec.DoubleValue heldItemEnchantmentPenaltyFactor = StealthConfig.HELD_ITEM_ENCHANTMENT_PENALTY_FACTOR;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> camouflageArmorItems = StealthConfig.CAMOUFLAGE_ARMOR_ITEMS;
        public final ForgeConfigSpec.BooleanValue requireFullSetForCamouflageBonus = StealthConfig.REQUIRE_FULL_SET_FOR_CAMOUFLAGE_BONUS;
        public final ForgeConfigSpec.DoubleValue fullArmorStealthBonus = StealthConfig.FULL_ARMOR_STEALTH_BONUS;
        public final ForgeConfigSpec.DoubleValue helmetCamouflageEffectiveness = StealthConfig.HELMET_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue chestplateCamouflageEffectiveness = StealthConfig.CHESTPLATE_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue leggingsCamouflageEffectiveness = StealthConfig.LEGGINGS_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue bootsCamouflageEffectiveness = StealthConfig.BOOTS_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue maxCamouflageEffectivenessCap = StealthConfig.MAX_CAMOUFLAGE_EFFECTIVENESS_CAP;
        public final ForgeConfigSpec.BooleanValue allowPartialBonusIfFullSetRequired = StealthConfig.ALLOW_PARTIAL_BONUS_IF_FULL_SET_REQUIRED;

        public final ForgeConfigSpec.BooleanValue enableEnvironmentalCamouflage = StealthConfig.ENABLE_ENVIRONMENTAL_CAMOUFLAGE;
        public final ForgeConfigSpec.DoubleValue environmentalCamouflageMaxEffectiveness = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_MAX_EFFECTIVENESS;
        public final ForgeConfigSpec.IntValue environmentalCamouflageColorMatchThreshold = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_COLOR_MATCH_THRESHOLD;
        public final ForgeConfigSpec.BooleanValue environmentalCamouflageOnlyDyedLeather = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_ONLY_DYED_LEATHER;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customArmorColors = StealthConfig.CUSTOM_ARMOR_COLORS;
        public final ForgeConfigSpec.IntValue envColorSampleRadius = StealthConfig.ENV_COLOR_SAMPLE_RADIUS;
        public final ForgeConfigSpec.IntValue envColorSampleYOffsetStart = StealthConfig.ENV_COLOR_SAMPLE_Y_OFFSET_START;
        public final ForgeConfigSpec.IntValue envColorSampleYOffsetEnd = StealthConfig.ENV_COLOR_SAMPLE_Y_OFFSET_END;
        public final ForgeConfigSpec.BooleanValue enableEnvironmentalMismatchPenalty = StealthConfig.ENABLE_ENVIRONMENTAL_MISMATCH_PENALTY;
        public final ForgeConfigSpec.DoubleValue environmentalMismatchPenaltyFactor = StealthConfig.ENVIRONMENTAL_MISMATCH_PENALTY_FACTOR;
        public final ForgeConfigSpec.IntValue environmentalMismatchThreshold = StealthConfig.ENVIRONMENTAL_MISMATCH_THRESHOLD;

        public final ForgeConfigSpec.BooleanValue enableTaczIntegration = GunsConfig.ENABLE_TACZ_INTEGRATION;
        public final ForgeConfigSpec.DoubleValue taczReloadRange = GunsConfig.TACZ_RELOAD_RANGE;
        public final ForgeConfigSpec.DoubleValue taczReloadWeight = GunsConfig.TACZ_RELOAD_WEIGHT;
        public final ForgeConfigSpec.DoubleValue taczShootRange = GunsConfig.TACZ_SHOOT_RANGE;
        public final ForgeConfigSpec.DoubleValue taczShootWeight = GunsConfig.TACZ_SHOOT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels = GunsConfig.TACZ_GUN_SHOOT_DECIBELS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions = GunsConfig.TACZ_ATTACHMENT_REDUCTIONS;
        public final ForgeConfigSpec.DoubleValue taczAttachmentReductionDefault = GunsConfig.TACZ_ATTACHMENT_REDUCTION_DEFAULT;
        public final ForgeConfigSpec.DoubleValue gunshotBaseDetectionRange = GunsConfig.GUNSHOT_BASE_DETECTION_RANGE;
        public final ForgeConfigSpec.IntValue gunshotDetectionDurationTicks = GunsConfig.GUNSHOT_DETECTION_DURATION_TICKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczMuzzleFlashReductions = GunsConfig.TACZ_MUZZLE_FLASH_REDUCTIONS;
        public final ForgeConfigSpec.DoubleValue taczAttachmentFlashReductionDefault = GunsConfig.TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT;

        public final ForgeConfigSpec.BooleanValue enablePointBlankIntegration = GunsConfig.ENABLE_POINT_BLANK_INTEGRATION;
        public final ForgeConfigSpec.DoubleValue pointBlankReloadRange = GunsConfig.POINT_BLANK_RELOAD_RANGE;
        public final ForgeConfigSpec.DoubleValue pointBlankReloadWeight = GunsConfig.POINT_BLANK_RELOAD_WEIGHT;
        public final ForgeConfigSpec.DoubleValue pointBlankShootRange = GunsConfig.POINT_BLANK_SHOOT_RANGE;
        public final ForgeConfigSpec.DoubleValue pointBlankShootWeight = GunsConfig.POINT_BLANK_SHOOT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankGunShootRanges = GunsConfig.POINT_BLANK_GUN_SHOOT_RANGES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankAttachmentSoundReductions = GunsConfig.POINT_BLANK_ATTACHMENT_SOUND_REDUCTIONS;
        public final ForgeConfigSpec.DoubleValue pointBlankAttachmentReductionDefault = GunsConfig.POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankMuzzleFlashReductions = GunsConfig.POINT_BLANK_MUZZLE_FLASH_REDUCTIONS;

        public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration = VoiceConfig.ENABLE_VOICE_CHAT_INTEGRATION;
        public final ForgeConfigSpec.IntValue voiceChatWhisperRange = VoiceConfig.VOICE_CHAT_WHISPER_RANGE;
        public final ForgeConfigSpec.IntValue voiceChatNormalRange = VoiceConfig.VOICE_CHAT_NORMAL_RANGE;
        public final ForgeConfigSpec.DoubleValue voiceChatWeight = VoiceConfig.VOICE_CHAT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap = VoiceConfig.VOICE_CHAT_DB_THRESHOLD_MAP;

        public final ForgeConfigSpec.DoubleValue defaultHorizontalFov = StealthConfig.DEFAULT_HORIZONTAL_FOV;
        public final ForgeConfigSpec.DoubleValue defaultVerticalFov = StealthConfig.DEFAULT_VERTICAL_FOV;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fovOverrides = StealthConfig.CUSTOM_FOV_OVERRIDES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fovExclusionList = StealthConfig.FOV_EXCLUSION_LIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> nonBlockingVisionAllowList = StealthConfig.NON_BLOCKING_VISION_ALLOW_LIST;

        public final ForgeConfigSpec.BooleanValue enableBlockBreaking = IntegrationConfig.ENABLE_BLOCK_BREAKING;
        public final ForgeConfigSpec.DoubleValue blockBreakingTimeMultiplier = IntegrationConfig.BLOCK_BREAKING_TIME_MULTIPLIER;
        public final ForgeConfigSpec.BooleanValue blockBreakingToolOnly = IntegrationConfig.BLOCK_BREAKING_TOOL_ONLY;
        public final ForgeConfigSpec.BooleanValue blockBreakingProperToolOnly = IntegrationConfig.BLOCK_BREAKING_PROPER_TOOL_ONLY;

        public final ForgeConfigSpec.BooleanValue enableTeleportToSound = IntegrationConfig.ENABLE_TELEPORT_TO_SOUND;
        public final ForgeConfigSpec.DoubleValue teleportChance = IntegrationConfig.TELEPORT_CHANCE;
        public final ForgeConfigSpec.IntValue teleportCooldownTicks = IntegrationConfig.TELEPORT_COOLDOWN_TICKS;
        public final ForgeConfigSpec.ConfigValue<String> teleportCanTeleportTag = IntegrationConfig.TELEPORT_CAN_TELEPORT_TAG;
        public final ForgeConfigSpec.ConfigValue<String> teleportCanBeTeleportedTag = IntegrationConfig.TELEPORT_CAN_BE_TELEPORTED_TAG;

        public final ForgeConfigSpec.BooleanValue enablePickUpAndThrowToSound = IntegrationConfig.ENABLE_PICK_UP_AND_THROW_TO_SOUND;
        public final ForgeConfigSpec.DoubleValue pickUpChance = IntegrationConfig.PICK_UP_CHANCE;
        public final ForgeConfigSpec.IntValue pickUpCooldownTicks = IntegrationConfig.PICK_UP_COOLDOWN_TICKS;
        public final ForgeConfigSpec.IntValue pickUpMinDistanceToPickUp = IntegrationConfig.PICK_UP_MIN_DISTANCE_TO_PICK_UP;
        public final ForgeConfigSpec.IntValue pickUpMaxDistanceToThrow = IntegrationConfig.PICK_UP_MAX_DISTANCE_TO_THROW;
        public final ForgeConfigSpec.DoubleValue pickUpSpeedModifier = IntegrationConfig.PICK_UP_SPEED_MODIFIER;
        public final ForgeConfigSpec.ConfigValue<String> pickUpCanPickUpTag = IntegrationConfig.PICK_UP_CAN_PICK_UP_TAG;
        public final ForgeConfigSpec.ConfigValue<String> pickUpCanBePickedUpTag = IntegrationConfig.PICK_UP_CAN_BE_PICKED_UP_TAG;

        public final ForgeConfigSpec.BooleanValue enableXrayTargeting = IntegrationConfig.ENABLE_XRAY_TARGETING;
        public final ForgeConfigSpec.ConfigValue<String> xrayApplyTag = IntegrationConfig.XRAY_APPLY_TAG;
        public final ForgeConfigSpec.BooleanValue xrayRequireBetterNearby = IntegrationConfig.XRAY_REQUIRE_BETTER_NEARBY;
        public final ForgeConfigSpec.ConfigValue<String> xrayBetterNearbyTag = IntegrationConfig.XRAY_BETTER_NEARBY_TAG;
        public final ForgeConfigSpec.IntValue xrayMinRange = IntegrationConfig.XRAY_MIN_RANGE;
        public final ForgeConfigSpec.IntValue xrayMaxRange = IntegrationConfig.XRAY_MAX_RANGE;
        public final ForgeConfigSpec.DoubleValue xrayChance = IntegrationConfig.XRAY_CHANCE;

        public final ForgeConfigSpec.IntValue configSchemaVersion = GeneralConfig.CONFIG_SCHEMA_VERSION;

        public final ForgeConfigSpec.BooleanValue enableOptimizedLos = PerformanceConfig.ENABLE_OPTIMIZED_LOS;
        public final ForgeConfigSpec.BooleanValue enableOptimizedLosPairCache = PerformanceConfig.ENABLE_OPTIMIZED_LOS_PAIR_CACHE;
        public final ForgeConfigSpec.IntValue optimizedLosPairCacheMaxEntries = PerformanceConfig.OPTIMIZED_LOS_PAIR_CACHE_MAX_ENTRIES;
        public final ForgeConfigSpec.BooleanValue enableOptimizedLosVanillaFallback = PerformanceConfig.ENABLE_OPTIMIZED_LOS_VANILLA_FALLBACK;

        public final ForgeConfigSpec.BooleanValue enableLosBatching = PerformanceConfig.ENABLE_LOS_BATCHING;
        public final ForgeConfigSpec.IntValue losBatchBudgetPerTick = PerformanceConfig.LOS_BATCH_BUDGET_PER_TICK;
        public final ForgeConfigSpec.IntValue losBatchQueueMaxSize = PerformanceConfig.LOS_BATCH_QUEUE_MAX_SIZE;

        public final ForgeConfigSpec.BooleanValue enableLivingEntityLosOverride = PerformanceConfig.ENABLE_LIVING_ENTITY_LOS_OVERRIDE;

        public final ForgeConfigSpec.IntValue globalCacheMaxSize = PerformanceConfig.GLOBAL_CACHE_MAX_SIZE;
        public final ForgeConfigSpec.IntValue globalCacheExpireMins = PerformanceConfig.GLOBAL_CACHE_EXPIRE_MINS;

        public final ForgeConfigSpec.BooleanValue enableRelentlessClimbing = IntegrationConfig.ENABLE_RELENTLESS_CLIMBING;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> relentlessEligibleMobs = IntegrationConfig.RELENTLESS_ELIGIBLE_MOBS;
        public final ForgeConfigSpec.BooleanValue zombiesIgnoreHeight = IntegrationConfig.ZOMBIES_IGNORE_HEIGHT;
        public final ForgeConfigSpec.BooleanValue zombiesCanStack = IntegrationConfig.ZOMBIES_CAN_STACK;
        public final ForgeConfigSpec.DoubleValue zombieFallDamageMultiplier = IntegrationConfig.ZOMBIE_FALL_DAMAGE_MULTIPLIER;

        public final ForgeConfigSpec.BooleanValue enableImmersiveMelodiesIntegration = IntegrationConfig.ENABLE_IMMERSIVE_MELODIES_INTEGRATION;
        public final ForgeConfigSpec.IntValue immersiveMelodiesPollInterval = IntegrationConfig.IMMERSIVE_MELODIES_POLL_INTERVAL;
        public final ForgeConfigSpec.DoubleValue immersiveMelodiesDefaultRange = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_RANGE;
        public final ForgeConfigSpec.DoubleValue immersiveMelodiesDefaultWeight = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> immersiveMelodiesInstrumentMultipliers = IntegrationConfig.IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> immersiveMelodiesMelodyOverrides = IntegrationConfig.IMMERSIVE_MELODIES_MELODY_OVERRIDES;

        public final ForgeConfigSpec.BooleanValue enableScentSystem = ScentConfig.ENABLE_SCENT_SYSTEM;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> scentEligibleMobs = ScentConfig.SCENT_ELIGIBLE_MOBS;
        public final ForgeConfigSpec.BooleanValue enableScentParticles = ScentConfig.ENABLE_SCENT_PARTICLES;
        public final ForgeConfigSpec.BooleanValue enableGameplayScentParticles = ScentConfig.ENABLE_GAMEPLAY_SCENT_PARTICLES;
        public final ForgeConfigSpec.IntValue scentParticleSpawnInterval = ScentConfig.SCENT_PARTICLE_SPAWN_INTERVAL;
        public final ForgeConfigSpec.DoubleValue scentParticleRenderDistance = ScentConfig.SCENT_PARTICLE_RENDER_DISTANCE;
        public final ForgeConfigSpec.IntValue scentNodeDurationTicks = ScentConfig.SCENT_NODE_DURATION_TICKS;
        public final ForgeConfigSpec.DoubleValue scentCreationIntervalBlocks = ScentConfig.SCENT_CREATION_INTERVAL_BLOCKS;
        public final ForgeConfigSpec.DoubleValue tempHeavyDecayThreshold = ScentConfig.TEMP_HEAVY_DECAY_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue tempFreezeThreshold = ScentConfig.TEMP_FREEZE_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue humidityPreserveThreshold = ScentConfig.HUMIDITY_PRESERVE_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue rainDecayMultiplier = ScentConfig.RAIN_DECAY_MULTIPLIER;
        public final ForgeConfigSpec.BooleanValue waterStopsScent = ScentConfig.WATER_STOPS_SCENT;
        public final ForgeConfigSpec.IntValue scentAmbushDurationTicks = ScentConfig.SCENT_AMBUSH_DURATION_TICKS;
        public final ForgeConfigSpec.IntValue scentScanBudgetPerTick = ScentConfig.SCENT_SCAN_BUDGET_PER_TICK;
        public Common(ForgeConfigSpec.Builder builder) {





        }

    }

    @SuppressWarnings("unchecked")
    private static <T> void moveConfigValue(com.electronwill.nightconfig.core.UnmodifiableConfig config, String oldPath, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<T> newValue) {
        if (config.contains(oldPath)) {
            Object oldValue = config.get(oldPath);

            newValue.set((T) oldValue);
            SoundAttractMod.LOGGER.info("Migrated config value '{}' -> '{}'", oldPath, String.join(".", newValue.getPath()));
        }
    }

    public static void bakeConfig() {



        SOUND_ID_WHITELIST_CACHE.clear();
        COMMON.soundIdWhitelist.get().forEach(idStr -> {
            ResourceLocation loc = ResourceLocation.tryParse(idStr);
            if (loc != null) {
                SOUND_ID_WHITELIST_CACHE.add(loc);
            } else {
                SoundAttractMod.LOGGER.warn("Invalid ResourceLocation in soundIdWhitelist: {}", idStr);
            }
        });
        ATTRACTED_ENTITY_TYPES_CACHE.clear();
        if (COMMON.attractedEntities != null) {
            COMMON.attractedEntities.get().forEach(id -> ATTRACTED_ENTITY_TYPES_CACHE.add(id.toString()));
        }

        STEALTH_BYPASS_ENTITY_TYPES_CACHE.clear();
        if (COMMON.stealthBypassMobIds != null) {
            for (String entry : COMMON.stealthBypassMobIds.get()) {
                if (entry == null || entry.trim().isEmpty()) continue;
                ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
                if (loc != null) {
                    STEALTH_BYPASS_ENTITY_TYPES_CACHE.add(loc);
                } else {
                    SoundAttractMod.LOGGER.warn("Invalid ResourceLocation in stealthBypassMobIds: {}", entry);
                }
            }
        }
        STEALTH_BYPASS_MOD_NAMESPACES_CACHE.clear();
        if (COMMON.stealthBypassModNamespaces != null) {
            for (String entry : COMMON.stealthBypassModNamespaces.get()) {
                if (entry == null) continue;
                String cleaned = entry.trim().toLowerCase(Locale.ROOT);
                if (cleaned.isEmpty()) continue;
                STEALTH_BYPASS_MOD_NAMESPACES_CACHE.add(cleaned);
            }
        }

        SOUND_DEFAULT_ENTRIES_CACHE.clear();
        if (COMMON.rawSoundDefaults != null) {
            COMMON.rawSoundDefaults.get().forEach(entry -> {
                String[] parts = entry.split(";");
                if (parts.length == 3) {
                    ResourceLocation soundId = ResourceLocation.tryParse(parts[0]);
                    try {
                        double range = Double.parseDouble(parts[1]);
                        double weight = Double.parseDouble(parts[2]);
                        if (soundId != null) {
                            SOUND_DEFAULT_ENTRIES_CACHE.put(soundId, new SoundDefaultEntry(range, weight));
                        }
                    } catch (NumberFormatException e) {
                        SoundAttractMod.LOGGER.warn("Could not parse range/weight for sound default entry: {}", entry, e);
                    }
                }
            });
        }

        boolean enableDataDriven = COMMON.enableDataDriven.get();
        String priority = COMMON.datapackPriority.get();
        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);

        if (enableDataDriven && !DP_SOUND_WHITELIST_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                SOUND_ID_WHITELIST_CACHE.clear();
                SOUND_ID_WHITELIST_CACHE.addAll(DP_SOUND_WHITELIST_CACHE);
            } else {
                for (ResourceLocation loc : DP_SOUND_WHITELIST_CACHE) {
                    SOUND_ID_WHITELIST_CACHE.add(loc);
                }
            }
        }

        if (enableDataDriven && !DP_SOUND_DEFAULTS_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                for (Map.Entry<ResourceLocation, SoundDefaultEntry> e : DP_SOUND_DEFAULTS_CACHE.entrySet()) {
                    SOUND_DEFAULT_ENTRIES_CACHE.put(e.getKey(), e.getValue());
                }
            } else {
                for (Map.Entry<ResourceLocation, SoundDefaultEntry> e : DP_SOUND_DEFAULTS_CACHE.entrySet()) {
                    SOUND_DEFAULT_ENTRIES_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        CUSTOM_LIQUID_BLOCKS_CACHE.clear();
        if (COMMON.customLiquidBlocks != null) {
            COMMON.customLiquidBlocks.get().forEach(id -> CUSTOM_LIQUID_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_WOOL_BLOCKS_CACHE.clear();
        if (COMMON.customWoolBlocks != null) {
            COMMON.customWoolBlocks.get().forEach(id -> CUSTOM_WOOL_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_SOLID_BLOCKS_CACHE.clear();
        if (COMMON.customSolidBlocks != null) {
            COMMON.customSolidBlocks.get().forEach(id -> CUSTOM_SOLID_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_NON_SOLID_BLOCKS_CACHE.clear();
        if (COMMON.customNonSolidBlocks != null) {
            COMMON.customNonSolidBlocks.get().forEach(id -> CUSTOM_NON_SOLID_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_THIN_BLOCKS_CACHE.clear();
        if (COMMON.customThinBlocks != null) {
            COMMON.customThinBlocks.get().forEach(id -> CUSTOM_THIN_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_AIR_BLOCKS_CACHE.clear();
        if (COMMON.customAirBlocks != null) {
            COMMON.customAirBlocks.get().forEach(id -> CUSTOM_AIR_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }

        parseAndCacheNonBlockingVisionAllowList();

        TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && COMMON.enableTaczIntegration.get();
        TACZ_RELOAD_RANGE_CACHE = COMMON.taczReloadRange.get();
        TACZ_RELOAD_WEIGHT_CACHE = COMMON.taczReloadWeight.get();
        TACZ_SHOOT_RANGE_CACHE = COMMON.taczShootRange.get();
        TACZ_SHOOT_WEIGHT_CACHE = COMMON.taczShootWeight.get();
        TACZ_ATTACHMENT_REDUCTION_DEFAULT_CACHE = COMMON.taczAttachmentReductionDefault.get();
        TACZ_GUN_SHOOT_DB_CACHE.clear();
        List<? extends String> rawShoot = COMMON.taczGunShootDecibels.get();
        for (String raw : rawShoot) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
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
                String idStr = parts[0].trim();
                ResourceLocation rl = ResourceLocation.tryParse(idStr);
                double reduction = Double.parseDouble(parts[1]);
                if (rl != null) {
                    TACZ_ATTACHMENT_REDUCTION_DB_CACHE.put(rl.toString(), reduction);
                }
            } catch (Exception e) {
            }
        }

        TACZ_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        List<? extends String> rawFlashAtt = COMMON.taczMuzzleFlashReductions.get();
        for (String raw : rawFlashAtt) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double reductionValue = Double.parseDouble(parts[1]);
                if (rl != null) {
                    TACZ_MUZZLE_FLASH_REDUCTION_CACHE.put(rl, reductionValue);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse muzzle flash reduction entry: {}", raw, e);
            }
        }

        TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE = COMMON.taczAttachmentFlashReductionDefault.get();

        if (enableDataDriven && !DP_TACZ_GUN_SHOOT_DB_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                TACZ_GUN_SHOOT_DB_CACHE.clear();
                TACZ_GUN_SHOOT_DB_CACHE.putAll(DP_TACZ_GUN_SHOOT_DB_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Pair<Double, Double>> e : DP_TACZ_GUN_SHOOT_DB_CACHE.entrySet()) {
                    TACZ_GUN_SHOOT_DB_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
                TACZ_ATTACHMENT_REDUCTION_DB_CACHE.putAll(DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE);
            } else {
                for (Map.Entry<String, Double> e : DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE.entrySet()) {
                    TACZ_ATTACHMENT_REDUCTION_DB_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                TACZ_MUZZLE_FLASH_REDUCTION_CACHE.clear();
                TACZ_MUZZLE_FLASH_REDUCTION_CACHE.putAll(DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE.entrySet()) {
                    TACZ_MUZZLE_FLASH_REDUCTION_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        parseAndCacheCustomArmorColors();

        POINT_BLANK_ENABLED_CACHE = net.minecraftforge.fml.ModList.get().isLoaded("pointblank") && COMMON.enablePointBlankIntegration.get();
        POINT_BLANK_RELOAD_RANGE_CACHE = COMMON.pointBlankReloadRange.get();
        POINT_BLANK_RELOAD_WEIGHT_CACHE = COMMON.pointBlankReloadWeight.get();
        POINT_BLANK_SHOOT_RANGE_CACHE = COMMON.pointBlankShootRange.get();
        POINT_BLANK_SHOOT_WEIGHT_CACHE = COMMON.pointBlankShootWeight.get();

        POINT_BLANK_GUN_RANGE_CACHE.clear();
        java.util.List<? extends String> pbGunRanges = COMMON.pointBlankGunShootRanges.get();
        for (String raw : pbGunRanges) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double range = Double.parseDouble(parts[1]);
                if (rl != null) {
                    POINT_BLANK_GUN_RANGE_CACHE.put(rl, range);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse Point Blank gun range entry: {}", raw, e);
            }
        }

        POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.clear();
        java.util.List<? extends String> pbAttachRed = COMMON.pointBlankAttachmentSoundReductions.get();
        for (String raw : pbAttachRed) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double reduction = Double.parseDouble(parts[1]);
                if (rl != null) {
                    POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.put(rl, reduction);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse Point Blank attachment reduction entry: {}", raw, e);
            }
        }
        POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT_CACHE = COMMON.pointBlankAttachmentReductionDefault.get();

        POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        java.util.List<? extends String> pbFlashRed = COMMON.pointBlankMuzzleFlashReductions.get();
        for (String raw : pbFlashRed) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double reduction = Double.parseDouble(parts[1]);
                if (rl != null) {
                    POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.put(rl, reduction);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse Point Blank muzzle flash reduction entry: {}", raw, e);
            }
        }

        if (enableDataDriven && !DP_POINT_BLANK_GUN_RANGE_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                POINT_BLANK_GUN_RANGE_CACHE.clear();
                POINT_BLANK_GUN_RANGE_CACHE.putAll(DP_POINT_BLANK_GUN_RANGE_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_POINT_BLANK_GUN_RANGE_CACHE.entrySet()) {
                    POINT_BLANK_GUN_RANGE_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.clear();
                POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.putAll(DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.entrySet()) {
                    POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.clear();
                POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.putAll(DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.entrySet()) {
                    POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        List<com.example.soundattract.config.MobProfile2> tmpMobProfiles = new ArrayList<>();
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

                net.minecraft.resources.ResourceLocation mobId = null;
                net.minecraft.nbt.CompoundTag nbtMatcher = null;
                Map<net.minecraft.resources.ResourceLocation, com.example.soundattract.config.SoundOverride> soundOverrides = new HashMap<>();
                Map<PlayerStance, Double> detectionOverrides = new HashMap<>();

                String mobIdString = parts[1].trim();
                net.minecraft.advancements.critereon.EntityPredicate.Builder predicateBuilder = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity();
                
                if (!mobIdString.isEmpty() && !mobIdString.equals("*")) {
                    mobId = net.minecraft.resources.ResourceLocation.tryParse(mobIdString);
                    if (mobId != null) {
                         net.minecraft.world.entity.EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                         if (type != null && type != net.minecraft.world.entity.EntityType.PIG && !mobId.equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(net.minecraft.world.entity.EntityType.PIG))) {
                             predicateBuilder.of(type);
                         } else if (type == null) {
                            SoundAttractMod.LOGGER.warn("Invalid or missing mob ID '{}' for profile '{}'.", mobIdString, profileName);
                         }
                    } else {
                        SoundAttractMod.LOGGER.warn("Invalid mob ID format '{}' for profile '{}'. Matching will cover all mobs if allowed.", mobIdString, profileName);
                    }
                }

                String nbtMatcherString = parts[2].trim();
                if (!nbtMatcherString.isEmpty()) {
                    try {
                        nbtMatcher = net.minecraft.nbt.TagParser.parseTag(nbtMatcherString);
                        predicateBuilder.nbt(new net.minecraft.advancements.critereon.NbtPredicate(nbtMatcher));
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
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
                            com.example.soundattract.config.SoundOverride so = com.example.soundattract.config.SoundOverride.parse(raw);
                            soundOverrides.put(so.getSoundId(), so);
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
                                PlayerStance stance = PlayerStance.valueOf(doParts[0].trim().toUpperCase(java.util.Locale.ROOT));
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
                

                com.example.soundattract.config.MobProfile2 profile = new com.example.soundattract.config.MobProfile2(
                    profileName,
                    java.util.Optional.of(predicateBuilder.build()),
                    new ArrayList<>(soundOverrides.values()),
                    detectionOverrides,
                    java.util.Optional.empty()
                );

                tmpMobProfiles.add(profile);
                if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached LEGACY mob profile: {}", profileName);
                }
            }
        }
        boolean enableDataDrivenProfiles = COMMON != null && COMMON.enableDataDriven != null && COMMON.enableDataDriven.get();
        String profilePriority = COMMON != null && COMMON.datapackPriority != null ? COMMON.datapackPriority.get() : "datapack_over_config";
        boolean profilesDpOverConfig = "datapack_over_config".equalsIgnoreCase(profilePriority);

        if (enableDataDrivenProfiles && DP_MOB_PROFILES_CACHE != null && !DP_MOB_PROFILES_CACHE.isEmpty()) {
            List<com.example.soundattract.config.MobProfile2> merged = new ArrayList<>();
            if (profilesDpOverConfig) {
                merged.addAll(DP_MOB_PROFILES_CACHE);
            } else {
                merged.addAll(tmpMobProfiles);
                merged.addAll(DP_MOB_PROFILES_CACHE);
            }
            SPECIAL_MOB_PROFILES_CACHE = merged;
        } else {
            SPECIAL_MOB_PROFILES_CACHE = tmpMobProfiles;
        }

        List<com.example.soundattract.config.PlayerProfile2> tmpPlayerProfiles = new ArrayList<>();
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
                net.minecraft.advancements.critereon.EntityPredicate.Builder predicateBuilder = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity();
                
                if (!nbtMatcherString.isEmpty()) {
                     try {
                        net.minecraft.nbt.CompoundTag nbtMatcher = net.minecraft.nbt.TagParser.parseTag(nbtMatcherString);
                        predicateBuilder.nbt(new net.minecraft.advancements.critereon.NbtPredicate(nbtMatcher));
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        SoundAttractMod.LOGGER.warn("Invalid NBT matcher string '{}' for player profile '{}': {}. NBT matching will be skipped for this profile.", nbtMatcherString, profileName, e.getMessage());
                    }
                }

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

                com.example.soundattract.config.PlayerProfile2 profile = new com.example.soundattract.config.PlayerProfile2(
                    profileName,
                    java.util.Optional.of(predicateBuilder.build()),
                    detectionOverrides,
                    java.util.Optional.empty()
                );
                tmpPlayerProfiles.add(profile);
                if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached LEGACY player profile: {}", profileName);
                }
            }
        }
        if (enableDataDrivenProfiles && DP_PLAYER_PROFILES_CACHE != null && !DP_PLAYER_PROFILES_CACHE.isEmpty()) {
            List<com.example.soundattract.config.PlayerProfile2> mergedPlayers = new ArrayList<>();
            if (profilesDpOverConfig) {
                mergedPlayers.addAll(DP_PLAYER_PROFILES_CACHE);
            } else {
                mergedPlayers.addAll(tmpPlayerProfiles);
                mergedPlayers.addAll(DP_PLAYER_PROFILES_CACHE);
            }
            SPECIAL_PLAYER_PROFILES_CACHE = mergedPlayers;
        } else {
            SPECIAL_PLAYER_PROFILES_CACHE = tmpPlayerProfiles;
        }

        parseAndCachePlayerActionConfig();
        com.example.soundattract.camo.CamoMaterialRegistry.parseConfig();

        com.example.soundattract.event.StealthDetectionEvents.reinitializeCaches();
        com.example.soundattract.event.ScentEvents.reinitializeCaches();
        com.example.soundattract.pathfinding.PathTaskScheduler.reinitializeCaches();

        if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "SoundAttractConfig: Baked config. {} special mob profiles loaded. {} special player profiles loaded. {} sound defaults. {} whitelist.",
                    SPECIAL_MOB_PROFILES_CACHE.size(),
                    SPECIAL_PLAYER_PROFILES_CACHE.size(),
                    SOUND_DEFAULT_ENTRIES_CACHE.size(),
                    SOUND_ID_WHITELIST_CACHE.size()
            );
        }
    }

    public static com.example.soundattract.config.MobProfile2 getMatchingProfile(Mob mob) {
        if (SPECIAL_MOB_PROFILES_CACHE == null || SPECIAL_MOB_PROFILES_CACHE.isEmpty()) {
            return null;
        }

        if (mob == null) {
            return null;
        }

        if (QuantifiedCacheCompat.isUsable()) {
            long staggerTicks = Math.abs(mob.getUUID().getLeastSignificantBits() % 20); 
            String key = new StringBuilder(96)
                .append(mob.getUUID().toString()).append('|')
                .append(SPECIAL_MOB_PROFILES_CACHE.size())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_mob_profile_match",
                key,
                () -> getMatchingProfileUncached(mob),
                1200L + staggerTicks,
                8192L
            );
        }

        return getMatchingProfileUncached(mob);
    }

    private static com.example.soundattract.config.MobProfile2 getMatchingProfileUncached(Mob mob) {
        for (com.example.soundattract.config.MobProfile2 profile : SPECIAL_MOB_PROFILES_CACHE) {
            if (profile.matches(mob)) {
                return profile;
            }
        }
        return null;
    }

    public static com.example.soundattract.config.PlayerProfile2 getMatchingPlayerProfile(Player player) {
        if (SPECIAL_PLAYER_PROFILES_CACHE == null || SPECIAL_PLAYER_PROFILES_CACHE.isEmpty()) {
            return null;
        }

        if (player == null) {
            return null;
        }

        if (QuantifiedCacheCompat.isUsable()) {
            String key = new StringBuilder(96)
                .append(player.getUUID().toString()).append('|')
                .append(SPECIAL_PLAYER_PROFILES_CACHE.size())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_player_profile_match",
                key,
                () -> getMatchingPlayerProfileUncached(player),
                1L,
                8192L
            );
        }

        return getMatchingPlayerProfileUncached(player);
    }

    private static com.example.soundattract.config.PlayerProfile2 getMatchingPlayerProfileUncached(Player player) {
        for (com.example.soundattract.config.PlayerProfile2 profile : SPECIAL_PLAYER_PROFILES_CACHE) {
            if (profile.matches(player)) {
                return profile;
            }
        }
        return null;
    }
}
