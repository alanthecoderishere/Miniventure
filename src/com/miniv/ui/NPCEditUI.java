package com.miniv.ui;

import com.miniv.core.NPCManager;
import com.miniv.core.NPCEntity;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Teacher/editor UI for NPC name, dialog lines, and scenario button labels.
 */
public class NPCEditUI implements UIState {
    private static final int BOX_W = 520;
    private static final int FIELD_X = 30;
    private static final int FIELD_W = 460;
    private static final int FIELD_H = 28;
    private static final int FIRST_ROW_Y = 88;
    private static final int ROW_STEP = 50;
    private static final int FIELD_COUNT = 7;
    private static final int BOX_H = FIRST_ROW_Y + ROW_STEP * FIELD_COUNT + 72;

    private final int cellX, cellY, cellZ;
    private final StringBuilder name = new StringBuilder();
    private final StringBuilder[] dialog = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
    private final StringBuilder[] buttons = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
    private int activeField = 0; // 0=name, 1-3=dialog, 4-6=buttons

    public NPCEditUI(NPCEntity npc, int cellX, int cellY, int cellZ) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.cellZ = cellZ;
        name.append(npc.name != null ? npc.name : "");
        if (npc.dialogLines != null) {
            for (int i = 0; i < dialog.length && i < npc.dialogLines.length; i++) {
                dialog[i].append(npc.dialogLines[i]);
            }
        }
        for (int i = 0; i < buttons.length && i < npc.scenarios.size(); i++) {
            buttons[i].append(npc.scenarios.get(i).buttonText);
        }
    }

    private int boxX(int width) { return (width - BOX_W) / 2; }
    private int boxY(int height) { return (height - BOX_H) / 2; }

    private int rowY(int fieldIndex) {
        return FIRST_ROW_Y + fieldIndex * ROW_STEP;
    }

    private StringBuilder activeBuffer() {
        if (activeField == 0) return name;
        if (activeField >= 1 && activeField <= 3) return dialog[activeField - 1];
        return buttons[activeField - 4];
    }

    private void save() {
        String[] lines = new String[3];
        for (int i = 0; i < 3; i++) lines[i] = dialog[i].toString().trim();
        String[] labels = new String[3];
        for (int i = 0; i < 3; i++) labels[i] = buttons[i].toString().trim();
        NPCManager.setNpcData(cellX, cellY, cellZ, name.toString().trim(), lines, labels);
        UIManager.closeUI();
    }

    @Override
    public void draw(Graphics2D g, int width, int height) {
        int bx = boxX(width);
        int by = boxY(height);

        g.setColor(new Color(28, 32, 52, 245));
        g.fillRoundRect(bx, by, BOX_W, BOX_H, 14, 14);
        g.setColor(new Color(130, 160, 255));
        g.drawRoundRect(bx, by, BOX_W, BOX_H, 14, 14);

        g.setFont(UIFont.bold(20));
        g.setColor(new Color(255, 200, 100));
        g.drawString("Edit NPC", bx + 200, by + 36);

        g.setFont(UIFont.plain(13));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("NPC at " + cellX + ", " + cellY + ", " + cellZ, bx + FIELD_X, by + 58);

        g.setFont(UIFont.plain(16));
        drawField(g, "Name:", name, bx + FIELD_X, by + rowY(0), activeField == 0);
        drawField(g, "Dialog line 1:", dialog[0], bx + FIELD_X, by + rowY(1), activeField == 1);
        drawField(g, "Dialog line 2:", dialog[1], bx + FIELD_X, by + rowY(2), activeField == 2);
        drawField(g, "Dialog line 3:", dialog[2], bx + FIELD_X, by + rowY(3), activeField == 3);
        drawField(g, "Button 1:", buttons[0], bx + FIELD_X, by + rowY(4), activeField == 4);
        drawField(g, "Button 2:", buttons[1], bx + FIELD_X, by + rowY(5), activeField == 5);
        drawField(g, "Button 3:", buttons[2], bx + FIELD_X, by + rowY(6), activeField == 6);

        int footerTop = by + rowY(6) + 36;
        g.setColor(new Color(180, 200, 255));
        g.setFont(UIFont.plain(12));
        g.drawString("Leave dialog/buttons empty for none. Locked after save.", bx + FIELD_X, footerTop);

        g.setColor(new Color(255, 230, 120));
        g.drawString("TAB = next field   |   ENTER = save & lock   |   ESC = cancel", bx + 70, footerTop + 22);
    }

    private void drawField(Graphics2D g, String label, StringBuilder val, int x, int y, boolean active) {
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(label, x, y);
        g.setColor(active ? Color.YELLOW : Color.WHITE);
        String text = val.length() == 0 ? "(click, then type)" : val.toString();
        g.drawString(text + (active ? "_" : ""), x + 160, y);
    }

    @Override
    public void handleClick(int mouseX, int mouseY, int button, int width, int height) {
        if (button != 0) return;
        int bx = boxX(width);
        int by = boxY(height);
        for (int i = 0; i < FIELD_COUNT; i++) {
            if (hit(mouseX, mouseY, bx + FIELD_X, by + rowY(i) - 12, FIELD_W, FIELD_H)) {
                activeField = i;
                UIManager.isDirty = true;
                return;
            }
        }
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void handleKey(int key, int action) {
        if (action != 1) return;

        if (key == 256) {
            UIManager.closeUI();
            return;
        }
        if (key == 257 || key == 335) {
            save();
            return;
        }
        if (key == 258) {
            activeField = (activeField + 1) % FIELD_COUNT;
            UIManager.isDirty = true;
            return;
        }
        if (key == 259) {
            StringBuilder buf = activeBuffer();
            if (buf.length() > 0) {
                buf.setLength(buf.length() - 1);
                UIManager.isDirty = true;
            }
        }
    }

    @Override
    public void handleChar(int codepoint) {
        if (codepoint >= 32 && codepoint != 127) {
            activeBuffer().appendCodePoint(codepoint);
            UIManager.isDirty = true;
        }
    }
}
