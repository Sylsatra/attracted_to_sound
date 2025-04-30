package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TaczIntegrationClientEvents {
    private static final String GUN_ITEM_ID = "tacz:modern_kinetic_gun";
    private static final String SOUND_ID = "tacz:gun";
    private static int lastLeftClickTick = -100;
    private static int lastGunshotSentTick = -100;
    private static int clientTick = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clientTick++;
        }
    }

    @SubscribeEvent
    public static void onPlayerLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Client: LeftClickEmpty event fired!");
        }
        lastLeftClickTick = clientTick;
        sendGunshotIfTaczGun();
    }

    @SubscribeEvent
    public static void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Client: LeftClickBlock event fired!");
        }
        lastLeftClickTick = clientTick;
        sendGunshotIfTaczGun();
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        if (event.getAction() == 1) return; // Only handle PRESS (0=PRESS, 1=RELEASE)
        if (event.getButton() != 0) return; // Only handle left mouse button
        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Client: Mouse left button pressed!");
        }
        sendGunshotIfTaczGun();
    }

    private static void sendGunshotIfTaczGun() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!held.isEmpty() && ForgeRegistries.ITEMS.getKey(held.getItem()) != null &&
                ForgeRegistries.ITEMS.getKey(held.getItem()).toString().equals(GUN_ITEM_ID)) {
            CompoundTag tag = held.getTag();
            if (tag == null || !tag.contains("GunId")) return;
            String gunId = tag.getString("GunId");
            String attachmentId = null;
            if (tag.contains("AttachmentMUZZLE")) {
                CompoundTag muzzleTag = tag.getCompound("AttachmentMUZZLE");
                if (muzzleTag.contains("tag")) {
                    CompoundTag muzzleInner = muzzleTag.getCompound("tag");
                    if (muzzleInner.contains("AttachmentId")) {
                        attachmentId = muzzleInner.getString("AttachmentId");
                    }
                }
            }
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Client: Sending gunshot message for GunId={}, AttachmentId={}", gunId, attachmentId);
            }
            SoundAttractNetwork.INSTANCE.sendToServer(new TaczGunshotMessage(gunId, attachmentId));
            lastGunshotSentTick = clientTick;
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        ResourceLocation soundRL = event.getSound().getLocation();
        if (soundRL == null || !soundRL.toString().equals(SOUND_ID)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!held.isEmpty() && ForgeRegistries.ITEMS.getKey(held.getItem()) != null &&
                ForgeRegistries.ITEMS.getKey(held.getItem()).toString().equals(GUN_ITEM_ID)) {
            CompoundTag tag = held.getTag();
            String gunId = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
            // Always send reload message, no cooldown suppression
            SoundAttractNetwork.INSTANCE.sendToServer(new TaczReloadMessage(gunId));
        }
    }
}
