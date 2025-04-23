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
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions;
        public final ForgeConfigSpec.DoubleValue soundSwitchRatio;
        public final ForgeConfigSpec.IntValue woolBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue woolBlockWeightReduction;
        public final ForgeConfigSpec.IntValue solidBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue solidBlockWeightReduction;
        public final ForgeConfigSpec.IntValue nonSolidBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue nonSolidBlockWeightReduction;
        public final ForgeConfigSpec.IntValue thinBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue thinBlockWeightReduction;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customThinBlocks;
        public final ForgeConfigSpec.BooleanValue woolMufflingEnabled;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("General");

            attractedEntities = builder
                .comment("List of mobs that will be attracted to sounds. Example: ['minecraft:zombie', 'minecraft:skeleton'].")
                .defineList("attractedEntities", Arrays.asList(
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
                .comment("List of sounds that attract mobs. Format: 'soundId;range;weight'. Example: 'minecraft:block.bell.use;20;5' (range in blocks, weight = priority, higher means more attractive).")
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
                .comment("How long (in ticks) a sound remains interesting. 20 ticks = 1 second. Example: 400 = 20 seconds.")
                .defineInRange("soundLifetimeTicks", 400, 20, 1200);

            scanCooldownTicks = builder
                .comment("How often (in ticks) mobs scan for new sounds. 20 = once per second. Lower = more responsive, higher = less lag.")
                .defineInRange("scanCooldownTicks", 20, 5, 100);

            arrivalDistance = builder
                .comment("The distance (in blocks) at which the mob is considered to have reached the sound. Example: 4.0 = mob stops within 4 blocks of sound.")
                .defineInRange("arrivalDistance", 6.0, 1.0, 16.0);

            mobMoveSpeed = builder
                .comment("The movement speed modifier for mobs moving towards a sound. 1.0 = normal mob speed, 2.0 = twice as fast.")
                .defineInRange("mobMoveSpeed", 1.0, 0.5, 2.0);

            woolBlockRangeReduction = builder
                .comment("How much the range is reduced for each wool block between mob and sound. Example: 30 means wool blocks reduce the sound range by 30 blocks.")
                .defineInRange("woolBlockRangeReduction", 30, 0, 100);

            woolBlockWeightReduction = builder
                .comment("How much the weight is reduced for each wool block between mob and sound. Example: 1 means wool blocks make sounds less attractive.")
                .defineInRange("woolBlockWeightReduction", 3.0, 0.0, 10.0);

            solidBlockRangeReduction = builder
                .comment("How much the range is reduced for each solid block (not wool) between mob and sound.")
                .defineInRange("solidBlockRangeReduction", 20, 0, 100);

            solidBlockWeightReduction = builder
                .comment("How much the weight is reduced for each solid block (not wool) between mob and sound.")
                .defineInRange("solidBlockWeightReduction", 2.0, 0.0, 10.0);

            nonSolidBlockRangeReduction = builder
                .comment("How much the range is reduced for each non-solid block (like glass) between mob and sound.")
                .defineInRange("nonSolidBlockRangeReduction", 10, 0, 50);

            nonSolidBlockWeightReduction = builder
                .comment("How much the weight is reduced for each non-solid block (like glass) between mob and sound.")
                .defineInRange("nonSolidBlockWeightReduction", 1.0, 0.0, 5.0);

            thinBlockRangeReduction = builder
                .comment("How much the range is reduced for each thin block (like panes, fences) between mob and sound.")
                .defineInRange("thinBlockRangeReduction", 5, 0, 25);

            thinBlockWeightReduction = builder
                .comment("How much the weight is reduced for each thin block (like panes, fences) between mob and sound.")
                .defineInRange("thinBlockWeightReduction", 0.5, 0.0, 2.0);

            woolMufflingEnabled = builder
                .comment("Enable wool muffling.")
                .define("woolMufflingEnabled", true);

            builder.pop();

            builder.push("Player Movement Sounds");

            enableSoundBasedDetection = builder
                .comment("Enable sound detection for regular player steps (walking, sprinting, falling). Recommended: true.")
                .define("enableSoundBasedDetection", true);

            enableMovementBasedDetection = builder
                .comment("Enable detection for specific player actions (sneaking, crawling). Recommended: true.")
                .define("enableMovementBasedDetection", true);

            movementCheckFrequencyTicks = builder
                .comment("How often (in ticks) to check player movement state. Lower = more accurate, higher = less lag. Example: 4 = 5 times/sec.")
                .defineInRange("movementCheckFrequencyTicks", 4, 1, 20);

            playerStepSounds = builder
                .comment("List of sound event IDs considered player steps (e.g., walking, running, sneaking). Only change if you add new sounds.")
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
                .comment("List of player speed configs for step sounds. Format: 'minSpeed;maxSpeed;range;weight'. Example: '0.1;1.0;2;1'.")
                .defineList("playerSpeedConfigs", Arrays.asList(
                    "0.1;1.0;2;1",
                    "0.1;1.5;4;1",    
                    "1.51;4.5;8;1",  
                    "4.51;6.0;16;1",  
                    "6.01;10.0;24;1" 
                ), obj -> obj instanceof String);

            builder.pop();

            builder.push("Custom Block Muffling");
            customWoolBlocks = builder
                .defineListAllowEmpty("customWoolBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            customSolidBlocks = builder
                .defineListAllowEmpty("customSolidBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            customNonSolidBlocks = builder
                .defineListAllowEmpty("customNonSolidBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            customThinBlocks = builder
                .defineListAllowEmpty("customThinBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            builder.pop();

            builder.push("Voice Chat Integration (Requires Simple Voice Chat mod)");
            enableVoiceChatIntegration = builder
                .define("enableVoiceChatIntegration", true);
                
            voiceChatWhisperRange = builder
                .comment("The range (in blocks) at which voice chat is considered a whisper.")
                .defineInRange("voiceChatWhisperRange", 4, 0, 64);
                
            voiceChatNormalRange = builder
                .comment("The range (in blocks) at which voice chat is considered normal.")
                .defineInRange("voiceChatNormalRange", 16, 0, 128);
                
            voiceChatWeight = builder
                .comment("The weight of voice chat sounds. Higher means more attractive to mobs.")
                .defineInRange("voiceChatWeight", 10.0, 0.0, 100.0);
                
            builder.pop(); 

            builder.push("Sound Behavior");
            soundSwitchRatio = builder
                .comment("The ratio at which mobs switch between sounds. Higher means more likely to switch.")
                .defineInRange("soundSwitchRatio", 1.1, 1.0, 10.0);
            builder.pop();

            builder.push("TaCz Gun Integration");

            enableTaczIntegration = builder
                .define("enableTaczIntegration", true);

            taczReloadRange = builder
                .comment("The range (in blocks) at which TaCz gun reload sounds are heard.")
                .defineInRange("taczReloadRange", 8.0, 0.0, 256.0);

            taczReloadWeight = builder
                .comment("The weight of TaCz gun reload sounds. Higher means more attractive to mobs.")
                .defineInRange("taczReloadWeight", 3, 0, 100);

            taczShootRange = builder
                .comment("The range (in blocks) at which TaCz gun shoot sounds are heard.")
                .defineInRange("taczShootRange", 128.0, 0.0, 512.0);

            taczShootWeight = builder
                .comment("The weight of TaCz gun shoot sounds. Higher means more attractive to mobs.")
                .defineInRange("taczShootWeight", 10, 0, 100);

            taczGunShootDecibels = builder
                .comment("List of TaCz gun shoot decibel levels. Format: 'soundId;decibel'. Example: 'tacz:ai_awp;166.5'.")
                .defineList("taczGunShootDecibels", Arrays.asList(
                    "tacz:ai_awp;166.5", "tacz:cz75;163", "tacz:glock_17;163", "tacz:p320;163", "tacz:uzi;159.8",
                    "tacz:hk_mp5a5;159.8", "tacz:m95;159.7", "tacz:deagle;159.7", "tacz:ak47;158.9", "tacz:m4a1;158.9",
                    "tacz:m16a1;158.9", "tacz:m16a4;158.9", "tacz:hk416d;158.9", "tacz:aug;158.9", "tacz:mk14;158.9",
                    "tacz:m249;158.9", "tacz:rpk;158.9", "tacz:ump45;157.0", "tacz:vector45;157.0", "tacz:db_short;156.3",
                    "tacz:db_long;156.3", "tacz:aa12;156.3", "tacz:scar_h;156.2", "tacz:hk_g3;156.2", "tacz:sks_tactical;156.0"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 2);
                    if (parts.length != 2) return false;
                    if (ResourceLocation.tryParse(parts[0]) == null) return false;
                    try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                });

            taczAttachmentReductions = builder
                .comment("List of TaCz gun attachment decibel reductions. Format: 'soundId;reduction'. Example: 'tacz:muzzle_silencer_phantom_s1;35'.")
                .defineList("taczAttachmentReductions", Arrays.asList(
                    "tacz:muzzle_silencer_phantom_s1;35", "tacz:muzzle_silencer_vulture;32", "tacz:muzzle_silencer_mirage;30",
                    "tacz:muzzle_silencer_knight_qd;28", "tacz:muzzle_silencer_ursus;25", "tacz:muzzle_silencer_ptilopsis;20",
                    "tacz:deagle_golden_long_barrel;-5"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 2);
                    if (parts.length != 2) return false;
                    if (ResourceLocation.tryParse(parts[0]) == null) return false;
                    try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                });
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
    public static Map<ResourceLocation, Double> TACZ_GUN_SHOOT_DB_CACHE = new HashMap<>();
    public static Map<ResourceLocation, Double> TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new HashMap<>();
    public static double SOUND_SWITCH_RATIO_CACHE = 1.1;
    public static int WOOL_BLOCK_RANGE_REDUCTION_CACHE = 50;
    public static double WOOL_BLOCK_WEIGHT_REDUCTION_CACHE = 5.0;
    public static int SOLID_BLOCK_RANGE_REDUCTION_CACHE = 30;
    public static double SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = 3.0;
    public static int NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE = 10;
    public static double NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = 1.0;
    public static int THIN_BLOCK_RANGE_REDUCTION_CACHE = 5;
    public static double THIN_BLOCK_WEIGHT_REDUCTION_CACHE = 0.5;
    public static List<String> CUSTOM_WOOL_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_SOLID_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_NON_SOLID_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_THIN_BLOCKS_CACHE = java.util.Collections.emptyList();

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
        ATTRACTED_ENTITIES_CACHE = new ArrayList<>(COMMON.attractedEntities.get());

        SOUND_CONFIGS_CACHE.clear();
        Map<SoundEvent, SoundConfig> bakedSounds = new HashMap<>();
        List<? extends String> rawSounds = COMMON.soundConfigsRaw.get(); 

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
                            } 
                         } 
                    } 
                } 
            } catch (Exception e) {
            }
        }
        SOUND_CONFIGS_CACHE = bakedSounds;

        SOUND_BASED_DETECTION_ENABLED_CACHE = COMMON.enableSoundBasedDetection.get();
        MOVEMENT_BASED_DETECTION_ENABLED_CACHE = COMMON.enableMovementBasedDetection.get();
        MOVEMENT_CHECK_FREQUENCY_TICKS_CACHE = COMMON.movementCheckFrequencyTicks.get();

        PLAYER_STEP_SOUNDS_CACHE.clear();
        List<SoundEvent> bakedPlayerSounds = new ArrayList<>();
        for (String soundIdStr : COMMON.playerStepSounds.get()) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(soundIdStr);
                if (rl != null) {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                    if (se != null) {
                        bakedPlayerSounds.add(se);
                    } 
                } 
            } catch (Exception e) {
            }
        }
        PLAYER_STEP_SOUNDS_CACHE = bakedPlayerSounds;

        PLAYER_SPEED_CONFIGS_CACHE.clear();
        List<PlayerSpeedConfig> bakedSpeedConfigs = new ArrayList<>();
        List<? extends String> rawSpeedConfigs = COMMON.playerSpeedConfigsRaw.get(); 

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
                    } 
                } 
            } catch (Exception e) {
            }
        }

        bakedSpeedConfigs.sort(Comparator.comparingDouble(c -> c.minSpeed));
        PLAYER_SPEED_CONFIGS_CACHE = bakedSpeedConfigs;

        VOICE_CHAT_ENABLED_CACHE = ModList.get().isLoaded("voicechat") && COMMON.enableVoiceChatIntegration.get();
        VOICE_CHAT_DETECTION_ENABLED_CACHE = VOICE_CHAT_ENABLED_CACHE;
        VOICE_CHAT_WHISPER_RANGE_CACHE = COMMON.voiceChatWhisperRange.get();
        VOICE_CHAT_NORMAL_RANGE_CACHE = COMMON.voiceChatNormalRange.get();
        VOICE_CHAT_WEIGHT_CACHE = COMMON.voiceChatWeight.get();

        TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && COMMON.enableTaczIntegration.get();
        TACZ_RELOAD_RANGE_CACHE = COMMON.taczReloadRange.get();
        TACZ_RELOAD_WEIGHT_CACHE = COMMON.taczReloadWeight.get();
        TACZ_SHOOT_RANGE_CACHE = COMMON.taczShootRange.get();
        TACZ_SHOOT_WEIGHT_CACHE = COMMON.taczShootWeight.get();

        SOUND_SWITCH_RATIO_CACHE = COMMON.soundSwitchRatio.get();

        WOOL_BLOCK_RANGE_REDUCTION_CACHE = COMMON.woolBlockRangeReduction.get();
        WOOL_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.woolBlockWeightReduction.get();
        SOLID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.solidBlockRangeReduction.get();
        SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.solidBlockWeightReduction.get();
        NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.nonSolidBlockRangeReduction.get();
        NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.nonSolidBlockWeightReduction.get();
        THIN_BLOCK_RANGE_REDUCTION_CACHE = COMMON.thinBlockRangeReduction.get();
        THIN_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.thinBlockWeightReduction.get();

        CUSTOM_WOOL_BLOCKS_CACHE = new ArrayList<>(COMMON.customWoolBlocks.get());
        CUSTOM_SOLID_BLOCKS_CACHE = new ArrayList<>(COMMON.customSolidBlocks.get());
        CUSTOM_NON_SOLID_BLOCKS_CACHE = new ArrayList<>(COMMON.customNonSolidBlocks.get());
        CUSTOM_THIN_BLOCKS_CACHE = new ArrayList<>(COMMON.customThinBlocks.get());

        TACZ_GUN_SHOOT_DB_CACHE.clear();
        List<? extends String> rawShoot = COMMON.taczGunShootDecibels.get();
        for (String raw : rawShoot) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null && db >= 0) TACZ_GUN_SHOOT_DB_CACHE.put(rl, db);
            } catch (Exception e) {
            }
        }

        TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
        List<? extends String> rawAtt = COMMON.taczAttachmentReductions.get();
        for (String raw : rawAtt) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null) TACZ_ATTACHMENT_REDUCTION_DB_CACHE.put(rl, db);
            } catch (Exception e) {
            }
        }
    }

}
