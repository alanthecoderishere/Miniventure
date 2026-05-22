package com.miniv.world;

public class Voxel {
    public static final byte AIR    = 0;
    public static final byte GRASS  = 1;
    public static final byte DIRT   = 2;
    public static final byte STONE  = 3;
    public static final byte LEAVES = 4;
    public static final byte WOOD   = 5;
    public static final byte SAND   = 6;
    public static final byte COBBLE = 7; // uses Stone texture
    public static final byte LOG    = 8; // uses Wood texture (vertical log)
    public static final byte GRAVEL = 9; // uses Dirt texture
    
    public static final byte QUIZ_BLOCK = 10;
    public static final byte INFO_BLOCK = 11;
    public static final byte TELEPORT_BLOCK = 12;
    public static final byte ATTENDANCE_BLOCK = 13;
    public static final byte NPC_SPAWNER = 14;
}
