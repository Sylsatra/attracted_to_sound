package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class ItemMixin {
    /**
     * @param stack    the ItemStack being queried for a glint
     * @param cir      callback; allows us to override return value
     */
    @Inject(
        method = "hasGlint(Lnet/minecraft/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void soundattract_modifyGlint(
        ItemStack stack,
        CallbackInfoReturnable<Boolean> cir
    ) {
        // If this stack has our "Conceal" enchantment, cancel the glint:
        Enchantment conceal = ModEnchantments.CONCEAL;
        if (conceal != null) {
            int level = EnchantmentHelper.getLevel(conceal, stack);
            if (level > 0) {
                // Force return false (no glint), skipping vanilla logic entirely
                cir.setReturnValue(false);
            }
        } else {
            // If somehow ModEnchantments.CONCEAL isn't registered:
            if (SoundAttractMod.LOGGER != null) {
                SoundAttractMod.LOGGER.error("[ItemMixin] CONCEAL is null! Registration issue?");
            } else {
                System.err.println("[ItemMixin] CRITICAL: ModEnchantments.CONCEAL is null!");
            }
        }
    }
}