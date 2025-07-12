package com.example.soundattract.integration;

import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.config.SoundAttractConfig;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class VoiceChatIntegrationClient {

    public static void handleClientSound(ClientSoundEvent event) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[SVC Integration] ClientSoundEvent received. Whispering: {}", event.isWhispering());
        if (!SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
            return;
        }

        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;

        if (clientPlayer == null || clientWorld == null) {
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }

        boolean isWhispering = event.isWhispering();
        int range = isWhispering ? SoundAttractConfig.COMMON.voiceChatWhisperRange.get()
                : SoundAttractConfig.COMMON.voiceChatNormalRange.get();
        double weight = SoundAttractConfig.COMMON.voiceChatWeight.get();

        Vec3 pos = clientPlayer.position();
        ResourceLocation dim = clientWorld.dimension().location();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
        String animatorClass = "voice_chat";

        SoundMessage msg = new SoundMessage(
            SoundMessage.VOICE_CHAT_SOUND_ID,
            pos,
            dim,
            sourcePlayerUUID,
            range,
            weight,
            Optional.of(animatorClass),
            Optional.empty()
        );
        com.example.soundattract.SoundAttractMod.LOGGER.info("[SVC Integration] Sending SoundMessage to server: {}", msg);
        PacketDistributor.sendToServer(msg);
    }
}