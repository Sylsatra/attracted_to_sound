package com.example.soundattract.integration;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class VanillaIntegrationEvents {
    private static boolean wasSprinting = false;
    private static boolean wasSneaking = false;
    private static boolean wasCrawling = false;
    private static boolean wasOnGround = true;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;

        tickCounter++;
        int cooldown = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
        if (tickCounter % cooldown != 0) return;

        Player player = event.getEntity();
        Identifier dim = SoundTracker.getDimensionKey(player.level());
        Vec3 pos = player.position();
        Optional<UUID> uuid = Optional.of(player.getUUID());

        if (player.isSprinting()) {
            addVanillaSound("VanillaSprint", "minecraft:entity.player.sprint", pos, dim, uuid, 10, 1.2);
        }
        wasSprinting = player.isSprinting();

        if (!player.isSprinting() && !player.isCrouching() && player.getDeltaMovement().horizontalDistanceSqr() > 0.01 && player.onGround() && !player.isSwimming()) {
            addVanillaSound("VanillaWalk", "minecraft:entity.player.walk", pos, dim, uuid, 6, 0.6);
        }

        boolean isJumping = player.fallDistance == 0 && player.getDeltaMovement().y > 0.1 && !wasOnGround;
        if (isJumping) {
            addVanillaSound("VanillaJump", "minecraft:entity.player.jump", pos, dim, uuid, 7, 0.7);
        }
        wasOnGround = player.onGround();

        if (player.isCrouching()) {
            addVanillaSound("VanillaSneak", "minecraft:entity.player.sneak", pos, dim, uuid, 3, 0.2);
        }
        wasSneaking = player.isCrouching();

        boolean isCrawling = player.isVisuallyCrawling();
        if (isCrawling) {
            addVanillaSound("VanillaCrawl", "minecraft:block.wool.step", pos, dim, uuid, 2, 0.1);
        }
        wasCrawling = isCrawling;
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static void addVanillaSound(String animatorClass, String soundId, Vec3 position, Identifier dim, Optional<UUID> uuid, int range, double weight) {
        Identifier soundResource = Identifier.parse(soundId);
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundResource)) {
            return;
        }

        BlockPos pos = BlockPos.containing(position);
        String dimString = dim.toString();
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

        Optional<SoundEvent> se = BuiltInRegistries.SOUND_EVENT.getOptional(soundResource);

        se.ifPresentOrElse(soundEvent -> {
            SoundTracker.addSound(soundEvent, pos, dimString, range, weight, lifetime);
        }, () -> {
            if (animatorClass != null && !animatorClass.isEmpty()) {
                SoundTracker.addVirtualSound(pos, dimString, range, weight, lifetime, uuid.orElse(null), animatorClass);
            }
        });
    }
}
