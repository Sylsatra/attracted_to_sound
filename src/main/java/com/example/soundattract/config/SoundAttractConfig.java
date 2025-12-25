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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

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

    public static List<com.example.soundattract.config.MobProfile> SPECIAL_MOB_PROFILES_CACHE = Collections.emptyList();
    public static List<com.example.soundattract.config.PlayerProfile> SPECIAL_PLAYER_PROFILES_CACHE = Collections.emptyList();
    public static List<com.example.soundattract.config.MobProfile> DP_MOB_PROFILES_CACHE = Collections.emptyList();
    public static List<com.example.soundattract.config.PlayerProfile> DP_PLAYER_PROFILES_CACHE = Collections.emptyList();
    public static final Map<ResourceLocation, Integer> customArmorColors = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Integer> DP_CUSTOM_ARMOR_COLORS = new ConcurrentHashMap<>();
    public static final Set<String> ATTRACTED_ENTITY_TYPES_CACHE = ConcurrentHashMap.newKeySet();

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

    public static class Common {

        public final ForgeConfigSpec.BooleanValue debugLogging;
        public final ForgeConfigSpec.BooleanValue enableDataDriven;
        public final ForgeConfigSpec.ConfigValue<String> datapackPriority;
        public final ForgeConfigSpec.BooleanValue edgeMobSmartBehavior;
        public final ForgeConfigSpec.BooleanValue enableFleeFromUnseenAttackerGoal;
        public final ForgeConfigSpec.IntValue soundLifetimeTicks;
        public final ForgeConfigSpec.DoubleValue arrivalDistance;
        public final ForgeConfigSpec.DoubleValue followLeaderSpreadOutDistance;
        public final ForgeConfigSpec.DoubleValue mobMoveSpeed;
        public final ForgeConfigSpec.IntValue maxSoundsTracked;
        public final ForgeConfigSpec.DoubleValue soundSwitchRatio;
        public final ForgeConfigSpec.DoubleValue soundNoveltyBonusWeight;
        public final ForgeConfigSpec.IntValue soundNoveltyTimeTicks;

        public final ForgeConfigSpec.BooleanValue enableRaycastCache;
        public final ForgeConfigSpec.IntValue raycastCacheTtlTicks;
        public final ForgeConfigSpec.IntValue raycastCacheMaxEntries;
        public final ForgeConfigSpec.IntValue scanCooldownTicks;
        public final ForgeConfigSpec.DoubleValue cooldownTicksPerMob;
        public final ForgeConfigSpec.DoubleValue minTpsForScanCooldown;
        public final ForgeConfigSpec.DoubleValue maxTpsForScanCooldown;
        public final ForgeConfigSpec.IntValue soundScoringSubmitCooldownTicks;
        public final ForgeConfigSpec.IntValue asyncResultTtlTicks;

        public final ForgeConfigSpec.IntValue maxGroupSize;
        public final ForgeConfigSpec.DoubleValue leaderGroupRadius;
        public final ForgeConfigSpec.DoubleValue groupDistance;
        public final ForgeConfigSpec.DoubleValue leaderSpacingMultiplier;
        public final ForgeConfigSpec.IntValue numEdgeSectors;
        public final ForgeConfigSpec.IntValue groupUpdateInterval;
        public final ForgeConfigSpec.IntValue maxLeaders;
        public final ForgeConfigSpec.IntValue edgeMobsPerSector;
        public final ForgeConfigSpec.DoubleValue groupSprintMultiplier;
        public final ForgeConfigSpec.DoubleValue leaderReturnArrivalDistance;
        public final ForgeConfigSpec.IntValue raidCountdownTicks;
        public final ForgeConfigSpec.IntValue initialGroupComputationDelay;

        public final ForgeConfigSpec.IntValue workerThreads;
        public final ForgeConfigSpec.IntValue workerTaskBudgetMs;

        public final ForgeConfigSpec.BooleanValue enableQuantifiedIntegration;
        public final ForgeConfigSpec.BooleanValue enableSmartBrainLibIntegration;

        public final ForgeConfigSpec.BooleanValue enableCustomNpcsIntegration;

        public final ForgeConfigSpec.BooleanValue enableQuantifiedCacheIntegration;
        public final ForgeConfigSpec.IntValue quantifiedCacheMemoryLimitMB;
        public final ForgeConfigSpec.BooleanValue disableQuantifiedCacheOnMemoryPressure;
        public final ForgeConfigSpec.BooleanValue triggerQuantifiedCacheCleanupOnMemoryPressure;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> attractedEntities;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> specialMobProfilesRaw;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> specialPlayerProfilesRaw;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mobBlacklist;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> soundIdWhitelist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> rawSoundDefaults;
        public final ForgeConfigSpec.DoubleValue minSoundLevelForPlayer;
        public final ForgeConfigSpec.DoubleValue minSoundLevelForMob;

        public final ForgeConfigSpec.BooleanValue enableBlockMuffling;
        public final ForgeConfigSpec.IntValue maxMufflingBlocksToCheck;
        public final ForgeConfigSpec.DoubleValue mufflingFactorWool;
        public final ForgeConfigSpec.DoubleValue mufflingFactorSolid;
        public final ForgeConfigSpec.DoubleValue mufflingFactorNonSolid;
        public final ForgeConfigSpec.DoubleValue mufflingFactorThin;
        public final ForgeConfigSpec.DoubleValue mufflingFactorLiquid;
        public final ForgeConfigSpec.DoubleValue mufflingFactorAir;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customThinBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customLiquidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customAirBlocks;

        public final ForgeConfigSpec.BooleanValue enableStealthMechanics;
        public final ForgeConfigSpec.IntValue stealthCheckInterval;
        public final ForgeConfigSpec.IntValue stealthGracePeriodTicks;
        public final ForgeConfigSpec.DoubleValue minStealthDetectionRange;
        public final ForgeConfigSpec.DoubleValue maxStealthDetectionRange;

        public final ForgeConfigSpec.DoubleValue standingDetectionRangePlayer;
        public final ForgeConfigSpec.DoubleValue sneakingDetectionRangePlayer;
        public final ForgeConfigSpec.DoubleValue crawlingDetectionRangePlayer;

        public final ForgeConfigSpec.IntValue neutralLightLevel;
        public final ForgeConfigSpec.DoubleValue lightLevelSensitivity;
        public final ForgeConfigSpec.DoubleValue minLightFactor;
        public final ForgeConfigSpec.DoubleValue maxLightFactor;
        public final ForgeConfigSpec.IntValue lightSampleRadiusHorizontal;
        public final ForgeConfigSpec.IntValue lightSampleRadiusVertical;
        public final ForgeConfigSpec.DoubleValue rainStealthFactor;
        public final ForgeConfigSpec.DoubleValue thunderStealthFactor;

        public final ForgeConfigSpec.DoubleValue movementStealthPenalty;
        public final ForgeConfigSpec.DoubleValue stationaryStealthBonusFactor;
        public final ForgeConfigSpec.DoubleValue movementThreshold;
        public final ForgeConfigSpec.DoubleValue invisibilityStealthFactor;

        public final ForgeConfigSpec.BooleanValue enableCamouflage;
        public final ForgeConfigSpec.BooleanValue enableHeldItemPenalty;
        public final ForgeConfigSpec.DoubleValue heldItemPenaltyFactor;
        public final ForgeConfigSpec.BooleanValue enableEnchantmentPenalty;
        public final ForgeConfigSpec.DoubleValue armorEnchantmentPenaltyFactor;
        public final ForgeConfigSpec.DoubleValue heldItemEnchantmentPenaltyFactor;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> camouflageArmorItems;
        public final ForgeConfigSpec.BooleanValue requireFullSetForCamouflageBonus;
        public final ForgeConfigSpec.DoubleValue fullArmorStealthBonus;
        public final ForgeConfigSpec.DoubleValue helmetCamouflageEffectiveness;
        public final ForgeConfigSpec.DoubleValue chestplateCamouflageEffectiveness;
        public final ForgeConfigSpec.DoubleValue leggingsCamouflageEffectiveness;
        public final ForgeConfigSpec.DoubleValue bootsCamouflageEffectiveness;
        public final ForgeConfigSpec.DoubleValue maxCamouflageEffectivenessCap;
        public final ForgeConfigSpec.BooleanValue allowPartialBonusIfFullSetRequired;

        public final ForgeConfigSpec.BooleanValue enableEnvironmentalCamouflage;
        public final ForgeConfigSpec.DoubleValue environmentalCamouflageMaxEffectiveness;
        public final ForgeConfigSpec.IntValue environmentalCamouflageColorMatchThreshold;
        public final ForgeConfigSpec.BooleanValue environmentalCamouflageOnlyDyedLeather;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customArmorColors;
        public final ForgeConfigSpec.IntValue envColorSampleRadius;
        public final ForgeConfigSpec.IntValue envColorSampleYOffsetStart;
        public final ForgeConfigSpec.IntValue envColorSampleYOffsetEnd;
        public final ForgeConfigSpec.BooleanValue enableEnvironmentalMismatchPenalty;
        public final ForgeConfigSpec.DoubleValue environmentalMismatchPenaltyFactor;
        public final ForgeConfigSpec.IntValue environmentalMismatchThreshold;

        public final ForgeConfigSpec.BooleanValue enableTaczIntegration;
        public final ForgeConfigSpec.DoubleValue taczReloadRange;
        public final ForgeConfigSpec.DoubleValue taczReloadWeight;
        public final ForgeConfigSpec.DoubleValue taczShootRange;
        public final ForgeConfigSpec.DoubleValue taczShootWeight;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions;
        public final ForgeConfigSpec.DoubleValue taczAttachmentReductionDefault;
        public final ForgeConfigSpec.DoubleValue gunshotBaseDetectionRange;
        public final ForgeConfigSpec.IntValue gunshotDetectionDurationTicks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczMuzzleFlashReductions;
        public final ForgeConfigSpec.DoubleValue taczAttachmentFlashReductionDefault;
        public final ForgeConfigSpec.BooleanValue enablePointBlankIntegration;
        public final ForgeConfigSpec.DoubleValue pointBlankReloadRange;
        public final ForgeConfigSpec.DoubleValue pointBlankReloadWeight;
        public final ForgeConfigSpec.DoubleValue pointBlankShootRange;
        public final ForgeConfigSpec.DoubleValue pointBlankShootWeight;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankGunShootRanges;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankAttachmentSoundReductions;
        public final ForgeConfigSpec.DoubleValue pointBlankAttachmentReductionDefault;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankMuzzleFlashReductions;

        public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration;
        public final ForgeConfigSpec.IntValue voiceChatWhisperRange;
        public final ForgeConfigSpec.IntValue voiceChatNormalRange;
        public final ForgeConfigSpec.DoubleValue voiceChatWeight;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap;

        public final ForgeConfigSpec.DoubleValue defaultHorizontalFov;
        public final ForgeConfigSpec.DoubleValue defaultVerticalFov;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fovOverrides;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fovExclusionList;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> nonBlockingVisionAllowList;

        public final ForgeConfigSpec.BooleanValue enableBlockBreaking;
        public final ForgeConfigSpec.DoubleValue blockBreakingTimeMultiplier;
        public final ForgeConfigSpec.BooleanValue blockBreakingToolOnly;
        public final ForgeConfigSpec.BooleanValue blockBreakingProperToolOnly;

        // Enhanced AI-inspired special actions
        public final ForgeConfigSpec.BooleanValue enableTeleportToSound;
        public final ForgeConfigSpec.DoubleValue teleportChance;
        public final ForgeConfigSpec.IntValue teleportCooldownTicks;
        public final ForgeConfigSpec.ConfigValue<String> teleportCanTeleportTag;
        public final ForgeConfigSpec.ConfigValue<String> teleportCanBeTeleportedTag;

        public final ForgeConfigSpec.BooleanValue enablePickUpAndThrowToSound;
        public final ForgeConfigSpec.DoubleValue pickUpChance;
        public final ForgeConfigSpec.IntValue pickUpCooldownTicks;
        public final ForgeConfigSpec.IntValue pickUpMinDistanceToPickUp;
        public final ForgeConfigSpec.IntValue pickUpMaxDistanceToThrow;
        public final ForgeConfigSpec.DoubleValue pickUpSpeedModifier;
        public final ForgeConfigSpec.ConfigValue<String> pickUpCanPickUpTag;
        public final ForgeConfigSpec.ConfigValue<String> pickUpCanBePickedUpTag;

        // XRAY targeting (Enhanced AI Targeting compat)
        public final ForgeConfigSpec.BooleanValue enableXrayTargeting;
        public final ForgeConfigSpec.ConfigValue<String> xrayApplyTag;
        public final ForgeConfigSpec.BooleanValue xrayRequireBetterNearby;
        public final ForgeConfigSpec.ConfigValue<String> xrayBetterNearbyTag;
        public final ForgeConfigSpec.IntValue xrayMinRange;
        public final ForgeConfigSpec.IntValue xrayMaxRange;
        public final ForgeConfigSpec.DoubleValue xrayChance;

        public final ForgeConfigSpec.IntValue configSchemaVersion;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Internal schema version for config migrations. Do not change.").push("internal");
            configSchemaVersion = builder.defineInRange("configSchemaVersion", 9, 0, Integer.MAX_VALUE);
            builder.pop();

            builder.comment("Sound Attract Mod Configuration").push("general");
            debugLogging = builder.comment("Enable debug logging for troubleshooting.")
                    .define("debugLogging", false);
            enableDataDriven = builder.comment(
                            "Enable datapack-based configuration (tags/JSON).",
                            "If false, only the TOML configuration is used for sounds and other features.")
                    .define("enableDataDriven", true);
            datapackPriority = builder.comment(
                            "Controls precedence when both datapacks and config define the same entries.",
                            "'datapack_over_config' = datapacks override config values.",
                            "'config_over_datapack' = config overrides datapack values.")
                    .define("datapackPriority", "datapack_over_config");
            enableFleeFromUnseenAttackerGoal = builder
                    .comment("Enable the FleeFromUnseenAttackerGoal that makes mobs flee after being hurt by an attacker they cannot see.")
                    .define("enableFleeFromUnseenAttackerGoal", true);
            maxSoundsTracked = builder.comment("Maximum number of sounds any single mob can track simultaneously.")
                    .defineInRange("maxSoundsTracked", 20, 1, 1000000);
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

            builder.pop();

            builder.comment("Mob grouping and squad behavior").push("groups");
            edgeMobSmartBehavior = builder.comment("Enables smarter behavior for mobs at the edge of their hearing range (e.g. pathing closer to investigate further)")
                    .define("edgeMobSmartBehavior", false);
            soundLifetimeTicks = builder.comment("How long a sound event remains 'interesting' to a mob, in ticks (20 ticks = 1 second).")
                    .defineInRange("soundLifetimeTicks", 1200, 20, 1000000);
            arrivalDistance = builder.comment("How close a mob needs to get to a sound source to consider it 'reached'.")
                    .defineInRange("arrivalDistance", 6.0, 1.0, 100.0);
            followLeaderSpreadOutDistance = builder.comment(
                            "After a follower reaches the investigation area near a sound, it will path away from the sound position to spread out and search.",
                            "This controls how far (in blocks) it tries to spread out.")
                    .defineInRange("followLeaderSpreadOutDistance", 24.0, 0.0, 256.0);
            mobMoveSpeed = builder.comment("Base speed multiplier for mobs moving towards a sound.")
                    .defineInRange("mobMoveSpeed", 1.15, 0.1, 3.0);
            maxGroupSize = builder.comment("Maximum number of mobs allowed in a group for group AI behavior. Default: 64")
                    .defineInRange("maxGroupSize", 64, 1, 128);
            leaderGroupRadius = builder.comment("Radius (in blocks) used to group mobs under a leader for group AI behavior. Default: 64.0")
                    .defineInRange("leaderGroupRadius", 64.0, 1.0, 128.0);
            groupDistance = builder.comment("Maximum distance (in blocks) for mobs to consider themselves part of a group for group behaviors. Used in AI such as FollowLeaderGoal.")
                    .defineInRange("groupDistance", 128.0, 1.0, 256.0);
            leaderSpacingMultiplier = builder.comment("Multiplier for spacing between mob leaders in a group. Default: 1.0")
                    .defineInRange("leaderSpacingMultiplier", 1.0, 0.1, 10.0);
            numEdgeSectors = builder.comment("Number of edge sectors for group detection (AI). Default: 4")
                    .defineInRange("numEdgeSectors", 4, 1, 64);
            groupUpdateInterval = builder.comment("Interval (in ticks) between group AI updates. Default: 200")
                    .defineInRange("groupUpdateInterval", 200, 1, 20000);
            maxLeaders = builder.comment("Maximum number of group leaders tracked for AI grouping. Default: 16")
                    .defineInRange("maxLeaders", 16, 1, 64);
            edgeMobsPerSector = builder.comment("Maximum number of edge mobs to select per angular sector. Default: 4")
                    .defineInRange("edgeMobsPerSector", 1, 1, 64);
            groupSprintMultiplier = builder.comment("Sprint speed multiplier used when followers rally/advance during RAID and when edge mobs return to the leader.")
                    .defineInRange("sprintMultiplier", 1.1, 1.0, 5.0);
            leaderReturnArrivalDistance = builder.comment("Distance (in blocks) within which an edge mob considers itself 'returned' to its leader.")
                    .defineInRange("leaderReturnArrivalDistance", 2.0, 0.5, 16.0);
            raidCountdownTicks = builder.comment("Countdown duration (in ticks) before a RAID advances to the target. 20 ticks = 1 second.")
                    .defineInRange("raidCountdownTicks", 100, 20, 72000);
            builder.pop();

            builder.comment("Performance-tuning options. Adjust these to balance responsiveness and server load.").push("performance");

            initialGroupComputationDelay = builder.comment(
                    "Delay in ticks before the first mob group computation is run after server startup.",
                    "This helps prevent lag on world load by giving the server time to stabilize.",
                    "20 ticks = 1 second. Default: 50 (2.5 seconds)"
            ).defineInRange("initialGroupComputationDelay", 50, 0, 72000);

            workerThreads = builder.comment(
                    "Number of background worker threads used for off-thread computations (e.g., group building).",
                    "Increase for large servers; decrease if you observe contention."
            )
                    .defineInRange("workerThreads", 2, 1, 64);
            workerTaskBudgetMs = builder.comment(
                    "Soft per-task time budget in milliseconds for worker computations before yielding.",
                    "Higher values allow more work per batch but can increase latency to apply results."
            )
                    .defineInRange("workerTaskBudgetMs", 10, 1, 1000);

            enableQuantifiedIntegration = builder.define("enableQuantifiedIntegration", true);
            enableSmartBrainLibIntegration = builder.define("enableSmartBrainLibIntegration", true);

            enableCustomNpcsIntegration = builder.define("enableCustomNpcsIntegration", false);

            enableQuantifiedCacheIntegration = builder.define("enableQuantifiedCacheIntegration", true);
            quantifiedCacheMemoryLimitMB = builder.defineInRange("quantifiedCacheMemoryLimitMB", 256, 0, 65536);
            disableQuantifiedCacheOnMemoryPressure = builder.define("disableQuantifiedCacheOnMemoryPressure", true);
            triggerQuantifiedCacheCleanupOnMemoryPressure = builder.define("triggerQuantifiedCacheCleanupOnMemoryPressure", true);

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

            soundScoringSubmitCooldownTicks = builder.comment(
                    "Cooldown (in ticks) per mob between async sound scoring submissions when candidates/target are unchanged.")
                    .defineInRange("soundScoringSubmitCooldownTicks", 1, 0, 10000);
            asyncResultTtlTicks = builder.comment(
                    "Time-to-live (in ticks) for cached async sound scoring results before considered stale.")
                    .defineInRange("asyncResultTtlTicks", 10, 1, 10000);

            enableRaycastCache = builder.comment("Enable caching for raycast results to improve performance. Disable if experiencing issues with sound obstruction detection.")
                    .define("enableRaycastCache", true);
            raycastCacheTtlTicks = builder.comment(
                    "Time-to-live (in ticks) for raycast cache entries before they are considered expired.")
                    .defineInRange("raycastCacheTtlTicks", 200, 1, 1000000);
            raycastCacheMaxEntries = builder.comment(
                    "Maximum number of entries stored in the raycast cache. Oldest entries are evicted when exceeded.")
                    .defineInRange("raycastCacheMaxEntries", 5000, 100, 1000000);

            builder.pop();

            builder.push("Enchanced AI Integration");

            enableBlockBreaking = builder
                    .comment("Enable mobs running AttractionGoal to break blocks to reach their destination when stuck. Requires EnhancedAI to be installed for block blacklists/rules.")
                    .define("enableBlockBreaking", false);

            blockBreakingTimeMultiplier = builder
                    .comment("Multiplier for the time a mob takes to break a block. E.g., 2.0 means it takes twice as long. Uses EnhancedAI's config value if installed and this is set to -1.")
                    .defineInRange("blockBreakingTimeMultiplier", 1.5, -1.0, 100.0);

            blockBreakingToolOnly = builder
                    .comment("If true, mobs will only attempt to break blocks if they are holding a tool (e.g. pickaxe) in their offhand.")
                    .define("blockBreakingToolOnly", false);

            blockBreakingProperToolOnly = builder
                    .comment("If true, mobs will only break blocks if their tool is the 'proper' tool for that block (e.g., pickaxe for stone).")
                    .define("blockBreakingProperToolOnly", false);

            // Teleport a nearby target to the current sound location (inspired by EnhancedAI TeleportToTarget)
            enableTeleportToSound = builder
                    .comment("Enable special AI: mobs with the specified tag can teleport another mob to the sound location.")
                    .define("enableTeleportToSound", true);
            teleportChance = builder
                    .comment("Chance [0..1] for a teleporter mob to attempt the teleport behavior when evaluating goals. If EnhancedAI is installed, its difficulty-based chance is used instead.")
                    .defineInRange("teleportChance", 0.35, 0.0, 1.0);
            teleportCooldownTicks = builder
                    .comment("Cooldown (in ticks) after a teleport action. If EnhancedAI is installed, its value is used instead.")
                    .defineInRange("teleportCooldownTicks", 300, 0, 72000);
            teleportCanTeleportTag = builder
                    .comment("EntityType tag used to decide which mobs can perform teleport-to-sound. Defaults to EnhancedAI's tag.")
                    .define("teleportCanTeleportTag", "enhancedai:mobs/teleport_to_target/can_teleport");
            teleportCanBeTeleportedTag = builder
                    .comment("EntityType tag used to decide which mobs can be teleported. Defaults to EnhancedAI's tag.")
                    .define("teleportCanBeTeleportedTag", "enhancedai:mobs/teleport_to_target/can_be_teleported");

            // Pick up a mob and throw it towards the current sound location (inspired by EnhancedAI PickUpAndThrow)
            enablePickUpAndThrowToSound = builder
                    .comment("Enable special AI: mobs with the specified tag can pick up another mob and throw it toward the sound location.")
                    .define("enablePickUpAndThrowToSound", true);
            pickUpChance = builder
                    .comment("Chance [0..1] for a thrower mob to attempt the pick-up-and-throw behavior. If EnhancedAI is installed, its difficulty-based chance is used instead.")
                    .defineInRange("pickUpChance", 0.05, 0.0, 1.0);
            pickUpCooldownTicks = builder
                    .comment("Cooldown (in ticks) after a throw action. If EnhancedAI is installed, its value is used instead.")
                    .defineInRange("pickUpCooldownTicks", 600, 0, 72000);
            pickUpMinDistanceToPickUp = builder
                    .comment("Minimum distance from the sound for the mob to consider picking up a target.")
                    .defineInRange("pickUpMinDistanceToPickUp", 5, 0, 1024);
            pickUpMaxDistanceToThrow = builder
                    .comment("Max distance to the sound within which the mob will release/throw the picked-up mob.")
                    .defineInRange("pickUpMaxDistanceToThrow", 24, 0, 1024);
            pickUpSpeedModifier = builder
                    .comment("Speed modifier applied to the mob while approaching the pick-up target.")
                    .defineInRange("pickUpSpeedModifier", 1.25, 0.0, 10.0);
            pickUpCanPickUpTag = builder
                    .comment("EntityType tag used to decide which mobs can perform pick-up-and-throw. Defaults to EnhancedAI's tag.")
                    .define("pickUpCanPickUpTag", "enhancedai:mobs/pick_up_and_throw/can_pick_up");
            pickUpCanBePickedUpTag = builder
                    .comment("EntityType tag used to decide which mobs can be picked up. Defaults to EnhancedAI's tag.")
                    .define("pickUpCanBePickedUpTag", "enhancedai:mobs/pick_up_and_throw/can_be_picked_up");

            // XRAY follow range compat (see Enhanced AI Targeting)
            enableXrayTargeting = builder
                    .comment("Enable XRAY targeting compat: mobs in the apply_xray tag can detect players through walls up to a configured range.")
                    .define("enableXrayTargeting", true);
            xrayApplyTag = builder
                    .comment("EntityType tag used to decide which mobs can have XRAY detection. Defaults to EnhancedAI's tag.")
                    .define("xrayApplyTag", "enhancedai:mobs/targeting/apply_xray");
            xrayRequireBetterNearby = builder
                    .comment("Require the mob to also be in the Better Nearby Targeting tag to apply XRAY (mirrors EnhancedAI behavior).")
                    .define("xrayRequireBetterNearby", true);
            xrayBetterNearbyTag = builder
                    .comment("EntityType tag for Better Nearby Targeting. Used only if xrayRequireBetterNearby is true.")
                    .define("xrayBetterNearbyTag", "enhancedai:mobs/targeting/better_nearby_targeting");
            xrayMinRange = builder
                    .comment("Minimum XRAY follow range (blocks) for fallback when EnhancedAI is not present. 0..128")
                    .defineInRange("xrayMinRange", 16, 0, 128);
            xrayMaxRange = builder
                    .comment("Maximum XRAY follow range (blocks). 0 disables XRAY fallback.")
                    .defineInRange("xrayMaxRange", 24, 0, 128);
            xrayChance = builder
                    .comment("Chance [0..1] for a mob in the XRAY tag to get the XRAY range (fallback when EnhancedAI is not present).")
                    .defineInRange("xrayChance", 0.5, 0.0, 1.0);

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
                            "scguns:cog_knight", "scguns:cog_minion", "scguns:blunderer", "scguns:hive", "scguns:dissident", "scguns:hornlin", "scguns:redcoat", "scguns:cog_knight",
                            "scguns:sky_carrier", "scguns:supply_scamp", "scguns:swarm", "scguns:zombified_hornlin",
                            "spore:braiomil", "spore:braurei", "spore:brot", "spore:brute", "spore:busser",
                            "spore:inf_construct", "spore:delusioner", "spore:gastgaber", "spore:gazenbreacher",
                            "spore:griefer", "spore:hevoker", "spore:hidenburg", "spore:howitzer", "spore:howler",
                            "spore:hvindicator", "spore:illusion", "spore:inf_drownded", "spore:inf_evoker",
                            "spore:inf_hazmat", "spore:husk", "spore:inf_pillager", "spore:inf_player",
                            "spore:inf_villager", "spore:inf_vindicator", "spore:inf_wanderer", "spore:inf_witch",
                            "spore:inf_human", "spore:inquisitor", "spore:jagd", "spore:knight",
                            "spore:lacerator", "spore:leaper", "spore:mound", "spore:nuclea",
                            "spore:ogre", "spore:plagued", "spore:proto", "spore:reconstructor",
                            "spore:scamper", "spore:scavenger", "spore:scent", "spore:sieger", "spore:specter",
                            "spore:spitter", "spore:stalker", "spore:thorn", "spore:umarmed", "spore:usurper",
                            "spore:verva", "spore:vigil", "spore:volatile", "spore:wendigo",
                            "sculkhorde:sculk_zombie", "sculkhorde:sculk_witch", "sculkhorde:sculk_vindicator", "sculkhorde:sculk_vex",
                            "sculkhorde:sculk_squid", "sculkhorde:sculk_spore_spewer",
                            "sculkhorde:sculk_spitter", "sculkhorde:sculk_soul_reaper",
                            "sculkhorde:sculk_sheep", "sculkhorde:sculk_salmon",
                            "sculkhorde:sculk_ravager", "sculkhorde:sculk_pufferfish",
                            "sculkhorde:sculk_phantom", "sculkhorde:sculk_mite_aggressor",
                            "sculkhorde:sculk_mite", "sculkhorde:sculk_metamorphosis_pod",
                            "sculkhorde:sculk_hatcher", "sculkhorde:sculk_guardian",
                            "sculkhorde:sculk_ghast", "sculkhorde:sculk_enderman", "sculkhorde:sculk_creeper", "sculkhorde:sculk_broodling", "sculkhorde:sculk_brood_hatcher", "sculkhorde:sculk_bee_infector",
                            "sculkhorde:sculk_bee_harvester", "sculkhorde:golem_of_wrath"
                    ), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

            mobBlacklist = builder.comment("A list of entity resource IDs to PREVENT from receiving the attraction AI goals.",
                    "This acts as a blacklist. Mobs on this list will never be attracted to sounds, regardless of other settings.",
                    "Format: ['minecraft:pig', 'modid:some_other_mob']")
                    .defineList("mobBlacklist", Collections.singletonList("minecraft:pig"), obj -> obj instanceof String);
            builder.pop();

            builder.push("Sounds White List");
            soundIdWhitelist = builder.comment("If not empty, only sound event IDs in this list will be considered by mobs.")
                    .defineList("soundIdWhitelist", Arrays.asList(
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
                            "soundattract:virtual",
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
                            "scguns:item.rocket_rifle.fire",
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
                            "scguns:item.boomstick.fire", "scguns:item.brass_shotgun.fire", "scguns:item.brass_revolver.fire", "scguns:item.shulker.fire", "scguns:item.iron_rifle.fire", "scguns:item.combat_shotgun.fire", "scguns:item.beam.fire", "scguns:item.airgun.fire", "scguns:item.heavier_rifle.fire", "scguns:item.iron_pistol.fire", "scguns:item.gauss.fire", "scguns:item.heavy_rifle.fire", "scguns:item.greaser_smg.fire", "scguns:item.plasma.fire", "scguns:item.rusty_gnat.fire", "scguns:item.cowboy.fire", "scguns:item.scrapper.fire", "scguns:item.sculk.fire", "scguns:item.raygun.fire", "scguns:item.rocket_rifle.fire",
                            "superbwarfare:taser_fire_1p",
                            "superbwarfare:taser_fire_3p",
                            "superbwarfare:taser_reload_empty",
                            "superbwarfare:trachelium_fire_1p",
                            "superbwarfare:trachelium_fire_3p",
                            "superbwarfare:trachelium_far",
                            "superbwarfare:trachelium_veryfar",
                            "superbwarfare:trachelium_fire_1p_s",
                            "superbwarfare:trachelium_fire_3p_s",
                            "superbwarfare:trachelium_far_s",
                            "superbwarfare:trachelium_reload_empty",
                            "superbwarfare:trachelium_bolt",
                            "superbwarfare:hunting_rifle_fire_1p",
                            "superbwarfare:hunting_rifle_fire_3p",
                            "superbwarfare:hunting_rifle_far",
                            "superbwarfare:hunting_rifle_veryfar",
                            "superbwarfare:hunting_rifle_reload_empty",
                            "superbwarfare:m_79_fire_1p",
                            "superbwarfare:m_79_fire_3p",
                            "superbwarfare:m_79_far",
                            "superbwarfare:m_79_veryfar",
                            "superbwarfare:m_79_reload_empty",
                            "superbwarfare:sks_fire_1p",
                            "superbwarfare:sks_fire_3p",
                            "superbwarfare:sks_reload_normal",
                            "superbwarfare:sks_reload_empty",
                            "superbwarfare:sks_far",
                            "superbwarfare:sks_veryfar",
                            "superbwarfare:homemade_shotgun_fire_1p",
                            "superbwarfare:homemade_shotgun_fire_3p",
                            "superbwarfare:homemade_shotgun_far",
                            "superbwarfare:homemade_shotgun_veryfar",
                            "superbwarfare:homemade_shotgun_reload_normal",
                            "superbwarfare:homemade_shotgun_reload_empty",
                            "superbwarfare:ak_47_fire_1p",
                            "superbwarfare:ak_47_fire_1p_s",
                            "superbwarfare:ak_47_fire_3p",
                            "superbwarfare:ak_47_fire_3p_s",
                            "superbwarfare:ak_47_far",
                            "superbwarfare:ak_47_veryfar",
                            "superbwarfare:ak_47_reload_normal",
                            "superbwarfare:ak_47_reload_empty",
                            "superbwarfare:devotion_fire_1p",
                            "superbwarfare:devotion_fire_3p",
                            "superbwarfare:devotion_far",
                            "superbwarfare:devotion_veryfar",
                            "superbwarfare:devotion_reload_normal",
                            "superbwarfare:devotion_reload_empty",
                            "superbwarfare:rpg_fire_1p",
                            "superbwarfare:rpg_fire_3p",
                            "superbwarfare:rpg_far",
                            "superbwarfare:rpg_veryfar",
                            "superbwarfare:rpg_reload_empty",
                            "superbwarfare:m_4_fire_1p",
                            "superbwarfare:m_4_fire_1p_s",
                            "superbwarfare:m_4_fire_3p",
                            "superbwarfare:m_4_fire_3p_s",
                            "superbwarfare:m_4_far",
                            "superbwarfare:m_4_veryfar",
                            "superbwarfare:m_4_reload_normal",
                            "superbwarfare:m_4_reload_empty",
                            "superbwarfare:aa_12_fire_1p",
                            "superbwarfare:aa_12_fire_3p",
                            "superbwarfare:aa_12_far",
                            "superbwarfare:aa_12_veryfar",
                            "superbwarfare:aa_12_reload_normal",
                            "superbwarfare:aa_12_reload_empty",
                            "superbwarfare:bocek_zoom_fire_1p",
                            "superbwarfare:bocek_zoom_fire_3p",
                            "superbwarfare:bocek_shatter_cap_fire_1p",
                            "superbwarfare:bocek_shatter_cap_fire_3p",
                            "superbwarfare:bocek_pull_1p",
                            "superbwarfare:bocek_pull_3p",
                            "superbwarfare:hk_416_fire_1p",
                            "superbwarfare:hk_416_fire_1p_s",
                            "superbwarfare:hk_416_fire_3p",
                            "superbwarfare:hk_416_fire_3p_s",
                            "superbwarfare:hk_416_far",
                            "superbwarfare:hk_416_veryfar",
                            "superbwarfare:hk_416_reload_normal",
                            "superbwarfare:hk_416_reload_empty",
                            "superbwarfare:rpk_fire_1p",
                            "superbwarfare:rpk_fire_3p",
                            "superbwarfare:rpk_fire_1p_s",
                            "superbwarfare:rpk_fire_3p_s",
                            "superbwarfare:rpk_far",
                            "superbwarfare:rpk_veryfar",
                            "superbwarfare:rpk_reload_normal",
                            "superbwarfare:rpk_reload_empty",
                            "superbwarfare:ntw_20_fire_1p",
                            "superbwarfare:ntw_20_fire_3p",
                            "superbwarfare:ntw_20_far",
                            "superbwarfare:ntw_20_veryfar",
                            "superbwarfare:ntw_20_reload_normal",
                            "superbwarfare:ntw_20_reload_empty",
                            "superbwarfare:ntw_20_bolt",
                            "superbwarfare:vector_fire_1p",
                            "superbwarfare:vector_fire_3p",
                            "superbwarfare:vector_far",
                            "superbwarfare:vector_veryfar",
                            "superbwarfare:vector_fire_1p_s",
                            "superbwarfare:vector_fire_3p_s",
                            "superbwarfare:vector_far_s",
                            "superbwarfare:vector_reload_normal",
                            "superbwarfare:vector_reload_empty",
                            "superbwarfare:minigun_fire_1p",
                            "superbwarfare:minigun_fire_3p",
                            "superbwarfare:minigun_far",
                            "superbwarfare:minigun_veryfar",
                            "superbwarfare:minigun_rot",
                            "superbwarfare:minigun_overheat",
                            "superbwarfare:mk_14_fire_1p",
                            "superbwarfare:mk_14_fire_3p",
                            "superbwarfare:mk_14_far",
                            "superbwarfare:mk_14_veryfar",
                            "superbwarfare:mk_14_fire_1p_s",
                            "superbwarfare:mk_14_fire_3p_s",
                            "superbwarfare:mk_14_far_s",
                            "superbwarfare:mk_14_reload_normal",
                            "superbwarfare:mk_14_reload_empty",
                            "superbwarfare:sentinel_fire_1p",
                            "superbwarfare:sentinel_fire_3p",
                            "superbwarfare:sentinel_charge_fire_1p",
                            "superbwarfare:sentinel_charge_fire_3p",
                            "superbwarfare:sentinel_far",
                            "superbwarfare:sentinel_veryfar",
                            "superbwarfare:sentinel_charge_far",
                            "superbwarfare:sentinel_charge_veryfar",
                            "superbwarfare:sentinel_reload_normal",
                            "superbwarfare:sentinel_reload_empty",
                            "superbwarfare:sentinel_charge",
                            "superbwarfare:sentinel_bolt",
                            "superbwarfare:m_60_fire_1p",
                            "superbwarfare:m_60_fire_3p",
                            "superbwarfare:m_60_far",
                            "superbwarfare:m_60_veryfar",
                            "superbwarfare:m_60_reload_normal",
                            "superbwarfare:m_60_reload_empty",
                            "superbwarfare:svd_fire_1p",
                            "superbwarfare:svd_fire_3p",
                            "superbwarfare:svd_far",
                            "superbwarfare:svd_veryfar",
                            "superbwarfare:svd_fire_1p_s",
                            "superbwarfare:svd_fire_3p_s",
                            "superbwarfare:svd_far_s",
                            "superbwarfare:svd_reload_normal",
                            "superbwarfare:svd_reload_empty",
                            "superbwarfare:m_98b_fire_1p",
                            "superbwarfare:m_98b_fire_3p",
                            "superbwarfare:m_98b_fire_1p_s",
                            "superbwarfare:m_98b_fire_3p_s",
                            "superbwarfare:m_98b_far",
                            "superbwarfare:m_98b_veryfar",
                            "superbwarfare:m_98b_reload_normal",
                            "superbwarfare:m_98b_reload_empty",
                            "superbwarfare:m_98b_bolt",
                            "superbwarfare:marlin_fire_1p",
                            "superbwarfare:marlin_fire_3p",
                            "superbwarfare:marlin_far",
                            "superbwarfare:marlin_veryfar",
                            "superbwarfare:marlin_loop",
                            "superbwarfare:marlin_prepare",
                            "superbwarfare:marlin_end",
                            "superbwarfare:marlin_bolt",
                            "superbwarfare:m_870_fire_1p",
                            "superbwarfare:m_870_fire_3p",
                            "superbwarfare:m_870_far",
                            "superbwarfare:m_870_veryfar",
                            "superbwarfare:m_870_prepare_load",
                            "superbwarfare:m_870_loop",
                            "superbwarfare:m_870_bolt",
                            "superbwarfare:glock_17_fire_1p",
                            "superbwarfare:glock_17_fire_3p",
                            "superbwarfare:glock_17_far",
                            "superbwarfare:glock_17_veryfar",
                            "superbwarfare:glock_17_reload_normal",
                            "superbwarfare:glock_17_reload_empty",
                            "superbwarfare:mp_443_fire_1p",
                            "superbwarfare:mp_443_fire_3p",
                            "superbwarfare:m_1911_fire_1p",
                            "superbwarfare:m_1911_fire_3p",
                            "superbwarfare:m_1911_far",
                            "superbwarfare:m_1911_veryfar",
                            "superbwarfare:qbz_95_fire_1p",
                            "superbwarfare:qbz_95_fire_1p_s",
                            "superbwarfare:qbz_95_fire_3p",
                            "superbwarfare:qbz_95_fire_3p_s",
                            "superbwarfare:qbz_95_far",
                            "superbwarfare:qbz_95_veryfar",
                            "superbwarfare:qbz_95_reload_normal",
                            "superbwarfare:qbz_95_reload_empty",
                            "superbwarfare:k_98_fire_1p",
                            "superbwarfare:k_98_fire_3p",
                            "superbwarfare:k_98_far",
                            "superbwarfare:k_98_veryfar",
                            "superbwarfare:k_98_reload_empty",
                            "superbwarfare:k_98_bolt",
                            "superbwarfare:k_98_loop",
                            "superbwarfare:k_98_prepare",
                            "superbwarfare:k_98_end",
                            "superbwarfare:mosin_nagant_fire_1p",
                            "superbwarfare:mosin_nagant_fire_3p",
                            "superbwarfare:mosin_nagant_far",
                            "superbwarfare:mosin_nagant_veryfar",
                            "superbwarfare:mosin_nagant_bolt",
                            "superbwarfare:mosin_nagant_loop",
                            "superbwarfare:mosin_nagant_prepare",
                            "superbwarfare:mosin_nagant_prepare_empty",
                            "superbwarfare:mosin_nagant_end",
                            "superbwarfare:javelin_fire_1p",
                            "superbwarfare:javelin_fire_3p",
                            "superbwarfare:javelin_far",
                            "superbwarfare:javelin_reload_empty",
                            "superbwarfare:javelin_lock",
                            "superbwarfare:javelin_lockon",
                            "superbwarfare:ak_12_fire_1p",
                            "superbwarfare:ak_12_fire_3p",
                            "superbwarfare:ak_12_fire_1p_s",
                            "superbwarfare:ak_12_fire_3p_s",
                            "superbwarfare:ak_12_far",
                            "superbwarfare:ak_12_veryfar",
                            "superbwarfare:ak_12_far_s",
                            "superbwarfare:ak_12_veryfar_s",
                            "superbwarfare:ak_12_reload_normal",
                            "superbwarfare:ak_12_reload_empty",
                            "superbwarfare:bullet_supply",
                            "superbwarfare:shock",
                            "superbwarfare:electric",
                            "superbwarfare:triggerclick",
                            "superbwarfare:hit",
                            "superbwarfare:targetdown",
                            "superbwarfare:indication",
                            "superbwarfare:indication_vehicle",
                            "superbwarfare:jump",
                            "superbwarfare:doublejump",
                            "superbwarfare:explosion_close",
                            "superbwarfare:explosion_far",
                            "superbwarfare:explosion_very_far",
                            "superbwarfare:explosion_water",
                            "superbwarfare:ouch",
                            "superbwarfare:step",
                            "superbwarfare:growl",
                            "superbwarfare:idle",
                            "superbwarfare:land",
                            "superbwarfare:headshot",
                            "superbwarfare:mortar_fire",
                            "superbwarfare:mortar_distant",
                            "superbwarfare:mortar_load",
                            "superbwarfare:firerate",
                            "superbwarfare:adjust_fov",
                            "superbwarfare:mk_42_fire_1p",
                            "superbwarfare:mk_42_far",
                            "superbwarfare:mk_42_veryfar",
                            "superbwarfare:mk_42_fire_3p",
                            "superbwarfare:cannon_reload",
                            "superbwarfare:cannon_zoom_in",
                            "superbwarfare:cannon_zoom_out",
                            "superbwarfare:drone_sound",
                            "superbwarfare:grenade_throw",
                            "superbwarfare:grenade_pull",
                            "superbwarfare:heng",
                            "superbwarfare:edit_mode",
                            "superbwarfare:edit",
                            "superbwarfare:shell_casing_normal",
                            "superbwarfare:shell_casing_shotgun",
                            "superbwarfare:shell_casing_50cal",
                            "superbwarfare:huge_explosion_close",
                            "superbwarfare:huge_explosion_far",
                            "superbwarfare:huge_explosion_very_far",
                            "superbwarfare:open",
                            "superbwarfare:charge_rifle_fire_1p",
                            "superbwarfare:charge_rifle_fire_3p",
                            "superbwarfare:charge_rifle_fire_boom_1p",
                            "superbwarfare:charge_rifle_fire_boom_3p",
                            "superbwarfare:annihilator_fire_1p",
                            "superbwarfare:annihilator_fire_3p",
                            "superbwarfare:annihilator_far",
                            "superbwarfare:annihilator_veryfar",
                            "superbwarfare:annihilator_reload",
                            "superbwarfare:boat_engine",
                            "superbwarfare:vehicle_strike",
                            "superbwarfare:m_2_hb_fire_1p",
                            "superbwarfare:m_2_hb_fire_3p",
                            "superbwarfare:m_2_hb_far",
                            "superbwarfare:m_2_hb_veryfar",
                            "superbwarfare:m_2_hb_reload_normal",
                            "superbwarfare:m_2_hb_reload_empty",
                            "superbwarfare:wheel_chair_engine",
                            "superbwarfare:wheel_chair_jump",
                            "superbwarfare:radar_search_start",
                            "superbwarfare:radar_search_idle",
                            "superbwarfare:radar_search_end",
                            "superbwarfare:helicopter_engine_start",
                            "superbwarfare:helicopter_engine",
                            "superbwarfare:heli_cannon_fire_1p",
                            "superbwarfare:heli_cannon_fire_3p",
                            "superbwarfare:heli_cannon_far",
                            "superbwarfare:heli_cannon_veryfar",
                            "superbwarfare:small_rocket_fire_1p",
                            "superbwarfare:small_rocket_fire_3p",
                            "superbwarfare:into_cannon",
                            "superbwarfare:into_missile",
                            "superbwarfare:missile_reload",
                            "superbwarfare:low_health",
                            "superbwarfare:no_health",
                            "superbwarfare:locking_warning",
                            "superbwarfare:locked_warning",
                            "superbwarfare:missile_warning",
                            "superbwarfare:decoy_reload",
                            "superbwarfare:decoy_fire",
                            "superbwarfare:secondary_cataclysm_fire_1p",
                            "superbwarfare:secondary_cataclysm_fire_3p",
                            "superbwarfare:secondary_cataclysm_far",
                            "superbwarfare:secondary_cataclysm_veryfar",
                            "superbwarfare:secondary_cataclysm_loop",
                            "superbwarfare:secondary_cataclysm_prepare_load",
                            "superbwarfare:secondary_cataclysm_end",
                            "superbwarfare:secondary_cataclysm_fire_1p_charge",
                            "superbwarfare:secondary_cataclysm_fire_3p_charge",
                            "superbwarfare:secondary_cataclysm_far_charge",
                            "superbwarfare:secondary_cataclysm_veryfar_charge",
                            "superbwarfare:lunge_mine_growl",
                            "superbwarfare:lav_fire_1p",
                            "superbwarfare:lav_fire_3p",
                            "superbwarfare:lav_far",
                            "superbwarfare:lav_veryfar",
                            "superbwarfare:lav_engine",
                            "superbwarfare:coax_fire_1p",
                            "superbwarfare:bmp_fire_1p",
                            "superbwarfare:bmp_fire_3p",
                            "superbwarfare:bmp_engine",
                            "superbwarfare:bmp_missile_fire_1p",
                            "superbwarfare:bmp_missile_fire_3p",
                            "superbwarfare:bmp_missile_reload",
                            "superbwarfare:wheel_step",
                            "superbwarfare:laser_tower_shoot",
                            "superbwarfare:yx_100_reload",
                            "superbwarfare:yx_100_fire_1p",
                            "superbwarfare:yx_100_fire_3p",
                            "superbwarfare:yx_100_far",
                            "superbwarfare:yx_100_veryfar",
                            "superbwarfare:yx_100_engine",
                            "superbwarfare:turret_turn",
                            "superbwarfare:c4_beep",
                            "superbwarfare:c4_final",
                            "superbwarfare:c4_throw",
                            "superbwarfare:c4_detonator_click",
                            "superbwarfare:prism_fire_1p",
                            "superbwarfare:prism_fire_3p",
                            "superbwarfare:prism_fire_1p_2",
                            "superbwarfare:prism_fire_3p_2",
                            "superbwarfare:prism_engine",
                            "superbwarfare:insidious_fire_1p",
                            "superbwarfare:insidious_fire_3p",
                            "superbwarfare:insidious_far",
                            "superbwarfare:insidious_veryfar",
                            "superbwarfare:insidious_reload_empty",
                            "superbwarfare:smoke_fire",
                            "superbwarfare:hpj_11_fire_3p",
                            "superbwarfare:track_move",
                            "superbwarfare:rocket_fly",
                            "superbwarfare:shell_fly",
                            "superbwarfare:rocket_engine",
                            "superbwarfare:vehicle_swim",
                            "superbwarfare:a10_engine",
                            "superbwarfare:a10_fire",
                            "superbwarfare:bomb_release",
                            "superbwarfare:missile_start",
                            "superbwarfare:bomb_reload",
                            "superbwarfare:jet_lock",
                            "superbwarfare:jet_lockon",
                            "superbwarfare:dps_generator_evolve",
                            "superbwarfare:melee_hit",
                            "superbwarfare:aurelia_sceptre_fire_1p",
                            "superbwarfare:aurelia_sceptre_fire_3p",
                            "superbwarfare:fly_loop",
                            "superbwarfare:mp_5_fire_1p",
                            "superbwarfare:mp_5_fire_3p",
                            "superbwarfare:mp_5_far",
                            "superbwarfare:mp_5_veryfar",
                            "superbwarfare:mp_5_fire_1p_s",
                            "superbwarfare:mp_5_fire_3p_s",
                            "superbwarfare:mp_5_reload_normal",
                            "superbwarfare:mp_5_reload_empty",
                            "superbwarfare:steel_pipe_hit",
                            "superbwarfare:steel_pipe_drop",
                            "superbwarfare:qbz_191_fire_1p",
                            "superbwarfare:qbz_191_fire_1p_s",
                            "superbwarfare:qbz_191_fire_3p",
                            "superbwarfare:qbz_191_fire_3p_s",
                            "superbwarfare:qbz_191_far",
                            "superbwarfare:qbz_191_veryfar",
                            "superbwarfare:qbz_191_reload_normal",
                            "superbwarfare:qbz_191_reload_empty",
                            "superbwarfare:smoke_grenade_release",
                            "superbwarfare:hand_wheel_rot",
                            "superbwarfare:medium_rocket_fire",
                            "superbwarfare:ty63_reload",
                            "pointblank:gun_action"
                    ),
                            obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
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
                                    "soundattract:virtual;10;1.0",
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
                                    "scguns:item.makeshift_rifle.cock;10;1.0", "scguns:item.pistol.cock;5;0.5", "scguns:item.flamethrower.reload;10;1.0", "scguns:item.gauss.reload;25;2.5", "scguns:item.pistol.reload;10;1.0", "scguns:item.airgun.fire;80;8.0", "scguns:item.beam.fire;100;10.0", "scguns:item.blackpowder.fire;115;11.5", "scguns:item.boomstick.fire;120;12.0", "scguns:item.brass_pistol.fire;105;10.5", "scguns:item.brass_revolver.fire;110;11.0", "scguns:item.brass_shotgun.fire;110;11.0", "scguns:item.bruiser.fire;115;11.5", "scguns:item.combat_shotgun.fire;120;12.0", "scguns:item.cowboy.fire;110;11.0", "scguns:item.flamethrower.fire_2;95;9.5", "scguns:item.gauss.fire;130;13.0", "scguns:item.greaser_smg.fire;110;11.0", "scguns:item.gyrojet.fire;115;11.5", "scguns:item.heavy_rifle.fire;120;12.0", "scguns:item.heavier_rifle.fire;125;12.5", "scguns:item.iron_pistol.fire;110;11.0", "scguns:item.iron_rifle.fire;120;12.0", "scguns:item.makeshift_rifle.fire;110;11.0", "scguns:item.plasma.fire;115;11.5", "scguns:item.raygun.fire;110;11.0", "scguns:item.rocket.fire;140;14.0", "scguns:item.rocket_rifle.fire;135;13.5", "scguns:item.rusty_gnat.fire;105;10.5", "scguns:item.scrapper.fire;110;11.0", "scguns:item.sculk.fire;100;10.0", "scguns:item.shock.fire;95;9.5", "scguns:item.shulker.fire;105;10.5", "scguns:item.scorched_sniper.fire;130;13.0", "scguns:item.scorched_rifle.fire;125;12.5", "scguns:item.shock.silenced_fire;45;4.5", "scguns:item.bruiser.silenced_fire;55;5.5", "scguns:item.makeshift_rifle.silenced_fire;50;5.0", "scguns:item.combat_shotgun.silenced_fire;60;6.0", "scguns:item.rusty_gnat.silenced_fire;45;4.5", "scguns:item.shock.fire;155;15.5", "scguns:item.scorched_sniper.fire;190;19.0", "scguns:item.scorched_rifle.fire;185;18.5", "scguns:item.bruiser.fire;175;17.5", "scguns:item.flamethrower.fire_2;155;15.5", "scguns:item.blackpowder.fire;175;17.5", "scguns:item.gyrojet.fire;175;17.5", "scguns:item.boomstick.fire;180;18.0", "scguns:item.brass_shotgun.fire;170;17.0", "scguns:item.brass_revolver.fire;170;17.0", "scguns:item.shulker.fire;165;16.5", "scguns:item.iron_rifle.fire;180;18.0", "scguns:item.combat_shotgun.fire;180;18.0", "scguns:item.beam.fire;160;16.0", "scguns:item.airgun.fire;140;14.0", "scguns:item.heavier_rifle.fire;185;18.5", "scguns:item.iron_pistol.fire;170;17.0", "scguns:item.gauss.fire;190;19.0", "scguns:item.heavy_rifle.fire;180;18.0", "scguns:item.greaser_smg.fire;170;17.0", "scguns:item.plasma.fire;175;17.5", "scguns:item.rusty_gnat.fire;165;16.5", "scguns:item.cowboy.fire;170;17.0", "scguns:item.scrapper.fire;170;17.0", "scguns:item.sculk.fire;160;16.0", "scguns:item.raygun.fire;170;17.0", "scguns:item.rocket_rifle.fire;195;19.5",
                                    "superbwarfare:taser_fire_1p;150;65",
                                    "superbwarfare:taser_fire_3p;150;65",
                                    "superbwarfare:taser_reload_empty;30;25",
                                    "superbwarfare:trachelium_fire_1p;150;65",
                                    "superbwarfare:trachelium_fire_3p;150;65",
                                    "superbwarfare:trachelium_far;250;150",
                                    "superbwarfare:trachelium_veryfar;400;300",
                                    "superbwarfare:trachelium_fire_1p_s;150;65",
                                    "superbwarfare:trachelium_fire_3p_s;150;65",
                                    "superbwarfare:trachelium_far_s;250;150",
                                    "superbwarfare:trachelium_reload_empty;30;25",
                                    "superbwarfare:trachelium_bolt;30;10",
                                    "superbwarfare:hunting_rifle_fire_1p;150;65",
                                    "superbwarfare:hunting_rifle_fire_3p;150;65",
                                    "superbwarfare:hunting_rifle_far;250;150",
                                    "superbwarfare:hunting_rifle_veryfar;400;300",
                                    "superbwarfare:hunting_rifle_reload_empty;30;25",
                                    "superbwarfare:m_79_fire_1p;150;65",
                                    "superbwarfare:m_79_fire_3p;150;65",
                                    "superbwarfare:m_79_far;250;150",
                                    "superbwarfare:m_79_veryfar;400;300",
                                    "superbwarfare:m_79_reload_empty;30;25",
                                    "superbwarfare:sks_fire_1p;150;65",
                                    "superbwarfare:sks_fire_3p;150;65",
                                    "superbwarfare:sks_reload_normal;30;25",
                                    "superbwarfare:sks_reload_empty;30;25",
                                    "superbwarfare:sks_far;250;150",
                                    "superbwarfare:sks_veryfar;400;300",
                                    "superbwarfare:homemade_shotgun_fire_1p;150;65",
                                    "superbwarfare:homemade_shotgun_fire_3p;150;65",
                                    "superbwarfare:homemade_shotgun_far;250;150",
                                    "superbwarfare:homemade_shotgun_veryfar;400;300",
                                    "superbwarfare:homemade_shotgun_reload_normal;30;25",
                                    "superbwarfare:homemade_shotgun_reload_empty;30;25",
                                    "superbwarfare:ak_47_fire_1p;150;65",
                                    "superbwarfare:ak_47_fire_1p_s;150;65",
                                    "superbwarfare:ak_47_fire_3p;150;65",
                                    "superbwarfare:ak_47_fire_3p_s;150;65",
                                    "superbwarfare:ak_47_far;250;150",
                                    "superbwarfare:ak_47_veryfar;400;300",
                                    "superbwarfare:ak_47_reload_normal;30;25",
                                    "superbwarfare:ak_47_reload_empty;30;25",
                                    "superbwarfare:devotion_fire_1p;150;65",
                                    "superbwarfare:devotion_fire_3p;150;65",
                                    "superbwarfare:devotion_far;250;150",
                                    "superbwarfare:devotion_veryfar;400;300",
                                    "superbwarfare:devotion_reload_normal;30;25",
                                    "superbwarfare:devotion_reload_empty;30;25",
                                    "superbwarfare:rpg_fire_1p;150;65",
                                    "superbwarfare:rpg_fire_3p;150;65",
                                    "superbwarfare:rpg_far;250;150",
                                    "superbwarfare:rpg_veryfar;400;300",
                                    "superbwarfare:rpg_reload_empty;30;25",
                                    "superbwarfare:m_4_fire_1p;150;65",
                                    "superbwarfare:m_4_fire_1p_s;150;65",
                                    "superbwarfare:m_4_fire_3p;150;65",
                                    "superbwarfare:m_4_fire_3p_s;150;65",
                                    "superbwarfare:m_4_far;250;150",
                                    "superbwarfare:m_4_veryfar;400;300",
                                    "superbwarfare:m_4_reload_normal;30;25",
                                    "superbwarfare:m_4_reload_empty;30;25",
                                    "superbwarfare:aa_12_fire_1p;150;65",
                                    "superbwarfare:aa_12_fire_3p;150;65",
                                    "superbwarfare:aa_12_far;250;150",
                                    "superbwarfare:aa_12_veryfar;400;300",
                                    "superbwarfare:aa_12_reload_normal;30;25",
                                    "superbwarfare:aa_12_reload_empty;30;25",
                                    "superbwarfare:bocek_zoom_fire_1p;150;65",
                                    "superbwarfare:bocek_zoom_fire_3p;150;65",
                                    "superbwarfare:bocek_shatter_cap_fire_1p;150;65",
                                    "superbwarfare:bocek_shatter_cap_fire_3p;150;65",
                                    "superbwarfare:bocek_pull_1p;30;10",
                                    "superbwarfare:bocek_pull_3p;30;10",
                                    "superbwarfare:hk_416_fire_1p;150;65",
                                    "superbwarfare:hk_416_fire_1p_s;150;65",
                                    "superbwarfare:hk_416_fire_3p;150;65",
                                    "superbwarfare:hk_416_fire_3p_s;150;65",
                                    "superbwarfare:hk_416_far;250;150",
                                    "superbwarfare:hk_416_veryfar;400;300",
                                    "superbwarfare:hk_416_reload_normal;30;25",
                                    "superbwarfare:hk_416_reload_empty;30;25",
                                    "superbwarfare:rpk_fire_1p;150;65",
                                    "superbwarfare:rpk_fire_3p;150;65",
                                    "superbwarfare:rpk_fire_1p_s;150;65",
                                    "superbwarfare:rpk_fire_3p_s;150;65",
                                    "superbwarfare:rpk_far;250;150",
                                    "superbwarfare:rpk_veryfar;400;300",
                                    "superbwarfare:rpk_reload_normal;30;25",
                                    "superbwarfare:rpk_reload_empty;30;25",
                                    "superbwarfare:ntw_20_fire_1p;150;65",
                                    "superbwarfare:ntw_20_fire_3p;150;65",
                                    "superbwarfare:ntw_20_far;250;150",
                                    "superbwarfare:ntw_20_veryfar;400;300",
                                    "superbwarfare:ntw_20_reload_normal;30;25",
                                    "superbwarfare:ntw_20_reload_empty;30;25",
                                    "superbwarfare:ntw_20_bolt;30;10",
                                    "superbwarfare:vector_fire_1p;150;65",
                                    "superbwarfare:vector_fire_3p;150;65",
                                    "superbwarfare:vector_far;250;150",
                                    "superbwarfare:vector_veryfar;400;300",
                                    "superbwarfare:vector_fire_1p_s;150;65",
                                    "superbwarfare:vector_fire_3p_s;150;65",
                                    "superbwarfare:vector_far_s;250;150",
                                    "superbwarfare:vector_reload_normal;30;25",
                                    "superbwarfare:vector_reload_empty;30;25",
                                    "superbwarfare:minigun_fire_1p;150;65",
                                    "superbwarfare:minigun_fire_3p;150;65",
                                    "superbwarfare:minigun_far;250;150",
                                    "superbwarfare:minigun_veryfar;400;300",
                                    "superbwarfare:minigun_rot;30;10",
                                    "superbwarfare:minigun_overheat;30;10",
                                    "superbwarfare:mk_14_fire_1p;150;65",
                                    "superbwarfare:mk_14_fire_3p;150;65",
                                    "superbwarfare:mk_14_far;250;150",
                                    "superbwarfare:mk_14_veryfar;400;300",
                                    "superbwarfare:mk_14_fire_1p_s;150;65",
                                    "superbwarfare:mk_14_fire_3p_s;150;65",
                                    "superbwarfare:mk_14_far_s;250;150",
                                    "superbwarfare:mk_14_reload_normal;30;25",
                                    "superbwarfare:mk_14_reload_empty;30;25",
                                    "superbwarfare:sentinel_fire_1p;150;65",
                                    "superbwarfare:sentinel_fire_3p;150;65",
                                    "superbwarfare:sentinel_charge_fire_1p;150;65",
                                    "superbwarfare:sentinel_charge_fire_3p;150;65",
                                    "superbwarfare:sentinel_far;250;150",
                                    "superbwarfare:sentinel_veryfar;400;300",
                                    "superbwarfare:sentinel_charge_far;250;150",
                                    "superbwarfare:sentinel_charge_veryfar;400;300",
                                    "superbwarfare:sentinel_reload_normal;30;25",
                                    "superbwarfare:sentinel_reload_empty;30;25",
                                    "superbwarfare:sentinel_charge;30;10",
                                    "superbwarfare:sentinel_bolt;30;10",
                                    "superbwarfare:m_60_fire_1p;150;65",
                                    "superbwarfare:m_60_fire_3p;150;65",
                                    "superbwarfare:m_60_far;250;150",
                                    "superbwarfare:m_60_veryfar;400;300",
                                    "superbwarfare:m_60_reload_normal;30;25",
                                    "superbwarfare:m_60_reload_empty;30;25",
                                    "superbwarfare:svd_fire_1p;150;65",
                                    "superbwarfare:svd_fire_3p;150;65",
                                    "superbwarfare:svd_far;250;150",
                                    "superbwarfare:svd_veryfar;400;300",
                                    "superbwarfare:svd_fire_1p_s;150;65",
                                    "superbwarfare:svd_fire_3p_s;150;65",
                                    "superbwarfare:svd_far_s;250;150",
                                    "superbwarfare:svd_reload_normal;30;25",
                                    "superbwarfare:svd_reload_empty;30;25",
                                    "superbwarfare:m_98b_fire_1p;150;65",
                                    "superbwarfare:m_98b_fire_3p;150;65",
                                    "superbwarfare:m_98b_fire_1p_s;150;65",
                                    "superbwarfare:m_98b_fire_3p_s;150;65",
                                    "superbwarfare:m_98b_far;250;150",
                                    "superbwarfare:m_98b_veryfar;400;300",
                                    "superbwarfare:m_98b_reload_normal;30;25",
                                    "superbwarfare:m_98b_reload_empty;30;25",
                                    "superbwarfare:m_98b_bolt;30;10",
                                    "superbwarfare:marlin_fire_1p;150;65",
                                    "superbwarfare:marlin_fire_3p;150;65",
                                    "superbwarfare:marlin_far;250;150",
                                    "superbwarfare:marlin_veryfar;400;300",
                                    "superbwarfare:marlin_loop;30;10",
                                    "superbwarfare:marlin_prepare;30;10",
                                    "superbwarfare:marlin_end;30;10",
                                    "superbwarfare:marlin_bolt;30;10",
                                    "superbwarfare:m_870_fire_1p;150;65",
                                    "superbwarfare:m_870_fire_3p;150;65",
                                    "superbwarfare:m_870_far;250;150",
                                    "superbwarfare:m_870_veryfar;400;300",
                                    "superbwarfare:m_870_prepare_load;30;10",
                                    "superbwarfare:m_870_loop;30;10",
                                    "superbwarfare:m_870_bolt;30;10",
                                    "superbwarfare:glock_17_fire_1p;150;65",
                                    "superbwarfare:glock_17_fire_3p;150;65",
                                    "superbwarfare:glock_17_far;250;150",
                                    "superbwarfare:glock_17_veryfar;400;300",
                                    "superbwarfare:glock_17_reload_normal;30;25",
                                    "superbwarfare:glock_17_reload_empty;30;25",
                                    "superbwarfare:mp_443_fire_1p;150;65",
                                    "superbwarfare:mp_443_fire_3p;150;65",
                                    "superbwarfare:m_1911_fire_1p;150;65",
                                    "superbwarfare:m_1911_fire_3p;150;65",
                                    "superbwarfare:m_1911_far;250;150",
                                    "superbwarfare:m_1911_veryfar;400;300",
                                    "superbwarfare:qbz_95_fire_1p;150;65",
                                    "superbwarfare:qbz_95_fire_1p_s;150;65",
                                    "superbwarfare:qbz_95_fire_3p;150;65",
                                    "superbwarfare:qbz_95_fire_3p_s;150;65",
                                    "superbwarfare:qbz_95_far;250;150",
                                    "superbwarfare:qbz_95_veryfar;400;300",
                                    "superbwarfare:qbz_95_reload_normal;30;25",
                                    "superbwarfare:qbz_95_reload_empty;30;25",
                                    "superbwarfare:k_98_fire_1p;150;65",
                                    "superbwarfare:k_98_fire_3p;150;65",
                                    "superbwarfare:k_98_far;250;150",
                                    "superbwarfare:k_98_veryfar;400;300",
                                    "superbwarfare:k_98_reload_empty;30;25",
                                    "superbwarfare:k_98_bolt;30;10",
                                    "superbwarfare:k_98_loop;30;10",
                                    "superbwarfare:k_98_prepare;30;10",
                                    "superbwarfare:k_98_end;30;10",
                                    "superbwarfare:mosin_nagant_fire_1p;150;65",
                                    "superbwarfare:mosin_nagant_fire_3p;150;65",
                                    "superbwarfare:mosin_nagant_far;250;150",
                                    "superbwarfare:mosin_nagant_veryfar;400;300",
                                    "superbwarfare:mosin_nagant_bolt;30;10",
                                    "superbwarfare:mosin_nagant_loop;30;10",
                                    "superbwarfare:mosin_nagant_prepare;30;10",
                                    "superbwarfare:mosin_nagant_prepare_empty;10;14",
                                    "superbwarfare:mosin_nagant_end;30;10",
                                    "superbwarfare:javelin_fire_1p;150;65",
                                    "superbwarfare:javelin_fire_3p;150;65",
                                    "superbwarfare:javelin_far;250;150",
                                    "superbwarfare:javelin_reload_empty;30;25",
                                    "superbwarfare:javelin_lock;30;10",
                                    "superbwarfare:javelin_lockon;30;10",
                                    "superbwarfare:ak_12_fire_1p;150;65",
                                    "superbwarfare:ak_12_fire_3p;150;65",
                                    "superbwarfare:ak_12_fire_1p_s;150;65",
                                    "superbwarfare:ak_12_fire_3p_s;150;65",
                                    "superbwarfare:ak_12_far;250;150",
                                    "superbwarfare:ak_12_veryfar;400;300",
                                    "superbwarfare:ak_12_far_s;250;150",
                                    "superbwarfare:ak_12_veryfar_s;400;300",
                                    "superbwarfare:ak_12_reload_normal;30;25",
                                    "superbwarfare:ak_12_reload_empty;30;25",
                                    "superbwarfare:bullet_supply;30;10",
                                    "superbwarfare:shock;30;10",
                                    "superbwarfare:electric;30;10",
                                    "superbwarfare:triggerclick;30;10",
                                    "superbwarfare:hit;30;10",
                                    "superbwarfare:targetdown;30;10",
                                    "superbwarfare:indication;30;10",
                                    "superbwarfare:indication_vehicle;30;10",
                                    "superbwarfare:jump;30;10",
                                    "superbwarfare:doublejump;30;10",
                                    "superbwarfare:explosion_close;30;10",
                                    "superbwarfare:explosion_far;250;150",
                                    "superbwarfare:explosion_very_far;400;300",
                                    "superbwarfare:explosion_water;30;10",
                                    "superbwarfare:ouch;30;10",
                                    "superbwarfare:step;30;10",
                                    "superbwarfare:growl;30;10",
                                    "superbwarfare:idle;30;10",
                                    "superbwarfare:land;30;10",
                                    "superbwarfare:headshot;30;10",
                                    "superbwarfare:mortar_fire;150;65",
                                    "superbwarfare:mortar_distant;30;10",
                                    "superbwarfare:mortar_load;30;10",
                                    "superbwarfare:firerate;150;65",
                                    "superbwarfare:adjust_fov;30;10",
                                    "superbwarfare:mk_42_fire_1p;150;65",
                                    "superbwarfare:mk_42_far;250;150",
                                    "superbwarfare:mk_42_veryfar;400;300",
                                    "superbwarfare:mk_42_fire_3p;150;65",
                                    "superbwarfare:cannon_reload;30;25",
                                    "superbwarfare:cannon_zoom_in;30;10",
                                    "superbwarfare:cannon_zoom_out;30;10",
                                    "superbwarfare:drone_sound;30;10",
                                    "superbwarfare:grenade_throw;30;10",
                                    "superbwarfare:grenade_pull;30;10",
                                    "superbwarfare:heng;30;10",
                                    "superbwarfare:edit_mode;30;10",
                                    "superbwarfare:edit;30;10",
                                    "superbwarfare:shell_casing_normal;30;10",
                                    "superbwarfare:shell_casing_shotgun;30;10",
                                    "superbwarfare:shell_casing_50cal;30;10",
                                    "superbwarfare:huge_explosion_close;30;10",
                                    "superbwarfare:huge_explosion_far;250;150",
                                    "superbwarfare:huge_explosion_very_far;400;300",
                                    "superbwarfare:open;30;10",
                                    "superbwarfare:charge_rifle_fire_1p;150;65",
                                    "superbwarfare:charge_rifle_fire_3p;150;65",
                                    "superbwarfare:charge_rifle_fire_boom_1p;150;65",
                                    "superbwarfare:charge_rifle_fire_boom_3p;150;65",
                                    "superbwarfare:annihilator_fire_1p;150;65",
                                    "superbwarfare:annihilator_fire_3p;150;65",
                                    "superbwarfare:annihilator_far;250;150",
                                    "superbwarfare:annihilator_veryfar;400;300",
                                    "superbwarfare:annihilator_reload;30;25",
                                    "superbwarfare:boat_engine;30;10",
                                    "superbwarfare:vehicle_strike;30;10",
                                    "superbwarfare:m_2_hb_fire_1p;150;65",
                                    "superbwarfare:m_2_hb_fire_3p;150;65",
                                    "superbwarfare:m_2_hb_far;250;150",
                                    "superbwarfare:m_2_hb_veryfar;400;300",
                                    "superbwarfare:m_2_hb_reload_normal;30;25",
                                    "superbwarfare:m_2_hb_reload_empty;30;25",
                                    "superbwarfare:wheel_chair_engine;30;10",
                                    "superbwarfare:wheel_chair_jump;30;10",
                                    "superbwarfare:radar_search_start;30;10",
                                    "superbwarfare:radar_search_idle;30;10",
                                    "superbwarfare:radar_search_end;30;10",
                                    "superbwarfare:helicopter_engine_start;30;10",
                                    "superbwarfare:helicopter_engine;30;10",
                                    "superbwarfare:heli_cannon_fire_1p;150;65",
                                    "superbwarfare:heli_cannon_fire_3p;150;65",
                                    "superbwarfare:heli_cannon_far;250;150",
                                    "superbwarfare:heli_cannon_veryfar;400;300",
                                    "superbwarfare:small_rocket_fire_1p;150;65",
                                    "superbwarfare:small_rocket_fire_3p;150;65",
                                    "superbwarfare:into_cannon;30;10",
                                    "superbwarfare:into_missile;30;10",
                                    "superbwarfare:missile_reload;30;25",
                                    "superbwarfare:low_health;30;10",
                                    "superbwarfare:no_health;30;10",
                                    "superbwarfare:locking_warning;30;10",
                                    "superbwarfare:locked_warning;30;10",
                                    "superbwarfare:missile_warning;30;10",
                                    "superbwarfare:decoy_reload;30;25",
                                    "superbwarfare:decoy_fire;150;65",
                                    "superbwarfare:secondary_cataclysm_fire_1p;150;65",
                                    "superbwarfare:secondary_cataclysm_fire_3p;150;65",
                                    "superbwarfare:secondary_cataclysm_far;250;150",
                                    "superbwarfare:secondary_cataclysm_veryfar;400;300",
                                    "superbwarfare:secondary_cataclysm_loop;30;10",
                                    "superbwarfare:secondary_cataclysm_prepare_load;30;10",
                                    "superbwarfare:secondary_cataclysm_end;30;10",
                                    "superbwarfare:secondary_cataclysm_fire_1p_charge;150;65",
                                    "superbwarfare:secondary_cataclysm_fire_3p_charge;150;65",
                                    "superbwarfare:secondary_cataclysm_far_charge;250;150",
                                    "superbwarfare:secondary_cataclysm_veryfar_charge;400;300",
                                    "superbwarfare:lunge_mine_growl;30;10",
                                    "superbwarfare:lav_fire_1p;150;65",
                                    "superbwarfare:lav_fire_3p;150;65",
                                    "superbwarfare:lav_far;250;150",
                                    "superbwarfare:lav_veryfar;400;300",
                                    "superbwarfare:lav_engine;30;10",
                                    "superbwarfare:coax_fire_1p;150;65",
                                    "superbwarfare:bmp_fire_1p;150;65",
                                    "superbwarfare:bmp_fire_3p;150;65",
                                    "superbwarfare:bmp_engine;30;10",
                                    "superbwarfare:bmp_missile_fire_1p;150;65",
                                    "superbwarfare:bmp_missile_fire_3p;150;65",
                                    "superbwarfare:bmp_missile_reload;30;25",
                                    "superbwarfare:wheel_step;30;10",
                                    "superbwarfare:laser_tower_shoot;30;10",
                                    "superbwarfare:yx_100_reload;30;25",
                                    "superbwarfare:yx_100_fire_1p;150;65",
                                    "superbwarfare:yx_100_fire_3p;150;65",
                                    "superbwarfare:yx_100_far;250;150",
                                    "superbwarfare:yx_100_veryfar;400;300",
                                    "superbwarfare:yx_100_engine;30;10",
                                    "superbwarfare:turret_turn;30;10",
                                    "superbwarfare:c4_beep;30;10",
                                    "superbwarfare:c4_final;30;10",
                                    "superbwarfare:c4_throw;30;10",
                                    "superbwarfare:c4_detonator_click;30;10",
                                    "superbwarfare:prism_fire_1p;150;65",
                                    "superbwarfare:prism_fire_3p;150;65",
                                    "superbwarfare:prism_fire_1p_2;150;65",
                                    "superbwarfare:prism_fire_3p_2;150;65",
                                    "superbwarfare:prism_engine;30;10",
                                    "superbwarfare:insidious_fire_1p;150;65",
                                    "superbwarfare:insidious_fire_3p;150;65",
                                    "superbwarfare:insidious_far;250;150",
                                    "superbwarfare:insidious_veryfar;400;300",
                                    "superbwarfare:insidious_reload_empty;30;25",
                                    "superbwarfare:smoke_fire;150;65",
                                    "superbwarfare:hpj_11_fire_3p;150;65",
                                    "superbwarfare:track_move;30;10",
                                    "superbwarfare:rocket_fly;30;10",
                                    "superbwarfare:shell_fly;30;10",
                                    "superbwarfare:rocket_engine;30;10",
                                    "superbwarfare:vehicle_swim;30;10",
                                    "superbwarfare:a10_engine;30;10",
                                    "superbwarfare:a10_fire;150;65",
                                    "superbwarfare:bomb_release;30;10",
                                    "superbwarfare:missile_start;30;10",
                                    "superbwarfare:bomb_reload;30;25",
                                    "superbwarfare:jet_lock;30;10",
                                    "superbwarfare:jet_lockon;30;10",
                                    "superbwarfare:dps_generator_evolve;30;10",
                                    "superbwarfare:melee_hit;30;10",
                                    "superbwarfare:aurelia_sceptre_fire_1p;150;65",
                                    "superbwarfare:aurelia_sceptre_fire_3p;150;65",
                                    "superbwarfare:fly_loop;30;10",
                                    "superbwarfare:mp_5_fire_1p;150;65",
                                    "superbwarfare:mp_5_fire_3p;150;65",
                                    "superbwarfare:mp_5_far;250;150",
                                    "superbwarfare:mp_5_veryfar;400;300",
                                    "superbwarfare:mp_5_fire_1p_s;150;65",
                                    "superbwarfare:mp_5_fire_3p_s;150;65",
                                    "superbwarfare:mp_5_reload_normal;30;25",
                                    "superbwarfare:mp_5_reload_empty;30;25",
                                    "superbwarfare:steel_pipe_hit;30;10",
                                    "superbwarfare:steel_pipe_drop;30;10",
                                    "superbwarfare:qbz_191_fire_1p;150;65",
                                    "superbwarfare:qbz_191_fire_1p_s;150;65",
                                    "superbwarfare:qbz_191_fire_3p;150;65",
                                    "superbwarfare:qbz_191_fire_3p_s;150;65",
                                    "superbwarfare:qbz_191_far;250;150",
                                    "superbwarfare:qbz_191_veryfar;400;300",
                                    "superbwarfare:qbz_191_reload_normal;30;25",
                                    "superbwarfare:qbz_191_reload_empty;30;25",
                                    "superbwarfare:smoke_grenade_release;30;10",
                                    "superbwarfare:hand_wheel_rot;30;10",
                                    "superbwarfare:medium_rocket_fire;150;65",
                                    "superbwarfare:ty63_reload;30;25",
                                    "pointblank:gun_action;15;5"
                            ),
                            obj -> obj instanceof String && ((String) obj).split(";").length == 3);
            builder.pop();

            builder.comment("====================================================================",
                    " Sound Attract Mod - Stealth & Detection Configuration",
                    "====================================================================").push("sound_attract_main");
            builder.comment("--- Field of View Settings ---").push("fov");
            defaultHorizontalFov = builder.comment(
                    "The default horizontal Field of View for all mobs not in the override list.",
                    "Represents the width of the vision cone in degrees."
            )
                    .defineInRange("defaultHorizontalFov", 200.0, 0.0, 360.0);

            defaultVerticalFov = builder.comment(
                    "The default vertical Field of View for all mobs not in the override list.",
                    "Represents the height of the vision cone in degrees."
            )
                    .defineInRange("defaultVerticalFov", 135.0, 0.0, 360.0);
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
                    .defineList("nonBlockingVisionAllowList", Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

            builder.pop();

            builder.comment("General Stealth System Settings").push("general_stealth_settings");
            enableStealthMechanics = builder.comment("Master switch for all custom stealth mechanics. If false, mobs use vanilla detection (modified only by maxStealthDetectionRange if set).")
                    .define("enableStealthMechanics", true);
            stealthCheckInterval = builder.comment("How often (in ticks) the server checks ongoing stealth situations (e.g., for grace period). Lower is more responsive but higher performance cost.", "20 ticks = 1 second.")
                    .defineInRange("stealthCheckInterval", 40, 5, 100);
            stealthGracePeriodTicks = builder.comment("How long (in ticks) a mob will keep targeting a player after losing direct detection (due to stealth) before giving up.", "Set to 0 for no grace period (immediate de-aggro if stealth conditions met).")
                    .defineInRange("stealthGracePeriodTicks", 100, 0, 200);
            builder.pop();

            builder.comment("Base detection ranges for players based on their stance.", "These are modified by all other factors (light, camo, etc.).").push("player_stance_detection_ranges");
            standingDetectionRangePlayer = builder.comment("Base detection range (in blocks) when a player is standing.").defineInRange("standingDetectionRangePlayer", 32.0, 0.0, 512.0);
            sneakingDetectionRangePlayer = builder.comment("Base detection range (in blocks) when a player is sneaking (crouching).").defineInRange("sneakingDetectionRangePlayer", 12.0, 0.0, 512.0);
            crawlingDetectionRangePlayer = builder.comment("Base detection range (in blocks) when a player is crawling (e.g., in a 1-block high gap).")
                    .defineInRange("crawlingDetectionRangePlayer", 4.0, 0.0, 512.0);
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
                            obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
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
            maxStealthDetectionRange = builder.comment("The absolute maximum detection range (in blocks). Player cannot be easier to detect than this.", "Also used as default range if 'enableStealthMechanics' is false.")
                    .defineInRange("maxStealthDetectionRange", 64.0, 1.0, 1024.0);
            builder.pop();

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
            taczAttachmentFlashReductionDefault = builder.comment("Default flash reduction value for Tacz attachments if the attachment id is not in the list.")
                    .defineInRange("taczAttachmentFlashReductionDefault", 0.0, -300.0, 300.0);

            builder.pop();

            builder.comment("Point Blank Integration Configuration").push("pointblank");
            enablePointBlankIntegration = builder.comment("Enable Point Blank gun integration").define("enablePointBlankIntegration", true);
            pointBlankReloadRange = builder.comment("Point Blank reload sound range fallback (used when specific gun data is not provided)").defineInRange("pointBlankReloadRange", 9, 1.0, 128.0);
            pointBlankReloadWeight = builder.comment("Point Blank reload sound weight fallback (used when specific gun data is not provided)").defineInRange("pointBlankReloadWeight", 1.0, 0.0, 10.0);
            pointBlankShootRange = builder.comment("Point Blank shoot sound range fallback (used when specific gun data is not provided)").defineInRange("pointBlankShootRange", 140.0, 1.0, 256.0);
            pointBlankShootWeight = builder.comment("Point Blank shoot sound weight fallback (used when specific gun data is not provided)").defineInRange("pointBlankShootWeight", 15.0, 0.0, 10.0);
            pointBlankGunShootRanges = builder.comment("Point Blank gun shoot ranges (when provided, override fallback). Format: 'modid:item;range' (range in blocks)")
                    .defineList("pointBlankGunShootRanges", Arrays.asList(
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
            pointBlankAttachmentSoundReductions = builder.comment("Point Blank attachment sound reduction. Format: 'modid:item;reduction'. Positive reduces range, negative increases.")
                    .defineList("pointBlankAttachmentSoundReductions", Arrays.asList(
                            "pointblank:ar_suppressor;40.0",
                            "pointblank:ar_suppressor_tan;40.0",
                            "pointblank:xm7_suppressor;40.0",
                            "pointblank:ak_suppressor;40.0",
                            "pointblank:smg_suppressor;40.0",
                            "pointblank:rf_suppressor;40.0",
                            "pointblank:hp_suppressor;40.0",
                            "pointblank:sg_suppressor;40.0"
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
            pointBlankAttachmentReductionDefault = builder.comment("Default sound reduction for Point Blank attachments not listed above.")
                    .defineInRange("pointBlankAttachmentReductionDefault", 20.0, -300.0, 300.0);
            pointBlankMuzzleFlashReductions = builder.comment("Point Blank attachment VISUAL FLASH reduction. Positive reduces flash range, negative increases. Format: 'modid:item;reduction_amount'")
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
            enableVoiceChatIntegration = builder.comment(
                    "Enable Simple Voice Chat (SVC) integration.",
                    "When enabled, voice chat frames generate a dynamic sound whose range scales with the audio's peak level (dBFS).",
                    "Optional: Only takes effect if SVC is installed.")
                    .define("enableVoiceChatIntegration", true);
            voiceChatWhisperRange = builder.comment(
                    "Base range used when the player is whispering in SVC (before applying dB multiplier).")
                    .defineInRange("voiceChatWhisperRange", 16, 1, 64);
            voiceChatNormalRange = builder.comment(
                    "Base range used for normal speaking in SVC (before applying dB multiplier).")
                    .defineInRange("voiceChatNormalRange", 32, 1, 128);
            voiceChatWeight = builder.comment(
                    "Weight assigned to the generated SVC sound event.")
                    .defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
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
                    .defineList("customWoolBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
            customSolidBlocks = builder.comment("List of custom solid block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customSolidBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
            customNonSolidBlocks = builder.comment("List of custom non-solid block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customNonSolidBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
            customThinBlocks = builder.comment("List of custom thin block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customThinBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
            customLiquidBlocks = builder.comment("List of custom liquid block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customLiquidBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
            customAirBlocks = builder.comment("List of custom air block IDs for sound muffling. Format: 'modid:blockid'. Default: empty list.")
                    .defineList("customAirBlocks", java.util.Collections.emptyList(), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

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

        if (COMMON.configSchemaVersion.get() < 2) {
            SoundAttractMod.LOGGER.info("Migrating config from version 1 to 2.");
            com.electronwill.nightconfig.core.UnmodifiableConfig config = COMMON_SPEC.getValues();

            moveConfigValue(config, "maxSoundSources", COMMON.maxSoundsTracked);
            moveConfigValue(config, "soundProcessingInterval", COMMON.scanCooldownTicks);
            moveConfigValue(config, "maxMuffledBlocks", COMMON.maxMufflingBlocksToCheck);

            COMMON.configSchemaVersion.set(2);
            SoundAttractMod.LOGGER.info("Config migration complete. New schema version: 2. Saving config...");
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 3) {
            SoundAttractMod.LOGGER.info("Migrating config from version 2 to 3 (relocating special profiles to 'profiles' section).");
            com.electronwill.nightconfig.core.UnmodifiableConfig config = COMMON_SPEC.getValues();

            moveConfigValue(config, "muffling.specialMobProfilesRaw", COMMON.specialMobProfilesRaw);
            moveConfigValue(config, "muffling.specialPlayerProfilesRaw", COMMON.specialPlayerProfilesRaw);

            COMMON.configSchemaVersion.set(3);
            SoundAttractMod.LOGGER.info("Config migration complete. New schema version: 3. Saving config...");
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 4) {
            SoundAttractMod.LOGGER.info("Migrating config from version 3 to 4 (moving group settings to 'groups' section).");
            com.electronwill.nightconfig.core.UnmodifiableConfig config = COMMON_SPEC.getValues();

            moveConfigValue(config, "general.maxGroupSize", COMMON.maxGroupSize);
            moveConfigValue(config, "general.leaderGroupRadius", COMMON.leaderGroupRadius);
            moveConfigValue(config, "general.groupDistance", COMMON.groupDistance);
            moveConfigValue(config, "general.leaderSpacingMultiplier", COMMON.leaderSpacingMultiplier);
            moveConfigValue(config, "general.numEdgeSectors", COMMON.numEdgeSectors);
            moveConfigValue(config, "general.groupUpdateInterval", COMMON.groupUpdateInterval);
            moveConfigValue(config, "general.maxLeaders", COMMON.maxLeaders);

            moveConfigValue(config, "general.edgeMobSmartBehavior", COMMON.edgeMobSmartBehavior);
            moveConfigValue(config, "general.soundLifetimeTicks", COMMON.soundLifetimeTicks);
            moveConfigValue(config, "general.arrivalDistance", COMMON.arrivalDistance);
            moveConfigValue(config, "general.mobMoveSpeed", COMMON.mobMoveSpeed);

            COMMON.configSchemaVersion.set(4);
            SoundAttractMod.LOGGER.info("Config migration complete. New schema version: 4. Saving config...");
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 5) {
            SoundAttractMod.LOGGER.info("Migrating config from version 4 to 5.");

            COMMON.configSchemaVersion.set(5);
            SoundAttractMod.LOGGER.info("Config migration complete. New schema version: 5. Saving config...");
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 6) {
            SoundAttractMod.LOGGER.info("Migrating config from version 5 to 6 (adding Enhanced AI integration keys).");
            // No values to move; new keys will be created with defaults on save.
            COMMON.configSchemaVersion.set(6);
            SoundAttractMod.LOGGER.info("Config migration complete. New schema version: 6. Saving config...");
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 7) {
            COMMON.configSchemaVersion.set(7);
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 8) {
            COMMON.configSchemaVersion.set(8);
            COMMON_SPEC.save();
        }

        if (COMMON.configSchemaVersion.get() < 9) {
            COMMON.configSchemaVersion.set(9);
            COMMON_SPEC.save();
        }

        if (COMMON == null) {
            SoundAttractMod.LOGGER.warn("SoundAttractConfig.COMMON is null during bakeConfig. Skipping cache population.");
            return;
        }

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

        List<MobProfile> tmpMobProfiles = new ArrayList<>();
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

                ResourceLocation mobId = null;
                CompoundTag nbtMatcher = null;
                Map<ResourceLocation, com.example.soundattract.config.SoundOverride> soundOverrides = new HashMap<>();
                Map<PlayerStance, Double> detectionOverrides = new HashMap<>();

                String mobIdString = parts[1].trim();
                if (!mobIdString.isEmpty() && !mobIdString.equals("*")) {
                    mobId = ResourceLocation.tryParse(mobIdString);
                    if (mobId == null) {
                        SoundAttractMod.LOGGER.warn("Invalid mob ID '{}' for profile '{}'. This part of the matching will be ignored.", mobIdString, profileName);
                    }
                }

                String nbtMatcherString = parts[2].trim();
                if (!nbtMatcherString.isEmpty()) {
                    try {
                        nbtMatcher = TagParser.parseTag(nbtMatcherString);
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
                        soundOverrides.values().stream().map(so -> new com.example.soundattract.config.SoundOverride(so.getSoundId(), so.getRange(), so.getWeight())).toList(),
                        detectionOverrides
                );
                tmpMobProfiles.add(profile);
                if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached mob profile: {}", profileName);
                }
            }
        }
        boolean enableDataDrivenProfiles = COMMON != null && COMMON.enableDataDriven != null && COMMON.enableDataDriven.get();
        String profilePriority = COMMON != null && COMMON.datapackPriority != null ? COMMON.datapackPriority.get() : "datapack_over_config";
        boolean profilesDpOverConfig = "datapack_over_config".equalsIgnoreCase(profilePriority);

        if (enableDataDrivenProfiles && DP_MOB_PROFILES_CACHE != null && !DP_MOB_PROFILES_CACHE.isEmpty()) {
            List<MobProfile> merged = new ArrayList<>();
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

        List<PlayerProfile> tmpPlayerProfiles = new ArrayList<>();
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
                tmpPlayerProfiles.add(profile);
                if (COMMON != null && COMMON.debugLogging != null && COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached player profile: {}", profileName);
                }
            }
        }
        if (enableDataDrivenProfiles && DP_PLAYER_PROFILES_CACHE != null && !DP_PLAYER_PROFILES_CACHE.isEmpty()) {
            List<PlayerProfile> mergedPlayers = new ArrayList<>();
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

    public static MobProfile getMatchingProfile(Mob mob) {
        if (SPECIAL_MOB_PROFILES_CACHE == null || SPECIAL_MOB_PROFILES_CACHE.isEmpty()) {
            return null;
        }

        if (mob == null) {
            return null;
        }

        if (QuantifiedCacheCompat.isUsable()) {
            String key = new StringBuilder(96)
                .append(mob.getUUID().toString()).append('|')
                .append(SPECIAL_MOB_PROFILES_CACHE.size())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_mob_profile_match",
                key,
                () -> getMatchingProfileUncached(mob),
                1L,
                8192L
            );
        }

        return getMatchingProfileUncached(mob);
    }

    private static MobProfile getMatchingProfileUncached(Mob mob) {
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

    private static PlayerProfile getMatchingPlayerProfileUncached(Player player) {
        for (PlayerProfile profile : SPECIAL_PLAYER_PROFILES_CACHE) {
            if (profile.matches(player)) {
                return profile;
            }
        }
        return null;
    }
}
