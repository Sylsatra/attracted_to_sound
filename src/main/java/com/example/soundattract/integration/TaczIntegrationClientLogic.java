package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.util.Objects;

public class TaczIntegrationClientLogic {

    private static final Identifier TACZ_GENERIC_GUN_SOUND_ID = new Identifier("tacz", "gun");
    private static final String TAG_AMMO = "GunCurrentAmmoCount";
    private static final String TAG_GUN_ID = "GunId";
    private static final String TAG_MUZZLE = "AttachmentMUZZLE";
    private static final String TAG_ATTACHMENT_ID = "AttachmentId";

    private static int clientTickCounter = 0;
    private static int lastGunshotSentTick = -200; // Cooldown to prevent spam from ammo check
    private static int lastReloadSentTick = -200;  // Cooldown for ammo check and sound check
    private static final int GUNSHOT_COOLDOWN_TICKS = 3; // Cooldown after detecting a shot via ammo
    private static final int RELOAD_COOLDOWN_TICKS = 20; // Cooldown after detecting a reload via ammo OR sound

    private static int previousAmmoCount = -1;
    private static String previousHeldGunId = null;

    // Method to be called by a MinecraftClient.tick() mixin
    public static void onClientTick(MinecraftClient client) {
        clientTickCounter++;
        if (client.player == null) {
            previousAmmoCount = -1;
            previousHeldGunId = null;
            return;
        }

        ItemStack heldStack = client.player.getMainHandStack();
        String currentHeldItemIdString = heldStack.isEmpty() ? null : Registries.ITEM.getId(heldStack.getItem()).toString();
        NbtCompound tag = heldStack.getNbt();
        String currentGunNbtId = (tag != null && tag.contains(TAG_GUN_ID, NbtElement.STRING_TYPE)) ? tag.getString(TAG_GUN_ID) : null;
        String uniqueCurrentGunIdentifier = currentHeldItemIdString + ":" + currentGunNbtId;

        // Reset ammo if gun changed, unequipped, or is no longer a TaCZ gun
        if (heldStack.isEmpty() || !Registries.ITEM.getId(heldStack.getItem()).getNamespace().equals("tacz") ||
            !Objects.equals(previousHeldGunId, uniqueCurrentGunIdentifier)) {
            if (previousAmmoCount != -1 || (previousHeldGunId != null && !previousHeldGunId.startsWith("null:"))) { // Log only if there was a gun or ammo before
                 if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration - Tick] Gun changed/unequipped ({} -> {}). Resetting cached ammo from {}.",
                                                previousHeldGunId, uniqueCurrentGunIdentifier, previousAmmoCount);
                }
            }
            previousAmmoCount = -1;
            previousHeldGunId = uniqueCurrentGunIdentifier; // Update to current, even if null/non-TaCZ
        }

        if (!heldStack.isEmpty() && Registries.ITEM.getId(heldStack.getItem()).getNamespace().equals("tacz") && tag != null) {
            if (tag.contains(TAG_AMMO, NbtElement.INT_TYPE)) {
                int currentAmmo = tag.getInt(TAG_AMMO);
                String gunIdForMessage = tag.getString(TAG_GUN_ID); // Assuming GunId is present if TAG_AMMO is
                String attachmentId = getAttachmentId(tag);

                if (previousAmmoCount != -1) { // Only check for changes if we have a valid previous count for this gun
                    // Check for shot (ammo decrease)
                    if (currentAmmo < previousAmmoCount) {
                        if (clientTickCounter >= lastGunshotSentTick + GUNSHOT_COOLDOWN_TICKS) {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[TaczIntegration - AmmoTick] SHOT detected ({} -> {}). Sending TaczGunshotMessage: gunId={}, attachmentId={}",
                                        previousAmmoCount, currentAmmo, gunIdForMessage, attachmentId);
                            }
                            SoundAttractNetwork.sendTaczGunshotToServer(new TaczGunshotMessage(gunIdForMessage, attachmentId));
                            lastGunshotSentTick = clientTickCounter;
                            // Also update lastReloadSentTick to prevent immediate sound-based reload detection if shot sound is tacz:gun
                            lastReloadSentTick = clientTickCounter;
                        } else {
                             if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[TaczIntegration - AmmoTick] Shot detected ({} -> {}) but suppressed by GUNSHOT_COOLDOWN. Last sent: {}, Current: {}", previousAmmoCount, currentAmmo, lastGunshotSentTick, clientTickCounter);
                            }
                        }
                    // Check for reload (ammo increase) - CRITICAL CHANGE HERE
                    } else if (currentAmmo > previousAmmoCount) { // Detects any increase in ammo
                        if (clientTickCounter >= lastReloadSentTick + RELOAD_COOLDOWN_TICKS) {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[TaczIntegration - AmmoTick] RELOAD detected ({} -> {}). Sending TaczReloadMessage: gunId={}",
                                        previousAmmoCount, currentAmmo, gunIdForMessage);
                            }
                            SoundAttractNetwork.sendTaczReloadToServer(new TaczReloadMessage(gunIdForMessage));
                            lastReloadSentTick = clientTickCounter;
                        } else {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[TaczIntegration - AmmoTick] Reload detected ({} -> {}) but suppressed by RELOAD_COOLDOWN. Last sent: {}, Current: {}", previousAmmoCount, currentAmmo, lastReloadSentTick, clientTickCounter);
                            }
                        }
                    }
                }
                previousAmmoCount = currentAmmo;
                // Ensure previousHeldGunId is correctly set if we are processing a valid TaCZ gun
                if (!Objects.equals(previousHeldGunId, uniqueCurrentGunIdentifier)) {
                     previousHeldGunId = uniqueCurrentGunIdentifier; // Should have been set by the gun change check, but as a safeguard
                }

            } else { // TaCZ gun held, but no ammo tag (e.g., freshly spawned, mod quirk, or gun doesn't use ammo count like melee)
                if (previousAmmoCount != -1 && SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                     SoundAttractMod.LOGGER.info("[TaczIntegration - AmmoTick] TaCZ gun ({}) held, but no ammo NBT tag found. Resetting cached ammo from {}.", uniqueCurrentGunIdentifier, previousAmmoCount);
                }
                previousAmmoCount = -1; // Reset so we don't get false positives if ammo tag appears later
            }
        }
        // If not holding a TaCZ gun, or stack is empty, previousAmmoCount is handled by the gun change check at the top.
    }

    // Method to be called by a SoundSystem.play() mixin
    // This acts as a potential fallback or primary detector for reloads if ammo NBT is unreliable for them,
    // or if tacz:gun is a distinct reload sound.
    public static void onPlaySound(SoundInstance soundInstance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        if (soundInstance.getId().equals(TACZ_GENERIC_GUN_SOUND_ID)) {
            ItemStack heldStack = client.player.getMainHandStack();
            if (heldStack.isEmpty() || !Registries.ITEM.getId(heldStack.getItem()).getNamespace().equals("tacz")) {
                // tacz:gun played, but player not holding a tacz gun. Ignore.
                return;
            }

            NbtCompound tag = heldStack.getNbt();
            if (tag == null || !tag.contains(TAG_GUN_ID, NbtElement.STRING_TYPE)) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.warn("[TaczIntegration - PlaySound] tacz:gun played, but held TaCZ item has no GunId NBT. Cannot determine gun for event.");
                }
                return;
            }
            String gunId = tag.getString(TAG_GUN_ID);

            // Heuristic: If a shot was detected by ammo *very* recently, this sound is likely the shot sound.
            // Give a small window (e.g., slightly more than GUNSHOT_COOLDOWN_TICKS) for the sound to play after ammo detects the shot.
            // lastGunshotSentTick is updated by the ammo-based shot detection.
            boolean likelyShotSound = clientTickCounter < lastGunshotSentTick + (GUNSHOT_COOLDOWN_TICKS + 2); // Small buffer

            if (!likelyShotSound && clientTickCounter >= lastReloadSentTick + RELOAD_COOLDOWN_TICKS) {
                // Conditions met: Not likely the direct sound of a shot just detected by ammo, and reload cooldown has passed.
                // Assume this is a reload event (or an event that tacz:gun sound signifies other than an immediate ammo-detected shot).
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[TaczIntegration - PlaySound] tacz:gun sound played for gunId {}. Assuming RELOAD (or other non-shot event). Sending TaczReloadMessage.", gunId);
                }
                SoundAttractNetwork.sendTaczReloadToServer(new TaczReloadMessage(gunId));
                lastReloadSentTick = clientTickCounter; // Update reload cooldown
            } else {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    String reason = likelyShotSound ? "sound likely belongs to recent ammo-detected shot" : "reload cooldown active";
                    SoundAttractMod.LOGGER.info("[TaczIntegration - PlaySound] tacz:gun sound played for gunId {}, but event message suppressed. Reason: {}. LastShotTick: {}, LastReloadTick: {}, CurrentTick: {}",
                        gunId, reason, lastGunshotSentTick, lastReloadSentTick, clientTickCounter);
                }
            }
        }
    }

    private static String getAttachmentId(NbtCompound gunTag) {
        if (gunTag != null && gunTag.contains(TAG_MUZZLE, NbtElement.COMPOUND_TYPE)) {
            NbtCompound muzzleTag = gunTag.getCompound(TAG_MUZZLE);
            // TaCZ seems to store attachment NBT sometimes in "tag" and sometimes directly
            if (muzzleTag.contains("tag", NbtElement.COMPOUND_TYPE)) {
                NbtCompound innerAttachmentTag = muzzleTag.getCompound("tag");
                if (innerAttachmentTag.contains(TAG_ATTACHMENT_ID, NbtElement.STRING_TYPE)) {
                    return innerAttachmentTag.getString(TAG_ATTACHMENT_ID);
                }
            } else if (muzzleTag.contains(TAG_ATTACHMENT_ID, NbtElement.STRING_TYPE)) { // Check direct tag if "tag" compound not found or no ID in it
                 return muzzleTag.getString(TAG_ATTACHMENT_ID);
            }
        }
        return null;
    }

    // --- Keybind/Action Trigger Methods REMOVED ---
    // The following methods were previously used to proactively send messages,
    // typically called from keybind handlers or other direct action listeners.
    // They are removed as per the request to rely on ammo count and sound events.

    /*
    public static void triggerGunshotFromAction(MinecraftClient client, String triggerSource) {
        // This method's logic is now superseded by onClientTick's ammo decrease detection.
        // If called, it would be redundant or could conflict with the passive detection.
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] triggerGunshotFromAction called from {} - This method is now deprecated/removed.", triggerSource);
        }
    }
    */

    /*
    public static void triggerReloadFromAction(MinecraftClient client, String triggerSource) {
        // This method's logic is now superseded by onClientTick's ammo increase detection
        // and the onPlaySound fallback.
        // If called, it would be redundant or could conflict with the passive detection.
         if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] triggerReloadFromAction called from {} - This method is now deprecated/removed.", triggerSource);
        }
    }
    */
}