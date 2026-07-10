package com.example.soundattract.camo;

import net.minecraft.resources.Identifier;

import java.util.Optional;

public record CamoRenderData(
        boolean present,
        long seed,
        Optional<Integer> color,
        float strength,
        float upperStrength,
        float lowerStrength,
        float erosion,
        float humidity,
        float temperature,
        int resolution,
        Identifier texture) {
    public static final CamoRenderData EMPTY = new CamoRenderData(false, 0L, Optional.empty(), 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 16, null);
}
