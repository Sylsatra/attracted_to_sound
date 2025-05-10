package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StealthDetectionEvents {
    private static final Map<Player, Double> camoCache = new HashMap<>();
    private static final Map<Player, Long> camoCacheTick = new HashMap<>();
private static int getStealthCheckInterval() {
    return SoundAttractConfig.stealthCheckInterval.get();
}
    private static long lastStealthCheckTick = -1;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
            long gameTime = server.getGameTime();
            if (gameTime % getStealthCheckInterval() != 0 || gameTime == lastStealthCheckTick) return;
            lastStealthCheckTick = gameTime;
            for (ServerLevel level : server.getServer().getAllLevels()) {
                for (Player player : level.players()) {
                    BlockPos playerPos = player.blockPosition();
                    int chunkRadius = 1;
                    int chunkX = playerPos.getX() >> 4;
                    int chunkZ = playerPos.getZ() >> 4;
                    for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                            int cx = chunkX + dx, cz = chunkZ + dz;
                            int minX = (cx << 4), minZ = (cz << 4);
                            int maxX = minX + 15, maxZ = minZ + 15;
                            for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(minX, 0, minZ, maxX, 256, maxZ))) {
                                if (mob.getTarget() instanceof Player targetPlayer && targetPlayer == player) {
                                    double detectionRange = getRealisticStealthDetectionRange(player, mob, level);
                                    double dist = mob.distanceTo(player);
                                    if (dist > detectionRange) {
                                        mob.setTarget(null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMobTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        LivingEntity target = event.getNewTarget();
        if (!(target instanceof Player player)) return;
        if (mob.getTarget() != null && !mob.getTarget().isAlive()) return;

        double detectionRange = getStealthDetectionRange(player, mob.level());
        double dist = mob.distanceTo(player);
        if (dist > detectionRange) {
            event.setCanceled(true); 
        }
    }

    public static double getRealisticStealthDetectionRange(Player player, Mob mob, Level level) {
        boolean isNight = !level.dimensionType().hasSkyLight() || level.getDayTime() % 24000L >= 13000L;
        double nightMultiplier = isNight ? SoundAttractConfig.detectionNightMultiplier.get() : 1.0;
        int light = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
        double lightFactor = 1.0;
        if (light <= SoundAttractConfig.detectionLightLowThreshold.get()) {
            lightFactor = SoundAttractConfig.detectionLightLowMultiplier.get();
        } else if (light <= SoundAttractConfig.detectionLightMidThreshold.get()) {
            lightFactor = SoundAttractConfig.detectionLightMidMultiplier.get();
        }
        boolean isSneak = player.isCrouching();
        boolean isCrawl = isCrawling(player);
        double stanceBase, stanceCamo;
        CamouflageFactorResult camoResult;
        if (isCrawl) {
            stanceBase = SoundAttractConfig.crawlDetectionRange.get();
            stanceCamo = SoundAttractConfig.crawlDetectionRangeCamouflage.get();
            camoResult = getCrawlingCamouflageFactor(player, level);
        } else if (isSneak) {
            stanceBase = SoundAttractConfig.sneakDetectionRange.get();
            stanceCamo = SoundAttractConfig.sneakDetectionRangeCamouflage.get();
            camoResult = getSneakingCamouflageFactor(player, level);
        } else {
            stanceBase = SoundAttractConfig.standingDetectionRange.get();
            stanceCamo = SoundAttractConfig.standingDetectionRangeCamouflage.get();
            camoResult = getStandingCamouflageFactor(player, level);
        }
        double dist = mob.distanceTo(player);
        double distFactor = 1.0;
        if (SoundAttractConfig.camouflageDistanceScaling.get()) {
            double maxDist = SoundAttractConfig.camouflageDistanceMax.get();
            double minEff = SoundAttractConfig.camouflageDistanceMinEffectiveness.get();
            distFactor = Math.max(minEff, 1.0 - dist / maxDist);
        }
     
        double movePenalty = 1.0;
        if (SoundAttractConfig.camouflageMovementPenalty.get()) {
            if (isPlayerMoving(player)) {
                movePenalty = player.isSprinting() ? SoundAttractConfig.camouflageSprintingPenalty.get() : SoundAttractConfig.camouflageWalkingPenalty.get();
            }
        }
       
        double camoFactor = camoResult.factor * distFactor * movePenalty;
        camoFactor = Math.max(0, Math.min(1, camoFactor));
        double range = stanceBase - (stanceBase - stanceCamo) * camoFactor;
        range *= nightMultiplier;
        range *= lightFactor;
        return Math.max(range, 2.0);
    }

    public static double getStealthDetectionRange(Player player, Level level) {
        boolean isSneak = player.isCrouching();
        boolean isCrawl = isCrawling(player);
        if (isCrawl) {
            CamouflageFactorResult camoResult = getCrawlingCamouflageFactor(player, level);
            double base = SoundAttractConfig.crawlDetectionRange.get();
            double camo = SoundAttractConfig.crawlDetectionRangeCamouflage.get();
            if (camoResult.factor <= 0) return base;
            if (camoResult.factor >= 1) return camo;
            return base - (base - camo) * camoResult.factor;
        } else if (isSneak) {
            CamouflageFactorResult camoResult = getSneakingCamouflageFactor(player, level);
            double base = SoundAttractConfig.sneakDetectionRange.get();
            double camo = SoundAttractConfig.sneakDetectionRangeCamouflage.get();
            if (camoResult.factor <= 0) return base;
            if (camoResult.factor >= 1) return camo;
            return base - (base - camo) * camoResult.factor;
        } else {
            CamouflageFactorResult camoResult = getStandingCamouflageFactor(player, level);
            double base = SoundAttractConfig.standingDetectionRange.get();
            double camo = SoundAttractConfig.standingDetectionRangeCamouflage.get();
            if (camoResult.factor <= 0) return base;
            if (camoResult.factor >= 1) return camo;
            return base - (base - camo) * camoResult.factor;
        }
    }

    private static CamouflageFactorResult getStealthCamouflageFactor(Player player, Level level, int totalBlocks) {
        long tick = level.getGameTime();
        if (camoCache.containsKey(player) && camoCacheTick.get(player) != null && camoCacheTick.get(player) == tick) {
            return new CamouflageFactorResult(camoCache.get(player));
        }
        double factor = getAdjacentCamouflageFactor(player, level, totalBlocks).factor;
        if (isPlayerMoving(player)) factor *= 0.7; 
        camoCache.put(player, factor);
        camoCacheTick.put(player, tick);
        return new CamouflageFactorResult(factor);
    }

    public static class CamouflageFactorResult {
        public final double factor;
        public CamouflageFactorResult(double factor) { this.factor = factor; }
    }

    private static CamouflageFactorResult getStandingCamouflageFactor(Player player, Level level) {
        return getStealthCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getSneakingCamouflageFactor(Player player, Level level) {
        return getStealthCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getCrawlingCamouflageFactor(Player player, Level level) {
        return getStealthCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getAdjacentCamouflageFactor(Player player, Level level, int totalBlocks) {
        List<?> camoSets = SoundAttractConfig.camouflageSets.get();
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) return new CamouflageFactorResult(0);
            ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
            equipped[idx++] = itemId.toString();
        }
        String armorColor = getCamouflageArmorColorHex(player);
        if (armorColor == null) return new CamouflageFactorResult(0);
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            String colorHex = parts[0];
            if (!armorColor.equalsIgnoreCase(colorHex)) continue;
            boolean matchesArmor = true;
            int matchCount = 0;
            for (int i = 0; i < 4; i++) {
                if (equipped[i].equals(parts[i+1])) {
                    matchCount++;
                } else {
                    matchesArmor = false;
                }
            }
            if (!matchesArmor) continue;
            double armorMatchFactor = (double) matchCount / 4.0;
            if (SoundAttractConfig.camouflagePartialMatching.get()) {
                armorMatchFactor = Math.max(0.0, Math.min(1.0, armorMatchFactor));
            } else {
                if (armorMatchFactor < 1.0) continue;
            }
            if (parts.length > 5) {
                BlockPos pos = player.blockPosition();
                BlockPos[] adjacent = new BlockPos[] {
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
                };
                int blockMatchCount = 0;
                for (BlockPos adj : adjacent) {
                    BlockState blockState = level.getBlockState(adj);
                    ResourceLocation blockId = blockState.getBlock().builtInRegistryHolder().key().location();
                    String blockIdStr = blockId.toString();
                    for (int i = 5; i < parts.length; i++) {
                        if (blockIdStr.equals(parts[i])) {
                            blockMatchCount++;
                            break;
                        }
                    }
                }
                double blockFactor = Math.pow(blockMatchCount / (double) totalBlocks, 2);
                return new CamouflageFactorResult(armorMatchFactor * blockFactor);
            } else {
                return new CamouflageFactorResult(armorMatchFactor);
            }
        }
        return new CamouflageFactorResult(0);
    }

    private static boolean isPlayerMoving(Player player) {
        return player.getDeltaMovement().lengthSqr() > 0.0025; 
    }

    private static boolean isCrawling(Player player) {
        return player.getPose().name().equalsIgnoreCase("SWIMMING") ||
               player.getTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains("crawl"));
}

    private static String getCamouflageArmorColorHex(Player player) {
        List<?> camoSets = SoundAttractConfig.camouflageSets.get();
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
            equipped[idx++] = itemId.toString();
        }
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            boolean matchesArmor = true;
            int matchCount = 0;
            for (int i = 0; i < 4; i++) {
                if (equipped[i].equals(parts[i+1])) {
                    matchCount++;
                } else {
                    matchesArmor = false;
                }
            }
            if (matchesArmor) {
                return parts[0].toUpperCase();
            }
        }
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == net.minecraft.world.item.ArmorMaterials.LEATHER) {
                if (stack.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem dyeable) {
                    int color = dyeable.getColor(stack);
                    return String.format("%06X", color & 0xFFFFFF);
                }
            }
        }
        return null;
    }

    private static boolean isPlayerCamouflaged(Player player, Level level, String stance) {
        List<?> camoSets = SoundAttractConfig.camouflageSets.get();
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) return false;
            ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
            equipped[idx++] = itemId.toString();
        }
        String armorColor = getCamouflageArmorColorHex(player);
        if (armorColor == null) return false;
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            String colorHex = parts[0];
            if (!armorColor.equalsIgnoreCase(colorHex)) continue;
            int matchCount = 0;
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (equipped[i].equals(parts[i+1])) {
                    matchCount++;
                } else {
                    matchesArmor = false;
                }
            }
            if (!matchesArmor) continue;
            double armorMatchFactor = (double) matchCount / 4.0;
            if (SoundAttractConfig.camouflagePartialMatching.get()) {
                armorMatchFactor = Math.max(0.0, Math.min(1.0, armorMatchFactor));
            } else {
                if (armorMatchFactor < 1.0) continue;
            }
            if (parts.length > 5) {
                BlockPos pos = player.blockPosition();
                BlockPos[] adjacent = new BlockPos[] {
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
                };
                int blockMatchCount = 0;
                for (BlockPos adj : adjacent) {
                    BlockState blockState = level.getBlockState(adj);
                    ResourceLocation blockId = blockState.getBlock().builtInRegistryHolder().key().location();
                    String blockIdStr = blockId.toString();
                    for (int i = 5; i < parts.length; i++) {
                        if (blockIdStr.equals(parts[i])) {
                            blockMatchCount++;
                            break;
                        }
                    }
                }
                return (blockMatchCount >= 2) && (armorMatchFactor > 0.0);
            } else {
                return armorMatchFactor > 0.0;
            }
        }
        return false;
    }

}
