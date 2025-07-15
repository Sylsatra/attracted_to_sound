package com.example.soundattract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.enchantment.ModEnchantments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class StealthDetectionEvents {

    private static final Map<Mob, Integer> mobOutOfRangeTicks = new HashMap<>();
    private static final Map<Player, net.minecraft.world.phys.Vec3> lastPlayerPositions = new HashMap<>();
    private static long lastStealthCheckTick = -1;
    private static final Map<UUID, GunshotInfo> playerGunshotInfo = new HashMap<>();

    private static int getStealthCheckInterval() {
        return SoundAttractConfig.COMMON.stealthCheckInterval.get();
    }
    
    private static boolean hasConcealmentEnchant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments ==null) {
            return false;
        }
        return enchantments.keySet().stream().anyMatch(holder -> holder.is(ModEnchantments.CONCEAL));
    }

    @SubscribeEvent
    public void onMobAttemptTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();

        if (newTarget instanceof Player playerTarget) {
            if (playerTarget.isCreative() || playerTarget.isSpectator() || !playerTarget.isAlive()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[net.neoforged.neoforge.event.entity.living.LivingSetAttackTargetEvent] Player {} is creative/spectator/dead. Allowing target by {}.",
                            playerTarget.getName().getString(), mob.getName().getString()
                    );
                }
                return;
            }

            if (!canMobDetectPlayer(mob, playerTarget)) {
                event.setCanceled(true);
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[net.neoforged.neoforge.event.entity.living.LivingSetAttackTargetEvent] Mob {} targeting of Player {} CANCELED due to stealth rules.",
                            mob.getName().getString(), playerTarget.getName().getString()
                    );
                }
            } else {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[net.neoforged.neoforge.event.entity.living.LivingSetAttackTargetEvent] Mob {} targeting of Player {} ALLOWED (passes stealth check).",
                            mob.getName().getString(), playerTarget.getName().getString()
                    );
                }
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
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "[StealthQuery] Player {} IS DETECTABLE by mob {} (in range and in FOV).",
                    player.getName().getString(), mob.getName().getString()
            );
        }
        return true;
    }
    public static class GunshotInfo {
        public final long timestamp;
        public final double detectionRange;
        public GunshotInfo(long timestamp, double detectionRange) {
            this.timestamp = timestamp;
            this.detectionRange = detectionRange;
        }
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

    public static boolean shouldSuppressTargeting(Mob mob) {
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
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
    public void onServerTick(ServerTickEvent.Post event) {
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
            int simDistBlocks = level.getServer().getPlayerList().getViewDistance() * 16;
            Set<Mob> mobsToCheck = new HashSet<>();
            for (net.minecraft.server.level.ServerPlayer serverPlayer : level.players()) {
                AABB scanArea = serverPlayer.getBoundingBox().inflate(simDistBlocks);
                mobsToCheck.addAll(level.getEntitiesOfClass(Mob.class, scanArea, entity -> entity.isAlive() && entity.getTarget() instanceof Player));
            }
            for (Mob mob : mobsToCheck) {
                Player playerTarget = (Player) mob.getTarget();
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
        float playerHeight = player.getBbHeight();

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

    private static int colorDifference(int color1, int color2) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
    
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
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
                SoundAttractMod.LOGGER.info("[GRSDR] Player has active gunshot flash. Initial range: {}", String.format("%.2f", baseRange));
            }
            double standingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
            double currentPoseBaseRange;
            switch (currentStance) {
                case CRAWLING: currentPoseBaseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get(); break;
                case SNEAKING: currentPoseBaseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get(); break;
                default: currentPoseBaseRange = standingRange; break;
            }
            double poseReduction = Math.max(0, standingRange - currentPoseBaseRange);
            baseRange -= poseReduction;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[GRSDR] Gunshot range adjusted by pose {}. Reduction: {}. New range: {}.", currentStance, String.format("%.2f", poseReduction), String.format("%.2f", baseRange));
            }
        } else {
            com.example.soundattract.config.MobProfile mobProfile = SoundAttractConfig.getMatchingProfile(mob);
            Optional<Double> override = (mobProfile != null) ? mobProfile.getDetectionOverride(currentStance) : Optional.empty();
            if (override.isPresent()) {
                baseRange = override.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR] Mob profile '{}' provides override for stance {}: {}", mobProfile.getProfileName(), currentStance, baseRange);
                }
            } else {
                switch (currentStance) {
                    case CRAWLING: baseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get(); break;
                    case SNEAKING: baseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get(); break;
                    default: baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get(); break;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[GRSDR] No profile override for stance {}, using default: {}", currentStance, baseRange);
                }
            }
        }

        if (player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            baseRange *= SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
            if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Invisibility applied. Range -> {}", String.format("%.2f", baseRange));
        }

        int effectiveLight = 0;
        BlockPos playerFeetPos = player.blockPosition(); 
        BlockPos playerEyesPos = playerFeetPos.above();
        int skyDarkness = level.getSkyDarken();
        if (level.isLoaded(playerFeetPos)) {
            effectiveLight = level.getRawBrightness(playerFeetPos, skyDarkness);
        }
        if (level.isLoaded(playerEyesPos)) {
            effectiveLight = Math.max(effectiveLight, level.getRawBrightness(playerEyesPos, skyDarkness));
        }
        for (ItemStack s : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (s.getItem() instanceof BlockItem bi) effectiveLight = Math.max(effectiveLight, bi.getBlock().defaultBlockState().getLightEmission());
        }
        double lightFactor = 1.0 + (effectiveLight - SoundAttractConfig.COMMON.neutralLightLevel.get()) * (SoundAttractConfig.COMMON.lightLevelSensitivity.get() / 15.0);
        lightFactor = Math.clamp(lightFactor, SoundAttractConfig.COMMON.minLightFactor.get(), SoundAttractConfig.COMMON.maxLightFactor.get());
        baseRange *= lightFactor;
        if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Light Level: {}, Factor: {}, Range -> {}", effectiveLight, String.format("%.2f", lightFactor), String.format("%.2f", baseRange));
    
        if (SoundAttractConfig.COMMON.enableEnchantmentPenalty.get()) {
            int enchantedArmorCount = 0;
            for (ItemStack armorStack : player.getArmorSlots()) if (!armorStack.isEmpty() && armorStack.isEnchanted() && !hasConcealmentEnchant(armorStack)) enchantedArmorCount++;
            if(enchantedArmorCount > 0) {
                baseRange *= Math.pow(SoundAttractConfig.COMMON.armorEnchantmentPenaltyFactor.get(), enchantedArmorCount);
                if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Enchanted Armor Penalty ({} pcs). Range -> {}", enchantedArmorCount, String.format("%.2f", baseRange));
            }

            int enchantedHeldCount = 0;
            if(!player.getMainHandItem().isEmpty() && player.getMainHandItem().isEnchanted() && !hasConcealmentEnchant(player.getMainHandItem())) enchantedHeldCount++;
            if(!player.getOffhandItem().isEmpty() && player.getOffhandItem().isEnchanted() && !hasConcealmentEnchant(player.getOffhandItem())) enchantedHeldCount++;
            if(enchantedHeldCount > 0) {
                baseRange *= Math.pow(SoundAttractConfig.COMMON.heldItemEnchantmentPenaltyFactor.get(), enchantedHeldCount);
                if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Enchanted Held Item Penalty ({} pcs). Range -> {}", enchantedHeldCount, String.format("%.2f", baseRange));
            }
        }

        if (SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            Optional<Integer> armorColorOpt = getEffectiveArmorColor(player);
            Optional<Integer> envColorOpt = getAverageEnvironmentalColor(player, level);
            if (armorColorOpt.isPresent() && envColorOpt.isPresent()) {
                int diff = colorDifference(armorColorOpt.get(), envColorOpt.get());
                if (diff <= SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get()) {
                    double ratio = 1.0 - ((double) diff / SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get());
                    double effect = SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.get() * ratio;
                    baseRange *= (1.0 - effect);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] EnvCamo BONUS (diff: {}). Range -> {}", diff, String.format("%.2f", baseRange));
                } else if (SoundAttractConfig.COMMON.enableEnvironmentalMismatchPenalty.get() && diff > SoundAttractConfig.COMMON.environmentalMismatchThreshold.get()) {
                    baseRange *= SoundAttractConfig.COMMON.environmentalMismatchPenaltyFactor.get();
                    if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] EnvCamo PENALTY (diff: {}). Range -> {}", diff, String.format("%.2f", baseRange));
                }
            }
        }

        if (level.isRainingAt(player.blockPosition())) {
            baseRange *= SoundAttractConfig.COMMON.rainStealthFactor.get();
            if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Rain applied. Range -> {}", String.format("%.2f", baseRange));
        }
        if (level.isThundering()) {
            baseRange *= SoundAttractConfig.COMMON.thunderStealthFactor.get();
            if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Thunder applied. Range -> {}", String.format("%.2f", baseRange));
        }

        if (currentStance != PlayerStance.SNEAKING && currentStance != PlayerStance.CRAWLING) {
            if (isPlayerMoving(player, SoundAttractConfig.COMMON.movementThreshold.get())) {
                baseRange *= SoundAttractConfig.COMMON.movementStealthPenalty.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Movement penalty applied. Range -> {}", String.format("%.2f", baseRange));
            } else {
                baseRange *= SoundAttractConfig.COMMON.stationaryStealthBonusFactor.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Stationary bonus applied. Range -> {}", String.format("%.2f", baseRange));
            }
        }

        if (SoundAttractConfig.COMMON.enableCamouflage.get()) {
            List<String> camoItems = new ArrayList<>(SoundAttractConfig.COMMON.camouflageArmorItems.get());
            if (!camoItems.isEmpty()) {
                double totalEffect = 0.0;
                int piecesWorn = 0, camoPiecesWorn = 0;
                List<ItemStack> armorList = new ArrayList<>();
                player.getArmorSlots().forEach(armorList::add);
                Collections.reverse(armorList); 
                for(int i = 0; i < armorList.size(); i++){
                    ItemStack armorStack = armorList.get(i);
                    if(!armorStack.isEmpty()){
                        piecesWorn++;
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(armorStack.getItem());
                        if(itemId != null && camoItems.contains(itemId.toString())){
                            camoPiecesWorn++;
                            switch(i){
                                case 0: totalEffect += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                case 1: totalEffect += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                case 2: totalEffect += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                case 3: totalEffect += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                            }
                        }
                    }
                }
                boolean hasFullSet = (piecesWorn == 4 && camoPiecesWorn == 4);
                if(SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()){
                    if(hasFullSet) totalEffect = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                    else totalEffect = 0; 
                } else if(hasFullSet && SoundAttractConfig.COMMON.fullArmorStealthBonus.get() > totalEffect) {
                    totalEffect = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                }
            
                if (totalEffect > 0) {
                    baseRange *= (1.0 - Math.min(totalEffect, 0.99));
                    if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR] Item Camo effect {} applied. Range -> {}", String.format("%.2f", totalEffect), String.format("%.2f", baseRange));
                }
            }
        }
    
        double finalRange = Math.clamp(baseRange, SoundAttractConfig.COMMON.minStealthDetectionRange.get(), SoundAttractConfig.COMMON.maxStealthDetectionRange.get());
        if (SoundAttractConfig.COMMON.debugLogging.get()) SoundAttractMod.LOGGER.info("[GRSDR_End] Final calculated range for {}: {}", player.getName().getString(), String.format("%.2f", finalRange));
        return finalRange;
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

            if (item instanceof ArmorItem armorItem && armorItem.getMaterial().is(ArmorMaterials.LEATHER)) {
                DyedItemColor dyedColor = itemStack.get(DataComponents.DYED_COLOR);
                if (dyedColor != null && dyedColor.showInTooltip()) {
                    colors.add(dyedColor.rgb());
                    colorAdded = true;
                }
            }

            if (onlyDyedLeather && !colorAdded) {
                continue;
            }

            if (!colorAdded) {
                ResourceLocation itemIdRL = BuiltInRegistries.ITEM.getKey(item);
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
                            int mapColor = blockState.getMapColor(level, currentPos).col;
                            if (mapColor != 0) {
                                blockColors.add(mapColor);
                            }
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