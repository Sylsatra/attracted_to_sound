package com.example.soundattract.ai;

import net.minecraft.util.math.BlockPos;

public class SpatialPartitioner {
    public static long getKey(BlockPos pos, int partitionSize) {
        long cellX = pos.getX() / partitionSize;
        long cellZ = pos.getZ() / partitionSize;
        return (cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }
}
