package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LootEvents {

    private static final Set<ResourceLocation> TARGET_TABLES = ImmutableSet.of(
            BuiltInLootTables.ANCIENT_CITY,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.NETHER_BRIDGE,
            BuiltInLootTables.END_CITY_TREASURE,
            BuiltInLootTables.STRONGHOLD_CROSSING,
            BuiltInLootTables.BASTION_OTHER
    );

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        if (TARGET_TABLES.contains(event.getName())) {


            SoundAttractMod.LOGGER.debug("Skipping direct loot injection for {} (handled by GLM)", event.getName());
        }
    }
}
