package com.example.soundattract.gecko;

import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import java.util.Optional;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.camo.CamoRenderTypes;

@Environment(EnvType.CLIENT)
public class CamoGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    public CamoGeoLayer(GeoRenderer<T> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!(animatable instanceof LivingEntity living)) {
            return;
        }
        if (living.isInvisible()) return;

        Optional<CamouflageCapability> capOpt = CamouflageCapability.getCapability(living);
        if (capOpt.isEmpty()) return;

        CamouflageCapability capability = capOpt.get();
        if (capability.getLayers().isEmpty()) return;

        Optional<Integer> colorOpt = capability.getBlendedColor();
        float strength = capability.getVisualCamoStrength();
        
        if (colorOpt.isEmpty() || strength <= 0.05f) return;

        int color = colorOpt.get();
        long seed = capability.getDisplaySeed();
        
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(living);
        ResourceLocation maskLoc = getTextureResource(animatable);
        
        ResourceLocation camoTex = CamoTextureGenerator.getOrCreateMaskedSmudge(
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
            RenderType camoType = CamoRenderTypes.camoOverlay(camoTex);
            
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, camoType,
                bufferSource.getBuffer(camoType), partialTick, packedLight, packedOverlay,
                0xFFFFFFFF);
        }
    }
}
