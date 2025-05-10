package com.example.soundattract.integration;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class TaczGunshotMessage {

    public static final Identifier ID = new Identifier("soundattract", "tacz_gunshot");
    private final String gunId;
    private final String attachmentId;

    public TaczGunshotMessage(String gunId, String attachmentId) {
        this.gunId = gunId;
        this.attachmentId = attachmentId;
    }

    public TaczGunshotMessage(PacketByteBuf buf) {
        this.gunId = buf.readString(64);
        this.attachmentId = buf.readString(64);
    }
    public void encode(PacketByteBuf buf) {
        buf.writeString(gunId, 64);
        buf.writeString(attachmentId == null ? "" : attachmentId, 64);
    }
    public static TaczGunshotMessage decode(PacketByteBuf buf) {
        return new TaczGunshotMessage(buf);
    }

    public static void register() {
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczGunshotMessage] register() called, registering server packet handler. Thread: {} Env: {}", Thread.currentThread().getName(), FabricLoader.getInstance().getEnvironmentType().toString());
}
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczGunshotMessage] register() called, registering server packet handler");
    }
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            TaczGunshotMessage msg = TaczGunshotMessage.decode(buf);
            server.execute(() -> {
                if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczGunshotMessage] Server received gunshot packet for player: {} gunId: {} attachmentId: {}", player != null ? player.getName().getString() : "null", msg.gunId, msg.attachmentId);
                }
                try {
                    TaczIntegrationEvents.handleGunshotFromClient(player, msg.gunId, msg.attachmentId);
                } catch (Exception e) {
                    com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczGunshotMessage] Exception in handle for gunId={}, attachmentId={}", msg.gunId, msg.attachmentId, e);
                }
            });
        });
    }

    public String getGunId() { return gunId; }
    public String getAttachmentId() { return attachmentId; }
}
