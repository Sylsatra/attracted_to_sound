package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetEnchantmentsLootFunction; 
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;

public class ModLootTables {
    private static final List<Identifier> TARGET_TABLES = Arrays.asList(
            new Identifier("minecraft", "chests/ancient_city"),
            new Identifier("minecraft", "chests/end_city_treasure"),
            new Identifier("minecraft", "chests/stronghold_library"),
            new Identifier("minecraft", "chests/woodland_mansion"),
            new Identifier("minecraft", "chests/buried_treasure"),
            new Identifier("minecraft", "chests/desert_pyramid")
    );

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (TARGET_TABLES.contains(id)) {

                SetEnchantmentsLootFunction.Builder enchantFunctionBuilder = new SetEnchantmentsLootFunction.Builder()
                        .enchantment(ModEnchantments.CONCEAL, ConstantLootNumberProvider.create(1));

                LootPool.Builder poolBuilder = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.05f))
                        .with(ItemEntry.builder(net.minecraft.item.Items.ENCHANTED_BOOK)
                                .apply(enchantFunctionBuilder)
                        );

                tableBuilder.pool(poolBuilder.build());
            }
        });
    }

}