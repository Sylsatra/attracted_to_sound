package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.SoundMessagePayload;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerPlayer;
import net.minecraft.util.Identifier;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;

import java.util.Optional;

@Addon(id = "soundattract", name = "Attract to Sound", version = "4.0.3b", authors = {"Paldiu", "Sylsatra"})
public class PlasmoClientIntegration implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceServer server;

    @Override
    public void onAddonInitialize() {
        server.getEventBus().register(this, new VoiceListener());
    }

    @Override
    public void onAddonShutdown() {
        AddonInitializer.super.onAddonShutdown();
    }

    public static final class VoiceListener {
        @EventSubscribe
        public void voiceActive(PlayerSpeakEvent event) {
            // Get Minecraft player instance (raw)
            Object mcPlayerObj = event.getPlayer().getInstance();
            if (!(mcPlayerObj instanceof ServerPlayer mcPlayer)) {
                // Not a server player, skip
                return;
            }
            Position pos = mcPlayer.getPosition();

            // Get position and dimension info
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();
            Identifier dim = SoundAttractMod.getDimensionId(mcPlayer.getUuid());

            //TODO: Possibly rewrite this entire thing to use custom Activation class.

            short range = event.getPacket().getDistance();

            // Send AtS sound message to server
            SoundAttractNetwork.sendSoundMessageToServer(
                    SoundMessagePayload.VOICE_CHAT_SOUND_ID,
                    x, y, z,
                    dim,
                    Optional.of(mcPlayer.getUuid()),
                    range,
                    100.0, // Player voice should always take priority over non-player sounds.
                    null,
                    null
            );
        }
    }
}
