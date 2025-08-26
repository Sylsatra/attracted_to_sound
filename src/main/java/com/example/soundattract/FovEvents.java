package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import com.mojang.logging.LogUtils; 
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FovEvents {
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final double BACKSTAB_DAMAGE_MULTIPLIER = 1.2;

    private record FovData(double horizontal, double vertical) {}
    private static final FovData DEFAULT_FOV = new FovData(200.0, 135.0);

    private static final Set<EntityType<?>> DEVELOPER_EXCLUSIONS = Set.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static Map<ResourceLocation, FovData> CONFIG_FOV_CACHE = null;
    private static Set<ResourceLocation> USER_EXCLUSION_CACHE = null;

    private static void buildCaches() {
        USER_EXCLUSION_CACHE = new HashSet<>();
        List<? extends String> exclusionList = SoundAttractConfig.COMMON.fovExclusionList.get();
        for (String entry : exclusionList) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
                if (loc != null) {
                    USER_EXCLUSION_CACHE.add(loc);
                } else {
                    LOGGER.warn("[FOV Config] Malformed exclusion entry, skipping: " + entry);
                }
            } catch (Exception e) {
                LOGGER.error("[FOV Config] Failed to parse exclusion entry: " + entry, e);
            }
        }
        LOGGER.info("[FOV Config] Loaded {} user-defined exclusions.", USER_EXCLUSION_CACHE.size());

        CONFIG_FOV_CACHE = new HashMap<>();
        List<? extends String> overrideList = SoundAttractConfig.COMMON.fovOverrides.get();
        for (String entry : overrideList) {
            try {
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    LOGGER.warn("[FOV Config] Malformed FOV override, skipping: " + entry);
                    continue;
                }
                ResourceLocation mobId = ResourceLocation.tryParse(parts[0].trim());
                if (mobId == null) {
                    LOGGER.warn("[FOV Config] Malformed mob identifier in override, skipping: " + entry);
                    continue;
                }
                double h = Double.parseDouble(parts[1].trim());
                double v = Double.parseDouble(parts[2].trim());
                CONFIG_FOV_CACHE.put(mobId, new FovData(h, v));
            } catch (Exception e) {
                LOGGER.error("[FOV Config] Failed to parse FOV override entry: " + entry, e);
            }
        }
        LOGGER.info("[FOV Config] Loaded {} custom FOV overrides.", CONFIG_FOV_CACHE.size());
    }

    @SubscribeEvent
    public void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getVisibilityModifier() <= 0) return;
        if (!(event.getEntity() instanceof Mob looker)) return;
        Entity target = event.getLookingEntity();
        if (target == null) return;

        if (!isTargetInFov(looker, target, false)) {
            event.modifyVisibility(0.0);
        }
    }

    @SubscribeEvent
    public void onMobHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        Entity attacker = event.getSource().getDirectEntity(); 
        if (attacker == null || attacker == mob) return;

        if (!isTargetInFov(mob, attacker, true)) {
            float originalDamage = event.getOriginalDamage();
            float newDamage = (float) (originalDamage * BACKSTAB_DAMAGE_MULTIPLIER);
            event.setNewDamage(newDamage);

            if (attacker instanceof Player) {
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, mob.getSoundSource(), 1.0F, 1.2F);
            }
        }
    }

    public static boolean isTargetInFov(Mob looker, Entity target, boolean checkObstructions) {
        if (USER_EXCLUSION_CACHE == null) {
            buildCaches();
        }

        ResourceLocation lookerId = EntityType.getKey(looker.getType());

        if (DEVELOPER_EXCLUSIONS.contains(looker.getType())) return true;
        if (USER_EXCLUSION_CACHE.contains(lookerId)) return true;

        FovData fov = CONFIG_FOV_CACHE.getOrDefault(lookerId, DEFAULT_FOV);
        if (fov.horizontal() >= 360) return true;

        if (checkObstructions && !hasLineOfSightAllowlist(looker, target)) {
            return false;
        }

        return isWithinFieldOfView(looker, target, fov.horizontal(), fov.vertical());
    }

    /**
     * Custom LOS that ignores blocks listed in SoundAttractConfig.VISION_PASSTHROUGH_BLOCKS_CACHE.
     * Uses precise ray casting against block collision shapes so partial blocks (slabs, stairs, fences)
     * only occlude when actually intersected. Falls back to vanilla LOS on error.
     */
    private static boolean hasLineOfSightAllowlist(Mob looker, Entity target) {
        try {
            Level level = looker.level();
            Vec3 start = looker.getEyePosition();
            Vec3 end = target.getEyePosition();


            for (int i = 0; i < 64; i++) {
                net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                        start, end,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        looker
                );
                net.minecraft.world.phys.HitResult hit = level.clip(ctx);
                if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                    return true;
                }

                if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                    BlockPos pos = bhr.getBlockPos();
                    BlockState state = level.getBlockState(pos);
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    boolean passThrough = blockId != null && SoundAttractConfig.VISION_PASSTHROUGH_BLOCKS_CACHE.contains(blockId);
                    if (passThrough) {

                        Vec3 hp = bhr.getLocation();
                        Vec3 nudge = new Vec3(bhr.getDirection().getStepX(), bhr.getDirection().getStepY(), bhr.getDirection().getStepZ()).scale(1.0e-4);
                        start = hp.add(nudge);
                        if (start.distanceTo(end) < 1.0e-4) return true;
                        continue;
                    }
                    return false;
                }


                return true;
            }
            return true;
        } catch (Exception e) {
            try { return looker.getSensing().hasLineOfSight(target); } catch (Exception ignored) {}
            return true;
        }
    }

    private static boolean isWithinFieldOfView(Mob looker, Entity target, double horizontalFovDegrees, double verticalFovDegrees) {
        Vec3 lookVector = looker.getLookAngle();
        Vec3 toTargetVector = target.position()
                                    .add(0, target.getEyeHeight() / 2.0, 0)
                                    .subtract(looker.getEyePosition())
                                    .normalize();
        
        Vec3 lookHorizontal = new Vec3(lookVector.x, 0, lookVector.z).normalize();
        Vec3 targetHorizontal = new Vec3(toTargetVector.x, 0, toTargetVector.z).normalize();
        double dotHorizontal = lookHorizontal.dot(targetHorizontal);
        double angleHorizontal = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dotHorizontal))));
        if (angleHorizontal > horizontalFovDegrees / 2.0) return false;

        double pitchLook = Math.toDegrees(Math.asin(lookVector.y));
        double pitchTarget = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTargetVector.y))));
        double angleVertical = Math.abs(pitchTarget - pitchLook);
        return angleVertical <= verticalFovDegrees / 2.0;
    }
}