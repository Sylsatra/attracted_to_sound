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
 * This class now supports “mob profiles” in addition to existing sound and
 * stealth settings. A “mob profile” entry is a semicolon-separated string of
 * five parts: 1. profileName (String) 2. mobIdString (String, or "*" to match
 * all) 3. nbtMatcherString (String, optional NBT like "{IsAlpha:1b}", or blank)
 * 4. soundOverridesString (comma-separated list of
 * “namespace:path:range:weight” entries) 5. detectionOverridesString
 * (comma-separated list of “stance:value” entries, e.g. “standing:80.0”)
 *
 * Example raw entry:
 * "GreedyGoblin;minecraft:piglin;{IsAlpha:1b};minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0;standing:40.0,sneaking:20.0,crawling:10.0"
 */
public class SoundAttractConfigData {




    /**
     * Each entry here must be in the format:
     * "profileName;mobIdString;nbtMatcherString;soundOverrides;detectionOverrides"
     * where: - profileName: a unique identifier for your profile (for
     * logging/reference). - mobIdString: either a namespaced entity ID (e.g.
     * "minecraft:zombie") or "*" to match any mob. - nbtMatcherString: optional
     * CompoundTag string (e.g. "{IsAlpha:1b}"), or blank if none. -
     * soundOverrides: comma-separated list of entries, each
     * "namespace:path:range:weight". Example:
     * "minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0"
     * - detectionOverrides: comma-separated list of "stance:value" pairs
     * (stance ∈ [standing, sneaking, crawling]). Example:
     * "standing:40.0,sneaking:20.0,crawling:10.0"
     *
     * If the mobIdString is "*", the profile will match any mob (ignoring NBT
     * if absent). If nbtMatcherString is non-empty, it’s parsed by
     * NbtHelper.parseNbt(...) into a CompoundTag matcher. If soundOverrides is
     * blank or empty, then no overrides are applied for sounds. If
     * detectionOverrides is blank or empty, no stance overrides are applied.
     */

    private List<MobProfile> cachedMobProfiles = null;
    private transient Map<String, Double> pointBlankGunShootRangesMap;
    private transient Map<String, Double> pointBlankAttachmentSoundReductionsMap;
    private transient Map<String, Double> pointBlankMuzzleFlashReductionsMap = null;

    public void buildCaches() {
        this.cachedMobProfiles = parseMobProfiles();

        this.pointBlankGunShootRangesMap = parseConfigList(this.pointblankGunShootRanges);
        this.pointBlankAttachmentSoundReductionsMap = parseConfigList(this.pointblankAttachmentSoundReductions);
        this.pointBlankMuzzleFlashReductionsMap = parseConfigList(this.pointblankMuzzleFlashReductions);

        SoundAttractMod.LOGGER.info("[Config] Caches have been built.");
    }

    /**
     * Returns a parsed, unmodifiable map of Point Blank muzzle flash
     * reductions. This is the recommended way to access this data for
     * performance.
     */
    public void invalidateCaches() {
        this.cachedMobProfiles = null;
        this.pointBlankGunShootRangesMap = null;
        this.pointBlankAttachmentSoundReductionsMap = null;
        this.pointBlankMuzzleFlashReductionsMap = null;
        SoundAttractMod.LOGGER.info("[Config] All caches have been invalidated.");
    }

    public Map<String, Double> getPointBlankGunShootRanges() {
        if (pointBlankGunShootRangesMap == null) {
            buildCaches();
        }
        return pointBlankGunShootRangesMap;
    }

    public Map<String, Double> getPointBlankAttachmentSoundReductions() {
        if (pointBlankAttachmentSoundReductionsMap == null) {
            buildCaches();
        }
        return pointBlankAttachmentSoundReductionsMap;
    }

    public Map<String, Double> getPointBlankMuzzleFlashReductions() {
        if (pointBlankMuzzleFlashReductionsMap == null) {
            buildCaches();
        }
        return pointBlankMuzzleFlashReductionsMap;
    }

    public List<MobProfile> parseMobProfiles() {
        List<MobProfile> list = new ArrayList<>();
        for (String rawEntry : specialMobProfilesRaw) {
            if (rawEntry == null || rawEntry.trim().isEmpty()) {
                continue;
            }


            String[] parts = rawEntry.trim().split(";", -1);
            if (parts.length != 5) {

                SoundAttractMod.LOGGER.warn(
                        "Skipping malformed mob profile entry: '{}'. Expected 5 semicolon-separated parts.",
                        rawEntry
                );
                continue;
            }

            String profileName = parts[0].trim();
            String mobIdString = parts[1].trim();
            String nbtMatcherString = parts[2].trim();
            String soundOverridesString = parts[3].trim();
            String detectionString = parts[4].trim();


            NbtCompound parsedNbt = null;
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


            List<SoundOverride> soundOverrides = new ArrayList<>();
            if (!soundOverridesString.isEmpty()) {
                String[] overrideEntries = soundOverridesString.split(",");
                for (String entry : overrideEntries) {
                    String trimmed = entry.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
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


            Map<PlayerStance, Double> detectionOverrides = new HashMap<>();
            if (!detectionString.isEmpty()) {
                String[] stancePairs = detectionString.split(",");
                for (String pair : stancePairs) {
                    String trimmedPair = pair.trim();
                    if (trimmedPair.isEmpty()) {
                        continue;
                    }

                    String[] kv = trimmedPair.split(":", 2);
                    if (kv.length != 2) {
                        SoundAttractMod.LOGGER.warn(
                                "Skipping malformed detection override '{}' in profile '{}'. Expected 'stance:value'.",
                                trimmedPair, profileName
                        );
                        continue;
                    }

                    String stanceName = kv[0].trim().toLowerCase(Locale.ROOT);
                    String valueStr = kv[1].trim();
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


        cachedMobProfiles = Collections.unmodifiableList(list);
        return cachedMobProfiles;
    }

    public MobProfile getMatchingProfile(MobEntity mob) {

        for (MobProfile profile : getMobProfiles()) {
            if (profile.matches(mob)) {
                return profile;
            }
        }
        return null;
    }




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
     * Find and return the first MobProfile whose criteria match the given
     * MobEntity. If no profile matches, returns null.
     */
    public List<MobProfile> getMobProfiles() {
        if (cachedMobProfiles == null) {
            buildCaches();
        }
        return cachedMobProfiles;
    }

    public SoundConfig getSoundConfigForId(String id) {
        if (id == null || nonPlayerSoundIdList == null) {
            return null;
        }
        for (String entry : nonPlayerSoundIdList) {
            String[] parts = entry.split(";");
            if (parts.length >= 3 && id.equals(parts[0])) {
                try {
                    double range = Double.parseDouble(parts[1]);
                    double weight = Double.parseDouble(parts[2]);
                    return new SoundConfig(id, range, weight);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Map<String, Double> parseConfigList(List<String> list) {
        if (list == null) {
            return Collections.emptyMap();
        }

        Map<String, Double> map = new HashMap<>();
        for (String entry : list) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            try {
                String[] parts = entry.split(";", 2);
                if (parts.length == 2) {
                    map.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                } else {
                    SoundAttractMod.LOGGER.warn("Skipping malformed config entry: '{}'. Expected 'id;value'.", entry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse config entry: '{}'. Error: {}", entry, e.getMessage());
            }
        }
        return Collections.unmodifiableMap(map);
    }




    /**
     * If true, enables detailed debug logging for the Sound Attract mod. Useful
     * for troubleshooting or development. Has no effect on gameplay. Default:
     * false Recommended: false for normal use, true only if you want to see
     * debug logs in your console.
     */
    public boolean debugLogging = false;

    /**
     * The lifetime of a sound event in ticks (20 ticks = 1 second). Higher
     * values mean mobs will be attracted to sounds for longer. Default: 200 (10
     * seconds) Recommended: 40–400. Minimum: 1. Maximum: 1200. Lower values =
     * mobs lose interest faster. Higher = mobs may travel farther for old
     * sounds.
     */
    public int soundLifetimeTicks = 200;

    /**
     * The cooldown between mob sound scans in ticks (20 ticks = 1 second).
     * Lower values mean mobs scan for sounds more frequently (more responsive
     * but higher CPU usage). Default: 25 (~1 second) Recommended: 10–60.
     * Minimum: 1. Maximum: 200.
     */
    public int scanCooldownTicks = 25;

    /**
     * Minimum server TPS (ticks per second) at which the scan cooldown is
     * applied. If TPS drops below this, scanning slows down to reduce lag.
     * Default: 10.0 Recommended: 5.0–15.0. Minimum: 1.0. Maximum: 20.0.
     */
    public double minTpsForScanCooldown = 10.0;

    /**
     * Maximum server TPS (ticks per second) at which the scan cooldown is
     * applied. If TPS is at or above this, scanning is at normal speed.
     * Default: 20.0 Recommended: 15.0–20.0. Minimum: 10.0. Maximum: 20.0.
     */
    public double maxTpsForScanCooldown = 20.0;

    /**
     * The number of spatial cells to process per server tick for mob group
     * updates. Higher values = faster updates but more CPU usage. Lower values
     * = less lag but slower group response. Default: 30 Recommended: 10–100.
     * Minimum: 1.
     */
    public int cellsPerTick = 50;

    /**
     * Minimum number of cells to process per tick (adaptive batching). Default:
     * 5
     */
    public int minCellsPerTick = 25;

    /**
     * Maximum number of cells to process per tick (adaptive batching). Default:
     * 60
     */
    public int maxCellsPerTick = 100;

    /**
     * The number of mobs to process per cell per tick for group assignment.
     * Higher values = faster grouping but more CPU usage. Lower values = less
     * lag but slower group response. Default: 25 Recommended: 10–50. Minimum:
     * 1.
     */
    public int mobsPerCellPerTick = 55;

    /**
     * Minimum number of mobs per cell per tick (adaptive batching). Default: 1
     */
    public int minMobsPerCellPerTick = 25;

    /**
     * Maximum number of mobs per cell per tick (adaptive batching). Default:
     * 100
     */
    public int maxMobsPerCellPerTick = 100;

    /**
     * The most recent known TPS value for adaptive batching. Should be updated
     * by the mod at runtime.
     */
    public double lastKnownTps = 20.0;

    /**
     * Optional: Supplier for real-time TPS (set by mod at runtime).
     */
    public java.util.function.Supplier<Double> serverTpsSupplier = null;

    /**
     * The ratio used to determine if a mob should switch to a new sound target
     * while pursuing a sound. If the new sound's range is greater than the
     * current target's range multiplied by this ratio, the mob (leader, edge,
     * or deserter) will switch to the new target. This also updates group
     * members if the leader switches. Default: 0.7 Recommended: 0.1–1.0.
     * Minimum: 0.01. Maximum: 2.0. Lower = mobs switch more easily to new
     * sounds. Higher = mobs stick to their current target longer.
     */
    public boolean useRangeInSoundSwitch = true;

    public double soundSwitchRatio = 0.7;
    /**
     * A small weight bonus given to very new sounds to make mobs more likely to
     * switch to them. This helps break ties and makes mobs seem more 'alert' to
     * new threats. Set to 0.0 to disable. Default: 0.5
     */
    public double soundNoveltyBonusWeight = 0.5;

    /**
     * How long (in ticks) a sound is considered 'new' for the novelty bonus to
     * apply. 20 ticks = 1 second. Default: 100
     */
    public int soundNoveltyTimeTicks = 100;

    /**
     * The distance (in blocks) at which mobs consider themselves to have
     * "arrived" at a sound source. Default: 6.0 Recommended: 2.0–16.0. Minimum:
     * 0.5. Maximum: 64.0. Lower values = mobs must get closer to the sound.
     * Higher = mobs stop farther away.
     */
    public double arrivalDistance = 6.0;

    /**
     * The movement speed multiplier for mobs attracted to sounds. 1.0 is normal
     * speed, higher values make mobs move faster to sounds. Default: 1.0
     * Recommended: 0.5–2.0. Minimum: 0.1. Maximum: 10.0.
     */
    public double mobMoveSpeed = 1.15;




    /**
     * List of entity IDs (as strings) for mobs that will be attracted to
     * sounds. Example: ["minecraft:zombie", "minecraft:skeleton"] You can add
     * or remove entities to customize which mobs react to sound events.
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
     * If true, enables experimental "edge mob smart behavior". This may cause
     * instability or bugs and is intended for advanced users or testing.
     * Default: false Recommended: false for normal use, true only if you want
     * to help test edge mob logic.
     */
    public boolean edgeMobSmartBehavior = false;

    public int delayedRelayTicks = 2000;

    /**
     * The size (in blocks) of each spatial partition (cell/chunk) used for both
     * sound detection and mob grouping. Increasing this value means each
     * partition covers a larger area, which can improve performance but may
     * reduce precision. Decreasing it makes partitions smaller and more
     * precise, but may be less efficient.
     *
     * Example: If set to 32, both the sound system and mob group system will
     * process events in 32x32 block regions. Default: 16 (standard chunk size).
     * Recommended: 8–64. Minimum: 4. Maximum: 128.
     */
    public int spatialPartitionSize = 24;

    /**
     * Maximum distance (in blocks) for mobs to be considered part of the same
     * group. Default: 32.0 Recommended: 8–64. Minimum: 1. Maximum: 128.
     */
    public double groupDistance = 128.0;

    /**
     * Maximum number of mobs allowed in a single group (cell). Default: 128
     * Recommended: 16–256. Minimum: 1. Maximum: 1024.
     */
    public int maxGroupSize = 128;

    /**
     * Number of angular sectors used for edge mob selection logic. Affects how
     * mobs are distributed on the "edge" of a group. Default: 6 Recommended:
     * 4–12. Minimum: 1. Maximum: 32.
     */
    public int numEdgeSectors = 6;




    /**
     * List of non-player sound IDs that mobs can be attracted to. Each entry is
     * in the format: "soundId;range;weight" - soundId: The resource location of
     * the sound event (e.g., "minecraft:block.lever.click") - range: The
     * maximum distance (in blocks) at which mobs can hear this sound - weight:
     * How strongly mobs are attracted to this sound (higher = more attractive)
     * Example: "minecraft:block.lever.click;6;1.0" Range recommended: 1–128.
     * Weight recommended: 0.1–100. You can add custom modded sounds here as
     * well.
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




    /**
     * Whitelist of sound IDs to process for mob attraction. Only sounds in this
     * list will be checked, improving performance. Each entry is a resource
     * location string (e.g., "minecraft:block.note_block.bass"). Add or remove
     * sounds as needed for your modpack or server. Example:
     * "minecraft:block.note_block.bass"
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
            "pointblank:gun_action"
    ));




    /**
     * The radius (in blocks) around the path from sound source to mob to check
     * for muffling blocks. 0 = only check the direct line, 1 = check a 3x3
     * area, 2 = check a 5x5 area, etc. Higher values make muffling more
     * forgiving but may reduce performance. Default: 0
     */
    public int mufflingAreaRadius = 0;

    /**
     * If true, wool blocks will reduce sound range and weight. Default: true
     */
    public boolean woolMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as wool for muffling (e.g., modded wool
     * blocks). Example: "modid:custom_wool" Leave empty to use only vanilla
     * wool.
     */
    public List<String> customWoolBlocks = new ArrayList<>();

    /**
     * Number of blocks by which wool reduces sound range per block in the path.
     * Default: 6
     */
    public int woolBlockRangeReduction = 6;

    /**
     * Amount by which wool reduces sound weight per block in the path (absolute
     * deduction). Example: 0.5 means each wool block reduces sound weight by
     * 0.5. Default: 0.6
     */
    public double woolBlockWeightReduction = 0.6;

    /**
     * If true, solid blocks will reduce sound range and weight. Default: true
     */
    public boolean solidMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as solid for muffling (e.g., modded
     * stone blocks). Example: "modid:custom_stone" Leave empty to use only
     * vanilla solid blocks.
     */
    public List<String> customSolidBlocks = new ArrayList<>();

    /**
     * Number of blocks by which solid blocks reduce sound range per block in
     * the path. Default: 4
     */
    public int solidBlockRangeReduction = 4;

    /**
     * Amount by which solid blocks reduce sound weight per block in the path
     * (absolute deduction). Example: 1.0 means each solid block reduces sound
     * weight by 1.0. Default: 0.4
     */
    public double solidBlockWeightReduction = 0.4;

    /**
     * If true, non-solid blocks (like leaves) will reduce sound range and
     * weight. Default: true
     */
    public boolean nonSolidMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as non-solid for muffling. Example:
     * "modid:custom_leaves" Leave empty to use only vanilla non-solid blocks.
     */
    public List<String> customNonSolidBlocks = new ArrayList<>();

    /**
     * Number of blocks by which non-solid blocks reduce sound range per block
     * in the path. Default: 3
     */
    public int nonSolidBlockRangeReduction = 3;

    /**
     * Amount by which non-solid blocks reduce sound weight per block in the
     * path (absolute deduction). Example: 0.5 means each non-solid block
     * reduces sound weight by 0.5. Default: 0.3
     */
    public double nonSolidBlockWeightReduction = 0.3;

    /**
     * If true, thin blocks (like glass panes or fences) will reduce sound range
     * and weight. Default: true
     */
    public boolean thinMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as thin for muffling. Example:
     * "modid:custom_fence" Leave empty to use only vanilla thin blocks.
     */
    public List<String> customThinBlocks = new ArrayList<>();

    /**
     * Number of blocks by which thin blocks reduce sound range per block in the
     * path. Default: 2
     */
    public int thinBlockRangeReduction = 2;

    /**
     * Amount by which thin blocks reduce sound weight per block in the path
     * (absolute deduction). Example: 0.2 means each thin block reduces sound
     * weight by 0.2. Default: 0.2
     */
    public double thinBlockWeightReduction = 0.2;

    /**
     * If true, liquid blocks (like water or lava) will reduce sound range and
     * weight. Default: true
     */
    public boolean liquidMufflingEnabled = true;

    /**
     * List of custom block IDs to treat as liquid for muffling. Example:
     * "minecraft:lava", "modid:custom_fluid" Leave empty to use only vanilla
     * liquids.
     */
    public List<String> customLiquidBlocks = new ArrayList<>();

    /**
     * Number of blocks by which liquid blocks reduce sound range per block in
     * the path. Default: 1
     */
    public int liquidBlockRangeReduction = 1;

    /**
     * Amount by which liquid blocks reduce sound weight per block in the path
     * (absolute deduction). Example: 1.0 means each liquid block reduces sound
     * weight by 1.0. Default: 0.1
     */
    public double liquidBlockWeightReduction = 0.1;




    public List<String> fovOverrides = new ArrayList<>(List.of(

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
    ));

    public List<String> fovExclusionList = new ArrayList<>(List.of());


    /**
     * How often (in ticks) to check mob stealth detection. Higher = less CPU,
     * lower = more responsive. Default: 30
     */
    public int stealthCheckInterval = 30;

    /**
     * How long (in ticks) a mob will remember a target before losing interest.
     * Default: 60 Recommended: 20–120. Minimum: 1. Maximum: 600.
     */
    public int targetLossGracePeriodTicks = 60;

    /**
     * Detection range for standing players. Default: 32.0
     */
    public double standingDetectionRange = 32.0;
    /**
     * Detection range for standing players with camouflage. Default: 1.0
     */
    public double standingDetectionRangeCamouflage = 1.0;
    /**
     * Detection range for sneaking players. Default: 12.0
     */
    public double sneakDetectionRange = 12.0;
    /**
     * Detection range for sneaking camouflaged players. Default: 1.0
     */
    public double sneakDetectionRangeCamouflage = 1.0;
    /**
     * Detection range for crawling players. Default: 4.0
     */
    public double crawlDetectionRange = 4.0;
    /**
     * Detection range for crawling camouflaged players. Default: 1.0
     */
    public double crawlDetectionRangeCamouflage = 1.0;


    /**
     * Invisibility effect multiplier (multiplicative). Default: 0.1
     */
    public double invisibilityStealthFactor = 0.1;

    /**
     * The “neutral” light level at which there is zero net detection change.
     * Default: 7.0
     */
    public double neutralLightLevel = 7.0;
    /**
     * How strongly (per light point above/below neutral) detection range is
     * scaled. Default: 0.3
     */
    public double lightLevelSensitivity = 0.8;
    /**
     * Minimum allowed light‐factor (clamped). Default: 0.2
     */
    public double minLightFactor = 0.2;
    /**
     * Maximum allowed light‐factor (clamped). Default: 3.0
     */
    public double maxLightFactor = 3.0;

    /**
     * Whether holding items widens (penalizes) detection range. Default: true
     */
    public boolean enableHeldItemPenalty = true;
    /**
     * Per‐held‐item factor: actual range ×= heldItemPenaltyFactor for each
     * item. Default: 1.1
     */
    public double heldItemPenaltyFactor = 1.1;

    /**
     * Whether visibly enchanted armor/held items widen detection. Default: true
     */
    public boolean enableEnchantmentPenalty = true;
    /**
     * Per‐enchanted‐armor‐piece factor: range ×= armorEnchantmentPenaltyFactor.
     * Default: 1.15
     */
    public double armorEnchantmentPenaltyFactor = 1.15;
    /**
     * Per‐enchanted‐held‐item factor: range ×=
     * heldItemEnchantmentPenaltyFactor. Default: 0.9
     */
    public double heldItemEnchantmentPenaltyFactor = 1.15;

    /**
     * Rain penalty multiplier: range ×= rainStealthFactor. Default: 0.8
     */
    public double rainStealthFactor = 0.8;
    /**
     * Thunder penalty multiplier: range ×= thunderStealthFactor. Default: 0.6
     */
    public double thunderStealthFactor = 0.6;

    /**
     * Squared‐velocity threshold above which the player is “moving.” Default:
     * 0.003
     */
    public double movementThreshold = 0.003;
    /**
     * Multiplier when player is moving (and not sneak/crawl): range ×=
     * movementStealthPenalty. Default: 0.9
     */
    public double movementStealthPenalty = 1.2;
    /**
     * Multiplier when player is stationary (and not sneak/crawl): range ×=
     * stationaryStealthBonusFactor. Default: 1.1
     */
    public double stationaryStealthBonusFactor = 0.8;


    /**
     * Enable distance scaling: camo is more effective at longer distances.
     * Default: true
     */
    public boolean camouflageDistanceScaling = true;
    /**
     * Distance for maximum camouflage effectiveness. Default: 16.0
     */
    public double camouflageDistanceMax = 16.0;
    /**
     * Minimum camouflage effectiveness at point-blank. Default: 0.3
     */
    public double camouflageDistanceMinEffectiveness = 0.3;
    /**
     * Enable movement penalty: moving quickly reduces camouflage. Default: true
     */
    public boolean camouflageMovementPenalty = true;
    /**
     * Penalty to camouflage factor when sprinting (0.0 = no penalty, 1.0 = full
     * penalty). Default: 0.4
     */
    public double camouflageSprintingPenalty = 0.4;
    /**
     * Penalty to camouflage factor when walking (0.0 = no penalty, 1.0 = full
     * penalty). Default: 0.15
     */
    public double camouflageWalkingPenalty = 0.15;

    public int camouflageColorSimilarityThreshold = 70;


    /**
     * List of camouflage sets, each describing a color and the required armor
     * and blocks for camouflage. Format:
     * color;helmet;chestplate;leggings;boots;block1;block2;… Example:
     * "F9FFFE;minecraft:leather_helmet;…;minecraft:white_wool"
     */
    public List<String> camouflageSets = new ArrayList<>(List.of(

            "F9FFFE;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "F9801D;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "C74EBD;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "3AB3DA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "FED83D;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "80C71F;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "F38BAA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "474F52;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "9D9D97;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "169C9C;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "8932B8;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "3C44AA;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "835432;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "5E7C16;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "B02E26;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots",

            "1D1D21;minecraft:leather_helmet;minecraft:leather_chestplate;minecraft:leather_leggings;minecraft:leather_boots"
    ));




    /**
     * If true, enables integration with Simple Voice Chat mod. Sounds from
     * players using voice chat will attract mobs. Default: true
     */
    public boolean enableVoiceChatIntegration = true;

    /**
     * The range (in blocks) at which mobs can hear players whispering in voice
     * chat. Default: 8
     */
    public int voiceChatWhisperRange = 8;

    /**
     * The range (in blocks) at which mobs can hear players speaking normally in
     * voice chat. Default: 24
     */
    public int voiceChatNormalRange = 32;

    /**
     * The "weight" of voice chat sounds. Higher values make mobs more likely to
     * be attracted to voice chat. Default: 9.0
     */
    public double voiceChatWeight = 50.0;




    /**
     * If true, enables integration with Point Blank mod for custom gun sounds.
     * Default: true
     */
    public boolean enablePointBlankIntegration = true;

    /**
     * The fallback range (in blocks) for Point Blank gun reload sounds, if no
     * specific value is found for a gun. For known guns, this is calculated as
     * shootDb/20.0. Default: 9.0
     */
    public double pointblankReloadRange = 9.0;

    /**
     * The fallback "weight" for Point Blank gun reload sounds, if no specific
     * value is found for a gun. For known guns, this is calculated as
     * (shootDb/10.0)/2.0. Default: 9
     */
    public int pointblankReloadWeight = 9;

    /**
     * The fallback range (in blocks) for Point Blank gun shoot sounds, if no
     * specific value is found for a gun. For known guns, this is calculated as
     * the gun's decibel value. Default: 128.0
     */
    public double pointblankShootRange = 128.0;

    /**
     * The base attachment reduction applied to all Point Blank gunshots,
     * regardless of specific attachment. Default: 0.0 (no reduction)
     */
    public double pointblankBaseAttachmentReduction = 0.0;

    /**
     * The fallback "weight" for Point Blank gun shoot sounds, if no specific
     * value is found for a gun. For known guns, this is calculated as
     * decibels/10.0. Default: 10
     */
    public int pointblankShootWeight = 10;

    /**
     * The base visual detection range (in blocks) when a gunshot occurs, before
     * muzzle attachments are factored in. Default: 128.0
     */
    public double gunshotBaseDetectionRange = 128.0;

    /**
     * How long (in ticks) the increased detection from a gunshot lasts. 20
     * ticks = 1 second. Default: 60
     */
    public int gunshotDetectionDurationTicks = 60;

    /**
     * List of gun decibel values for Point Blank mod guns. Each entry is in the
     * format: 'modid:item;decibels'. Example: 'Point Blank:akm;120.0' means the
     * AKM gun has a shoot sound of 120 decibels. You can add custom Point Blank
     * guns here.
     */
    public List<String> pointblankGunShootRanges = new ArrayList<>(List.of(
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

    /**
     * List of attachment sound reductions for Point Blank mod guns. Each entry
     * is in the format: 'modid:item;reduction'. Example: 'Point
     * Blank:suppressor;15.0' means the suppressor reduces gun sound by 15
     * decibels. You can add custom or modded attachments here.
     */
    public List<String> pointblankAttachmentSoundReductions = new ArrayList<>(List.of(
            "pointblank:ar_suppressor;40.0",
            "pointblank:ar_suppressor_tan;40.0",
            "pointblank:xm7_suppressor;40.0",
            "pointblank:ak_suppressor;40.0",
            "pointblank:smg_suppressor;40.0",
            "pointblank:rf_suppressor;40.0",
            "pointblank:hp_suppressor;40.0",
            "pointblank:sg_suppressor;40.0"
    ));

    /**
     * Point Blank attachment VISUAL FLASH reduction. A positive value reduces
     * flash range, a negative value INCREASES it (e.g., for muzzle brakes).
     * Format: 'modid:item;reduction_amount'
     */
    public List<String> pointblankMuzzleFlashReductions = new ArrayList<>(List.of(
            "pointblank:ar_suppressor;90.0",
            "pointblank:ar_suppressor_tan;90.0",
            "pointblank:xm7_suppressor;90.0",
            "pointblank:ak_suppressor;90.0",
            "pointblank:smg_suppressor;90.0",
            "pointblank:rf_suppressor;90.0",
            "pointblank:hp_suppressor;90.0",
            "pointblank:sg_suppressor;90.0"
    ));

    public List<String> specialMobProfilesRaw = new ArrayList<>(List.of(
            "GreedyGoblin;minecraft:piglin;;minecraft:block.chest.open:30.0:2.5,minecraft:entity.player.death:50.0:3.0;standing:40.0,sneaking:20.0,crawling:10.0",
            "FastZombie;minecraft:zombie;{IsAlpha:1b};minecraft:entity.player.hurt:25.0:2.0;standing:60.0,sneaking:30.0,crawling:10.0"
    ));

    // === Fabric-native Block Breaking (no EnhancedAI required) ===
    /** If true, mobs can break blocks when stuck pursuing a sound. */
    public boolean enableBlockBreaking = false;
    /** Multiplier applied to time needed to break a block. Higher = slower. Default 1.0 */
    public double blockBreakTimeMultiplier = 1.0;
    /** If true, only allow breaking when mob holds any item. */
    public boolean blockBreakToolOnly = false;
    /** If true, only allow when the held tool is the proper tool for the block. */
    public boolean blockBreakProperToolOnly = false;
    /** If true, disallow breaking blocks that require tool when tool is unsuitable. */
    public boolean blockBreakProperToolRequired = false;
    /** Max Y level above which blocks will not be broken. */
    public int blockBreakMaxY = 255;
    /** If true, blocks with block entities are disallowed. */
    public boolean blockBreakBlacklistTileEntities = true;
    /** If true, treat blockBreakBlockList as a whitelist; otherwise as a blacklist. */
    public boolean blockBreakListAsWhitelist = false;
    /** List of block IDs used as blacklist or whitelist depending on mode. */
    public java.util.List<String> blockBreakBlockList = new java.util.ArrayList<>();

    // === Smart LOS configuration ===
    /**
     * Blocks in this list are treated as NOT blocking vision by smart LOS.
     * Entries must be full block IDs like "modid:block_name".
     * Example: "create:framed_glass", "myglassmod:clear_glass".
     */
    public java.util.List<String> nonBlockingVisionAllowList = new java.util.ArrayList<>();
}
