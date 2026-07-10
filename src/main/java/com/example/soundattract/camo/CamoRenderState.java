package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

public final class CamoRenderState {
    public static final ContextKey<CamoRenderData> CAMO_DATA = new ContextKey<>(Identifier.tryBuild(SoundAttractMod.MOD_ID, "camo_data"));
    public static final ContextKey<CamoLODUtil.LODResult> CAMO_LOD = new ContextKey<>(Identifier.tryBuild(SoundAttractMod.MOD_ID, "camo_lod"));

    private CamoRenderState() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class, (LivingEntity entity, LivingEntityRenderState state) -> {
            CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
            state.setRenderData(CAMO_LOD, lod);
            CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
            if (!lod.shouldRender() || camo == null || camo.getLayers().isEmpty()) {
                state.setRenderData(CAMO_DATA, CamoRenderData.EMPTY);
                return;
            }

            float upperStrength = 0.0F;
            float lowerStrength = 0.0F;
            for (CamoLayer layer : camo.getLayers()) {
                upperStrength += layer.upperDurability();
                lowerStrength += layer.lowerDurability();
            }
            upperStrength = Math.min(1.0F, upperStrength);
            lowerStrength = Math.min(1.0F, lowerStrength);

            state.setRenderData(CAMO_DATA, new CamoRenderData(
                    true,
                    camo.getDisplaySeed(),
                    camo.getBlendedColor(),
                    camo.getVisualCamoStrength(),
                    upperStrength,
                    lowerStrength,
                    camo.getDisplayErosion(),
                    camo.getDisplayHumidity(),
                    camo.getDisplayTemperature(),
                    lod.resolution(),
                    null));
        });
    }
}
