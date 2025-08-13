package com.example.soundattract.loot;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.SetEnchantmentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public class ModLootTables {

    private static final List<Identifier> TARGET_LOOT_TABLES = List.of(
            Identifier.of("minecraft", "chests/ancient_city"),
            Identifier.of("minecraft", "chests/end_city_treasure"),
            Identifier.of("minecraft", "chests/stronghold_library"),
            Identifier.of("minecraft", "chests/woodland_mansion"),
            Identifier.of("minecraft", "chests/buried_treasure"),
            Identifier.of("minecraft", "chests/desert_pyramid"),
            Identifier.of("minecraft", "vaults/trial_chamber/reward"),
            Identifier.of("minecraft", "vaults/trial_chamber/reward_ominous")
    );

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Identifier id = key.getValue();
            if (!TARGET_LOOT_TABLES.contains(id)) {
                return;
            }

            Optional<RegistryEntry<Enchantment>> concealEntryOptional = registries.getOptionalWrapper(RegistryKeys.ENCHANTMENT)
                    .flatMap(wrapper -> wrapper.getOptional(ModEnchantments.CONCEAL));

            if (concealEntryOptional.isEmpty()) {
                SoundAttractMod.LOGGER.error("Failed to find 'conceal' enchantment in the registry.");
                return;
            }
            RegistryEntry<Enchantment> concealEntry = concealEntryOptional.get();






            LootFunction setEnchantmentFunctionForBook = new SetEnchantmentsLootFunction.Builder()
                    .enchantment(concealEntry, ConstantLootNumberProvider.create(1))
                    .build();


            LootPool.Builder bookPoolBuilder = LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1))
                    .conditionally(RandomChanceLootCondition.builder(0.05f))
                    .with(ItemEntry.builder(Items.ENCHANTED_BOOK))
                    .apply(setEnchantmentFunctionForBook);


            tableBuilder.pool(bookPoolBuilder.build());




            LootFunction conditionalSetEnchantmentFunction = new SetEnchantmentsLootFunction.Builder()
                    .enchantment(concealEntry, ConstantLootNumberProvider.create(1))
                    .conditionally(RandomChanceLootCondition.builder(0.05f))
                    .build();


            tableBuilder.modifyPools(poolBuilder -> {
                poolBuilder.apply(conditionalSetEnchantmentFunction);
            });
        });
    }
}