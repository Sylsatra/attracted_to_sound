package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import java.util.Optional;
import java.util.UUID;

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
    public static void onPlaySoundEvent(PlaySoundEvent event) {
        if (event.getSound() == null || !SoundAttractConfig.SOUND_BASED_DETECTION_ENABLED_CACHE) {
            return;
        }

        Level clientWorld = Minecraft.getInstance().level;
        Player clientPlayer = Minecraft.getInstance().player;
        if (clientWorld == null || clientPlayer == null) {
            return;
        }

        if (event.getSound() instanceof AbstractSoundInstance soundInstance) {
            ResourceLocation soundRL = soundInstance.getLocation();
            if (soundRL == null || soundRL.equals(SoundMessage.VOICE_CHAT_SOUND_ID)) {
                return;
            }

            SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(soundRL);
            if (se == null) {
                return;
            }

            double x = soundInstance.getX();
            double y = soundInstance.getY();
            double z = soundInstance.getZ();
            ResourceLocation dim = clientWorld.dimension().location();
            Optional<UUID> sourcePlayerUUID = Optional.empty();
            int calculatedRange = -1;
            double calculatedWeight = 1.0;

            if (SoundAttractConfig.PLAYER_STEP_SOUNDS_CACHE.contains(se) &&
                clientPlayer.position().distanceToSqr(x, y, z) < 1.5 * 1.5) {

                sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
                Vec3 motion = clientPlayer.getDeltaMovement();
                double horizontalSpeedSq = motion.x * motion.x + motion.z * motion.z;
                boolean isOnGround = clientPlayer.onGround();
                boolean isSneaking = clientPlayer.isShiftKeyDown();
                boolean isSprinting = clientPlayer.isSprinting();

                PlayerAction currentAction = PlayerAction.IDLE;

                if (isSneaking) {
                    if (horizontalSpeedSq > IDLE_THRESHOLD_SQ && horizontalSpeedSq <= CRAWLING_THRESHOLD_SQ) {
                        currentAction = PlayerAction.CRAWLING;
                    } else if (horizontalSpeedSq > CRAWLING_THRESHOLD_SQ && horizontalSpeedSq <= SNEAKING_SPEED_SQ * 1.1) {
                        currentAction = PlayerAction.SNEAKING;
                    }
                } else {
                    if (isSprinting && !isOnGround) {
                        currentAction = PlayerAction.SPRINT_JUMPING;
                    } else if (isSprinting && horizontalSpeedSq > WALKING_SPEED_SQ) {
                        currentAction = PlayerAction.SPRINTING;
                    } else if (isOnGround && horizontalSpeedSq > IDLE_THRESHOLD_SQ && horizontalSpeedSq <= WALKING_SPEED_SQ) {
                        currentAction = PlayerAction.WALKING;
                    }
                }

                switch (currentAction) {
                    case CRAWLING:
                        calculatedRange = 2;
                        calculatedWeight = 1.0;
                        SoundAttractMod.LOGGER.trace("Player Action: CRAWLING -> Range: {}, Weight: {}", calculatedRange, calculatedWeight);
                        break;
                    case SNEAKING:
                        calculatedRange = 3;
                        calculatedWeight = 1.0;
                        SoundAttractMod.LOGGER.trace("Player Action: SNEAKING -> Range: {}, Weight: {}", calculatedRange, calculatedWeight);
                        break;
                    case WALKING:
                        calculatedRange = 8;
                        calculatedWeight = 1.0;
                        SoundAttractMod.LOGGER.trace("Player Action: WALKING -> Range: {}, Weight: {}", calculatedRange, calculatedWeight);
                        break;
                    case SPRINTING:
                        calculatedRange = 12;
                        calculatedWeight = 1.0;
                        SoundAttractMod.LOGGER.trace("Player Action: SPRINTING -> Range: {}, Weight: {}", calculatedRange, calculatedWeight);
                        break;
                    case SPRINT_JUMPING:
                        calculatedRange = 16;
                        calculatedWeight = 1.0;
                        SoundAttractMod.LOGGER.trace("Player Action: SPRINT_JUMPING -> Range: {}, Weight: {}", calculatedRange, calculatedWeight);
                        break;
                    case IDLE:
                    default:
                        SoundAttractMod.LOGGER.trace("Player Action: IDLE -> No message sent.");
                        return;
                }
            }

            SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim, sourcePlayerUUID, calculatedRange, calculatedWeight);
            SoundAttractNetwork.INSTANCE.sendToServer(msg);
            SoundAttractMod.LOGGER.trace(" -> Sent SoundMessage: {}, Pos: ({}, {}, {}), PlayerUUID: {}, Range: {}, Weight: {}", soundRL, x, y, z, sourcePlayerUUID.map(UUID::toString).orElse("None"), calculatedRange, calculatedWeight);
        } else {
            SoundAttractMod.LOGGER.trace("Ignoring non-AbstractSoundInstance sound: {}", event.getSound().getClass().getName());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SoundTracker.tick();
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (entityId == null) return;

            String entityIdStr = entityId.toString();
            if (!SoundAttractConfig.ATTRACTED_ENTITIES_CACHE.contains(entityIdStr)) {
                return;
            }

            double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
            int scanRadius = SoundAttractConfig.COMMON.mobScanRadius.get();

            boolean goalExists = mob.goalSelector.getAvailableGoals().stream()
                    .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof AttractionGoal);

            if (!goalExists) {
                mob.goalSelector.addGoal(2, new AttractionGoal(mob, moveSpeed, scanRadius));
                SoundAttractMod.LOGGER.debug("Added AttractionGoal to {} (ID: {}) with speed {} and radius {}",
                        mob.getName().getString(), entityIdStr, moveSpeed, scanRadius);
            } else {
                SoundAttractMod.LOGGER.trace("AttractionGoal already exists for {}", mob.getName().getString());
            }
        }
    }
}
