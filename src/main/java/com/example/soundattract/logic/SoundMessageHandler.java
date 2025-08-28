package com.example.soundattract.logic;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessagePayload;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.integration.PointBlankIntegration;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SoundMessageHandler {

    public static void handle(SoundMessagePayload payload, ServerPlayerEntity sender) {
        try {
            if (sender == null) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] sender is null for sound message with soundId: {}", payload.soundId());
                return;
            }

            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[SoundMessage] Received: id={}, range={}, weight={}, dim={}, pos=({}, {}, {})",
                        payload.soundId(), payload.range(), payload.weight(), payload.dimension(),
                        String.format("%.2f", payload.x()), String.format("%.2f", payload.y()), String.format("%.2f", payload.z()));
            }

            RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, payload.dimension());
            ServerWorld serverWorld = sender.getServer().getWorld(worldKey);

            if (serverWorld == null) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] serverWorld is null for dimension: {}", payload.dimension());
                return;
            }



            if (payload.soundId().equals(PointBlankIntegration.PB_GUN_SOUND_ID) && SoundAttractMod.CONFIG.enablePointBlankIntegration) {



                double finalRange = payload.range();
                double finalWeight = payload.weight();

                int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;
                BlockPos pos = BlockPos.ofFloored(payload.x(), payload.y(), payload.z());


                String gunId = "unknown";
                String action = "unknown";
                if (payload.gunData() != null && !payload.gunData().isEmpty()) {
                    String[] parts = payload.gunData().split(";");
                    if (parts.length >= 2) {
                        action = parts[0];
                        gunId = parts[1];
                    }
                }


                SoundTracker.addSound(serverWorld, null, pos, finalRange, finalWeight, payload.soundId().toString());

                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[SoundMessage] Handled PointBlank sound: action={}, gunId={}, finalRange={}, finalWeight={}",
                            action, gunId, String.format("%.2f", finalRange), String.format("%.2f", finalWeight));
                }

                return;
            }


            String soundIdStr = payload.soundId() != null ? payload.soundId().toString() : null;
            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty()
                    && (soundIdStr == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundIdStr))
                    && (payload.soundId() == null || !payload.soundId().equals(SoundMessagePayload.VOICE_CHAT_SOUND_ID))) {
                return;
            }

            BlockPos pos = BlockPos.ofFloored(payload.x(), payload.y(), payload.z());
            if (pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0) {
                pos = sender.getBlockPos();
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[SoundMessage] Fallback to sender position {} for sound {}", pos, payload.soundId());
                }
            }
            
            int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;

            if (payload.soundId().equals(SoundMessagePayload.VOICE_CHAT_SOUND_ID)) {
                int effectiveRange = payload.range();
                if (effectiveRange <= 0) {
                    effectiveRange = SoundAttractMod.CONFIG.voiceChatNormalRange;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] VOICE_CHAT range fallback to config: {}", effectiveRange);
                    }
                }
                if (effectiveRange > 0) {
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] VOICE_CHAT handled at {} in {} with range={} weight={}",
                                pos, serverWorld.getRegistryKey().getValue(), effectiveRange, payload.weight());
                    }
                    SoundTracker.addSound(serverWorld, null, pos, effectiveRange, payload.weight(), SoundMessagePayload.VOICE_CHAT_SOUND_ID.toString());
                }
            } else {
                double range = payload.range();
                double weight = payload.weight();
                if (range < 0 && payload.soundId() != null) {
                    var config = SoundAttractMod.CONFIG.getSoundConfigForId(payload.soundId().toString());
                    if (config != null) {
                        range = config.range;
                        weight = config.weight;
                    }
                }
                SoundTracker.addSound(serverWorld, null, pos, range, weight, payload.soundId().toString());
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[SoundMessage] Exception in handle for soundId={}", payload.soundId(), e);
        }
    }
}