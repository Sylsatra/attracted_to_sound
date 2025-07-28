package com.example.soundattract.mixin;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessor {
    
    @Accessor("threadedAnvilChunkStorage")
    ThreadedAnvilChunkStorage getThreadedAnvilChunkStorage();
}