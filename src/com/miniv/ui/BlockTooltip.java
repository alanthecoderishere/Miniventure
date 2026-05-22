package com.miniv.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public final class BlockTooltip {
    private BlockTooltip() {}

    public static void draw(Graphics2D g, int blockIndex, int anchorX, int anchorY, int screenW, int screenH) {
        if (blockIndex < 0) return;

        String title = BlockHints.getTitle(blockIndex);
        String[] lines = BlockHints.getLines(blockIndex);
        if (title.isEmpty()) return;

        g.setFont(UIFont.bold(13));
        FontMetrics fmBold = g.getFontMetrics();
        g.setFont(UIFont.plain(12));
        FontMetrics fm = g.getFontMetrics();

        int pad = 10;
        int maxW = 280;
        int lineH = fm.getHeight();
        int boxW = pad * 2 + Math.max(fmBold.stringWidth(title), maxLineWidth(fm, lines, maxW - pad * 2));
        int boxH = pad + fmBold.getHeight() + 4 + lines.length * lineH + pad;

        int tx = anchorX - boxW / 2;
        int ty = anchorY - boxH - 8;
        if (tx < 8) tx = 8;
        if (tx + boxW > screenW - 8) tx = screenW - boxW - 8;
        if (ty < 8) ty = anchorY + 12;

        g.setColor(new Color(20, 25, 40, 235));
        g.fillRoundRect(tx, ty, boxW, boxH, 8, 8);
        g.setColor(new Color(255, 200, 80, 200));
        g.drawRoundRect(tx, ty, boxW, boxH, 8, 8);

        g.setFont(UIFont.bold(13));
        g.setColor(new Color(255, 230, 120));
        g.drawString(title, tx + pad, ty + pad + fmBold.getAscent());

        g.setFont(UIFont.plain(12));
        g.setColor(new Color(220, 225, 235));
        int ly = ty + pad + fmBold.getHeight() + 6;
        for (String line : lines) {
            g.drawString(line, tx + pad, ly + fm.getAscent());
            ly += lineH;
        }
    }

    private static int maxLineWidth(FontMetrics fm, String[] lines, int maxW) {
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, Math.min(fm.stringWidth(line), maxW));
        }
        return Math.max(w, 80);
    }
}
