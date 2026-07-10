package com.example.soundattract.camo;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CamoRenderTypes {
    private CamoRenderTypes() {
    }

    public static RenderType camoOverlay(Identifier texture) {
        Identifier resolved = texture == null ? Identifier.tryBuild("minecraft", "textures/block/stone.png") : texture;
        return RenderType.create("soundattract_camo_overlay",
                RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                        .withTexture("Sampler0", resolved)
                        .useLightmap()
                        .useOverlay()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING_FORWARD)
                        .sortOnUpload()
                        .createRenderSetup());
    }
}
