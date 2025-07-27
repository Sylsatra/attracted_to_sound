package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessagePayload;
import com.example.soundattract.SoundTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class VanillaIntegrationEvents {
    private static boolean wasSprinting = false;
    private static boolean wasSneaking = false;
    private static boolean wasCrawling = false;
    private static boolean wasOnGround = true;
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                onPlayerTick(player);
            }
        });
    }

    public static void onPlayerTick(ServerPlayerEntity player) {
        tickCounter++;
        int cooldown = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
        if (tickCounter % cooldown != 0) return;


        World world = player.getWorld();
        
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        Optional<UUID> uuid = Optional.of(player.getUuid());

        if (player.isSprinting()) {
            sendVanillaSound(world, "VanillaSprint", "minecraft:entity.player.sprint", x, y, z, uuid, 10, 1.2, 1.0, 1.0);
        }
        wasSprinting = player.isSprinting();

        if (!player.isSprinting() && !player.isSneaking() && player.getVelocity().horizontalLengthSquared() > 0.01 && player.isOnGround() && !player.getPose().name().equalsIgnoreCase("SWIMMING")) {
            sendVanillaSound(world, "VanillaWalk", "minecraft:entity.player.walk", x, y, z, uuid, 6, 0.6, 0.8, 1.0);
        }
        boolean isJumping = player.fallDistance == 0 && player.getVelocity().y > 0.1 && !wasOnGround;
        if (isJumping) {
            sendVanillaSound(world, "VanillaJump", "minecraft:entity.player.jump", x, y, z, uuid, 7, 0.7, 0.8, 1.0);
        }
        wasOnGround = player.isOnGround();

        if (player.isSneaking()) {
            sendVanillaSound(world, "VanillaSneak", "minecraft:entity.player.sneak", x, y, z, uuid, 3, 0.2, 0.4, 1.0);
        }
        wasSneaking = player.isSneaking();

        boolean isCrawling = player.getPose().name().equalsIgnoreCase("SWIMMING");
        if (isCrawling) {
            sendVanillaSound(world, "VanillaCrawl", "minecraft:block.wool.step", x, y, z, uuid, 2, 0.1, 0.2, 1.0);
        }
        wasCrawling = isCrawling;
    }


    private static void sendVanillaSound(World world, String animatorClass, String soundIdStr, double x, double y, double z, Optional<UUID> uuid, int range, double weight, double volume, double pitch) {
        
        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() && (soundIdStr == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundIdStr))) {
            return;
        }

        BlockPos pos = BlockPos.ofFloored(x, y, z);
        int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;
        Identifier soundId = Identifier.of(soundIdStr);

        if (soundId.equals(SoundMessagePayload.VOICE_CHAT_SOUND_ID)) {
            if (range > 0) {

                SoundTracker.addSound(world, null, pos, range, weight, lifetime, soundIdStr);
            }
        } else {
            SoundEvent se = Registries.SOUND_EVENT.get(soundId);
            if (se != null) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[SoundMessage] addSound: soundId={} | range={} | weight={} | pos=({}, {}, {}) | dim={}",
                            soundIdStr, range, weight, x, y, z, world.getRegistryKey().getValue());
                }

                SoundTracker.addSound(world, se, pos, range, weight, lifetime, soundIdStr);
            } else {
                if (animatorClass != null && !animatorClass.isEmpty()) {

                    SoundTracker.addVirtualSound(world, pos, range, weight, lifetime, uuid.orElse(null), animatorClass);
                }
            }
        }
    }
}