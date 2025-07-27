package com.example.soundattract.mixin;

import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * This accessor mixin provides a getter for the private 'viewDistance' field
 * inside the ServerChunkLoadingManager, which is held by the ServerChunkManager.
 */
@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessor {
    
    /**
     * This will generate a method at compile time that allows us to access
     * the protected 'chunkLoadingManager' field from anywhere.
     */
    @Accessor("chunkLoadingManager")
    net.minecraft.server.world.ServerChunkLoadingManager getChunkLoadingManager();
}