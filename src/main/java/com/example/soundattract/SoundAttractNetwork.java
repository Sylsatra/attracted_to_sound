package com.example.soundattract;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SoundAttractNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;

    public static void init() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SoundAttractMod.MODID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );

        int id = 0;
        INSTANCE.registerMessage(
            id++,
            SoundMessage.class,
            SoundMessage::encode,
            SoundMessage::decode,
            SoundMessage::handle
        );
    }
}
