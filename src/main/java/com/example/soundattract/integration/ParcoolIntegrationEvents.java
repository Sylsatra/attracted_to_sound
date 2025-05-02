package com.example.soundattract.integration;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.SoundMessage;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

import java.lang.reflect.Method;

public class ParcoolIntegrationEvents {
    private static final boolean IS_PARCOOL_LOADED = ModList.get().isLoaded("parcool");
    private static final String PARCOOL_ANIMATION_EVENT = "com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent";
    private static final String PARCOOL_ANIMATION_INFO_EVENT_GET_PLAYER = "getPlayer";
    private static final String PARCOOL_ANIMATION_INFO_EVENT_GET_ANIMATOR = "getAnimator";

    public static void register() {
        if (!IS_PARCOOL_LOADED) return;
        try {
            Class<?> animationEventClass = Class.forName(PARCOOL_ANIMATION_EVENT);
            Method method = animationEventClass.getMethod("getHandlerList");
            Object handlerList = method.invoke(null);
            Method registerMethod = handlerList.getClass().getMethod("register", Object.class);
            registerMethod.invoke(handlerList, new Object() {
                public void onParCoolAnimationInfo(Object event) {
                    onParCoolAnimationInfo(event);
                }
            });
        } catch (Exception e) {
        }
    }

    public static void onParCoolAnimationInfo(Object event) {
        try {
            Object player = event.getClass().getMethod(PARCOOL_ANIMATION_INFO_EVENT_GET_PLAYER).invoke(event);
            Object animator = event.getClass().getMethod(PARCOOL_ANIMATION_INFO_EVENT_GET_ANIMATOR).invoke(event);
            if (player == null || animator == null) return;

            String animatorClass = animator.getClass().getSimpleName();
            String foundConfig = null;
            for (String raw : SoundAttractConfig.parcoolAnimatorSoundConfigs.get()) {
                String[] parts = raw.split(";");
                if (parts.length >= 6 && parts[0].equals(animatorClass)) {
                    foundConfig = raw;
                    break;
                }
            }
            if (foundConfig == null) return;
            String[] parts = foundConfig.split(";");
            String soundId = parts[1];
            int range = Integer.parseInt(parts[2]);
            double weight = Double.parseDouble(parts[3]);
            float volume = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);

            double x = 0, y = 0, z = 0;
            net.minecraft.resources.ResourceLocation dim = null;
            java.util.UUID uuid = null;
            if (player instanceof net.minecraft.world.entity.Entity e) {
                x = e.getX();
                y = e.getY();
                z = e.getZ();
                dim = e.level().dimension().location();
                uuid = e.getUUID();
            }

            SoundMessage msg = new SoundMessage(
                    new net.minecraft.resources.ResourceLocation(soundId),
                    x, y, z,
                    dim,
                    java.util.Optional.ofNullable(uuid),
                    range,
                    weight,
                    animatorClass
            );
            SoundAttractNetwork.INSTANCE.sendToServer(msg);
        } catch (Exception e) {
        }
    }
}
