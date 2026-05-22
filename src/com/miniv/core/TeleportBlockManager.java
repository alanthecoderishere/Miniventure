package com.miniv.core;

import com.miniv.ui.TeleportUI;
import com.miniv.ui.UIManager;
import com.miniv.world.Voxel;
import com.miniv.world.World;

import java.util.HashMap;

public class TeleportBlockManager {
    public static class TeleportTarget {
        public float tx, ty, tz;
        public TeleportTarget(float x, float y, float z) {
            this.tx = x;
            this.ty = y;
            this.tz = z;
        }
    }

    private static final HashMap<String, TeleportTarget> teleports = new HashMap<>();
    private static long lastTeleportMs = 0;

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static void interact(int x, int y, int z, Player player) {
        String k = key(x, y, z);
        TeleportTarget t = teleports.get(k);
        if (t == null) {
            t = new TeleportTarget(player.position.x + 0.5f, player.position.y, player.position.z + 0.5f);
            teleports.put(k, t);
        }
        UIManager.setActiveUI(new TeleportUI(x, y, z, t, player));
    }

    public static void setTeleport(int x, int y, int z, float tx, float ty, float tz) {
        teleports.put(key(x, y, z), new TeleportTarget(tx, ty, tz));
        SupabaseMultiplayer.broadcastTeleport(x, y, z, tx, ty, tz);
    }

    public static void applyRemote(int x, int y, int z, float tx, float ty, float tz) {
        teleports.put(key(x, y, z), new TeleportTarget(tx, ty, tz));
    }

    public static void applyRemoteRemove(int x, int y, int z) {
        teleports.remove(key(x, y, z));
    }

    public static void remove(int x, int y, int z) {
        teleports.remove(key(x, y, z));
        SupabaseMultiplayer.broadcastTeleportRemove(x, y, z);
    }

    /** Serialized for late-join sync: bx,by,bz,tx,ty,tz;... */
    public static String encodeSyncData() {
        if (teleports.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var e : teleports.entrySet()) {
            String[] p = e.getKey().split(",");
            if (p.length != 3) continue;
            TeleportTarget t = e.getValue();
            if (count++ > 0) sb.append(';');
            sb.append(p[0]).append(',').append(p[1]).append(',').append(p[2])
              .append(',').append(t.tx).append(',').append(t.ty).append(',').append(t.tz);
        }
        return sb.toString();
    }

    public static void checkStep(Player player, World world) {
        if (System.currentTimeMillis() - lastTeleportMs < 400) return;

        int bx = (int) Math.floor(player.position.x + 0.5f);
        int bz = (int) Math.floor(player.position.z + 0.5f);
        int yFeet = (int) Math.floor(player.position.y);
        int yBelow = (int) Math.floor(player.position.y - 0.05f);

        for (int by = yFeet; by >= yBelow; by--) {
            if (world.getBlock(bx, by, bz) != Voxel.TELEPORT_BLOCK) continue;

            TeleportTarget t = teleports.get(key(bx, by, bz));
            if (t == null) continue;

            player.landAt(t.tx, t.ty, t.tz);
            lastTeleportMs = System.currentTimeMillis();
            return;
        }
    }
}
