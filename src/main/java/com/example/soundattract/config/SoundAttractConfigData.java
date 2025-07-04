package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Identifier;
import net.minecraft.entity.mob.MobEntity;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration data for the Sound Attract mod (Fabric).
 * 
 * This class now supports “mob profiles” in addition to existing sound and stealth settings.
 * A “mob profile” entry is a semicolon-separated string of five parts:
 *   1. profileName            (String)
 *   2. mobIdString            (String, or "*" to match all)
 *   3. nbtMatcherString       (String, optional NBT like "{IsAlpha:1b}", or blank)
 *   4. soundOverridesString   (comma-separated list of “namespace:path:range:weight” entries)
 *   5. detectionOverridesString (comma-separated list of “stance:value” entries, e.g. “standing:80.0”)
 *
 * Example raw entry:
 *   "GreedyGoblin;minecraft:piglin;{IsAlpha:1b};minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0;standing:40.0,sneaking:20.0,crawling:10.0"
 */
public class SoundAttractConfigData {
    // ====================
    // === Mob Profiles ===
    // ====================

    /**
     * Each entry here must be in the format:
     *   "profileName;mobIdString;nbtMatcherString;soundOverrides;detectionOverrides"
     * where:
     *   - profileName: a unique identifier for your profile (for logging/reference).
     *   - mobIdString: either a namespaced entity ID (e.g. "minecraft:zombie") or "*" to match any mob.
     *   - nbtMatcherString: optional CompoundTag string (e.g. "{IsAlpha:1b}"), or blank if none.
     *   - soundOverrides: comma-separated list of entries, each "namespace:path:range:weight". 
     *       Example: "minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0"
     *   - detectionOverrides: comma-separated list of "stance:value" pairs (stance ∈ [standing, sneaking, crawling]).
     *       Example: "standing:40.0,sneaking:20.0,crawling:10.0"
     *
     * If the mobIdString is "*", the profile will match any mob (ignoring NBT if absent).  
     * If nbtMatcherString is non-empty, it’s parsed by NbtHelper.parseNbt(...) into a CompoundTag matcher.
     * If soundOverrides is blank or empty, then no overrides are applied for sounds.  
     * If detectionOverrides is blank or empty, no stance overrides are applied.
     */

    // Transient cache of parsed MobProfile instances.
    private List<MobProfile> cachedMobProfiles = null;

    /**
     * Returns a parsed, unmodifiable list of MobProfile objects derived from {@link #specialMobProfilesRaw}.
     * Parses each raw entry on first access (lazy), and caches the result. If you modify
     * specialMobProfilesRaw afterward, you can call {@link #invalidateMobProfileCache()} to force re-parse.
     */
    public List<MobProfile> getMobProfiles() {
        if (cachedMobProfiles != null) {
            return cachedMobProfiles;
        }

        List<MobProfile> list = new ArrayList<>();
        for (String rawEntry : specialMobProfilesRaw) {
            if (rawEntry == null || rawEntry.trim().isEmpty()) continue;

            // Split into 5 semicolon-separated parts
            String[] parts = rawEntry.trim().split(";", -1);
            if (parts.length != 5) {
                // Skip invalid lines or log a warning
                SoundAttractMod.LOGGER.warn(
                    "Skipping malformed mob profile entry: '{}'. Expected 5 semicolon-separated parts.",
                    rawEntry
                );
                continue;
            }

            String profileName          = parts[0].trim();
            String mobIdString          = parts[1].trim();
            String nbtMatcherString     = parts[2].trim();
            String soundOverridesString = parts[3].trim();
            String detectionString      = parts[4].trim();

            // 1) Parse NBT matcher (if any)
            NbtCompound  parsedNbt = null;
            if (!nbtMatcherString.isEmpty()) {
                try {
                    parsedNbt = StringNbtReader.parse(nbtMatcherString);
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.warn(
                        "Failed to parse NBT matcher for mob profile '{}': {}. Error: {}",
                        profileName, nbtMatcherString, e.getMessage()
                    );
                    parsedNbt = null;
                }
            }

            // 2) Parse sound overrides → List<SoundOverride>
            List<SoundOverride> soundOverrides = new ArrayList<>();
            if (!soundOverridesString.isEmpty()) {
                String[] overrideEntries = soundOverridesString.split(",");
                for (String entry : overrideEntries) {
                    String trimmed = entry.trim();
                    if (trimmed.isEmpty()) continue;
                    try {
                        SoundOverride so = SoundOverride.parse(trimmed);
                        soundOverrides.add(so);
                    } catch (IllegalArgumentException e) {
                        SoundAttractMod.LOGGER.warn(
                            "Failed to parse SoundOverride '{}' for profile '{}': {}",
                            trimmed, profileName, e.getMessage()
                        );
                    }
                }
            }

            // 3) Parse detection overrides → Map<PlayerStance, Double>
            Map<PlayerStance, Double> detectionOverrides = new HashMap<>();
            if (!detectionString.isEmpty()) {
                String[] stancePairs = detectionString.split(",");
                for (String pair : stancePairs) {
                    String trimmedPair = pair.trim();
                    if (trimmedPair.isEmpty()) continue;

                    String[] kv = trimmedPair.split(":", 2);
                    if (kv.length != 2) {
                        SoundAttractMod.LOGGER.warn(
                            "Skipping malformed detection override '{}' in profile '{}'. Expected 'stance:value'.",
                            trimmedPair, profileName
                        );
                        continue;
                    }

                    String stanceName = kv[0].trim().toLowerCase(Locale.ROOT);
                    String valueStr    = kv[1].trim();
                    Optional<PlayerStance> stanceOpt = PlayerStance.fromString(stanceName);
                    if (stanceOpt.isEmpty()) {
                        SoundAttractMod.LOGGER.warn(
                            "Unknown PlayerStance '{}' in profile '{}'. Skipping.",
                            stanceName, profileName
                        );
                        continue;
                    }

                    double val;
                    try {
                        val = Double.parseDouble(valueStr);
                    } catch (NumberFormatException e) {
                        SoundAttractMod.LOGGER.warn(
                            "Invalid detection override value '{}' for stance '{}' in profile '{}'. Skipping.",
                            valueStr, stanceName, profileName
                        );
                        continue;
                    }

                    detectionOverrides.put(stanceOpt.get(), val);
                }
            }

            // 4) Construct and add MobProfile
            try {
                MobProfile mp = new MobProfile(
                    profileName,
                    mobIdString.isEmpty() ? "*" : mobIdString,
                    nbtMatcherString.isEmpty() ? null : nbtMatcherString,
                    soundOverrides,
                    detectionOverrides
                );
                list.add(mp);
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn(
                    "Failed to construct MobProfile for entry '{}': {}",
                    rawEntry, e.getMessage()
                );
            }
        }

        // Cache as unmodifiable list
        cachedMobProfiles = Collections.unmodifiableList(list);
        return cachedMobProfiles;
    }

    /** Call this if you've changed {@link #specialMobProfilesRaw} at runtime, to force re-parsing. */
    public void invalidateMobProfileCache() {
        this.cachedMobProfiles = null;
    }

    // ===============
    // === Sounds ===
    // ===============

    public static class SoundConfig {
        public final String soundId;
        public final double range;
        public final double weight;

        public SoundConfig(String soundId, double range, double weight) {
            this.soundId = soundId;
            this.range = range;
            this.weight = weight;
        }
    }
    /**
 * Find and return the first MobProfile whose criteria match the given MobEntity.
 * If no profile matches, returns null.
 */
    public MobProfile getMatchingProfile(MobEntity mob) {
    // Iterate over all parsed MobProfile entries
        for (MobProfile profile : getMobProfiles()) {
            if (profile.matches(mob)) {
                return profile;
            }
        }
        return null;
    }


    public SoundConfig getSoundConfigForId(String id) {
        if (id == null || nonPlayerSoundIdList == null) return null;
        for (String entry : nonPlayerSoundIdList) {
            String[] parts = entry.split(";");
            if (parts.length >= 3 && id.equals(parts[0])) {
                try {
                    double range = Double.parseDouble(parts[1]);
                    double weight = Double.parseDouble(parts[2]);
                    return new SoundConfig(id, range, weight);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    // ===================
    // === General =======
    // ===================

    /**
     * If true, enables detailed debug logging for the Sound Attract mod.
     * Useful for troubleshooting or development. Has no effect on gameplay.
     * Default: false
     * Recommended: false for normal use, true only if you want to see debug logs in your console.
     */
    public boolean debugLogging = false;

    /**
     * The lifetime of a sound event in ticks (20 ticks = 1 second).
     * Higher values mean mobs will be attracted to sounds for longer.
     * Default: 200 (10 seconds)
     * Recommended: 40–400. Minimum: 1. Maximum: 1200.
     * Lower values = mobs lose interest faster. Higher = mobs may travel farther for old sounds.
     */
    public int soundLifetimeTicks = 200;

    /**
     * The cooldown between mob sound scans in ticks (20 ticks = 1 second).
     * Lower values mean mobs scan for sounds more frequently (more responsive but higher CPU usage).
     * Default: 25 (~1 second)
     * Recommended: 10–60. Minimum: 1. Maximum: 200.
     */
    public int scanCooldownTicks = 25;

    /**
     * Minimum server TPS (ticks per second) at which the scan cooldown is applied.
     * If TPS drops below this, scanning slows down to reduce lag.
     * Default: 10.0
     * Recommended: 5.0–15.0. Minimum: 1.0. Maximum: 20.0.
     */
    public double minTpsForScanCooldown = 10.0;

    /**
     * Maximum server TPS (ticks per second) at which the scan cooldown is applied.
     * If TPS is at or above this, scanning is at normal speed.
     * Default: 20.0
     * Recommended: 15.0–20.0. Minimum: 10.0. Maximum: 20.0.
     */
    public double maxTpsForScanCooldown = 20.0;

    /**
     * The number of spatial cells to process per server tick for mob group updates.
     * Higher values = faster updates but more CPU usage. Lower values = less lag but slower group response.
     * Default: 30
     * Recommended: 10–100. Minimum: 1.
     */
    public int cellsPerTick = 50;

    /**
     * Minimum number of cells to process per tick (adaptive batching).
     * Default: 5
     */
    public int minCellsPerTick = 25;

    /**
     * Maximum number of cells to process per tick (adaptive batching).
     * Default: 60
     */
    public int maxCellsPerTick = 100;

    /**
     * The number of mobs to process per cell per tick for group assignment.
     * Higher values = faster grouping but more CPU usage. Lower values = less lag but slower group response.
     * Default: 25
     * Recommended: 10–50. Minimum: 1.
     */
    public int mobsPerCellPerTick = 55;

    /**
     * Minimum number of mobs per cell per tick (adaptive batching).
     * Default: 1
     */
    public int minMobsPerCellPerTick = 25;

    /**
     * Maximum number of mobs per cell per tick (adaptive batching).
     * Default: 100
     */
    public int maxMobsPerCellPerTick = 100;

    /**
     * The most recent known TPS value for adaptive batching.
     * Should be updated by the mod at runtime.
     */
    public double lastKnownTps = 20.0;

    /**
     * Optional: Supplier for real-time TPS (set by mod at runtime).
     */
    public java.util.function.Supplier<Double> serverTpsSupplier = null;

    /**
     * The ratio used to determine if a mob should switch to a new sound target while pursuing a sound.
     * If the new sound's range is greater than the current target's range multiplied by this ratio,
     * the mob (leader, edge, or deserter) will switch to the new target. This also updates group members if the leader switches.
     * Default: 0.7
     * Recommended: 0.1–1.0. Minimum: 0.01. Maximum: 2.0.
     * Lower = mobs switch more easily to new sounds. Higher = mobs stick to their current target longer.
     */

    public boolean useRangeInSoundSwitch = true;

    public double soundSwitchRatio = 0.7;

    /**
     * The distance (in blocks) at which mobs consider themselves to have "arrived" at a sound source.
     * Default: 6.0
     * Recommended: 2.0–16.0. Minimum: 0.5. Maximum: 64.0.
     * Lower values = mobs must get closer to the sound. Higher = mobs stop farther away.
     */
    public double arrivalDistance = 6.0;

    /**
     * The movement speed multiplier for mobs attracted to sounds.
     * 1.0 is normal speed, higher values make mobs move faster to sounds.
     * Default: 1.0
     * Recommended: 0.5–2.0. Minimum: 0.1. Maximum: 10.0.
     */
    public double mobMoveSpeed = 1.0;

    // ================
    // === Mobs =======
    // ================

    /**
     * List of entity IDs (as strings) for mobs that will be attracted to sounds.
     * Example: ["minecraft:zombie", "minecraft:skeleton"]
     * You can add or remove entities to customize which mobs react to sound events.
     * Default: a variety of hostile mobs (see list).
     */
    public List<String> attractedEntities = new ArrayList<>(List.of(
        "minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned",
        "minecraft:endermite", "minecraft:evoker", "minecraft:guardian", "minecraft:hoglin", "minecraft:husk",
        "minecraft:magma_cube", "minecraft:phantom", "minecraft:piglin", "minecraft:piglin_brute",
        "minecraft:pillager", "minecraft:ravager", "minecraft:shulker", "minecraft:silverfish",
        "minecraft:skeleton", "minecraft:slime", "minecraft:spider", "minecraft:stray",
        "minecraft:vex", "minecraft:vindicator", "minecraft:witch",
        "minecraft:wither_skeleton", "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager"
    ));

    /**
     * If true, enables experimental "edge mob smart behavior".
     * This may cause instability or bugs and is intended for advanced users or testing.
     * Default: false
     * Recommended: false for normal use, true only if you want to help test edge mob logic.
     */
    public boolean edgeMobSmartBehavior = false;

    public int delayedRelayTicks = 2000;

    /**
     * The size (in blocks) of each spatial partition (cell/chunk) used for both sound detection and mob grouping.
     * Increasing this value means each partition covers a larger area, which can improve performance but may reduce precision.
     * Decreasing it makes partitions smaller and more precise, but may be less efficient.
     *
     * Example: If set to 32, both the sound system and mob group system will process events in 32x32 block regions.
     * Default: 16 (standard chunk size).
     * Recommended: 8–64. Minimum: 4. Maximum: 128.
     */
    public int spatialPartitionSize = 24;

    /**
     * Maximum distance (in blocks) for mobs to be considered part of the same group.
     * Default: 32.0
     * Recommended: 8–64. Minimum: 1. Maximum: 128.
     */
    public double groupDistance = 128.0;

    /**
     * Maximum number of mobs allowed in a single group (cell).
     * Default: 128
     * Recommended: 16–256. Minimum: 1. Maximum: 1024.
     */
    public int maxGroupSize = 128;

    /**
     * Number of angular sectors used for edge mob selection logic. Affects how mobs are distributed on the "edge" of a group.
     * Default: 6
     * Recommended: 4–12. Minimum: 1. Maximum: 32.
     */
    public int numEdgeSectors = 6;

    // ================
    // === Sound =======
    // ================

    /**
     * List of non-player sound IDs that mobs can be attracted to.
     * Each entry is in the format: "soundId;range;weight"
     *   - soundId: The resource location of the sound event (e.g., "minecraft:block.lever.click")
     *   - range: The maximum distance (in blocks) at which mobs can hear this sound
     *   - weight: How strongly mobs are attracted to this sound (higher = more attractive)
     * Example: "minecraft:block.lever.click;6;1.0"
     * Range recommended: 1–128. Weight recommended: 0.1–100.
     * You can add custom modded sounds here as well.
     */
    public List<String> nonPlayerSoundIdList = new ArrayList<>(List.of(
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
        "minecraft:block.piston.extend;25;4",
        "minecraft:block.piston.contract;25;4",
        "minecraft:block.dispenser.dispense;12;4",
        "minecraft:block.dispenser.launch;12;4",
        "minecraft:block.anvil.land;45;5",
        "minecraft:block.anvil.use;35;5",
        "minecraft:block.anvil.destroy;30;5",
        "minecraft:block.sand.fall;6;3",
        "minecraft:block.gravel.fall;6;3",
        "minecraft:block.grass.break;3;2",
        "minecraft:block.scaffolding.break;3;2",
        "tacz:target_block_hit;26;3",
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
        "minecraft:block.bell.use;40;5",
        "minecraft:block.bell.resonate;25;4",
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
    ));

    // =========================
    // === SoundIdWhitelist ===
    // =========================

    /**
     * Whitelist of sound IDs to process for mob attraction. Only sounds in this list will be checked, improving performance.
     * Each entry is a resource location string (e.g., "minecraft:block.note_block.bass").
     * Add or remove sounds as needed for your modpack or server.
     * Example: "minecraft:block.note_block.bass"
     */
    public List<String> soundIdWhitelist = new ArrayList<>(List.of(
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
        "minecraft:block.furnace.fire_crackle",
        "minecraft:entity.generic.explode",
        "minecraft:entity.firework_rocket.launch",
        "minecraft:entity.firework_rocket.blast",
        "minecraft:entity.firework_rocket.large_blast",
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
    ));

    // =======================
    // === Block Muffling ===
    // =======================

    /**
     * The radius (in blocks) around the path from sound source to mob to check for muffling blocks.
     * 0 = only check the direct line, 1 = check a 3x3 area, 2 = check a 5x5 area, etc.
     * Higher values make muffling more forgiving but may reduce performance.
     * Default: 0
     */
    public int mufflingAreaRadius = 0;

    /**
     * If true, wool blocks will reduce sound range and weight.
     * Default: true
     */
    public boolean woolMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as wool for muffling (e.g., modded wool blocks).
     * Example: "modid:custom_wool"
     * Leave empty to use only vanilla wool.
     */
    public List<String> customWoolBlocks = new ArrayList<>();

    /**
     * Number of blocks by which wool reduces sound range per block in the path.
     * Default: 6
     */
    public int woolBlockRangeReduction = 6;

    /**
     * Amount by which wool reduces sound weight per block in the path (absolute deduction).
     * Example: 0.5 means each wool block reduces sound weight by 0.5.
     * Default: 0.6
     */
    public double woolBlockWeightReduction = 0.6;

    /**
     * If true, solid blocks will reduce sound range and weight.
     * Default: true
     */
    public boolean solidMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as solid for muffling (e.g., modded stone blocks).
     * Example: "modid:custom_stone"
     * Leave empty to use only vanilla solid blocks.
     */
    public List<String> customSolidBlocks = new ArrayList<>();

    /**
     * Number of blocks by which solid blocks reduce sound range per block in the path.
     * Default: 4
     */
    public int solidBlockRangeReduction = 4;

    /**
     * Amount by which solid blocks reduce sound weight per block in the path (absolute deduction).
     * Example: 1.0 means each solid block reduces sound weight by 1.0.
     * Default: 0.4
     */
    public double solidBlockWeightReduction = 0.4;

    /**
     * If true, non-solid blocks (like leaves) will reduce sound range and weight.
     * Default: true
     */
    public boolean nonSolidMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as non-solid for muffling.
     * Example: "modid:custom_leaves"
     * Leave empty to use only vanilla non-solid blocks.
     */
    public List<String> customNonSolidBlocks = new ArrayList<>();

    /**
     * Number of blocks by which non-solid blocks reduce sound range per block in the path.
     * Default: 3
     */
    public int nonSolidBlockRangeReduction = 3;

    /**
     * Amount by which non-solid blocks reduce sound weight per block in the path (absolute deduction).
     * Example: 0.5 means each non-solid block reduces sound weight by 0.5.
     * Default: 0.3
     */
    public double nonSolidBlockWeightReduction = 0.3;

    /**
     * If true, thin blocks (like glass panes or fences) will reduce sound range and weight.
     * Default: true
     */
    public boolean thinMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as thin for muffling.
     * Example: "modid:custom_fence"
     * Leave empty to use only vanilla thin blocks.
     */
    public List<String> customThinBlocks = new ArrayList<>();

    /**
     * Number of blocks by which thin blocks reduce sound range per block in the path.
     * Default: 2
     */
    public int thinBlockRangeReduction = 2;

    /**
     * Amount by which thin blocks reduce sound weight per block in the path (absolute deduction).
     * Example: 0.2 means each thin block reduces sound weight by 0.2.
     * Default: 0.2
     */
    public double thinBlockWeightReduction = 0.2;

    /**
     * If true, liquid blocks (like water or lava) will reduce sound range and weight.
     * Default: true
     */
    public boolean liquidMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as liquid for muffling.
     * Example: "minecraft:lava", "modid:custom_fluid"
     * Leave empty to use only vanilla liquids.
     */
    public List<String> customLiquidBlocks = new ArrayList<>();

    /**
     * Number of blocks by which liquid blocks reduce sound range per block in the path.
     * Default: 1
     */
    public int liquidBlockRangeReduction = 1;

    /**
     * Amount by which liquid blocks reduce sound weight per block in the path (absolute deduction).
     * Example: 1.0 means each liquid block reduces sound weight by 1.0.
     * Default: 0.1
     */
    public double liquidBlockWeightReduction = 0.1;

    // ===============================
    // === Stealth Detection Settings ==
    // ===============================
    
    public List<String> fovOverrides = new ArrayList<>(List.of(
                        // --- 360° OMNI-DIRECTIONAL VISION ---
                        "minecraft:spider, 360.0, 360.0",
                        "minecraft:cave_spider, 360.0, 360.0",

                        // --- AERIAL VISION (Enhanced Vertical) ---
                        "minecraft:phantom, 200.0, 280.0",
                        "minecraft:vex, 200.0, 280.0",
                        "minecraft:allay, 200.0, 280.0",
                        "minecraft:bat, 20.0, 20.0",    
                        "minecraft:parrot, 200.0, 280.0",
                        "minecraft:ghast, 200.0, 280.0",
                        "minecraft:blaze, 200.0, 280.0",

                        // --- PREY VISION (Wide Horizontal) ---
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
                        
                        // --- AQUATIC PREY VISION (Wide Horizontal) ---
                        "minecraft:cod, 300.0, 100.0",
                        "minecraft:pufferfish, 300.0, 100.0",
                        "minecraft:salmon, 300.0, 100.0",
                        "minecraft:squid, 300.0, 100.0",
                        "minecraft:glow_squid, 300.0, 100.0",
                        "minecraft:tadpole, 300.0, 100.0",
                        "minecraft:tropical_fish, 300.0, 100.0",

                        // --- PREDATOR VISION (Focused Forward) ---
                        "minecraft:cat, 140.0, 140.0",
                        "minecraft:ocelot, 140.0, 140.0",
                        "minecraft:wolf, 140.0, 140.0",
                        "minecraft:polar_bear, 140.0, 140.0",
                        "minecraft:fox, 140.0, 140.0",      
                        "minecraft:frog, 140.0, 140.0",      

                        // --- HUMANOID/STANDARD VISION ---
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
                        
                        // --- SPECIAL CASES & MONSTROSITIES ---
                        "minecraft:creeper, 90.0, 90.0",       
                        "minecraft:enderman, 180.0, 240.0",     
                        "minecraft:guardian, 320.0, 180.0",     
                        "minecraft:elder_guardian, 320.0, 180.0",
                        "minecraft:ravager, 160.0, 100.0",     
                        "minecraft:hoglin, 160.0, 100.0",      
                        "minecraft:zoglin, 160.0, 100.0",      
                        "minecraft:shulker, 270.0, 45.0"    
    ));

    public List<String> fovExclusionList = new ArrayList<>(List.of(
    ));

    // -- General/Performance --
    /** How often (in ticks) to check mob stealth detection. Higher = less CPU, lower = more responsive. Default: 30 */
    public int stealthCheckInterval = 30;

    // -- Player Stance & Detection Ranges --
    /** Detection range for standing players. Default: 32.0 */
    public double standingDetectionRange = 32.0;
    /** Detection range for standing players with camouflage. Default: 1.0 */
    public double standingDetectionRangeCamouflage = 1.0;
    /** Detection range for sneaking players. Default: 12.0 */
    public double sneakDetectionRange = 12.0;
    /** Detection range for sneaking camouflaged players. Default: 1.0 */
    public double sneakDetectionRangeCamouflage = 1.0;
    /** Detection range for crawling players. Default: 4.0 */
    public double crawlDetectionRange = 4.0;
    /** Detection range for crawling camouflaged players. Default: 1.0 */
    public double crawlDetectionRangeCamouflage = 1.0;

    // -- New “Full” Stealth Modifiers --
    /** Invisibility effect multiplier (multiplicative). Default: 0.1 */
    public double invisibilityStealthFactor = 0.1;

    /** The “neutral” light level at which there is zero net detection change. Default: 7.0 */
    public double neutralLightLevel = 7.0;
    /** How strongly (per light point above/below neutral) detection range is scaled. Default: 0.3 */
    public double lightLevelSensitivity = 0.8;
    /** Minimum allowed light‐factor (clamped). Default: 0.2 */
    public double minLightFactor = 0.2;
    /** Maximum allowed light‐factor (clamped). Default: 3.0 */
    public double maxLightFactor = 3.0;

    /** Whether holding items widens (penalizes) detection range. Default: true */
    public boolean enableHeldItemPenalty = true;
    /** Per‐held‐item factor: actual range ×= heldItemPenaltyFactor for each item. Default: 1.1 */
    public double heldItemPenaltyFactor = 1.1;

    /** Whether visibly enchanted armor/held items widen detection. Default: true */
    public boolean enableEnchantmentPenalty = true;
    /** Per‐enchanted‐armor‐piece factor: range ×= armorEnchantmentPenaltyFactor. Default: 1.15 */
    public double armorEnchantmentPenaltyFactor = 1.15;
    /** Per‐enchanted‐held‐item factor: range ×= heldItemEnchantmentPenaltyFactor. Default: 0.9 */
    public double heldItemEnchantmentPenaltyFactor = 1.15;

    /** Rain penalty multiplier: range ×= rainStealthFactor. Default: 0.8 */
    public double rainStealthFactor = 0.8;
    /** Thunder penalty multiplier: range ×= thunderStealthFactor. Default: 0.6 */
    public double thunderStealthFactor = 0.6;

    /** Squared‐velocity threshold above which the player is “moving.” Default: 0.003 */
    public double movementThreshold = 0.003;
    /** Multiplier when player is moving (and not sneak/crawl): range ×= movementStealthPenalty. Default: 0.9 */
    public double movementStealthPenalty = 1.2;
    /** Multiplier when player is stationary (and not sneak/crawl): range ×= stationaryStealthBonusFactor. Default: 1.1 */
    public double stationaryStealthBonusFactor = 0.8;

    // -- Camouflage Scaling & Penalties --
    /** Enable distance scaling: camo is more effective at longer distances. Default: true */
    public boolean camouflageDistanceScaling = true;
    /** Distance for maximum camouflage effectiveness. Default: 16.0 */
    public double camouflageDistanceMax = 16.0;
    /** Minimum camouflage effectiveness at point-blank. Default: 0.3 */
    public double camouflageDistanceMinEffectiveness = 0.3;
    /** Enable movement penalty: moving quickly reduces camouflage. Default: true */
    public boolean camouflageMovementPenalty = true;
    /** Penalty to camouflage factor when sprinting (0.0 = no penalty, 1.0 = full penalty). Default: 0.4 */
    public double camouflageSprintingPenalty = 0.4;
    /** Penalty to camouflage factor when walking (0.0 = no penalty, 1.0 = full penalty). Default: 0.15 */
    public double camouflageWalkingPenalty = 0.15;

    public int camouflageColorSimilarityThreshold = 70;


    // -- Camouflage Sets --
    /**
     * List of camouflage sets, each describing a color and the required armor and blocks for camouflage.
     * Format: color;helmet;chestplate;leggings;boots;block1;block2;…
     * Example: "F9FFFE;minecraft:leather_helmet;…;minecraft:white_wool"
     */
    public List<String> camouflageSets = new ArrayList<>(List.of(
    // White
    "F9FFFE;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Orange
    "F9801D;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Magenta
    "C74EBD;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Light Blue
    "3AB3DA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Yellow
    "FED83D;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Lime
    "80C71F;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Pink
    "F38BAA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Gray
    "474F52;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Light Gray
    "9D9D97;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Cyan
    "169C9C;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Purple
    "8932B8;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Blue
    "3C44AA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Brown
    "835432;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Green
    "5E7C16;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Red
    "B02E26;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",
    // Black
    "1D1D21;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots"
    ));


    // ==========================
    // === Voice Chat Integration ===
    // ==========================

    /**
     * If true, enables integration with Simple Voice Chat mod. Sounds from players using voice chat will attract mobs.
     * Default: true
     */
    public boolean enableVoiceChatIntegration = true;

    /**
     * The range (in blocks) at which mobs can hear players whispering in voice chat.
     * Default: 8
     */
    public int voiceChatWhisperRange = 8;

    /**
     * The range (in blocks) at which mobs can hear players speaking normally in voice chat.
     * Default: 24
     */
    public int voiceChatNormalRange = 32;

    /**
     * The "weight" of voice chat sounds. Higher values make mobs more likely to be attracted to voice chat.
     * Default: 9.0
     */
    public double voiceChatWeight = 50.0;

    // =====================
    // === Tacz Integration ===
    // =====================

    /**
     * If true, enables integration with Tacz mod for custom gun sounds.
     * Default: true
     */
    public boolean enableTaczIntegration = true;

    /**
     * The fallback range (in blocks) for Tacz gun reload sounds, if no specific value is found for a gun.
     * For known guns, this is calculated as shootDb/20.0.
     * Default: 9.0
     */
    public double taczReloadRange = 9.0;

    /**
     * The fallback "weight" for Tacz gun reload sounds, if no specific value is found for a gun.
     * For known guns, this is calculated as (shootDb/10.0)/2.0.
     * Default: 9
     */
    public int taczReloadWeight = 9;

    /**
     * The fallback range (in blocks) for Tacz gun shoot sounds, if no specific value is found for a gun.
     * For known guns, this is calculated as the gun's decibel value.
     * Default: 128.0
     */
    public double taczShootRange = 128.0;

    /**
     * The base attachment reduction applied to all Tacz gunshots, regardless of specific attachment.
     * Default: 0.0 (no reduction)
     */
    public double taczBaseAttachmentReduction = 0.0;

    /**
     * The fallback "weight" for Tacz gun shoot sounds, if no specific value is found for a gun.
     * For known guns, this is calculated as decibels/10.0.
     * Default: 10
     */
    public int taczShootWeight = 10;

    /**
     * List of gun decibel values for Tacz mod guns.
     * Each entry is in the format: 'modid:item;decibels'.
     * Example: 'tacz:akm;120.0' means the AKM gun has a shoot sound of 120 decibels.
     * You can add custom tacz guns here.
     */
    public List<String> taczGunShootDecibels = new ArrayList<>(List.of(
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
    ));

    /**
     * List of attachment sound reductions for Tacz mod guns.
     * Each entry is in the format: 'modid:item;reduction'.
     * Example: 'tacz:suppressor;15.0' means the suppressor reduces gun sound by 15 decibels.
     * You can add custom or modded attachments here.
     */
    public List<String> taczAttachmentReductions = new ArrayList<>(List.of(
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
    ));

    public List<String> specialMobProfilesRaw = new ArrayList<>(List.of(
        "GreedyGoblin;minecraft:piglin;;minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0;standing:40.0,sneaking:20.0,crawling:10.0",
        "FastZombie;minecraft:zombie;{IsAlpha:1b};minecraft:entity.player.hurt:25.0:2.0;standing:60.0,sneaking:30.0,crawling:10.0"
    ));
}
