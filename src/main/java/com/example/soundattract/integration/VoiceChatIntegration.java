package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractionEvents;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;


@ForgeVoicechatPlugin
public class VoiceChatIntegration implements VoicechatPlugin {

    private static final String VOICE_CHAT_PLUGIN_ID = "soundattract_vc_integration";

    @Override
    public String getPluginId() {
        return VOICE_CHAT_PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        SoundAttractMod.LOGGER.info("Simple Voice Chat detected and integration enabled. VoiceChatIntegration class loaded and registering events.");
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
    }

    public void onClientSound(ClientSoundEvent event) {
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

        SoundAttractMod.LOGGER.trace("Player {} {} (range {}, weight {}), sending voice sound message.",
                clientPlayer.getName().getString(),
                isWhispering ? "whispering" : "talking",
                range, weight);

        SoundMessage msg = new SoundMessage(SoundMessage.VOICE_CHAT_SOUND_ID, x, y, z, dim, sourcePlayerUUID, range, weight);
        SoundAttractNetwork.INSTANCE.sendToServer(msg);
    }
} 