package com.example.soundattract.integration;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class TaczGunshotMessage {
    private final String gunId;
    private final String attachmentId;

    public TaczGunshotMessage(String gunId, String attachmentId) {
        this.gunId = gunId;
        this.attachmentId = attachmentId;
    }

    public TaczGunshotMessage(FriendlyByteBuf buf) {
        this.gunId = buf.readUtf(64);
        this.attachmentId = buf.readUtf(64);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(gunId, 64);
        buf.writeUtf(attachmentId == null ? "" : attachmentId, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.example.soundattract.integration.TaczIntegrationEvents.handleGunshotFromClient(ctx.get().getSender(), gunId, attachmentId);
        });
        ctx.get().setPacketHandled(true);
    }

    public String getGunId() { return gunId; }
    public String getAttachmentId() { return attachmentId; }
}
