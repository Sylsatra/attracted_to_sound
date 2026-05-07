package com.example.soundattract.network;

import com.example.soundattract.camo.CamoLayer;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public record CamoSyncMessage(int entityId, List<CamoLayer> layers) {
    public static final ResourceLocation ID = new ResourceLocation("soundattract", "camo_sync");

    public static void write(FriendlyByteBuf buf, CamoSyncMessage msg) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.layers.size());
        for (CamoLayer layer : msg.layers) {
            buf.writeNbt(layer.save());
        }
    }

    public static CamoSyncMessage read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int size = buf.readInt();
        List<CamoLayer> layers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                layers.add(CamoLayer.load(tag));
            }
        }
        return new CamoSyncMessage(entityId, layers);
    }

    public static void handle(CamoSyncMessage msg, Minecraft client) {
        if (client.level == null) return;
        Entity entity = client.level.getEntity(msg.entityId);
        if (entity instanceof LivingEntity living) {
            CamouflageCapability.getCapability(living).ifPresent(c -> c.setLayers(msg.layers));
        }
    }
}
