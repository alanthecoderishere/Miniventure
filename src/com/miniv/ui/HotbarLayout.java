package com.miniv.ui;

import com.miniv.core.Config;

/** Shared hotbar geometry for drawing and hover hit-tests. */
public final class HotbarLayout {
    public static final int SLOT_W = 52;
    public static final int SLOT_H = 52;
    public static final int SLOT_PAD = 6;

    private HotbarLayout() {}

    public static int hotbarWidth() {
        return Config.HOTBAR_SIZE * (SLOT_W + SLOT_PAD) - SLOT_PAD;
    }

    public static int hotbarX(int screenWidth) {
        return (screenWidth - hotbarWidth()) / 2;
    }

    public static int hotbarY(int screenHeight) {
        return screenHeight - SLOT_H - 16;
    }

    /** Baseline Y for the bottom-most chat line (clears hotbar + label + hint). */
    public static int chatBaseY(int screenHeight) {
        return hotbarY(screenHeight) - 48;
    }

    /** Returns placeable block index under cursor, or -1. */
    public static int hitTest(int mx, int my, int screenWidth, int screenHeight) {
        int hx = hotbarX(screenWidth);
        int hy = hotbarY(screenHeight);
        for (int i = 0; i < Config.HOTBAR_SIZE; i++) {
            int sx = hx + i * (SLOT_W + SLOT_PAD);
            if (mx >= sx && mx <= sx + SLOT_W && my >= hy && my <= hy + SLOT_H) {
                return Config.hotbarSlots[i];
            }
        }
        return -1;
    }

    public static int slotCenterX(int slotIndex, int screenWidth) {
        return hotbarX(screenWidth) + slotIndex * (SLOT_W + SLOT_PAD) + SLOT_W / 2;
    }
}
