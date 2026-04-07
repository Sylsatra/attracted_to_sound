package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class IntegrationConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SMART_BRAIN_LIB_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CUSTOM_NPCS_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_CACHE_INTEGRATION;
    public static final ForgeConfigSpec.IntValue QUANTIFIED_CACHE_MEMORY_LIMIT_MB;
    public static final ForgeConfigSpec.BooleanValue DISABLE_QUANTIFIED_CACHE_ON_MEMORY_PRESSURE;
    public static final ForgeConfigSpec.BooleanValue TRIGGER_QUANTIFIED_CACHE_CLEANUP_ON_MEMORY_PRESSURE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUANTIFIED_SOUND_SCORE_SLICE_CACHE;
    public static final ForgeConfigSpec.BooleanValue QUANTIFIED_SOUND_SCORE_SLICE_CACHE_PERSISTENT;
    public static final ForgeConfigSpec.IntValue QUANTIFIED_SOUND_SCORE_SLICE_CACHE_TTL_TICKS;
    public static final ForgeConfigSpec.IntValue QUANTIFIED_SOUND_SCORE_SLICE_CACHE_MAX_ENTRIES;

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ForgeConfigSpec.DoubleValue BLOCK_BREAKING_TIME_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue BLOCK_BREAKING_TOOL_ONLY;
    public static final ForgeConfigSpec.BooleanValue BLOCK_BREAKING_PROPER_TOOL_ONLY;

    public static final ForgeConfigSpec.BooleanValue ENABLE_TELEPORT_TO_SOUND;
    public static final ForgeConfigSpec.DoubleValue TELEPORT_CHANCE;
    public static final ForgeConfigSpec.IntValue TELEPORT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> TELEPORT_CAN_TELEPORT_TAG;
    public static final ForgeConfigSpec.ConfigValue<String> TELEPORT_CAN_BE_TELEPORTED_TAG;

    public static final ForgeConfigSpec.BooleanValue ENABLE_PICK_UP_AND_THROW_TO_SOUND;
    public static final ForgeConfigSpec.DoubleValue PICK_UP_CHANCE;
    public static final ForgeConfigSpec.IntValue PICK_UP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PICK_UP_MIN_DISTANCE_TO_PICK_UP;
    public static final ForgeConfigSpec.IntValue PICK_UP_MAX_DISTANCE_TO_THROW;
    public static final ForgeConfigSpec.DoubleValue PICK_UP_SPEED_MODIFIER;
    public static final ForgeConfigSpec.ConfigValue<String> PICK_UP_CAN_PICK_UP_TAG;
    public static final ForgeConfigSpec.ConfigValue<String> PICK_UP_CAN_BE_PICKED_UP_TAG;

    public static final ForgeConfigSpec.BooleanValue ENABLE_XRAY_TARGETING;
    public static final ForgeConfigSpec.ConfigValue<String> XRAY_APPLY_TAG;
    public static final ForgeConfigSpec.BooleanValue XRAY_REQUIRE_BETTER_NEARBY;
    public static final ForgeConfigSpec.ConfigValue<String> XRAY_BETTER_NEARBY_TAG;
    public static final ForgeConfigSpec.IntValue XRAY_MIN_RANGE;
    public static final ForgeConfigSpec.IntValue XRAY_MAX_RANGE;
    public static final ForgeConfigSpec.DoubleValue XRAY_CHANCE;



    public static final ForgeConfigSpec.BooleanValue ENABLE_RELENTLESS_CLIMBING;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RELENTLESS_ELIGIBLE_MOBS;
    public static final ForgeConfigSpec.BooleanValue ZOMBIES_IGNORE_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue ZOMBIES_CAN_STACK;
    public static final ForgeConfigSpec.DoubleValue ZOMBIE_FALL_DAMAGE_MULTIPLIER;
    
    public static final ForgeConfigSpec.BooleanValue ENABLE_IMMERSIVE_MELODIES_INTEGRATION;
    public static final ForgeConfigSpec.IntValue IMMERSIVE_MELODIES_POLL_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue IMMERSIVE_MELODIES_DEFAULT_RANGE;
    public static final ForgeConfigSpec.DoubleValue IMMERSIVE_MELODIES_DEFAULT_WEIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IMMERSIVE_MELODIES_MELODY_OVERRIDES;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SPORE_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SPORE_SCENT_TRAIL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STEALTH_MARKER_BRIDGE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_BLOCK_SUPPRESSION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROTO_SOUND_DEPLOYMENT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SOUND_RELAY;
    public static final ForgeConfigSpec.BooleanValue SMART_SPREAD_TO_SOUND;
    public static final ForgeConfigSpec.DoubleValue STEALTH_MARKER_CAMO_THRESHOLD;
    public static final ForgeConfigSpec.IntValue STEALTH_BRIDGE_CHECK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SCENT_ENTITY_DISSIPATION_ACCEL;
    public static final ForgeConfigSpec.DoubleValue PROTO_SOUND_WEIGHT_THRESHOLD;
    public static final ForgeConfigSpec.IntValue PROTO_SOUND_DEPLOYMENT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PROTO_SOUND_BIOMASS_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue PROTO_SPREAD_MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue PROTO_SPREAD_LERP_FACTOR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROTO_PREDATORY_CREEP;
    public static final ForgeConfigSpec.DoubleValue PROTO_CREEP_ADVANCE_DISTANCE;
    public static final ForgeConfigSpec.IntValue PROTO_CREEP_INTERVAL_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> VIGIL_SIGHTED_SOUND;
    public static final ForgeConfigSpec.ConfigValue<String> PROTO_HUNT_BEGIN_SOUND;
    public static final ForgeConfigSpec.ConfigValue<String> PROTO_HUNT_BEGIN_PARTICLE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROTO_NEURAL_INFLUENCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HARMONIC_FEAR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DELUGE_OF_SOUND;
    public static final ForgeConfigSpec.DoubleValue HARMONIC_FEAR_RANGE;
    public static final ForgeConfigSpec.DoubleValue DELUGE_OF_SOUND_WEIGHT_THRESHOLD;

    public static final ForgeConfigSpec.DoubleValue SOUND_RELAY_RANGE;

    static {
        BUILDER.comment("Settings for other mod integrations").push("integration");

        ENABLE_QUANTIFIED_INTEGRATION = BUILDER.define("enableQuantifiedIntegration", true);
        ENABLE_SMART_BRAIN_LIB_INTEGRATION = BUILDER.define("enableSmartBrainLibIntegration", true);
        ENABLE_CUSTOM_NPCS_INTEGRATION = BUILDER.define("enableCustomNpcsIntegration", true);
        
        BUILDER.push("quantified_cache");
        ENABLE_QUANTIFIED_CACHE_INTEGRATION = BUILDER.define("enableQuantifiedCacheIntegration", true);
        QUANTIFIED_CACHE_MEMORY_LIMIT_MB = BUILDER.defineInRange("quantifiedCacheMemoryLimitMB", 256, 0, 65536);
        DISABLE_QUANTIFIED_CACHE_ON_MEMORY_PRESSURE = BUILDER.define("disableQuantifiedCacheOnMemoryPressure", true);
        TRIGGER_QUANTIFIED_CACHE_CLEANUP_ON_MEMORY_PRESSURE = BUILDER.define("triggerQuantifiedCacheCleanupOnMemoryPressure", true);
        ENABLE_QUANTIFIED_SOUND_SCORE_SLICE_CACHE = BUILDER.define("enableQuantifiedSoundScoreSliceCache", true);
        QUANTIFIED_SOUND_SCORE_SLICE_CACHE_PERSISTENT = BUILDER.define("quantifiedSoundScoreSliceCachePersistent", false);
        QUANTIFIED_SOUND_SCORE_SLICE_CACHE_TTL_TICKS = BUILDER.defineInRange("quantifiedSoundScoreSliceCacheTtlTicks", 20, 1, 72000);
        QUANTIFIED_SOUND_SCORE_SLICE_CACHE_MAX_ENTRIES = BUILDER.defineInRange("quantifiedSoundScoreSliceCacheMaxEntries", 4096, 64, 1_000_000);
        BUILDER.pop();

        BUILDER.push("enhanced_ai_inspired");
        ENABLE_BLOCK_BREAKING = BUILDER.define("enableBlockBreaking", false);
        BLOCK_BREAKING_TIME_MULTIPLIER = BUILDER.defineInRange("blockBreakingTimeMultiplier", 1.5, -1.0, 100.0);
        BLOCK_BREAKING_TOOL_ONLY = BUILDER.define("blockBreakingToolOnly", false);
        BLOCK_BREAKING_PROPER_TOOL_ONLY = BUILDER.define("blockBreakingProperToolOnly", false);

        ENABLE_TELEPORT_TO_SOUND = BUILDER.define("enableTeleportToSound", false);
        TELEPORT_CHANCE = BUILDER.defineInRange("teleportChance", 0.35, 0.0, 1.0);
        TELEPORT_COOLDOWN_TICKS = BUILDER.defineInRange("teleportCooldownTicks", 300, 0, 72000);
        TELEPORT_CAN_TELEPORT_TAG = BUILDER.define("teleportCanTeleportTag", "enhancedai:mobs/teleport_to_target/can_teleport");
        TELEPORT_CAN_BE_TELEPORTED_TAG = BUILDER.comment("EntityType tag used to decide which mobs can be teleported. Defaults to EnhancedAI's tag.")
                .define("teleportCanBeTeleportedTag", "enhancedai:mobs/teleport_to_target/can_be_teleported");

        ENABLE_PICK_UP_AND_THROW_TO_SOUND = BUILDER.comment("Enable special AI: mobs with the specified tag can pick up another mob and throw it toward the sound location.")
                .define("enablePickUpAndThrowToSound", false);
        PICK_UP_CHANCE = BUILDER.comment("Chance [0..1] for a thrower mob to attempt the pick-up-and-throw behavior. If EnhancedAI is installed, its difficulty-based chance is used instead.")
                .defineInRange("pickUpChance", 0.05, 0.0, 1.0);
        PICK_UP_COOLDOWN_TICKS = BUILDER.comment("Cooldown (in ticks) after a throw action. If EnhancedAI is installed, its value is used instead.")
                .defineInRange("pickUpCooldownTicks", 600, 0, 72000);
        PICK_UP_MIN_DISTANCE_TO_PICK_UP = BUILDER.comment("Minimum distance from the sound for the mob to consider picking up a target.")
                .defineInRange("pickUpMinDistanceToPickUp", 5, 0, 1024);
        PICK_UP_MAX_DISTANCE_TO_THROW = BUILDER.comment("Max distance to the sound within which the mob will release/throw the picked-up mob.")
                .defineInRange("pickUpMaxDistanceToThrow", 24, 0, 1024);
        PICK_UP_SPEED_MODIFIER = BUILDER.comment("Speed modifier applied to the mob while approaching the pick-up target.")
                .defineInRange("pickUpSpeedModifier", 1.25, 0.0, 10.0);
        PICK_UP_CAN_PICK_UP_TAG = BUILDER.comment("EntityType tag used to decide which mobs can perform pick-up-and-throw. Defaults to EnhancedAI's tag.")
                .define("pickUpCanPickUpTag", "enhancedai:mobs/pick_up_and_throw/can_pick_up");
        PICK_UP_CAN_BE_PICKED_UP_TAG = BUILDER.comment("EntityType tag used to decide which mobs can be picked up. Defaults to EnhancedAI's tag.")
                .define("pickUpCanBePickedUpTag", "enhancedai:mobs/pick_up_and_throw/can_be_picked_up");

        ENABLE_XRAY_TARGETING = BUILDER.comment("Enable XRAY targeting compat: mobs in the apply_xray tag can detect players through walls up to a configured range.")
                .define("enableXrayTargeting", false);
        XRAY_APPLY_TAG = BUILDER.comment("EntityType tag used to decide which mobs can have XRAY detection. Defaults to EnhancedAI's tag.")
                .define("xrayApplyTag", "enhancedai:mobs/targeting/apply_xray");
        XRAY_REQUIRE_BETTER_NEARBY = BUILDER.comment("Require the mob to also be in the Better Nearby Targeting tag to apply XRAY (mirrors EnhancedAI behavior).")
                .define("xrayRequireBetterNearby", false);
        XRAY_BETTER_NEARBY_TAG = BUILDER.comment("EntityType tag for Better Nearby Targeting. Used only if xrayRequireBetterNearby is true.")
                .define("xrayBetterNearbyTag", "enhancedai:mobs/targeting/better_nearby_targeting");
        XRAY_MIN_RANGE = BUILDER.comment("Minimum XRAY follow range (blocks) for fallback when EnhancedAI is not present. 0..128")
                .defineInRange("xrayMinRange", 16, 0, 128);
        XRAY_MAX_RANGE = BUILDER.comment("Maximum XRAY follow range (blocks). 0 disables XRAY fallback.")
                .defineInRange("xrayMaxRange", 24, 0, 128);
        XRAY_CHANCE = BUILDER.comment("Chance [0..1] for a mob in the XRAY tag to get the XRAY range (fallback when EnhancedAI is not present).")
                .defineInRange("xrayChance", 0.5, 0.0, 1.0);

        BUILDER.pop();

        BUILDER.push("relentless_undead_inspired");
        ENABLE_RELENTLESS_CLIMBING = BUILDER.define("enableRelentlessClimbing", false);
        RELENTLESS_ELIGIBLE_MOBS = BUILDER.defineList("relentlessEligibleMobs", Arrays.asList("minecraft:zombie", "minecraft:husk", "minecraft:drowned"), obj -> obj instanceof String);
        ZOMBIES_IGNORE_HEIGHT = BUILDER.define("zombiesIgnoreHeight", false);
        ZOMBIES_CAN_STACK = BUILDER.define("zombiesCanStack", true);
        ZOMBIE_FALL_DAMAGE_MULTIPLIER = BUILDER.defineInRange("zombieFallDamageMultiplier", 0.5, 0.0, 10.0);
        BUILDER.pop();
        
        BUILDER.push("immersive_melodies");
        ENABLE_IMMERSIVE_MELODIES_INTEGRATION = BUILDER.comment("Enable integration with Immersive Melodies: playing instruments attracts mobs.")
                .define("enableImmersiveMelodiesIntegration", true);
        IMMERSIVE_MELODIES_POLL_INTERVAL = BUILDER.comment("How often (in ticks) to check if a player is playing an instrument. A higher value reduces overhead and increases the gap in the sound trail.")
                .defineInRange("immersiveMelodiesPollInterval", 40, 1, 1200);
        IMMERSIVE_MELODIES_DEFAULT_RANGE = BUILDER.comment("Default attraction range for instruments.")
                .defineInRange("immersiveMelodiesDefaultRange", 48.0, 0.0, 512.0);
        IMMERSIVE_MELODIES_DEFAULT_WEIGHT = BUILDER.comment("Default weight for instruments.")
                .defineInRange("immersiveMelodiesDefaultWeight", 30.0, 0.0, 100.0);
        IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS = BUILDER.comment("List of instrument item IDs and their weight multipliers (e.g. 'immersive_melodies:drum:2.0').")
                .defineList("immersiveMelodiesInstrumentMultipliers", Arrays.asList("immersive_melodies:drum:1.5"), obj -> obj instanceof String);
        IMMERSIVE_MELODIES_MELODY_OVERRIDES = BUILDER.comment("List of melody IDs and their custom range:weight (e.g. 'immersive_melodies:test_melody:32.0:3.0').")
                .defineList("immersiveMelodiesMelodyOverrides", Arrays.asList(), obj -> obj instanceof String);
        BUILDER.pop();

        BUILDER.push("spore");
        ENABLE_SPORE_INTEGRATION = BUILDER.comment("Master toggle for Spore mod integration. Enables sound attraction, stealth bridge, and Proto deployment for Spore entities.")
                .define("enableSporeIntegration", true);
        ENABLE_SPORE_SCENT_TRAIL = BUILDER.comment("Inject FollowScentGoal into Spore Infected entities so they can follow player scent trails.")
                .define("enableSporeScentTrail", true);
        ENABLE_STEALTH_MARKER_BRIDGE = BUILDER.comment("Bridge Sound Attract's camouflage system with Spore's MARKER effect. Degraded camo applies MARKER, making the entity visible to all Infected through walls.")
                .define("enableStealthMarkerBridge", true);
        ENABLE_SCENT_BLOCK_SUPPRESSION = BUILDER.comment("When a player with high scent-blocking camo is near a Spore ScentEntity, accelerate its dissipation.")
                .define("enableScentBlockSuppression", true);
        ENABLE_PROTO_SOUND_DEPLOYMENT = BUILDER.comment("Allow Proto to react to high-weight sounds by deploying Vigil scouts toward the sound source.")
                .define("enableProtoSoundDeployment", true);
        ENABLE_SOUND_RELAY = BUILDER.comment("Allow linked Infected to propagate sound positions to nearby linked Infected via their searchPos.")
                .define("enableSoundRelay", true);
        SMART_SPREAD_TO_SOUND = BUILDER.comment("Proto waits for scout confirmation before spreading biomass toward sound. If false, spreads immediately.")
                .define("smartSpreadToSound", true);

        STEALTH_MARKER_CAMO_THRESHOLD = BUILDER.comment("Visual camo strength below which MARKER is applied when degrading from above.")
                .defineInRange("stealthMarkerCamoThreshold", 0.3, 0.0, 1.0);
        STEALTH_BRIDGE_CHECK_INTERVAL_TICKS = BUILDER.comment("How often (in ticks) the stealth bridge scans for camo transitions and ScentEntity suppression.")
                .defineInRange("stealthBridgeCheckIntervalTicks", 40, 1, 1200);
        SCENT_ENTITY_DISSIPATION_ACCEL = BUILDER.comment("Extra dissipation ticks added per bridge cycle to ScentEntities near camo'd entities.")
                .defineInRange("scentEntityDissipationAccel", 40, 1, 600);

        PROTO_SOUND_WEIGHT_THRESHOLD = BUILDER.comment("Minimum sound weight for Proto to consider deploying scouts.")
                .defineInRange("protoSoundWeightThreshold", 10.0, 0.0, 100.0);
        PROTO_SOUND_DEPLOYMENT_COOLDOWN_TICKS = BUILDER.comment("Cooldown (in ticks) between Proto scout deployments.")
                .defineInRange("protoSoundDeploymentCooldownTicks", 200, 0, 72000);
        PROTO_SOUND_BIOMASS_THRESHOLD = BUILDER.comment("Minimum biomass for Proto to deploy scouts.")
                .defineInRange("protoSoundBiomassThreshold", 20, 0, 10000);
        PROTO_SPREAD_MAX_DISTANCE = BUILDER.comment("Maximum distance from Proto's NODE for biased biomass spread toward sound. Beyond this, no spread occurs.")
                .defineInRange("protoSpreadMaxDistance", 256.0, 0.0, 512.0);
        PROTO_SPREAD_LERP_FACTOR = BUILDER.comment("How far toward the sound source to bias the spread center (0.0=at NODE, 1.0=at sound).")
                .defineInRange("protoSpreadLerpFactor", 0.35, 0.0, 1.0);

        BUILDER.push("predatory_creep");
        ENABLE_PROTO_PREDATORY_CREEP = BUILDER.comment("If true, when a Vigil spots a player, the Proto will creep biomass toward their location.")
                .define("enableProtoPredatoryCreep", true);
        PROTO_CREEP_ADVANCE_DISTANCE = BUILDER.comment("Distance (blocks) the biomass advances per creeping step.")
                .defineInRange("protoCreepAdvanceDistance", 8.0, 1.0, 64.0);
        PROTO_CREEP_INTERVAL_TICKS = BUILDER.comment("Ticks between creeping steps. Higher values reduce CPU usage.")
                .defineInRange("protoCreepIntervalTicks", 200, 20, 12000);
        VIGIL_SIGHTED_SOUND = BUILDER.comment("Sound played by the Vigil when spotting a player to alert the Proto. Default is Spore's eye stare sound.")
                .define("vigilSightedSound", "spore:vigil_eye_use");
        PROTO_HUNT_BEGIN_SOUND = BUILDER.comment("Sound played by the Proto when it begins a predatory hunt.")
                .define("protoHuntBeginSound", "spore:proto_ambient");
        PROTO_HUNT_BEGIN_PARTICLE = BUILDER.comment("Particle played at the Proto's NODE when a hunt begins.")
                .define("protoHuntBeginParticle", "minecraft:sculk_soul");
        
        ENABLE_PROTO_NEURAL_INFLUENCE = BUILDER.comment("Neural-Net Influence: Loud sounds bias Proto toward heavy units; quiet sounds bias it toward scouts.")
                .define("enableProtoNeuralInfluence", true);
        ENABLE_HARMONIC_FEAR = BUILDER.comment("Harmonic Fear: Hive Tumors panic if players make sound while standing on biomass.")
                .define("enableHarmonicFear", true);
        ENABLE_DELUGE_OF_SOUND = BUILDER.comment("Deluge of Sound: Delusionares warp nearby infected to noisy players.")
                .define("enableDelugeOfSound", true);
        HARMONIC_FEAR_RANGE = BUILDER.comment("Range (blocks) for Hive Tumor biomass sound detection.")
                .defineInRange("harmonicFearRange", 32.0, 1.0, 512.0);
        DELUGE_OF_SOUND_WEIGHT_THRESHOLD = BUILDER.comment("Minimum sound weight to trigger Delusionare's mass teleportation.")
                .defineInRange("delugeOfSoundWeightThreshold", 20.0, 1.0, 100.0);
        BUILDER.pop();

        SOUND_RELAY_RANGE = BUILDER.comment("Range (blocks) for linked Infected sound propagation to nearby Infected.")
                .defineInRange("soundRelayRange", 32.0, 0.0, 256.0);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
