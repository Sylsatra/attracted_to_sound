package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Optional;
import java.util.UUID;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractionEvents {

    private enum PlayerAction {
        IDLE, CRAWLING, SNEAKING, WALKING, SPRINTING, SPRINT_JUMPING
    }

    private static final double IDLE_THRESHOLD_SQ = 0.001 * 0.001;
    private static final double CRAWLING_THRESHOLD_SQ = 0.03 * 0.03;
    private static final double SNEAKING_SPEED_SQ = 0.066 * 0.066;
    private static final double WALKING_SPEED_SQ = 0.216 * 0.216;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel serverLevel = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
            java.util.Set<String> attractedTypes = new java.util.HashSet<>();
            for (Object o : com.example.soundattract.config.SoundAttractConfig.attractedEntities.get()) {
                attractedTypes.add(o.toString());
            }
            int mobCount = 0;
            for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, serverLevel.getWorldBorder().getCollisionShape().bounds())) {
                String mobTypeId = mob.getType().builtInRegistryHolder().key().location().toString();
                if (attractedTypes.contains(mobTypeId)) {
                    mobCount++;
                }
            }
            com.example.soundattract.DynamicScanCooldownManager.update(serverLevel.getGameTime(), mobCount);
            SoundTracker.tick();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            if (event.level instanceof ServerLevel serverLevel) {
                SoundTracker.pruneIrrelevantSounds(serverLevel);
                com.example.soundattract.DynamicScanCooldownManager.update(serverLevel.getServer().getTickCount(), 0);
                com.example.soundattract.ai.MobGroupManager.updateGroups(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (entityId == null) return;

            String entityIdStr = entityId.toString();
            if (!SoundAttractConfig.attractedEntities.get().contains(entityIdStr)) {
                return;
            }

            double moveSpeed = SoundAttractConfig.mobMoveSpeed.get();

            boolean attractionGoalExists = mob.goalSelector.getAvailableGoals().stream()
                    .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof AttractionGoal);
            if (!attractionGoalExists) {
                mob.goalSelector.addGoal(10, new AttractionGoal(mob, moveSpeed));
            }
            boolean followLeaderGoalExists = mob.goalSelector.getAvailableGoals().stream()
                    .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof FollowLeaderGoal);
            if (!followLeaderGoalExists) {
                mob.goalSelector.addGoal(11, new FollowLeaderGoal(mob, moveSpeed));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;
        java.util.Set<String> attractedTypes = new java.util.HashSet<>();
        for (Object o : com.example.soundattract.config.SoundAttractConfig.attractedEntities.get()) {
            attractedTypes.add(o.toString());
        }
        String mobTypeId = mob.getType().builtInRegistryHolder().key().location().toString();
        if (attractedTypes.contains(mobTypeId)) {
            com.example.soundattract.ai.MobGroupManager.updateGroups((ServerLevel)event.getLevel());
        }
    }

    public static class SoundMapping {
        public final ResourceLocation soundEvent;
        public final int range;
        public final double weight;

        public SoundMapping(ResourceLocation soundEvent, int range, double weight) {
            this.soundEvent = soundEvent;
            this.range = range;
            this.weight = weight;
        }

        public static SoundMapping forAnimator(Class<?> animatorClass) {
            return null;
        }
    }
}
