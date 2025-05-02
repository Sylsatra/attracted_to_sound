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
        try {
            Runnable logic = () -> {
                com.example.soundattract.integration.TaczIntegrationEvents.handleGunshotFromClient(ctx != null && ctx.get() != null ? ctx.get().getSender() : null, gunId, attachmentId);
            };
            if (ctx != null && ctx.get() != null) {
                ctx.get().enqueueWork(logic);
                ctx.get().setPacketHandled(true);
            } else {
                logic.run();
            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczGunshotMessage] Exception in handle for gunId={}, attachmentId={}", gunId, attachmentId, e);
            if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
        }
    }

    public String getGunId() { return gunId; }
    public String getAttachmentId() { return attachmentId; }
}
