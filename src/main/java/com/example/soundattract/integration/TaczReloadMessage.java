package com.example.soundattract.integration;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class TaczReloadMessage {
    public static final Identifier ID = new Identifier("soundattract", "tacz_reload");
    private final String gunId;

    public TaczReloadMessage(String gunId) {
        this.gunId = gunId;
    }

    public TaczReloadMessage(PacketByteBuf buf) {
        this.gunId = buf.readString(64);
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(gunId, 64);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            String gunId = buf.readString(64);
            server.execute(() -> {
                try {
                    TaczIntegrationEvents.handleReloadFromClient(player, gunId);
                } catch (Exception e) {
                    com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczReloadMessage] Exception in handle for gunId={}", gunId, e);
                }
            });
        });
    }

    public String getGunId() {
        return gunId;
    }
}
