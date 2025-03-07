package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractionEvents {

    @SubscribeEvent
    public static void onPlaySoundEvent(PlaySoundEvent event) {
        if (event.getSound() == null) {
            return;
        }
    
        if (event.getSound() instanceof net.minecraft.client.resources.sounds.AbstractSoundInstance soundInstance) {
            ResourceLocation soundRL = soundInstance.getLocation();
            if (soundRL == null) {
                return;
            }
            double x = event.getSound().getX();
            double y = event.getSound().getY();
            double z = event.getSound().getZ();
    
            Level clientWorld = Minecraft.getInstance().level;
            if (clientWorld == null) {
                return;
            }
            ResourceLocation dim = clientWorld.dimension().location();
    
            SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim);
            SoundAttractNetwork.INSTANCE.sendToServer(msg);
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SoundTracker.tick();
        }
    }
    
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (event.getWorld().isClientSide()) {
            return;
        }
    
        var key = mob.getType().getRegistryName();
        if (key == null) return;
    
        String idStr = key.toString();
        if (!SoundAttractConfig.ATTRACTED_ENTITIES.contains(idStr)) {
            return;
        }
    
        mob.goalSelector.addGoal(2, new AttractionGoal(
            mob,
            1.0D,
            SoundAttractConfig.SOUND_HEARING_RADIUS
        ));
    }
}
