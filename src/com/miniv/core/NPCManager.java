package com.miniv.core;

import com.miniv.ui.ChatSystem;
import com.miniv.ui.DialogUI;
import com.miniv.ui.NPCEditUI;
import com.miniv.ui.UIManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NPCManager {
    public static final String TEMPLATE_GURU = "guru";

    private static Player localPlayer;
    private static ChatSystem localChat;

    /** NPC instance key (spawn cell) -> entity */
    private static final Map<String, NPCEntity> npcs = new ConcurrentHashMap<>();
    /** Spawner block position -> NPC instance key */
    private static final Map<String, String> spawnerBlocks = new ConcurrentHashMap<>();

    public static List<NPCEntity> getAll() {
        return new ArrayList<>(npcs.values());
    }

    public static void initDefaults(Player player, ChatSystem chat, com.miniv.world.World world) {
        localPlayer = player;
        localChat = chat;
    }

    /** Foot Y on top of the highest solid block in this column (same rule as player spawn). */
    public static int footYOnSurface(com.miniv.world.World world, int bx, int bz) {
        if (world == null) return 1;
        for (int y = Config.worldHeight - 1; y >= 1; y--) {
            if (world.getBlock(bx, y, bz) != com.miniv.world.Voxel.AIR) {
                return y + 1;
            }
        }
        return 1;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static NPCEntity createFromTemplate(String templateId, float x, float y, float z) {
        return new NPCEntity(templateId, "NPC", x, y, z, new String[0]);
    }

    /**
     * Spawn NPC with feet at (bx+0.5, footY, bz+0.5) — same placement as {@link Player}.
     * Sync key uses block XZ and foot Y.
     */
    public static void spawn(String templateId, int bx, int footY, int bz, boolean broadcast) {
        String nk = key(bx, footY, bz);
        if (npcs.containsKey(nk)) return;

        NPCEntity npc = createFromTemplate(templateId, bx + 0.5f, footY, bz + 0.5f);
        npcs.put(nk, npc);
        if (broadcast) {
            SupabaseMultiplayer.broadcastNpcSpawn(templateId, bx, footY, bz);
        }
    }

    /**
     * Spawn NPC at the placement cell — same height as the player (one block tall).
     * No extra pedestal block (that was making NPCs look 2 blocks high).
     */
    public static void spawnAtBlock(int bx, int footY, int bz) {
        String nk = key(bx, footY, bz);
        if (npcs.containsKey(nk)) return;

        spawn(TEMPLATE_GURU, bx, footY, bz, true);
        spawnerBlocks.put(nk, nk);
        openEdit(bx, footY, bz);
    }

    public static void openEdit(int bx, int footY, int bz) {
        NPCEntity npc = npcs.get(key(bx, footY, bz));
        if (npc == null || npc.locked) return;
        UIManager.setActiveUI(new NPCEditUI(npc, bx, footY, bz));
    }

    /** Left-click near an NPC to remove it (no spawner block in the world). */
    public static boolean removeNear(float px, float py, float pz) {
        NPCEntity npc = findClosest(px, py, pz, 2.5f);
        if (npc == null) return false;
        int bx = (int) npc.position.x;
        int footY = (int) npc.position.y;
        int bz = (int) npc.position.z;
        removeAt(bx, footY, bz);
        spawnerBlocks.remove(key(bx, footY, bz));
        return true;
    }

    /** Turn NPCs to face the local player (same facing rules as movement). */
    public static void updateFacing(Player player) {
        if (player == null) return;
        float px = player.position.x + 0.5f;
        float pz = player.position.z + 0.5f;
        for (NPCEntity npc : npcs.values()) {
            float dx = px - (npc.position.x + 0.5f);
            float dz = pz - (npc.position.z + 0.5f);
            if (Math.abs(dx) < 0.01f && Math.abs(dz) < 0.01f) continue;
            if (Math.abs(dx) > Math.abs(dz)) {
                npc.facing = dx > 0 ? 3 : 2;
            } else {
                npc.facing = dz > 0 ? 0 : 1;
            }
        }
    }

    public static void applyRemoteSpawn(String templateId, int x, int y, int z) {
        spawn(templateId, x, y, z, false);
    }

    public static void removeAt(int x, int y, int z) {
        String nk = key(x, y, z);
        if (npcs.remove(nk) != null) {
            SupabaseMultiplayer.broadcastNpcRemove(x, y, z);
        }
        spawnerBlocks.values().removeIf(nk::equals);
    }

    public static void removeAtBlock(int bx, int by, int bz) {
        String nk = spawnerBlocks.remove(key(bx, by, bz));
        if (nk != null) {
            String[] p = nk.split(",");
            if (p.length == 3) {
                try {
                    int x = Integer.parseInt(p[0]);
                    int y = Integer.parseInt(p[1]);
                    int z = Integer.parseInt(p[2]);
                    npcs.remove(nk);
                    SupabaseMultiplayer.broadcastNpcRemove(x, y, z);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public static void applyRemoteRemove(int x, int y, int z) {
        npcs.remove(key(x, y, z));
        spawnerBlocks.values().removeIf(k -> k.equals(key(x, y, z)));
    }

    public static NPCEntity findNearby(float px, float py, float pz) {
        return findClosest(px, py, pz, 4.0f);
    }

    private static NPCEntity findClosest(float px, float py, float pz, float maxDist) {
        NPCEntity closest = null;
        float best = Float.MAX_VALUE;
        for (NPCEntity npc : npcs.values()) {
            float d = npc.distanceTo(px, py, pz);
            if (d <= maxDist && d < best) {
                best = d;
                closest = npc;
            }
        }
        return closest;
    }

    public static void openDialog(NPCEntity npc) {
        if (npc == null) return;
        UIManager.setActiveUI(new DialogUI(npc));
    }

    public static boolean tryInteract(float px, float py, float pz) {
        NPCEntity npc = findNearby(px, py, pz);
        if (npc == null) return false;
        if (!npc.locked) {
            openEdit((int) npc.position.x, (int) npc.position.y, (int) npc.position.z);
            return true;
        }
        openDialog(npc);
        return true;
    }

    /** Shift + right-click: open editor only while NPC is not locked yet. */
    public static boolean tryEdit(float px, float py, float pz) {
        NPCEntity npc = findNearby(px, py, pz);
        if (npc == null) return false;
        if (npc.locked) {
            if (localChat != null) {
                localChat.addSystemMessage("NPC is locked. Left-click to remove and place a new one.");
                localChat.dirty = true;
            }
            return false;
        }
        openEdit((int) npc.position.x, (int) npc.position.y, (int) npc.position.z);
        return true;
    }

    public static void setNpcData(int x, int y, int z, String name, String[] dialogLines, String[] buttonLabels) {
        NPCEntity npc = npcs.get(key(x, y, z));
        if (npc == null) return;
        applyData(npc, name, dialogLines, buttonLabels);
        npc.locked = true;
        SupabaseMultiplayer.broadcastNpcData(x, y, z, name, dialogLines, buttonLabels, true);
    }

    public static void applyRemoteData(int x, int y, int z, String name, String[] dialogLines,
                                       String[] buttonLabels, boolean locked) {
        NPCEntity npc = npcs.get(key(x, y, z));
        if (npc == null) return;
        applyData(npc, name, dialogLines, buttonLabels);
        npc.locked = locked;
    }

    private static void applyData(NPCEntity npc, String name, String[] dialogLines, String[] buttonLabels) {
        npc.name = (name != null && !name.isEmpty()) ? name : "NPC";
        List<String> lines = new ArrayList<>();
        if (dialogLines != null) {
            for (String line : dialogLines) {
                if (line != null && !line.isEmpty()) lines.add(line);
            }
        }
        npc.dialogLines = lines.toArray(new String[0]);

        npc.scenarios.clear();
        if (buttonLabels != null) {
            for (String label : buttonLabels) {
                if (label != null && !label.isEmpty()) {
                    npc.addScenario(label, UIManager::closeUI);
                }
            }
        }
    }

    private static String encode(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("|", "\\p").replace(";", "\\s").replace("\n", "\\n");
    }

    private static String decode(String raw) {
        if (raw == null) return "";
        return raw.replace("\\n", "\n").replace("\\s", ";").replace("\\p", "|").replace("\\\\", "\\");
    }

    public static String encodeSyncData() {
        if (npcs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (NPCEntity npc : npcs.values()) {
            if (count++ > 0) sb.append(";;");
            int x = (int) npc.position.x;
            int y = (int) npc.position.y;
            int z = (int) npc.position.z;
            String dialog = npc.dialogLines == null ? "" : String.join("\n", npc.dialogLines);
            StringBuilder labels = new StringBuilder();
            for (int i = 0; i < npc.scenarios.size(); i++) {
                if (i > 0) labels.append(';');
                labels.append(npc.scenarios.get(i).buttonText);
            }
            sb.append(npc.id).append("|").append(x).append("|").append(y).append("|").append(z)
              .append("|").append(encode(npc.name)).append("|").append(encode(dialog))
              .append("|").append(encode(labels.toString()))
              .append("|").append(npc.locked ? "1" : "0");
        }
        return sb.toString();
    }

    public static void decodeSyncEntry(String entry) {
        String[] p = entry.split("\\|", 8);
        if (p.length < 4) return;
        try {
            String templateId = p[0];
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            if (!npcs.containsKey(key(x, y, z))) {
                applyRemoteSpawn(templateId, x, y, z);
            }
            if (p.length >= 7) {
                String name = decode(p[4]);
                String[] dialogLines = decode(p[5]).split("\n", -1);
                String[] labels = decode(p[6]).split(";", -1);
                boolean locked = p.length >= 8 && "1".equals(p[7]);
                applyRemoteData(x, y, z, name, dialogLines, labels, locked);
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
