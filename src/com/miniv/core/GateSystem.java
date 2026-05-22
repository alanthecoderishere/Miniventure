package com.miniv.core;

import com.miniv.world.World;
import com.miniv.world.Voxel;

import java.util.ArrayList;
import java.util.List;

public class GateSystem {
    public static class Gate {
        public String id;
        public int targetScore;
        public List<int[]> blocks;
        public boolean isOpen = false;
        
        public Gate(String id, int targetScore, List<int[]> blocks) {
            this.id = id;
            this.targetScore = targetScore;
            this.blocks = blocks;
        }
    }

    public static List<Gate> gates = new ArrayList<>();

    public static void registerGate(String id, int targetScore, List<int[]> blocks) {
        gates.add(new Gate(id, targetScore, blocks));
    }

    public static void checkGates(int globalScore, World world) {
        for (Gate g : gates) {
            if (!g.isOpen && globalScore >= g.targetScore) {
                g.isOpen = true;
                // Open the gate by removing all associated blocks
                for (int[] pos : g.blocks) {
                    world.setBlock(pos[0], pos[1], pos[2], Voxel.AIR);
                    SupabaseMultiplayer.broadcastBlock(pos[0], pos[1], pos[2], Voxel.AIR);
                }
            }
        }
    }
}
