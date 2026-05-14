package com.example.soundattract.mixin.gecko;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import com.example.soundattract.gecko.CamoGeoLayer;

@Mixin(GeoEntityRenderer.class)
public abstract class MixinGeoEntityRenderer<T extends net.minecraft.world.entity.Entity & GeoAnimatable> {

    @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
    private void soundattract$registerCamoLayer(CallbackInfo ci) {
        ((GeoEntityRenderer<T>) (Object) this).addRenderLayer(new CamoGeoLayer<>((GeoEntityRenderer<T>) (Object) this));
    }
}
