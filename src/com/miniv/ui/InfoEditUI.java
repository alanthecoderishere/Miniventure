package com.miniv.ui;

import com.miniv.core.InfoBlockManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class InfoEditUI implements UIState {
    private final int bx, by, bz;
    private final StringBuilder message;

    public InfoEditUI(int bx, int by, int bz, String initialText) {
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.message = new StringBuilder(initialText == null ? "" : initialText);
    }

    private void save() {
        InfoBlockManager.setInfo(bx, by, bz, message.toString().trim());
        UIManager.closeUI();
    }

    @Override
    public void draw(Graphics2D g, int width, int height) {
        int boxW = 480;
        int boxH = 340;
        int x0 = (width - boxW) / 2;
        int y0 = (height - boxH) / 2;

        g.setColor(new Color(40, 30, 20, 245));
        g.fillRoundRect(x0, y0, boxW, boxH, 12, 12);
        g.setColor(new Color(180, 130, 70));
        g.drawRoundRect(x0, y0, boxW, boxH, 12, 12);

        g.setFont(UIFont.bold(20));
        g.setColor(new Color(255, 220, 150));
        g.drawString("Edit Info Sign", x0 + 155, y0 + 36);

        g.setFont(UIFont.plain(13));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Sign at " + bx + ", " + by + ", " + bz + "  —  type your message below", x0 + 30, y0 + 62);

        int areaX = x0 + 24;
        int areaY = y0 + 78;
        int areaW = boxW - 48;
        int areaH = 200;
        g.setColor(new Color(25, 20, 15));
        g.fillRoundRect(areaX, areaY, areaW, areaH, 8, 8);
        g.setColor(new Color(120, 100, 70));
        g.drawRoundRect(areaX, areaY, areaW, areaH, 8, 8);

        g.setFont(UIFont.plain(15));
        g.setColor(Color.WHITE);
        drawWrapped(g, message.toString() + "_", areaX + 10, areaY + 22, areaW - 20, areaH - 30);

        g.setColor(new Color(255, 230, 120));
        g.setFont(UIFont.plain(12));
        g.drawString("ENTER = save for everyone   |   ESC = cancel", x0 + 70, y0 + boxH - 24);
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxW, int maxH) {
        FontMetrics fm = g.getFontMetrics();
        String[] lines = text.split("\n", -1);
        int ly = y;
        for (String rawLine : lines) {
            String line = "";
            for (String word : rawLine.split(" ")) {
                if (word.isEmpty()) continue;
                String tryLine = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(tryLine) > maxW) {
                    if (!line.isEmpty()) {
                        g.drawString(line, x, ly);
                        ly += fm.getHeight();
                        if (ly > y + maxH) return;
                    }
                    line = word;
                } else {
                    line = tryLine;
                }
            }
            if (!line.isEmpty()) {
                g.drawString(line, x, ly);
                ly += fm.getHeight();
                if (ly > y + maxH) return;
            }
        }
    }

    @Override
    public void handleClick(int mouseX, int mouseY, int button, int width, int height) {}

    @Override
    public void handleKey(int key, int action) {
        if (action != 1) return;

        if (key == 256) { // ESC
            UIManager.closeUI();
            return;
        }
        if (key == 257 || key == 335) { // ENTER
            save();
            return;
        }
        if (key == 259) { // BACKSPACE
            if (message.length() > 0) {
                message.setLength(message.length() - 1);
                UIManager.isDirty = true;
            }
        }
    }

    @Override
    public void handleChar(int codepoint) {
        if (codepoint >= 32 && codepoint != 127) {
            message.appendCodePoint(codepoint);
            UIManager.isDirty = true;
        }
    }
}
