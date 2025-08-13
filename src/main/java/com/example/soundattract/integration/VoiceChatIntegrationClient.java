package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.SoundMessagePayload;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;



public class VoiceChatIntegrationClient {
    public static void handleClientSound(ClientSoundEvent event) {
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            ClientWorld world = MinecraftClient.getInstance().world;
            SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] handleClientSound called: isWhispering={}, playerPos=({}, {}, {}), dim={}, uuid={}",
                    event.isWhispering(),
                    player != null ? player.getX() : -1,
                    player != null ? player.getY() : -1,
                    player != null ? player.getZ() : -1,
                    world != null ? world.getRegistryKey().getValue() : "null",
                    player != null ? player.getUuid() : "null");
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity clientPlayer = mc.player;
        ClientWorld clientWorld = mc.world;

        if (clientPlayer == null || clientWorld == null) {
            SoundAttractMod.LOGGER.warn("[VoiceChatIntegrationClient] Client world or player is null in handleClientSound, skipping event.");
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }


        boolean isWhispering = event.isWhispering();
        int range = isWhispering ? SoundAttractMod.CONFIG.voiceChatWhisperRange : SoundAttractMod.CONFIG.voiceChatNormalRange;
        double weight = SoundAttractMod.CONFIG.voiceChatWeight;

        double x = clientPlayer.getX();
        double y = clientPlayer.getY();
        double z = clientPlayer.getZ();
        Identifier dim = clientWorld.getRegistryKey().getValue();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUuid());


        SoundAttractNetwork.sendSoundMessageToServer(
                SoundMessagePayload.VOICE_CHAT_SOUND_ID,
                x,
                y,
                z,
                dim,
                sourcePlayerUUID,
                range,
                weight,
                null,
                null
        );
    }
}