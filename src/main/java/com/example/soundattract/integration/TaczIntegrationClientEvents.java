package com.example.soundattract.integration;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;

public class TaczIntegrationClientEvents {
    private static final String GUN_ITEM_ID = "tacz:modern_kinetic_gun";
    private static int lastLeftClickTick = -100;
    private static int lastGunshotSentTick = -100;
    private static int clientTick = 0;

    public static void tryTriggerGunshot(MinecraftClient client, String source) {
        int tick = clientTick;
        boolean leftClick = client.options.attackKey.isPressed();
        if (leftClick && lastLeftClickTick != tick) {
            
            lastLeftClickTick = tick;
            sendGunshotIfTaczGun(client);
        sendReloadIfTaczGun(client);
        } else if (leftClick) {
            
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null || client.world == null) {

                return;
            }
            clientTick++;
            if (!com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(0, clientTick)) {

                return;
            }
            tryTriggerGunshot(client, "tick");
        });
    }

    public static void sendGunshotIfTaczGun(MinecraftClient mc) {
        
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] sendGunshotIfTaczGun CALLED");
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandStack();
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] Held item: {} | NBT: {}", held.getItem(), held.getNbt());
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationClientEvents] sendGunshotIfTaczGun CALLED");
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationClientEvents] Held item: {}", held);
        String heldItemId = net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString();
        
        if (!held.isEmpty() && heldItemId.equals(GUN_ITEM_ID)) {
    
    NbtCompound tag = held.getNbt();
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationClientEvents] Sending full NBT to server: {}", tag);
    com.example.soundattract.network.FabricSimpleNbtSync.sendNbtToServer(tag, com.example.soundattract.SoundAttractMod.LOGGER);
    String gunId = null;
    String attachmentId = null;
    if (tag != null) {
        if (tag.contains("GunId")) {
            gunId = tag.getString("GunId");
        }
        if (tag.contains("AttachmentMUZZLE")) {
            NbtCompound muzzleTag = tag.getCompound("AttachmentMUZZLE");
            if (muzzleTag.contains("tag")) {
                NbtCompound muzzleInner = muzzleTag.getCompound("tag");
                if (muzzleInner.contains("AttachmentId")) {
                    attachmentId = muzzleInner.getString("AttachmentId");
                }
            }
        }
    }
    if (gunId != null && !gunId.isEmpty()) {
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationClientEvents] Sending TaczGunshotMessage to server: gunId={}, attachmentId={}", gunId, attachmentId);
        com.example.soundattract.SoundAttractNetwork.sendTaczGunshotToServer(new com.example.soundattract.integration.TaczGunshotMessage(gunId, attachmentId));
    } else {
        com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationClientEvents] GunId missing in NBT, not sending TaczGunshotMessage");
    }
    lastGunshotSentTick = clientTick;
}
    }

    public static void sendReloadIfTaczGun(MinecraftClient mc) {
        
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] sendReloadIfTaczGun CALLED");
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandStack();
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_CLIENT] Held item: {} | NBT: {}", held.getItem(), held.getNbt());
        String heldItemId = net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString();
        
        if (!held.isEmpty() && heldItemId.equals(GUN_ITEM_ID)) {
            NbtCompound tag = held.getNbt();
            String gunId = null;
            if (tag != null && tag.contains("GunId")) {
                gunId = tag.getString("GunId");
            }
            if (gunId != null && !gunId.isEmpty()) {
                if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationClientEvents] Sending TaczReloadMessage to server: gunId={}", gunId);
                com.example.soundattract.SoundAttractNetwork.sendTaczReloadToServer(new com.example.soundattract.integration.TaczReloadMessage(gunId));
            } else {
                com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationClientEvents] GunId missing in NBT, not sending TaczReloadMessage");
            }
        }
    }
}

