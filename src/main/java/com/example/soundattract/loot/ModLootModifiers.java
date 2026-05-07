package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Set;

public class ModLootModifiers {

    private static final Set<ResourceLocation> ADD_CONCEAL_BOOK_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/ancient_city"),
            new ResourceLocation("minecraft", "chests/end_city_treasure"),
            new ResourceLocation("minecraft", "chests/stronghold_library"),
            new ResourceLocation("minecraft", "chests/woodland_mansion"),
            new ResourceLocation("minecraft", "chests/buried_treasure"),
            new ResourceLocation("minecraft", "chests/desert_pyramid")
    );

    private static final Set<ResourceLocation> ENCHANT_ARMOR_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/ancient_city"),
            new ResourceLocation("minecraft", "chests/bastion_treasure"),
            new ResourceLocation("minecraft", "chests/nether_bridge")
    );

    private static final Set<ResourceLocation> ENCHANT_TOOL_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/ancient_city"),
            new ResourceLocation("minecraft", "chests/end_city_treasure"),
            new ResourceLocation("minecraft", "chests/stronghold_crossing"),
            new ResourceLocation("minecraft", "chests/bastion_other")
    );

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) return;

            if (ADD_CONCEAL_BOOK_TABLES.contains(id)) {
                tableBuilder.pool(concealBookPool());
            }
            if (ENCHANT_ARMOR_TABLES.contains(id)) {
                tableBuilder.pool(concealBookPool());
            }
            if (ENCHANT_TOOL_TABLES.contains(id)) {
                tableBuilder.pool(concealBookPool());
            }
        });
    }

    private static LootPool concealBookPool() {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(new SetEnchantmentsFunction.Builder()
                                .withEnchantment(ModEnchantments.CONCEAL, ConstantValue.exactly(1))))
                .when(LootItemRandomChanceCondition.randomChance(0.05f))
                .build();
    }
}
