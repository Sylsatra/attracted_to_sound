package com.example.soundattract.event.client;


import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.SoundMessage;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class SoundAttractClientEvents {

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

            if (SoundAttractConfig.POINT_BLANK_ENABLED_CACHE && "pointblank".equals(soundRL.getNamespace())) {
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
            String actionName = null;

            boolean isStep = se != null && se.getLocation().getPath().contains("step")
                    && clientPlayer.position().distanceToSqr(x, y, z) < 1.5 * 1.5;

            if (isStep) {
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

                if (currentAction == PlayerAction.IDLE) return;
                actionName = currentAction.name();
            }

            SoundMessage msg = new SoundMessage(
                soundRL,
                new Vec3(x, y, z),
                dim,
                sourcePlayerUUID,
                actionName
            );
            PacketDistributor.sendToServer(msg);
        }
    }

    public static void registerVoiceChatIntegration() {
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
