package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.StealthDetectionEvents;
import com.vicmatskiv.pointblank.attachment.Attachment;
import com.vicmatskiv.pointblank.attachment.AttachmentCategory;
import com.vicmatskiv.pointblank.attachment.Attachments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class PointBlankIntegration {

    public static final Identifier PB_GUN_SOUND_ID = new Identifier("pointblank", "gun_action");

    public static void onGunShoot(ServerPlayerEntity player, ItemStack gunStack) {
        Identifier gunId = Registries.ITEM.getId(gunStack.getItem());


        double flashRange = SoundAttractMod.CONFIG.gunshotBaseDetectionRange;
        double reduction = 0.0;


        for (ItemStack attachmentStack : Attachments.getAttachments(gunStack)) {
            if (attachmentStack.getItem() instanceof Attachment attachment && attachment.getCategory() == AttachmentCategory.MUZZLE) {
                Identifier muzzleId = Registries.ITEM.getId(attachmentStack.getItem());
                reduction += SoundAttractMod.CONFIG.getPointBlankMuzzleFlashReductions().getOrDefault(muzzleId.toString(), 0.0);
            }
        }
        double finalDetectionRange = Math.max(0, flashRange - reduction);
        StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);
        

        double[] rangeAndWeight = calculateShootRangeWeight(gunStack);
        
        SoundMessage msg = new SoundMessage(
                PB_GUN_SOUND_ID,
                player.getX(), player.getY(), player.getZ(),
                player.getWorld().getRegistryKey().getValue(),
                Optional.of(player.getUuid()),
                (int) rangeAndWeight[0],
                rangeAndWeight[1],
                null,
                "shoot;" + gunId
        );

        SoundMessage.handle(msg, player);
    }

    public static void onGunReload(ServerPlayerEntity player, ItemStack gunStack) {
        Identifier gunId = Registries.ITEM.getId(gunStack.getItem());
        
        double[] rangeAndWeight = calculateReloadRangeWeight(gunStack);

        SoundMessage msg = new SoundMessage(
                PB_GUN_SOUND_ID,
                player.getX(), player.getY(), player.getZ(),
                player.getWorld().getRegistryKey().getValue(),
                Optional.of(player.getUuid()),
                (int) rangeAndWeight[0],
                rangeAndWeight[1],
                null,
                "reload;" + gunId
        );

        SoundMessage.handle(msg, player);
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        Identifier gunId = Registries.ITEM.getId(gunStack.getItem());


        double finalRange = SoundAttractMod.CONFIG.getPointBlankGunShootRanges().getOrDefault(gunId.toString(), SoundAttractMod.CONFIG.pointblankShootRange);
        

        double soundReduction = 0.0;
        for (ItemStack attachmentStack : Attachments.getAttachments(gunStack)) {
            Identifier attachmentId = Registries.ITEM.getId(attachmentStack.getItem());
            soundReduction += SoundAttractMod.CONFIG.getPointBlankAttachmentSoundReductions().getOrDefault(attachmentId.toString(), 0.0);
        }
        finalRange = Math.max(0, finalRange - soundReduction);
        

        double weight = finalRange / 10.0;

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Shoot: Gun='{}', BaseRange={}, Reduction={}, FinalRange={}, Weight={}",
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
}