package com.example.soundattract.gecko;

import java.util.Optional;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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

        var cap = living.getCapability(com.example.soundattract.camo.CamouflageCapability.INSTANCE);
        if (!cap.isPresent()) return;

        com.example.soundattract.camo.CamouflageCapability capability = cap.orElse(null);
        if (capability == null || capability.getLayers().isEmpty()) return;

        Optional<Integer> colorOpt = capability.getBlendedColor();
        float strength = capability.getVisualCamoStrength();
        
        if (colorOpt.isEmpty() || strength <= 0.05f) return;

        int color = colorOpt.get();
        long seed = capability.getDisplaySeed();
        
        com.example.soundattract.camo.CamoLODUtil.LODResult lod = com.example.soundattract.camo.CamoLODUtil.getLOD(living);
        ResourceLocation maskLoc = getTextureResource(animatable);
        
        ResourceLocation camoTex = com.example.soundattract.camo.CamoTextureGenerator.getOrCreateMaskedSmudge(
            seed, 
            color, 
            strength, 
            maskLoc, 
            lod.resolution(),
            capability.getDisplayErosion(),
            capability.getDisplayHumidity(),
            capability.getDisplayTemperature()
        );

        if (camoTex != null) {
            RenderType camoType = com.example.soundattract.camo.CamoRenderTypes.camoOverlay(camoTex);
            
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, camoType, 
                bufferSource.getBuffer(camoType), partialTick, packedLight, packedOverlay, 
                1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
