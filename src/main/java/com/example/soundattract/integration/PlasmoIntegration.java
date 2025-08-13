package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessagePayload;
import com.example.soundattract.logic.SoundMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;

import java.util.Optional;

@Addon(id = "soundattract", name = "Attract to Sound", version = "PlasmoVoice", authors = {"Paldiu", "Sylsatra"})
public class PlasmoIntegration implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceServer server;

    @Override
    public void onAddonInitialize() {
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PlasmoIntegration] onAddonInitialize: registering voice listener");
        }

        try {
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
        @EventSubscribe
        public void voiceActive(PlayerSpeakEvent event) {

            Object mcPlayerObj = event.getPlayer().getInstance();
            if (!(mcPlayerObj instanceof ServerPlayerEntity mcPlayer)) {

                return;
            }


            double x = mcPlayer.getX();
            double y = mcPlayer.getY();
            double z = mcPlayer.getZ();
            Identifier dim = mcPlayer.getWorld().getRegistryKey().getValue();




            int range = SoundAttractMod.CONFIG.voiceChatNormalRange;

            SoundMessagePayload payload = new SoundMessagePayload(
                    SoundMessagePayload.VOICE_CHAT_SOUND_ID,
                    x, y, z,
                    dim,
                    Optional.of(mcPlayer.getUuid()),
                    range,
                    100.0,
                    null,
                    null
            );

            mcPlayer.getServer().execute(() -> {
                SoundMessageHandler.handle(payload, mcPlayer);
            });
        }

        @EventSubscribe
        public void onServerSourceAudio(ServerSourceAudioPacketEvent event) {
            try {

                short distance = event.getDistance();


                var activationInfo = event.getActivationInfo();
                if (activationInfo == null) {
                    return;
                }
                var voicePlayer = activationInfo.getPlayer();
                if (voicePlayer == null) {
                    return;
                }
                Object playerObj = voicePlayer.getInstance();
                if (playerObj == null) {
                    return;
                }
                ServerPlayerEntity mcPlayer = null;
                if (playerObj instanceof ServerPlayerEntity sp) {
                    mcPlayer = sp;
                } else {

                    String[] candidateMethods = new String[]{"getPlayer", "getMinecraftPlayer", "getHandle", "getInstance"};
                    for (String methodName : candidateMethods) {
                        try {
                            var m = playerObj.getClass().getMethod(methodName);
                            Object inner = m.invoke(playerObj);
                            if (inner instanceof ServerPlayerEntity sp2) {
                                mcPlayer = (ServerPlayerEntity) inner;

                                break;
                            }
                        } catch (Throwable ignored) { }
                    }

                    if (mcPlayer == null) {
                        try {
                            java.util.UUID uuid = null;
                            try { uuid = (java.util.UUID) voicePlayer.getClass().getMethod("getUuid").invoke(voicePlayer); } catch (Throwable ignored) {}
                            if (uuid == null) { try { uuid = (java.util.UUID) voicePlayer.getClass().getMethod("getUUID").invoke(voicePlayer); } catch (Throwable ignored) {} }
                            if (uuid == null) { try { Object id = voicePlayer.getClass().getMethod("getUniqueId").invoke(voicePlayer); if (id instanceof java.util.UUID u) uuid = u; } catch (Throwable ignored) {} }
                            if (uuid != null) {

                                Object source = event.getSource();
                                ServerPlayerEntity candidate = null;




                            }
                        } catch (Throwable ignored) { }
                    }
                }
                if (mcPlayer == null) {
                    return;
                }

                int range = (distance > 0) ? distance : SoundAttractMod.CONFIG.voiceChatNormalRange;

                double x = mcPlayer.getX();
                double y = mcPlayer.getY();
                double z = mcPlayer.getZ();
                Identifier dim = mcPlayer.getWorld().getRegistryKey().getValue();

                SoundMessagePayload payload = new SoundMessagePayload(
                        SoundMessagePayload.VOICE_CHAT_SOUND_ID,
                        x, y, z,
                        dim,
                        Optional.of(mcPlayer.getUuid()),
                        range,
                        100.0,
                        null,
                        null
                );
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] VOICE_CHAT forwarded via Plasmo: player={} range={} dim={}",
                            mcPlayer.getGameProfile().getName(), range, dim);
                }
                var server = mcPlayer.getServer();
                if (server == null) {
                    return;
                }
                final ServerPlayerEntity mcPlayerFinal = mcPlayer;
                final SoundMessagePayload payloadFinal = payload;
                server.execute(() -> {
                    SoundMessageHandler.handle(payloadFinal, mcPlayerFinal);
                });
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onServerSourceAudio", t);
            }
        }
    }
}
