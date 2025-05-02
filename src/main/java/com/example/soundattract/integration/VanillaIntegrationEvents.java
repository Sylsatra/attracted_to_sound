package com.example.soundattract.integration;

import com.example.soundattract.SoundMessage;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class VanillaIntegrationEvents {
    private static boolean wasSprinting = false;
    private static boolean wasSneaking = false;
    private static boolean wasCrawling = false;
    private static boolean wasOnGround = true;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        tickCounter++;
        int cooldown = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
        if (tickCounter % cooldown != 0) return;
        Player player = event.player;
        ResourceLocation dim = player.level().dimension().location();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        Optional<java.util.UUID> uuid = Optional.of(player.getUUID());

        if (player.isSprinting()) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaSprint", "minecraft:entity.player.sprint", x, y, z, dim, uuid, 10, 1.2, 1.0, 1.0);
            }
        }
        wasSprinting = player.isSprinting();

        if (!player.isSprinting() && !player.isCrouching() && player.getDeltaMovement().horizontalDistanceSqr() > 0.01 && player.onGround() && !player.getPose().name().equalsIgnoreCase("SWIMMING")) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaWalk", "minecraft:entity.player.walk", x, y, z, dim, uuid, 6, 0.6, 0.8, 1.0);
            }
        }

        boolean isJumping = player.fallDistance == 0 && player.getDeltaMovement().y > 0.1 && !wasOnGround;
        if (isJumping) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaJump", "minecraft:entity.player.jump", x, y, z, dim, uuid, 7, 0.7, 0.8, 1.0);
            }
        }
        wasOnGround = player.onGround();

        if (player.isCrouching()) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaSneak", "minecraft:entity.player.sneak", x, y, z, dim, uuid, 3, 0.2, 0.4, 1.0);
            }
        }
        wasSneaking = player.isCrouching();

        boolean isCrawling = player.getPose().name().equalsIgnoreCase("SWIMMING");
        if (isCrawling) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaCrawl", "minecraft:block.wool.step", x, y, z, dim, uuid, 2, 0.1, 0.2, 1.0);
            }
        }
        wasCrawling = isCrawling;
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static void sendVanillaSound(String animatorClass, String soundId, double x, double y, double z, ResourceLocation dim, Optional<java.util.UUID> uuid, int range, double weight, double volume, double pitch) {
        SoundMessage msg = new SoundMessage(
            new ResourceLocation(soundId),
            x, y, z,
            dim,
            uuid,
            range,
            weight,
            animatorClass
        );
        boolean isServer = true;
        try {
            Class.forName("net.minecraft.server.level.ServerPlayer");
        } catch (Throwable t) {
            isServer = false;
        }
        if (isServer) {
            String soundIdStr = soundId != null ? soundId : null;
            if (!com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && (soundIdStr == null || !com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundIdStr))) {
                return;
            }
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
            String dimString = dim.toString();
            int lifetime = com.example.soundattract.config.SoundAttractConfig.soundLifetimeTicks.get();
            if ((new ResourceLocation(soundId)).equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID)) {
                if (range > 0) {
                    com.example.soundattract.SoundTracker.addSound(null, pos, dimString, range, weight, lifetime);
                }
            } else {
                net.minecraft.sounds.SoundEvent se = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
                double _range = range;
                double _weight = weight;
                String id = soundId != null ? soundId : "";
                if (se != null) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] addSound: soundId={} | range={} | weight={} | pos=({}, {}, {}) | dim={}", soundId, _range, _weight, x, y, z, dim);
                    com.example.soundattract.SoundTracker.addSound(se, pos, dimString, _range, _weight, lifetime);
                } else {
                    if (animatorClass != null && !animatorClass.isEmpty()) {
                        com.example.soundattract.SoundTracker.addVirtualSound(pos, dimString, _range, _weight, lifetime, uuid.orElse(null), animatorClass);
                    }
                }
            }
            return;
        } else {
            com.example.soundattract.SoundAttractNetwork.INSTANCE.sendToServer(msg);
        }
    }
}
