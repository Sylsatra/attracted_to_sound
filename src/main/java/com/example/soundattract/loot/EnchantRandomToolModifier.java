package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public class EnchantRandomToolModifier extends LootModifier {
    public static final MapCodec<EnchantRandomToolModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, EnchantRandomToolModifier::new));

    public EnchantRandomToolModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        var enchantmentRegistry = context.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> concealHolder = enchantmentRegistry.getHolderOrThrow(ModEnchantments.CONCEAL);

        for (ItemStack stack : generatedLoot) {
            if (!stack.isEmpty() &&
                (stack.getItem() instanceof DiggerItem || stack.getItem() instanceof SwordItem) &&
                concealHolder.value().canEnchant(stack) &&
                EnchantmentHelper.getItemEnchantmentLevel(concealHolder, stack) == 0) {

                stack.enchant(concealHolder, 1);
                return generatedLoot;
            }
        }

        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
