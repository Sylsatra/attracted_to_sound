package com.example.soundattract;

import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.FovEvents;
import com.example.soundattract.CamoUtil;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.ai.MobGroupManager;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import com.example.soundattract.integration.EnhancedAICompat;
import com.example.soundattract.integration.QuantifiedCacheCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StealthDetectionEvents {

    private static final Map<Mob, Integer> mobOutOfRangeTicks = new HashMap<>();
    private static final Map<Player, net.minecraft.world.phys.Vec3> lastPlayerPositions = new HashMap<>();
    private static final Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastMobPositions = new HashMap<>();
    private static long lastStealthCheckTick = -1;
    private static final Map<UUID, GunshotInfo> playerGunshotInfo = new HashMap<>();

    private static final Map<UUID, Double> XRAY_RANGE_CACHE = new HashMap<>();

    private static final Set<UUID> suppressedEdgeDetections = new HashSet<>();

    private static LivingEntity getAttackTargetCompat(Mob mob) {
        if (mob == null) return null;
        LivingEntity direct = mob.getTarget();
        if (direct != null) return direct;
        try {
            return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isTargetingPlayerCompat(Mob mob) {
        return getAttackTargetCompat(mob) instanceof Player;
    }

    public static void recordSuppressedEdgeDetection(Mob mob) {
        if (mob != null) suppressedEdgeDetections.add(mob.getUUID());
    }

    public static boolean consumeSuppressedEdgeDetection(Mob mob) {
        if (mob == null) return false;
        UUID id = mob.getUUID();
        if (suppressedEdgeDetections.remove(id)) {
            return true;
        }
        return false;
    }

    private static int getStealthCheckInterval() {
        return SoundAttractConfig.COMMON.stealthCheckInterval.get();
    }
    private static boolean hasConcealmentEnchant(ItemStack stack) {
        if (stack.isEmpty() || !stack.isEnchanted() || ModEnchantments.CONCEAL == null) { 
            return false;
        }
        Enchantment concealEnchant = ModEnchantments.CONCEAL.get();
        if (concealEnchant == null) { 
             if (SoundAttractConfig.COMMON.debugLogging.get() && !stack.isEmpty() && stack.isEnchanted()) {
                SoundAttractMod.LOGGER.warn("[HasConceal] Conceal enchantment not resolved from ModEnchantments for item: {}", stack.getDisplayName().getString());
            }
            return false;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(concealEnchant, stack) > 0;
    }
    public static class GunshotInfo {
        public final long timestamp;
        public final double detectionRange;
        public GunshotInfo(long timestamp, double detectionRange) {
            this.timestamp = timestamp;
            this.detectionRange = detectionRange;
        }
    }

    public static double getRealisticStealthDetectionRange(Mob target, Mob looker, Level level) {
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        double baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();

        if (target.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            double invisFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
            baseRange *= invisFactor;
        }

        int effectiveLight = 0;
        net.minecraft.core.BlockPos feet = target.blockPosition();
        net.minecraft.core.BlockPos eyes = feet.above();
        long dayTime = level.getDayTime() % 24000L;
        boolean isDay = dayTime >= 0 && dayTime < 12000L;
        if (level.isLoaded(feet)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet));
            if (isDay && level.canSeeSky(feet)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, feet));
            }
        }
        if (level.isLoaded(eyes)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, eyes));
            if (isDay && level.canSeeSky(eyes)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eyes));
            }
        }
        double neutral = SoundAttractConfig.COMMON.neutralLightLevel.get();
        double sensitivity = SoundAttractConfig.COMMON.lightLevelSensitivity.get();
        double lightEffect = (effectiveLight - neutral) * (sensitivity / 15.0);
        double lightFactor = 1.0 + lightEffect;
        lightFactor = Math.max(SoundAttractConfig.COMMON.minLightFactor.get(), lightFactor);
        lightFactor = Math.min(SoundAttractConfig.COMMON.maxLightFactor.get(), lightFactor);
        baseRange *= lightFactor;

        if (level.isRainingAt(feet)) {
            baseRange *= SoundAttractConfig.COMMON.rainStealthFactor.get();
        }
        if (level.isThundering()) {
            baseRange *= SoundAttractConfig.COMMON.thunderStealthFactor.get();
        }

        if (SoundAttractConfig.COMMON.enableHeldItemPenalty.get()) {
            int held = (target.getMainHandItem().isEmpty() ? 0 : 1) + (target.getOffhandItem().isEmpty() ? 0 : 1);
            if (held > 0) {
                double penaltyPerItem = SoundAttractConfig.COMMON.heldItemPenaltyFactor.get();
                for (int i = 0; i < held; i++) baseRange *= penaltyPerItem;
            }
        }

        if (SoundAttractConfig.COMMON.enableEnchantmentPenalty.get()) {
            int enchantedArmor = 0;
            for (net.minecraft.world.item.ItemStack armor : target.getArmorSlots()) {
                if (!armor.isEmpty() && armor.isEnchanted() && !hasConcealmentEnchant(armor)) enchantedArmor++;
            }
            if (enchantedArmor > 0) {
                double armorPenaltyFactor = SoundAttractConfig.COMMON.armorEnchantmentPenaltyFactor.get();
                for (int i = 0; i < enchantedArmor; i++) baseRange *= armorPenaltyFactor;
            }
            int enchantedHeld = 0;
            if (!target.getMainHandItem().isEmpty() && target.getMainHandItem().isEnchanted() && !hasConcealmentEnchant(target.getMainHandItem())) enchantedHeld++;
            if (!target.getOffhandItem().isEmpty() && target.getOffhandItem().isEnchanted() && !hasConcealmentEnchant(target.getOffhandItem())) enchantedHeld++;
            if (enchantedHeld > 0) {
                double heldItemEnchantPenalty = SoundAttractConfig.COMMON.heldItemEnchantmentPenaltyFactor.get();
                for (int i = 0; i < enchantedHeld; i++) baseRange *= heldItemEnchantPenalty;
            }
        }

        if (SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            java.util.Optional<Integer> armorColorOpt = getEffectiveArmorColorEntity(target);
            java.util.Optional<Integer> envColorOpt = getAverageEnvironmentalColorEntity(target, level);
            if (armorColorOpt.isPresent() && envColorOpt.isPresent()) {
                int armorColor = armorColorOpt.get();
                int envColor = envColorOpt.get();
                int rArmor = (armorColor >> 16) & 0xFF;
                int gArmor = (armorColor >> 8) & 0xFF;
                int bArmor = armorColor & 0xFF;
                int rEnv = (envColor >> 16) & 0xFF;
                int gEnv = (envColor >> 8) & 0xFF;
                int bEnv = envColor & 0xFF;
                int diff = Math.abs(rArmor - rEnv) + Math.abs(gArmor - gEnv) + Math.abs(bArmor - bEnv);
                int matchBonusThreshold = SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get();
                if (diff <= matchBonusThreshold) {
                    double maxBonusEffect = SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.get();
                    double effectivenessRatio = (matchBonusThreshold > 0) ? 1.0 - ((double) diff / matchBonusThreshold) : ((diff == 0) ? 1.0 : 0.0);
                    double actualBonus = maxBonusEffect * effectivenessRatio;
                    baseRange *= (1.0 - actualBonus);
                } else if (SoundAttractConfig.COMMON.enableEnvironmentalMismatchPenalty.get()) {
                    int mismatchThreshold = SoundAttractConfig.COMMON.environmentalMismatchThreshold.get();
                    if (diff > mismatchThreshold) {
                        double penaltyFactor = SoundAttractConfig.COMMON.environmentalMismatchPenaltyFactor.get();
                        baseRange *= penaltyFactor;
                    }
                }
            }
        }

        double moveThreshold = SoundAttractConfig.COMMON.movementThreshold.get();
        if (isMobMoving(target, moveThreshold)) {
            baseRange *= SoundAttractConfig.COMMON.movementStealthPenalty.get();
        } else {
            baseRange *= SoundAttractConfig.COMMON.stationaryStealthBonusFactor.get();
        }

        if (SoundAttractConfig.COMMON.enableCamouflage.get()) {
            java.util.List<String> camouflageItems = new java.util.ArrayList<>(SoundAttractConfig.COMMON.camouflageArmorItems.get());
            if (!camouflageItems.isEmpty()) {
                double effectToApply = 0.0;
                int totalActualArmorPieces = 0;
                long wornListedCamouflagePieces = 0;
                java.util.List<net.minecraft.world.item.ItemStack> armorItemsList = new java.util.ArrayList<>();
                target.getArmorSlots().forEach(armorItemsList::add);
                for (net.minecraft.world.item.ItemStack stack : armorItemsList) {
                    if (!stack.isEmpty()) totalActualArmorPieces++;
                    if (CamoUtil.isCamouflageArmorItem(stack.getItem())) {
                        wornListedCamouflagePieces++;
                    }
                }
                boolean fullSet = (totalActualArmorPieces == 4 && wornListedCamouflagePieces == totalActualArmorPieces && totalActualArmorPieces > 0);
                if (SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()) {
                    if (fullSet) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                    } else {
                        double totalEffectiveness = 0.0;
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            net.minecraft.world.item.ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            if (CamoUtil.isCamouflageArmorItem(stack.getItem())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                    }
                } else {
                    if (fullSet && SoundAttractConfig.COMMON.fullArmorStealthBonus.get() > 0) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                    } else {
                        double totalEffectiveness = 0.0;
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            net.minecraft.world.item.ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            if (CamoUtil.isCamouflageArmorItem(stack.getItem())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                    }
                }
                if (effectToApply > 0.0) {
                    double itemCamoMultiplier = 1.0 - Math.min(effectToApply, 0.99);
                    baseRange *= itemCamoMultiplier;
                }
            }
        }

        double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[GRSDR_MobTarget_End] Looker: {}, Target: {}, Final Range: {}",
                    looker.getName().getString(), target.getName().getString(), String.format("%.2f", finalCalculatedRange));
        }
        return finalCalculatedRange;
    }

    private static boolean isMobMoving(Mob mob, double threshold) {
        if (mob == null) return false;
        net.minecraft.world.phys.Vec3 current = mob.position();
        java.util.UUID id = mob.getUUID();
        net.minecraft.world.phys.Vec3 last = lastMobPositions.get(id);
        boolean moved = false;
        if (last != null) {
            double distSq = current.distanceToSqr(last);
            moved = distSq > (threshold * threshold);
        }
        lastMobPositions.put(id, current);
        return moved;
    }

    private static double getEffectiveXrayRange(Mob mob) {
        if (mob == null) return 0d;
        if (!SoundAttractConfig.COMMON.enableXrayTargeting.get()) return 0d;

        if (EnhancedAICompat.isEnhancedAiLoaded()) {
            double v = EnhancedAICompat.getXrayAttributeValue(mob);
            if (v > 0d) {
                return v;
            }
            return 0d;
        }

        try {
            String applyTagStr = SoundAttractConfig.COMMON.xrayApplyTag.get();
            if (applyTagStr == null || applyTagStr.isBlank()) return 0d;
            TagKey<EntityType<?>> applyTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(applyTagStr));
            if (!mob.getType().is(applyTag)) return 0d;

            if (SoundAttractConfig.COMMON.xrayRequireBetterNearby.get()) {
                String betterTagStr = SoundAttractConfig.COMMON.xrayBetterNearbyTag.get();
                if (betterTagStr == null || betterTagStr.isBlank()) return 0d;
                TagKey<EntityType<?>> betterTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(betterTagStr));
                if (!mob.getType().is(betterTag)) return 0d;
            }
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[XRAY] Tag check failed for mob {}: {}", mob.getName().getString(), e.getMessage());
            }
            return 0d;
        }

        Double cached = XRAY_RANGE_CACHE.get(mob.getUUID());
        if (cached != null) return cached;

        int max = SoundAttractConfig.COMMON.xrayMaxRange.get();
        if (max <= 0) {
            XRAY_RANGE_CACHE.put(mob.getUUID(), 0d);
            return 0d;
        }
        int min = SoundAttractConfig.COMMON.xrayMinRange.get();
        min = Math.max(0, Math.min(min, max));
        double chance = SoundAttractConfig.COMMON.xrayChance.get();
        if (mob.getRandom().nextDouble() >= chance) {
            XRAY_RANGE_CACHE.put(mob.getUUID(), 0d);
            return 0d;
        }
        int spread = max - min;
        int chosen = spread <= 0 ? max : (min + mob.getRandom().nextInt(spread + 1));
        double result = (double) chosen;
        XRAY_RANGE_CACHE.put(mob.getUUID(), result);
        return result;
    }

    private static java.util.Optional<Integer> getEffectiveArmorColorEntity(LivingEntity entity) {
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        boolean onlyDyedLeather = SoundAttractConfig.COMMON.environmentalCamouflageOnlyDyedLeather.get();
        for (net.minecraft.world.item.ItemStack itemStack : entity.getArmorSlots()) {
            if (itemStack.isEmpty()) continue;
            net.minecraft.world.item.Item item = itemStack.getItem();
            boolean colorAdded = false;
            if (item instanceof net.minecraft.world.item.ArmorItem armorItem && armorItem.getMaterial() == net.minecraft.world.item.ArmorMaterials.LEATHER && item instanceof net.minecraft.world.item.DyeableLeatherItem dyeableItem) {
                if (dyeableItem.hasCustomColor(itemStack)) {
                    colors.add(dyeableItem.getColor(itemStack));
                    colorAdded = true;
                }
            }
            if (onlyDyedLeather && !colorAdded) continue;
            if (!colorAdded) {
                net.minecraft.resources.ResourceLocation itemIdRL = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                if (itemIdRL != null) {
                    Integer mapped = SoundAttractConfig.customArmorColors.get(itemIdRL);
                    if (mapped != null) {
                        colors.add(mapped);
                    }
                }
            }
        }
        if (colors.isEmpty()) return java.util.Optional.empty();
        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : colors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }
        int n = colors.size();
        int avgR = (int) (totalR / n);
        int avgG = (int) (totalG / n);
        int avgB = (int) (totalB / n);
        int finalAvg = (avgR << 16) | (avgG << 8) | avgB;
        return java.util.Optional.of(finalAvg);
    }

    private static java.util.Optional<Integer> getAverageEnvironmentalColorEntity(LivingEntity entity, Level level) {
        if (entity == null || level == null) {
            return java.util.Optional.empty();
        }
        if (QuantifiedCacheCompat.isUsable()) {
            net.minecraft.core.BlockPos base = entity.blockPosition();
            String key = new StringBuilder(96)
                .append(level.dimension().location().toString()).append('|')
                .append(base.getX()).append(',').append(base.getY()).append(',').append(base.getZ())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_env_color_entity",
                key,
                () -> soundattract$computeAverageEnvironmentalColorEntity(entity, level),
                2L,
                8192L
            );
        }

        return soundattract$computeAverageEnvironmentalColorEntity(entity, level);
    }

    private static java.util.Optional<Integer> soundattract$computeAverageEnvironmentalColorEntity(LivingEntity entity, Level level) {
        java.util.List<Integer> blockColors = new java.util.ArrayList<>();
        net.minecraft.core.BlockPos base = entity.blockPosition();
        for (int y = 0; y >= -1; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    net.minecraft.core.BlockPos pos = base.offset(x, y, z);
                    if (level.isLoaded(pos)) {
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            @SuppressWarnings("deprecation")
                            int mapColor;
                            try {
                                mapColor = state.getMapColor(level, pos).col;
                            } catch (Throwable ignored) {
                                continue;
                            }
                            if (mapColor != 0) {
                                blockColors.add(mapColor);
                            }
                        }
                    }
                }
            }
        }
        if (blockColors.isEmpty()) return java.util.Optional.empty();
        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : blockColors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }
        int n = blockColors.size();
        int avgR = (int) (totalR / n);
        int avgG = (int) (totalG / n);
        int avgB = (int) (totalB / n);
        int finalAvg = (avgR << 16) | (avgG << 8) | avgB;
        return java.util.Optional.of(finalAvg);
    }
    public static void recordPlayerGunshot(Player player, double detectionRange) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        long currentTime = player.level().getGameTime();
        playerGunshotInfo.put(player.getUUID(), new GunshotInfo(currentTime, detectionRange));
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[Gunshot] Recorded gunshot for {} with range {}. Effective until tick {}.",
                player.getName().getString(),
                String.format("%.2f", detectionRange),
                currentTime + SoundAttractConfig.COMMON.gunshotDetectionDurationTicks.get()
            );
        }
    }
    private static Optional<Double> getActiveGunshotRange(Player player) {
        GunshotInfo info = playerGunshotInfo.get(player.getUUID());
        if (info == null) {
            return Optional.empty();
        }

        long currentTime = player.level().getGameTime();
        long duration = SoundAttractConfig.COMMON.gunshotDetectionDurationTicks.get();

        if ((currentTime - info.timestamp) < duration) {
            return Optional.of(info.detectionRange);
        } else {
            playerGunshotInfo.remove(player.getUUID());
            return Optional.empty();
        }
    }

    @SubscribeEvent
    public static void onMobAttemptTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        LivingEntity newTarget = event.getNewTarget();

        if (newTarget instanceof Player playerTarget) {
            if (playerTarget.isCreative() || playerTarget.isSpectator() || !playerTarget.isAlive()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[LivingChangeTargetEvent] Player {} is creative/spectator/dead. Allowing target by {}.",
                            playerTarget.getName().getString(), mob.getName().getString()
                    );
                }
                return;
            }

            if (!canMobDetectPlayer(mob, playerTarget)) {
                event.setCanceled(true);
                try {
                    LivingEntity mem = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                    if (mem == playerTarget) {
                        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    }
                } catch (Throwable ignored) {
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[LivingChangeTargetEvent] Mob {} targeting of Player {} CANCELED due to stealth rules.",
                            mob.getName().getString(), playerTarget.getName().getString()
                    );
                }
            } else {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[LivingChangeTargetEvent] Mob {} targeting of Player {} ALLOWED (passes stealth check).",
                            mob.getName().getString(), playerTarget.getName().getString()
                    );
                }
            }
        } else if (newTarget instanceof Mob targetMob) {
            if (!FovEvents.isTargetInFov(mob, targetMob, true)) {
                double xrayRange = getEffectiveXrayRange(mob);
                if (xrayRange > 0) {
                    double distSqXray = mob.distanceToSqr(targetMob);
                    if (distSqXray <= xrayRange * xrayRange) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                "[XRAY] Mob {} detects Mob {} within XRAY range {} (distSq {}).",
                                mob.getName().getString(), targetMob.getName().getString(),
                                String.format("%.2f", xrayRange), String.format("%.2f", distSqXray)
                            );
                        }
                    } else {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                "[LivingChangeTargetEvent] Mob {} cannot see Mob {} (FOV). CANCELED.",
                                mob.getName().getString(), targetMob.getName().getString()
                            );
                        }
                        event.setCanceled(true);
                        return;
                    }
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "[LivingChangeTargetEvent] Mob {} cannot see Mob {} (FOV). CANCELED.",
                                mob.getName().getString(), targetMob.getName().getString()
                        );
                    }
                    event.setCanceled(true);
                    return;
                }
            }
            double range = getRealisticStealthDetectionRange(targetMob, mob, mob.level());
            double distSq = mob.distanceToSqr(targetMob);
            if (distSq > range * range) {
                event.setCanceled(true);
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[LivingChangeTargetEvent] Mob {} targeting of Mob {} CANCELED by stealth (distSq: {}, rangeSq: {}).",
                            mob.getName().getString(), targetMob.getName().getString(),
                            String.format("%.2f", distSq), String.format("%.2f", (range * range))
                    );
                }
            } else if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[LivingChangeTargetEvent] Mob {} targeting of Mob {} ALLOWED (passes stealth check).",
                        mob.getName().getString(), targetMob.getName().getString()
                );
            }
        }
    }


    public static boolean canMobDetectPlayer(Mob mob, Player player) {
        if (mob == null || player == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[CanDetectPlayer] Called with null mob or player. Defaulting to detectable.");
            }
            return true;
        }
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CanDetectPlayer] Player {} is creative/spectator/dead. Bypassing stealth. Mob {}.", player.getName().getString(), mob.getName().getString());
            }
            return true;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return true;
        }

        Level level = mob.level();

        double xrayRange = getEffectiveXrayRange(mob);
        if (xrayRange > 0) {
            double distSqXray = mob.distanceToSqr(player);
            if (distSqXray <= xrayRange * xrayRange) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                        "[XRAY] Mob {} detects Player {} within XRAY range {} (distSq {}).",
                        mob.getName().getString(), player.getName().getString(), String.format("%.2f", xrayRange), String.format("%.2f", distSqXray)
                    );
                }
                return true;
            }
        }
        double detectionRange = getRealisticStealthDetectionRange(player, mob, level);
        double distSq = mob.distanceToSqr(player);

        if (distSq > detectionRange * detectionRange) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[StealthQuery] Player {} is OUT OF RANGE for mob {} (distSq: {}, rangeSq: {}).",
                        player.getName().getString(), mob.getName().getString(),
                        String.format("%.2f", distSq),
                        String.format("%.2f", (detectionRange * detectionRange))
                );
            }
            return false;
        }

        if (!FovEvents.isTargetInFov(mob, player, true)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[StealthQuery] Player {} is IN RANGE for mob {} but OUTSIDE FOV. Denying detection.",
                        player.getName().getString(), mob.getName().getString()
                );
            }
            return false;
        }



        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            try {
                boolean isEdge = MobGroupManager.isEdgeMob(mob);
                boolean isDeserter = MobGroupManager.isDeserter(mob);
                if (isEdge && !isDeserter) {
                    recordSuppressedEdgeDetection(mob);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[CanDetectPlayer] Suppressing EDGE mob {} (non-deserter) despite detectability; signaling RAID.", mob.getName().getString());
                    }
                    return false;
                }
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[CanDetectPlayer] Edge suppression check failed: {}", t.getMessage());
                }
            }
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "[StealthQuery] Player {} IS DETECTABLE by mob {} (in range and in FOV).",
                    player.getName().getString(), mob.getName().getString()
            );
        }
        return true;
    }

    public static boolean shouldSuppressTargeting(Mob mob) {
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return false;
        }
        LivingEntity target = getAttackTargetCompat(mob);
        if (target == null) {
            return false;
        }
        if (!(target instanceof Player player)) {
            return false;
        }
        return !canMobDetectPlayer(mob, player);
    }

    public static boolean shouldSuppressTargeting(Mob mob, Player player) {
        return !canMobDetectPlayer(mob, player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        long gameTime = event.getServer().overworld().getGameTime();
        int stealthCheckInterval = getStealthCheckInterval();

        if (gameTime % stealthCheckInterval != 0 || gameTime == lastStealthCheckTick) {
            return;
        }
        lastStealthCheckTick = gameTime;

        for (ServerLevel level : event.getServer().getAllLevels()) {

            int scanningRadius = Math.max(32, (int) Math.ceil(SoundAttractConfig.COMMON.maxStealthDetectionRange.get()) + 16);
            Set<Mob> mobsToCheck = new HashSet<>();
            for (net.minecraft.server.level.ServerPlayer serverPlayer : level.players()) {
                AABB scanArea = serverPlayer.getBoundingBox().inflate(scanningRadius);
                mobsToCheck.addAll(level.getEntitiesOfClass(Mob.class, scanArea, entity -> entity.isAlive() && isTargetingPlayerCompat(entity)));
            }
            for (Mob mob : mobsToCheck) {
                LivingEntity rawTarget = getAttackTargetCompat(mob);
                if (!(rawTarget instanceof Player playerTarget)) {
                    mobOutOfRangeTicks.remove(mob);
                    continue;
                }
                if (playerTarget.isCreative() || playerTarget.isSpectator()) {
                    mobOutOfRangeTicks.remove(mob);
                    continue;
                }

                boolean canCurrentlyDetect = canMobDetectPlayer(mob, playerTarget);

                if (canCurrentlyDetect) {
                    if (mobOutOfRangeTicks.remove(mob) != null) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[TickCheck] Mob {} regained direct detection of {}. Grace period reset.",
                                    mob.getName().getString(), playerTarget.getName().getString()
                            );
                        }
                    }
                } else {
                    int ticks = mobOutOfRangeTicks.getOrDefault(mob, 0) + stealthCheckInterval;
                    if (ticks >= SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[TickCheck] Mob {} lost target {} due to stealth grace period timeout.",
                                    mob.getName().getString(), playerTarget.getName().getString()
                            );
                        }
                        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
                            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
                        }
                        try {
                            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                        } catch (Throwable ignored) {
                        }
                        mob.setTarget(null);
                        mobOutOfRangeTicks.remove(mob);
                    } else {
                        mobOutOfRangeTicks.put(mob, ticks);
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[TickCheck] Mob {} cannot detect {}. In grace period ({}/{}).",
                                    mob.getName().getString(), playerTarget.getName().getString(),
                                    ticks, SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()
                            );
                        }
                    }
                }
            }
        }
    }

    public static boolean isPlayerMoving(Player player, double threshold) {
        if (player == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 currentPos = player.position();
        net.minecraft.world.phys.Vec3 lastPos = lastPlayerPositions.get(player);
        boolean moved = false;
        if (lastPos != null) {
            double distSq = currentPos.distanceToSqr(lastPos);
            moved = distSq > (threshold * threshold);
        }
        lastPlayerPositions.put(player, currentPos);
        return moved;
    }

    private static PlayerStance determinePlayerStance(Player player) {
        Pose currentPose = player.getPose();
        boolean isVisuallyCrawling = player.isVisuallyCrawling();
        boolean isCrouching = player.isCrouching();
        float playerHeight = player.getDimensions(currentPose).height;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "[DetermineStanceDetails] Player: {}, Pose: {}, isVisuallyCrawling: {}, isCrouching: {}, Height: {}",
                    player.getName().getString(),
                    currentPose,
                    isVisuallyCrawling,
                    isCrouching,
                    String.format("%.2f", playerHeight)
            );
        }

        if (isVisuallyCrawling) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is CRAWLING (VisualCrawl: true, Pose: {}, Height: {})",
                        player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
            }
            return PlayerStance.CRAWLING;
        }

        if (currentPose == Pose.SWIMMING || currentPose == Pose.SPIN_ATTACK || currentPose == Pose.FALL_FLYING) {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is CRAWLING-EQUIVALENT (Pose: {}, Height: {})",
                        player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
            }
            return PlayerStance.CRAWLING;
        }

        if (isCrouching) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is SNEAKING (Pose: {}, Crouching: {}, Height: {})",
                        player.getName().getString(), currentPose, isCrouching, String.format("%.2f", playerHeight));
            }
            return PlayerStance.SNEAKING;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is STANDING (Pose: {}, Height: {})",
                    player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
        }
        return PlayerStance.STANDING;
    }

    public static double getRealisticStealthDetectionRange(Player player, Mob mob, Level level) {
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }
        double baseRange;
        Optional<Double> gunshotRangeOpt = getActiveGunshotRange(player);
        PlayerStance currentStance = determinePlayerStance(player);
        if (gunshotRangeOpt.isPresent()) {
            baseRange = gunshotRangeOpt.get();
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                "[GRSDR_Update] Player {} has active gunshot flash. Initial range set to {}.",
                player.getName().getString(), String.format("%.2f", baseRange)
                );
            }
            double standingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
            double currentPoseBaseRange;
            switch (currentStance) {
                case CRAWLING:
                    currentPoseBaseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                    break;
                case SNEAKING:
                    currentPoseBaseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                    break;
                default:
                    currentPoseBaseRange = standingRange;
                    break;
            }
            double poseReduction = Math.max(0, standingRange - currentPoseBaseRange); 
            baseRange -= poseReduction;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                "[GRSDR_Update] Gunshot range adjusted by pose {}. Reduction of {}. New range: {}.",
                currentStance, String.format("%.2f", poseReduction), String.format("%.2f", baseRange)
                );
            }
        } else {
            com.example.soundattract.config.MobProfile mobProfile = SoundAttractConfig.getMatchingProfile(mob);
            Optional<Double> override = (mobProfile != null) ? mobProfile.getDetectionOverride(currentStance) : Optional.empty();
            if (override.isPresent()) {
                baseRange = override.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                    "[GRSDR_Update] Mob {} using profile '{}' detection range for stance {}: {}",
                    mob.getName().getString(), mobProfile.getProfileName(), currentStance, baseRange
                    );
                }
            } else {
                com.example.soundattract.config.PlayerProfile playerProfile = SoundAttractConfig.getMatchingPlayerProfile(player);
                Optional<Double> playerOverride = (playerProfile != null) ? playerProfile.getDetectionOverride(currentStance) : Optional.empty();
                if (playerOverride.isPresent()) {
                    baseRange = playerOverride.get();
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "[GRSDR_Update] Player {} matched player profile '{}' for stance {}: {}",
                                player.getName().getString(), playerProfile.getProfileName(), currentStance, baseRange
                        );
                    }
                } else {
                    switch (currentStance) {
                        case CRAWLING:
                            baseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                            break;
                        case SNEAKING:
                            baseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                            break;
                        case STANDING:
                        default:
                            baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
                            break;
                    }
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        if (mobProfile != null) {
                            SoundAttractMod.LOGGER.info(
                                    "[GRSDR_Update] Mob {} profile '{}' has no override for stance {}. No matching player profile override. Using default: {}",
                                    mob.getName().getString(), mobProfile.getProfileName(), currentStance, baseRange
                            );
                        } else {
                            SoundAttractMod.LOGGER.info(
                                    "[GRSDR_Update] No mob profile override and no player profile override for Mob {}. Using default for stance {}: {}",
                                    mob.getName().getString(), currentStance, baseRange
                            );
                        }
                    }
                }
            }
        }
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            double invisFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
            baseRange *= invisFactor;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[GRSDR_Update] Player {} is invisible, reducing baseRange to {}",
                        player.getName().getString(), String.format("%.2f", baseRange)
                );
            }
        }

        BlockPos basePos = player.blockPosition();
        long dayTime = level.getDayTime() % 24000L;
        boolean isDay = dayTime >= 0 && dayTime < 12000L;

        int effectiveLight = 0;
        BlockPos playerFeetPos = player.blockPosition();
        BlockPos playerEyesPos = playerFeetPos.above();

        if (level.isLoaded(playerFeetPos)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.BLOCK, playerFeetPos));
            if (isDay && level.canSeeSky(playerFeetPos)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.SKY, playerFeetPos));
            }
        }
        if (level.isLoaded(playerEyesPos)) {
             effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.BLOCK, playerEyesPos));
            if (isDay && level.canSeeSky(playerEyesPos)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.SKY, playerEyesPos));
            }
        }

        for (ItemStack s : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (s.getItem() instanceof BlockItem bi) {
                BlockState def = bi.getBlock().defaultBlockState();
                if (level.isLoaded(basePos)) {
                    int emit = def.getLightEmission(level, basePos);
                    effectiveLight = Math.max(effectiveLight, emit);
                }
            }
        }

        double neutral = SoundAttractConfig.COMMON.neutralLightLevel.get();
        double sensitivity = SoundAttractConfig.COMMON.lightLevelSensitivity.get();
        double lightEffect = (effectiveLight - neutral) * (sensitivity / 15.0);
        double lightFactor = 1.0 + lightEffect;
        lightFactor = Math.max(SoundAttractConfig.COMMON.minLightFactor.get(), lightFactor);
        lightFactor = Math.min(SoundAttractConfig.COMMON.maxLightFactor.get(), lightFactor);
        baseRange *= lightFactor;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[GRSDR_Update] Light - EffectiveLight: {}, LightFactor (clamped): {}, baseRange: {}",
                    effectiveLight, String.format("%.2f", lightFactor), String.format("%.2f", baseRange));
        }
        if (SoundAttractConfig.COMMON.enableHeldItemPenalty.get()) {
            int heldItemCount = 0;
            if (!player.getMainHandItem().isEmpty()) heldItemCount++;
            if (!player.getOffhandItem().isEmpty()) heldItemCount++;
            if (heldItemCount > 0) {
                double penaltyPerItem = SoundAttractConfig.COMMON.heldItemPenaltyFactor.get();
                for (int i = 0; i < heldItemCount; i++) {
                    baseRange *= penaltyPerItem;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR_Update] Held Item Penalty: {} items, factor {:.2f} (applied {} times) -> {:.2f}",
                            heldItemCount, penaltyPerItem, heldItemCount, baseRange);
                }
            }
        }
        if (SoundAttractConfig.COMMON.enableEnchantmentPenalty.get()) {
            int visiblyEnchantedArmorPieces = 0;
            for (ItemStack armorStack : player.getArmorSlots()) {
                if (!armorStack.isEmpty() && armorStack.isEnchanted() && !hasConcealmentEnchant(armorStack)) {
                    visiblyEnchantedArmorPieces++;
                }
            }
            if (visiblyEnchantedArmorPieces > 0) {
                double armorPenaltyFactor = SoundAttractConfig.COMMON.armorEnchantmentPenaltyFactor.get();
                for (int i = 0; i < visiblyEnchantedArmorPieces; i++) {
                    baseRange *= armorPenaltyFactor;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR_Update] Armor Enchant Penalty: {} pieces, factor {:.2f} (applied {} times) -> {:.2f}",
                            visiblyEnchantedArmorPieces, armorPenaltyFactor, visiblyEnchantedArmorPieces, baseRange);
                }
            }
            int visiblyEnchantedHeldItems = 0;
            if (!player.getMainHandItem().isEmpty() && player.getMainHandItem().isEnchanted() && !hasConcealmentEnchant(player.getMainHandItem())) {
                visiblyEnchantedHeldItems++;
            }
            if (!player.getOffhandItem().isEmpty() && player.getOffhandItem().isEnchanted() && !hasConcealmentEnchant(player.getOffhandItem())) {
                visiblyEnchantedHeldItems++;
            }
            if (visiblyEnchantedHeldItems > 0) {
                double heldItemEnchantPenalty = SoundAttractConfig.COMMON.heldItemEnchantmentPenaltyFactor.get();
                for (int i = 0; i < visiblyEnchantedHeldItems; i++) {
                    baseRange *= heldItemEnchantPenalty;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR_Update] Held Item Enchant Penalty: {} items, factor {:.2f} (applied {} times) -> {:.2f}",
                            visiblyEnchantedHeldItems, heldItemEnchantPenalty, visiblyEnchantedHeldItems, baseRange);
                }
            }
        }
        List<String> camouflageItems = new ArrayList<>(SoundAttractConfig.COMMON.camouflageArmorItems.get());

        if (SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            Optional<Integer> armorColorOpt = getEffectiveArmorColor(player);
            Optional<Integer> envColorOpt = getAverageEnvironmentalColor(player, level);

            if (armorColorOpt.isPresent() && envColorOpt.isPresent()) {
                int armorColor = armorColorOpt.get();
                int envColor = envColorOpt.get();

                int rArmor = (armorColor >> 16) & 0xFF;
                int gArmor = (armorColor >> 8) & 0xFF;
                int bArmor = armorColor & 0xFF;

                int rEnv = (envColor >> 16) & 0xFF;
                int gEnv = (envColor >> 8) & 0xFF;
                int bEnv = envColor & 0xFF;

                int diff = Math.abs(rArmor - rEnv) + Math.abs(gArmor - gEnv) + Math.abs(bArmor - bEnv);
                int matchBonusThreshold = SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get();

                if (diff <= matchBonusThreshold) {
                    double maxBonusEffect = SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.get();
                    double effectivenessRatio;
                    if (matchBonusThreshold > 0) {
                        effectivenessRatio = 1.0 - ((double) diff / matchBonusThreshold);
                    } else {
                        effectivenessRatio = (diff == 0) ? 1.0 : 0.0;
                    }

                    double actualBonusEffectiveness = maxBonusEffect * effectivenessRatio;
                    baseRange *= (1.0 - actualBonusEffectiveness);

                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "[EnvCamo] Player {} BONUS: armor=0x{}, env=0x{}, diff={}, matchThold={}, ratio={}, effect={}, newRange={}",
                                player.getName().getString(), String.format("%06X", armorColor), String.format("%06X", envColor),
                                diff, matchBonusThreshold, String.format("%.2f", effectivenessRatio),
                                String.format("%.2f", actualBonusEffectiveness), String.format("%.2f", baseRange)
                        );
                    }
                } else if (SoundAttractConfig.COMMON.enableEnvironmentalMismatchPenalty.get()) {
                    int mismatchPenaltyThreshold = SoundAttractConfig.COMMON.environmentalMismatchThreshold.get();
                    if (diff > mismatchPenaltyThreshold) {
                        double penaltyFactor = SoundAttractConfig.COMMON.environmentalMismatchPenaltyFactor.get();
                        baseRange *= penaltyFactor;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[EnvCamo] Player {} PENALTY: armor=0x{}, env=0x{}, diff={}, mismatchThold={}, penaltyFactor={}, newRange={}",
                                    player.getName().getString(), String.format("%06X", armorColor), String.format("%06X", envColor),
                                    diff, mismatchPenaltyThreshold, String.format("%.2f", penaltyFactor), String.format("%.2f", baseRange)
                            );
                        }
                    } else {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[EnvCamo] Player {} NEUTRAL: armor=0x{}, env=0x{}, diff={}, no bonus or penalty from env camo.",
                                    player.getName().getString(), String.format("%06X", armorColor), String.format("%06X", envColor), diff
                            );
                        }
                    }
                }
            } else {
                 if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[EnvCamo] Player {} - Could not get armor or environment color. Skipping.", player.getName().getString());
                 }
            }
        }

        if (level.isRainingAt(player.blockPosition())) {
            baseRange *= SoundAttractConfig.COMMON.rainStealthFactor.get();
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[GRSDR_Update] Raining. Factor applied. baseRange: {}", String.format("%.2f", baseRange));
            }
        }
        if (level.isThundering()) {
            baseRange *= SoundAttractConfig.COMMON.thunderStealthFactor.get();
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[GRSDR_Update] Thundering. Factor applied. baseRange: {}", String.format("%.2f", baseRange));
            }
        }

        if (currentStance != PlayerStance.SNEAKING && currentStance != PlayerStance.CRAWLING) {
            if (isPlayerMoving(player, SoundAttractConfig.COMMON.movementThreshold.get())) {
                baseRange *= SoundAttractConfig.COMMON.movementStealthPenalty.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR_Update] Player moving (not sneak/crawl). Penalty applied. baseRange: {}", String.format("%.2f", baseRange));
                }
            } else {
                baseRange *= SoundAttractConfig.COMMON.stationaryStealthBonusFactor.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR_Update] Player stationary (not sneak/crawl). Bonus applied. baseRange: {}", String.format("%.2f", baseRange));
                }
            }
        } else {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[GRSDR_Update] Player sneaking/crawling. Movement penalty/bonus not applied here (handled by stance base range). baseRange: {}", String.format("%.2f", baseRange));
            }
        }

        if (SoundAttractConfig.COMMON.enableCamouflage.get()) {
            if (!camouflageItems.isEmpty()) {
                double effectToApply = 0.0;
                int totalActualArmorPieces = 0;
                long wornListedCamouflagePieces = 0;
                for (ItemStack armorStack : player.getArmorSlots()) {
                    if (!armorStack.isEmpty()) {
                        totalActualArmorPieces++;
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(armorStack.getItem());
                        if (itemId != null && camouflageItems.contains(itemId.toString())) {
                            wornListedCamouflagePieces++;
                        }
                    }
                }
                boolean isActuallyWearingFullSetOfListedItems = (totalActualArmorPieces == 4 && wornListedCamouflagePieces == totalActualArmorPieces && totalActualArmorPieces > 0);

                if (SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()) {
                    if (isActuallyWearingFullSetOfListedItems) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} wearing full set of listed items (requireFullSet=true). Applying fullArmorStealthBonus: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    } else {
                        double totalEffectiveness = 0.0;
                        List<ItemStack> armorItemsList = new ArrayList<>();
                        player.getArmorSlots().forEach(armorItemsList::add);
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            Item item = stack.getItem();
                            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                            if (itemId != null && camouflageItems.contains(itemId.toString())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                             SoundAttractMod.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} wearing partial listed camo (requireFullSet=true). Applying summed per-piece effectiveness: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    }
                } else {
                    if (isActuallyWearingFullSetOfListedItems && SoundAttractConfig.COMMON.fullArmorStealthBonus.get() > 0) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} wearing full set of listed items (requireFullSet=false, using full bonus). Applying fullArmorStealthBonus: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    } else {
                        double totalEffectiveness = 0.0;
                        List<ItemStack> armorItemsList = new ArrayList<>();
                        player.getArmorSlots().forEach(armorItemsList::add);
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            Item item = stack.getItem();
                            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                            if (itemId != null && camouflageItems.contains(itemId.toString())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} (requireFullSet=false). Applying summed per-piece effectiveness: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    }
                }

                if (effectToApply > 0.0) {
                    double itemCamoMultiplier = 1.0 - Math.min(effectToApply, 0.99);
                    baseRange *= itemCamoMultiplier;
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "[GRSDR_Update ItemCamo] Player {} range after general item camouflage: {}. Applied multiplier: {} (Effect: {})",
                                player.getName().getString(), String.format("%.2f", baseRange),
                                String.format("%.2f", itemCamoMultiplier), String.format("%.2f", effectToApply)
                        );
                    }
                }

                double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[GRSDR_End] Mob: {}, Player: {}, Final Calculated Range (camo items processed): {}",
                            mob.getName().getString(), player.getName().getString(), String.format("%.2f", finalCalculatedRange)
                    );
                }
                return finalCalculatedRange;
            } else {
                double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[GRSDR_End] Mob: {}, Player: {}, Item Camo enabled but no items configured. Final Range: {}",
                            mob.getName().getString(), player.getName().getString(), String.format("%.2f", finalCalculatedRange)
                    );
                }
                return finalCalculatedRange;
            }
        } else {
            double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[GRSDR_End] Mob: {}, Player: {}, Item Camo system disabled. Final Range: {}",
                        mob.getName().getString(), player.getName().getString(), String.format("%.2f", finalCalculatedRange)
                );
            }
            return finalCalculatedRange;
        }
    }


    private static Optional<Integer> getEffectiveArmorColor(Player player) {
        List<Integer> colors = new ArrayList<>();
        boolean onlyDyedLeather = SoundAttractConfig.COMMON.environmentalCamouflageOnlyDyedLeather.get();

        for (ItemStack itemStack : player.getArmorSlots()) {
            if (itemStack.isEmpty()) {
                continue;
            }

            Item item = itemStack.getItem();
            boolean colorAdded = false;

            if (item instanceof ArmorItem armorItem && armorItem.getMaterial() == ArmorMaterials.LEATHER && item instanceof DyeableLeatherItem dyeableItem) {
                if (dyeableItem.hasCustomColor(itemStack)) {
                    colors.add(dyeableItem.getColor(itemStack));
                    colorAdded = true;
                }
            }

            if (onlyDyedLeather && !colorAdded) {
                continue;
            }

            if (!colorAdded) {
                ResourceLocation itemIdRL = ForgeRegistries.ITEMS.getKey(item);
                if (itemIdRL != null) {
                    Integer mappedColorValue = SoundAttractConfig.customArmorColors.get(itemIdRL);
                    if (mappedColorValue != null) {
                        colors.add(mappedColorValue);
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[GetArmorColor] Using MAPPED color for {}: 0x{}",
                                    itemIdRL, String.format("%06X", mappedColorValue));
                        }
                    } else {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[GetArmorColor] Item {} not found in custom_armor_color_map.", itemIdRL);
                        }
                    }
                }
            }
        }
        if (colors.isEmpty()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[GetArmorColor] No determinable armor colors found.");
            }
            return Optional.empty();
        }

        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : colors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }

        int numColors = colors.size();
        if (numColors == 0) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[GetArmorColor] numColors is 0 after processing, this should not happen if colors list was not empty.");
            }
            return Optional.empty();
        }
        int avgR = (int) (totalR / numColors);
        int avgG = (int) (totalG / numColors);
        int avgB = (int) (totalB / numColors);
        int finalAvgColor = (avgR << 16) | (avgG << 8) | avgB;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[GetArmorColor] Average armor color: 0x{} from {} pieces.",
                    String.format("%06X", finalAvgColor), numColors);
        }
        return Optional.of(finalAvgColor);
    }


    private static Optional<Integer> getAverageEnvironmentalColor(Player player, Level level) {
        if (player == null || level == null) {
            return Optional.empty();
        }
        if (QuantifiedCacheCompat.isUsable()) {
            BlockPos playerPos = player.blockPosition();
            String key = new StringBuilder(96)
                .append(level.dimension().location().toString()).append('|')
                .append(playerPos.getX()).append(',').append(playerPos.getY()).append(',').append(playerPos.getZ())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_env_color_player",
                key,
                () -> soundattract$computeAverageEnvironmentalColor(player, level),
                2L,
                8192L
            );
        }

        return soundattract$computeAverageEnvironmentalColor(player, level);
    }

    private static Optional<Integer> soundattract$computeAverageEnvironmentalColor(Player player, Level level) {
        List<Integer> blockColors = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();

        for (int yOffset = 0; yOffset >= -1; yOffset--) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    BlockPos currentPos = playerPos.offset(xOffset, yOffset, zOffset);
                    if (level.isLoaded(currentPos)) {
                        BlockState blockState = level.getBlockState(currentPos);
                        if (!blockState.isAir()) {
                            @SuppressWarnings("deprecation")
                            int mapColor;
                            try {
                                mapColor = blockState.getMapColor(level, currentPos).col;
                            } catch (Throwable ignored) {
                                continue;
                            }
                            if (mapColor != 0) blockColors.add(mapColor);
                        }
                    }
                }
            }
        }

        if (blockColors.isEmpty()) {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                 SoundAttractMod.LOGGER.info("[GetEnvColor] No block map colors found in sampling area.");
            }
            return Optional.empty();
        }

        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : blockColors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }

        int numColors = blockColors.size();
        if (numColors == 0) {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                 SoundAttractMod.LOGGER.warn("[GetEnvColor] numColors is 0 after processing, this should not happen if blockColors list was not empty.");
            }
            return Optional.empty();
        }
        int avgR = (int) (totalR / numColors);
        int avgG = (int) (totalG / numColors);
        int avgB = (int) (totalB / numColors);
        int finalAvgColor = (avgR << 16) | (avgG << 8) | avgB;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[GetEnvColor] Average environment color: 0x{} from {} blocks.",
                    String.format("%06X", finalAvgColor), numColors);
        }
        return Optional.of(finalAvgColor);
    }
}