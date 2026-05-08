package com.miniv.ui;

import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;

public class TextRenderer {
    private int textureId;
    private int width = 800;
    private int height = 600;
    private Font font;

    public void init(int screenWidth, int screenHeight) {
        this.width = screenWidth == 0 ? 800 : screenWidth;
        this.height = screenHeight == 0 ? 600 : screenHeight;
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        font = new Font("SansSerif", Font.BOLD, 18);
    }

    public void updateTexture(ChatSystem chat, int screenWidth, int screenHeight) {
        if (screenWidth > 0 && screenHeight > 0 && (this.width != screenWidth || this.height != screenHeight)) {
            this.width = screenWidth;
            this.height = screenHeight;
            chat.dirty = true;
        }

        if (!chat.dirty) return;
        chat.dirty = false;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        int y = height - 50;

        if (chat.isTyping) {
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRect(10, y - fm.getAscent() - 5, width - 20, fm.getHeight() + 10);
            g2d.setColor(Color.WHITE);
            g2d.drawString("> " + chat.currentInput.toString() + "_", 15, y);
            y -= 40;
        }

        List<ChatSystem.ChatMessage> msgs = chat.getVisibleMessages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatSystem.ChatMessage msg = msgs.get(i);
            g2d.setColor(Color.BLACK);
            g2d.drawString(msg.text, 16, y + 1);
            g2d.setColor(Color.WHITE);
            g2d.drawString(msg.text, 15, y);
            y -= 25;
            if (y < 50) break;
        }

        if (chat.isSettingsOpen) {
            g2d.setColor(new Color(0, 0, 0, 210));
            g2d.fillRect(40, 40, width - 80, height - 80);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2d.drawString("SETTINGS", 65, 85);

            g2d.setFont(font);
            int ly = 125;
            int gap = 35;

            g2d.setColor(Color.CYAN);
            g2d.drawString("[Q] Shade Quality: " + getShadeString(com.miniv.core.Config.shadeQuality), 65, ly); ly += gap;

            g2d.setColor(Color.WHITE);
            g2d.drawString("[R] Render Distance: " + getRenderDistString(com.miniv.core.Config.renderDistance), 65, ly); ly += gap;

            g2d.drawString("[P] Chunk Load Mode: " + (com.miniv.core.Config.progressiveLoading ? "Progressive (smooth)" : "Simultaneous (stall)"), 65, ly); ly += gap;

            g2d.setColor(Color.ORANGE);
            g2d.drawString("[B] Place Block: " + com.miniv.core.Config.placeableNames[com.miniv.core.Config.activeBlockIndex], 65, ly); ly += gap;

            g2d.setColor(Color.WHITE);
            g2d.drawString("[↑/↓] World Height: " + com.miniv.core.Config.worldHeight
                + " blocks  (min " + com.miniv.core.Config.MIN_WORLD_HEIGHT
                + " / max " + com.miniv.core.Config.MAX_WORLD_HEIGHT + ")  Requires restart", 65, ly); ly += gap;

            g2d.drawString("Zoom: " + String.format("%.1f", com.miniv.core.Config.zoom)
                + " / Max: " + (int) com.miniv.core.Config.getMaxZoom() + "   (Hold C + Scroll)", 65, ly); ly += gap;

            g2d.drawString("World Seed: " + com.miniv.core.Config.worldSeed, 65, ly); ly += gap;

            g2d.setColor(Color.YELLOW);
            g2d.drawString("ESC  close settings", 65, ly + 10);
        }

        // Draw hotbar only when settings/chat is closed
        if (!chat.isSettingsOpen) {
            int numSlots = com.miniv.core.Config.placeableBlocks.length; // 6 slots
            int slotW = 52, slotH = 52, slotPad = 6;
            int hotbarW = numSlots * (slotW + slotPad) - slotPad;
            int hotbarX = (width - hotbarW) / 2;
            int hotbarY = height - slotH - 16;

            for (int i = 0; i < numSlots; i++) {
                int sx = hotbarX + i * (slotW + slotPad);
                boolean selected = (i == com.miniv.core.Config.activeBlockIndex);

                // Slot background
                g2d.setColor(selected ? new Color(255, 200, 50, 220) : new Color(0, 0, 0, 160));
                g2d.fillRoundRect(sx, hotbarY, slotW, slotH, 8, 8);

                // Slot border
                g2d.setColor(selected ? new Color(255, 230, 100) : new Color(180, 180, 180, 120));
                g2d.setStroke(new java.awt.BasicStroke(selected ? 2.5f : 1.5f));
                g2d.drawRoundRect(sx, hotbarY, slotW, slotH, 8, 8);
                g2d.setStroke(new java.awt.BasicStroke(1));

                // Slot number
                g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2d.setColor(selected ? Color.BLACK : new Color(200, 200, 200));
                g2d.drawString(String.valueOf(i + 1), sx + 4, hotbarY + 13);

                // Block name (abbreviated)
                String name = com.miniv.core.Config.placeableNames[i];
                String abbr = name.length() > 6 ? name.substring(0, 5) + "." : name;
                g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2d.setColor(selected ? Color.BLACK : Color.WHITE);
                FontMetrics fm2 = g2d.getFontMetrics();
                int tw = fm2.stringWidth(abbr);
                g2d.drawString(abbr, sx + (slotW - tw) / 2, hotbarY + slotH - 8);
            }

            // Active block label above hotbar
            g2d.setFont(font);
            String activeLabel = "[ " + com.miniv.core.Config.placeableNames[com.miniv.core.Config.activeBlockIndex] + " ]";
            g2d.setColor(new Color(0, 0, 0, 140));
            FontMetrics fmLabel = g2d.getFontMetrics();
            int lw = fmLabel.stringWidth(activeLabel);
            g2d.fillRoundRect((width - lw) / 2 - 6, hotbarY - 28, lw + 12, 22, 6, 6);
            g2d.setColor(new Color(255, 230, 100));
            g2d.drawString(activeLabel, (width - lw) / 2, hotbarY - 10);
        }

        g2d.dispose();

        int[] pixels = new int[width * height];
        img.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buffer);
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    private String getShadeString(int q) {
        switch(q) {
            case 0: return "Off";
            case 1: return "Low";
            case 2: return "Medium";
            case 3: return "High";
            case 4: return "Lock";
            default: return "Unknown";
        }
    }

    private String getRenderDistString(int rd) {
        switch(rd) {
            case 0: return "Low (8x8 chunks)";
            case 1: return "Medium (12x12 chunks)";
            case 2: return "High (20x20 chunks)";
            default: return "Unknown";
        }
    }
}
