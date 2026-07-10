package com.example.soundattract.config.separate;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Arrays;
import java.util.List;

public class ScentConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_SCENT_SYSTEM;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SCENT_ELIGIBLE_MOBS;
    public static final ModConfigSpec.BooleanValue ENABLE_SCENT_PARTICLES;
    public static final ModConfigSpec.BooleanValue ENABLE_GAMEPLAY_SCENT_PARTICLES;
    public static final ModConfigSpec.IntValue SCENT_PARTICLE_SPAWN_INTERVAL;
    public static final ModConfigSpec.DoubleValue SCENT_PARTICLE_RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue SCENT_NODE_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue SCENT_CREATION_INTERVAL_BLOCKS;
    
    public static final ModConfigSpec.DoubleValue TEMP_HEAVY_DECAY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TEMP_FREEZE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HUMIDITY_PRESERVE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue RAIN_DECAY_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WATER_STOPS_SCENT;
    
    public static final ModConfigSpec.IntValue SCENT_AMBUSH_DURATION_TICKS;
    public static final ModConfigSpec.IntValue SCENT_UPDATE_INTERVAL;
    public static final ModConfigSpec.DoubleValue SCENT_STRENGTH_DECAY;
    public static final ModConfigSpec.DoubleValue SCENT_DETECTION_RADIUS;
    public static final ModConfigSpec.BooleanValue RAIN_WASHES_SCENTS;
    
    public static final ModConfigSpec.IntValue SCENT_SCAN_BUDGET_PER_TICK;

    public static final ModConfigSpec.BooleanValue ENABLE_ARROW_SCENT_TRAIL;
    public static final ModConfigSpec.DoubleValue ARROW_SCENT_EMISSION_INTERVAL_BLOCKS;
    public static final ModConfigSpec.DoubleValue ARROW_SCENT_STRENGTH;
    public static final ModConfigSpec.IntValue ARROW_SCENT_NODE_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue SCENT_OVERRIDE_SOUND_PRIORITY;
    public static final ModConfigSpec.BooleanValue ENABLE_SCENT_PARTICLES_FOR_ARROWS;
    public static final ModConfigSpec.IntValue ARROW_SCENT_RATE_LIMIT_PER_SHOOTER_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue SCENT_VS_SOUND_HYBRID_MULTIPLIER;
    public static final ModConfigSpec.IntValue SCENT_QUERY_CACHE_TTL_TICKS;
    public static final ModConfigSpec.IntValue SCENT_QUERY_CACHE_MAX_ENTRIES;

    static {
        BUILDER.comment("Sound Attract Mod - Scent System Configuration").push("scent_system");

        ENABLE_SCENT_SYSTEM = BUILDER.comment("Enable the scent tracking system for mobs.")
                .define("enableScentSystem", true);

        SCENT_ELIGIBLE_MOBS = BUILDER.comment("List of mobs that can smell and track scent trails.")
                .defineList("scentEligibleMobs", Arrays.asList("minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:wolf"), obj -> obj instanceof String);

        ENABLE_SCENT_PARTICLES = BUILDER.comment("Render debug particles for scent nodes (debug only, one-shot).")
                .define("enableScentParticles", false);

        ENABLE_GAMEPLAY_SCENT_PARTICLES = BUILDER.comment("Enable persistent gameplay scent particles that last as long as the scent node. Per-player colored.")
                .define("enableGameplayScentParticles", false);

        SCENT_PARTICLE_SPAWN_INTERVAL = BUILDER.comment("Ticks between re-spawning particles at each living scent node (lower = more frequent, higher = better performance).")
                .defineInRange("scentParticleSpawnInterval", 40, 5, 200);

        SCENT_PARTICLE_RENDER_DISTANCE = BUILDER.comment("Maximum distance from a player to render scent particles.")
                .defineInRange("scentParticleRenderDistance", 32.0, 8.0, 128.0);

        SCENT_NODE_DURATION_TICKS = BUILDER.comment("How long a scent node lasts in ticks (default 6000 = 5 minutes).")
                .defineInRange("scentNodeDurationTicks", 6000, 100, 72000);

        SCENT_CREATION_INTERVAL_BLOCKS = BUILDER.comment("Distance in blocks the player must move to create a new scent node.")
                .defineInRange("scentCreationIntervalBlocks", 5.0, 1.0, 64.0);

        BUILDER.push("environmental_logic");
        TEMP_HEAVY_DECAY_THRESHOLD = BUILDER.comment("Biome temperature above which scent decays faster (Hot).")
                .defineInRange("tempHeavyDecayThreshold", 1.0, 0.0, 2.0);
        TEMP_FREEZE_THRESHOLD = BUILDER.comment("Biome temperature below which scent is preserved but has reduced range (Cold).")
                .defineInRange("tempFreezeThreshold", 0.15, -1.0, 2.0);
        HUMIDITY_PRESERVE_THRESHOLD = BUILDER.comment("Biome rainfall/humidity above which scent lasts longer (High Humidity).")
                .defineInRange("humidityPreserveThreshold", 0.8, 0.0, 1.0);
        RAIN_DECAY_MULTIPLIER = BUILDER.comment("Multiplier for scent decay rate when raining.")
                .defineInRange("rainDecayMultiplier", 4.0, 1.0, 100.0);
        WATER_STOPS_SCENT = BUILDER.comment("If true, players in water will not leave scent trails.")
                .define("waterStopsScent", true);
        RAIN_WASHES_SCENTS = BUILDER.comment("If true, rain will wash away scents faster.")
                .define("rainWashesScents", true);
        BUILDER.pop();

        BUILDER.push("ai_logic");
        SCENT_AMBUSH_DURATION_TICKS = BUILDER.comment("How long a mob will 'camp' at the last known location of an offline player.")
                .defineInRange("scentAmbushDurationTicks", 400, 0, 72000);
        SCENT_UPDATE_INTERVAL = BUILDER.comment("Interval (in ticks) for updating scent logic.")
                .defineInRange("scentUpdateInterval", 20, 1, 100);
        SCENT_STRENGTH_DECAY = BUILDER.comment("Amount of scent strength lost per tick.")
                .defineInRange("scentStrengthDecay", 0.01, 0.0, 1.0);
        SCENT_DETECTION_RADIUS = BUILDER.comment("Radius within which mobs can detect scents.")
                .defineInRange("scentDetectionRadius", 16.0, 1.0, 64.0);
        SCENT_SCAN_BUDGET_PER_TICK = BUILDER.comment("Maximum number of mobs that can compute a new scent path per tick (prevents lag spikes during horde scent tracking).")
                .defineInRange("scentScanBudgetPerTick", 5, 1, 100);
        BUILDER.pop();

        BUILDER.push("arrow_scent");
        ENABLE_ARROW_SCENT_TRAIL = BUILDER.comment("Enable scent trails from arrows and projectiles.")
                .define("enableArrowScentTrail", true);
        ARROW_SCENT_EMISSION_INTERVAL_BLOCKS = BUILDER.comment("Distance in blocks an arrow must travel to emit a new scent node.")
                .defineInRange("arrowScentEmissionIntervalBlocks", 3.0, 0.5, 16.0);
        ARROW_SCENT_STRENGTH = BUILDER.comment("Base strength of arrow scent nodes.")
                .defineInRange("arrowScentStrength", 0.8, 0.1, 2.0);
        ARROW_SCENT_NODE_DURATION_TICKS = BUILDER.comment("How long arrow scent nodes last in ticks (default 500 = 25 seconds).")
                .defineInRange("arrowScentNodeDurationTicks", 500, 100, 72000);
        SCENT_OVERRIDE_SOUND_PRIORITY = BUILDER.comment("If true, fresh scent trails override sound attraction priority.")
                .define("scentOverrideSoundPriority", true);
        ENABLE_SCENT_PARTICLES_FOR_ARROWS = BUILDER.comment("Render particles for arrow scent trails.")
                .define("enableScentParticlesForArrows", false);
        ARROW_SCENT_RATE_LIMIT_PER_SHOOTER_PER_MINUTE = BUILDER.comment("Max arrow scent nodes a single shooter can create per minute.")
                .defineInRange("arrowScentRateLimitPerShooterPerMinute", 20, 10, 500);
        SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD = BUILDER
                .comment("When scentOverrideSoundPriority=true: sounds with weight strictly below this value always lose to fresh scent.")
                .defineInRange("scentOverrideSoundWeightThreshold", 10.0, 0.0, 1000.0);
        SCENT_VS_SOUND_HYBRID_MULTIPLIER = BUILDER
                .comment("Above the threshold: soundWeight is compared against (scentStrength * multiplier). Higher = scent wins more often.")
                .defineInRange("scentVsSoundHybridMultiplier", 1.0, 0.0, 100.0);
        SCENT_QUERY_CACHE_TTL_TICKS = BUILDER
                .comment("How long scent-query results are cached per mob (ticks). Lower = fresher but more CPU.")
                .defineInRange("scentQueryCacheTtlTicks", 5, 1, 200);
        SCENT_QUERY_CACHE_MAX_ENTRIES = BUILDER
                .comment("Maximum mobs tracked in the scent-query cache (memory cap).")
                .defineInRange("scentQueryCacheMaxEntries", 4096, 128, 65536);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
