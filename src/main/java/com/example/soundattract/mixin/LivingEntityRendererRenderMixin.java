package com.example.soundattract.mixin;

import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.camo.GenericMobCamoLayer;
import com.example.soundattract.network.PacketCamoRemoval;
import com.example.soundattract.network.SoundAttractNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRenderMixin<T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderPre(T entity, float entityYaw, float partialTicks, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        ArmorValidationTracker.clearForEntity(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(T entity, float entityYaw, float partialTicks, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof Player player) {
            if (player != Minecraft.getInstance().player) return;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;

                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;

                if (stack.hasTag() && (stack.getTag().contains("soundattract:CamoLayers") || stack.getTag().contains("soundattract:CamoStrength"))) {
                    if (!ArmorValidationTracker.wasSlotValidated(player, slot)) {
                        SoundAttractNetwork.sendCamoRemovalToServer(new PacketCamoRemoval(slot));

                        stack.getTag().remove("soundattract:CamoLayers");
                        stack.getTag().remove("soundattract:CamoStrength");
                        stack.getTag().remove("soundattract:CamoColor");
                        stack.getTag().remove("soundattract:CamoSeed");
                    }
                }
            }
        }
    }
}
