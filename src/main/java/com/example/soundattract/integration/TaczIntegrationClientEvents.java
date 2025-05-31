package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractNetwork;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = SoundAttractMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TaczIntegrationClientEvents {

    private static final String GUN_ITEM_ID    = "tacz:modern_kinetic_gun";
    private static final String SOUND_ID       = "tacz:gun";

    private static final String TAG_AMMO       = "GunCurrentAmmoCount";
    private static final String TAG_GUN_ID     = "GunId";
    private static final String TAG_MUZZLE     = "AttachmentMUZZLE";
    private static final String TAG_ATTACHMENT_ID = "AttachmentId";

    private static int  clientTick          = 0;
    private static int  lastGunshotSentTick = -100;
    private static int  lastLeftClickTick   = -100;
    private static int  cachedAmmo          = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTick++;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() ||
            !GUN_ITEM_ID.equals(ForgeRegistries.ITEMS.getKey(held.getItem()).toString())) {
            cachedAmmo = -1;
            return;
        }

        CompoundTag tag = held.getTag();
        if (tag == null || !tag.contains(TAG_AMMO)) return;

        int currentAmmo = tag.getInt(TAG_AMMO);
        if (cachedAmmo == -1) {
            cachedAmmo = currentAmmo;
            return;
        }

        if (currentAmmo < cachedAmmo) {
            sendGunshotPacket(tag);
            cachedAmmo = currentAmmo;
            return;
        }

        if (currentAmmo > cachedAmmo + 1) {
            sendReloadPacket(tag);
            cachedAmmo = currentAmmo;
        }
    }

    @SubscribeEvent
    public static void onPlayerLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
        debug("[Client] LeftClickEmpty");
        lastLeftClickTick = clientTick;
        sendGunshotIfTaczGun();
    }

    @SubscribeEvent
    public static void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        debug("[Client] LeftClickBlock");
        lastLeftClickTick = clientTick;
        sendGunshotIfTaczGun();
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        if (event.getAction() == 1 || event.getButton() != 0) return;
        debug("[Client] Mouse left button pressed");
        sendGunshotIfTaczGun();
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        if (!SOUND_ID.equals(event.getSound().getLocation().toString())) return;

        if (clientTick - lastGunshotSentTick <= 1) {
            debug("[Client] Suppressed reload – shot just sent");
            return;
        }
        sendReloadPacket(null);
    }

    private static void sendGunshotIfTaczGun() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;
        if (!GUN_ITEM_ID.equals(ForgeRegistries.ITEMS.getKey(held.getItem()).toString())) return;

        CompoundTag tag = held.getTag();
        if (tag == null || !tag.contains(TAG_GUN_ID)) return;

        sendGunshotPacket(tag);
    }

    private static void sendGunshotPacket(CompoundTag tag) {
        String gunId = tag != null ? tag.getString(TAG_GUN_ID) : "<unknown>";
        String attachmentId = null;

        if (tag != null && tag.contains(TAG_MUZZLE)) {
            CompoundTag muzzleTag = tag.getCompound(TAG_MUZZLE);
            if (muzzleTag.contains("tag")) {
                CompoundTag inner = muzzleTag.getCompound("tag");
                if (inner.contains(TAG_ATTACHMENT_ID)) {
                    attachmentId = inner.getString(TAG_ATTACHMENT_ID);
                }
            }
        }

        debug("[Client] Sending GUNSHOT – gun=%s, att=%s", gunId, attachmentId);
        SoundAttractNetwork.INSTANCE.sendToServer(new TaczGunshotMessage(gunId, attachmentId));
        lastGunshotSentTick = clientTick;
    }

    private static void sendReloadPacket(CompoundTag tag) {
        String gunId;
        if (tag != null && tag.contains(TAG_GUN_ID)) {
            gunId = tag.getString(TAG_GUN_ID);
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack held = mc.player.getMainHandItem();
            gunId = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
        }

        debug("[Client] Sending RELOAD – gun=%s", gunId);
        SoundAttractNetwork.INSTANCE.sendToServer(new TaczReloadMessage(gunId));
    }

    private static void debug(String fmt, Object... args) {
        if (!SoundAttractConfig.COMMON.debugLogging.get()) return;
        SoundAttractMod.LOGGER.info("[TaczIntegration] " + String.format(fmt, args));
    }
}
