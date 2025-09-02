package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraftforge.fml.ModList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.Registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A compatibility wrapper to safely interact with the EnhancedAI mod.
 * This class prevents a hard dependency and avoids crashes if EnhancedAI is not installed.
 */
public class EnhancedAICompat {

    public static final String TAG_DIG_POS = "enhancedai:dig_pos";

    private static final boolean IS_ENHANCED_AI_LOADED = ModList.get().isLoaded("enhancedai");

    private static final int DEFAULT_MAX_Y = 256;
    private static final boolean DEFAULT_BLACKLIST_TILES = true;
    private static final boolean DEFAULT_BLACKLIST_AS_WHITELIST = false;
    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("soundattract", "empty_block_tag"));

    /**
     * Safely gets the maximum Y level for mining from EnhancedAI, or returns a default.
     */
    public static int getMaxY() {
        if (!IS_ENHANCED_AI_LOADED)
            return DEFAULT_MAX_Y;
        return DiggerZombieProxy.getMaxY();
    }

    /**
     * Safely checks if tile entities should be blacklisted from EnhancedAI, or returns a default.
     */
    public static boolean shouldBlacklistTileEntities() {
        if (!IS_ENHANCED_AI_LOADED)
            return DEFAULT_BLACKLIST_TILES;
        return DiggerZombieProxy.blacklistTileEntities();
    }

    /**
     * Safely checks if the blacklist is a whitelist from EnhancedAI, or returns a default.
     */
    public static boolean isBlacklistAsWhitelist() {
        if (!IS_ENHANCED_AI_LOADED)
            return DEFAULT_BLACKLIST_AS_WHITELIST;
        try {
            Class<?> DiggerZombie = Class.forName("insane96mcp.enhancedai.modules.zombie.feature.DiggerZombie");
            Field blockBlacklistField = DiggerZombie.getField("blockBlacklist");
            Object blockBlacklist = blockBlacklistField.get(null);
            Field isWhitelistField = blockBlacklist.getClass().getField("isWhitelist");
            return (boolean) isWhitelistField.get(blockBlacklist);
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("Failed to get isBlacklistAsWhitelist via reflection", e);
            return DEFAULT_BLACKLIST_AS_WHITELIST;
        }
    }

    /**
     * Returns a TagKey used as a blacklist/whitelist for blocks to break, if available.
     * On 1.19.2 EnhancedAI doesn't expose a tag publicly; return an empty tag to indicate 'no-op'.
     */
    public static TagKey<Block> getBlockBlacklistTag() {


        return EMPTY_BLACKLIST;
    }

    /**
     * Returns true if the returned tag is the sentinel empty tag we define.
     * Useful for skipping whitelist logic when no real tag is available.
     */
    public static boolean isEmptyBlacklistTag() {
        return EMPTY_BLACKLIST.location().equals(new ResourceLocation("soundattract", "empty_block_tag"));
    }

    public static Goal getDiggingGoal(PathfinderMob mob) {
        if (!IS_ENHANCED_AI_LOADED)
            return null;
        return DiggerZombieProxy.getDiggingGoal(mob);
    }

    public static void addDiggingGoal(PathfinderMob mob) {
        if (!IS_ENHANCED_AI_LOADED)
            return;
        DiggerZombieProxy.addDiggingGoal(mob);
    }

    public static boolean canStartDigging(LivingEntity entity) {
        if (!IS_ENHANCED_AI_LOADED)
            return false;
        if (!(entity instanceof Mob)) {
            return false;
        }

        return isDigger((Mob) entity) && !isDigging(entity);
    }

    public static boolean isDigger(Mob entity) {
        try {
            Class<?> diggerZombieClass = Class.forName("insane96mcp.enhancedai.modules.zombie.feature.DiggerZombie");
            Method canBeDiggerMethod = diggerZombieClass.getDeclaredMethod("canBeDigger", Mob.class);
            canBeDiggerMethod.setAccessible(true);
            return (boolean) canBeDiggerMethod.invoke(null, entity);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isDigging(LivingEntity entity) {
        if (!IS_ENHANCED_AI_LOADED)
            return false;
        if (entity.getPersistentData().contains(TAG_DIG_POS)) {
            return true;
        }
        return false;
    }

    /**
     * Instruct EnhancedAI's digging goal to dig towards the given position by writing the expected NBT tag.
     * Uses the common BlockPos NBT format (X/Y/Z) via NbtUtils.
     */
    public static void startDiggingTowards(LivingEntity entity, BlockPos pos) {
        if (!IS_ENHANCED_AI_LOADED || entity == null || pos == null)
            return;
        try {
            CompoundTag data = entity.getPersistentData();
            data.put(TAG_DIG_POS, NbtUtils.writeBlockPos(pos));
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("Failed to set EnhancedAI dig position via NBT", e);
        }
    }

    /**
     * Clears any active EnhancedAI digging target from the mob's persistent data.
     */
    public static void clearDigging(LivingEntity entity) {
        if (!IS_ENHANCED_AI_LOADED || entity == null)
            return;
        try {
            entity.getPersistentData().remove(TAG_DIG_POS);
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("Failed to clear EnhancedAI dig position via NBT", e);
        }
    }

    /**
     * An inner class that references EnhancedAI classes directly.
     * This class will ONLY be loaded by the JVM if IS_ENHANCED_AI_LOADED is true,
     * preventing a NoClassDefFoundError.
     */
    private static class DiggerZombieProxy {
        static Goal getDiggingGoal(PathfinderMob mob) {
            for (WrappedGoal prioritizedGoal : mob.goalSelector.getAvailableGoals()) {
                if (prioritizedGoal.getGoal() instanceof insane96mcp.enhancedai.modules.zombie.ai.DiggingGoal) {
                    return prioritizedGoal.getGoal();
                }
            }
            return null;
        }

        static void addDiggingGoal(PathfinderMob mob) {
            if (!(mob instanceof Zombie zombie)) {
                return;
            }

            for (WrappedGoal prioritizedGoal : mob.goalSelector.getAvailableGoals()) {
                if (prioritizedGoal.getGoal() instanceof insane96mcp.enhancedai.modules.zombie.ai.DiggingGoal) {
                    return;
                }
            }
            mob.goalSelector.addGoal(1, new insane96mcp.enhancedai.modules.zombie.ai.DiggingGoal(zombie, 1.0d, true, false));
        }
        static int getMaxY() {
            return insane96mcp.enhancedai.modules.zombie.feature.DiggerZombie.maxYDig;
        }

        static boolean blacklistTileEntities() {
            return insane96mcp.enhancedai.modules.zombie.feature.DiggerZombie.blacklistTileEntities;
        }

    }
}