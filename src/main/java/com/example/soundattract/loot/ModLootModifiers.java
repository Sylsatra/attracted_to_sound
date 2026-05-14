package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Set;

public class ModLootModifiers {

    private static final Set<ResourceLocation> ADD_CONCEAL_BOOK_TABLES = Set.of(
            ResourceLocation.tryParse("minecraft:chests/ancient_city"),
            ResourceLocation.tryParse("minecraft:chests/end_city_treasure"),
            ResourceLocation.tryParse("minecraft:chests/stronghold_library"),
            ResourceLocation.tryParse("minecraft:chests/woodland_mansion"),
            ResourceLocation.tryParse("minecraft:chests/buried_treasure"),
            ResourceLocation.tryParse("minecraft:chests/desert_pyramid")
    );

    private static final Set<ResourceLocation> ENCHANT_ARMOR_TABLES = Set.of(
            ResourceLocation.tryParse("minecraft:chests/ancient_city"),
            ResourceLocation.tryParse("minecraft:chests/bastion_treasure"),
            ResourceLocation.tryParse("minecraft:chests/nether_bridge")
    );

    private static final Set<ResourceLocation> ENCHANT_TOOL_TABLES = Set.of(
            ResourceLocation.tryParse("minecraft:chests/ancient_city"),
            ResourceLocation.tryParse("minecraft:chests/end_city_treasure"),
            ResourceLocation.tryParse("minecraft:chests/stronghold_crossing"),
            ResourceLocation.tryParse("minecraft:chests/bastion_other")
    );

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            ResourceLocation id = key.location();
            if (!source.isBuiltin()) return;

            var enchantmentHolder = registries.lookupOrThrow(Registries.ENCHANTMENT).get(ModEnchantments.CONCEAL);
            if (enchantmentHolder.isEmpty()) return;

            Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment = enchantmentHolder.get();

            if (ADD_CONCEAL_BOOK_TABLES.contains(id)) {
                tableBuilder.pool(concealBookPool(enchantment));
            }
            if (ENCHANT_ARMOR_TABLES.contains(id)) {
                tableBuilder.pool(concealBookPool(enchantment));
            }
            if (ENCHANT_TOOL_TABLES.contains(id)) {
                tableBuilder.pool(concealBookPool(enchantment));
            }
        });
    }

    private static LootPool concealBookPool(Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(new SetEnchantmentsFunction.Builder()
                                .withEnchantment(enchantment, ConstantValue.exactly(1))))
                .when(LootItemRandomChanceCondition.randomChance(0.05f))
                .build();
    }
}
