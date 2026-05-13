package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class DataDrivenReloadRegistrar {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SoundDefinitionsReloadListener());
        event.addListener(new ArmorColorsReloadListener());
        event.addListener(new MobProfilesReloadListener(event.getRegistryAccess()));
        event.addListener(new PlayerProfilesReloadListener(event.getRegistryAccess()));
        event.addListener(new TaczGunsReloadListener());
        event.addListener(new PointBlankGunsReloadListener());
    }
}
