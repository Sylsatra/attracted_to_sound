package com.example.soundattract.mixin.gecko;

import com.example.soundattract.gecko.CamoGeoLayer;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GeoEntityRenderer.class)
public abstract class MixinGeoEntityRenderer<T extends Entity & GeoAnimatable, R extends EntityRenderState> {

    @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
    private void soundattract$registerCamoLayer(CallbackInfo ci) {
        GeoEntityRenderer<T, R> renderer = (GeoEntityRenderer<T, R>) (Object) this;
        renderer.withRenderLayer(new CamoGeoLayer(renderer));
    }
}
