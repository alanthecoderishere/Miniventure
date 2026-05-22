package com.miniv.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class InfoUI implements UIState {
    private String text;

    public InfoUI(String text) {
        this.text = text;
    }

    @Override
    public void draw(Graphics2D g, int width, int height) {
        int boxW = 400;
        int boxH = 300;
        int bx = (width - boxW) / 2;
        int by = (height - boxH) / 2;

        g.setColor(new Color(40, 30, 20, 240));
        g.fillRoundRect(bx, by, boxW, boxH, 10, 10);
        g.setColor(new Color(150, 100, 50));
        g.drawRoundRect(bx, by, boxW, boxH, 10, 10);

        g.setColor(Color.WHITE);
        g.setFont(UIFont.bold(22));
        g.drawString("Info Sign", bx + 130, by + 40);

        g.setFont(UIFont.plain(18));
        g.setColor(Color.LIGHT_GRAY);
        
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        String line = "";
        int y = by + 90;
        for (String word : words) {
            if (fm.stringWidth(line + word) > boxW - 40) {
                g.drawString(line, bx + 20, y);
                line = "";
                y += fm.getHeight();
            }
            line += word + " ";
        }
        g.drawString(line, bx + 20, y);

        g.setColor(Color.YELLOW);
        g.setFont(UIFont.bold(14));
        g.drawString("Press ESC to close  |  Right-click sign to edit", bx + 50, by + 280);
    }

    @Override
    public void handleClick(int x, int y, int button, int width, int height) {}

    @Override
    public void handleKey(int key, int action) {
        if (action == 1 && key == 256) { // ESC
            UIManager.closeUI();
        }
    }

    @Override
    public void handleChar(int codepoint) {}
}
