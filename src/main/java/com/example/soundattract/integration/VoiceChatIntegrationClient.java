package com.example.soundattract.integration;

import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;

public class VoiceChatIntegrationClient {
    public static void handleClientSound(ClientSoundEvent event) {
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] handleClientSound called: isWhispering={}, playerPos=({}, {}, {}), dim={}, uuid={}", event.isWhispering(), MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getX() : -1, MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getY() : -1, MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getZ() : -1, MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getRegistryKey().getValue() : "null", MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getUuid() : "null");
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

        SoundMessage msg = new SoundMessage(SoundMessage.VOICE_CHAT_SOUND_ID, x, y, z, dim, sourcePlayerUUID, range, weight);
        SoundAttractNetwork.sendSoundMessageToServer(msg);

    }
}
