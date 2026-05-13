package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public class CamoClientEvents {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        event.getSkins().forEach(skin -> {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CamoRenderLayer(renderer));
            }
        });

        tryAddLayer(event, EntityType.ZOMBIE);
        tryAddLayer(event, EntityType.HUSK);
        tryAddLayer(event, EntityType.DROWNED);
        tryAddLayer(event, EntityType.SKELETON);
        tryAddLayer(event, EntityType.STRAY);
        tryAddLayer(event, EntityType.WITHER_SKELETON);
        tryAddLayer(event, EntityType.PIGLIN);
        tryAddLayer(event, EntityType.PIGLIN_BRUTE);
        tryAddLayer(event, EntityType.ZOMBIFIED_PIGLIN);
        tryAddLayer(event, EntityType.PILLAGER);
        tryAddLayer(event, EntityType.VINDICATOR);
        tryAddLayer(event, EntityType.EVOKER);
        tryAddLayer(event, EntityType.ILLUSIONER);
        tryAddLayer(event, EntityType.VILLAGER);
        tryAddLayer(event, EntityType.WANDERING_TRADER);
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> void tryAddLayer(EntityRenderersEvent.AddLayers event, EntityType<?> type) {
        EntityRenderer<?> renderer;
        try {
            renderer = event.getRenderer((EntityType<? extends T>) type);
        } catch (Exception e) {
            return;
        }

        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            var model = livingRenderer.getModel();
            
            if (model instanceof HumanoidModel humanoid) {

                if (type != EntityType.PLAYER) {
                    livingRenderer.addLayer(new GenericMobCamoLayer(livingRenderer));
                }
            } else if (model instanceof IllagerModel illager) {
                livingRenderer.addLayer(new GenericMobCamoLayer.Illager((RenderLayerParent<AbstractIllager, IllagerModel<AbstractIllager>>) livingRenderer));
            } else if (model instanceof VillagerModel villager) {
                livingRenderer.addLayer(new GenericMobCamoLayer.VillagerLayer(livingRenderer));
            }
        }
    }
}
