package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CsGrenadesTracker {

    private static final Map<UUID, SmokeEntry> ACTIVE_SMOKE = new ConcurrentHashMap<>();
    private static final Map<UUID, FireEntry> ACTIVE_FIRE = new ConcurrentHashMap<>();
    private static volatile List<SmokeEntry> SMOKE_SNAPSHOT = new CopyOnWriteArrayList<>();
    private static volatile List<FireEntry> FIRE_SNAPSHOT = new CopyOnWriteArrayList<>();

    private static Method GET_GRENADE_TYPE_METHOD;
    private static Class<?> GRENADE_ENTITY_CLASS;
    private static boolean REFLECTION_INITIALIZED = false;

    private record SmokeEntry(Vec3 center, double radius, long tickCreated) {}
    private record FireEntry(Vec3 center, double dangerRadius, long tickCreated) {}

    private static void ensureReflection() {
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        if (REFLECTION_INITIALIZED) {
            return;
        }
        try {
            GRENADE_ENTITY_CLASS = Class.forName("club.pisquad.minecraft.csgrenades.entity.CounterStrikeGrenadeEntity");
            GET_GRENADE_TYPE_METHOD = GRENADE_ENTITY_CLASS.getMethod("getGrenadeType");
            REFLECTION_INITIALIZED = true;
            SoundAttractMod.LOGGER.info("[CSGrenades] Successfully initialized grenade entity reflection");
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("[CSGrenades] Failed to initialize grenade entity reflection: {} (type: {})", e.getMessage(), e.getClass().getSimpleName());
        }
    }

    private static Class<?> THROWABLE_ITEM_PROJECTILE_CLASS;

    @SuppressWarnings("unchecked")
    public static void scanAndRebuild(Level level, long currentTick, int smokeMaxAgeTicks) {
        if (!CsGrenadesCompat.isLoaded()) {
            return;
        }
        ensureReflection();
        if (!REFLECTION_INITIALIZED || GRENADE_ENTITY_CLASS == null) {
            SoundAttractMod.LOGGER.warn("[CSGrenades] Cannot scan: reflection not initialized");
            return;
        }

        ACTIVE_SMOKE.clear();
        ACTIVE_FIRE.clear();

        try {
            if (THROWABLE_ITEM_PROJECTILE_CLASS == null) {
                THROWABLE_ITEM_PROJECTILE_CLASS = Class.forName("net.minecraft.world.entity.projectile.ThrowableItemProjectile");
            }
        } catch (Exception ignored) {}

        try {
            List<Entity> grenades = new ArrayList<>();
            java.util.Set<String> foundClasses = new java.util.HashSet<>();
            int throwableCount = 0;
            for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(-30000000, -30000000, -30000000, 30000000, 30000000, 30000000))) {
                String className = entity.getClass().getName();
                foundClasses.add(className);
                if (className.contains("grenade") || className.contains("Grenade") ||
                    className.contains("csgrenade") || className.contains("pisquad")) {
                    SoundAttractMod.LOGGER.debug("[CSGrenades] Found potential grenade: {} alive={}", className, entity.isAlive());
                }
                if (THROWABLE_ITEM_PROJECTILE_CLASS != null && THROWABLE_ITEM_PROJECTILE_CLASS.isInstance(entity)) {
                    throwableCount++;
                }
                if (GRENADE_ENTITY_CLASS.isInstance(entity) && entity.isAlive()) {
                    grenades.add(entity);
                    SoundAttractMod.LOGGER.debug("[CSGrenades] Found grenade entity: {} type={}", className, entity.getType().getDescriptionId());
                }
            }
            SoundAttractMod.LOGGER.debug("[CSGrenades] All entity classes in level: {}", foundClasses);
            SoundAttractMod.LOGGER.debug("[CSGrenades] ThrowableItemProjectile count: {}", throwableCount);
            SoundAttractMod.LOGGER.debug("[CSGrenades] GRENADE_ENTITY_CLASS = {}", GRENADE_ENTITY_CLASS != null ? GRENADE_ENTITY_CLASS.getName() : "null");
            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker scan found {} grenades in level", grenades.size());

            for (Entity entity : grenades) {
                UUID id = entity.getUUID();
                Vec3 center = entity.position();
                String simpleName = entity.getClass().getSimpleName();

                switch (simpleName) {
                    case "SmokeGrenadeEntity" -> {
                        double smokeRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.smokeCloudRadius.get();
                        ACTIVE_SMOKE.put(id, new SmokeEntry(center, smokeRadius, currentTick));
                        SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: smoke grenade at {} (radius {})", center, String.format("%.2f", smokeRadius));
                    }
                    case "MolotovEntity", "IncendiaryEntity" -> {
                        double dangerRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
                        ACTIVE_FIRE.put(id, new FireEntry(center, dangerRadius, currentTick));
                        SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: fire grenade at {} (radius {})", center, String.format("%.2f", dangerRadius));
                    }
                    case "FlashBangEntity", "HEGrenadeEntity", "DecoyGrenadeEntity" -> {
                    }
                    default -> {
                        SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: unhandled grenade subclass '{}'", simpleName);
                    }
                }
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("[CSGrenades] Error scanning CS Grenades: {}", e.getMessage(), e);
        }

        SMOKE_SNAPSHOT = new CopyOnWriteArrayList<>(ACTIVE_SMOKE.values());
        FIRE_SNAPSHOT = new CopyOnWriteArrayList<>(ACTIVE_FIRE.values());
        SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker scan complete: {} smoke, {} fire entries", SMOKE_SNAPSHOT.size(), FIRE_SNAPSHOT.size());
    }

    public static boolean smokeBlocksRay(Vec3 from, Vec3 to, int smokeMaxAgeTicks, long currentTick) {
        if (SMOKE_SNAPSHOT.isEmpty()) {
            return false;
        }
        double smokeRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.smokeCloudRadius.get();
        for (SmokeEntry smoke : SMOKE_SNAPSHOT) {
            if ((currentTick - smoke.tickCreated) > smokeMaxAgeTicks) {
                continue;
            }
            if (SmokeLosSuppression.blocksRay(from, to, smoke.center, smokeRadius)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Smoke blocked LOS: from {} to {}", from, to);
                return true;
            }
        }
        return false;
    }

    public static Vec3 nearestActiveFire(Vec3 mobPos, int fireMaxAgeTicks, long currentTick) {
        if (FIRE_SNAPSHOT.isEmpty()) {
            return null;
        }
        Vec3 nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (FireEntry fire : FIRE_SNAPSHOT) {
            if ((currentTick - fire.tickCreated) > fireMaxAgeTicks) {
                continue;
            }
            double distSq = mobPos.distanceToSqr(fire.center);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = fire.center;
            }
        }
        return nearest;
    }

    public static void clear() {
        ACTIVE_SMOKE.clear();
        ACTIVE_FIRE.clear();
        SMOKE_SNAPSHOT = new CopyOnWriteArrayList<>();
        FIRE_SNAPSHOT = new CopyOnWriteArrayList<>();
    }
}
