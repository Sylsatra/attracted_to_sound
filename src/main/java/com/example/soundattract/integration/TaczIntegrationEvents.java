package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TaczIntegrationEvents {
    private static final String GUN_ITEM_ID = "tacz:modern_kinetic_gun";
    private static final String SOUND_ID    = "tacz:gun";
    private static final String TAG_LAST_SHOT = "tacz_last_shot";

    public static void handleReloadFromClient(Player player, String gunId) {
        if (player == null || player.level().isClientSide()) return;

        long now   = player.level().getGameTime();
        long shotT = player.getPersistentData().getLong(TAG_LAST_SHOT);

        if (now - shotT <= 2) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "[Tacz] Ignoring reload: only {} ticks after last shot", (now - shotT));
            }
            return;
        }

        double range  = lookupReloadRange(gunId);
        double weight = lookupReloadWeight(gunId);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                "[Tacz] Reload: player={}, gun={}, range={}, weight={}",
                player.getName().getString(), gunId, range, weight);
        }

        ResourceLocation rl  = ResourceLocation.parse(SOUND_ID);
        ResourceLocation dim = player.level().dimension().location();
        SoundMessage msg     = new SoundMessage(
            rl, player.getX(), player.getY(), player.getZ(),
            dim, java.util.Optional.of(player.getUUID()),
            (int) range, weight, null, "reload"
        );
        SoundMessage.handle(msg, () -> null);
    }

    public static void handleGunshotFromClient(Player player,
                                               String gunId,
                                               String attachmentId) {
        try {
            if (player == null || player.level().isClientSide()) return;

            player.getPersistentData().putLong(TAG_LAST_SHOT,
                                               player.level().getGameTime());

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()
                || ForgeRegistries.ITEMS.getKey(held.getItem()) == null
                || !ForgeRegistries.ITEMS.getKey(held.getItem())
                       .toString().equals(GUN_ITEM_ID)) {
                return;
            }

            double gunRange  = lookupShootRange(gunId);
            double reduction = getAttachmentReduction(attachmentId);
            double range     = Math.max(0, gunRange - reduction);
            double weight    = range / 10.0;

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "[Tacz] Shot: player={}, gun={}, att={}, range={}, weight={}",
                    player.getName().getString(), gunId, attachmentId, range, weight);
            }

            ResourceLocation rl  = ResourceLocation.parse(SOUND_ID);
            ResourceLocation dim = player.level().dimension().location();
            SoundMessage msg     = new SoundMessage(
                rl, player.getX(), player.getY(), player.getZ(),
                dim, java.util.Optional.of(player.getUUID()),
                (int) range, weight, null, "shoot"
            );
            SoundMessage.handle(msg, () -> null);

        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[Tacz] Gun-shot handler error", e);
        }
    }

    private static double lookupReloadRange(String gunId) {
        return SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
    }
    private static double lookupReloadWeight(String gunId) {
        return SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;
    }

    private static double lookupShootRange(String gunId) {
        if (gunId == null || gunId.isEmpty())
            return SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;

        String key = gunId.trim().toLowerCase();
        return SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.entrySet().stream()
            .filter(e -> e.getKey().toString().trim().toLowerCase().equals(key))
            .map(e -> e.getValue().getLeft())
            .findFirst()
            .orElse(SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE);
    }

    private static double getAttachmentReduction(String attachmentId) {
        if (attachmentId == null || attachmentId.isEmpty())
            return SoundAttractConfig.COMMON.taczAttachmentReductionDefault.get();

        String key = attachmentId.trim().toLowerCase();
        return SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.entrySet().stream()
            .filter(e -> e.getKey().toString().trim().toLowerCase().equals(key))
            .map(e -> e.getValue().getRight())
            .findFirst()
            .orElse(SoundAttractConfig.COMMON.taczAttachmentReductionDefault.get());
    }
}
