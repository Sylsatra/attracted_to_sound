package com.example.soundattract.integration.voicechat;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.server.level.ServerPlayer;
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
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
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
            if (!SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
                return;
            }

            Object mcPlayerObj = event.getPlayer().getInstance();
            if (!(mcPlayerObj instanceof ServerPlayer mcPlayer)) {
                return;
            }

            int range = SoundAttractConfig.COMMON.voiceChatNormalRange.get();

            SoundMessage msg = new SoundMessage(
                    SoundMessage.VOICE_CHAT_SOUND_ID,
                    mcPlayer.getX(), mcPlayer.getY(), mcPlayer.getZ(),
                    mcPlayer.serverLevel().dimension().location(),
                    Optional.of(mcPlayer.getUUID()),
                    range,
                    SoundAttractConfig.COMMON.voiceChatWeight.get()
            );

            mcPlayer.getServer().execute(() -> SoundMessage.handle(msg, null));
        }

        @EventSubscribe
        public void onServerSourceAudio(ServerSourceAudioPacketEvent event) {
            if (!SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
                return;
            }

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

                ServerPlayer mcPlayer = null;
                if (playerObj instanceof ServerPlayer sp) {
                    mcPlayer = sp;
                } else {
                    String[] candidateMethods = new String[]{"getPlayer", "getMinecraftPlayer", "getHandle", "getInstance"};
                    for (String methodName : candidateMethods) {
                        try {
                            var m = playerObj.getClass().getMethod(methodName);
                            Object inner = m.invoke(playerObj);
                            if (inner instanceof ServerPlayer sp2) {
                                mcPlayer = sp2;
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }

                if (mcPlayer == null) {
                    return;
                }

                int range = (distance > 0) ? distance : SoundAttractConfig.COMMON.voiceChatNormalRange.get();

                SoundMessage msg = new SoundMessage(
                        SoundMessage.VOICE_CHAT_SOUND_ID,
                        mcPlayer.getX(), mcPlayer.getY(), mcPlayer.getZ(),
                        mcPlayer.serverLevel().dimension().location(),
                        Optional.of(mcPlayer.getUUID()),
                        range,
                        SoundAttractConfig.COMMON.voiceChatWeight.get()
                );

                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[PlasmoIntegration] VOICE_CHAT forwarded via Plasmo: player={} range={} dim={}",
                            mcPlayer.getGameProfile().getName(), range, mcPlayer.serverLevel().dimension().location());
                }

                var server = mcPlayer.getServer();
                if (server == null) {
                    return;
                }
                server.execute(() -> SoundMessage.handle(msg, null));
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("[PlasmoIntegration] Exception in onServerSourceAudio", t);
            }
        }
    }
}
