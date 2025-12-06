package com.example.soundattract.integration;

import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A compatibility wrapper to safely interact with the EnhancedAI mod.
 * This class prevents a hard dependency and avoids crashes if EnhancedAI is not installed.
 */
public class EnhancedAICompat {

    private static final boolean IS_ENHANCED_AI_LOADED = ModList.get().isLoaded("enhancedai");

    private static final int DEFAULT_MAX_Y = 256;
    private static final boolean DEFAULT_BLACKLIST_TILES = true;
    private static final boolean DEFAULT_BLACKLIST_AS_WHITELIST = false;

    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(Registries.BLOCK, new ResourceLocation("soundattract", "empty_block_tag"));

    public static int getMaxY() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.getMaxY();
        }
        return DEFAULT_MAX_Y;
    }

    public static boolean shouldBlacklistTileEntities() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.blacklistTileEntities();
        }
        return DEFAULT_BLACKLIST_TILES;
    }

    public static boolean isBlacklistAsWhitelist() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.isBlacklistAsWhitelist();
        }
        return DEFAULT_BLACKLIST_AS_WHITELIST;
    }

    public static TagKey<Block> getBlockBlacklistTag() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.getBlockBlacklistTag();
        }
        return EMPTY_BLACKLIST;
    }

    public static double getTeleportToTargetChance(Level level) {
        if (IS_ENHANCED_AI_LOADED) {
            return TeleportProxy.getChance(level);
        }
        return SoundAttractConfig.COMMON.teleportChance.get();
    }

    public static int getTeleportCooldownTicks() {
        if (IS_ENHANCED_AI_LOADED) {
            return TeleportProxy.getCooldown();
        }
        return SoundAttractConfig.COMMON.teleportCooldownTicks.get();
    }

    public static double getPickUpAndThrowChance(Level level) {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getChance(level);
        }
        return SoundAttractConfig.COMMON.pickUpChance.get();
    }

    public static int getPickUpMinDistanceToPickUp() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getMinDistanceToPickUp();
        }
        return SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.get();
    }

    public static int getPickUpMaxDistanceToThrow() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getMaxDistanceToThrow();
        }
        return SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.get();
    }

    public static double getPickUpSpeedModifier() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getSpeedModifier();
        }
        return SoundAttractConfig.COMMON.pickUpSpeedModifier.get();
    }

    public static int getPickUpCooldownTicks() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getCooldown();
        }
        return SoundAttractConfig.COMMON.pickUpCooldownTicks.get();
    }

    public static boolean isEnhancedAiLoaded() {
        return IS_ENHANCED_AI_LOADED;
    }

    public static double getXrayAttributeValue(Mob mob) {
        if (!IS_ENHANCED_AI_LOADED || mob == null) return 0d;
        try {
            return XrayProxy.getXrayFollowRange(mob);
        } catch (Throwable t) {
            return 0d;
        }
    }

    /**
     * Inner proxies that reference EnhancedAI classes directly. These are only loaded when EnhancedAI is present.
     */
    private static class MinerMobsProxy {
        static int getMaxY() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.maxY; }
        static boolean blacklistTileEntities() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.blacklistTileEntities; }
        static boolean isBlacklistAsWhitelist() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.blockBlacklistAsWhitelist; }
        static TagKey<Block> getBlockBlacklistTag() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.BLOCK_BLACKLIST; }
    }

    private static class TeleportProxy {
        static double getChance(Level level) { return insane96mcp.enhancedai.modules.mobs.teleporttotarget.TeleportToTarget.chance.getByDifficulty(level); }
        static int getCooldown() { return insane96mcp.enhancedai.modules.mobs.teleporttotarget.TeleportToTarget.cooldown; }
    }

    private static class PickUpProxy {
        static double getChance(Level level) { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.chance.getByDifficulty(level); }
        static int getMinDistanceToPickUp() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.minDistanceToPickUp; }
        static int getMaxDistanceToThrow() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.maxDistanceToThrow; }
        static double getSpeedModifier() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.speedModifierToPickUp; }
        static int getCooldown() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.cooldown; }
    }

    private static class XrayProxy {
        static double getXrayFollowRange(Mob mob) {
            AttributeInstance inst = mob.getAttribute(insane96mcp.enhancedai.setup.EAIAttributes.XRAY_FOLLOW_RANGE.get());

            if (inst == null) {
                try {
                    Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("enhancedai", "xray_follow_range"));
                    if (attr == null) {
                        attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("enhancedai", "generic.xray_follow_range"));
                    }
                    if (attr != null) {
                        inst = mob.getAttribute(attr);
                    }
                } catch (Throwable ignored) {}
            }

            if (inst == null) return 0d;
            double v = inst.getBaseValue();
            if (v <= 0d) v = inst.getValue();
            return Math.max(0d, v);
        }
    }
}