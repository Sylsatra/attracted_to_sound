package com.example.soundattract.mixin;

import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class ItemMixin {

    @Inject(
        method = "hasGlint(Lnet/minecraft/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void soundattract_modifyGlint(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {

        

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);

        if (enchantments != null) {

            for (RegistryEntry<Enchantment> enchantmentEntry : enchantments.getEnchantments()) {

                if (enchantmentEntry.matchesKey(ModEnchantments.CONCEAL)) {

                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}