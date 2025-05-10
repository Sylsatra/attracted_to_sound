package com.example.soundattract;

import net.minecraft.util.math.Box;


import com.example.soundattract.SoundAttractMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ArmorItem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.Locale;

public class StealthDetectionEvents {

    private static final java.util.Map<java.util.UUID, Double> camoCache = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> camoCacheTick = new java.util.HashMap<>();
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            int checkInterval = SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.stealthCheckInterval : 10;
            long tick = server.getOverworld().getTime();
            if (tick % checkInterval != 0) return;
            for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
                boolean isNight = world.getTimeOfDay() > 13000 && world.getTimeOfDay() < 23000;
                java.util.Set<MobEntity> mobs = new java.util.HashSet<>();
                for (PlayerEntity player : world.getPlayers()) {
                    BlockPos playerPos = player.getBlockPos();
                    int playerChunkX = playerPos.getX() >> 4;
                    int playerChunkZ = playerPos.getZ() >> 4;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int chunkX = playerChunkX + dx;
                            int chunkZ = playerChunkZ + dz;
                            net.minecraft.server.world.ServerChunkManager chunkManager = world.getChunkManager();
                            net.minecraft.server.world.ServerWorld worldRef = world;
                            net.minecraft.world.chunk.Chunk chunk = chunkManager.getWorldChunk(chunkX, chunkZ);
                            if (chunk == null) continue;
                            Box chunkBox = new Box(
                                chunk.getPos().getStartX(), 0, chunk.getPos().getStartZ(),
                                chunk.getPos().getEndX(), world.getHeight(), chunk.getPos().getEndZ()
                            );
                            List<MobEntity> mobsInChunk = world.getEntitiesByClass(MobEntity.class, chunkBox, mob -> true);
                            mobs.addAll(mobsInChunk);
                        }
                    }
                }
                if (mobs.contains(null)) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.warn("[StealthDetectionEvents] getEntitiesByClass returned a null mob entity");
                    }
                }
                for (MobEntity mob : mobs) {
                    try {
                        if (mob == null) {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.warn("[StealthDetectionEvents] mob was null in tick loop");
                            }
                            continue;
                        }
                        LivingEntity target = mob.getTarget();
                        if (target == null) {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.warn("[StealthDetectionEvents] mob.getTarget() was null for mob {}", mob.getName().getString());
                            }
                            continue;
                        }
                        if (target instanceof PlayerEntity player && target.isAlive()) {
                            double dist = mob.distanceTo(player);
                            double detectionRange = getRealisticStealthDetectionRange(player, world, isNight, dist);
if (!mob.canSee(player)) {
    detectionRange *= 0.5;
}
BlockPos pos = player.getBlockPos();
int light = world.getLightLevel(pos);
if (light < SoundAttractMod.CONFIG.detectionLightLowThreshold) detectionRange *= SoundAttractMod.CONFIG.detectionLightLowMultiplier;
else if (light < SoundAttractMod.CONFIG.detectionLightMidThreshold) detectionRange *= SoundAttractMod.CONFIG.detectionLightMidMultiplier;
if (isNight) detectionRange *= SoundAttractMod.CONFIG.detectionNightMultiplier;
if (player.isSprinting()) detectionRange *= 1.2;
else if (player.isSneaking()) detectionRange *= 0.9;

if (dist > detectionRange) {
    mob.setTarget(null);
}
                        }
                    } catch (Exception ex) {
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.error("[StealthDetectionEvents] Exception in mob tick: ", ex);
                        }
                    }
                }
            }
        });
    }

    public static double getRealisticStealthDetectionRange(PlayerEntity player, net.minecraft.world.World level, boolean isNight, double mobDist) {
        long tick = level.getTime();
        double camoFactor;
        if (camoCache.containsKey(player.getUuid()) && camoCacheTick.get(player.getUuid()) != null && tick - camoCacheTick.get(player.getUuid()) < 10) {
            camoFactor = camoCache.get(player.getUuid());
        } else {
            camoFactor = getStealthCamouflageFactor(player, level, mobDist);
            camoCache.put(player.getUuid(), camoFactor);
            camoCacheTick.put(player.getUuid(), (int)tick);
        }
        boolean isSneak = player.isSneaking();
        boolean isCrawl = isCrawling(player);
        double base, camo;
        if (isCrawl) {
            base = SoundAttractMod.CONFIG.crawlDetectionRange;
            camo = SoundAttractMod.CONFIG.crawlDetectionRangeCamouflage;
        } else if (isSneak) {
            base = SoundAttractMod.CONFIG.sneakDetectionRange;
            camo = SoundAttractMod.CONFIG.sneakDetectionRangeCamouflage;
        } else {
            base = SoundAttractMod.CONFIG.standingDetectionRange;
            camo = SoundAttractMod.CONFIG.standingDetectionRangeCamouflage;
        }
        double range = camoFactor <= 0 ? base : camoFactor >= 1 ? camo : base - (base - camo) * camoFactor;
        return range;
    }

    private static double getStealthCamouflageFactor(PlayerEntity player, net.minecraft.world.World level, double mobDist) {
        boolean isCrawl = isCrawling(player);
        CamouflageFactorResult camoResult;
        if (isCrawl) camoResult = getCrawlingCamouflageFactor(player, level);
        else if (player.isSneaking()) camoResult = getSneakingCamouflageFactor(player, level);
        else camoResult = getStandingCamouflageFactor(player, level);
        double factor = camoResult.factor;

        if (SoundAttractMod.CONFIG.camouflageDistanceScaling) {
            double maxDist = SoundAttractMod.CONFIG.camouflageDistanceMax;
            double minEff = SoundAttractMod.CONFIG.camouflageDistanceMinEffectiveness;
            double scale = Math.max(minEff, Math.min(1.0, mobDist / maxDist));
            factor *= scale;
        }

        if (SoundAttractMod.CONFIG.camouflageMovementPenalty) {
            double penalty = 0.0;
            if (player.isSprinting()) penalty = SoundAttractMod.CONFIG.camouflageSprintingPenalty;
            else if (isPlayerMoving(player) && !player.isSneaking()) penalty = SoundAttractMod.CONFIG.camouflageWalkingPenalty;
            factor *= (1.0 - penalty);
        }
        return Math.max(0.0, Math.min(1.0, factor));
    }

    private static boolean isPlayerMoving(PlayerEntity player) {
        return player.getVelocity().lengthSquared() > 0.001;
    }
    private static CamouflageFactorResult getAdjacentCamouflageFactor(PlayerEntity player, net.minecraft.world.World level, int totalBlocks) {
        if (!SoundAttractMod.CONFIG.camouflagePartialMatching) {
            return legacyGetAdjacentCamouflageFactor(player, level, totalBlocks);
        }
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equipped = new String[4];
        int armorCount = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                equipped[armorCount++] = itemId.toString();
            }
        }
        String armorColor = getCamouflageArmorColorHex(player);
        double bestScore = 0.0;
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            String colorHex = parts[0];
            int matchArmor = 0;
            for (int i = 0; i < 4; i++) {
                if (equipped[i] != null && equipped[i].equals(parts[i+1])) matchArmor++;
            }
            double armorScore = matchArmor * SoundAttractMod.CONFIG.camouflageArmorPieceWeight;
            double colorScore = 0.0;
            if (armorColor != null) {
                if (armorColor.equalsIgnoreCase(colorHex)) colorScore = SoundAttractMod.CONFIG.camouflageColorSimilarityWeight;
                else if (isColorSimilar(armorColor, colorHex, SoundAttractMod.CONFIG.camouflageColorSimilarityThreshold)) colorScore = SoundAttractMod.CONFIG.camouflageColorSimilarityWeight * 0.5;
            }
            int blockMatch = 0;
            BlockPos pos = player.getBlockPos();
            BlockPos[] adjacent = new BlockPos[] {
                pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down()
            };
            for (BlockPos adj : adjacent) {
                BlockState blockState = level.getBlockState(adj);
                Identifier blockId = Registries.BLOCK.getId(blockState.getBlock());
                String blockIdStr = blockId.toString();
                for (int i = 5; i < parts.length; i++) {
                    if (blockIdStr.equals(parts[i])) {
                        blockMatch++;
                        break;
                    }
                }
            }
            double blockScore = blockMatch * SoundAttractMod.CONFIG.camouflageBlockMatchWeight;
            double totalScore = armorScore + colorScore + blockScore;
            bestScore = Math.max(bestScore, Math.min(1.0, totalScore));
        }
        return new CamouflageFactorResult(bestScore);
    }

    private static CamouflageFactorResult legacyGetAdjacentCamouflageFactor(PlayerEntity player, net.minecraft.world.World level, int totalBlocks) {
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) return new CamouflageFactorResult(0);
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
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
            for (int i = 0; i < 4; i++) {
                if (!equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (!matchesArmor) continue;
            if (parts.length > 5) {
                BlockPos pos = player.getBlockPos();
                BlockPos[] adjacent = new BlockPos[] {
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down()
                };
                int matchCount = 0;
                for (BlockPos adj : adjacent) {
                    BlockState blockState = level.getBlockState(adj);
                    Identifier blockId = Registries.BLOCK.getId(blockState.getBlock());
                    String blockIdStr = blockId.toString();
                    for (int i = 5; i < parts.length; i++) {
                        if (blockIdStr.equals(parts[i])) {
                            matchCount++;
                            break;
                        }
                    }
                }
                double factor = Math.pow(matchCount / (double) totalBlocks, 2);
                return new CamouflageFactorResult(factor);
            } else {
                return new CamouflageFactorResult(1.0);
            }
        }
        return new CamouflageFactorResult(0);
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

    public static double getStealthDetectionRange(PlayerEntity player, net.minecraft.world.World level) {
        boolean isSneak = player.isSneaking();
        boolean isCrawl = isCrawling(player);
        if (isCrawl) {
            CamouflageFactorResult camoResult = getCrawlingCamouflageFactor(player, level);
            double base = SoundAttractMod.CONFIG.crawlDetectionRange;
            double camo = SoundAttractMod.CONFIG.crawlDetectionRangeCamouflage;
            if (camoResult.factor <= 0) return base;
            if (camoResult.factor >= 1) return camo;
            return base - (base - camo) * camoResult.factor;
        } else if (isSneak) {
            CamouflageFactorResult camoResult = getSneakingCamouflageFactor(player, level);
            double base = SoundAttractMod.CONFIG.sneakDetectionRange;
            double camo = SoundAttractMod.CONFIG.sneakDetectionRangeCamouflage;
            if (camoResult.factor <= 0) return base;
            if (camoResult.factor >= 1) return camo;
            return base - (base - camo) * camoResult.factor;
        } else {
            CamouflageFactorResult camoResult = getStandingCamouflageFactor(player, level);
            double base = SoundAttractMod.CONFIG.standingDetectionRange;
            double camo = SoundAttractMod.CONFIG.standingDetectionRangeCamouflage;
            if (camoResult.factor <= 0) return base;
            if (camoResult.factor >= 1) return camo;
            return base - (base - camo) * camoResult.factor;
        }
    }

    private static class CamouflageFactorResult {
        public final double factor;
        public CamouflageFactorResult(double factor) { this.factor = factor; }
    }

    private static CamouflageFactorResult getStandingCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {
        return getAdjacentCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getSneakingCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {
        return getAdjacentCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getCrawlingCamouflageFactor(PlayerEntity player, net.minecraft.world.World level) {
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) return new CamouflageFactorResult(0);
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            equipped[idx++] = itemId.toString();
        }
        String armorColor = getCamouflageArmorColorHex(player);
        if (armorColor == null) {
            return new CamouflageFactorResult(0);
        }
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            String colorHex = parts[0];
            if (!armorColor.equalsIgnoreCase(colorHex)) continue;
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (!equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (!matchesArmor) continue;
            if (parts.length > 5) {
                BlockPos below = player.getBlockPos().down();
                BlockState blockState = level.getBlockState(below);
                Identifier blockId = Registries.BLOCK.getId(blockState.getBlock());
                String blockIdStr = blockId.toString();
                for (int i = 5; i < parts.length; i++) {
                    if (blockIdStr.equals(parts[i])) {
                        return new CamouflageFactorResult(1.0);
                    }
                }
                return new CamouflageFactorResult(0);
            } else {
                return new CamouflageFactorResult(1.0);
            }
        }
        return new CamouflageFactorResult(0);
    }


    private static boolean isCrawling(PlayerEntity player) {
        return player.getPose().name().equalsIgnoreCase("SWIMMING");
    }

    private static String getCamouflageArmorColorHex(PlayerEntity player) {
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) continue;
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            equipped[idx++] = itemId.toString();
        }
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (!equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (matchesArmor) {
                return parts[0].toUpperCase();
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.LEATHER) {
                if (stack.getItem() instanceof DyeableArmorItem dyeable) {
                    int color = dyeable.getColor(stack);
                    return String.format("%06X", color & 0xFFFFFF);
                }
            }
        }
        return null;
    }

    private static boolean isPlayerCamouflaged(PlayerEntity player, net.minecraft.world.World level, String stance) {
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) return false;
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
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
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (!equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (!matchesArmor) continue;
            if (parts.length > 5) {
                BlockPos pos = player.getBlockPos();
                BlockPos[] adjacent = new BlockPos[] {
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down()
                };
                int matchCount = 0;
                for (BlockPos adj : adjacent) {
                    BlockState blockState = level.getBlockState(adj);
                    Identifier blockId = Registries.BLOCK.getId(blockState.getBlock());
                    String blockIdStr = blockId.toString();
                    for (int i = 5; i < parts.length; i++) {
                        if (blockIdStr.equals(parts[i])) {
                            matchCount++;
                            break;
                        }
                    }
                }
                return matchCount >= 2;
            } else {
                return true;
            }
        }
        return false;
    }
}
