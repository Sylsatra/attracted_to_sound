package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DataDrivenReloadRegistrar {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SoundDefinitionsReloadListener());
        event.addListener(new ArmorColorsReloadListener());
        event.addListener(new MobProfilesReloadListener());
        event.addListener(new PlayerProfilesReloadListener());
        event.addListener(new TaczGunsReloadListener());
        event.addListener(new PointBlankGunsReloadListener());
    }
}
