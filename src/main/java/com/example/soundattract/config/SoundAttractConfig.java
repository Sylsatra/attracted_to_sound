package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.function.Function;
import java.util.Collections; 
import java.util.function.Predicate; 
import java.util.function.Supplier; 

public class SoundAttractConfig {


    public static class Common {
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> attractedEntities;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> soundConfigsRaw; 
        public final ForgeConfigSpec.IntValue soundLifetimeTicks;
        public final ForgeConfigSpec.IntValue scanCooldownTicks;
        public final ForgeConfigSpec.DoubleValue arrivalDistance;
        public final ForgeConfigSpec.DoubleValue mobMoveSpeed;
        public final ForgeConfigSpec.IntValue mobScanRadius;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerStepSounds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerSpeedConfigsRaw; 
        public final ForgeConfigSpec.BooleanValue enableSoundBasedDetection;
        public final ForgeConfigSpec.BooleanValue enableMovementBasedDetection;
        public final ForgeConfigSpec.IntValue movementCheckFrequencyTicks;
        public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration;
        public final ForgeConfigSpec.IntValue voiceChatWhisperRange;
        public final ForgeConfigSpec.IntValue voiceChatNormalRange;
        public final ForgeConfigSpec.DoubleValue voiceChatWeight;
        public final ForgeConfigSpec.BooleanValue enableTaczIntegration;
        public final ForgeConfigSpec.DoubleValue taczReloadRange;
        public final ForgeConfigSpec.IntValue taczReloadWeight;
        public final ForgeConfigSpec.DoubleValue taczShootRange;
        public final ForgeConfigSpec.IntValue taczShootWeight;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment(
                "Sound Attract Mod Configuration",
                "------------------------------------",
                "This mod allows specific entities to be attracted to certain sounds.",
                "Modify this config to control which mobs react and what sounds they respond to.",
                "------------------------------------",
                "FINDING SOUND IDs:",
                "  - Vanilla Minecraft: Check the official Minecraft Wiki for a list of sound event IDs.",
                "    Link: https://minecraft.fandom.com/wiki/Sounds.json#Java_Edition_values ",
                "  - Modded Sounds: Look inside the mod's JAR file or source code (if available).",
                "    Navigate to: src/main/resources/assets/<modid>/sounds.json ",
                "    Find the sound event name, formatted like 'modid:sound_name'.",
                "FINDING ENTITY IDs:",
                "  - Use the in-game /summon command. Start typing '/summon minecraft:' or '/summon <modid>:' ",
                "    and the game will suggest valid entity IDs."
            ).push("General");

            attractedEntities = builder
                .comment(
                    "List of entity IDs that should be attracted to configured sounds.",
                    "Example: Add \"minecraft:skeleton\" to make skeletons react."
                )
                .defineList("attractedEntities", Arrays.asList(
                    // Default Hostile Mobs List
                    "minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned",
                    "minecraft:endermite", "minecraft:evoker", "minecraft:guardian", "minecraft:hoglin", "minecraft:husk",
                    "minecraft:magma_cube", "minecraft:phantom", "minecraft:piglin", "minecraft:piglin_brute",
                    "minecraft:pillager", "minecraft:ravager", "minecraft:shulker", "minecraft:silverfish",
                    "minecraft:skeleton", "minecraft:slime", "minecraft:spider", "minecraft:stray",
                    "minecraft:vex", "minecraft:vindicator", "minecraft:witch",
                    "minecraft:wither_skeleton", "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager",
                    "minecraft:zombified_piglin"
                ), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null); 

            soundConfigsRaw = builder
                .comment(
                    "List of standard sound events and their attraction configuration.",
                    "Format: \"sound_event_id;range;weight\"",
                    "  - sound_event_id: The ID of the sound (e.g., 'minecraft:block.lever.click').",
                    "  - range: Max distance (in blocks) mobs will notice the sound (integer > 0).",
                    "  - weight: Priority (integer > 0, higher=more important).",
                    "Example: \"minecraft:block.lever.click;24;1\""
                )
                .defineList("soundConfigs", Arrays.asList(
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
                    "minecraft:entity.firework_rocket.large_blast;30;6"
                ), obj -> { 
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 3);
                    if (parts.length != 3) return false;
                    if (ResourceLocation.tryParse(parts[0]) == null) return false;
                    try {
                        int range = Integer.parseInt(parts[1]);
                        int weight = Integer.parseInt(parts[2]);
                        return range > 0 && weight > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });

            soundLifetimeTicks = builder
                .comment("How long (in ticks) a sound remains 'interesting' after being heard. 20 ticks = 1 second.")
                .defineInRange("soundLifetimeTicks", 60, 1, Integer.MAX_VALUE);

            scanCooldownTicks = builder
                .comment("How often (in ticks) the mob scans for new sounds. 20 = once per second.")
                .defineInRange("scanCooldownTicks", 20, 1, Integer.MAX_VALUE);

            arrivalDistance = builder
                .comment(
                    "The distance (in blocks) at which the mob is considered to have reached the sound.",
                    "Example: If set to 4.0, mobs will stop moving toward the sound when within 4 blocks."
                 )
                .defineInRange("arrivalDistance", 4.0, 0.5, 64.0);

            mobMoveSpeed = builder
                .comment("The movement speed modifier for mobs moving towards a sound.")
                .defineInRange("mobMoveSpeed", 1.0, 0.1, 5.0);

            mobScanRadius = builder
                .comment("The radius (in blocks) mobs use to initially detect sounds with the AttractionGoal.")
                .defineInRange("mobScanRadius", 32, 4, 128);

            builder.pop();

            builder.push("Player Movement Sounds");

            enableSoundBasedDetection = builder
                .comment("Enable sound detection for regular player steps (walking, sprinting, falling).")
                .define("enableSoundBasedDetection", true);

            enableMovementBasedDetection = builder
                .comment("Enable movement state detection for specific actions (sneaking, potentially modded crawling/proning).")
                .define("enableMovementBasedDetection", true);

             movementCheckFrequencyTicks = builder
                .comment("How often (in ticks) to check player movement state for sneak/crawl/prone detection. 4 ticks = 5 times/sec.")
                .defineInRange("movementCheckFrequencyTicks", 4, 1, 60);

            playerStepSounds = builder
                .comment(
                    "List of sound event IDs considered player steps (e.g., walking, running, sneaking).",
                    "These sounds will use dynamic range/weight based on player speed.",
                    "At least one sound MUST be defined here if using movement based detection.",
                    "Make sure these IDs exist!"
                 )
                .defineList("playerStepSounds", Arrays.asList(
                    "minecraft:block.stone.step",
                    "minecraft:block.wood.step",
                    "minecraft:block.grass.step",
                    "minecraft:block.deepslate.step",
                    "minecraft:block.deepslate_bricks.step",
                    "minecraft:block.deepslate_tiles.step",
                    "minecraft:block.mud_bricks.step",
                    "minecraft:block.nether_bricks.step",
                    "minecraft:block.netherrack.step",
                    "minecraft:block.sand.step",
                    "minecraft:block.gravel.step",
                    "minecraft:block.snow.step",
                    "minecraft:block.suspicious_gravel.step",
                    "minecraft:block.suspicious_sand.step",
                    "minecraft:block.polished_deepslate.step",
                    "minecraft:block.packed_mud.step",
                    "minecraft:block.nylium.step",
                    "minecraft:block.roots.step",
                    "minecraft:block.tuff.step",
                    "minecraft:block.soul_sand.step",
                    "minecraft:block.soul_soil.step",
                    "minecraft:block.pointed_dripstone.step",
                    "minecraft:block.sculk.step",
                    "minecraft:block.sculk_sensor.step",
                    "minecraft:block.sculk_catalyst.step",
                    "minecraft:block.sculk_shrieker.step",
                    "minecraft:block.sculk_vein.step",
                    "minecraft:block.shroomlight.step",
                    "minecraft:block.spore_blossom.step",
                    "minecraft:block.stem.step",
                    "minecraft:block.lodestone.step",
                    "minecraft:block.scaffolding.step",
                    "minecraft:block.wet_grass.step",
                    "minecraft:block.wet_sponge.step",
                    "minecraft:block.ladder.step",
                    "minecraft:block.lantern.step",
                    "minecraft:block.wool.step"
                ), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

            playerSpeedConfigsRaw = builder
                .comment(
                    "List of player speed configurations determining sound range/weight.",
                    "Format: \"minSpeed;maxSpeed;range;weight\"",
                    "  - minSpeed: Minimum speed (blocks/sec, >= 0).",
                    "  - maxSpeed: Maximum speed (blocks/sec, >= minSpeed).",
                    "  - range: Detection range (integer > 0).",
                    "  - weight: Attraction weight (integer > 0).",
                    "Speed ranges should ideally not overlap."
                )
                .defineList("playerSpeedConfigs", Arrays.asList(
                    "0.1;1.0;2;1",
                    "0.1;1.5;4;1",    
                    "1.51;4.5;8;1",  
                    "4.51;6.0;16;1",  
                    "6.01;10.0;24;1"  
                ), obj -> { 
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 4);
                    if (parts.length != 4) return false;
                    try {
                        double minSpeed = Double.parseDouble(parts[0]);
                        double maxSpeed = Double.parseDouble(parts[1]);
                        int range = Integer.parseInt(parts[2]);
                        int weight = Integer.parseInt(parts[3]);
                        return minSpeed >= 0 && maxSpeed >= minSpeed && range > 0 && weight > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });

            builder.pop();

            builder.push("Voice Chat Integration (Requires Simple Voice Chat mod)");
            enableVoiceChatIntegration = builder
                .comment("Enable integration with the Simple Voice Chat mod to attract mobs to voice sounds.")
                .define("enableVoiceChatIntegration", true);
                
            voiceChatWhisperRange = builder
                .comment("Attraction range (in blocks) for whispering voice chat sounds.")
                .defineInRange("voiceChatWhisperRange", 4, 0, 64);
                
            voiceChatNormalRange = builder
                .comment("Attraction range (in blocks) for normal (non-whispering) voice chat sounds.")
                .defineInRange("voiceChatNormalRange", 16, 0, 128);
                
            voiceChatWeight = builder
                .comment("Attraction weight for voice chat sounds (higher means more priority).")
                .defineInRange("voiceChatWeight", 10.0, 0.0, 100.0);
                
            builder.pop(); 

            builder.push("TaCz Gun Mod Integration (Requires TaCz mod)");
            enableTaczIntegration = builder.define("enableTaczIntegration", true);
            taczReloadRange = builder.defineInRange("taczReloadRange", 8.0, 0.0, 256.0);
            taczReloadWeight = builder.defineInRange("taczReloadWeight", 3, 0, 100);
            taczShootRange = builder.defineInRange("taczShootRange", 128.0, 0.0, 512.0);
            taczShootWeight = builder.defineInRange("taczShootWeight", 10, 0, 100);
            builder.pop(); 
        }
    }



    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static List<String> ATTRACTED_ENTITIES_CACHE = new ArrayList<>();
    public static Map<SoundEvent, SoundConfig> SOUND_CONFIGS_CACHE = new HashMap<>();
    public static List<SoundEvent> PLAYER_STEP_SOUNDS_CACHE = new ArrayList<>();
    public static List<PlayerSpeedConfig> PLAYER_SPEED_CONFIGS_CACHE = new ArrayList<>();
    public static boolean SOUND_BASED_DETECTION_ENABLED_CACHE = true;
    public static boolean MOVEMENT_BASED_DETECTION_ENABLED_CACHE = true;
    public static int MOVEMENT_CHECK_FREQUENCY_TICKS_CACHE = 4;
    public static boolean VOICE_CHAT_ENABLED_CACHE = false;
    public static boolean VOICE_CHAT_DETECTION_ENABLED_CACHE = false;
    public static int VOICE_CHAT_WHISPER_RANGE_CACHE = 4;
    public static int VOICE_CHAT_NORMAL_RANGE_CACHE = 16;
    public static double VOICE_CHAT_WEIGHT_CACHE = 1.0;
    public static boolean TACZ_ENABLED_CACHE = false;
    public static double TACZ_RELOAD_RANGE_CACHE = 8.0;
    public static int TACZ_RELOAD_WEIGHT_CACHE = 1;
    public static double TACZ_SHOOT_RANGE_CACHE = 128.0;
    public static int TACZ_SHOOT_WEIGHT_CACHE = 5;

    public static class SoundConfig {
        public final int range;
        public final double weight;
        public SoundConfig(int range, double weight) { this.range = range; this.weight = weight; }
    }
    public static class PlayerSpeedConfig {
        public final double minSpeed;
        public final double maxSpeed;
        public final int range;
        public final double weight;
        public PlayerSpeedConfig(double minSpeed, double maxSpeed, int range, double weight) {
            this.minSpeed = minSpeed; this.maxSpeed = maxSpeed; this.range = range; this.weight = weight;
        }
    }

    public static void bakeConfig() {
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baking configuration...");

        ATTRACTED_ENTITIES_CACHE = new ArrayList<>(COMMON.attractedEntities.get());
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baked {} attracted entity IDs.", ATTRACTED_ENTITIES_CACHE.size());

        SOUND_CONFIGS_CACHE.clear();
        Map<SoundEvent, SoundConfig> bakedSounds = new HashMap<>();
        List<? extends String> rawSounds = COMMON.soundConfigsRaw.get(); 
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baking {} standard sound config entries from raw list.", rawSounds.size());

        for (String rawEntry : rawSounds) {
            try {
                String[] parts = rawEntry.split(";", 3);
                if (parts.length == 3) {
                    ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                    if (rl != null) {
                         SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                         if (se != null) {
                            int range = Integer.parseInt(parts[1]);
                            double weight = Double.parseDouble(parts[2]);
                            if (range > 0 && weight > 0) {
                                bakedSounds.put(se, new SoundConfig(range, weight));
                            } else {
                                SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Invalid range/weight in baked sound config entry '{}'. Must be positive.", rawEntry);
                            }
                         } else {
                             SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Configured sound ID '{}' does not correspond to a registered SoundEvent.", parts[0]);
                         }
                    } else {
                         SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Configured sound ID '{}' is not a valid ResourceLocation format.", parts[0]);
                    }
                } else {
                    SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Invalid sound config entry format '{}'. Expected 'id;range;weight'.", rawEntry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.error("[SoundAttract] Error parsing sound config entry '{}': {}", rawEntry, e.getMessage());
            }
        }
        SOUND_CONFIGS_CACHE = bakedSounds;
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baked {} standard sound configs.", SOUND_CONFIGS_CACHE.size());

        SOUND_BASED_DETECTION_ENABLED_CACHE = COMMON.enableSoundBasedDetection.get();
        MOVEMENT_BASED_DETECTION_ENABLED_CACHE = COMMON.enableMovementBasedDetection.get();
        MOVEMENT_CHECK_FREQUENCY_TICKS_CACHE = COMMON.movementCheckFrequencyTicks.get();
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Sound-based detection: {}. Movement-based detection: {}. Movement check freq: {} ticks.",
               SOUND_BASED_DETECTION_ENABLED_CACHE, MOVEMENT_BASED_DETECTION_ENABLED_CACHE, MOVEMENT_CHECK_FREQUENCY_TICKS_CACHE);

        PLAYER_STEP_SOUNDS_CACHE.clear();
        List<SoundEvent> bakedPlayerSounds = new ArrayList<>();
        for (String soundIdStr : COMMON.playerStepSounds.get()) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(soundIdStr);
                if (rl != null) {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                    if (se != null) {
                        bakedPlayerSounds.add(se);
                    } else {
                        SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Configured player step sound ID '{}' does not correspond to a registered SoundEvent.", soundIdStr);
                    }
                } else {
                    SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Configured player step sound ID '{}' is not a valid ResourceLocation format.", soundIdStr);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.error("[SoundAttract] Error parsing player step sound entry '{}': {}", soundIdStr, e.getMessage());
            }
        }
        PLAYER_STEP_SOUNDS_CACHE = bakedPlayerSounds;
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baked {} player step sounds.", PLAYER_STEP_SOUNDS_CACHE.size());

        PLAYER_SPEED_CONFIGS_CACHE.clear();
        List<PlayerSpeedConfig> bakedSpeedConfigs = new ArrayList<>();
        List<? extends String> rawSpeedConfigs = COMMON.playerSpeedConfigsRaw.get(); 
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baking {} player speed config entries from raw list.", rawSpeedConfigs.size());

        for (String rawEntry : rawSpeedConfigs) {
            try {
                String[] parts = rawEntry.split(";", 4);
                if (parts.length == 4) {
                    double minSpeed = Double.parseDouble(parts[0]);
                    double maxSpeed = Double.parseDouble(parts[1]);
                    int range = Integer.parseInt(parts[2]);
                    double weight = Double.parseDouble(parts[3]);
                    if (minSpeed >= 0 && maxSpeed >= minSpeed && range > 0 && weight > 0) {
                        bakedSpeedConfigs.add(new PlayerSpeedConfig(minSpeed, maxSpeed, range, weight));
                    } else {
                         SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Invalid values in baked player speed config entry '{}'. Skipping.", rawEntry);
                    }
                } else {
                     SoundAttractMod.LOGGER.warn("[SoundAttract] Warning: Invalid player speed config entry format '{}'. Expected 'minSpeed;maxSpeed;range;weight'.", rawEntry);
                }
            } catch (Exception e) {
                 SoundAttractMod.LOGGER.error("[SoundAttract] Error parsing player speed config entry '{}': {}", rawEntry, e.getMessage());
            }
        }

        bakedSpeedConfigs.sort(Comparator.comparingDouble(c -> c.minSpeed));
        PLAYER_SPEED_CONFIGS_CACHE = bakedSpeedConfigs;
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baked and sorted {} player speed configs.", PLAYER_SPEED_CONFIGS_CACHE.size());

        VOICE_CHAT_ENABLED_CACHE = ModList.get().isLoaded("voicechat") && COMMON.enableVoiceChatIntegration.get();
        VOICE_CHAT_DETECTION_ENABLED_CACHE = VOICE_CHAT_ENABLED_CACHE;
        VOICE_CHAT_WHISPER_RANGE_CACHE = COMMON.voiceChatWhisperRange.get();
        VOICE_CHAT_NORMAL_RANGE_CACHE = COMMON.voiceChatNormalRange.get();
        VOICE_CHAT_WEIGHT_CACHE = COMMON.voiceChatWeight.get();
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baked Voice Chat Config: Enabled={}, WhisperRange={}, NormalRange={}, Weight={}", VOICE_CHAT_ENABLED_CACHE, VOICE_CHAT_WHISPER_RANGE_CACHE, VOICE_CHAT_NORMAL_RANGE_CACHE, VOICE_CHAT_WEIGHT_CACHE);

        TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && COMMON.enableTaczIntegration.get();
        TACZ_RELOAD_RANGE_CACHE = COMMON.taczReloadRange.get();
        TACZ_RELOAD_WEIGHT_CACHE = COMMON.taczReloadWeight.get();
        TACZ_SHOOT_RANGE_CACHE = COMMON.taczShootRange.get();
        TACZ_SHOOT_WEIGHT_CACHE = COMMON.taczShootWeight.get();
        SoundAttractMod.LOGGER.debug("[SoundAttractConfig] Baked TaCz Config: Enabled={}, Reload(R={}, W={}), Shoot(R={}, W={})",
                TACZ_ENABLED_CACHE, TACZ_RELOAD_RANGE_CACHE, TACZ_RELOAD_WEIGHT_CACHE, TACZ_SHOOT_RANGE_CACHE, TACZ_SHOOT_WEIGHT_CACHE);

        SoundAttractMod.LOGGER.info("[SoundAttractConfig] Finished baking configuration.");
    }

}
