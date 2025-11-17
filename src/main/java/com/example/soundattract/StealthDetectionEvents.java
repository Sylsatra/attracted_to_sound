package com.example.soundattract;

import net.minecraft.util.math.Box;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.FovEvents;
import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.mixin.ServerChunkManagerAccessor;
import com.example.soundattract.mixin.ServerChunkLoadingManagerAccessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.component.DataComponentTypes;
import com.example.soundattract.config.PlayerProfile;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ArmorItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerChunkManager;

public class StealthDetectionEvents {

    private static class GunshotInfo {

        public final double detectionRange;
        public final long timestamp;

        public GunshotInfo(double detectionRange, long timestamp) {
            this.detectionRange = detectionRange;
            this.timestamp = timestamp;
        }
    }

    public static void resetXrayCache() {
        XRAY_RANGE_CACHE.clear();
    }

    private static double getEffectiveXrayRange(MobEntity mob) {
        if (mob == null || SoundAttractMod.CONFIG == null) {
            return 0.0;
        }
        if (!SoundAttractMod.CONFIG.enableXrayTargeting) {
            return 0.0;
        }

        try {
            String applyTagStr = SoundAttractMod.CONFIG.xrayApplyTag;
            if (applyTagStr == null || applyTagStr.isBlank()) return 0.0;
            TagKey<EntityType<?>> applyTag = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(applyTagStr));
            if (!mob.getType().isIn(applyTag)) return 0.0;

            if (SoundAttractMod.CONFIG.xrayRequireBetterNearby) {
                String betterTagStr = SoundAttractMod.CONFIG.xrayBetterNearbyTag;
                if (betterTagStr == null || betterTagStr.isBlank()) return 0.0;
                TagKey<EntityType<?>> betterTag = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(betterTagStr));
                if (!mob.getType().isIn(betterTag)) return 0.0;
            }
        } catch (Exception e) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[XRAY] Tag check failed for mob {}: {}", mob.getName().getString(), e.getMessage());
            }
            return 0.0;
        }

        Double cached = XRAY_RANGE_CACHE.get(mob.getUuid());
        if (cached != null) {
            return cached;
        }

        int max = SoundAttractMod.CONFIG.xrayMaxRange;
        if (max <= 0) {
            XRAY_RANGE_CACHE.put(mob.getUuid(), 0.0);
            return 0.0;
        }
        int min = SoundAttractMod.CONFIG.xrayMinRange;
        min = Math.max(0, Math.min(min, max));
        double chance = SoundAttractMod.CONFIG.xrayChance;
        if (mob.getRandom().nextDouble() >= chance) {
            XRAY_RANGE_CACHE.put(mob.getUuid(), 0.0);
            return 0.0;
        }
        int spread = max - min;
        int chosen = spread <= 0 ? max : (min + mob.getRandom().nextInt(spread + 1));
        double result = (double) chosen;
        XRAY_RANGE_CACHE.put(mob.getUuid(), result);
        return result;
    }

    private static final Map<UUID, GunshotInfo> playerGunshotInfo = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> XRAY_RANGE_CACHE = new ConcurrentHashMap<>();

    public static void recordPlayerGunshot(PlayerEntity player, double detectionRange) {
        if (player == null || SoundAttractMod.CONFIG == null) {
            return;
        }
        long timestamp = player.getWorld().getTime();
        playerGunshotInfo.put(player.getUuid(), new GunshotInfo(detectionRange, timestamp));

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                    "[StealthDetection] Recorded gunshot flash for {} with range {} at tick {}",
                    player.getName().getString(),
                    String.format("%.2f", detectionRange),
                    timestamp
            );
        }
    }

    private static Optional<Double> getActiveGunshotRange(PlayerEntity player) {
        GunshotInfo info = playerGunshotInfo.get(player.getUuid());
        if (info == null || SoundAttractMod.CONFIG == null) {
            return Optional.empty();
        }

        long currentTime = player.getWorld().getTime();
        long duration = SoundAttractMod.CONFIG.gunshotDetectionDurationTicks;

        if ((currentTime - info.timestamp) < duration) {
            return Optional.of(info.detectionRange);
        } else {

            playerGunshotInfo.remove(player.getUuid());
            return Optional.empty();
        }
    }
    private static final Map<java.util.UUID, Double> camoCache = new HashMap<>();
    private static final Map<java.util.UUID, Long> camoCacheTick = new HashMap<>();

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {

            if (SoundAttractMod.CONFIG == null) {
                return;
            }

            int checkInterval = SoundAttractMod.CONFIG.stealthCheckInterval;
            long tick = server.getOverworld().getTime();

            if (tick % checkInterval != 0) {
                return;
            }

            for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
                Set<MobEntity> mobs = new java.util.HashSet<>();

                for (PlayerEntity player : world.getPlayers()) {
                    ServerChunkManager serverChunkManager = world.getChunkManager();

                    ServerChunkLoadingManager loadingManager = ((ServerChunkManagerAccessor) serverChunkManager).getChunkLoadingManager();

                    int viewDistance = ((ServerChunkLoadingManagerAccessor) loadingManager).getWatchDistance();

                    double searchRadius = viewDistance * 16.0;

                    Box searchBox = player.getBoundingBox().expand(searchRadius);
                    mobs.addAll(world.getEntitiesByClass(MobEntity.class, searchBox, mob -> mob.getTarget() instanceof PlayerEntity));
                }

                for (MobEntity mob : mobs) {
                    try {

                        if (!(mob.getTarget() instanceof PlayerEntity player) || !player.isAlive()) {
                            continue;
                        }

                        if (!(mob instanceof com.example.soundattract.accessor.StealthTargetingAccessor accessor)) {
                            continue;
                        }

                        double xrayRange = getEffectiveXrayRange(mob);
                        double distSq = mob.squaredDistanceTo(player);
                        boolean xrayDetect = xrayRange > 0 && distSq <= xrayRange * xrayRange;

                        boolean isCurrentlyDetectable = xrayDetect ||
                                (FovEvents.isTargetInFov(mob, player, true)
                                        && (mob.distanceTo(player) <= computeFullDetectionRange(mob, player, world)));

                        if (isCurrentlyDetectable) {

                            accessor.soundattract_setLosingTargetTicks(0);
                        } else {

                            int currentTicks = accessor.soundattract_getLosingTargetTicks();
                            currentTicks += checkInterval;
                            accessor.soundattract_setLosingTargetTicks(currentTicks);

                            int gracePeriodTicks = SoundAttractMod.CONFIG.targetLossGracePeriodTicks;

                            if (currentTicks >= gracePeriodTicks) {

                                mob.setTarget(null);
                                accessor.soundattract_setLosingTargetTicks(0);

                                if (SoundAttractMod.CONFIG.debugLogging) {
                                    SoundAttractMod.LOGGER.info(
                                            "[StealthDetectionEvents] {} lost sight of {} for ~{} ticks. Clearing target.",
                                            mob.getName().getString(),
                                            player.getName().getString(),
                                            gracePeriodTicks
                                    );
                                }
                            }
                        }
                    } catch (Exception ex) {
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.error("[StealthDetectionEvents] Exception in mob tick for mob " + (mob != null ? mob.getName().getString() : "null"), ex);
                        }
                    }
                }
            }
        });
    }

    public static boolean canMobDetectPlayer(MobEntity mob, PlayerEntity player) {
        if (mob == null || player == null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[CanDetectPlayer] Called with null mob or player. Defaulting to detectable.");
            }
            return true;
        }

        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[CanDetectPlayer] Player {} is creative/spectator/dead. Bypassing stealth. Mob {}.", player.getDisplayName().getString(), mob.getDisplayName().getString());
            }
            return true;
        }

        boolean canSee = FovEvents.hasSmartLineOfSight(mob, player);

        if (SoundAttractMod.CONFIG.debugLogging && !canSee) {

            SoundAttractMod.LOGGER.info("[CanDetectPlayer] mob.canSee() returned false for {}.", player.getName().getString());
        }

        return canSee;
    }

    public static double computeFullDetectionRange(MobEntity mob,
            PlayerEntity player,
            net.minecraft.world.World level) {

        double range;
        PlayerStance stance = determinePlayerStance(player);
        Optional<Double> gunshotRangeOpt = getActiveGunshotRange(player);

        if (gunshotRangeOpt.isPresent()) {
            range = gunshotRangeOpt.get();
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Player {} has active gunshot flash. Initial range: {}",
                        player.getName().getString(), String.format("%.2f", range)
                );
            }

            double standingRange = SoundAttractMod.CONFIG.standingDetectionRange;
            double currentPoseBaseRange;
            switch (stance) {
                case CRAWLING ->
                    currentPoseBaseRange = SoundAttractMod.CONFIG.crawlDetectionRange;
                case SNEAKING ->
                    currentPoseBaseRange = SoundAttractMod.CONFIG.sneakDetectionRange;
                default ->
                    currentPoseBaseRange = standingRange;
            }
            double poseReduction = Math.max(0, standingRange - currentPoseBaseRange);
            range -= poseReduction;

            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Gunshot range adjusted by pose {}. Reduction: {}, New range: {}.",
                        stance, String.format("%.2f", poseReduction), String.format("%.2f", range)
                );
            }
        } else {
            MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);
            Optional<Double> overrideOpt = (profile != null) ? profile.getDetectionOverride(stance) : Optional.empty();

            if (overrideOpt.isPresent()) {
                range = overrideOpt.get();
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                            "[StealthDetection] Applied mob profile override for mob {} with stance {} → range {}",
                            mob.getName().getString(),
                            stance,
                            String.format("%.2f", range)
                    );
                }
            } else {
                PlayerProfile pProfile = SoundAttractMod.CONFIG.getMatchingPlayerProfile(player);
                Optional<Double> pOverrideOpt = (pProfile != null) ? pProfile.getDetectionOverride(stance) : Optional.empty();
                if (pOverrideOpt.isPresent()) {
                    range = pOverrideOpt.get();
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                                "[StealthDetection] Applied player profile override for {} with stance {} → range {}",
                                player.getName().getString(),
                                stance,
                                String.format("%.2f", range)
                        );
                    }
                } else {
                    double base, camo;
                    if (stance == PlayerStance.CRAWLING) {
                        base = SoundAttractMod.CONFIG.crawlDetectionRange;
                        camo = SoundAttractMod.CONFIG.crawlDetectionRangeCamouflage;
                    } else if (stance == PlayerStance.SNEAKING) {
                        base = SoundAttractMod.CONFIG.sneakDetectionRange;
                        camo = SoundAttractMod.CONFIG.sneakDetectionRangeCamouflage;
                    } else {
                        base = SoundAttractMod.CONFIG.standingDetectionRange;
                        camo = SoundAttractMod.CONFIG.standingDetectionRangeCamouflage;
                    }

                    CamouflageFactorResult camoResult;
                    if (stance == PlayerStance.CRAWLING) {
                        camoResult = getCrawlingCamouflageFactor(player, level);
                    } else if (stance == PlayerStance.SNEAKING) {
                        camoResult = getSneakingCamouflageFactor(player, level);
                    } else {
                        camoResult = getStandingCamouflageFactor(player, level);
                    }
                    double factor = camoResult.factor;

                    range = (factor <= 0.0)
                            ? base
                            : (factor >= 1.0)
                                    ? camo
                                    : base - (base - camo) * factor;
                }
            }
        }

        int effectiveLight = 0;
        BlockPos feet = player.getBlockPos();
        BlockPos eyes = feet.up();
        long dayTime = level.getTimeOfDay() % 24000L;
        boolean isDay = dayTime < 12000L;

        if (level.isChunkLoaded(feet)) {
            effectiveLight = Math.max(effectiveLight,
                    level.getLightLevel(net.minecraft.world.LightType.BLOCK, feet));
            if (isDay && level.isSkyVisible(feet)) {
                effectiveLight = Math.max(effectiveLight,
                        level.getLightLevel(net.minecraft.world.LightType.SKY, feet));
            }
        }
        if (level.isChunkLoaded(eyes)) {
            effectiveLight = Math.max(effectiveLight,
                    level.getLightLevel(net.minecraft.world.LightType.BLOCK, eyes));
            if (isDay && level.isSkyVisible(eyes)) {
                effectiveLight = Math.max(effectiveLight,
                        level.getLightLevel(net.minecraft.world.LightType.SKY, eyes));
            }
        }
        for (ItemStack s : List.of(player.getMainHandStack(), player.getOffHandStack())) {
            if (s.getItem() instanceof net.minecraft.item.BlockItem bi) {
                BlockState def = bi.getBlock().getDefaultState();
                if (level.isChunkLoaded(feet)) {
                    int emit = def.getLuminance();
                    effectiveLight = Math.max(effectiveLight, emit);
                }
            }
        }

        double neutral = SoundAttractMod.CONFIG.neutralLightLevel;
        double sensitivity = SoundAttractMod.CONFIG.lightLevelSensitivity;
        double lightEffect = (effectiveLight - neutral) * (sensitivity / 15.0);
        double lightFactor = 1.0 + lightEffect;
        lightFactor = Math.max(SoundAttractMod.CONFIG.minLightFactor, lightFactor);
        lightFactor = Math.min(SoundAttractMod.CONFIG.maxLightFactor, lightFactor);
        range *= lightFactor;
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                    "[StealthDetection] Light: effective={}, factor={}, range={}",
                    effectiveLight,
                    String.format("%.2f", lightFactor),
                    String.format("%.2f", range)
            );
        }

        if (SoundAttractMod.CONFIG.enableHeldItemPenalty) {
            int heldCount = 0;
            if (!player.getMainHandStack().isEmpty()) {
                heldCount++;
            }
            if (!player.getOffHandStack().isEmpty()) {
                heldCount++;
            }

            double penalty = SoundAttractMod.CONFIG.heldItemPenaltyFactor;
            for (int i = 0; i < heldCount; i++) {
                range *= penalty;
            }
            if (SoundAttractMod.CONFIG.debugLogging && heldCount > 0) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Held‐item penalty: {} items ×{} → {}",
                        heldCount,
                        String.format("%.2f", penalty),
                        String.format("%.2f", range)
                );
            }
        }

        if (SoundAttractMod.CONFIG.enableEnchantmentPenalty) {
            int enchantedArmorPieces = 0;
            for (ItemStack armor : player.getInventory().armor) {
                if (!armor.isEmpty() && armor.hasEnchantments() && !hasConcealmentEnchant(armor)) {
                    enchantedArmorPieces++;
                }
            }
            double armorFactor = SoundAttractMod.CONFIG.armorEnchantmentPenaltyFactor;
            for (int i = 0; i < enchantedArmorPieces; i++) {
                range *= armorFactor;
            }
            if (SoundAttractMod.CONFIG.debugLogging && enchantedArmorPieces > 0) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Enchanted armor penalty: {} pieces ×{} → {}",
                        enchantedArmorPieces,
                        String.format("%.2f", armorFactor),
                        String.format("%.2f", range)
                );
            }

            int enchantedHeldItems = 0;
            if (!player.getMainHandStack().isEmpty()
                    && player.getMainHandStack().hasEnchantments()
                    && !hasConcealmentEnchant(player.getMainHandStack())) {
                enchantedHeldItems++;
            }
            if (!player.getOffHandStack().isEmpty()
                    && player.getOffHandStack().hasEnchantments()
                    && !hasConcealmentEnchant(player.getOffHandStack())) {
                enchantedHeldItems++;
            }
            double heldEnchantFactor = SoundAttractMod.CONFIG.heldItemEnchantmentPenaltyFactor;
            for (int i = 0; i < enchantedHeldItems; i++) {
                range *= heldEnchantFactor;
            }
            if (SoundAttractMod.CONFIG.debugLogging && enchantedHeldItems > 0) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Enchanted held‐item penalty: {} items ×{} → {}",
                        enchantedHeldItems,
                        String.format("%.2f", heldEnchantFactor),
                        String.format("%.2f", range)
                );
            }
        }

        if (level.isSkyVisible(player.getBlockPos()) && level.isRaining()) {
            double rf = SoundAttractMod.CONFIG.rainStealthFactor;
            range *= rf;
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Raining bonus ×{} → {}",
                        String.format("%.2f", rf),
                        String.format("%.2f", range)
                );
            }
        }
        if (level.isThundering()) {
            double tf = SoundAttractMod.CONFIG.thunderStealthFactor;
            range *= tf;
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                        "[StealthDetection] Thundering bonus ×{} → {}",
                        String.format("%.2f", tf),
                        String.format("%.2f", range)
                );
            }
        }

        boolean isMoving = player.getVelocity().lengthSquared() > SoundAttractMod.CONFIG.movementThreshold;
        if (stance != PlayerStance.SNEAKING && stance != PlayerStance.CRAWLING) {
            if (isMoving) {
                double mPen = SoundAttractMod.CONFIG.movementStealthPenalty;
                range *= mPen;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                            "[StealthDetection] Moving penalty (stance={}): ×{} → {}",
                            stance.name(),
                            String.format("%.2f", mPen),
                            String.format("%.2f", range)
                    );
                }
            } else {
                double sBonus = SoundAttractMod.CONFIG.stationaryStealthBonusFactor;
                range *= sBonus;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                            "[StealthDetection] Stationary bonus (stance={}): ×{} → {}",
                            stance.name(),
                            String.format("%.2f", sBonus),
                            String.format("%.2f", range)
                    );
                }
            }
        }

        return Math.max(0.0, range);
    }

    private static PlayerStance determinePlayerStance(PlayerEntity player) {
        if (player.getPose().name().equalsIgnoreCase("SWIMMING")) {
            return PlayerStance.CRAWLING;
        } else if (player.isSneaking()) {
            return PlayerStance.SNEAKING;
        } else {
            return PlayerStance.STANDING;
        }
    }

    private static boolean hasConcealmentEnchant(ItemStack stack) {

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null) {
            return false;
        }

        for (RegistryEntry<Enchantment> enchantmentEntry : enchantments.getEnchantments()) {
            if (enchantmentEntry.matchesKey(ModEnchantments.CONCEAL)) {
                return true;
            }
        }

        return false;
    }

    private static double getStealthCamouflageFactor(PlayerEntity player, net.minecraft.world.World level, double mobDist) {
        boolean isCrawl = player.getPose().name().equalsIgnoreCase("SWIMMING");
        CamouflageFactorResult camoResult;
        if (isCrawl) {
            camoResult = getCrawlingCamouflageFactor(player, level);
        } else if (player.isSneaking()) {
            camoResult = getSneakingCamouflageFactor(player, level);
        } else {
            camoResult = getStandingCamouflageFactor(player, level);
        }
        double factor = camoResult.factor;

        if (SoundAttractMod.CONFIG.camouflageDistanceScaling) {
            double maxDist = SoundAttractMod.CONFIG.camouflageDistanceMax;
            double minEff = SoundAttractMod.CONFIG.camouflageDistanceMinEffectiveness;
            double scale = Math.max(minEff, Math.min(1.0, mobDist / maxDist));
            factor *= (1.0 - scale);
        }

        if (SoundAttractMod.CONFIG.camouflageMovementPenalty) {
            double penalty = 0.0;
            if (player.isSprinting()) {
                penalty = SoundAttractMod.CONFIG.camouflageSprintingPenalty;
            } else if (isPlayerMoving(player) && !player.isSneaking()) {
                penalty = SoundAttractMod.CONFIG.camouflageWalkingPenalty;
            }
            factor *= (1.0 - penalty);
        }
        return Math.max(0.0, Math.min(1.0, factor));
    }

    private static boolean isPlayerMoving(PlayerEntity player) {
        return player.getVelocity().lengthSquared() > 0.001;
    }

    private static CamouflageFactorResult getAdjacentCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {

        final double[] slotWeights = {0.15, 0.35, 0.25, 0.15};

        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equippedIds = new String[4];
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = player.getInventory().armor.get(slot);
            if (stack.isEmpty()) {
                equippedIds[slot] = null;
            } else {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                equippedIds[slot] = itemId.toString();
            }
        }

        double bestScore = 0.0;
        int threshold = SoundAttractMod.CONFIG.camouflageColorSimilarityThreshold;

        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) {
                continue;
            }
            String[] parts = s.split(";");
            if (parts.length < 5) {
                continue;
            }
            String colorHex = parts[0].toUpperCase();

            double score = 0.0;

            for (int i = 0; i < 4; i++) {
                String targetArmorId = parts[i + 1];
                String playerArmorId = equippedIds[i];
                double w = slotWeights[i];

                if (playerArmorId != null && playerArmorId.equals(targetArmorId)) {
                    score += w;
                } else {
                    ItemStack stack = player.getInventory().armor.get(i);
                    if (!stack.isEmpty()
                            && stack.contains(DataComponentTypes.DYED_COLOR)
                            && ((ArmorItem) stack.getItem()).getMaterial() == ArmorMaterials.LEATHER) {
                        DyedColorComponent colorComponent = stack.get(DataComponentTypes.DYED_COLOR);
                        if (colorComponent != null) {
                            int dyedColor = colorComponent.rgb() & 0xFFFFFF;
                            String dyedHex = String.format("%06X", dyedColor);
                            if (!isColorSimilar(dyedHex, colorHex, threshold)) {
                                score -= w;
                            }
                        }
                    }
                }
            }

            score = Math.max(0.0, Math.min(1.0, score));
            bestScore = Math.max(bestScore, score);
        }
        return new CamouflageFactorResult(bestScore);
    }

    private static CamouflageFactorResult getStandingCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {
        return getAdjacentCamouflageFactor(player, level);
    }

    private static CamouflageFactorResult getSneakingCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {
        return getAdjacentCamouflageFactor(player, level);
    }

    private static CamouflageFactorResult getCrawlingCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {
        String[] equippedIds = new String[4];
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = player.getInventory().armor.get(slot);
            if (stack.isEmpty()) {
                equippedIds[slot] = null;
            } else {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                equippedIds[slot] = itemId.toString();
            }
        }

        double bestScore = 0.0;
        int threshold = SoundAttractMod.CONFIG.camouflageColorSimilarityThreshold;
        final double[] slotWeights = {0.15, 0.35, 0.25, 0.15};

        for (Object entry : SoundAttractMod.CONFIG.camouflageSets) {
            if (!(entry instanceof String s)) {
                continue;
            }
            String[] parts = s.split(";");
            if (parts.length < 5) {
                continue;
            }
            String colorHex = parts[0].toUpperCase();

            double score = 0.0;
            for (int i = 0; i < 4; i++) {
                String targetArmorId = parts[i + 1];
                String playerArmorId = equippedIds[i];
                double w = slotWeights[i];

                if (playerArmorId != null && playerArmorId.equals(targetArmorId)) {
                    score += w;
                } else {
                    ItemStack stack = player.getInventory().armor.get(i);
                    if (!stack.isEmpty()
                            && stack.contains(DataComponentTypes.DYED_COLOR)
                            && ((ArmorItem) stack.getItem()).getMaterial() == ArmorMaterials.LEATHER) {
                        DyedColorComponent colorComponent = stack.get(DataComponentTypes.DYED_COLOR);
                        if (colorComponent != null) {
                            int dyedColor = colorComponent.rgb() & 0xFFFFFF;
                            String dyedHex = String.format("%06X", dyedColor);
                            if (!isColorSimilar(dyedHex, colorHex, threshold)) {
                                score -= w;
                            }
                        }
                    }
                }
            }

            score = Math.max(0.0, Math.min(1.0, score));
            bestScore = Math.max(bestScore, score);
        }

        return new CamouflageFactorResult(bestScore);
    }

    private static boolean isCrawling(PlayerEntity player) {
        return player.getPose().name().equalsIgnoreCase("SWIMMING");
    }

    private static String getCamouflageArmorColorHex(PlayerEntity player) {
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equippedIds = new String[4];
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = player.getInventory().armor.get(slot);
            if (stack.isEmpty()) {
                equippedIds[slot] = null;
            } else {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                equippedIds[slot] = itemId.toString();
            }
        }

        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) {
                continue;
            }
            String[] parts = s.split(";");
            if (parts.length < 5) {
                continue;
            }
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (equippedIds[i] == null || !equippedIds[i].equals(parts[i + 1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (matchesArmor) {
                return parts[0].toUpperCase();
            }
        }

        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.LEATHER) {
                DyedColorComponent colorComponent = stack.get(DataComponentTypes.DYED_COLOR);
                if (colorComponent != null) {
                    int color = colorComponent.rgb();
                    return String.format("%06X", color & 0xFFFFFF);
                }
            }
        }
        return null;
    }

    private static boolean isPlayerCamouflaged(PlayerEntity player, net.minecraft.world.World level, String stance) {
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equippedIds = new String[4];
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = player.getInventory().armor.get(slot);
            if (stack.isEmpty()) {
                equippedIds[slot] = null;
            } else {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                equippedIds[slot] = itemId.toString();
            }
        }
        String armorColor = getCamouflageArmorColorHex(player);
        if (armorColor == null) {
            return false;
        }
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) {
                continue;
            }
            String[] parts = s.split(";");
            if (parts.length < 5) {
                continue;
            }
            if (!armorColor.equalsIgnoreCase(parts[0])) {
                continue;
            }
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (equippedIds[i] == null || !equippedIds[i].equals(parts[i + 1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (!matchesArmor) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean isColorSimilar(String hex1, String hex2, int threshold) {
        try {
            int c1 = Integer.parseInt(hex1, 16);
            int c2 = Integer.parseInt(hex2, 16);
            int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
            int dr = r1 - r2, dg = g1 - g2, db = b1 - b2;
            return (dr * dr + dg * dg + db * db) <= (threshold * threshold);
        } catch (Exception e) {
            return false;
        }
    }

    private static class CamouflageFactorResult {

        public final double factor;

        public CamouflageFactorResult(double factor) {
            this.factor = factor;
        }
    }

}
