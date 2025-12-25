package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.pointblank.PointBlankIntegration;
import com.vicmatskiv.pointblank.item.FireModeInstance;
import com.vicmatskiv.pointblank.item.GunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = GunItem.class, priority = 1100, remap = false)
public abstract class PointBlankGunItemMixin {

    @Inject(
            method = "hitScanTarget(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;IILnet/minecraft/world/phys/HitResult;DLjava/util/List;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void soundattract$onFire(Player player,
                                     ItemStack itemStack,
                                     int slotIndex,
                                     int correlationId,
                                     HitResult hitResult,
                                     double maxHitScanDistance,
                                     List<?> blockPosToDestroy,
                                     CallbackInfo ci) {
        if (com.example.soundattract.config.SoundAttractConfig.COMMON == null ||
            !com.example.soundattract.config.SoundAttractConfig.COMMON.enablePointBlankIntegration.get()) {
            return;
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PBMixin] hitScanTarget hooked for {} with item {}", player, itemStack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PointBlankIntegration.onGunShoot(serverPlayer, itemStack);
        }
    }

    @Inject(
            method = "handleClientReloadRequest(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Ljava/util/UUID;ILcom/vicmatskiv/pointblank/item/FireModeInstance;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void soundattract$onReloadRequest(ServerPlayer player,
                                              ItemStack itemStack,
                                              UUID clientStateId,
                                              int slotIndex,
                                              FireModeInstance fireModeInstance,
                                              CallbackInfo ci) {
        if (!SoundAttractConfig.POINT_BLANK_ENABLED_CACHE) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PBMixin] handleClientReloadRequest hooked for {} with item {}", player, itemStack);
        }
        PointBlankIntegration.onGunReload(player, itemStack);
    }
}
