package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.los.OptimizedLOS;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class CsGrenadesEventHandler {

    private static final Object LOCK = new Object();
    private static long lastScanTick = -1;
    private static final Map<Mob, Long> FLEE_COOLDOWNS = new WeakHashMap<>();
    private static final Set<UUID> PROCESSED_FLASHBANGS = new HashSet<>();
    private static final Set<Entity> TRACKED_GRENADES = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    private static Class<?> GRENADE_ENTITY_CLASS;
    private static Method GET_GRENADE_TYPE_METHOD;
    private static java.lang.reflect.Field ENTITY_DATA_FIELD;
    private static Method GET_IS_EXPLODED_ACCESSOR_METHOD;
    private static boolean FLASHBANG_REFLECTION_INITIALIZED = false;
    private static boolean FLASHBANG_REFLECTION_FAILED = false;

    private static void ensureFlashbangReflection() {
        if (FLASHBANG_REFLECTION_INITIALIZED || FLASHBANG_REFLECTION_FAILED) {
            return;
        }
        try {
            GRENADE_ENTITY_CLASS = Class.forName("club.pisquad.minecraft.csgrenades.entity.CounterStrikeGrenadeEntity");
            GET_GRENADE_TYPE_METHOD = GRENADE_ENTITY_CLASS.getMethod("getGrenadeType");
            for (java.lang.reflect.Field field : Entity.class.getDeclaredFields()) {
                if (field.getType().getName().contains("SynchedEntityData")) {
                    ENTITY_DATA_FIELD = field;
                    ENTITY_DATA_FIELD.setAccessible(true);
                    SoundAttractMod.LOGGER.debug("[CSGrenades] Found entityData field: {} (type: {})", field.getName(), field.getType().getName());
                    break;
                }
            }
            if (ENTITY_DATA_FIELD == null) {
                throw new NoSuchFieldException("Could not find SynchedEntityData field in Entity class");
            }
            Object companion = GRENADE_ENTITY_CLASS.getField("Companion").get(null);
            GET_IS_EXPLODED_ACCESSOR_METHOD = companion.getClass().getMethod("isExplodedAccessor");
            FLASHBANG_REFLECTION_INITIALIZED = true;
            SoundAttractMod.LOGGER.debug("[CSGrenades] Flashbang reflection initialized successfully");
        } catch (Exception e) {
            FLASHBANG_REFLECTION_FAILED = true;
            SoundAttractMod.LOGGER.warn("[CSGrenades] Failed to initialize flashbang reflection: {} - will not retry", e.getMessage());
        }
    }

    private static boolean isGrenadeExploded(Entity grenade) {
        if (!FLASHBANG_REFLECTION_INITIALIZED) {
            return false;
        }
        try {
            Object companion = GRENADE_ENTITY_CLASS.getField("Companion").get(null);
            Object accessor = GET_IS_EXPLODED_ACCESSOR_METHOD.invoke(companion);
            Object entityData = ENTITY_DATA_FIELD.get(grenade);
            Method getMethod = entityData.getClass().getMethod("get", EntityDataAccessor.class);
            Object isExploded = getMethod.invoke(entityData, accessor);
            return Boolean.TRUE.equals(isExploded);
        } catch (Exception e) {
            return false;
        }
    }

    public static void registerGrenadeEvent(Object eventBus) {
        SoundAttractMod.LOGGER.debug("[CSGrenades] Event-based registration skipped - using tick-based scanning instead");
    }

    private static void processExplodedFlashbang(Entity grenade, ServerLevel level) {
        if (!SoundAttractConfig.COMMON.enableFlashbangBlinding.get()) {
            return;
        }

        Vec3 flashPos = grenade.position();
        double radius = SoundAttractConfig.COMMON.flashbangBlindRadius.get();
        double radiusSq = radius * radius;
        int baseDuration = SoundAttractConfig.COMMON.flashbangBlindDurationTicks.get();
        boolean requireLos = SoundAttractConfig.COMMON.flashbangRequireLineOfSight.get();
        boolean requireFov = SoundAttractConfig.COMMON.flashbangRequireFov.get();
        int mobBudget = SoundAttractConfig.COMMON.flashbangMobScanBudget.get();
        long currentTick = level.getGameTime();

        int scanned = 0;
        int blinded = 0;
        var mobsInRange = level.getEntitiesOfClass(Mob.class, grenade.getBoundingBox().inflate(radius));
        SoundAttractMod.LOGGER.debug("[CSGrenades] Processing flashbang - found {} mobs in radius {}", mobsInRange.size(), radius);
        for (Mob mob : mobsInRange) {
            if (scanned++ >= mobBudget) {
                break;
            }
            Vec3 mobEye = mob.getEyePosition();
            double distSq = mobEye.distanceToSqr(flashPos);
            if (distSq > radiusSq) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            double factor = 1.0 - (dist / radius);
            if (factor <= 0) {
                continue;
            }

            if (requireLos && !OptimizedLOS.hasLineOfSight(level, mobEye, flashPos, mob)) {
                continue;
            }
            if (requireFov && !FovEvents.isTargetInFov(mob, grenade, false)) {
                continue;
            }

            long duration = (long) (baseDuration * factor);
            FlashbangEffect.blind(mob, duration, currentTick);
            blinded++;
            SoundAttractMod.LOGGER.debug("[CSGrenades] Blinded mob {} for {} ticks", mob.getUUID(), duration);
        }
        if (blinded > 0) {
            SoundAttractMod.LOGGER.debug("[CSGrenades] Flashbang blinded {} mobs", blinded);
        }
    }

    private static void processTrackedGrenades(ServerLevel level) {
        if (!CsGrenadesCompat.isLoaded() || !SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return;
        }
        ensureFlashbangReflection();
        if (!FLASHBANG_REFLECTION_INITIALIZED) {
            return;
        }

        for (Entity grenade : new HashSet<>(TRACKED_GRENADES)) {
            if (!grenade.isAlive() || grenade.level() != level) {
                TRACKED_GRENADES.remove(grenade);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return;
        }

        long currentTick = level.getGameTime();
        int scanInterval = SoundAttractConfig.COMMON.csgrenadesTrackerScanIntervalTicks.get();

        synchronized (LOCK) {
            if (currentTick - lastScanTick >= scanInterval) {
                int smokeMaxAge = SoundAttractConfig.COMMON.smokeLifetimeMaxTicks.get();
                CsGrenadesTracker.scanAndRebuild(level, currentTick, smokeMaxAge);
                FlashbangEffect.cleanup(currentTick);
                lastScanTick = currentTick;
            }
        }

        processTrackedGrenades(level);
        cleanupFleeCooldowns(currentTick);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        Entity entity = event.getEntity();

        ensureFlashbangReflection();
        SoundAttractMod.LOGGER.debug("[CSGrenades] Entity joined: {} (class: {}, isGrenadeClass={})",
                entity.getUUID(), entity.getClass().getName(),
                GRENADE_ENTITY_CLASS != null && GRENADE_ENTITY_CLASS.isInstance(entity));
        if (GRENADE_ENTITY_CLASS != null && GRENADE_ENTITY_CLASS.isInstance(entity)) {
            TRACKED_GRENADES.add(entity);
            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracking grenade entity: {} (type={})", entity.getUUID(), entity.getType().getDescriptionId());
            return;
        }

        if (!(entity instanceof PathfinderMob mob)) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableFireFlee.get()) {
            return;
        }
        if (mob.fireImmune()) {
            return;
        }

        Set<String> eligible = SoundAttractConfig.FLEE_FROM_FIRE_ELIGIBLE_SET;
        String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        if (!eligible.contains(entityId)) {
            return;
        }

        boolean hasGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wg -> wg.getGoal() instanceof FleeFromFireGoal);
        if (!hasGoal) {
            mob.goalSelector.addGoal(1, new FleeFromFireGoal(mob));
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        TRACKED_GRENADES.remove(entity);
        if (entity instanceof Mob mob) {
            FLEE_COOLDOWNS.remove(mob);
        }

        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                || !SoundAttractConfig.COMMON.enableFlashbangBlinding.get()) {
            return;
        }
        if (!"FlashBangEntity".equals(entity.getClass().getSimpleName())) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel sLevel)) {
            return;
        }
        UUID id = entity.getUUID();
        if (!PROCESSED_FLASHBANGS.add(id)) {
            return;
        }
        processExplodedFlashbang(entity, sLevel);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CsGrenadesTracker.clear();
        FlashbangEffect.clear();
        FLEE_COOLDOWNS.clear();
        PROCESSED_FLASHBANGS.clear();
        TRACKED_GRENADES.clear();
    }

    public static boolean isOnFleeCooldown(Mob mob, long currentTick) {
        Long cooldown = FLEE_COOLDOWNS.get(mob);
        if (cooldown == null) {
            return false;
        }
        return currentTick < cooldown;
    }

    public static void setFleeCooldown(Mob mob, long currentTick) {
        int cooldownTicks = SoundAttractConfig.COMMON.fireFleeCooldownTicks.get();
        FLEE_COOLDOWNS.put(mob, currentTick + cooldownTicks);
    }

    private static void cleanupFleeCooldowns(long currentTick) {
        Iterator<Map.Entry<Mob, Long>> it = FLEE_COOLDOWNS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, Long> e = it.next();
            if (currentTick >= e.getValue()) {
                it.remove();
            }
        }
    }
}
