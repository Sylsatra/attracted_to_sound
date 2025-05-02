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
        try {
            Runnable logic = () -> {
                com.example.soundattract.integration.TaczIntegrationEvents.handleReloadFromClient(ctx != null && ctx.get() != null ? ctx.get().getSender() : null, gunId);
            };
            if (ctx != null && ctx.get() != null) {
                ctx.get().enqueueWork(logic);
                ctx.get().setPacketHandled(true);
            } else {
                logic.run();
            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczReloadMessage] Exception in handle for gunId={}", gunId, e);
            if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
        }
    }

    public String getGunId() {
        return gunId;
    }
}
