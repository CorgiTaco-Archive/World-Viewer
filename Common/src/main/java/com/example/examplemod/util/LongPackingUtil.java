package com.example.examplemod.util;

public class LongPackingUtil {

    public static long tileKey(int tileX, int tileZ) {
        return (long) tileX & 4294967295L | ((long) tileZ & 4294967295L) << 32;
    }

    public static int getTileX(long tilePos) {
        return (int) (tilePos & 4294967295L);
    }

    public static int getTileZ(long tilePos) {
        return (int) (tilePos >>> 32 & 4294967295L);
    }

    public static int blockToTile(int blockCoord, int shift) {
        return blockCoord >> shift;
    }

    public static int tileToBlock(int tileCoord, int shift) {
        return tileCoord << shift;
    }
}
