package com.example.soundattract;



import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import com.example.soundattract.config.SoundAttractConfigData;

import java.lang.reflect.Method;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;


public class SoundAttractClientEvents {




    public static void onPlaySoundEvent(net.minecraft.client.sound.SoundInstance soundInstance) {
        if (soundInstance == null) {
            return;
        }
        World clientWorld = MinecraftClient.getInstance().world;
        ClientPlayerEntity clientPlayer = MinecraftClient.getInstance().player;
        if (clientWorld == null || clientPlayer == null) {
            com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundAttractClientEvents] Client world or player is null in onPlaySoundEvent, skipping event.");
            return;
        }
        Identifier soundRL = soundInstance.getId();
        if (soundRL == null || soundRL.equals(SoundMessage.VOICE_CHAT_SOUND_ID)) {
            return;
        }


        SoundEvent se = Registries.SOUND_EVENT.get(soundRL);
        if (se == null) {
            return;
        }

        double x = soundInstance.getX();
        double y = soundInstance.getY();
        double z = soundInstance.getZ();
        Identifier dim = clientWorld.getRegistryKey().getValue();
        Optional<UUID> sourcePlayerUUID = Optional.empty();
        int calculatedRange = -1;
        double calculatedWeight = 1.0;

        if (se != null && se.getId().getPath().contains("step") && clientPlayer.squaredDistanceTo(x, y, z) < 1.5 * 1.5) {

            sourcePlayerUUID = Optional.of(clientPlayer.getUuid());
            Vec3d motion = clientPlayer.getVelocity();
            double horizontalSpeedSq = motion.x * motion.x + motion.z * motion.z;
            boolean isOnGround = clientPlayer.isOnGround();
            boolean isSneaking = clientPlayer.isSneaking();
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
        SoundAttractNetwork.sendSoundMessageToServer(msg);
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
