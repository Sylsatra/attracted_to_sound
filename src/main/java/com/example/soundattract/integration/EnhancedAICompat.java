package com.example.soundattract.integration;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

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

    /**
     * Safely gets the maximum Y level for mining from EnhancedAI, or returns a default.
     */
    public static int getMaxY() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.getMaxY();
        }
        return DEFAULT_MAX_Y;
    }

    /**
     * Safely checks if tile entities should be blacklisted from EnhancedAI, or returns a default.
     */
    public static boolean shouldBlacklistTileEntities() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.blacklistTileEntities();
        }
        return DEFAULT_BLACKLIST_TILES;
    }
    
    /**
     * Safely checks if the blacklist is a whitelist from EnhancedAI, or returns a default.
     */
    public static boolean isBlacklistAsWhitelist() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.isBlacklistAsWhitelist();
        }
        return DEFAULT_BLACKLIST_AS_WHITELIST;
    }
    
    /**
     * Safely gets the block blacklist tag from EnhancedAI, or returns an empty tag.
     */
    public static TagKey<Block> getBlockBlacklistTag() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.getBlockBlacklistTag();
        }
        return EMPTY_BLACKLIST;
    }

    /**
     * An inner class that references EnhancedAI classes directly.
     * This class will ONLY be loaded by the JVM if IS_ENHANCED_AI_LOADED is true,
     * preventing a NoClassDefFoundError.
     */
    private static class MinerMobsProxy {
        static int getMaxY() {
            return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.maxY;
        }

        static boolean blacklistTileEntities() {
            return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.blacklistTileEntities;
        }
        
        static boolean isBlacklistAsWhitelist() {
            return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.blockBlacklistAsWhitelist;
        }
        
        static TagKey<Block> getBlockBlacklistTag() {
            return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.BLOCK_BLACKLIST;
        }
    }
}