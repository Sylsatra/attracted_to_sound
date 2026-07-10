package com.example.soundattract.mixin.gecko;

import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.animatable.GeoItem;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GeoArmorRenderer.class)
public abstract class MixinGeoArmorRenderer<T extends Item & GeoItem> {
    private static final DataTicket<LivingEntity> SOUNDATTRACT_ARMOR_ENTITY =
            DataTicket.create("soundattract_geo_armor_entity", LivingEntity.class);
    private static final DataTicket<ItemStack> SOUNDATTRACT_ARMOR_STACK =
            DataTicket.create("soundattract_geo_armor_stack", ItemStack.class);

    private boolean soundattract$renderingCamo;

    @Inject(
            method = "captureDefaultRenderState(Lnet/minecraft/world/item/Item;Lcom/geckolib/renderer/GeoArmorRenderer$RenderData;Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;F)V",
            at = @At("TAIL"),
            remap = false
    )
    private void soundattract$captureCamoRenderData(T animatable,
                                                    GeoArmorRenderer.RenderData renderData,
                                                    HumanoidRenderState renderState,
                                                    float partialTick,
                                                    CallbackInfo ci) {
        GeoRenderState geoState = (GeoRenderState) renderState;
        geoState.addGeckolibData(SOUNDATTRACT_ARMOR_ENTITY, renderData.entity());
        geoState.addGeckolibData(SOUNDATTRACT_ARMOR_STACK, renderData.itemStack());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "submitRenderTasks", at = @At("RETURN"), remap = false)
    private void soundattract$renderCamoOverlay(RenderPassInfo<?> renderPassInfo,
                                                OrderedSubmitNodeCollector submitNodeCollector,
                                                RenderType renderType,
                                                CallbackInfo ci) {
        if (soundattract$renderingCamo) return;

        if (!(renderPassInfo.renderState() instanceof HumanoidRenderState renderState)) {
            return;
        }
        GeoRenderState geoState = renderPassInfo.renderState();

        LivingEntity living = geoState.getGeckolibData(SOUNDATTRACT_ARMOR_ENTITY);
        if (living == null || living.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
            return;
        }

        ItemStack stack = geoState.getGeckolibData(SOUNDATTRACT_ARMOR_STACK);
        if (stack == null || stack.isEmpty() || !CamoUtil.hasCustomTag(stack)) {
            return;
        }

        CompoundTag tag = CamoUtil.getCustomTag(stack);
        if (!tag.contains("soundattract:CamoLayers")) {
            return;
        }

        ListTag list = tag.getListOrEmpty("soundattract:CamoLayers");
        CamoUtil.BlendedCamoData blended = CamoUtil.getBlendedDataFromTag(list);
        if (blended.color().isEmpty()) {
            return;
        }

        EquipmentSlot slot = geoState.getGeckolibData(GeoArmorRenderer.CURRENT_SLOT);
        ArmorValidationTracker.markSlotValidated(living, slot);

        float strength = blended.strength() * 2.0F;
        if (strength <= 0.05F) {
            return;
        }

        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(living);
        if (!lod.shouldRender()) {
            return;
        }

        Identifier smudgeTex = CamoTextureGenerator.getOrCreateSmudge(
                blended.seed(),
                blended.color().get(),
                strength,
                lod.resolution(),
                blended.erosion(),
                blended.humidity(),
                blended.temp());
        if (smudgeTex == null) {
            return;
        }

        RenderType camoType = CamoRenderTypes.camoOverlay(smudgeTex);
        soundattract$renderingCamo = true;
        try {
            ((GeoRenderer) (Object) this).submitRenderTasks((RenderPassInfo) renderPassInfo, submitNodeCollector, camoType);
        } finally {
            soundattract$renderingCamo = false;
        }
    }
}
