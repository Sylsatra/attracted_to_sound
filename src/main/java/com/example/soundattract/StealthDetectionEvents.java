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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StealthDetectionEvents {

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

    private static class CamouflageFactorResult {
        public final double factor;
        public CamouflageFactorResult(double factor) { this.factor = factor; }
    }

    private static CamouflageFactorResult getStandingCamouflageFactor(Player player, Level level) {
        return getAdjacentCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getSneakingCamouflageFactor(Player player, Level level) {
        return getAdjacentCamouflageFactor(player, level, 6);
    }

    private static CamouflageFactorResult getCrawlingCamouflageFactor(Player player, Level level) {
        List<?> camoSets = SoundAttractConfig.camouflageSets.get();
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) return new CamouflageFactorResult(0);
            ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
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
                BlockPos below = player.blockPosition().below();
                BlockState blockState = level.getBlockState(below);
                ResourceLocation blockId = blockState.getBlock().builtInRegistryHolder().key().location();
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
            for (int i = 0; i < 4; i++) {
                if (!equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (!matchesArmor) continue;
            if (parts.length > 5) {
                BlockPos pos = player.blockPosition();
                BlockPos[] adjacent = new BlockPos[] {
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
                };
                int matchCount = 0;
                for (BlockPos adj : adjacent) {
                    BlockState blockState = level.getBlockState(adj);
                    ResourceLocation blockId = blockState.getBlock().builtInRegistryHolder().key().location();
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
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (!equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (!matchesArmor) continue;
            if (parts.length > 5) {
                BlockPos pos = player.blockPosition();
                BlockPos[] adjacent = new BlockPos[] {
                    pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
                };
                int matchCount = 0;
                for (BlockPos adj : adjacent) {
                    BlockState blockState = level.getBlockState(adj);
                    ResourceLocation blockId = blockState.getBlock().builtInRegistryHolder().key().location();
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
