package com.miniv.ui;

import com.miniv.core.NPCEntity;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Simple conversation UI: NPC name, dialog lines, and numbered scenario buttons.
 */
public class DialogUI implements UIState {
    private static final int BTN_H = 30;
    private static final int BTN_PAD = 8;

    private final NPCEntity npc;
    private int boxX, boxY, boxW, boxH;
    private int btnStartY;

    public DialogUI(NPCEntity npc) {
        this.npc = npc;
    }

    private void layout(int screenW, int screenH) {
        boxW = 520;
        int lineCount = npc.dialogLines != null ? npc.dialogLines.length : 0;
        int scenarioCount = npc.scenarios.size();
        int dialogBlock = lineCount > 0 ? lineCount * 26 : 26;
        int scenarioBlock = scenarioCount > 0 ? 24 + scenarioCount * (BTN_H + BTN_PAD) : 0;
        boxH = 70 + dialogBlock + scenarioBlock + 55;
        boxX = (screenW - boxW) / 2;
        boxY = screenH - boxH - 48;
        btnStartY = boxY + 58 + dialogBlock;
    }

    @Override
    public void draw(Graphics2D g, int width, int height) {
        layout(width, height);

        g.setColor(new Color(18, 22, 38, 245));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 14, 14);
        g.setColor(new Color(130, 160, 255));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 14, 14);

        g.setFont(UIFont.bold(20));
        g.setColor(new Color(255, 200, 100));
        g.drawString(npc.name, boxX + 20, boxY + 32);

        g.setFont(UIFont.plain(16));
        g.setColor(Color.WHITE);
        int ly = boxY + 58;
        if (npc.dialogLines != null && npc.dialogLines.length > 0) {
            for (String line : npc.dialogLines) {
                g.drawString(line, boxX + 20, ly);
                ly += 26;
            }
        } else {
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("(no dialog)", boxX + 20, ly);
        }

        if (npc.scenarios.isEmpty()) {
            g.setColor(new Color(255, 230, 120));
            g.setFont(UIFont.plain(12));
            g.drawString("ESC to close", boxX + 20, boxY + boxH - 18);
            return;
        }

        g.setFont(UIFont.bold(13));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Pilih aksi:", boxX + 20, btnStartY - 10);

        int by = btnStartY;
        for (int i = 0; i < npc.scenarios.size(); i++) {
            int bx = boxX + 16;
            int bw = boxW - 32;
            g.setColor(new Color(45, 55, 90));
            g.fillRoundRect(bx, by, bw, BTN_H, 8, 8);
            g.setColor(new Color(100, 180, 255));
            g.drawRoundRect(bx, by, bw, BTN_H, 8, 8);
            g.setColor(Color.CYAN);
            g.drawString("[" + (i + 1) + "]  " + npc.scenarios.get(i).buttonText, bx + 12, by + 20);
            by += BTN_H + BTN_PAD;
        }

        g.setColor(new Color(255, 230, 120));
        g.setFont(UIFont.plain(12));
        g.drawString("Klik tombol atau tekan 1-" + npc.scenarios.size() + "  |  ESC tutup", boxX + 20, boxY + boxH - 18);
    }

    @Override
    public void handleClick(int mouseX, int mouseY, int button, int width, int height) {
        if (button != 0) return;
        layout(width, height);
        int by = btnStartY;
        int bw = boxW - 32;
        int bx = boxX + 16;
        for (int i = 0; i < npc.scenarios.size(); i++) {
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + BTN_H) {
                runScenario(i);
                return;
            }
            by += BTN_H + BTN_PAD;
        }
    }

    @Override
    public void handleKey(int key, int action) {
        if (action != 1) return;

        if (key == 256) {
            UIManager.closeUI();
            return;
        }
        if (key >= 49 && key <= 57) {
            int idx = key - 49;
            if (idx < npc.scenarios.size()) {
                runScenario(idx);
            }
        }
    }

    private void runScenario(int index) {
        npc.scenarios.get(index).action.run();
        UIManager.isDirty = true;
    }

    @Override
    public void handleChar(int codepoint) {}
}
