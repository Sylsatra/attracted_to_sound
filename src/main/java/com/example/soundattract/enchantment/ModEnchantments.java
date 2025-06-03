package com.example.soundattract.enchantment;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;

public class ModEnchantments {
    public static final EnchantmentConceal CONCEAL = Registry.register(
        Registries.ENCHANTMENT,
        new Identifier(SoundAttractMod.MOD_ID, "conceal"),
        new EnchantmentConceal(
            Enchantment.Rarity.VERY_RARE,
            EnchantmentTarget.BREAKABLE,
            new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
            }
        )
    );

    public static void register() {
    }
}
