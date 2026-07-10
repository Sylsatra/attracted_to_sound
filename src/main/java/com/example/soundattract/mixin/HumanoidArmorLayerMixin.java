package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderState;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Shadow
    private HumanoidModel<HumanoidRenderState> getArmorModel(HumanoidRenderState state, EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void soundattract$hideArmorWhenInvisible(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!state.isInvisible || !SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
            return;
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = switch (slot) {
                case HEAD -> state.headEquipment;
                case CHEST -> state.chestEquipment;
                case LEGS -> state.legsEquipment;
                case FEET -> state.feetEquipment;
                default -> ItemStack.EMPTY;
            };
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.slot() == slot) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void soundattract$renderArmorCamo(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state.isInvisible) {
            return;
        }

        CamoLODUtil.LODResult lod = state.getRenderDataOrDefault(CamoRenderState.CAMO_LOD, new CamoLODUtil.LODResult(false, 16));
        if (!lod.shouldRender()) {
            return;
        }

        renderArmorCamoPiece(poseStack, submitNodeCollector, packedLight, state, EquipmentSlot.HEAD, state.headEquipment, lod.resolution());
        renderArmorCamoPiece(poseStack, submitNodeCollector, packedLight, state, EquipmentSlot.CHEST, state.chestEquipment, lod.resolution());
        renderArmorCamoPiece(poseStack, submitNodeCollector, packedLight, state, EquipmentSlot.LEGS, state.legsEquipment, lod.resolution());
        renderArmorCamoPiece(poseStack, submitNodeCollector, packedLight, state, EquipmentSlot.FEET, state.feetEquipment, lod.resolution());
    }

    private void renderArmorCamoPiece(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidRenderState state, EquipmentSlot slot, ItemStack stack, int resolution) {
        if (stack == null || stack.isEmpty() || !CamoUtil.hasCustomTag(stack)) {
            return;
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) {
            return;
        }

        CompoundTag tag = CamoUtil.getCustomTag(stack);
        CamoUtil.BlendedCamoData blended;
        if (tag.contains("soundattract:CamoLayers")) {
            ListTag list = tag.getListOrEmpty("soundattract:CamoLayers");
            blended = CamoUtil.getBlendedDataFromTag(list);
        } else if (tag.contains("soundattract:CamoStrength")) {
            blended = new CamoUtil.BlendedCamoData(
                    java.util.Optional.of(tag.getIntOr("soundattract:CamoColor", 0xFFFFFF)),
                    tag.getFloatOr("soundattract:CamoStrength", 0.0F),
                    tag.getLongOr("soundattract:CamoSeed", 0L),
                    0.0F,
                    0.0F,
                    0.0F);
        } else {
            return;
        }

        if (blended.color().isEmpty()) {
            return;
        }

        float strength = blended.strength() * 2.0F;
        if (strength <= 0.05F) {
            return;
        }

        Identifier smudgeTex = CamoTextureGenerator.getOrCreateSmudge(
                blended.seed(),
                blended.color().get(),
                strength,
                resolution,
                blended.erosion(),
                blended.humidity(),
                blended.temp());
        if (smudgeTex == null) {
            return;
        }

        EquipmentClientInfo.LayerType layerType = getLayerType(state, slot);
        HumanoidModel<HumanoidRenderState> armorModel = getArmorModel(state, slot);
        equipmentRenderer.renderLayers(layerType, equippable.assetId().get(), armorModel, state, stack, poseStack,
                submitNodeCollector, packedLight, smudgeTex, 0xFFFFFFFF, state.outlineColor);
    }

    private static EquipmentClientInfo.LayerType getLayerType(HumanoidRenderState state, EquipmentSlot slot) {
        if (state.isBaby && state.entityType != EntityTypes.ARMOR_STAND) {
            return EquipmentClientInfo.LayerType.HUMANOID_BABY;
        }
        return slot == EquipmentSlot.LEGS
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                : EquipmentClientInfo.LayerType.HUMANOID;
    }
}
