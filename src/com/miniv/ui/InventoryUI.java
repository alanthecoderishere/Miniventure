package com.miniv.ui;

import com.miniv.core.Config;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class InventoryUI implements UIState {

    private static final int HOTBAR_SLOT = 52;
    private static final int HOTBAR_PAD = 6;
    private static final int GRID_SLOT = 56;
    private static final int GRID_PAD = 10;
    private static final int GRID_COLS = 5;

    @Override
    public void draw(Graphics2D g, int width, int height) {
        int boxW = 560;
        int boxH = 460;
        int bx = (width - boxW) / 2;
        int by = (height - boxH) / 2;

        g.setColor(new Color(30, 30, 35, 245));
        g.fillRoundRect(bx, by, boxW, boxH, 15, 15);
        g.setColor(new Color(120, 120, 130));
        g.drawRoundRect(bx, by, boxW, boxH, 15, 15);

        g.setFont(UIFont.bold(22));
        g.setColor(Color.WHITE);
        g.drawString("Inventory", bx + 220, by + 32);

        g.setFont(UIFont.plain(13));
        g.setColor(new Color(220, 220, 140));
        g.drawString("Step 1: Click a hotbar slot (or press 1-9).", bx + 48, by + 55);
        g.drawString("Step 2: Click a block below to put it on that slot.", bx + 48, by + 72);

        // Hotbar editor row
        g.setFont(UIFont.bold(13));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Hotbar", bx + 48, by + 82);

        int hotbarW = Config.HOTBAR_SIZE * (HOTBAR_SLOT + HOTBAR_PAD) - HOTBAR_PAD;
        int hotbarX = bx + (boxW - hotbarW) / 2;
        int hotbarY = by + 92;
        for (int i = 0; i < Config.HOTBAR_SIZE; i++) {
            drawSlot(g, hotbarX + i * (HOTBAR_SLOT + HOTBAR_PAD), hotbarY,
                    HOTBAR_SLOT, Config.hotbarSlots[i], i == Config.activeBlockIndex,
                    String.valueOf(i + 1));
        }

        // All placeable blocks
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("All blocks", bx + 48, by + 168);

        int gridW = GRID_COLS * (GRID_SLOT + GRID_PAD) - GRID_PAD;
        int startX = bx + (boxW - gridW) / 2;
        int startY = by + 178;

        for (int i = 0; i < Config.placeableBlocks.length; i++) {
            int cx = startX + (i % GRID_COLS) * (GRID_SLOT + GRID_PAD);
            int cy = startY + (i / GRID_COLS) * (GRID_SLOT + GRID_PAD);
            boolean onHotbar = isOnHotbar(i);
            boolean hover = i == UIManager.hoveredBlockIndex;
            drawSlot(g, cx, cy, GRID_SLOT, i, hover, null);
            if (onHotbar) {
                g.setColor(new Color(100, 200, 120, 180));
                g.drawRoundRect(cx, cy, GRID_SLOT, GRID_SLOT, 8, 8);
            }
        }

        for (int i = 0; i < Config.HOTBAR_SIZE; i++) {
            if (Config.hotbarSlots[i] == UIManager.hoveredBlockIndex) {
                int cx = hotbarX + i * (HOTBAR_SLOT + HOTBAR_PAD);
                g.setColor(new Color(255, 200, 50, 120));
                g.fillRoundRect(cx, hotbarY, HOTBAR_SLOT, HOTBAR_SLOT, 8, 8);
            }
        }

        g.setColor(new Color(180, 180, 190));
        g.setFont(UIFont.plain(12));
        g.drawString("E or ESC to close", bx + 220, by + boxH - 18);
    }

    private static void drawSlot(Graphics2D g, int x, int y, int size, int blockIndex,
                                 boolean selected, String cornerLabel) {
        g.setColor(selected ? new Color(255, 200, 50, 230) : new Color(55, 55, 60));
        g.fillRoundRect(x, y, size, size, 8, 8);
        g.setColor(selected ? new Color(255, 230, 100) : new Color(150, 150, 160));
        g.drawRoundRect(x, y, size, size, 8, 8);

        if (cornerLabel != null) {
            g.setFont(UIFont.bold(10));
            g.setColor(selected ? Color.BLACK : new Color(200, 200, 200));
            g.drawString(cornerLabel, x + 4, y + 12);
        }

        String name = Config.placeableNames[blockIndex];
        String abbr = name.length() > 7 ? name.substring(0, 6) + "." : name;
        g.setFont(UIFont.bold(10));
        g.setColor(selected ? Color.BLACK : Color.WHITE);
        int tw = g.getFontMetrics().stringWidth(abbr);
        g.drawString(abbr, x + (size - tw) / 2, y + size - 10);
    }

    private static boolean isOnHotbar(int blockIndex) {
        for (int slot : Config.hotbarSlots) {
            if (slot == blockIndex) return true;
        }
        return false;
    }

    @Override
    public void handleClick(int mouseX, int mouseY, int button, int width, int height) {
        if (button != 0) return;

        int boxW = 560;
        int boxH = 460;
        int bx = (width - boxW) / 2;
        int by = (height - boxH) / 2;

        int hotbarW = Config.HOTBAR_SIZE * (HOTBAR_SLOT + HOTBAR_PAD) - HOTBAR_PAD;
        int hotbarX = bx + (boxW - hotbarW) / 2;
        int hotbarY = by + 92;

        for (int i = 0; i < Config.HOTBAR_SIZE; i++) {
            int cx = hotbarX + i * (HOTBAR_SLOT + HOTBAR_PAD);
            if (hit(mouseX, mouseY, cx, hotbarY, HOTBAR_SLOT)) {
                Config.activeBlockIndex = i;
                UIManager.isDirty = true;
                return;
            }
        }

        int gridW = GRID_COLS * (GRID_SLOT + GRID_PAD) - GRID_PAD;
        int startX = bx + (boxW - gridW) / 2;
        int startY = by + 178;

        for (int i = 0; i < Config.placeableBlocks.length; i++) {
            int cx = startX + (i % GRID_COLS) * (GRID_SLOT + GRID_PAD);
            int cy = startY + (i / GRID_COLS) * (GRID_SLOT + GRID_PAD);
            if (hit(mouseX, mouseY, cx, cy, GRID_SLOT)) {
                Config.hotbarSlots[Config.activeBlockIndex] = i;
                UIManager.isDirty = true;
                return;
            }
        }
    }

    private static boolean hit(int mx, int my, int x, int y, int size) {
        return mx >= x && mx <= x + size && my >= y && my <= y + size;
    }

    /** Placeable block index under cursor in inventory UI, or -1. */
    public static int hitTest(int mx, int my, int width, int height) {
        int boxW = 560;
        int boxH = 460;
        int bx = (width - boxW) / 2;
        int by = (height - boxH) / 2;

        int hotbarW = Config.HOTBAR_SIZE * (HOTBAR_SLOT + HOTBAR_PAD) - HOTBAR_PAD;
        int hotbarX = bx + (boxW - hotbarW) / 2;
        int hotbarY = by + 92;
        for (int i = 0; i < Config.HOTBAR_SIZE; i++) {
            int cx = hotbarX + i * (HOTBAR_SLOT + HOTBAR_PAD);
            if (hit(mx, my, cx, hotbarY, HOTBAR_SLOT)) {
                return Config.hotbarSlots[i];
            }
        }

        int gridW = GRID_COLS * (GRID_SLOT + GRID_PAD) - GRID_PAD;
        int startX = bx + (boxW - gridW) / 2;
        int startY = by + 178;
        for (int i = 0; i < Config.placeableBlocks.length; i++) {
            int cx = startX + (i % GRID_COLS) * (GRID_SLOT + GRID_PAD);
            int cy = startY + (i / GRID_COLS) * (GRID_SLOT + GRID_PAD);
            if (hit(mx, my, cx, cy, GRID_SLOT)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void handleKey(int key, int action) {
        if (action != 1) return;

        if (key == 256 || key == 69) { // ESC or E
            UIManager.closeUI();
            return;
        }
        if (key >= 49 && key <= 57) { // 1-9
            int slot = key - 49;
            if (slot < Config.HOTBAR_SIZE) {
                Config.activeBlockIndex = slot;
                UIManager.isDirty = true;
            }
        }
    }

    @Override
    public void handleChar(int codepoint) {}
}
