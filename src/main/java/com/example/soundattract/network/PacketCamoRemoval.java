package com.example.soundattract.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Camo removal packet for 1.20.1 Fabric.
 * Matches Forge behavior: removes camo tags from specific armor slot.
 */
public record PacketCamoRemoval(
        EquipmentSlot slot
) {
    public static void write(FriendlyByteBuf buf, PacketCamoRemoval msg) {
        buf.writeEnum(msg.slot);
    }

    public static PacketCamoRemoval read(FriendlyByteBuf buf) {
        return new PacketCamoRemoval(buf.readEnum(EquipmentSlot.class));
    }

    public static void handle(PacketCamoRemoval msg, net.minecraft.server.level.ServerPlayer player) {
        if (player != null) {
            ItemStack stack = player.getItemBySlot(msg.slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                if (stack.hasTag()) {
                    var tag = stack.getTag();
                    if (tag.contains("soundattract:CamoLayers") || tag.contains("soundattract:CamoStrength")) {
                        tag.remove("soundattract:CamoLayers");
                        tag.remove("soundattract:CamoStrength");
                        tag.remove("soundattract:CamoColor");
                        tag.remove("soundattract:CamoSeed");

                        player.inventoryMenu.broadcastChanges();
                    }
                }
            }
        }
    }
}
