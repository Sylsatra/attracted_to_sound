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


    @Override
    public int getMinPower(int level) {
        return 25;
    }


    @Override
    public int getMaxPower(int level) {
        return 50;
    }


    @Override
    public int getMaxLevel() {
        return 1;
    }


    @Override
    public boolean isAvailableForEnchantedBookOffer() {
        return true;
    }


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

            || (stack.isDamageable() && !(item instanceof BookItem));
    }
}
