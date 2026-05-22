package com.miniv.ui;

public class UIManager {
    private static UIState activeUI = null;
    public static boolean isDirty = false;

    public static int mouseX;
    public static int mouseY;
    /** Placeable block index under cursor, or -1. */
    public static int hoveredBlockIndex = -1;

    public static void updateMouse(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public static void setHoveredBlock(int blockIndex) {
        if (blockIndex != hoveredBlockIndex) {
            hoveredBlockIndex = blockIndex;
            isDirty = true;
        }
    }

    public static void setActiveUI(UIState ui) {
        activeUI = ui;
        isDirty = true;
    }

    public static UIState getActiveUI() {
        return activeUI;
    }

    public static boolean hasActiveUI() {
        return activeUI != null;
    }

    public static void closeUI() {
        activeUI = null;
        isDirty = true;
    }
}
