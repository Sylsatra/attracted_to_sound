package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.client.model.EntityModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.example.soundattract.camo.CamoRenderLayer;
import com.example.soundattract.camo.GenericMobCamoLayer;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CamoClientEvents {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinType : new String[]{"default", "slim"}) {
            PlayerRenderer renderer = event.getSkin(skinType);
            if (renderer != null) {
                renderer.addLayer(new CamoRenderLayer((RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) renderer));
            }
        }

        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            tryAddLayer(event, type);
        }
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
