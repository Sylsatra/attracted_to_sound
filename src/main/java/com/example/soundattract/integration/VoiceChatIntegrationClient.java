package com.example.soundattract.integration;

import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VoiceChatIntegrationClient {
    public static void handleClientSound(ClientSoundEvent event) {
        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;

        if (clientPlayer == null || clientWorld == null || !SoundAttractConfig.VOICE_CHAT_DETECTION_ENABLED_CACHE) {
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

        double x = clientPlayer.getX();
        double y = clientPlayer.getY();
        double z = clientPlayer.getZ();
        ResourceLocation dim = clientWorld.dimension().location();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());

        SoundMessage msg = new SoundMessage(SoundMessage.VOICE_CHAT_SOUND_ID, x, y, z, dim, sourcePlayerUUID, range, weight);
        SoundAttractNetwork.INSTANCE.sendToServer(msg);
    }
}