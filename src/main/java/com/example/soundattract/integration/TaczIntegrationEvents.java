package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TaczIntegrationEvents {
    private static final String GUN_ITEM_ID = "tacz:modern_kinetic_gun";
    private static final String SOUND_ID = "tacz:gun";

    @SubscribeEvent
    public static void onPlayerLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
    }

    @SubscribeEvent
    public static void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
    }

    public static void handleReloadFromClient(Player player, String gunId) {
        try {
            if (player == null || player.level().isClientSide()) return;
            player.getPersistentData().putBoolean("tacz_reload_handled", true);
            double reloadRange;
            double reloadWeight;
            if (gunId == null || gunId.isEmpty() || !isGunIdInReloadConfig(gunId)) {
                reloadRange = SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
                reloadWeight = SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;
                if (SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration] Reloading (fallback): Player={}, GunId={}, ReloadRange={}, ReloadWeight={}", player.getName().getString(), gunId, reloadRange, reloadWeight);
                }
            } else {
                reloadRange = getReloadRangeFromConfig(gunId);
                reloadWeight = getReloadWeightFromConfig(gunId);
                if (SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration] Reloading: Player={}, GunId={}, ReloadRange={}, ReloadWeight={}", player.getName().getString(), gunId, reloadRange, reloadWeight);
                }
            }
            ResourceLocation soundRL = new ResourceLocation(SOUND_ID);
            ResourceLocation dim = player.level().dimension().location();
            SoundMessage msg = new SoundMessage(soundRL, player.getX(), player.getY(), player.getZ(), dim, java.util.Optional.of(player.getUUID()), (int)reloadRange, reloadWeight, null, "reload");
            SoundMessage.handle(msg, () -> null);
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[TaczIntegration] Exception in handleReloadFromClient for player={}, gunId={}", player != null ? player.getName().getString() : "null", gunId, e);
        }
    }

    public static void handleGunshotFromClient(Player player, String gunId, String attachmentId) {
        try {
            if (player == null || player.level().isClientSide()) return;
            if (player.getPersistentData().getBoolean("tacz_reload_handled")) {
                player.getPersistentData().remove("tacz_reload_handled");
                if (SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration] Skipping fallback shooting because reload was just handled for Player={}", player.getName().getString());
                }
                return;
            }
            if (SoundAttractConfig.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[TaczIntegration] Server: Received gunshot message from player={}, GunId={}, AttachmentId={}", player.getName().getString(), gunId, attachmentId);
            }
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty() || ForgeRegistries.ITEMS.getKey(held.getItem()) == null ||
                    !ForgeRegistries.ITEMS.getKey(held.getItem()).toString().equals(GUN_ITEM_ID)) {
                if (SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration] handleGunshotFromClient: Not a tacz:modern_kinetic_gun");
                }
                return;
            }
            CompoundTag tag = held.getTag();
            if (tag == null || !tag.contains("GunId")) {
                if (SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration] handleGunshotFromClient: GunId tag missing");
                }
                return;
            }
            if (gunId == null || gunId.isEmpty() || !isGunIdInShootConfig(gunId)) {
                double fallbackRange = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
                double fallbackWeight = SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE;
                if (SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration] Shooting (fallback): Player={}, GunId={}, Range={}, Weight={}", player.getName().getString(), gunId, fallbackRange, fallbackWeight);
                }
                ResourceLocation soundRL = new ResourceLocation(SOUND_ID);
                ResourceLocation dim = player.level().dimension().location();
                SoundMessage msg = new SoundMessage(soundRL, player.getX(), player.getY(), player.getZ(), dim, java.util.Optional.of(player.getUUID()), (int)fallbackRange, fallbackWeight, null, "shoot");
                SoundMessage.handle(msg, () -> null);
                return;
            }
            double gunRange = getGunRangeFromConfig(gunId);
            double reduction = 0.0;
            if (attachmentId != null && !attachmentId.isEmpty()) {
                reduction = getAttachmentReductionFromConfig(attachmentId);
            }
            double finalRange = Math.max(0, gunRange - reduction);
            double finalWeight = getGunWeightFromConfig(gunId);
            if (SoundAttractConfig.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[TaczIntegration] Shooting: Player={}, GunId={}, AttachmentId={}, Range={}, Weight={}", player.getName().getString(), gunId, attachmentId, finalRange, finalWeight);
            }
            ResourceLocation soundRL = new ResourceLocation(SOUND_ID);
            ResourceLocation dim = player.level().dimension().location();
            SoundMessage msg = new SoundMessage(soundRL, player.getX(), player.getY(), player.getZ(), dim, java.util.Optional.of(player.getUUID()), (int)finalRange, finalWeight, null, "shoot");
            SoundMessage.handle(msg, () -> null);
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[TaczIntegration] Exception in handleGunshotFromClient for player={}, gunId={}, attachmentId={}", player != null ? player.getName().getString() : "null", gunId, attachmentId, e);
        }
    }

    private static boolean isGunIdInReloadConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return false;
        String normalizedGunId = gunId.trim().toLowerCase();
        if (SoundAttractConfig.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Checking reload gunId '{}', normalized '{}', against keys: {}", gunId, normalizedGunId,
                com.example.soundattract.config.SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.keySet().stream()
                    .map(rl -> rl.toString().trim().toLowerCase())
                    .collect(java.util.stream.Collectors.toList()));
        }
        for (ResourceLocation rl : com.example.soundattract.config.SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.keySet()) {
            if (rl.toString().trim().toLowerCase().equals(normalizedGunId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGunIdInShootConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return false;
        String normalizedGunId = gunId.trim().toLowerCase();
        if (SoundAttractConfig.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Checking shoot gunId '{}', normalized '{}', against keys: {}", gunId, normalizedGunId,
                com.example.soundattract.config.SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.keySet().stream()
                    .map(rl -> rl.toString().trim().toLowerCase())
                    .collect(java.util.stream.Collectors.toList()));
        }
        for (ResourceLocation rl : com.example.soundattract.config.SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.keySet()) {
            if (rl.toString().trim().toLowerCase().equals(normalizedGunId)) {
                return true;
            }
        }
        return false;
    }

    private static double getReloadRangeFromConfig(String gunId) {
        return com.example.soundattract.config.SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
    }

    private static double getReloadWeightFromConfig(String gunId) {
        return com.example.soundattract.config.SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;
    }

    private static double getGunRangeFromConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
        String normalizedGunId = gunId.trim().toLowerCase();
        for (ResourceLocation rl : SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.keySet()) {
            if (rl.toString().trim().toLowerCase().equals(normalizedGunId)) {
                return SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(rl);
            }
        }
        return SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
    }

    private static double getGunWeightFromConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE;
        String normalizedGunId = gunId.trim().toLowerCase();
        for (ResourceLocation rl : SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.keySet()) {
            if (rl.toString().trim().toLowerCase().equals(normalizedGunId)) {
                double db = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(rl);
                return db / 10.0;
            }
        }
        return SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE;
    }

    private static double getAttachmentReductionFromConfig(String attachmentId) {
        if (attachmentId == null || attachmentId.isEmpty()) return 0.0;
        String normalizedAttachmentId = attachmentId.trim().toLowerCase();
        for (ResourceLocation rl : com.example.soundattract.config.SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.keySet()) {
            if (rl.toString().trim().toLowerCase().equals(normalizedAttachmentId)) {
                return com.example.soundattract.config.SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(rl);
            }
        }
        return 0.0;
    }
}