package com.example.soundattract;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FovEvents {

    private static final double BACKSTAB_DAMAGE_MULTIPLIER = 1.2;

    private record FovData(double horizontal, double vertical) {}
    private static final FovData DEFAULT_FOV = new FovData(200.0, 135.0);

    private static final Set<EntityType<?>> DEVELOPER_EXCLUSIONS = Set.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static Map<Identifier, FovData> CONFIG_FOV_CACHE = null;
    private static Set<Identifier> USER_EXCLUSION_CACHE = null;

    public static void buildCaches() {
        if (SoundAttractMod.CONFIG == null) {
            SoundAttractMod.LOGGER.warn("[FovEvents] Attempted to build caches before config was loaded!");
            return;
        }

        USER_EXCLUSION_CACHE = new HashSet<>();
        List<? extends String> exclusionList = SoundAttractMod.CONFIG.fovExclusionList;
        for (String entry : exclusionList) {
            try {
                Identifier id = Identifier.tryParse(entry.trim());
                if (id != null) {
                    USER_EXCLUSION_CACHE.add(id);
                } else {
                    SoundAttractMod.LOGGER.warn("[FOV Config] Failed to parse exclusion entry: " + entry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.error("[FOV Config] Error processing exclusion entry: " + entry, e);
            }
        }
        SoundAttractMod.LOGGER.info("[FOV Config] Loaded {} user-defined exclusions.", USER_EXCLUSION_CACHE.size());

        CONFIG_FOV_CACHE = new HashMap<>();
        List<? extends String> overrideList = SoundAttractMod.CONFIG.fovOverrides;
        for (String entry : overrideList) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    SoundAttractMod.LOGGER.warn("[FOV Config] Malformed FOV override, skipping: " + entry);
                    continue;
                }
                Identifier mobId = Identifier.tryParse(parts[0].trim());
                double h = Double.parseDouble(parts[1].trim());
                double v = Double.parseDouble(parts[2].trim());

                if (mobId != null) {
                    CONFIG_FOV_CACHE.put(mobId, new FovData(h, v));
                } else {
                     SoundAttractMod.LOGGER.warn("[FOV Config] Malformed mob identifier in override, skipping: " + entry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.error("[FOV Config] Failed to parse FOV override entry: " + entry, e);
            }
        }
        SoundAttractMod.LOGGER.info("[FOV Config] Loaded {} custom FOV overrides.", CONFIG_FOV_CACHE.size());
    }
    public static float getModifiedDamage(LivingEntity target, DamageSource source, float amount) {
        if (!(target instanceof MobEntity mob)) return amount;

        net.minecraft.entity.Entity attacker = source.getAttacker();
        if (attacker == null || attacker == mob) return amount;

        if (!isTargetInFov(mob, attacker, true)) {
            float newDamage = (float) (amount * BACKSTAB_DAMAGE_MULTIPLIER);

            if (attacker instanceof PlayerEntity) {
                mob.getWorld().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, mob.getSoundCategory(), 1.0F, 1.2F);
            }
            return newDamage;
        }

        return amount;
    }

    public static boolean isTargetInFov(MobEntity looker, net.minecraft.entity.Entity target, boolean checkObstructions) {
        if (USER_EXCLUSION_CACHE == null) {
            buildCaches();
        }

        Identifier lookerId = Registries.ENTITY_TYPE.getId(looker.getType());

        if (DEVELOPER_EXCLUSIONS.contains(looker.getType())) return true;
        if (USER_EXCLUSION_CACHE.contains(lookerId)) return true;

        FovData fov = CONFIG_FOV_CACHE.getOrDefault(lookerId, DEFAULT_FOV);
        if (fov.horizontal() >= 360) return true;

        if (checkObstructions && !looker.getVisibilityCache().canSee(target)) {
            return false;
        }

        return isWithinFieldOfView(looker, target, fov.horizontal(), fov.vertical());
    }

    private static boolean isWithinFieldOfView(MobEntity looker, net.minecraft.entity.Entity target, double horizontalFovDegrees, double verticalFovDegrees) {
        Vec3d lookVector = looker.getRotationVector(); 
        Vec3d toTargetVector = target.getPos()
                                    .add(0, target.getEyeHeight(target.getPose()) / 2.0, 0) 
                                    .subtract(looker.getEyePos())
                                    .normalize();

        Vec3d lookHorizontal = new Vec3d(lookVector.x, 0, lookVector.z).normalize();
        Vec3d targetHorizontal = new Vec3d(toTargetVector.x, 0, toTargetVector.z).normalize();

        double dotHorizontal = lookHorizontal.dotProduct(targetHorizontal);
        double angleHorizontal = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dotHorizontal))));

        if (angleHorizontal > horizontalFovDegrees / 2.0) {
            return false;
        }

        double pitchLook = Math.toDegrees(Math.asin(lookVector.y));
        double pitchTarget = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTargetVector.y))));
        double angleVertical = Math.abs(pitchTarget - pitchLook);
        
        return angleVertical <= verticalFovDegrees / 2.0;
    }
}