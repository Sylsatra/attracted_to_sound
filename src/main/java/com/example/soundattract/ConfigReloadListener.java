package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ConfigReloadListener {
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(SoundAttractMod.MOD_ID)) {
            SoundAttractConfig.bakeConfig();
        }
    }
}
