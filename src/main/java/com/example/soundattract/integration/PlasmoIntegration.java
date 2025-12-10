package com.example.soundattract.integration;

// ============================================================================
// PLASMO VOICE INTEGRATION - COMMENTED OUT FOR 1.21.11 (Plasmo Voice not available for NeoForge 1.21.11)
// ============================================================================

/*
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;

import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;

@Addon(id = "soundattract", name = "Attract to Sound", version = "PlasmoVoice", authors = {"Paldiu", "Sylsatra"})
public final class PlasmoIntegration implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceServer server;

    @Override
    public void onAddonInitialize() {
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PlasmoIntegration] onAddonInitialize: registering voice listener");
        }
        try {
            if (server == null) {
                SoundAttractMod.LOGGER.warn("[PlasmoIntegration] PlasmoVoiceServer is null; skipping listener registration.");
                return;
            }
            server.getEventBus().register(this, new VoiceListener());
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[PlasmoIntegration] register(this, listener) failed: {}", t.toString());
        }
    }

    @Override
    public void onAddonShutdown() {
        AddonInitializer.super.onAddonShutdown();
    }

    public static final class VoiceListener {

        private ServerPlayer resolveMcPlayer(Object inst) {
            if (inst instanceof ServerPlayer sp) return sp;
            if (inst == null) return null;

            String[] methodNames = new String[] {"getPlayer", "getMinecraftPlayer", "getHandle", "getNms", "player"};
            for (String name : methodNames) {
                try {
                    Method m = inst.getClass().getMethod(name);
                    m.setAccessible(true);
                    Object value = m.invoke(inst);
                    if (value instanceof ServerPlayer sp) return sp;
                } catch (Throwable ignored) { }
            }

            String[] fieldNames = new String[] {"player", "minecraftPlayer", "handle", "nms"};
            for (String name : fieldNames) {
                try {
                    Field f = inst.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    Object value = f.get(inst);
                    if (value instanceof ServerPlayer sp) return sp;
                } catch (Throwable ignored) { }
            }
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[PlasmoIntegration] resolveMcPlayer: failed to unwrap {}", inst.getClass().getName());
            }
            return null;
        }

        private UUID tryResolveUuid(Object obj) {
            if (obj == null) return null;

            String[] methods = new String[] {"getUUID", "getUuid", "getUniqueId", "getId"};
            for (String name : methods) {
                try {
                    Method m = obj.getClass().getMethod(name);
                    m.setAccessible(true);
                    Object value = m.invoke(obj);
                    if (value instanceof UUID u) return u;
                    if (value instanceof String s) {
                        try { return UUID.fromString(s); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) { }
            }

            String[] fields = new String[] {"uuid", "UUID", "id", "uniqueId"};
            for (String name : fields) {
                try {
                    Field f = obj.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    Object value = f.get(obj);
                    if (value instanceof UUID u) return u;
                    if (value instanceof String s) {
                        try { return UUID.fromString(s); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) { }
            }
            return null;
        }

        private ServerPlayer resolveViaServer(UUID uuid) {
            if (uuid == null) return null;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            return server.getPlayerList().getPlayer(uuid);
        }

        @EventSubscribe
        public void onPlayerSpeak(PlayerSpeakEvent event) {
            try {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] onPlayerSpeak: event={} player={}", event, event != null ? event.getPlayer() : null);
                }
                if (event == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onPlayerSpeak: event was null (early return)");
                    }
                    return;
                }
                if (event.getPlayer() == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onPlayerSpeak: event.getPlayer() was null (early return)");
                    }
                    return;
                }
                if (!SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onPlayerSpeak: integration disabled by config");
                    }
                    return;
                }
                Object inst = event.getPlayer().getInstance();
                ServerPlayer mcPlayer = resolveMcPlayer(inst);
                if (mcPlayer == null) {

                    UUID uuid = tryResolveUuid(event.getPlayer());
                    if (uuid == null) uuid = tryResolveUuid(inst);
                    mcPlayer = resolveViaServer(uuid);
                    if (mcPlayer == null) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[PlasmoIntegration] onPlayerSpeak: could not resolve ServerPlayer (uuid={} inst={})", uuid, (inst != null ? inst.getClass().getName() : "null"));
                        }
                        return;
                    }
                }


                int range = SoundAttractConfig.COMMON.voiceChatNormalRange.get();
                double weight = SoundAttractConfig.COMMON.voiceChatWeight.get();
                int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

                BlockPos pos = mcPlayer.blockPosition();
                String dim = mcPlayer.serverLevel().dimension().location().toString();
                UUID uuid = mcPlayer.getUUID();

                SoundTracker.addVirtualSound(pos, dim, (double) range, weight, lifetime, uuid, "voice_chat");

                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] VOICE_CHAT (PlayerSpeakEvent) player={} range={} dim={}", mcPlayer.getGameProfile().getName(), range, dim);
                }
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onPlayerSpeak", t);
            }
        }

        @EventSubscribe
        public void onServerSourceAudio(ServerSourceAudioPacketEvent event) {
            try {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: event received={}", event);
                }
                if (event == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: event was null (early return)");
                    }
                    return;
                }
                if (!SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: integration disabled by config");
                    }
                    return;
                }
                short distance = event.getDistance();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: distance={}", distance);
                }

                var activationInfo = event.getActivationInfo();
                if (activationInfo == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: activationInfo is null (early return)");
                    }
                    return;
                }
                var voicePlayer = activationInfo.getPlayer();
                if (voicePlayer == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: activationInfo.getPlayer() is null (early return)");
                    }
                    return;
                }

                Object inst = voicePlayer.getInstance();
                ServerPlayer mcPlayer = resolveMcPlayer(inst);
                if (mcPlayer == null) {

                    UUID uuid = tryResolveUuid(voicePlayer);
                    if (uuid == null) uuid = tryResolveUuid(inst);
                    mcPlayer = resolveViaServer(uuid);
                    if (mcPlayer == null) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: could not resolve ServerPlayer (uuid={} inst={})", uuid, (inst != null ? inst.getClass().getName() : "null"));
                        }
                        return;
                    }
                }

                int range = (distance > 0) ? distance : SoundAttractConfig.COMMON.voiceChatNormalRange.get();
                double weight = SoundAttractConfig.COMMON.voiceChatWeight.get();
                int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] onServerSourceAudio: computed range={} weight={} lifetime={} for player {}", range, weight, lifetime, mcPlayer.getGameProfile().getName());
                }

                BlockPos pos = mcPlayer.blockPosition();
                String dim = mcPlayer.serverLevel().dimension().location().toString();
                UUID uuid = mcPlayer.getUUID();


                SoundTracker.addVirtualSound(pos, dim, (double) range, weight, lifetime, uuid, "voice_chat");

                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] VOICE_CHAT (ServerSourceAudio) player={} range={} dim={}", mcPlayer.getGameProfile().getName(), range, dim);
                }
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onServerSourceAudio", t);
            }
        }
    }
}
*/

// Stub class to prevent compilation errors - Plasmo Voice integration disabled for 1.21.11
public final class PlasmoIntegration {
    private PlasmoIntegration() {}
}
