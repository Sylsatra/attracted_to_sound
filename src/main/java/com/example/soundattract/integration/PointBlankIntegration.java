package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import com.vicmatskiv.pointblank.attachment.Attachment;
import com.vicmatskiv.pointblank.attachment.AttachmentCategory;
import com.vicmatskiv.pointblank.attachment.Attachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;


public class PointBlankIntegration {

    public static final ResourceLocation PB_GUN_SOUND_ID = ResourceLocation.parse("pointblank:gun_action");

    public static void onGunShoot(ServerPlayer player, ItemStack gunStack) {
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enablePointBlankIntegration.get()) return;

        double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
        double flashReduction = 0.0;
        for (ItemStack attachmentStack : Attachments.getAttachments(gunStack)) {
            if (attachmentStack.getItem() instanceof Attachment attachment && attachment.getCategory() == AttachmentCategory.MUZZLE) {
                ResourceLocation muzzleId = BuiltInRegistries.ITEM.getKey(attachmentStack.getItem());
                if (muzzleId != null) {
                    flashReduction += SoundAttractConfig.POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.getOrDefault(muzzleId.toString(), 0.0);
                }
            }
        }
        double finalDetectionRange = Math.max(0.0, flashRange - flashReduction);
        StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                "[PointBlankIntegration] Gunshot Flash: BaseRange={}, Reduction={}, FinalRange={}",
                String.format("%.2f", flashRange), String.format("%.2f", flashReduction), String.format("%.2f", finalDetectionRange)
            );
        }

        double[] rangeAndWeight = calculateShootRangeWeight(gunStack);
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        SoundTracker.addSound(
            null,
            player.blockPosition(),
            player.level().dimension().location().toString(),
            rangeAndWeight[0],
            rangeAndWeight[1],
            lifetime,
            PB_GUN_SOUND_ID.toString()
        );
    }

    public static void onGunReload(ServerPlayer player, ItemStack gunStack) {
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enablePointBlankIntegration.get()) return;

        double[] rangeAndWeight = calculateReloadRangeWeight(gunStack);
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        SoundTracker.addSound(
            null,
            player.blockPosition(),
            player.level().dimension().location().toString(),
            rangeAndWeight[0],
            rangeAndWeight[1],
            lifetime,
            PB_GUN_SOUND_ID.toString()
        );
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        ResourceLocation gunId = BuiltInRegistries.ITEM.getKey(gunStack.getItem());
        double baseRange = (gunId != null)
                ? SoundAttractConfig.POINT_BLANK_GUN_RANGE_CACHE.getOrDefault(gunId, SoundAttractConfig.POINT_BLANK_SHOOT_RANGE_CACHE)
                : SoundAttractConfig.POINT_BLANK_SHOOT_RANGE_CACHE;

        double soundReduction = 0.0;
        for (ItemStack attachmentStack : Attachments.getAttachments(gunStack)) {
            ResourceLocation attachmentId = BuiltInRegistries.ITEM.getKey(attachmentStack.getItem());
            if (attachmentId != null) {
                soundReduction += SoundAttractConfig.POINT_BLANK_ATTACHMENT_REDUCTION_CACHE
                        .getOrDefault(attachmentId, 0.0);
            }
        }
        double finalRange = Math.max(0.0, baseRange - soundReduction);
        double weight = finalRange / 10.0;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                "[PointBlankIntegration] Shoot: Gun='{}', BaseRange={}, Reduction={}, FinalRange={}, Weight={}",
                gunId,
                String.format("%.2f", baseRange),
                String.format("%.2f", soundReduction),
                String.format("%.2f", finalRange),
                String.format("%.2f", weight)
            );
        }
        return new double[]{finalRange, weight};
    }

    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {
        double range = SoundAttractConfig.POINT_BLANK_RELOAD_RANGE_CACHE;
        double weight = SoundAttractConfig.POINT_BLANK_RELOAD_WEIGHT_CACHE;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Reload: range={}, weight={}",
                    String.format("%.2f", range), String.format("%.2f", weight));
        }
        return new double[]{range, weight};
    }
}
