
package com.example.soundattract.accessor;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;


public interface FleeOnDamageAccessor {
    void soundattract_setFleeFromLocation(@Nullable Vec3d pos);

    @Nullable
    Vec3d soundattract_getFleeFromLocation();
}