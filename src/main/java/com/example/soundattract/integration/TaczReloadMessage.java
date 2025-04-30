package com.example.soundattract.integration;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class TaczReloadMessage {
    private final String gunId;

    public TaczReloadMessage(String gunId) {
        this.gunId = gunId;
    }

    public TaczReloadMessage(FriendlyByteBuf buf) {
        this.gunId = buf.readUtf(64);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(gunId, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.soundattract.integration.TaczIntegrationEvents.handleReloadFromClient(ctx.get().getSender(), gunId);
        });
        ctx.get().setPacketHandled(true);
    }

    public String getGunId() {
        return gunId;
    }
}
