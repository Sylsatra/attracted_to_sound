package com.example.soundattract.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class AddEnchantedBookModifier extends LootModifier {
    public static final Supplier<Codec<AddEnchantedBookModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            ForgeRegistries.ENCHANTMENTS.getCodec().fieldOf("enchantment").forGetter(m -> m.enchantment)
                    ).and(
                            Codec.INT.optionalFieldOf("level", 1).forGetter(m -> m.level)
                    ).apply(inst, AddEnchantedBookModifier::new)
            )
    );

    private final Enchantment enchantment;
    private final int level;

    public AddEnchantedBookModifier(LootItemCondition[] conditionsIn, Enchantment enchantment, int level) {
        super(conditionsIn);
        this.enchantment = enchantment;
        this.level = Math.max(1, level);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(this.enchantment, this.level));
        generatedLoot.add(book);
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
