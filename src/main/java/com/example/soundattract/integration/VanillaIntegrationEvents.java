package com.example.soundattract.integration;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.Registries;
import java.util.Optional;

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
        Identifier dim = player.getWorld().getRegistryKey().getValue();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        Optional<java.util.UUID> uuid = Optional.of(player.getUuid());

        if (player.isSprinting()) {
            sendVanillaSound("VanillaSprint", "minecraft:entity.player.sprint", x, y, z, dim, uuid, 10, 1.2, 1.0, 1.0);
        }
        wasSprinting = player.isSprinting();

        if (!player.isSprinting() && !player.isSneaking() && player.getVelocity().horizontalLengthSquared() > 0.01 && player.isOnGround() && !player.getPose().name().equalsIgnoreCase("SWIMMING")) {
            sendVanillaSound("VanillaWalk", "minecraft:entity.player.walk", x, y, z, dim, uuid, 6, 0.6, 0.8, 1.0);
        }
        boolean isJumping = player.fallDistance == 0 && player.getVelocity().y > 0.1 && !wasOnGround;
        if (isJumping) {
            sendVanillaSound("VanillaJump", "minecraft:entity.player.jump", x, y, z, dim, uuid, 7, 0.7, 0.8, 1.0);
        }
        wasOnGround = player.isOnGround();

        if (player.isSneaking()) {
            sendVanillaSound("VanillaSneak", "minecraft:entity.player.sneak", x, y, z, dim, uuid, 3, 0.2, 0.4, 1.0);
        }
        wasSneaking = player.isSneaking();

        boolean isCrawling = player.getPose().name().equalsIgnoreCase("SWIMMING");
        if (isCrawling) {
            sendVanillaSound("VanillaCrawl", "minecraft:block.wool.step", x, y, z, dim, uuid, 2, 0.1, 0.2, 1.0);
        }
        wasCrawling = isCrawling;
    }
    private static void sendVanillaSound(String animatorClass, String soundId, double x, double y, double z, Identifier dim, Optional<java.util.UUID> uuid, int range, double weight, double volume, double pitch) {
        com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(
            new Identifier(soundId),
            x, y, z,
            dim,
            uuid,
            range,
            weight,
            animatorClass
        );
        String soundIdStr = soundId != null ? soundId : null;
        if (!com.example.soundattract.SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() && (soundIdStr == null || !com.example.soundattract.SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundIdStr))) {
            return;
        }
        net.minecraft.util.math.BlockPos pos = net.minecraft.util.math.BlockPos.ofFloored(x, y, z);
        String dimString = dim.toString();
        int lifetime = com.example.soundattract.SoundAttractMod.CONFIG.soundLifetimeTicks;
        if ((new Identifier(soundId)).equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID)) {
            if (range > 0) {
                com.example.soundattract.SoundTracker.addSound(null, pos, dimString, range, weight, lifetime);
            }
        } else {
            SoundEvent se = Registries.SOUND_EVENT.get(new Identifier(soundId));
            double _range = range;
            double _weight = weight;
            if (se != null) {
                if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info(
        "[SoundMessage] addSound: soundId={} | range={} | weight={} | pos=({}, {}, {}) | dim={}",
        soundId, _range, _weight, x, y, z, dim
    );
}
                com.example.soundattract.SoundTracker.addSound(se, pos, dimString, _range, _weight, lifetime);
            } else {
                if (animatorClass != null && !animatorClass.isEmpty()) {
                    com.example.soundattract.SoundTracker.addVirtualSound(
                        pos, dimString, _range, _weight, lifetime, uuid.orElse(null), animatorClass
                    );
                }
            }
        }
    }
}
