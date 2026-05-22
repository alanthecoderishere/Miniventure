package com.miniv.ui;

import com.miniv.core.Config;

/** Short descriptions shown when hovering blocks in the hotbar or inventory. */
public final class BlockHints {
    private static final String[][] LINES = {
        {"Building block — wood planks.", "Right-click to place."},
        {"Building block — stone.", "Right-click to place."},
        {"Building block — cobblestone.", "Right-click to place."},
        {"Building block — dirt.", "Right-click to place."},
        {"Building block — gravel.", "Right-click to place."},
        {"Building block — sand.", "Right-click to place."},
        {"Decoration — tree leaves.", "Right-click to place."},
        {"Building block — log.", "Right-click to place."},
        {"Surface block — grass.", "Right-click to place."},
        {"Quiz block — students answer questions.", "Teacher: right-click to create. Students: right-click to play."},
        {"Info sign — shows a message to players.", "Right-click to edit. Shift+right-click to read only."},
        {"Teleport pad — moves players to a spot.", "Right-click pad to set destination (ENTER to save)."},
        {"Attendance block — marks you present.", "Right-click while standing on it."},
        {"NPC spawner — empty NPC (configure on place).", "ENTER saves & locks. Right-click locked NPC to talk. Left-click to remove."},
    };

    private BlockHints() {}

    public static String getTitle(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= Config.placeableNames.length) return "";
        return Config.placeableNames[blockIndex];
    }

    public static String[] getLines(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= LINES.length) return new String[0];
        return LINES[blockIndex];
    }
}
