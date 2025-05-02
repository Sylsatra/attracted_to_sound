package com.example.soundattract;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractClientEvents {
    public static void onParCoolAnimationInfo(Object event) {
        if (!ModList.get().isLoaded("parcool")) return;
        try {
            Class<?> eventClass = Class.forName("com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent");
            if (!eventClass.isInstance(event)) return;
            Object player = eventClass.getMethod("getPlayer").invoke(event);
            Object animator = eventClass.getMethod("getAnimator").invoke(event);
            if (player == null || animator == null) return;
            if (!(player instanceof AbstractClientPlayer clientPlayer)) return;
            String animatorClass = animator.getClass().getSimpleName();
            SoundAttractionEvents.SoundMapping mapping = SoundAttractionEvents.SoundMapping.forAnimator(animator.getClass());
            if (mapping == null) return;
            ResourceLocation soundRL = mapping.soundEvent;
            int range = mapping.range;
            double weight = mapping.weight;
            ResourceLocation dim = clientPlayer.level().dimension().location();
            Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
            SoundMessage msg = new SoundMessage(soundRL, clientPlayer.getX(), clientPlayer.getY(), clientPlayer.getZ(), dim, sourcePlayerUUID, range, weight);
            SoundAttractNetwork.INSTANCE.sendToServer(msg);
        } catch (Exception ignored) {
        }
    }

    public static void registerParcoolClientHandler() {
        if (!ModList.get().isLoaded("parcool")) return;
        try {
            Class<?> eventClass = Class.forName("com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent");
            Method getHandlerList = eventClass.getMethod("getHandlerList");
            Object handlerList = getHandlerList.invoke(null);
            Method registerMethod = handlerList.getClass().getMethod("register", Object.class);
            registerMethod.invoke(handlerList, new Object() {
                public void onParCoolAnimationInfo(Object event) {
                    SoundAttractClientEvents.onParCoolAnimationInfo(event);
                }
            });
        } catch (Exception ignored) {
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onPlaySoundEvent(PlaySoundEvent event) {
        if (event.getSound() == null) {
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

            SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(soundRL);
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

            if (se != null && se.getLocation().getPath().contains("step") && clientPlayer.position().distanceToSqr(x, y, z) < 1.5 * 1.5) {

                sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
                Vec3 motion = clientPlayer.getDeltaMovement();
                double horizontalSpeedSq = motion.x * motion.x + motion.z * motion.z;
                boolean isOnGround = clientPlayer.onGround();
                boolean isSneaking = clientPlayer.isShiftKeyDown();
                boolean isSprinting = clientPlayer.isSprinting();

                PlayerAction currentAction = PlayerAction.IDLE;

                if (isSneaking) {
                    if (horizontalSpeedSq > 0.001 * 0.001 && horizontalSpeedSq <= 0.03 * 0.03) {
                        currentAction = PlayerAction.CRAWLING;
                    } else if (horizontalSpeedSq > 0.03 * 0.03 && horizontalSpeedSq <= 0.066 * 0.066 * 1.1) {
                        currentAction = PlayerAction.SNEAKING;
                    }
                } else {
                    if (isSprinting && !isOnGround) {
                        currentAction = PlayerAction.SPRINT_JUMPING;
                    } else if (isSprinting && horizontalSpeedSq > 0.216 * 0.216) {
                        currentAction = PlayerAction.SPRINTING;
                    } else if (isOnGround && horizontalSpeedSq > 0.001 * 0.001 && horizontalSpeedSq <= 0.216 * 0.216) {
                        currentAction = PlayerAction.WALKING;
                    }
                }

                switch (currentAction) {
                    case CRAWLING:
                        calculatedRange = 2;
                        calculatedWeight = 1.0;
                        break;
                    case SNEAKING:
                        calculatedRange = 3;
                        calculatedWeight = 1.0;
                        break;
                    case WALKING:
                        calculatedRange = 8;
                        calculatedWeight = 1.0;
                        break;
                    case SPRINTING:
                        calculatedRange = 12;
                        calculatedWeight = 1.0;
                        break;
                    case SPRINT_JUMPING:
                        calculatedRange = 16;
                        calculatedWeight = 1.0;
                        break;
                    case IDLE:
                    default:
                        return;
                }
            }

            SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim, sourcePlayerUUID, calculatedRange, calculatedWeight);
            SoundAttractNetwork.INSTANCE.sendToServer(msg);
        }
    }

    public static void registerVoiceChatIntegration() {
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    public enum PlayerAction {
        IDLE,
        CRAWLING,
        SNEAKING,
        WALKING,
        SPRINTING,
        SPRINT_JUMPING
    }
}
