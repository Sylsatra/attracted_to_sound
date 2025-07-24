package com.example.soundattract;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class SoundAttractNetwork {

    public static final Identifier SOUND_MESSAGE_ID = new Identifier(SoundAttractMod.MOD_ID, "sound_message");
    public static void sendSoundMessageToServer(SoundMessage msg) {
        PacketByteBuf buf = PacketByteBufs.create();
        SoundMessage.encode(msg, buf);
        ClientPlayNetworking.send(SOUND_MESSAGE_ID, buf);
    }
}