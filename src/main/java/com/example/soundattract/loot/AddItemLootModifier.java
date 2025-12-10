package com.example.soundattract.loot;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.enchantment.ModEnchantments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance; 
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public class AddItemLootModifier extends LootModifier {
    public static final MapCodec<AddItemLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, AddItemLootModifier::new));

    public AddItemLootModifier(LootItemCondition[] conditions) {
        super(conditions);
        SoundAttractMod.LOGGER.info("AddItemLootModifier INSTANCE CREATED!");
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        SoundAttractMod.LOGGER.info("AddItemLootModifier is running for loot table: " + context.getQueriedLootTableId());
        var enchantmentRegistry = context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        enchantmentRegistry.get(ModEnchantments.CONCEAL).ifPresent(concealHolder -> {
            SoundAttractMod.LOGGER.info("Found CONCEAL enchantment holder! Adding book to loot.");

            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            enchantedBook.enchant(concealHolder, 1);
            
            generatedLoot.add(enchantedBook);
        });

        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
