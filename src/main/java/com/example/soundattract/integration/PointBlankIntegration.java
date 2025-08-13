package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessagePayload;
import com.example.soundattract.logic.SoundMessageHandler;
import com.example.soundattract.StealthDetectionEvents;
import com.vicmatskiv.pointblank.attachment.Attachment;
import com.vicmatskiv.pointblank.attachment.AttachmentCategory;

import com.vicmatskiv.pointblank.inventory.VirtualInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PointBlankIntegration {

    public static final Identifier PB_GUN_SOUND_ID = Identifier.of("pointblank", "gun_action");

    /**
     * Called from the GunItemMixin#soundattract$onFire.
     * The gunStack parameter is now reliable because the mixin gets it directly
     * from the player's inventory.
     */
    public static void onGunShoot(ServerPlayerEntity player, ItemStack realGunStack) {
        Identifier gunId = Registries.ITEM.getId(realGunStack.getItem());


        double flashRange = SoundAttractMod.CONFIG.gunshotBaseDetectionRange;
        double flashReduction = 0.0;
        

        for (ItemStack attachmentStack : getAttachmentsFromVirtualInventory(player, realGunStack)) {
            if (attachmentStack.getItem() instanceof Attachment attachment && attachment.getCategory() == AttachmentCategory.MUZZLE) {
                Identifier muzzleId = Registries.ITEM.getId(attachmentStack.getItem());
                flashReduction += SoundAttractMod.CONFIG.getPointBlankMuzzleFlashReductions().getOrDefault(muzzleId.toString(), 0.0);
            }
        }
        double finalDetectionRange = Math.max(0, flashRange - flashReduction);
        StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);


        double[] rangeAndWeight = calculateShootRangeWeight(player, realGunStack);

        SoundMessagePayload payload = new SoundMessagePayload(
                PB_GUN_SOUND_ID,
                player.getX(), player.getY(), player.getZ(),
                player.getWorld().getRegistryKey().getValue(),
                Optional.of(player.getUuid()),
                (int) rangeAndWeight[0],
                rangeAndWeight[1],
                null,
                "shoot;" + gunId
        );

        SoundMessageHandler.handle(payload, player);
    }
    
    public static void onGunReload(ServerPlayerEntity player, ItemStack gunStack) {
        Identifier gunId = Registries.ITEM.getId(gunStack.getItem());
        double[] rangeAndWeight = calculateReloadRangeWeight(gunStack);

        SoundMessagePayload payload = new SoundMessagePayload(
                PB_GUN_SOUND_ID,
                player.getX(), player.getY(), player.getZ(),
                player.getWorld().getRegistryKey().getValue(),
                Optional.of(player.getUuid()),
                (int) rangeAndWeight[0],
                rangeAndWeight[1],
                null,
                "reload;" + gunId
        );
        SoundMessageHandler.handle(payload, player);
    }

    private static double[] calculateShootRangeWeight(ServerPlayerEntity player, ItemStack gunStack) {
        Identifier gunId = Registries.ITEM.getId(gunStack.getItem());
        double finalRange = SoundAttractMod.CONFIG.getPointBlankGunShootRanges().getOrDefault(gunId.toString(), SoundAttractMod.CONFIG.pointblankShootRange);
        double soundReduction = 0.0;


        List<ItemStack> foundAttachments = getAttachmentsFromVirtualInventory(player, gunStack);

        for (ItemStack attachmentStack : foundAttachments) {
            Identifier attachmentId = Registries.ITEM.getId(attachmentStack.getItem());
            soundReduction += SoundAttractMod.CONFIG.getPointBlankAttachmentSoundReductions().getOrDefault(attachmentId.toString(), 0.0);
        }
        
        finalRange = Math.max(0, finalRange - soundReduction);
        double weight = finalRange / 10.0;

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Final Calculation: Gun='{}', BaseRange={}, Reduction={}, FinalRange={}, Weight={}",
                    gunId, String.format("%.2f", finalRange + soundReduction), String.format("%.2f", soundReduction), String.format("%.2f", finalRange), String.format("%.2f", weight));
        }

        return new double[]{finalRange, weight};
    }

    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {

        double range = SoundAttractMod.CONFIG.pointblankReloadRange;
        double weight = SoundAttractMod.CONFIG.pointblankReloadWeight;
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Reload: range={}, weight={}", range, weight);
        }
        return new double[]{range, weight};
    }

    /**
     * Gets all attachments on a given item stack by using Point Blank's VirtualInventory API.
     * This is the robust, future-proof way to read attachment data.
     *
     * @param owner The player holding the item.
     * @param containerStack The ItemStack to inspect (e.g., a gun).
     * @return A list of ItemStacks representing all found attachments.
     */
    private static List<ItemStack> getAttachmentsFromVirtualInventory(ServerPlayerEntity owner, ItemStack containerStack) {
        List<ItemStack> foundAttachments = new ArrayList<>();
        if (containerStack == null || containerStack.isEmpty()) {
            return foundAttachments;
        }


        VirtualInventory inventory = VirtualInventory.createInventory(owner, containerStack);
        

        collectAttachments(inventory, foundAttachments);

        return foundAttachments;
    }

    private static void collectAttachments(VirtualInventory inventory, List<ItemStack> listToFill) {

        for (VirtualInventory childInventory : inventory.getElements().values()) {
            ItemStack attachmentStack = childInventory.getItemStack();

            if (attachmentStack != null && !attachmentStack.isEmpty()) {
                listToFill.add(attachmentStack);

                collectAttachments(childInventory, listToFill);
            }
        }
    }
}