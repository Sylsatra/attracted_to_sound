package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void soundattract_modifyGlintOnReturn(CallbackInfoReturnable<Boolean> cir) {
        // First, check if the original method determined the item should glint.
        // cir.getReturnValueZ() is for boolean primitive.
        if (cir.getReturnValueZ()) {
            ItemStack thisStack = (ItemStack) (Object) this;

            // Now, check for our Conceal enchantment.
            // We only care about removing the glint if the Conceal enchantment is loaded and present.
            if (ModEnchantments.CONCEAL != null && ModEnchantments.CONCEAL.isPresent()) {
                Enchantment concealEnchantmentInstance = ModEnchantments.CONCEAL.get();

                if (EnchantmentHelper.getItemEnchantmentLevel(concealEnchantmentInstance, thisStack) > 0) {
                    // The item was originally going to glint, but it has Conceal. So, remove glint.
                    cir.setReturnValue(false);
                }
                // If it doesn't have Conceal, we do nothing, and the original 'true' glint status remains.
            } else {
                // Conceal enchantment isn't ready. Log it but don't alter the glint.
                // The original 'true' glint status remains.
                if (ModEnchantments.CONCEAL == null) {
                    if (SoundAttractMod.LOGGER != null) {
                        SoundAttractMod.LOGGER.error("[ItemStackMixin @RETURN] ModEnchantments.CONCEAL RegistryObject is null! Critical mod setup issue.");
                    } else {
                        System.err.println("[ItemStackMixin @RETURN] CRITICAL: ModEnchantments.CONCEAL RegistryObject is null!");
                    }
                } else { // ModEnchantments.CONCEAL is not null, but .isPresent() is false
                    if (SoundAttractMod.LOGGER != null) {
                        SoundAttractMod.LOGGER.warn("[ItemStackMixin @RETURN] ModEnchantments.CONCEAL is not present. This might be a loading order issue or registration failure.");
                    } else {
                        System.err.println("[ItemStackMixin @RETURN] WARN: ModEnchantments.CONCEAL is not present!");
                    }
                }
            }
        }
        // If cir.getReturnValueZ() was initially false (item wasn't going to glint anyway),
        // we do nothing, and it remains false.
    }
}