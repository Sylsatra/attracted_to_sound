package com.example.soundattract.mixin; // Your mixin package

import com.example.soundattract.integration.TaczIntegrationClientLogic; // Import your class
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void soundattract_onClientTickHEAD(CallbackInfo ci) {
        // 'this' is an instance of MinecraftClient
        MinecraftClient client = (MinecraftClient) (Object) this;
        // It's good practice to check if world and player are not null
        if (client.world != null && client.player != null) {
            TaczIntegrationClientLogic.onClientTick(client);
        }
    }
}