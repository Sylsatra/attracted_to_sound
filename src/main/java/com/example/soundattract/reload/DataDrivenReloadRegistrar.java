package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.resource.ListenerKey;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class DataDrivenReloadRegistrar {

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(ListenerKey.create(Identifier.tryBuild(SoundAttractMod.MOD_ID, "sound_definitions")), new SoundDefinitionsReloadListener());
        event.addRetainedListener(ListenerKey.create(Identifier.tryBuild(SoundAttractMod.MOD_ID, "armor_colors")), new ArmorColorsReloadListener());
        event.addRetainedListener(ListenerKey.create(Identifier.tryBuild(SoundAttractMod.MOD_ID, "mob_profiles")), new MobProfilesReloadListener());
        event.addRetainedListener(ListenerKey.create(Identifier.tryBuild(SoundAttractMod.MOD_ID, "player_profiles")), new PlayerProfilesReloadListener());
    }
}
