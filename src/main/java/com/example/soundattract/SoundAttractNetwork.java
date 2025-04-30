package com.example.soundattract;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SoundAttractNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;

    public static void register() {
        INSTANCE = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(SoundAttractMod.MOD_ID, "network"))
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
        INSTANCE.messageBuilder(com.example.soundattract.integration.TaczReloadMessage.class, id++)
                .encoder((msg, buf) -> msg.toBytes(buf))
                .decoder(com.example.soundattract.integration.TaczReloadMessage::new)
                .consumerMainThread((msg, ctx) -> msg.handle(ctx))
                .add();
        INSTANCE.messageBuilder(com.example.soundattract.integration.TaczGunshotMessage.class, id++)
                .encoder((msg, buf) -> msg.toBytes(buf))
                .decoder(com.example.soundattract.integration.TaczGunshotMessage::new)
                .consumerMainThread((msg, ctx) -> msg.handle(ctx))
                .add();
    }

    public static void init() {
    }
}