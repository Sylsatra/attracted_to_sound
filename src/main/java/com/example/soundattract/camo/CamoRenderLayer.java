package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CamoRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public CamoRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;

        CamoRenderData camo = state.getRenderDataOrDefault(CamoRenderState.CAMO_DATA, CamoRenderData.EMPTY);
        if (!camo.present() || camo.color().isEmpty()) return;

            int color = camo.color().get();
            Identifier skinTex = state.skin.body().texturePath();
            PlayerModel model = this.getParentModel();

            if (camo.upperStrength() > 0) {
                Identifier upperSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.seed(), color, camo.upperStrength(), skinTex, camo.resolution(), camo.erosion(), camo.humidity(), camo.temperature());
                if (upperSmudge != null) {
                    boolean lLegVis = model.leftLeg.visible;
                    boolean rLegVis = model.rightLeg.visible;
                    boolean lPantsVis = model.leftPants.visible;
                    boolean rPantsVis = model.rightPants.visible;

                    model.leftLeg.visible = false;
                    model.rightLeg.visible = false;
                    model.leftPants.visible = false;
                    model.rightPants.visible = false;
    
                    renderColoredCutoutModel(model, upperSmudge, poseStack, submitNodeCollector, packedLight, state, 0xFFFFFFFF, OverlayTexture.pack(0, 10));
                    
                    model.leftLeg.visible = lLegVis;
                    model.rightLeg.visible = rLegVis;
                    model.leftPants.visible = lPantsVis;
                    model.rightPants.visible = rPantsVis;
                }
            }

            if (camo.lowerStrength() > 0) {
                Identifier lowerSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.seed(), color, camo.lowerStrength(), skinTex, camo.resolution(), camo.erosion(), camo.humidity(), camo.temperature());
                if (lowerSmudge != null) {
                    boolean headVisible = model.head.visible;
                    boolean hatVisible = model.hat.visible;
                    boolean bodyVisible = model.body.visible;
                    boolean jacketVisible = model.jacket.visible;
                    boolean leftArmVisible = model.leftArm.visible;
                    boolean rightArmVisible = model.rightArm.visible;
                    boolean leftSleeveVisible = model.leftSleeve.visible;
                    boolean rightSleeveVisible = model.rightSleeve.visible;
    
                    model.head.visible = false;
                    model.hat.visible = false;
                    model.body.visible = false;
                    model.jacket.visible = false;
                    model.leftArm.visible = false;
                    model.rightArm.visible = false;
                    model.leftSleeve.visible = false;
                    model.rightSleeve.visible = false;
    
                    renderColoredCutoutModel(model, lowerSmudge, poseStack, submitNodeCollector, packedLight, state, 0xFFFFFFFF, OverlayTexture.pack(0, 10));
    
                    model.head.visible = headVisible;
                    model.hat.visible = hatVisible;
                    model.body.visible = bodyVisible;
                    model.jacket.visible = jacketVisible;
                    model.leftArm.visible = leftArmVisible;
                    model.rightArm.visible = rightArmVisible;
                    model.leftSleeve.visible = leftSleeveVisible;
                    model.rightSleeve.visible = rightSleeveVisible;
                }
            }
    }
}
