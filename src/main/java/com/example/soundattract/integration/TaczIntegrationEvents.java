package com.example.soundattract.integration;

// Imports (no changes needed here from the previous version of this file)
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
// import net.minecraft.item.ItemStack; // Not strictly needed without server-side item checks
import net.minecraft.util.Identifier;
import java.util.stream.Collectors;
import java.util.List; // Added for List type hint

public class TaczIntegrationEvents {

    private static final String SOUND_ID_FOR_SERVER_EFFECT = "tacz:gun";

    public static void register() {
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationEvents] register() called. Event handlers (UseItem/UseBlock) are currently commented out to prevent duplicate event processing with client-side detection.");
        }
        // Server-side UseItem/UseBlock callbacks remain commented out as per previous reasoning.
    }

    public static void handleReloadFromClient(PlayerEntity player, String gunId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) { // Early exit if config not loaded
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczIntegrationEvents] Config not loaded, cannot handle reload event.");
            return;
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationEvents] handleReloadFromClient: Player={}, GunId={}", player != null ? player.getName().getString() : "null", gunId);
        }
        try {
            if (player == null || player.getWorld().isClient()) {
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] handleReloadFromClient: Aborting - Player is null or event is on client side. This should only run on server.");
                }
                return;
            }

            double reloadRange = getReloadRangeFromConfig(gunId);
            // Config has taczReloadWeight as int, but derivation is float. Let's use double for consistency in calculation, then cast if SoundMessage needs int.
            double reloadWeight = getReloadWeightFromConfig(gunId);

            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationEvents] Reloading Effect: Player={}, GunId={}, ReloadRange={}, ReloadWeight={}", player.getName().getString(), gunId, reloadRange, reloadWeight);
            }

            Identifier soundRL = new Identifier(SOUND_ID_FOR_SERVER_EFFECT);
            Identifier dim = player.getWorld().getRegistryKey().getValue();
            com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(
                soundRL, player.getX(), player.getY(), player.getZ(), dim,
                java.util.Optional.of(player.getUuid()),
                (int) reloadRange, // SoundMessage expects int for range
                reloadWeight,      // SoundMessage expects double for weight (adjust if it expects int)
                null, "reload"
            );

            if (player instanceof ServerPlayerEntity serverPlayerEntity) {
                com.example.soundattract.SoundMessage.handle(msg, serverPlayerEntity);
            } else {
                 if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] handleReloadFromClient: Player is not ServerPlayerEntity. Cannot send SoundMessage for {}.", player.getName().getString());
                }
            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczIntegrationEvents] Exception in handleReloadFromClient for player={}, gunId={}", player != null ? player.getName().getString() : "null", gunId, e);
        }
    }

    public static void handleGunshotFromClient(PlayerEntity player, String gunId, String attachmentId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) { // Early exit if config not loaded
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczIntegrationEvents] Config not loaded, cannot handle gunshot event.");
            return;
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationEvents] handleGunshotFromClient: Player={}, GunId={}, AttachmentId={}", player != null ? player.getName().getString() : "null", gunId, attachmentId);
        }
        try {
            if (player == null || player.getWorld().isClient()) {
                 if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] handleGunshotFromClient: Aborting - Player is null or event is on client side. This should only run on server.");
                }
                return;
            }

            double shootRange = getGunShootRangeFromConfig(gunId); // Method specific to shoot range
            double shootWeight = getGunShootWeightFromConfig(gunId); // Method specific to shoot weight

            // Apply attachment reductions
            double totalReduction = com.example.soundattract.SoundAttractMod.CONFIG.taczBaseAttachmentReduction;
            if (attachmentId != null && !attachmentId.isEmpty()) {
                totalReduction += getAttachmentReductionFromConfig(attachmentId);
            }
            shootRange = Math.max(0, shootRange - totalReduction); // Ensure range isn't negative

            // If shoot weight should also be affected by reduction (e.g. proportionally), adjust here.
            // Current config implies weight is derived from base decibels, not final range.
            // If final range should dictate weight: shootWeight = shootRange / 10.0;

            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                String determinationMethod = (gunId != null && !gunId.isEmpty() && isGunIdInConfigList(gunId, com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels, "shoot")) ? "specific" : "fallback";
                com.example.soundattract.SoundAttractMod.LOGGER.info("[TaczIntegrationEvents] Shooting Effect ({}): Player={}, GunId={}, AttachmentId={}, InitialRange={}, TotalReduction={}, FinalRange={}, Weight={}",
                    determinationMethod, player.getName().getString(), gunId, attachmentId, getGunShootRangeFromConfig(gunId), totalReduction, shootRange, shootWeight);
            }


            Identifier soundRL = new Identifier(SOUND_ID_FOR_SERVER_EFFECT);
            Identifier dim = player.getWorld().getRegistryKey().getValue();
            com.example.soundattract.SoundMessage msg = new com.example.soundattract.SoundMessage(
                soundRL, player.getX(), player.getY(), player.getZ(), dim,
                java.util.Optional.of(player.getUuid()),
                (int) shootRange,  // SoundMessage expects int for range
                shootWeight,       // SoundMessage expects double for weight (adjust if it expects int and taczShootWeight is int)
                null, "shoot"
            );

            if (player instanceof ServerPlayerEntity serverPlayerEntity) {
                com.example.soundattract.SoundMessage.handle(msg, serverPlayerEntity);
            } else {
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] handleGunshotFromClient: Player is not ServerPlayerEntity. Cannot send SoundMessage for {}.", player.getName().getString());
                }
            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[TaczIntegrationEvents] Exception in handleGunshotFromClient for player={}, gunId={}, attachmentId={}", player != null ? player.getName().getString() : "null", gunId, attachmentId, e);
        }
    }

    // --- Config Helper Methods (Aligned with provided config structure) ---

    private static boolean isGunIdInConfigList(String gunId, List<String> configList, String typeForLog) {
        // This helper is mostly for logging and checking existence.
        // The actual value retrieval will parse the entry again.
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) return false;
        if (gunId == null || gunId.isEmpty()) return false;
        String normalizedGunId = gunId.trim().toLowerCase();

        // Debug logging can be intensive here, consider if it's needed for every call
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging && typeForLog != null) { // typeForLog can be null if just checking existence
            com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[TaczIntegrationEvents] Checking {} gunId '{}' (normalized '{}') against config keys.",
                typeForLog, gunId, normalizedGunId
            );
        }

        for (String entry : configList) {
            String[] parts = entry.split(";", 2); // Split into 2 parts: id and value
            if (parts.length > 0) {
                String gunIdCandidate = parts[0].trim().toLowerCase();
                if (gunIdCandidate.equals(normalizedGunId)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Specific getters for shoot parameters
    private static double getGunShootRangeFromConfig(String gunId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) return 128.0; // Default from your example
        if (gunId != null && !gunId.isEmpty()) {
            String normalizedGunId = gunId.trim().toLowerCase();
            for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
                String[] parts = entry.split(";", 2);
                if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedGunId)) {
                    try {
                        return Double.parseDouble(parts[1].trim()); // This is the 'decibels' value used as range
                    } catch (NumberFormatException e) {
                         if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                            com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] Malformed shoot decibel value in taczGunShootDecibels for gunId {}: {}", gunId, parts[1]);
                         }
                    }
                }
            }
        }
        return com.example.soundattract.SoundAttractMod.CONFIG.taczShootRange; // Fallback
    }

    private static double getGunShootWeightFromConfig(String gunId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) return 10.0; // Default from your example
        if (gunId != null && !gunId.isEmpty()) {
            String normalizedGunId = gunId.trim().toLowerCase();
            for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
                String[] parts = entry.split(";", 2);
                if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedGunId)) {
                    try {
                        double decibels = Double.parseDouble(parts[1].trim());
                        return decibels / 10.0; // Derived as per config comment
                    } catch (NumberFormatException e) {
                        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                           com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] Malformed shoot decibel value (for weight calculation) in taczGunShootDecibels for gunId {}: {}", gunId, parts[1]);
                        }
                    }
                }
            }
        }
        // Fallback taczShootWeight is an int in config, ensure consistent type.
        // If SoundMessage needs double, this is fine. If it needs int, cast later or return int here.
        return (double) com.example.soundattract.SoundAttractMod.CONFIG.taczShootWeight; // Fallback
    }

    // Specific getters for reload parameters
    private static double getReloadRangeFromConfig(String gunId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) return 9.0; // Default from your example
        if (gunId != null && !gunId.isEmpty()) {
            String normalizedGunId = gunId.trim().toLowerCase();
            for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
                String[] parts = entry.split(";", 2);
                if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedGunId)) {
                    try {
                        double decibels = Double.parseDouble(parts[1].trim());
                        return decibels / 20.0; // Derived as per config comment "shootDb/20.0"
                    } catch (NumberFormatException e) {
                        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                           com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] Malformed shoot decibel value (for reload range calculation) in taczGunShootDecibels for gunId {}: {}", gunId, parts[1]);
                        }
                    }
                }
            }
        }
        return com.example.soundattract.SoundAttractMod.CONFIG.taczReloadRange; // Fallback
    }

    private static double getReloadWeightFromConfig(String gunId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) return 9.0; // Default from your example
        if (gunId != null && !gunId.isEmpty()) {
            String normalizedGunId = gunId.trim().toLowerCase();
            for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczGunShootDecibels) {
                String[] parts = entry.split(";", 2);
                if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedGunId)) {
                    try {
                        double decibels = Double.parseDouble(parts[1].trim());
                        // Derived as per config comment "(shootDb/10.0)/2.0" which is shootDb/20.0
                        return decibels / 20.0;
                    } catch (NumberFormatException e) {
                         if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                           com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] Malformed shoot decibel value (for reload weight calculation) in taczGunShootDecibels for gunId {}: {}", gunId, parts[1]);
                        }
                    }
                }
            }
        }
        // Fallback taczReloadWeight is an int in config.
        return (double) com.example.soundattract.SoundAttractMod.CONFIG.taczReloadWeight; // Fallback
    }

    private static double getAttachmentReductionFromConfig(String attachmentId) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) return 0.0;
        if (attachmentId == null || attachmentId.isEmpty()) return 0.0;
        String normalizedAttachmentId = attachmentId.trim().toLowerCase();
        for (String entry : com.example.soundattract.SoundAttractMod.CONFIG.taczAttachmentReductions) {
            String[] parts = entry.split(";", 2);
            if (parts.length == 2 && parts[0].trim().toLowerCase().equals(normalizedAttachmentId)) {
                try {
                    return Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                        com.example.soundattract.SoundAttractMod.LOGGER.warn("[TaczIntegrationEvents] Malformed reduction value in taczAttachmentReductions for attachmentId {}: {}", attachmentId, parts[1]);
                    }
                }
            }
        }
        return 0.0; // No reduction if not found or malformed
    }
}