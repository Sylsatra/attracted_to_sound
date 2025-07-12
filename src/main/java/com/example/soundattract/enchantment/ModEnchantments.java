package com.example.soundattract.enchantment;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public class ModEnchantments {

    public static final ResourceKey<Enchantment> CONCEAL = 
        ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "conceal"));

}