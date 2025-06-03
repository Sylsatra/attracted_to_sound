package com.example.soundattract.mixin; // Your mixin package

import com.example.soundattract.integration.TaczIntegrationClientLogic; // Import your class
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void soundattract_onPlaySoundHEAD(SoundInstance soundInstance, CallbackInfo ci) {
        // soundInstance can be null in some edge cases, though rare for "play"
        if (soundInstance != null) {
            TaczIntegrationClientLogic.onPlaySound(soundInstance);
        }
    }
}