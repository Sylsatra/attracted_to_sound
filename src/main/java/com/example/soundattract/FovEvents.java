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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.IceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.tag.BlockTags;

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
    private static Set<Identifier> NON_BLOCKING_VISION_ALLOW = null;

    public static void buildCaches() {
        if (SoundAttractMod.CONFIG == null) {
            SoundAttractMod.LOGGER.warn("[FovEvents] Attempted to build caches before config was loaded!");
            return;
        }

        USER_EXCLUSION_CACHE = new HashSet<>();
        List<? extends String> exclusionList = SoundAttractMod.CONFIG.fovExclusionList != null
                ? SoundAttractMod.CONFIG.fovExclusionList
                : java.util.Collections.emptyList();
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
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[FOV Config] Loaded {} user-defined exclusions.", USER_EXCLUSION_CACHE.size());
        }

        CONFIG_FOV_CACHE = new HashMap<>();
        List<? extends String> overrideList = SoundAttractMod.CONFIG.fovOverrides != null
                ? SoundAttractMod.CONFIG.fovOverrides
                : java.util.Collections.emptyList();
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
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[FOV Config] Loaded {} custom FOV overrides.", CONFIG_FOV_CACHE.size());
        }
        NON_BLOCKING_VISION_ALLOW = new HashSet<>();
        List<? extends String> allowList = SoundAttractMod.CONFIG.nonBlockingVisionAllowList != null
                ? SoundAttractMod.CONFIG.nonBlockingVisionAllowList
                : java.util.Collections.emptyList();
        for (String s : allowList) {
            try {
                Identifier id = Identifier.tryParse(s.trim());
                if (id != null) {
                    NON_BLOCKING_VISION_ALLOW.add(id);
                } else {
                    SoundAttractMod.LOGGER.warn("[FOV Config] Malformed nonBlockingVisionAllowList entry: {}", s);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.error("[FOV Config] Error processing nonBlockingVisionAllowList entry: {}", s, e);
            }
        }
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[FOV Config] Loaded {} non-blocking vision allowlist entries.", NON_BLOCKING_VISION_ALLOW.size());
        }
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

        if (checkObstructions && !hasSmartLineOfSight(looker, target)) {
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
    public static boolean hasSmartLineOfSight(MobEntity looker, net.minecraft.entity.Entity target) {
        World world = looker.getWorld();


        Vec3d eyeToEye = target.getEyePos();
        Vec3d center = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d feet = target.getPos().add(0, Math.max(0.1, target.getHeight() * 0.15), 0);

        Vec3d start = looker.getEyePos();
        return raycastIgnoringNonBlocking(world, start, eyeToEye, looker)
                || raycastIgnoringNonBlocking(world, start, center, looker)
                || raycastIgnoringNonBlocking(world, start, feet, looker);
    }

    private static boolean raycastIgnoringNonBlocking(World world, Vec3d start, Vec3d end, MobEntity looker) {

        final int maxPassThroughs = 24;
        Vec3d currStart = start;
        Vec3d dir = end.subtract(start);
        double totalDist = dir.length();
        if (totalDist < 1.0e-4) return true;
        dir = dir.normalize();

        for (int i = 0; i < maxPassThroughs; i++) {
            RaycastContext ctx = new RaycastContext(
                    currStart,
                    end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    looker
            );
            HitResult hit = world.raycast(ctx);
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }

            if (hit instanceof BlockHitResult bhr) {
                BlockPos pos = bhr.getBlockPos();
                BlockState state = world.getBlockState(pos);
                if (isNonBlockingVision(state, world, pos)) {

                    Vec3d step = dir.multiply(0.6);
                    currStart = bhr.getPos().add(step);
                    continue;
                }
                return false;
            }

            return false;
        }

        return false;
    }

    private static boolean isNonBlockingVision(BlockState state, World world, BlockPos pos) {
        if (state == null || world == null || pos == null) {
            return false;
        }

        if (state.isAir()) return true;


        if (state.getBlock() instanceof DoorBlock) {
            Boolean open = state.get(DoorBlock.OPEN);
            if (open != null && open) return true;
        }
        if (state.getBlock() instanceof TrapdoorBlock) {
            Boolean open = state.get(TrapdoorBlock.OPEN);
            if (open != null && open) return true;
        }


        if (NON_BLOCKING_VISION_ALLOW == null) {
            buildCaches();
        }
        try {
            Identifier bid = Registries.BLOCK.getId(state.getBlock());
            if (bid != null && NON_BLOCKING_VISION_ALLOW != null && NON_BLOCKING_VISION_ALLOW.contains(bid)) {
                return true;
            }
        } catch (Throwable ignored) {}



        try {
            var id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
            String path = id != null ? id.getPath() : "";
            if (path.contains("glass") && !path.contains("tinted")) {
                return true;
            }
        } catch (Throwable ignored) {}

        if (state.getBlock() instanceof IceBlock
                || state.isOf(Blocks.PACKED_ICE)
                || state.isOf(Blocks.BLUE_ICE)) {
            return true;
        }


        if (state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof PaneBlock) {
            return true;
        }

        // Treat anything in the WALLS tag (including modded walls) as vision-blocking.
        try {
            if (state.isIn(BlockTags.WALLS)) {
                return false;
            }
        } catch (Throwable ignored) {}


        var shape = state.getCollisionShape(world, pos, ShapeContext.absent());
        if (shape.isEmpty()) return true;


        try {
            if (!state.shouldBlockVision(world, pos)) return true;
        } catch (Throwable ignored) {

        }

        return false;
    }
}