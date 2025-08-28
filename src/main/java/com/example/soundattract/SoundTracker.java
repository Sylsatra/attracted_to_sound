package com.example.soundattract;

import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.SpatialPartitioner;

import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import com.example.soundattract.util.WorkerScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.util.WorkerScheduler.SoundScoreResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SoundTracker {




    public static class SoundRecord {
        public final SoundEvent sound;
        public final String soundId;
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;
        public final double weight;
        public final java.util.Set<Long> coveredCells = new java.util.HashSet<>();

        public SoundRecord(SoundEvent sound, String soundId, BlockPos pos, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.soundId = soundId;
            this.pos = pos;
            this.ticksRemaining = SoundAttractMod.CONFIG.soundLifetimeTicks;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }

        public SoundRecord(SoundEvent sound, BlockPos pos, String dimensionKey, double range, double weight) {
            this(sound,
                 (sound != null && sound.getId() != null ? sound.getId().toString() : "unknown_sound_event_id"),
                 pos, dimensionKey, range, weight);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SoundRecord other)) return false;
            return Objects.equals(soundId, other.soundId) &&
                   Objects.equals(pos, other.pos) &&
                   Objects.equals(dimensionKey, other.dimensionKey) &&
                   Double.compare(weight, other.weight) == 0 &&
                   Double.compare(range, other.range) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(soundId, pos, dimensionKey, range, weight);
        }

        public boolean isValid() {
            return this.pos != null && this.weight > 0;
        }

        public BlockPos getPosition() {
            return this.pos;
        }
    }

    public static class VirtualSoundRecord extends SoundRecord {
        public final UUID sourcePlayer;
        public final String animationClass;

        public VirtualSoundRecord(BlockPos pos, String dimensionKey, double range, double weight, UUID sourcePlayer, String animationClass) {
            super(null, "virtual_sound:" + (animationClass != null ? animationClass : "unknown"), pos, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }
    }

    private static class RaycastCacheKey {
        public final BlockPos mobPos;
        public final BlockPos soundPos;
        public final String soundId;

        public RaycastCacheKey(BlockPos mobPos, BlockPos soundPos, String soundId) {
            this.mobPos = mobPos;
            this.soundPos = soundPos;
            this.soundId = Objects.requireNonNull(soundId, "soundId cannot be null for RaycastCacheKey");
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RaycastCacheKey other)) return false;
            return mobPos.equals(other.mobPos) &&
                   soundPos.equals(other.soundPos) &&
                   soundId.equals(other.soundId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(mobPos, soundPos, soundId);
        }
    }
    private static int getGridRadiusForRange(double range, int partitionSize) {
        if (range < 8) return 1;
        if (partitionSize <= 0) partitionSize = 16; 
        for (int i = 1; i <= 20; i++) { 
            if (range < 8 + (double)partitionSize * i) { 
                return i + 1;
            }
        }
        return 21; 
    }

    public static java.util.Set<Long> getCoveredCells(BlockPos pos, double range, int partitionSize) {
        java.util.Set<Long> cells = new java.util.HashSet<>();
        if (partitionSize <= 0) partitionSize = 16; 
        int gridRadius = getGridRadiusForRange(range, partitionSize);
        int baseX = pos.getX() / partitionSize;
        int baseZ = pos.getZ() / partitionSize;
        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                int cellX = baseX + dx;
                int cellZ = baseZ + dz;
                long key = (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
                cells.add(key);
            }
        }
        return cells;
    }



    private static final Map<Identifier, List<SoundRecord>> RECENT_SOUNDS_BY_DIM = new ConcurrentHashMap<>();
    private static final Map<Identifier, Map<Long, List<SoundRecord>>> SPATIAL_SOUNDS_BY_DIM = new ConcurrentHashMap<>();
    private static final Map<Identifier, WeakHashMap<RaycastCacheKey, double[]>> RAYCAST_CACHE_BY_DIM = new ConcurrentHashMap<>();

    private static synchronized List<SoundRecord> getRecentSoundsList(World world) {
        return RECENT_SOUNDS_BY_DIM.computeIfAbsent(world.getRegistryKey().getValue(), k -> Collections.synchronizedList(new ArrayList<>()));
    }

    private static synchronized Map<Long, List<SoundRecord>> getSpatialSoundMap(World world) {
        return SPATIAL_SOUNDS_BY_DIM.computeIfAbsent(world.getRegistryKey().getValue(), k -> new ConcurrentHashMap<>());
    }

    private static synchronized WeakHashMap<RaycastCacheKey, double[]> getRaycastCache(World world) {
        return RAYCAST_CACHE_BY_DIM.computeIfAbsent(world.getRegistryKey().getValue(), k -> new WeakHashMap<>());
    }



    public static synchronized List<SoundRecord> getRecentSounds(World world) {
        return new ArrayList<>(getRecentSoundsList(world));
    }
    
    private static synchronized void updateSpatialSounds(World world) {
        Map<Long, List<SoundRecord>> spatialMap = getSpatialSoundMap(world);
        spatialMap.clear();

        if (SoundAttractMod.CONFIG == null) return;
        int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
        if (partitionSize <= 0) partitionSize = 16;

        List<SoundRecord> currentSounds = getRecentSoundsList(world);

        for (SoundRecord r : currentSounds) {
            r.coveredCells.clear();
            java.util.Set<Long> cells = getCoveredCells(r.pos, r.range, partitionSize);
            r.coveredCells.addAll(cells);

            for (Long key : cells) {
                spatialMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(r);
            }
        }
    }

    public static synchronized void addSound(World world, SoundEvent se, BlockPos pos, double range, double weight, String explicitSoundId) {
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot add sound.");
            return;
        }

        String dimensionKey = world.getRegistryKey().getValue().toString();

        String soundIdToUse = explicitSoundId;
        if (soundIdToUse == null && se != null && se.getId() != null) {
            soundIdToUse = se.getId().toString();
        }
        if (soundIdToUse == null) {
            soundIdToUse = "unknown_sound_id_at_" + pos.toShortString();
            if (SoundAttractMod.CONFIG.debugLogging) {
                 SoundAttractMod.LOGGER.warn("[SoundTracker] SoundEvent or explicitSoundId resulted in null soundId. Using placeholder: {}", soundIdToUse);
            }
        }
        final String finalSoundId = soundIdToUse;


        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
            !SoundAttractMod.CONFIG.soundIdWhitelist.contains(finalSoundId) &&
            !finalSoundId.equals(com.example.soundattract.SoundMessagePayload.VOICE_CHAT_SOUND_ID.toString())) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[SoundTracker] Skipped non-whitelist sound: {} at {} (dim: {}), range={}, weight={}",
                    finalSoundId, pos, dimensionKey, range, weight
                );
            }
            return;
        }

        if (range < 0) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn(
                    "[SoundTracker] Attempted to register sound {} at {} (dim: {}) with negative range={}, skipping.",
                    finalSoundId, pos, dimensionKey, range
                );
            }
            return;
        }

        List<SoundRecord> recentSounds = getRecentSoundsList(world);
        boolean removed = recentSounds.removeIf(r ->
            r.pos.equals(pos) &&
            Objects.equals(r.soundId, finalSoundId) &&
            Objects.equals(r.dimensionKey, dimensionKey)
        );

        if (removed && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Refreshed sound (removed old instance): {} at {} (dim: {})",
                finalSoundId, pos, dimensionKey
            );
        }

        recentSounds.add(new SoundRecord(se, finalSoundId, pos, dimensionKey, range, weight));
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Registered sound {} at {} (dim: {}), range={}, weight={}, lifetime={}",
                finalSoundId, pos, dimensionKey, String.format("%.2f",range), String.format("%.2f",weight), SoundAttractMod.CONFIG.soundLifetimeTicks
            );
        }
        updateSpatialSounds(world);
    }

    public static synchronized void addSound(World world, SoundEvent se, BlockPos pos, double range, double weight) {
        addSound(world, se, pos, range, weight, null);
    }

    public static synchronized void addSound(World world, SoundEvent se, BlockPos pos) {
        if (SoundAttractMod.CONFIG == null) {
             System.err.println("[SoundTracker] Config not loaded for default lifetime sound.");
             return;
        }
        addSound(world, se, pos, 16.0, 1.0);
    }

    public static synchronized void addVirtualSound(World world, BlockPos pos, double range, double weight, UUID sourcePlayer, String animationClass) {
        if (SoundAttractMod.CONFIG == null) {
             System.err.println("[SoundTracker] Config not loaded, cannot add virtual sound.");
             return;
        }

        String dimensionKey = world.getRegistryKey().getValue().toString();

        String constructedVirtualSoundId = "virtual_sound:" + (animationClass != null ? animationClass : "player_action");
        if (sourcePlayer != null) {
            String playerPart = sourcePlayer.toString();
            constructedVirtualSoundId += ":" + (playerPart.length() > 8 ? playerPart.substring(0, 8) : playerPart);
        }
        final String finalVirtualSoundId = constructedVirtualSoundId;

        List<SoundRecord> recentSounds = getRecentSoundsList(world);
        boolean removed = recentSounds.removeIf(r ->
            r.pos.equals(pos) &&
            Objects.equals(r.soundId, finalVirtualSoundId) &&
            Objects.equals(r.dimensionKey, dimensionKey)
        );
         if (removed && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Refreshed virtual sound (removed old instance): {} at {} (dim: {})",
                finalVirtualSoundId, pos, dimensionKey
            );
        }

        recentSounds.add(new VirtualSoundRecord(pos, dimensionKey, range, weight, sourcePlayer, animationClass));
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Registered virtual sound (intended ID for refresh check: {}) at {} (dim: {}), range={}, weight={}, lifetime={}",
                finalVirtualSoundId,
                pos, dimensionKey, String.format("%.2f",range), String.format("%.2f",weight), SoundAttractMod.CONFIG.soundLifetimeTicks
            );
        }
        updateSpatialSounds(world);
    }

    public static synchronized void tick(World world) {
        List<SoundRecord> recentSounds = getRecentSoundsList(world);
        if (recentSounds.isEmpty()) return;

        boolean soundsChanged = false;
        Iterator<SoundRecord> iter = recentSounds.iterator();
        while (iter.hasNext()) {
            SoundRecord r = iter.next();
            r.ticksRemaining--;
            if (r.ticksRemaining <= 0) {
                iter.remove();
                soundsChanged = true;
            }
        }
        if (soundsChanged) {
            updateSpatialSounds(world);
        }
    }

    public static void applySoundScoreResult(MinecraftServer server, WorkerScheduler.SoundScoreResult result) {
        if (result == null) return;

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(result.getDimensionKey()));
        ServerWorld world = server.getWorld(worldKey);
        if (world == null) {
            return;
        }

        Entity entity = world.getEntity(result.getMobUuid());
        if (entity instanceof MobEntity mob) {
            if (result.getBest() != null && result.getBest().isValid()) {
                mob.getBrain().remember(SoundAttractMod.SOUND_ATTRACTION_MEMORY, result.getBest().getPosition());
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("{} is now attracted to {}", mob.getName().getString(), result.getBest().getPosition());
                }
            }
        }
    }

    public static synchronized void removeSoundAt(World world, BlockPos pos) {
        List<SoundRecord> recentSounds = getRecentSoundsList(world);
        String dimensionKey = world.getRegistryKey().getValue().toString();

        boolean removed = recentSounds.removeIf(r -> r.pos.equals(pos) && Objects.equals(r.dimensionKey, dimensionKey));
        if (removed) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                 SoundAttractMod.LOGGER.info("[SoundTracker] Removed sound(s) at pos {} in dim {}", pos, dimensionKey);
            }
            updateSpatialSounds(world);
        }
    }

    public static double[] applyBlockMuffling(World level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot apply muffling.");
            return new double[]{origRange, origWeight};
        }
        String currentSoundId = soundId != null ? soundId : "unknown_muffling_sound";

        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
            !SoundAttractMod.CONFIG.soundIdWhitelist.contains(currentSoundId) &&
            !currentSoundId.startsWith("virtual_sound:") &&
            !currentSoundId.equals(com.example.soundattract.SoundMessagePayload.VOICE_CHAT_SOUND_ID.toString())) {

        }

        WeakHashMap<RaycastCacheKey, double[]> raycastCache = getRaycastCache(level);
        RaycastCacheKey key = new RaycastCacheKey(dst, src, currentSoundId);
        double[] cached = raycastCache.get(key);
        if (cached != null) return cached;

        double range = origRange;
        double weight = origWeight;
        int x0 = src.getX(), y0 = src.getY(), z0 = src.getZ();
        int x1 = dst.getX(), y1 = dst.getY(), z1 = dst.getZ();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0), sy = Integer.compare(y1, y0), sz = Integer.compare(z1, z0);

        int nMaxSteps = dx + dy + dz + 1;
        int x = x0, y = y0, z = z0;

        int err_xy = dx - dy;
        int err_xz = dx - dz;

        int blocksMuffled = 0;
        int areaRadius = SoundAttractMod.CONFIG.mufflingAreaRadius;

        Vec3d startVec = Vec3d.ofCenter(src);
        Vec3d endVec = Vec3d.ofCenter(dst);
        Vec3d direction = endVec.subtract(startVec).normalize();
        double totalDistance = startVec.distanceTo(endVec);
        double step = 0.5;
        for (double d = 0; d <= totalDistance && range > 0 && weight > 0; d += step) {
            Vec3d currentPoint = startVec.add(direction.multiply(d));
            BlockPos currentBlockPos = BlockPos.ofFloored(currentPoint);

            boolean muffledThisStep = false;
            for (int dxr = -areaRadius; dxr <= areaRadius && !muffledThisStep; dxr++) {
                for (int dyr = -areaRadius; dyr <= areaRadius && !muffledThisStep; dyr++) {
                    for (int dzr = -areaRadius; dzr <= areaRadius && !muffledThisStep; dzr++) {
                        BlockPos posToCheck = currentBlockPos.add(dxr, dyr, dzr);
                        if (d > step && posToCheck.equals(src)) continue;
                        if (d < totalDistance - step && posToCheck.equals(dst)) continue;

                        if (!level.isChunkLoaded(posToCheck)) continue;
                        BlockState state = level.getBlockState(posToCheck);
                        Block block = state.getBlock();

                        if (block == Blocks.AIR || state.isAir()) continue;

                        if (isCustomWool(state, block) && SoundAttractMod.CONFIG.woolMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.woolBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.woolBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomThin(state, block) && SoundAttractMod.CONFIG.thinMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.thinBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.thinBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomNonSolid(state, block) && SoundAttractMod.CONFIG.nonSolidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.nonSolidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.nonSolidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomSolid(state, block) && SoundAttractMod.CONFIG.solidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.solidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.solidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (((state.getFluidState() != null && !state.getFluidState().isEmpty()) || isCustomLiquid(block))
                                   && SoundAttractMod.CONFIG.liquidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.liquidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.liquidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        }
                    }
                }
            }
        }

        if (range < 0) range = 0;
        if (weight < 0) weight = 0;

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[BlockMuffling] soundId={}, src={}, dst={}, origRange={}, origWeight={}, muffledRange={}, muffledWeight={}, blocksMuffled={}",
                currentSoundId, src, dst, String.format("%.2f",origRange), String.format("%.2f",origWeight), String.format("%.2f",range), String.format("%.2f",weight), blocksMuffled
            );
        }
        double[] result = new double[]{range, weight};
        if (blocksMuffled > 0 || origRange == 0) {
             raycastCache.put(key, result);
        }
        return result;
    }

    public static synchronized void pruneIrrelevantSounds(World level) {
        List<SoundRecord> recentSounds = getRecentSoundsList(level);
        if (recentSounds.isEmpty() || SoundAttractMod.CONFIG == null) return;



        int pruneIndex = 0;
        final int SOUNDS_PRUNED_PER_TICK = 1;

        int total = recentSounds.size();
        if (pruneIndex >= total) pruneIndex = 0;

        int prunedThisTick = 0;
        int checkedThisTick = 0;

        List<SoundRecord> soundsToPrune = new ArrayList<>();

        for (int i = 0; i < total && checkedThisTick < total && prunedThisTick < SOUNDS_PRUNED_PER_TICK; i++) {
            int currentIndex = (pruneIndex + i) % total;
            if (currentIndex >= recentSounds.size()) continue;
            SoundRecord sound = recentSounds.get(currentIndex);
            checkedThisTick++;

            if (!Objects.equals(sound.dimensionKey, level.getRegistryKey().getValue().toString())) continue;
            if (sound.ticksRemaining > SoundAttractMod.CONFIG.scanCooldownTicks) continue;

            boolean isRelevantToAnyMob = false;
            for (Long cellKey : sound.coveredCells) {

                int cellX = (int) (cellKey >> 32);
                int cellZ = (int) (cellKey & 0xFFFFFFFFL);
                int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
                if (partitionSize <= 0) partitionSize = 16;

                net.minecraft.util.math.Box checkBox = new net.minecraft.util.math.Box(
                    cellX * partitionSize, level.getBottomY(), cellZ * partitionSize,
                    (cellX + 1) * partitionSize, level.getTopY(), (cellZ + 1) * partitionSize
                );

                List<MobEntity> mobsInCell = level.getEntitiesByClass(MobEntity.class, checkBox, m -> m.isAlive());

                for (MobEntity mob : mobsInCell) {
                    if (!SoundAttractMod.CONFIG.attractedEntities.contains(Registries.ENTITY_TYPE.getId(mob.getType()).toString())) {
                        continue;
                    }
                    MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);
                    double effectiveRange = sound.range;
                    double effectiveWeight = sound.weight;
                    if (profile != null && sound.soundId != null) {
                        Identifier rl = Identifier.tryParse(sound.soundId);
                        if (rl != null) {
                            Optional<com.example.soundattract.config.SoundOverride> ov = profile.getSoundOverride(rl);
                            if (ov.isPresent()) {
                                effectiveRange = ov.get().getRange();
                                effectiveWeight = ov.get().getWeight();
                            }
                        }
                    }
                    double[] muffled = applyBlockMuffling(level, sound.pos, mob.getBlockPos(), effectiveRange, effectiveWeight, sound.soundId);
                    double finalRange = muffled[0];
                    if (mob.getBlockPos().getSquaredDistance(sound.pos) <= finalRange * finalRange) {
                        isRelevantToAnyMob = true;
                        break;
                    }
                }
                if (isRelevantToAnyMob) break;
            }

            if (!isRelevantToAnyMob) {
                soundsToPrune.add(sound);
                prunedThisTick++;
            }
        }

        if (!soundsToPrune.isEmpty()) {
            recentSounds.removeAll(soundsToPrune);
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[SoundTracker] Pruned {} irrelevant sounds from {}.", soundsToPrune.size(), level.getRegistryKey().getValue());
            }
            updateSpatialSounds(level);
        }
    }


    public static synchronized SoundRecord findNearestSound(
            World level,
            MobEntity mob,
            BlockPos mobPos,
            Vec3d mobEyePos
    ) {
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot find nearest sound.");
            return null;
        }

        List<SoundRecord> currentSoundsSnapshot = getRecentSounds(level);
        if (currentSoundsSnapshot.isEmpty()) {
            return null;
        }
        
        String dimensionKey = level.getRegistryKey().getValue().toString();

        MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);
        SoundRecord bestSound = null;
        double highestComparisonWeight = -1.0;
        double closestDistSqrForBest = Double.MAX_VALUE;

        double noveltyBonusValue = SoundAttractMod.CONFIG.soundNoveltyBonusWeight;
        int noveltyTicks = SoundAttractMod.CONFIG.soundNoveltyTimeTicks;
        int maxLifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;

        for (SoundRecord r : currentSoundsSnapshot) {
            if (r == null || r.pos == null || !Objects.equals(r.dimensionKey, dimensionKey)) {
                continue;
            }

            String soundId = r.soundId != null ? r.soundId : "unknown_sound_in_recent_list";

            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
                !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId) &&
                !soundId.startsWith("virtual_sound:") &&
                !soundId.equals(com.example.soundattract.SoundMessagePayload.VOICE_CHAT_SOUND_ID.toString())) {
                continue;
            }

            double effectiveInitialRange = r.range;
            double effectiveInitialWeight = r.weight;
            if (profile != null) {
                Identifier rl = Identifier.tryParse(soundId);
                if (rl != null) {
                    Optional<com.example.soundattract.config.SoundOverride> ov = profile.getSoundOverride(rl);
                    if (ov.isPresent()) {
                        effectiveInitialRange = ov.get().getRange();
                        effectiveInitialWeight = ov.get().getWeight();
                    }
                }
            }

            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, effectiveInitialRange, effectiveInitialWeight, soundId);
            double muffledRange = muffled[0];
            double muffledWeight = muffled[1];
            double distSqr = mobPos.getSquaredDistance(r.pos);

            if (muffledWeight <= 0 || muffledRange <= 0 || distSqr > (muffledRange * muffledRange)) {
                continue;
            }

            double noveltyBonus = 0.0;
            if (noveltyBonusValue > 0 && r.ticksRemaining > (maxLifetime - noveltyTicks)) {
                noveltyBonus = noveltyBonusValue;
            }

            double finalComparisonWeight = muffledWeight + noveltyBonus;

            if (finalComparisonWeight > highestComparisonWeight || (Math.abs(finalComparisonWeight - highestComparisonWeight) < 0.001 && distSqr < closestDistSqrForBest)) {
                highestComparisonWeight = finalComparisonWeight;
                closestDistSqrForBest = distSqr;
                bestSound = new SoundRecord(r.sound, soundId, r.pos, r.dimensionKey, muffledRange, muffledWeight);
                bestSound.ticksRemaining = r.ticksRemaining;
            }
        }

        return bestSound;
    }
    



    public static java.util.List<MobEntity> getMobsForSound(
            java.util.List<MobEntity> mobsToFilter,
            SoundRecord sound,
            java.util.function.Predicate<MobEntity> filter
    ) {
        java.util.List<MobEntity> eligibleMobs = new java.util.ArrayList<>();
        if (sound == null || mobsToFilter == null) {
            return eligibleMobs;
        }

        for (MobEntity mob : mobsToFilter) {
            if (mob == null || !mob.isAlive()) {
                continue;
            }
            boolean inRange = mob.getBlockPos().getSquaredDistance(sound.pos) <= sound.range * sound.range;
            if (inRange && filter.test(mob)) {
                eligibleMobs.add(mob);
            }
        }
        return eligibleMobs;
    }
    
    private static boolean isBlockInConfigList(BlockState state, Block block, java.util.List<String> configList) {
        if (configList == null || configList.isEmpty()) return false;
        Identifier id = Registries.BLOCK.getId(block);
        if (id != null && configList.contains(id.toString())) return true;
        for (String entry : configList) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                TagKey<Block> tag = TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, Identifier.of(tagName));
                if (state.isIn(tag)) return true;
            }
        }
        return false;
    }
    private static boolean isCustomWool(BlockState state, Block block) {
        if (SoundAttractMod.CONFIG == null) return state.isIn(BlockTags.WOOL);
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customWoolBlocks) || state.isIn(BlockTags.WOOL);
    }
    private static boolean isCustomSolid(BlockState state, Block block) {
         if (SoundAttractMod.CONFIG == null) return state.isSolid();
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customSolidBlocks) || state.isSolid();
    }
    private static boolean isCustomNonSolid(BlockState state, Block block) {
         if (SoundAttractMod.CONFIG == null) return !state.isSolid();
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customNonSolidBlocks) || !state.isSolid();
    }
    private static boolean isCustomThin(BlockState state, Block block) {
        if (SoundAttractMod.CONFIG == null) return false;
        if (isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customThinBlocks)) return true;
        
        Identifier id = Registries.BLOCK.getId(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") ||
               path.contains("fence") || path.contains("trapdoor") || path.contains("door") ||
               path.contains("ladder") || path.contains("scaffolding") || path.contains("rail") ||
               path.contains("chain");
    }
    private static boolean isCustomLiquid(Block block) {
        if (SoundAttractMod.CONFIG == null) return false;
        String blockId = Registries.BLOCK.getId(block).toString();
        return SoundAttractMod.CONFIG.customLiquidBlocks.contains(blockId);
    }
}