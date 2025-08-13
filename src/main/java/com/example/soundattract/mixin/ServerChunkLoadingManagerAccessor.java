package com.example.soundattract.mixin;

import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor {

    /**
     * This will generate a method at compile time that allows us to access
     * the private 'viewDistance' field.
     */
    @Accessor("watchDistance")
    int getWatchDistance();
}