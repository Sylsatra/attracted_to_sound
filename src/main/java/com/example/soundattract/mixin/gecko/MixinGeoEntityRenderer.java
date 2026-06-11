package com.example.soundattract.mixin.gecko;

import com.example.soundattract.gecko.CamoGeoLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mixin(GeoEntityRenderer.class)
public abstract class MixinGeoEntityRenderer<T extends Entity & GeoAnimatable> {

    @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
    private void soundattract$registerCamoLayer(CallbackInfo ci) {
        GeoEntityRenderer<T> renderer = (GeoEntityRenderer<T>) (Object) this;
        renderer.addRenderLayer(new CamoGeoLayer<>(renderer));
    }
}
