package com.example.soundattract.integration;

import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

/**
 * A compatibility wrapper to safely interact with the EnhancedAI mod.
 * This class prevents a hard dependency and avoids crashes if EnhancedAI is not installed.
 */
public final class EnhancedAICompat {

    private EnhancedAICompat() {}

    private static final boolean IS_ENHANCED_AI_LOADED = ModList.get().isLoaded("enhancedai");

    private static final int DEFAULT_MAX_Y = 256;
    private static final boolean DEFAULT_BLACKLIST_TILES = true;
    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(Registries.BLOCK, Identifier.parse("soundattract:empty_block_tag"));

    // Miner config proxies (existing block-breaker behaviour)
    public static int getMaxY() {
        return SoundAttractConfig.COMMON.blockBreakMaxY.get();
    }

    public static boolean shouldBlacklistTileEntities() {
        return SoundAttractConfig.COMMON.blockBreakBlacklistTileEntities.get();
    }

    public static boolean isBlacklistAsWhitelist() {
        return SoundAttractConfig.COMMON.blockBreakListAsWhitelist.get();
    }

    public static TagKey<Block> getBlockBlacklistTag() {
        return EMPTY_BLACKLIST;
    }

    // TeleportToTarget (used for teleport-to-sound)
    public static double getTeleportToTargetChance(Level level) {
        return SoundAttractConfig.COMMON.teleportChance.get();
    }

    public static int getTeleportCooldownTicks() {
        return SoundAttractConfig.COMMON.teleportCooldownTicks.get();
    }

    // PickUpAndThrow values
    public static double getPickUpAndThrowChance(Level level) {
        return SoundAttractConfig.COMMON.pickUpChance.get();
    }

    public static int getPickUpMinDistanceToPickUp() {
        return SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.get();
    }

    public static int getPickUpMaxDistanceToThrow() {
        return SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.get();
    }

    public static double getPickUpSpeedModifier() {
        return SoundAttractConfig.COMMON.pickUpSpeedModifier.get();
    }

    public static int getPickUpCooldownTicks() {
        return SoundAttractConfig.COMMON.pickUpCooldownTicks.get();
    }

    public static boolean isEnhancedAiLoaded() {
        return IS_ENHANCED_AI_LOADED;
    }

    // XRAY attribute value (Enhanced AI Targeting)
    public static double getXrayAttributeValue(Mob mob) {
        return 0d;
    }
}
