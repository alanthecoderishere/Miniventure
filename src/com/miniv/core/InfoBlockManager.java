package com.miniv.core;

import com.miniv.ui.InfoEditUI;
import com.miniv.ui.InfoUI;
import com.miniv.ui.UIManager;

import java.util.HashMap;
import java.util.Map;

public class InfoBlockManager {
    private static final HashMap<String, String> infoBoards = new HashMap<>();

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static void setInfo(int x, int y, int z, String text) {
        infoBoards.put(key(x, y, z), text);
        SupabaseMultiplayer.broadcastInfo(x, y, z, text);
    }

    public static void applyRemote(int x, int y, int z, String text) {
        infoBoards.put(key(x, y, z), text);
    }

    public static void applyRemoteRemove(int x, int y, int z) {
        infoBoards.remove(key(x, y, z));
    }

    public static String getText(int x, int y, int z) {
        return infoBoards.getOrDefault(key(x, y, z), "");
    }

    /** Right-click: edit the sign message. */
    public static void edit(int x, int y, int z) {
        String text = getText(x, y, z);
        if (text.isEmpty()) {
            text = "Welcome! Edit this message.";
        }
        UIManager.setActiveUI(new InfoEditUI(x, y, z, text));
    }

    /** Shift + right-click: read only. */
    public static void view(int x, int y, int z) {
        String text = getText(x, y, z);
        if (text.isEmpty()) {
            text = "No message on this sign yet.";
        }
        UIManager.setActiveUI(new InfoUI(text));
    }

    public static void remove(int x, int y, int z) {
        infoBoards.remove(key(x, y, z));
        SupabaseMultiplayer.broadcastInfoRemove(x, y, z);
    }

    public static String encodeSyncData() {
        if (infoBoards.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> e : infoBoards.entrySet()) {
            if (count++ > 0) sb.append(";;");
            sb.append(e.getKey()).append("|").append(encode(e.getValue()));
        }
        return sb.toString();
    }

    private static String encode(String text) {
        return text.replace("\\", "\\\\").replace("|", "\\p").replace(";", "\\s").replace("\n", "\\n");
    }

    static void decodeEntry(String entry) {
        int sep = entry.indexOf('|');
        if (sep < 0) return;
        String[] pos = entry.substring(0, sep).split(",");
        if (pos.length != 3) return;
        try {
            int x = Integer.parseInt(pos[0].trim());
            int y = Integer.parseInt(pos[1].trim());
            int z = Integer.parseInt(pos[2].trim());
            String text = decode(entry.substring(sep + 1));
            applyRemote(x, y, z, text);
        } catch (NumberFormatException ignored) {
        }
    }

    private static String decode(String raw) {
        return raw.replace("\\n", "\n").replace("\\s", ";").replace("\\p", "|").replace("\\\\", "\\");
    }
}
