package com.example.soundattract.enchantment;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModEnchantments {

    public static final RegistryKey<Enchantment> CONCEAL = RegistryKey.of(
            RegistryKeys.ENCHANTMENT,
            Identifier.of(SoundAttractMod.MOD_ID, "conceal")
    );


    public static void register() {

    }
}