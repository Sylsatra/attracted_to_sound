package com.example.soundattract;

import net.minecraft.util.math.BlockPos;


public class SpatialPartitioner {
    /**
     * Gets a spatial key for the given BlockPos and partition size.
     * @param pos Block position
     * @param partitionSize Size of the cell/chunk (e.g., 16)
     * @return long key encoding cell/chunk coordinates
     */
    public static long getKey(BlockPos pos, int partitionSize) {
        int x = pos.getX() / partitionSize;
        int z = pos.getZ() / partitionSize;
        return (((long)x) << 32) | (z & 0xFFFFFFFFL);
    }
}
