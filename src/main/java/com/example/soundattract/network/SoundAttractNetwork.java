package com.example.soundattract.network;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SoundAttractNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;

    public static void register() {
        INSTANCE = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "network"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

        int id = 0;
        
        INSTANCE.messageBuilder(SoundMessage.class, id++)
                .encoder(SoundMessage::encode)
                .decoder(SoundMessage::decode)
                .consumerMainThread(SoundMessage::handle)
                .add();
    }

}
