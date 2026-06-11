package com.example.soundattract.gecko;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.camo.CamouflageCapability;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class CamoGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    public CamoGeoLayer(GeoRenderer<T> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!(animatable instanceof LivingEntity living)) {
            return;
        }
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(living);
        if (!lod.shouldRender() || living.isInvisible()) {
            return;
        }

        CamouflageCapability capability = living.getData(CamoAttachments.CAMOUFLAGE);
        if (capability == null || capability.getLayers().isEmpty()) return;

        Optional<Integer> colorOpt = capability.getBlendedColor();
        float strength = capability.getVisualCamoStrength();
        if (colorOpt.isEmpty() || strength <= 0.05f) return;

        ResourceLocation maskLoc = getTextureResource(animatable);
        ResourceLocation camoTex = CamoTextureGenerator.getOrCreateMaskedSmudge(
                capability.getDisplaySeed(),
                colorOpt.get(),
                strength,
                maskLoc,
                lod.resolution(),
                capability.getDisplayErosion(),
                capability.getDisplayHumidity(),
                capability.getDisplayTemperature());

        if (camoTex != null) {
            RenderType camoType = CamoRenderTypes.camoOverlay(camoTex);
            getRenderer().reRender(
                    bakedModel,
                    poseStack,
                    bufferSource,
                    animatable,
                    camoType,
                    bufferSource.getBuffer(camoType),
                    partialTick,
                    packedLight,
                    packedOverlay,
                    0xFFFFFFFF);
        }
    }
}
