package com.example.soundattract.integration;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;

public class TaczIntegrationEvents {
    public static void sendGunshotIfTaczGun() {
    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
    com.example.soundattract.integration.TaczIntegrationClientEvents.sendGunshotIfTaczGun(client);
}

    private static final String GUN_ITEM_ID = "tacz:modern_kinetic_gun";
    private static final String SOUND_ID = "tacz:gun";

    public static void register() {
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) 
    if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] register(): Registering event handlers");
    }
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && hand == net.minecraft.util.Hand.MAIN_HAND) {
                ItemStack stack = player.getMainHandStack();
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration/UseItemCallback] Held item ID: {}", stack.getItem().toString());
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration/UseItemCallback] Held item NBT: {}", stack.getNbt());
                }
                if (!stack.isEmpty() && stack.getItem().toString().equals(GUN_ITEM_ID)) {
                    NbtCompound tag = stack.getNbt();
                    String gunId = null;
                    String attachmentId = null;
                    if (tag != null && tag.contains("GunId")) {
                        gunId = tag.getString("GunId");
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
                    handleGunshotFromClient(player, gunId, attachmentId);
                }
            }
            ItemStack stack = player.getMainHandStack();
            return net.minecraft.util.TypedActionResult.pass(stack);
        });

        UseBlockCallback.EVENT.register((player, world, hand, blockHitResult) -> {
            if (!world.isClient() && hand == net.minecraft.util.Hand.MAIN_HAND) {
                ItemStack stack = player.getMainHandStack();
                if (!stack.isEmpty() && stack.getItem().toString().equals(GUN_ITEM_ID)) {
                    NbtCompound tag = stack.getNbt();
                    String gunId = null;
                    String attachmentId = null;
                    if (tag != null && tag.contains("GunId")) {
                        gunId = tag.getString("GunId");
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
                    handleGunshotFromClient(player, gunId, attachmentId);
                }
            }
            return ActionResult.PASS;
        });
    }

    public static void handleReloadFromClient(PlayerEntity player, String gunId) {
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    
}
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] handleReloadFromClient: Using gunId='{}' for config lookup", gunId);
    }
        try {
            if (player == null || player.getWorld().isClient()) return;

            double reloadRange;
reloadRange = getReloadRangeFromConfig(gunId);
double reloadWeight = reloadRange / 10.0; 
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Reloading: PlayerEntity={}, GunId={}, ReloadRange={}, ReloadWeight={}", player.getName().getString(), gunId, reloadRange, reloadWeight);
            }

            Identifier soundRL = new Identifier(SOUND_ID);
            Identifier dim = player.getWorld().getRegistryKey().getValue();
            com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(soundRL, player.getX(), player.getY(), player.getZ(), dim, java.util.Optional.of(player.getUuid()), (int) reloadRange, reloadWeight, null, "reload");
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayerEntity) {
                com.example.soundattract.SoundMessage.handle(msg, serverPlayerEntity);
                com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] handleGunshotFromClient: About to send NBT to client for player {}", serverPlayerEntity.getName().getString());

            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczIntegration] Exception in handleReloadFromClient for player={}, gunId={}", player != null ? player.getName().getString() : "null", gunId, e);
        }
    }

    public static void handleGunshotFromClient(PlayerEntity player, String gunId, String attachmentId) {
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    
}
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] handleGunshotFromClient: Using gunId='{}', attachmentId='{}' for config lookup", gunId, attachmentId);
    }
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_SERVER] handleGunshotFromClient CALLED: player={}, gunId={}, attachmentId={}, isClient={}", player != null ? player.getName().getString() : "null", gunId, attachmentId, player != null && player.getWorld().isClient());
    if (player != null) {
        ItemStack held = player.getMainHandStack();
        com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG_SERVER] Held item: {} | NBT: {}", held.getItem(), held.getNbt());
    }
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] handleGunshotFromClient ENTRY: player={}, gunId={}, attachmentId={}, isClient={}", player != null ? player.getName().getString() : "null", gunId, attachmentId, player != null && player.getWorld().isClient());
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationEvents] handleGunshotFromClient ENTRY for player: {} gunId: {} attachmentId: {}", player != null ? player.getName().getString() : "null", gunId, attachmentId);
    }
    if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] handleGunshotFromClient: Received gunId='{}' attachmentId='{}'", gunId, attachmentId);
    }
        try {
            if (player == null || player.getWorld().isClient()) return;

            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Server: Received gunshot message from player={}, GunId={}, AttachmentId={}", player.getName().getString(), gunId, attachmentId);
            }

            ItemStack held = player.getMainHandStack();
            if (held.isEmpty() || net.minecraft.registry.Registries.ITEM.getId(held.getItem()) == null ||
                !net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString().equals(GUN_ITEM_ID)) {
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] handleGunshotFromClient: Not a tacz:modern_kinetic_gun");
                }
                return;
            }

            net.minecraft.nbt.NbtCompound tag = held.getNbt();
            if (tag == null || !tag.contains("GunId")) {
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] handleGunshotFromClient: GunId tag missing");
                }
                return;
            }

            if (gunId == null || gunId.isEmpty() || !isGunIdInShootConfig(gunId)) {
                double fallbackRange = com.example.soundattract.SoundAttractMod.CONFIG.taczShootRange;
                double fallbackWeight = com.example.soundattract.SoundAttractMod.CONFIG.taczShootWeight;
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Shooting (fallback): PlayerEntity={}, GunId={}, Range={}, Weight={}", player.getName().getString(), gunId, fallbackRange, fallbackWeight);
                }

                Identifier soundRL = new Identifier(SOUND_ID);
                Identifier dim = player.getWorld().getRegistryKey().getValue();
                com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(soundRL, player.getX(), player.getY(), player.getZ(), dim, java.util.Optional.of(player.getUuid()), (int) fallbackRange, fallbackWeight, null, "shoot");
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayerEntity) {
                    com.example.soundattract.SoundMessage.handle(msg, serverPlayerEntity);
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] handleGunshotFromClient: About to send NBT to client for player {}", serverPlayerEntity.getName().getString());
                }
                return;
            }

            double gunRange = getGunRangeFromConfig(gunId);
            double reduction = 0.0;
double baseAttachmentReduction = 0.0;
try {
    baseAttachmentReduction = com.example.soundattract.SoundAttractMod.CONFIG.taczBaseAttachmentReduction;
} catch (Exception e) {
    baseAttachmentReduction = 0.0;
}
if (attachmentId != null && !attachmentId.isEmpty()) {
    reduction = getAttachmentReductionFromConfig(attachmentId);
}
reduction += baseAttachmentReduction;


double finalRange = Math.max(0, gunRange - reduction);
double finalWeight = finalRange / 10.0;
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegration] Shooting: PlayerEntity={}, GunId={}, AttachmentId={}, Range={}, Weight={}", player.getName().getString(), gunId, attachmentId, finalRange, finalWeight);
            }

            Identifier soundRL = new Identifier(SOUND_ID);
            Identifier dim = player.getWorld().getRegistryKey().getValue();
            com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(soundRL, player.getX(), player.getY(), player.getZ(), dim, java.util.Optional.of(player.getUuid()), (int) finalRange, finalWeight, null, "shoot");
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayerEntity) {
                com.example.soundattract.SoundMessage.handle(msg, serverPlayerEntity);
                com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] handleGunshotFromClient: About to send NBT to client for player {}", serverPlayerEntity.getName().getString());

            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczIntegration] Exception in handleGunshotFromClient for player={}, gunId={}, attachmentId={}", player != null ? player.getName().getString() : "null", gunId, attachmentId, e);
        }
    }

    private static boolean isGunIdInReloadConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return false;
        String normalizedGunId = gunId.trim().toLowerCase();
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[TaczIntegration] Checking reload gunId '{}', normalized '{}', against keys: {}",
                gunId, normalizedGunId,
                com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels.stream()
                    .map(entry -> {
                        String[] parts = entry.split(";");
                        return parts.length > 0 ? parts[0].trim().toLowerCase() : "";
                    })
                    .collect(java.util.stream.Collectors.toList())
            );
        }

        for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
            String[] parts = entry.split(";");
            if (parts.length > 0) {
                String gunIdCandidate = parts[0].trim().toLowerCase();
                if (gunIdCandidate.equals(normalizedGunId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isGunIdInShootConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return false;
        String normalizedGunId = gunId.trim().toLowerCase();
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[TaczIntegration] Checking shoot gunId '{}', normalized '{}', against keys: {}",
                gunId, normalizedGunId,
                com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels.stream()
                    .map(entry -> {
                        String[] parts = entry.split(";");
                        return parts.length > 0 ? parts[0].trim().toLowerCase() : "";
                    })
                    .collect(java.util.stream.Collectors.toList())
            );
        }

        for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
            String[] parts = entry.split(";");
            if (parts.length > 0) {
                String gunIdCandidate = parts[0].trim().toLowerCase();
                if (gunIdCandidate.equals(normalizedGunId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double getGunRangeFromConfig(String gunId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            
            
        }
        if (gunId == null || gunId.isEmpty()) return com.example.soundattract.SoundAttractMod.CONFIG.taczShootRange;
        String normalizedGunId = gunId.trim().toLowerCase();
        for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
            String[] parts = entry.split(";");
            if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedGunId)) {
                try {
                    return Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) { }
            }
        }
        return com.example.soundattract.SoundAttractMod.CONFIG.taczShootRange;
    }

    private static double getGunWeightFromConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return com.example.soundattract.SoundAttractMod.CONFIG.taczShootWeight;
        String normalizedGunId = gunId.trim().toLowerCase();
        for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
            String[] parts = entry.split(";");
            if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedGunId)) {
                try {
                    double db = Double.parseDouble(parts[1]);
                    return db / 10.0;
                } catch (NumberFormatException ignored) { }
            }
        }
        return com.example.soundattract.SoundAttractMod.CONFIG.taczShootWeight;
    }

    private static double getAttachmentReductionFromConfig(String attachmentId) {
        if (attachmentId == null || attachmentId.isEmpty()) return 0.0;
        String normalizedAttachmentId = attachmentId.trim().toLowerCase();
        for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczAttachmentReductions) {
            String[] parts = entry.split(";");
            if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedAttachmentId)) {
                try {
                    return Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) { }
            }
        }
        return 0.0;
    }

    private static double getReloadRangeFromConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return com.example.soundattract.SoundAttractMod.CONFIG.taczReloadRange;
        com.example.soundattract.config.SoundAttractConfigData.SoundConfig conf =
            com.example.soundattract.SoundAttractMod.CONFIG.getSoundConfigForId(gunId);
        if (conf != null) return conf.range;
        return com.example.soundattract.SoundAttractMod.CONFIG.taczReloadRange;
    }

    private static double getReloadWeightFromConfig(String gunId) {
        if (gunId == null || gunId.isEmpty()) return com.example.soundattract.SoundAttractMod.CONFIG.taczReloadWeight;
        com.example.soundattract.config.SoundAttractConfigData.SoundConfig conf =
            com.example.soundattract.SoundAttractMod.CONFIG.getSoundConfigForId(gunId);
        if (conf != null) return conf.weight;
        return com.example.soundattract.SoundAttractMod.CONFIG.taczReloadWeight;
    }
}