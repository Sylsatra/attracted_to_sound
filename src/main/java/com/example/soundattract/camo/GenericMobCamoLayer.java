package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import java.util.function.Function;

/**
 * Generic render layer for Humanoid-style mobs (Zombies, Skeletons, etc.)
 */
public class GenericMobCamoLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private final Function<S, Identifier> textureProvider;

    public GenericMobCamoLayer(RenderLayerParent<S, M> parent, Function<S, Identifier> textureProvider) {
        super(parent);
        this.textureProvider = textureProvider;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, S state, float yRot, float xRot) {
        if (state.isInvisible) return;

        CamoRenderData camo = state.getRenderDataOrDefault(CamoRenderState.CAMO_DATA, CamoRenderData.EMPTY);
        if (!camo.present() || camo.color().isEmpty()) return;

            int color = camo.color().get();
            Identifier entityTex = textureProvider.apply(state);
            if (entityTex == null) return;
            M model = this.getParentModel();

            if (camo.upperStrength() > 0) {
                Identifier upperSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.seed(), color, camo.upperStrength(), entityTex, camo.resolution(), camo.erosion(), camo.humidity(), camo.temperature());
                if (upperSmudge != null) {
                    boolean lLegVis = model.leftLeg != null && model.leftLeg.visible;
                    boolean rLegVis = model.rightLeg != null && model.rightLeg.visible;
                    
                    if (model.leftLeg != null) model.leftLeg.visible = false;
                    if (model.rightLeg != null) model.rightLeg.visible = false;
    
                    renderColoredCutoutModel(model, upperSmudge, poseStack, submitNodeCollector, packedLight, state, 0xFFFFFFFF, OverlayTexture.NO_OVERLAY);
                    
                    if (model.leftLeg != null) model.leftLeg.visible = lLegVis;
                    if (model.rightLeg != null) model.rightLeg.visible = rLegVis;
                }
            }

            if (camo.lowerStrength() > 0) {
                Identifier lowerSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.seed(), color, camo.lowerStrength(), entityTex, camo.resolution(), camo.erosion(), camo.humidity(), camo.temperature());
                if (lowerSmudge != null) {
                    boolean headVis = model.head != null && model.head.visible;
                    boolean hatVis = model.hat != null && model.hat.visible;
                    boolean bodyVis = model.body != null && model.body.visible;
                    boolean lArmVis = model.leftArm != null && model.leftArm.visible;
                    boolean rArmVis = model.rightArm != null && model.rightArm.visible;
    
                    if (model.head != null) model.head.visible = false;
                    if (model.hat != null) model.hat.visible = false;
                    if (model.body != null) model.body.visible = false;
                    if (model.leftArm != null) model.leftArm.visible = false;
                    if (model.rightArm != null) model.rightArm.visible = false;
    
                    renderColoredCutoutModel(model, lowerSmudge, poseStack, submitNodeCollector, packedLight, state, 0xFFFFFFFF, OverlayTexture.NO_OVERLAY);
    
                    if (model.head != null) model.head.visible = headVis;
                    if (model.hat != null) model.hat.visible = hatVis;
                    if (model.body != null) model.body.visible = bodyVis;
                    if (model.leftArm != null) model.leftArm.visible = lArmVis;
                    if (model.rightArm != null) model.rightArm.visible = rArmVis;
                }
            }
    }

    /**
     * Specialized layer for Illager-style mobs (Pillager, Evoker, etc.)
     */
    public static class Illager extends RenderLayer<IllagerRenderState, IllagerModel<IllagerRenderState>> {
        private final Function<IllagerRenderState, Identifier> textureProvider;

        public Illager(RenderLayerParent<IllagerRenderState, IllagerModel<IllagerRenderState>> parent, Function<IllagerRenderState, Identifier> textureProvider) {
            super(parent);
            this.textureProvider = textureProvider;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, IllagerRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            CamoRenderData camo = state.getRenderDataOrDefault(CamoRenderState.CAMO_DATA, CamoRenderData.EMPTY);
            if (!camo.present() || camo.color().isEmpty()) return;
            Identifier tex = getOrCreateSmudge(state, camo);
            if (tex != null) {
                renderColoredCutoutModel(this.getParentModel(), tex, poseStack, submitNodeCollector, packedLight, state, 0xFFFFFFFF, OverlayTexture.NO_OVERLAY);
            }
        }

        private Identifier getOrCreateSmudge(IllagerRenderState state, CamoRenderData camo) {
            Identifier entityTex = textureProvider.apply(state);
            if (entityTex == null) return null;
            return CamoTextureGenerator.getOrCreateMaskedSmudge(camo.seed(), camo.color().orElse(0), camo.strength(), entityTex, camo.resolution(), camo.erosion(), camo.humidity(), camo.temperature());
        }
    }

    /**
     * Specialized layer for Villagers and Wandering Traders
     */
    public static class VillagerLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
        private final Function<VillagerRenderState, Identifier> textureProvider;

        public VillagerLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent, Function<VillagerRenderState, Identifier> textureProvider) {
            super(parent);
            this.textureProvider = textureProvider;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, VillagerRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            CamoRenderData camo = state.getRenderDataOrDefault(CamoRenderState.CAMO_DATA, CamoRenderData.EMPTY);
            if (!camo.present() || camo.color().isEmpty()) return;
            Identifier entityTex = textureProvider.apply(state);
            if (entityTex == null) return;
            Identifier tex = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.seed(), camo.color().orElse(0), camo.strength(), entityTex, camo.resolution(), camo.erosion(), camo.humidity(), camo.temperature());
            if (tex != null) {
                renderColoredCutoutModel(this.getParentModel(), tex, poseStack, submitNodeCollector, packedLight, state, 0xFFFFFFFF, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
