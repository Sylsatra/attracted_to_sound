package com.example.soundattract.loot;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.Registry;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModLootFunctions {
        public static final DeferredRegister<LootItemFunctionType> LOOT_FUNCTION_TYPES = DeferredRegister.create(Registry.LOOT_FUNCTION_REGISTRY, SoundAttractMod.MOD_ID);

    public static final RegistryObject<LootItemFunctionType> ENCHANT_WITH_CONCEAL = LOOT_FUNCTION_TYPES.register("enchant_with_conceal",
            () -> new LootItemFunctionType(new EnchantWithConcealFunction.Serializer()));

    public static void register(IEventBus bus) {
        LOOT_FUNCTION_TYPES.register(bus);
    }
}
