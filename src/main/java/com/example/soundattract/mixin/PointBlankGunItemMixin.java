package com.example.soundattract.mixin;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.pointblank.PointBlankIntegration;
import com.vicmatskiv.pointblank.item.FireModeInstance;
import com.vicmatskiv.pointblank.item.GunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = GunItem.class, priority = 1100, remap = false)
public abstract class PointBlankGunItemMixin {

    @Inject(method = "handleClientHitScanFireRequest", at = @At("RETURN"), remap = false, require = 0)
    private void soundattract$onHitScanFire(ServerPlayer player,
                                            FireModeInstance fireModeInstance,
                                            UUID stateId,
                                            int slotIndex,
                                            int correlationId,
                                            boolean isAiming,
                                            long requestSeed,
                                            CallbackInfo ci) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[PBMixin] handleClientHitScanFireRequest hooked for {} slot {}", player, slotIndex);
        }
        ItemStack itemStack = player.getInventory().getItem(slotIndex);
        PointBlankIntegration.onGunShoot(player, itemStack);
    }

    @Inject(method = "handleClientProjectileFireRequest", at = @At("RETURN"), remap = false, require = 0)
    private void soundattract$onProjectileFire(ServerPlayer player,
                                               FireModeInstance fireModeInstance,
                                               UUID stateId,
                                               int slotIndex,
                                               int correlationId,
                                               boolean isAiming,
                                               double spawnPositionX,
                                               double spawnPositionY,
                                               double spawnPositionZ,
                                               double spawnDirectionX,
                                               double spawnDirectionY,
                                               double spawnDirectionZ,
                                               int targetEntityId,
                                               long requestSeed,
                                               CallbackInfo ci) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[PBMixin] handleClientProjectileFireRequest hooked for {} slot {}", player, slotIndex);
        }
        ItemStack itemStack = player.getInventory().getItem(slotIndex);
        PointBlankIntegration.onGunShoot(player, itemStack);
    }

    @Inject(method = "handleClientReloadRequest", at = @At("RETURN"), remap = false, require = 0)
    private void soundattract$onReload(ServerPlayer player,
                                      ItemStack itemStack,
                                      UUID clientStateId,
                                      int slotIndex,
                                      FireModeInstance fireModeInstance,
                                      CallbackInfo ci) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[PBMixin] handleClientReloadRequest hooked for {} with item {}", player, itemStack);
        }
        PointBlankIntegration.onGunReload(player, itemStack);
    }
}
