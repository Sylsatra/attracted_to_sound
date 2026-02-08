package com.example.soundattract.config.separate;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class ScentConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_SYSTEM;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCENT_ELIGIBLE_MOBS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCENT_PARTICLES;
    public static final ForgeConfigSpec.IntValue SCENT_NODE_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue SCENT_CREATION_INTERVAL_BLOCKS;
    
    public static final ForgeConfigSpec.DoubleValue TEMP_HEAVY_DECAY_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue TEMP_FREEZE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue HUMIDITY_PRESERVE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue RAIN_DECAY_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue WATER_STOPS_SCENT;
    
    public static final ForgeConfigSpec.IntValue SCENT_AMBUSH_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue SCENT_UPDATE_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue SCENT_STRENGTH_DECAY;
    public static final ForgeConfigSpec.DoubleValue SCENT_DETECTION_RADIUS;
    public static final ForgeConfigSpec.BooleanValue RAIN_WASHES_SCENTS;

    static {
        BUILDER.comment("Sound Attract Mod - Scent System Configuration").push("scent_system");

        ENABLE_SCENT_SYSTEM = BUILDER.comment("Enable the scent tracking system for mobs.")
                .define("enableScentSystem", true);

        SCENT_ELIGIBLE_MOBS = BUILDER.comment("List of mobs that can smell and track scent trails.")
                .defineList("scentEligibleMobs", Arrays.asList("minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:wolf"), obj -> obj instanceof String);

        ENABLE_SCENT_PARTICLES = BUILDER.comment("Render debug particles for scent nodes.")
                .define("enableScentParticles", false);

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
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
