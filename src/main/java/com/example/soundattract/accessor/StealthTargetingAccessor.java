package com.example.soundattract.accessor;

public interface StealthTargetingAccessor {
    /**
     * Gets the number of ticks the mob has been unable to see its current target.
     */
    int soundattract_getLosingTargetTicks();

    /**
     * Sets the number of ticks the mob has been unable to see its current target.
     * @param ticks The number of ticks.
     */
    void soundattract_setLosingTargetTicks(int ticks);
}