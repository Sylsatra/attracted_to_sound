package com.example.soundattract.enchantment;

import com.example.soundattract.Soundattract;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class ModEnchantments {
    public static Enchantment CONCEAL;

    public static void register() {
        CONCEAL = Registry.register(BuiltInRegistries.ENCHANTMENT,
                new ResourceLocation(Soundattract.MOD_ID, "conceal"),
                new EnchantmentConceal(
                        Enchantment.Rarity.VERY_RARE,
                        EnchantmentCategory.BREAKABLE,
                        new EquipmentSlot[]{
                                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                                EquipmentSlot.LEGS, EquipmentSlot.FEET
                        }
                ));
    }
}
