package com.example.soundattract.integration;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.SoundMessage;
import com.example.soundattract.SoundAttractNetwork;
import net.minecraftforge.fml.ModList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TaczIntegrationEvents {

    private static final boolean IS_TACZ_LOADED = ModList.get().isLoaded("tacz");
    private static final String GUN_RELOAD_EVENT = "com.tacz.guns.api.event.common.GunReloadEvent";
    private static final String GUN_SHOOT_EVENT = "com.tacz.guns.api.event.common.GunShootEvent";
    private static final String IGUN_CLASS = "com.tacz.guns.api.item.IGun";
    private static final String ATTACHMENT_TYPE_CLASS = "com.tacz.guns.api.item.attachment.AttachmentType";
    private static final String GUN_ID_METHOD = "getGunId";
    private static final String ATTACHMENT_ID_METHOD = "getAttachmentId";
    private static final String TACZ_GUN_SOUND_ID = "tacz:gun";

    private static Class<?> igunClass;
    private static Class<?> attachmentTypeClass;
    private static Method gunIdMethod;
    private static Method attachmentIdMethod;

    public static void register() {
        if (!IS_TACZ_LOADED) return;
        try {
            Class<?> reloadEventClass = Class.forName(GUN_RELOAD_EVENT);
            Class<?> shootEventClass = Class.forName(GUN_SHOOT_EVENT);
            igunClass = Class.forName(IGUN_CLASS);
            attachmentTypeClass = Class.forName(ATTACHMENT_TYPE_CLASS);
            gunIdMethod = igunClass.getMethod(GUN_ID_METHOD, ItemStack.class);
            attachmentIdMethod = igunClass.getMethod(ATTACHMENT_ID_METHOD, ItemStack.class, attachmentTypeClass);
            // Register event handlers via reflection
            // ...
        } catch (Exception e) {
            // Tacz API not present, safe to ignore
        }
    }

    public static void onGunReload(Object event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.enableTaczIntegration.get()) return;

        LivingEntity entity = getEntity(event);
        if (entity instanceof Player player && !player.level().isClientSide()) {
            SoundEvent taczGunSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(TACZ_GUN_SOUND_ID));

            if (taczGunSound == null) return;

            double[] rw = calculateTaczReloadRangeWeight(player);
            double range = rw[0];
            double weight = rw[1];
            if (SoundAttractConfig.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[Tacz] GunReload: Player={}, Gun={}, Range={}, Weight={}", player.getName().getString(), getGunId(player), range, weight);
            }

            SoundTracker.addSound(
                    taczGunSound,
                    player.blockPosition(),
                    player.level().dimension().location().toString(),
                    range,
                    weight,
                    SoundAttractConfig.soundLifetimeTicks.get()
            );
        }
    }

    public static void onGunShoot(Object event) {
        LivingEntity shooter = getShooter(event);
        boolean isPlayer = shooter instanceof Player;
        boolean sneaking = isPlayer && ((Player)shooter).isCrouching();
        boolean crawling = isPlayer && ((Player)shooter).isSwimming();

        if (!IS_TACZ_LOADED || !SoundAttractConfig.enableTaczIntegration.get()) return;

        if (isPlayer) {
            Player player = (Player)shooter;
            double[] rw = calculateTaczShootRangeWeight(player);
            double range = rw[0];
            double weight = rw[1];
            if (SoundAttractConfig.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[Tacz] GunShoot: Player={}, Gun={}, Range={}, Weight={}, Sneaking={}, Crawling={}", player.getName().getString(), getGunId(player), range, weight, sneaking, crawling);
            }

            SoundTracker.addSound(
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(TACZ_GUN_SOUND_ID)),
                    player.blockPosition(),
                    player.level().dimension().location().toString(),
                    range,
                    weight,
                    SoundAttractConfig.soundLifetimeTicks.get()
            );
        }
    }

    private static double[] calculateTaczReloadRangeWeight(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        Object iGun = getIGun(gunStack);
        ResourceLocation gunId = null;
        if (iGun != null) {
            gunId = (ResourceLocation) invokeMethod(gunIdMethod, iGun, gunStack);
            double shootDb = getTaczGunShootDb(gunId);
            if (shootDb >= 0) {
                double reloadRange = shootDb / 20.0;
                double reloadWeight = (shootDb / 10.0) / 2.0;
                return new double[]{reloadRange, reloadWeight};
            }
        }
        return new double[]{SoundAttractConfig.taczReloadRange.get(), SoundAttractConfig.taczReloadWeight.get()};
    }

    private static double[] calculateTaczShootRangeWeight(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        Object iGun = getIGun(gunStack);
        ResourceLocation gunId = null;
        ResourceLocation attId = null;
        if (iGun != null) {
            gunId = (ResourceLocation) invokeMethod(gunIdMethod, iGun, gunStack);
            double db = getTaczGunShootDb(gunId);
            attId = (ResourceLocation) invokeMethod(attachmentIdMethod, iGun, gunStack, getAttachmentType("MUZZLE"));
            double origDb = db;
            if (attId != null) {
                double reduction = getTaczAttachmentReduction(attId);
                if (reduction > 0) {
                    db = Math.max(0, db - reduction);
                }
                return new double[]{db, db / 10.0};
            } else {
                return new double[]{db, db / 10.0};
            }
        }
        return new double[]{SoundAttractConfig.taczShootRange.get(), SoundAttractConfig.taczShootWeight.get()};
    }

    private static double getTaczGunShootDb(ResourceLocation gunId) {
        if (gunId == null) return -1;
        for (String raw : SoundAttractConfig.taczGunShootDecibels.get()) {
            String[] parts = raw.split(";");
            if (parts.length >= 2 && parts[0].equals(gunId.toString())) {
                try {
                    return Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    private static double getTaczAttachmentReduction(ResourceLocation attId) {
        if (attId == null) return 0;
        for (String raw : SoundAttractConfig.taczAttachmentReductions.get()) {
            String[] parts = raw.split(";");
            if (parts.length >= 2 && parts[0].equals(attId.toString())) {
                try {
                    return Double.parseDouble(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static ResourceLocation getGunId(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        Object iGun = getIGun(gunStack);
        if (iGun != null) {
            return (ResourceLocation) invokeMethod(gunIdMethod, iGun, gunStack);
        }
        return null;
    }

    private static Object getIGun(ItemStack gunStack) {
        try {
            return igunClass.getMethod("getIGunOrNull", ItemStack.class).invoke(null, gunStack);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getAttachmentType(String type) {
        try {
            return attachmentTypeClass.getField(type).get(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static LivingEntity getEntity(Object event) {
        try {
            return (LivingEntity) event.getClass().getMethod("getEntity").invoke(event);
        } catch (Exception e) {
            return null;
        }
    }

    private static LivingEntity getShooter(Object event) {
        try {
            return (LivingEntity) event.getClass().getMethod("getShooter").invoke(event);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invokeMethod(Method method, Object obj, Object... args) {
        try {
            return method.invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }
}