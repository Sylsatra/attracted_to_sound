package com.example.soundattract.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantment.Rarity;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.BookItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.item.TridentItem;

public class EnchantmentConceal extends Enchantment {
    public EnchantmentConceal(Rarity rarity, EnchantmentTarget target, EquipmentSlot[] slots) {
        super(rarity, target, slots);
    }

    // Matches method_8182: getMinPower(int) → int
    @Override
    public int getMinPower(int level) {
        return 25;
    }

    // Matches method_20742: getMaxPower(int) → int
    @Override
    public int getMaxPower(int level) {
        return 50;
    }

    // Matches method_8183: getMaxLevel() → int
    @Override
    public int getMaxLevel() {
        return 1;
    }

    // Matches method_25949: isAvailableForEnchantedBookOffer() → boolean
    @Override
    public boolean isAvailableForEnchantedBookOffer() {
        return true;
    }

    // Matches method_8192: isAcceptableItem(ItemStack) → boolean
    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof ArmorItem
            || item instanceof ToolItem
            || item instanceof SwordItem
            || item instanceof BowItem
            || item instanceof CrossbowItem
            || item instanceof TridentItem
            || item instanceof ShieldItem
            || item instanceof ShearsItem
            || item instanceof ElytraItem
            // Or any damageable item that is not a BookItem:
            || (stack.isDamageable() && !(item instanceof BookItem));
    }
}
