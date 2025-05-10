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
import java.util.Collections; 
import java.util.function.Predicate; 
import java.util.function.Supplier; 

public class SoundAttractConfig {

    public static ForgeConfigSpec.IntValue stealthCheckInterval;
    public static ForgeConfigSpec.IntValue detectionLightLowThreshold;
    public static ForgeConfigSpec.DoubleValue detectionLightLowMultiplier;
    public static ForgeConfigSpec.IntValue detectionLightMidThreshold;
    public static ForgeConfigSpec.DoubleValue detectionLightMidMultiplier;
    public static ForgeConfigSpec.DoubleValue detectionNightMultiplier;
    public static ForgeConfigSpec.BooleanValue camouflagePartialMatching;
    public static ForgeConfigSpec.DoubleValue camouflageArmorPieceWeight;
    public static ForgeConfigSpec.DoubleValue camouflageColorSimilarityWeight;
    public static ForgeConfigSpec.IntValue camouflageColorSimilarityThreshold;
    public static ForgeConfigSpec.DoubleValue camouflageBlockMatchWeight;
    public static ForgeConfigSpec.BooleanValue camouflageDistanceScaling;
    public static ForgeConfigSpec.DoubleValue camouflageDistanceMax;
    public static ForgeConfigSpec.DoubleValue camouflageDistanceMinEffectiveness;
    public static ForgeConfigSpec.BooleanValue camouflageMovementPenalty;
    public static ForgeConfigSpec.DoubleValue camouflageSprintingPenalty;
    public static ForgeConfigSpec.DoubleValue camouflageWalkingPenalty;


    public static class Common {
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> attractedEntities;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> nonPlayerSoundIdList; 
        public final ForgeConfigSpec.BooleanValue debugLogging;
        public final ForgeConfigSpec.BooleanValue edgeMobSmartBehavior;
        public final ForgeConfigSpec.IntValue soundLifetimeTicks;
        public final ForgeConfigSpec.IntValue scanCooldownTicks;
        public final ForgeConfigSpec.DoubleValue minTpsForScanCooldown;
        public final ForgeConfigSpec.DoubleValue maxTpsForScanCooldown;
        public final ForgeConfigSpec.DoubleValue arrivalDistance;
        public final ForgeConfigSpec.DoubleValue mobMoveSpeed;
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
        public final ForgeConfigSpec.DoubleValue leaderGroupRadius;
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
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customLiquidBlocks;
        public final ForgeConfigSpec.BooleanValue woolMufflingEnabled;
        public final ForgeConfigSpec.BooleanValue solidMufflingEnabled;
        public final ForgeConfigSpec.BooleanValue nonSolidMufflingEnabled;
        public final ForgeConfigSpec.BooleanValue thinMufflingEnabled;
        public final ForgeConfigSpec.BooleanValue liquidMufflingEnabled;
        public final ForgeConfigSpec.IntValue liquidBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue liquidBlockWeightReduction;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> parcoolAnimatorSounds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> parcoolAnimatorSoundConfigs;
        public final ForgeConfigSpec.DoubleValue baseDetectionRange;
        public final ForgeConfigSpec.DoubleValue sneakDetectionRange;
        public final ForgeConfigSpec.DoubleValue crawlDetectionRange;
        public final ForgeConfigSpec.DoubleValue sneakDetectionRangeCamouflage;
        public final ForgeConfigSpec.DoubleValue crawlDetectionRangeCamouflage;
        public final ForgeConfigSpec.DoubleValue standingDetectionRange;
        public final ForgeConfigSpec.DoubleValue standingDetectionRangeCamouflage;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> camouflageSets;
        public final ForgeConfigSpec.DoubleValue groupDistance;
        public final ForgeConfigSpec.IntValue maxLeaders;
        public final ForgeConfigSpec.IntValue maxGroupSize;
        public final ForgeConfigSpec.DoubleValue leaderSpacingMultiplier;
        public final ForgeConfigSpec.IntValue numEdgeSectors;
        public final ForgeConfigSpec.IntValue mufflingAreaRadius;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> soundIdWhitelist;

        Common(ForgeConfigSpec.Builder builder) {

            builder.push("General");
            debugLogging = builder
                    .comment("Enable aggresive debug logging for Sound Attract mod")
                    .define("debugLogging", false);
            soundLifetimeTicks = builder.comment("Sound lifetime in ticks").defineInRange("soundLifetimeTicks", 200, 1, 1200);
            scanCooldownTicks = builder.comment("Mob scan cooldown in ticks").defineInRange("scanCooldownTicks", 15, 1, 100);
            minTpsForScanCooldown = builder.comment("Min TPS for scan cooldown").defineInRange("minTpsForScanCooldown", 10.0, 1.0, 20.0);
            maxTpsForScanCooldown = builder.comment("Max TPS for scan cooldown").defineInRange("maxTpsForScanCooldown", 20.0, 1.0, 20.0);
            arrivalDistance = builder.comment("Arrival distance for sound").defineInRange("arrivalDistance", 6.0, 0.1, 10.0);
            mobMoveSpeed = builder.comment("Mob movement speed").defineInRange("mobMoveSpeed", 1.0, 0.01, 10.0);
            soundSwitchRatio = builder.comment("Sound switch ratio, the lower the easier for the mob to switch target").defineInRange("soundSwitchRatio", 0.3, 0.0, 1.0);
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
                    "minecraft:wither_skeleton", "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager"
                ), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);
            edgeMobSmartBehavior = builder
                    .comment("Experimental Feature, may cause issues.")
                    .define("edgeMobSmartBehavior", false);
            groupDistance = builder.comment("Max distance for mobs to be considered in a group").defineInRange("groupDistance", 128.0, 1.0, 128.0);
            maxLeaders = builder.comment("Maximum number of group leaders").defineInRange("maxLeaders", 16, 1, 1000);
            maxGroupSize = builder.comment("Maximum number of mobs in a group").defineInRange("maxGroupSize", 128, 1, 128);
            leaderSpacingMultiplier = builder.comment("Multiplier for leader spacing").defineInRange("leaderSpacingMultiplier", 2.0, 1.0, 10.0);
            builder.push("Leader Group Radius");
            leaderGroupRadius = builder.comment("This has no effect. Default: 32.0").defineInRange("leaderGroupRadius", 32.0, 1.0, 32.0);
            builder.pop();
            numEdgeSectors = builder.comment("Number of edge sectors for edge mob selection").defineInRange("numEdgeSectors", 6, 2, 12);
            builder.pop();

            builder.push("Sound");
            nonPlayerSoundIdList = builder.comment("List of non-player sound IDs that mobs can be attracted to. Format: 'soundId;range;weight'. Example: 'minecraft:block.lever.click;5;3'")
                .defineList("nonPlayerSoundIdList", Arrays.asList(
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
                    "cgm:entity.stun_grenade.ring;104;10"
                ), obj -> obj instanceof String && ((String) obj).split(";").length == 3);
            builder.pop();

            builder.push("SoundIdWhitelist");
            soundIdWhitelist = builder.comment("Whitelist of sound IDs to process (all others will be ignored for performance). Example: 'minecraft:block.note_block.bass'")
                .defineListAllowEmpty("soundIdWhitelist", Arrays.asList(
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
                    "cgm:entity.stun_grenade.ring"
                ), o -> o instanceof String);
            builder.pop();

            builder.push("Block");
            mufflingAreaRadius = builder.comment("Radius (in blocks) around the ray to check for muffling blocks. 0 = only the direct ray, 1 = 3x3 area, 2 = 5x5, etc.").defineInRange("mufflingAreaRadius", 0, 0, 4);
            woolMufflingEnabled = builder.comment("Enable wool muffling").define("woolMufflingEnabled", true);
            customWoolBlocks = builder.comment("Custom wool blocks").defineListAllowEmpty("customWoolBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            woolBlockRangeReduction = builder.comment("Wool block range reduction (blocks)").defineInRange("woolBlockRangeReduction", 8, 0, 32);
            woolBlockWeightReduction = builder.comment("Wool block weight reduction (absolute deduction per block, e.g., 0.5 means -0.5 per block)").defineInRange("woolBlockWeightReduction", 0.8, 0.0, 10.0);
            solidMufflingEnabled = builder.comment("Enable solid block muffling").define("solidMufflingEnabled", true);
            customSolidBlocks = builder.comment("Custom solid blocks").defineListAllowEmpty("customSolidBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            solidBlockRangeReduction = builder.comment("Solid block range reduction (blocks)").defineInRange("solidBlockRangeReduction", 6, 0, 32);
            solidBlockWeightReduction = builder.comment("Solid block weight reduction (absolute deduction per block, e.g., 1.0 means -1.0 per block)").defineInRange("solidBlockWeightReduction", 0.6, 0.0, 10.0);
            nonSolidMufflingEnabled = builder.comment("Enable non-solid block muffling").define("nonSolidMufflingEnabled", true);
            customNonSolidBlocks = builder.comment("Custom non-solid blocks").defineListAllowEmpty("customNonSolidBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            nonSolidBlockRangeReduction = builder.comment("Non-solid block range reduction (blocks)").defineInRange("nonSolidBlockRangeReduction", 5, 0, 32);
            nonSolidBlockWeightReduction = builder.comment("Non-solid block weight reduction (absolute deduction per block, e.g., 0.5 means -0.5 per block)").defineInRange("nonSolidBlockWeightReduction", 0.5, 0.0, 10.0);
            thinMufflingEnabled = builder.comment("Enable thin block muffling").define("thinMufflingEnabled", true);
            customThinBlocks = builder.comment("Custom thin blocks").defineListAllowEmpty("customThinBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            thinBlockRangeReduction = builder.comment("Thin block range reduction (blocks)").defineInRange("thinBlockRangeReduction", 2, 0, 32);
            thinBlockWeightReduction = builder.comment("Thin block weight reduction (absolute deduction per block, e.g., 0.2 means -0.2 per block)").defineInRange("thinBlockWeightReduction", 0.2, 0.0, 10.0);
            liquidMufflingEnabled = builder.comment("Enable liquid block muffling").define("liquidMufflingEnabled", true);
            customLiquidBlocks = builder.comment("Custom liquid blocks (resource locations, e.g. 'minecraft:lava', 'modid:custom_fluid')").defineListAllowEmpty("customLiquidBlocks", java.util.Collections::emptyList, o -> o instanceof String);
            liquidBlockRangeReduction = builder.comment("Liquid block range reduction (blocks)").defineInRange("liquidBlockRangeReduction", 4, 0, 32);
            liquidBlockWeightReduction = builder.comment("Liquid block weight reduction (absolute deduction per block, e.g., 1.0 means -1.0 per block)").defineInRange("liquidBlockWeightReduction", 0.4, 0.0, 10.0);
            builder.pop();

            builder.push("Detection");
            baseDetectionRange = builder.comment("Base detection range").defineInRange("baseDetectionRange", 16.0, 1.0, 128.0);
            sneakDetectionRange = builder.comment("Sneak detection range").defineInRange("sneakDetectionRange", 8.0, 0.0, 128.0);
            crawlDetectionRange = builder.comment("Crawl detection range").defineInRange("crawlDetectionRange", 4.0, 0.0, 128.0);
            sneakDetectionRangeCamouflage = builder.comment("Sneak detection range with camouflage").defineInRange("sneakDetectionRangeCamouflage", 4.0, 0.0, 128.0);
            crawlDetectionRangeCamouflage = builder.comment("Crawl detection range with camouflage").defineInRange("crawlDetectionRangeCamouflage", 2.0, 0.0, 128.0);
            standingDetectionRange = builder.comment("Standing detection range").defineInRange("standingDetectionRange", 32.0, 0.0, 128.0);
            standingDetectionRangeCamouflage = builder.comment("Standing detection range with camouflage").defineInRange("standingDetectionRangeCamouflage", 16.0, 0.0, 128.0);

            stealthCheckInterval = builder.comment("Interval (in ticks) between stealth detection scans.").defineInRange("stealthCheckInterval", 10, 1, 100);
            detectionLightLowThreshold = builder.comment("Threshold for low light level (inclusive). Below or equal to this, use detectionLightLowMultiplier.").defineInRange("detectionLightLowThreshold", 7, 0, 15);
            detectionLightLowMultiplier = builder.comment("Detection range multiplier for low light.").defineInRange("detectionLightLowMultiplier", 0.7, 0.0, 1.0);
            detectionLightMidThreshold = builder.comment("Threshold for mid light level (inclusive). Below or equal to this, use detectionLightMidMultiplier.").defineInRange("detectionLightMidThreshold", 12, 0, 15);
            detectionLightMidMultiplier = builder.comment("Detection range multiplier for mid light.").defineInRange("detectionLightMidMultiplier", 0.85, 0.0, 1.0);
            detectionNightMultiplier = builder.comment("Detection range multiplier at night.").defineInRange("detectionNightMultiplier", 0.45, 0.0, 1.0);
            camouflagePartialMatching = builder.comment("If true, partial matching of camouflage armor pieces is allowed.").define("camouflagePartialMatching", true);
            camouflageArmorPieceWeight = builder.comment("Weight for each matching camouflage armor piece (0-1).").defineInRange("camouflageArmorPieceWeight", 0.25, 0.0, 1.0);
            camouflageColorSimilarityWeight = builder.comment("Weight for color similarity in camouflage calculation (0-1).").defineInRange("camouflageColorSimilarityWeight", 0.5, 0.0, 1.0);
            camouflageColorSimilarityThreshold = builder.comment("Threshold for color similarity (lower = more strict match, up to 255).").defineInRange("camouflageColorSimilarityThreshold", 48, 0, 255);
            camouflageBlockMatchWeight = builder.comment("Weight for matching adjacent blocks in camouflage calculation (0-1).").defineInRange("camouflageBlockMatchWeight", 0.15, 0.0, 1.0);
            camouflageDistanceScaling = builder.comment("If true, camouflage effectiveness scales with distance.").define("camouflageDistanceScaling", true);
            camouflageDistanceMax = builder.comment("Maximum distance for camouflage scaling.").defineInRange("camouflageDistanceMax", 16.0, 1.0, 128.0);
            camouflageDistanceMinEffectiveness = builder.comment("Minimum camouflage effectiveness at max distance (0-1).").defineInRange("camouflageDistanceMinEffectiveness", 0.3, 0.0, 1.0);
            camouflageMovementPenalty = builder.comment("If true, movement reduces camouflage effectiveness.").define("camouflageMovementPenalty", true);
            camouflageSprintingPenalty = builder.comment("Penalty multiplier for sprinting (0-1, lower = more penalty).").defineInRange("camouflageSprintingPenalty", 0.4, 0.0, 1.0);
            camouflageWalkingPenalty = builder.comment("Penalty multiplier for walking (0-1, lower = more penalty).").defineInRange("camouflageWalkingPenalty", 0.15, 0.0, 1.0);

            camouflageSets = builder.comment("Camouflage sets. Add more by appending new entries to the list. Each entry is: colorHex;helmetId;chestplateId;leggingsId;bootsId;[optional block ids...]. Use full item IDs for vanilla/modded armor and blocks.\nExample: '232323;minecraft:netherite_helmet;minecraft:netherite_chestplate;minecraft:netherite_leggings;minecraft:netherite_boots;minecraft:obsidian;minecraft:blackstone' adds Netherite armor camo for obsidian/blackstone. See documentation for details.")
                .defineListAllowEmpty("camouflageSets", Arrays.asList(
                    // White
                    "F9FFFE;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:snow_block;minecraft:white_wool;minecraft:quartz_block;minecraft:calcite;minecraft:diorite;minecraft:bone_block;minecraft:powder_snow;minecraft:wool;minecraft:white_concrete;minecraft:white_terracotta;minecraft:white_glazed_terracotta",
                    // Orange
                    "F9801D;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:orange_wool;minecraft:orange_terracotta;minecraft:acacia_planks;minecraft:honey_block;minecraft:pumpkin;minecraft:carved_pumpkin;minecraft:orange_concrete;minecraft:orange_glazed_terracotta;minecraft:mangrove_planks",
                    // Magenta
                    "C74EBD;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:magenta_wool;minecraft:magenta_terracotta;minecraft:purpur_block;minecraft:amethyst_block;minecraft:magenta_concrete;minecraft:magenta_glazed_terracotta;minecraft:shulker_box",
                    // Light Blue
                    "3AB3DA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:light_blue_wool;minecraft:light_blue_terracotta;minecraft:packed_ice;minecraft:ice;minecraft:blue_ice;minecraft:light_blue_concrete;minecraft:light_blue_glazed_terracotta;minecraft:prismarine;minecraft:prismarine_bricks",
                    // Yellow
                    "FED83D;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:yellow_wool;minecraft:yellow_terracotta;minecraft:sandstone;minecraft:smooth_sandstone;minecraft:end_stone;minecraft:sponge;minecraft:hay_block;minecraft:yellow_concrete;minecraft:yellow_glazed_terracotta",
                    // Lime
                    "80C71F;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:lime_wool;minecraft:lime_terracotta;minecraft:melon;minecraft:slime_block;minecraft:lime_concrete;minecraft:lime_glazed_terracotta;minecraft:leaves;minecraft:moss_block",
                    // Pink
                    "F38BAA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:pink_wool;minecraft:pink_terracotta;minecraft:brain_coral_block;minecraft:pink_concrete;minecraft:pink_glazed_terracotta;minecraft:peony;minecraft:pink_petals",
                    // Gray
                    "474F52;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:gray_wool;minecraft:gray_terracotta;minecraft:polished_andesite;minecraft:stone;minecraft:cobblestone;minecraft:gravel;minecraft:deepslate;minecraft:gray_concrete;minecraft:gray_glazed_terracotta",
                    // Light Gray
                    "9D9D97;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:light_gray_wool;minecraft:light_gray_terracotta;minecraft:stone;minecraft:andesite;minecraft:calcite;minecraft:diorite;minecraft:light_gray_concrete;minecraft:light_gray_glazed_terracotta;minecraft:oxidized_copper",
                    // Cyan
                    "169C9C;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:cyan_wool;minecraft:cyan_terracotta;minecraft:prismarine;minecraft:warped_planks;minecraft:cyan_concrete;minecraft:cyan_glazed_terracotta;minecraft:oxidized_cut_copper",
                    // Purple
                    "8932B8;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:purple_wool;minecraft:purple_terracotta;minecraft:obsidian;minecraft:purpur_block;minecraft:crying_obsidian;minecraft:purple_concrete;minecraft:purple_glazed_terracotta;minecraft:chorus_flower",
                    // Blue
                    "3C44AA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:blue_wool;minecraft:blue_terracotta;minecraft:lapis_block;minecraft:blue_ice;minecraft:blue_concrete;minecraft:blue_glazed_terracotta;minecraft:warped_nylium;minecraft:soul_fire",
                    // Brown
                    "835432;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:brown_wool;minecraft:brown_terracotta;minecraft:dirt;minecraft:podzol;minecraft:coarse_dirt;minecraft:mud;minecraft:brown_concrete;minecraft:brown_glazed_terracotta;minecraft:rooted_dirt",
                    // Green
                    "5E7C16;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:green_wool;minecraft:green_terracotta;minecraft:moss_block;minecraft:grass_block;minecraft:leaves;minecraft:vine;minecraft:green_concrete;minecraft:green_glazed_terracotta;minecraft:cactus;minecraft:bamboo",
                    // Red
                    "B02E26;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:red_wool;minecraft:red_terracotta;minecraft:netherrack;minecraft:red_sand;minecraft:red_concrete;minecraft:red_glazed_terracotta;minecraft:crimson_nylium;minecraft:nether_wart_block;minecraft:cherry_leaves",
                    // Black
                    "1D1D21;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots;minecraft:black_wool;minecraft:black_terracotta;minecraft:coal_block;minecraft:deepslate;minecraft:black_concrete;minecraft:black_glazed_terracotta;minecraft:obsidian;minecraft:basalt;minecraft:sculk"
                ), o -> o instanceof String);
            builder.pop();

            builder.push("Player Action Sound");
            parcoolAnimatorSounds = builder.comment("List of Parcool animator sound configs. Format: 'AnimatorClass;soundId;range;weight;volume;pitch'. Example: 'VaultAnimator;parcool:grabbing;10;1.0;1.25;0.6'")
                .defineList("parcoolAnimatorSounds", Arrays.asList(
                    // Vault
                    "VaultAnimator;parcool:grabbing;7;0.7;1.25;0.6",
                    // Vertical Wall Run
                    "VerticalWallRunAnimator;parcool:wallrun_and_running;7;0.7;1.5;0.6",
                    // Horizontal Wall Run
                    "HorizontalWallRunAnimator;parcool:wallrun_and_running;7;0.7;1.5;0.7",
                    // CatLeap
                    "CatLeapAnimator;parcool:jumping;7;0.7;0.4;0.7",
                    // ChargeJump
                    "ChargeJumpAnimator;parcool:jumping;7;0.7;0.35;0.6",
                    // ClingToCliff (grab)
                    "ClingToCliffAnimator;parcool:grabbing;7;0.7;1.0;0.6",
                    // ClingToCliff (jump)
                    "ClingToCliffJumpAnimator;parcool:jumping;7;0.7;0.4;0.6",
                    // HangDown (grab)
                    "HangDownAnimator;parcool:grabbing;7;0.7;0.9;0.6",
                    // HangDown (jump)
                    "HangDownJumpAnimator;parcool:jumping;7;0.7;0.4;0.6",
                    // Slide (multiple variants)
                    "SlideAnimator;parcool:sliding_1;7;0.7;0.5;0.6",
                    "SlideAnimator2;parcool:sliding_2;7;0.7;0.5;0.6",
                    "SlideAnimator3;parcool:sliding_3;7;0.7;0.5;0.6",
                    // Dodge
                    "DodgeAnimator;parcool:roll_and_dodge;7;0.7;0.5;1.0",
                    // Tap (breakfall.roll, breakfall.tap)
                    "TapAnimator;parcool:landing;7;0.7;0.4;0.7",
                    // Breakfall.just
                    "BreakfallJustAnimator;minecraft:random/anvil_land;7;0.7;0.75;2.0",
                    // Roll (breakfall.roll)
                    "RollAnimator;parcool:landing;7;0.7;0.4;0.7",
                    // FastRun
                    "FastRunningAnimator;parcool:wallrun_and_running;7;0.7;1.5;0.6",
                    // FastSwim
                    "FastSwimAnimator;parcool:jumping;7;0.7;0.4;0.7",
                    // Flipping
                    "FlippingAnimator;parcool:jumping;7;0.7;0.4;0.7",
                    // HideInBlock
                    "HideInBlockAnimator;parcool:jumping;7;0.7;0.4;0.7",
                    // Crawl
                    "CrawlAnimator;parcool:sliding_1;7;0.7;0.5;0.6",
                    // Dive
                    "DiveAnimator;parcool:jumping;7;0.7;0.4;0.7",
                    // RideZipline
                    "RideZiplineAnimator;entity/leashknot/place1;7;0.7;1.0;1.0",
                    // WallJump
                    "WallJumpAnimator;parcool:jumping;7;0.7;0.4;0.7",
                    // WallSlide
                    "WallSlideAnimator;parcool:sliding_2;7;0.7;0.5;0.6",
                    // VANILLA ACTIONS
                    "VanillaSprint;minecraft:entity.player.sprint;10;1.2;1.0;1.0",
                    "VanillaJump;minecraft:entity.player.jump;7;0.7;0.8;1.0",
                    "VanillaSneak;minecraft:entity.player.sneak;3;0.2;0.4;1.0",
                    "VanillaCrawl;minecraft:block.wool.step;2;0.1;0.2;1.0"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 6);
                    if (parts.length != 6) return false;
                    try {
                        Double.parseDouble(parts[2]); // range
                        Double.parseDouble(parts[3]); // weight
                        Double.parseDouble(parts[4]); // volume
                        Double.parseDouble(parts[5]); // pitch
                        return true;
                    } catch (NumberFormatException e) { return false; }
                });
            parcoolAnimatorSoundConfigs = builder.comment("List of Parcool animator sound configs. Format: 'animatorClass;soundId;range;weight;volume;pitch'. Example: 'VaultAnimator;parcool:grabbing;10;1.0;1.25;0.6'")
                .defineList("parcoolAnimatorSoundConfigs", Arrays.asList(), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 6);
                    if (parts.length != 6) return false;
                    try {
                        Double.parseDouble(parts[2]); // range
                        Double.parseDouble(parts[3]); // weight
                        Double.parseDouble(parts[4]); // volume
                        Double.parseDouble(parts[5]); // pitch
                        return true;
                    } catch (NumberFormatException e) { return false; }
                });
            builder.pop();

            builder.push("Simple VC");
            enableVoiceChatIntegration = builder.comment("Enable voice chat integration").define("enableVoiceChatIntegration", true);
            voiceChatWhisperRange = builder.comment("Voice chat whisper range").defineInRange("voiceChatWhisperRange", 8, 1, 64);
            voiceChatNormalRange = builder.comment("Voice chat normal range").defineInRange("voiceChatNormalRange", 24, 1, 128);
            voiceChatWeight = builder.comment("Voice chat weight").defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
            builder.pop();

            builder.push("Tacz");
            enableTaczIntegration = builder.comment("Enable Tacz gun integration").define("enableTaczIntegration", true);
            taczReloadRange = builder.comment("Tacz reload sound range (fallback, calculated as shootDb/20.0 for known guns)").defineInRange("taczReloadRange", 9, 1.0, 128.0);
            taczReloadWeight = builder.comment("Tacz reload sound weight (fallback, calculated as (shootDb/10.0)/2.0 for known guns)").defineInRange("taczReloadWeight", 9, 0, 10);
            taczShootRange = builder.comment("Tacz shoot sound range (fallback, calculated as db for known guns)").defineInRange("taczShootRange", 128.0, 1.0, 128.0);
            taczShootWeight = builder.comment("Tacz shoot sound weight (fallback, calculated as db/10.0 for known guns)").defineInRange("taczShootWeight", 10, 0, 10);
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
                    "suffuse:aks74u;157.0", "suffuse:qbz951s;160.0", "suffuse:qbz192;159.0",
                    "suffuse:an94;161.0", "tacz:sks_tactical;159.0", "tacz:ak47;159.0",
                    "tacz:type_81;158.0", "tacz:qbz_95;160.0", "tacz:hk416d;161.0",
                    "tacz:m4a1;159.0", "tacz:m16a1;159.0", "tacz:hk_g3;161.0",
                    "tacz:m16a4;159.0", "tacz:mk14;162.0", "tacz:scar_l;161.0", 
                    "tacz:scar_h;162.0", "tacz:aug;160.0", "tacz:db_short;165.0",
                    "tacz:db_long;166.0", "tacz:m870;165.0", "tacz:aa12;161.0",
                    "tacz:ump45;158.0", "tacz:hk_mp5a5;158.0", "suffuse:ump45;158.0",
                    "tacz:uzi;157.0", "suffuse:pp19;157.0", "tacz:vector45;158.0",
                    "tacz:p90;156.0", "tacz:rpg7;180.0", "tacz:m320;172.0",
                    "suffuse:m79;172.0", "suffuse:pkp;165.0", "tacz:m249;165.0",
                    "tacz:rpk;164.0"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 2);
                    if (parts.length != 2) return false;
                    try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                });
            taczAttachmentReductions = builder.comment("Tacz attachment sound reduction. Format: 'modid:item;reduction'. Example: 'tacz:suppressor;15.0'")
                .defineList("taczAttachmentReductions", Arrays.asList(
                    "tacz:muzzle_brake_cthulhu;-3.0",         
                    "tacz:muzzle_brake_pioneer;-3.0",        
                    "tacz:muzzle_brake_cyclone_d2;-3.0",     
                    "tacz:muzzle_brake_trex;-5.0",           
                    "tacz:muzzle_silencer_mirage;30.0",      
                    "tacz:muzzle_silencer_vulture;30.0",     
                    "tacz:muzzle_silencer_knight_qd;30.0",   
                    "tacz:muzzle_silencer_ursus;30.0",       
                    "tacz:muzzle_silencer_ptilopsis;30.0",   
                    "tacz:muzzle_silencer_phantom_s1;30.0",  
                    "tacz:muzzle_compensator_trident;-2.0",  
                    "tacz:deagle_golden_long_barrel;-1.0" 
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 2);
                    if (parts.length != 2) return false;
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
    public static double GROUP_DISTANCE_CACHE = 3.0;
    public static double LEADER_GROUP_RADIUS_CACHE = 5.0;
    public static int WOOL_BLOCK_RANGE_REDUCTION_CACHE = 50;
    public static double WOOL_BLOCK_WEIGHT_REDUCTION_CACHE = 5.0;
    public static int SOLID_BLOCK_RANGE_REDUCTION_CACHE = 30;
    public static double SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = 3.0;
    public static int NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE = 10;
    public static double NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = 1.0;
    public static int THIN_BLOCK_RANGE_REDUCTION_CACHE = 5;
    public static double THIN_BLOCK_WEIGHT_REDUCTION_CACHE = 0.5;
    public static int LIQUID_BLOCK_RANGE_REDUCTION_CACHE = 8;
    public static double LIQUID_BLOCK_WEIGHT_REDUCTION_CACHE = 1.0;
    public static List<String> CUSTOM_WOOL_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_SOLID_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_NON_SOLID_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_THIN_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static List<String> CUSTOM_LIQUID_BLOCKS_CACHE = java.util.Collections.emptyList();
    public static Map<String, ParcoolAnimatorSoundConfig> PARCOOL_ANIMATOR_SOUND_CONFIGS = new HashMap<>();
    public static List<ParcoolAnimatorSoundConfig> PARCOOL_ANIMATOR_SOUNDS_CACHE = new ArrayList<>();
    public static List<ParcoolAnimatorSoundConfig> PARCOOL_ANIMATOR_CONFIGS_CACHE = new ArrayList<>();
    public static int MAX_LEADERS_CACHE = 10;
    public static int MAX_GROUP_SIZE_CACHE = 16;
    public static double LEADER_SPACING_MULTIPLIER_CACHE = 3.0;
    public static int NUM_EDGE_SECTORS_CACHE = 4;
    public static int MUFFLING_AREA_RADIUS_CACHE = 1;
    public static List<String> SOUND_ID_WHITELIST_CACHE = java.util.Collections.emptyList();
    public static List<String> NON_PLAYER_SOUND_ID_LIST_CACHE = java.util.Collections.emptyList();

    public static class SoundConfig {
        public final int range;
        public final double weight;
        public SoundConfig(int range, double weight) { this.range = range; this.weight = weight; }
    }
    public static class ParcoolAnimatorSoundConfig {
        public final String animatorClass;
        public final String soundId;
        public final int range;
        public final double weight;
        public final float volume;
        public final float pitch;
        public ParcoolAnimatorSoundConfig(String animatorClass, String soundId, int range, double weight, float volume, float pitch) {
            this.animatorClass = animatorClass;
            this.soundId = soundId;
            this.range = range;
            this.weight = weight;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    // Utility to get sound config (range/weight) by soundId string
    public static SoundConfig getSoundConfigForId(String soundId) {
        if (soundId == null) return null;
        for (Map.Entry<SoundEvent, SoundConfig> entry : SOUND_CONFIGS_CACHE.entrySet()) {
            if (entry.getKey() != null && soundId.equals(entry.getKey().getLocation().toString())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void bakeConfig() {
        ATTRACTED_ENTITIES_CACHE = new ArrayList<>(COMMON.attractedEntities.get());

        SOUND_CONFIGS_CACHE.clear();
        Map<SoundEvent, SoundConfig> bakedSounds = new HashMap<>();
        List<? extends String> rawSounds = COMMON.nonPlayerSoundIdList.get();
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
                            bakedSounds.put(se, new SoundConfig(range, weight));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        SOUND_CONFIGS_CACHE.putAll(bakedSounds);

        // --- Parcool Animator Sound Configs ---
        // If parcoolAnimatorSoundConfigs is empty, populate it from parcoolAnimatorSounds
        if (COMMON.parcoolAnimatorSoundConfigs.get().isEmpty()) {
            List<? extends String> defaults = COMMON.parcoolAnimatorSounds.get();
            // This will only update the in-memory config, not the config file
            COMMON.parcoolAnimatorSoundConfigs.set(defaults);
        }
        PARCOOL_ANIMATOR_CONFIGS_CACHE.clear();
        for (String raw : COMMON.parcoolAnimatorSoundConfigs.get()) {
            try {
                // Format: animatorClass;soundId;range;weight;volume;pitch
                String[] parts = raw.split(";");
                if (parts.length >= 6) {
                    String animatorClass = parts[0];
                    String soundId = parts[1];
                    int range = Integer.parseInt(parts[2]);
                    double weight = Double.parseDouble(parts[3]);
                    float volume = Float.parseFloat(parts[4]);
                    float pitch = Float.parseFloat(parts[5]);
                    PARCOOL_ANIMATOR_CONFIGS_CACHE.add(new ParcoolAnimatorSoundConfig(animatorClass, soundId, range, weight, volume, pitch));
                }
            } catch (Exception ignored) {}
        }

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
        GROUP_DISTANCE_CACHE = COMMON.groupDistance.get();
        LEADER_GROUP_RADIUS_CACHE = COMMON.leaderGroupRadius.get();

        WOOL_BLOCK_RANGE_REDUCTION_CACHE = COMMON.woolBlockRangeReduction.get();
        WOOL_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.woolBlockWeightReduction.get();
        SOLID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.solidBlockRangeReduction.get();
        SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.solidBlockWeightReduction.get();
        NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.nonSolidBlockRangeReduction.get();
        NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.nonSolidBlockWeightReduction.get();
        THIN_BLOCK_RANGE_REDUCTION_CACHE = COMMON.thinBlockRangeReduction.get();
        THIN_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.thinBlockWeightReduction.get();
        LIQUID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.liquidBlockRangeReduction.get();
        LIQUID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.liquidBlockWeightReduction.get();

        CUSTOM_WOOL_BLOCKS_CACHE = new ArrayList<>(COMMON.customWoolBlocks.get());
        CUSTOM_SOLID_BLOCKS_CACHE = new ArrayList<>(COMMON.customSolidBlocks.get());
        CUSTOM_NON_SOLID_BLOCKS_CACHE = new ArrayList<>(COMMON.customNonSolidBlocks.get());
        CUSTOM_THIN_BLOCKS_CACHE = new ArrayList<>(COMMON.customThinBlocks.get());
        CUSTOM_LIQUID_BLOCKS_CACHE = new ArrayList<>(COMMON.customLiquidBlocks.get());

        PARCOOL_ANIMATOR_SOUND_CONFIGS.clear();
        PARCOOL_ANIMATOR_SOUNDS_CACHE.clear();
        List<? extends String> rawAnimatorSounds = COMMON.parcoolAnimatorSounds.get();
        for (String raw : rawAnimatorSounds) {
            try {
                String[] parts = raw.split(";", 6);
                if (parts.length == 6) {
                    String animatorClass = parts[0];
                    String soundId = parts[1];
                    int range = Integer.parseInt(parts[2]);
                    double weight = Double.parseDouble(parts[3]);
                    float volume = Float.parseFloat(parts[4]);
                    float pitch = Float.parseFloat(parts[5]);
                    ParcoolAnimatorSoundConfig config = new ParcoolAnimatorSoundConfig(animatorClass, soundId, range, weight, volume, pitch);
                    PARCOOL_ANIMATOR_SOUND_CONFIGS.put(animatorClass, config);
                    PARCOOL_ANIMATOR_SOUNDS_CACHE.add(config);
                }
            } catch (Exception e) {}
        }

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

        SOUND_ID_WHITELIST_CACHE = new ArrayList<>(COMMON.soundIdWhitelist.get());
        NON_PLAYER_SOUND_ID_LIST_CACHE = new ArrayList<>(COMMON.nonPlayerSoundIdList.get());

        MAX_LEADERS_CACHE = COMMON.maxLeaders.get();
        MAX_GROUP_SIZE_CACHE = COMMON.maxGroupSize.get();
        LEADER_SPACING_MULTIPLIER_CACHE = COMMON.leaderSpacingMultiplier.get();
        NUM_EDGE_SECTORS_CACHE = COMMON.numEdgeSectors.get();
        MUFFLING_AREA_RADIUS_CACHE = COMMON.mufflingAreaRadius.get();
    }

    public static final ForgeConfigSpec.BooleanValue debugLogging = COMMON.debugLogging;
    public static final ForgeConfigSpec.BooleanValue edgeMobSmartBehavior = COMMON.edgeMobSmartBehavior;
    public static final ForgeConfigSpec.IntValue soundLifetimeTicks = COMMON.soundLifetimeTicks;
    public static final ForgeConfigSpec.IntValue scanCooldownTicks = COMMON.scanCooldownTicks;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> attractedEntities = COMMON.attractedEntities;
    public static final ForgeConfigSpec.DoubleValue mobMoveSpeed = COMMON.mobMoveSpeed;
    public static final ForgeConfigSpec.DoubleValue arrivalDistance = COMMON.arrivalDistance;
    public static final ForgeConfigSpec.DoubleValue minTpsForScanCooldown = COMMON.minTpsForScanCooldown;
    public static final ForgeConfigSpec.DoubleValue maxTpsForScanCooldown = COMMON.maxTpsForScanCooldown;
    public static final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration = COMMON.enableVoiceChatIntegration;
    public static final ForgeConfigSpec.IntValue voiceChatWhisperRange = COMMON.voiceChatWhisperRange;
    public static final ForgeConfigSpec.IntValue voiceChatNormalRange = COMMON.voiceChatNormalRange;
    public static final ForgeConfigSpec.DoubleValue voiceChatWeight = COMMON.voiceChatWeight;
    public static final ForgeConfigSpec.BooleanValue enableTaczIntegration = COMMON.enableTaczIntegration;
    public static final ForgeConfigSpec.DoubleValue taczReloadRange = COMMON.taczReloadRange;
    public static final ForgeConfigSpec.IntValue taczReloadWeight = COMMON.taczReloadWeight;
    public static final ForgeConfigSpec.DoubleValue taczShootRange = COMMON.taczShootRange;
    public static final ForgeConfigSpec.IntValue taczShootWeight = COMMON.taczShootWeight;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels = COMMON.taczGunShootDecibels;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions = COMMON.taczAttachmentReductions;
    public static final ForgeConfigSpec.DoubleValue leaderGroupRadius = COMMON.leaderGroupRadius;
    public static final ForgeConfigSpec.IntValue woolBlockRangeReduction = COMMON.woolBlockRangeReduction;
    public static final ForgeConfigSpec.DoubleValue woolBlockWeightReduction = COMMON.woolBlockWeightReduction;
    public static final ForgeConfigSpec.IntValue solidBlockRangeReduction = COMMON.solidBlockRangeReduction;
    public static final ForgeConfigSpec.DoubleValue solidBlockWeightReduction = COMMON.solidBlockWeightReduction;
    public static final ForgeConfigSpec.IntValue nonSolidBlockRangeReduction = COMMON.nonSolidBlockRangeReduction;
    public static final ForgeConfigSpec.DoubleValue nonSolidBlockWeightReduction = COMMON.nonSolidBlockWeightReduction;
    public static final ForgeConfigSpec.IntValue thinBlockRangeReduction = COMMON.thinBlockRangeReduction;
    public static final ForgeConfigSpec.DoubleValue thinBlockWeightReduction = COMMON.thinBlockWeightReduction;
    public static final ForgeConfigSpec.IntValue liquidBlockRangeReduction = COMMON.liquidBlockRangeReduction;
    public static final ForgeConfigSpec.DoubleValue liquidBlockWeightReduction = COMMON.liquidBlockWeightReduction;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks = COMMON.customWoolBlocks;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks = COMMON.customSolidBlocks;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks = COMMON.customNonSolidBlocks;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> customThinBlocks = COMMON.customThinBlocks;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> customLiquidBlocks = COMMON.customLiquidBlocks;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> parcoolAnimatorSoundConfigs = COMMON.parcoolAnimatorSoundConfigs;
    public static final ForgeConfigSpec.DoubleValue baseDetectionRange = COMMON.baseDetectionRange;
    public static final ForgeConfigSpec.DoubleValue sneakDetectionRange = COMMON.sneakDetectionRange;
    public static final ForgeConfigSpec.DoubleValue crawlDetectionRange = COMMON.crawlDetectionRange;
    public static final ForgeConfigSpec.DoubleValue sneakDetectionRangeCamouflage = COMMON.sneakDetectionRangeCamouflage;
    public static final ForgeConfigSpec.DoubleValue crawlDetectionRangeCamouflage = COMMON.crawlDetectionRangeCamouflage;
    public static final ForgeConfigSpec.DoubleValue standingDetectionRange = COMMON.standingDetectionRange;
    public static final ForgeConfigSpec.DoubleValue standingDetectionRangeCamouflage = COMMON.standingDetectionRangeCamouflage;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> camouflageSets = COMMON.camouflageSets;

    // --- Stealth & Camouflage static references ---
    // (removed duplicate assignments to fix compilation error)

    public static final ForgeConfigSpec.DoubleValue groupDistance = COMMON.groupDistance;
    public static final ForgeConfigSpec.IntValue maxLeaders = COMMON.maxLeaders;
    public static final ForgeConfigSpec.IntValue maxGroupSize = COMMON.maxGroupSize;
    public static final ForgeConfigSpec.DoubleValue leaderSpacingMultiplier = COMMON.leaderSpacingMultiplier;
    public static final ForgeConfigSpec.IntValue numEdgeSectors = COMMON.numEdgeSectors;
    public static final ForgeConfigSpec.IntValue mufflingAreaRadius = COMMON.mufflingAreaRadius;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> soundIdWhitelist = COMMON.soundIdWhitelist;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> nonPlayerSoundIdList = COMMON.nonPlayerSoundIdList;

    public static final ForgeConfigSpec.BooleanValue woolMufflingEnabled = COMMON.woolMufflingEnabled;
    public static final ForgeConfigSpec.BooleanValue solidMufflingEnabled = COMMON.solidMufflingEnabled;
    public static final ForgeConfigSpec.BooleanValue nonSolidMufflingEnabled = COMMON.nonSolidMufflingEnabled;
    public static final ForgeConfigSpec.BooleanValue thinMufflingEnabled = COMMON.thinMufflingEnabled;
    public static final ForgeConfigSpec.BooleanValue liquidMufflingEnabled = COMMON.liquidMufflingEnabled;

    public static void register() {
    }
}
