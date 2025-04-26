package com.example.soundattract;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractClientEvents {
    public static void onParCoolAnimationInfo(Object event) {
        if (!ModList.get().isLoaded("parcool")) return;
        try {
            Class<?> eventClass = Class.forName("com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent");
            if (!eventClass.isInstance(event)) return;
            Object player = eventClass.getMethod("getPlayer").invoke(event);
            Object animator = eventClass.getMethod("getAnimator").invoke(event);
            if (player == null || animator == null) return;
            if (!(player instanceof AbstractClientPlayer clientPlayer)) return;
            String animatorClass = animator.getClass().getSimpleName();
            SoundAttractionEvents.SoundMapping mapping = SoundAttractionEvents.SoundMapping.forAnimator(animator.getClass());
            if (mapping == null) return;
            ResourceLocation soundRL = mapping.soundEvent;
            int range = mapping.range;
            double weight = mapping.weight;
            ResourceLocation dim = clientPlayer.level().dimension().location();
            Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
            SoundMessage msg = new SoundMessage(soundRL, clientPlayer.getX(), clientPlayer.getY(), clientPlayer.getZ(), dim, sourcePlayerUUID, range, weight);
            SoundAttractNetwork.INSTANCE.sendToServer(msg);
        } catch (Exception ignored) {
        }
    }

    public static void registerParcoolClientHandler() {
        if (!ModList.get().isLoaded("parcool")) return;
        try {
            Class<?> eventClass = Class.forName("com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent");
            Method getHandlerList = eventClass.getMethod("getHandlerList");
            Object handlerList = getHandlerList.invoke(null);
            Method registerMethod = handlerList.getClass().getMethod("register", Object.class);
            registerMethod.invoke(handlerList, new Object() {
                public void onParCoolAnimationInfo(Object event) {
                    SoundAttractClientEvents.onParCoolAnimationInfo(event);
                }
            });
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public static void onPlaySoundEvent(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if (event.getSound() == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.level.Level clientWorld = mc.level;
        net.minecraft.world.entity.player.Player clientPlayer = mc.player;
        if (clientWorld == null || clientPlayer == null) return;

        if (!(event.getSound() instanceof net.minecraft.client.resources.sounds.AbstractSoundInstance soundInstance)) return;
        net.minecraft.resources.ResourceLocation soundRL = soundInstance.getLocation();
        if (soundRL == null) return;

        String soundId = soundRL.toString();
        if (!com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
            && !com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundId)
            && !soundRL.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID)) {
            return;
        }

        for (Object entryObj : com.example.soundattract.config.SoundAttractConfig.COMMON.nonPlayerSoundIdList.get()) {
            if (!(entryObj instanceof String entry)) continue;
            String[] parts = entry.split(";");
            if (parts.length < 1) continue;
            String configSoundId = parts[0];
            if (!soundId.equals(configSoundId)) continue;
            int range = parts.length > 1 ? parseIntOr(parts[1], 16) : 16;
            double weight = parts.length > 2 ? parseDoubleOr(parts[2], 1.0) : 1.0;
            double x = soundInstance.getX();
            double y = soundInstance.getY();
            double z = soundInstance.getZ();
            net.minecraft.resources.ResourceLocation dim = clientWorld.dimension().location();
            java.util.Optional<java.util.UUID> sourcePlayerUUID = java.util.Optional.empty();
            com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(
                soundRL, x, y, z, dim, sourcePlayerUUID, range, weight, "VanillaBlockSound");
            com.example.soundattract.SoundAttractNetwork.INSTANCE.sendToServer(msg);
            break;
        }
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    public static void registerVoiceChatIntegration() {
    }
}
