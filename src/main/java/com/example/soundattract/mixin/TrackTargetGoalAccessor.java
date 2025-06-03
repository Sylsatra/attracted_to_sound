package com.example.soundattract.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.entity.ai.goal.TrackTargetGoal;

@Mixin(TrackTargetGoal.class)
public interface TrackTargetGoalAccessor {
    @Accessor("mob")
    MobEntity getMob();
}
