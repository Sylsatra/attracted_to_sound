package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.level.block.state.BlockState;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class FovEvents {

    public static final Logger LOGGER = LogManager.getLogger();

    private static final double BACKSTAB_DAMAGE_MULTIPLIER = 1.2;

    private record FovData(double horizontal, double vertical) {

    }

    private static FovData CONFIG_DEFAULT_FOV = null;

    private static final Set<EntityType<?>> DEVELOPER_EXCLUSIONS = Set.of(
            EntityType.WARDEN,
            EntityType.ENDER_DRAGON,
            EntityType.WITHER
    );

    private static Map<ResourceLocation, FovData> CONFIG_FOV_CACHE = null;
    private static Set<ResourceLocation> USER_EXCLUSION_CACHE = null;

    private static void buildCaches() {
        double defaultH = SoundAttractConfig.COMMON.defaultHorizontalFov.get();
        double defaultV = SoundAttractConfig.COMMON.defaultVerticalFov.get();
        CONFIG_DEFAULT_FOV = new FovData(defaultH, defaultV);
        LOGGER.info("[FOV Config] Loaded default FOV: {} horizontal, {} vertical.", defaultH, defaultV);

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
    public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getVisibilityModifier() <= 0) {
            return;
        }
        if (!(event.getEntity() instanceof Mob looker)) {
            return;
        }
        Entity target = event.getLookingEntity();
        if (target == null) {
            return;
        }

        if (!isTargetInFov(looker, target, false)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onMobHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == mob) {
            return;
        }

        if (!isTargetInFov(mob, attacker, true)) {
            float originalDamage = event.getAmount();
            float newDamage = (float) (originalDamage * BACKSTAB_DAMAGE_MULTIPLIER);
            event.setAmount(newDamage);

            if (attacker instanceof Player) {
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, mob.getSoundSource(), 1.0F, 1.2F);
            }
        }
    }

    public static boolean isTargetInFov(Mob looker, Entity target, boolean checkObstructions) {
        if (CONFIG_FOV_CACHE == null) {
            buildCaches();
        }

        ResourceLocation lookerId = EntityType.getKey(looker.getType());

        if (DEVELOPER_EXCLUSIONS.contains(looker.getType())) {
            return true;
        }
        if (USER_EXCLUSION_CACHE.contains(lookerId)) {
            return true;
        }

        FovData fov = CONFIG_FOV_CACHE.getOrDefault(lookerId, CONFIG_DEFAULT_FOV);
        if (fov.horizontal() >= 360) {
            return true;
        }

        if (checkObstructions && !hasVisualLineOfSight(looker, target)) {
            return false;
        }

        return isWithinFieldOfView(looker, target, fov.horizontal(), fov.vertical());
    }

    private static boolean hasVisualLineOfSight(Mob looker, Entity target) {
        Vec3 startVec = looker.getEyePosition();
        Vec3 endVec = target.position().add(0, target.getBbHeight() / 2.0, 0);

        ClipContext context = new ClipContext(startVec, endVec, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, looker);

        BlockHitResult hitResult = looker.level().clip(context);

        return hitResult.getType() == BlockHitResult.Type.MISS;
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
        if (angleHorizontal > horizontalFovDegrees / 2.0) {
            return false;
        }

        double pitchLook = Math.toDegrees(Math.asin(lookVector.y));
        double pitchTarget = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTargetVector.y))));
        double angleVertical = Math.abs(pitchTarget - pitchLook);
        return angleVertical <= verticalFovDegrees / 2.0;
    }
}
