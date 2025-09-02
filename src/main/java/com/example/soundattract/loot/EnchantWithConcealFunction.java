package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class EnchantWithConcealFunction extends LootItemConditionalFunction {

    public EnchantWithConcealFunction(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType getType() {
        return ModLootFunctions.ENCHANT_WITH_CONCEAL.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.isEnchanted() && context.getRandom().nextFloat() < 0.05f) {
            stack.enchant(ModEnchantments.CONCEAL.get(), 1);
        }
        return stack;
    }


    public static LootItemConditionalFunction.Builder<?> builder() {
        return simpleBuilder(EnchantWithConcealFunction::new);
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<EnchantWithConcealFunction> {
        @Override
        public EnchantWithConcealFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new EnchantWithConcealFunction(conditions);
        }
    }
}
