package com.example.soundattract.mixin;

import com.example.soundattract.integration.PointBlankIntegration;

import com.vicmatskiv.pointblank.item.FireModeInstance;
import com.vicmatskiv.pointblank.item.GunItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.UUID;

@Mixin(value = GunItem.class, priority = 1100, remap = false)
public class GunItemMixin {


    @Inject(method = "hitScanTarget", at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILHARD, remap = true)
    private void soundattract$onFire(PlayerEntity player, ItemStack itemStack, int slotIndex, int correlationId, HitResult hitResult, double maxHitScanDistance, List<?> blockPosToDestroy, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            PointBlankIntegration.onGunShoot(serverPlayer, itemStack);
        }
    }



    @Inject(method = "handleClientReloadRequest(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/item/ItemStack;Ljava/util/UUID;ILcom/vicmatskiv/pointblank/item/FireModeInstance;)V",
            at = @At("HEAD"),
            remap = true)

    private void soundattract$onReloadRequest(ServerPlayerEntity player, ItemStack itemStack, UUID clientStateId, int slotIndex, FireModeInstance fireModeInstance, CallbackInfo ci) {
        PointBlankIntegration.onGunReload(player, itemStack);
    }
}