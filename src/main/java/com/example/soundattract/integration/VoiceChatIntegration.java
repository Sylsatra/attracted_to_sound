package com.example.soundattract.integration;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;

@ForgeVoicechatPlugin
public class VoiceChatIntegration implements VoicechatPlugin {

    private static final String VOICE_CHAT_PLUGIN_ID = "soundattract_vc_integration";

    @Override
    public String getPluginId() {
        return VOICE_CHAT_PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            registration.registerEvent(ClientSoundEvent.class, event -> {
                ClientHandler.handle(event);
            });
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(ClientSoundEvent event) {
            com.example.soundattract.integration.VoiceChatIntegrationClient.handleClientSound(event);
        }
    }
}
