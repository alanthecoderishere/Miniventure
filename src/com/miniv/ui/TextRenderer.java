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

import com.miniv.ui.UIManager;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;

public class TextRenderer {
    private int textureId;
    private int width = 800;
    private int height = 600;
    private Font font;

    public static class FloatingText {
        public String text;
        public int x, y;
        public FloatingText(String text, int x, int y) {
            this.text = text; this.x = x; this.y = y;
        }
    }

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

        font = UIFont.bold(19);
    }

    public void updateTexture(ChatSystem chat, List<FloatingText> floatingTexts, int screenWidth, int screenHeight) {
        if (screenWidth > 0 && screenHeight > 0 && (this.width != screenWidth || this.height != screenHeight)) {
            this.width = screenWidth;
            this.height = screenHeight;
            chat.dirty = true;
        }

        if (!chat.dirty && floatingTexts.isEmpty() && !UIManager.isDirty) return;
        chat.dirty = false;
        UIManager.isDirty = false;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        int y = HotbarLayout.chatBaseY(height);

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
            g2d.setColor(msg.shadowColor());
            g2d.drawString(msg.text, 16, y + 1);
            g2d.setColor(msg.textColor());
            g2d.drawString(msg.text, 15, y);
            y -= 27;
            if (y < 50) break;
        }

        if (chat.isSettingsOpen) {
            drawSettingsPanel(g2d, chat, width, height);
        }

        if (chat.showControlsHelp && !chat.isSettingsOpen && !chat.isTyping && !UIManager.hasActiveUI()) {
            drawControlsHelp(g2d, width);
        }

        // Draw hotbar only when settings/chat is closed
        if (!chat.isSettingsOpen && !UIManager.hasActiveUI()) {
            int numSlots = com.miniv.core.Config.HOTBAR_SIZE;
            int slotW = HotbarLayout.SLOT_W;
            int slotH = HotbarLayout.SLOT_H;
            int slotPad = HotbarLayout.SLOT_PAD;
            int hotbarX = HotbarLayout.hotbarX(width);
            int hotbarY = HotbarLayout.hotbarY(height);

            for (int i = 0; i < numSlots; i++) {
                int sx = hotbarX + i * (slotW + slotPad);
                boolean selected = (i == com.miniv.core.Config.activeBlockIndex);
                boolean hover = com.miniv.core.Config.hotbarSlots[i] == UIManager.hoveredBlockIndex;

                // Slot background
                g2d.setColor(selected ? new Color(255, 200, 50, 220)
                        : hover ? new Color(70, 90, 120, 200) : new Color(0, 0, 0, 160));
                g2d.fillRoundRect(sx, hotbarY, slotW, slotH, 8, 8);

                // Slot border
                g2d.setColor(selected ? new Color(255, 230, 100) : new Color(180, 180, 180, 120));
                g2d.setStroke(new java.awt.BasicStroke(selected ? 2.5f : 1.5f));
                g2d.drawRoundRect(sx, hotbarY, slotW, slotH, 8, 8);
                g2d.setStroke(new java.awt.BasicStroke(1));

                // Slot number
                g2d.setFont(UIFont.bold(12));
                g2d.setColor(selected ? Color.BLACK : new Color(200, 200, 200));
                g2d.drawString(String.valueOf(i + 1), sx + 4, hotbarY + 13);

                // Block name (abbreviated)
                int blockIdx = com.miniv.core.Config.hotbarSlots[i];
                String name = com.miniv.core.Config.placeableNames[blockIdx];
                String abbr = name.length() > 6 ? name.substring(0, 5) + "." : name;
                g2d.setFont(UIFont.bold(11));
                g2d.setColor(selected ? Color.BLACK : Color.WHITE);
                FontMetrics fm2 = g2d.getFontMetrics();
                int tw = fm2.stringWidth(abbr);
                g2d.drawString(abbr, sx + (slotW - tw) / 2, hotbarY + slotH - 8);
            }

            // Active block label above hotbar
            g2d.setFont(font);
            int activeBlockIdx = com.miniv.core.Config.hotbarSlots[com.miniv.core.Config.activeBlockIndex];
            String activeLabel = "[ " + com.miniv.core.Config.placeableNames[activeBlockIdx] + " ]";
            g2d.setColor(new Color(0, 0, 0, 140));
            FontMetrics fmLabel = g2d.getFontMetrics();
            int lw = fmLabel.stringWidth(activeLabel);
            g2d.fillRoundRect((width - lw) / 2 - 6, hotbarY - 28, lw + 12, 22, 6, 6);
            g2d.setColor(new Color(255, 230, 100));
            g2d.drawString(activeLabel, (width - lw) / 2, hotbarY - 10);

            g2d.setFont(UIFont.plain(12));
            g2d.setColor(new Color(200, 200, 200, 200));
            String invHint = "V = rotate cam  |  X = cam axis  |  C+scroll zoom  |  E inventory  |  H help  |  ESC settings";
            int hw = g2d.getFontMetrics().stringWidth(invHint);
            g2d.drawString(invHint, (width - hw) / 2, hotbarY + slotH + 14);

            if (UIManager.hoveredBlockIndex >= 0) {
                for (int i = 0; i < numSlots; i++) {
                    if (com.miniv.core.Config.hotbarSlots[i] == UIManager.hoveredBlockIndex) {
                        int cx = HotbarLayout.slotCenterX(i, width);
                        BlockTooltip.draw(g2d, UIManager.hoveredBlockIndex, cx, hotbarY, width, height);
                        break;
                    }
                }
            }
        }

        // Draw floating texts
        g2d.setFont(UIFont.bold(15));
        FontMetrics fmFloat = g2d.getFontMetrics();
        for (FloatingText ft : floatingTexts) {
            int tw = fmFloat.stringWidth(ft.text);
            int tx = ft.x - tw / 2;
            int ty = ft.y;
            
            // Draw background pill
            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRoundRect(tx - 4, ty - fmFloat.getAscent() - 2, tw + 8, fmFloat.getHeight() + 4, 8, 8);
            
            // Draw text
            g2d.setColor(Color.WHITE);
            g2d.drawString(ft.text, tx, ty);
        }

        if (UIManager.hasActiveUI()) {
            UIManager.getActiveUI().draw(g2d, width, height);
            if (UIManager.hoveredBlockIndex >= 0) {
                BlockTooltip.draw(g2d, UIManager.hoveredBlockIndex,
                        UIManager.mouseX, UIManager.mouseY, width, height);
            }
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

    private void drawControlsHelp(Graphics2D g, int width) {
        int boxW = Math.min(380, width - 24);
        int bx = width - boxW - 12;
        int by = 12;

        g.setFont(UIFont.plain(13));
        String[] lines = {
            "Move: W A S D     Jump: Space",
            "Camera rotate: V     Switch axis: X",
            "Zoom: hold C + mouse wheel",
            "Break block: Left-click",
            "Place block: Right-click",
            "Hotbar slots: Keys 1 - 9",
            "More blocks: E (inventory)",
            "Chat with class: T",
            "Help panel: H     Settings: ESC",
            "NPC / Quiz / Info / Teleport / Attendance: Right-click",
            "NPC editor (before lock): Shift + right-click",
            "Info sign read-only: Shift + right-click",
            "Remove NPC: Left-click near NPC",
            "Hover hotbar slot for block details",
            "In settings: Q shadows, L brightness, R view, P loading",
            "In settings: B cycle block, Up/Down island height",
        };
        int boxH = 42 + lines.length * 18 + 14;

        g.setColor(new Color(15, 20, 35, 200));
        g.fillRoundRect(bx, by, boxW, boxH, 10, 10);
        g.setColor(new Color(100, 180, 255, 180));
        g.drawRoundRect(bx, by, boxW, boxH, 10, 10);

        g.setFont(UIFont.bold(15));
        g.setColor(new Color(255, 230, 120));
        g.drawString("How to play  (H to hide)", bx + 14, by + 22);

        g.setFont(UIFont.plain(13));
        g.setColor(new Color(230, 235, 245));
        int ly = by + 42;
        for (String line : lines) {
            g.drawString(line, bx + 14, ly);
            ly += 18;
        }
    }

    private void drawSettingsPanel(Graphics2D g, ChatSystem chat, int width, int height) {
        g.setColor(new Color(0, 0, 0, 210));
        g.fillRect(40, 40, width - 80, height - 80);
        g.setColor(Color.WHITE);
        g.setFont(UIFont.bold(23));
        g.drawString("Settings", 65, 85);

        g.setFont(UIFont.plain(14));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Change options with the keys shown. Press ESC when done.", 65, 108);

        g.setFont(font);
        int ly = 135;
        int gap = 32;

        g.setColor(new Color(150, 220, 255));
        g.drawString("— Graphics —", 65, ly); ly += gap;

        g.setColor(Color.WHITE);
        String fpsStr = com.miniv.core.Config.getFpsCap() == -1 ? "VSync" : 
                        (com.miniv.core.Config.getFpsCap() == 0 ? "Unlimited" : com.miniv.core.Config.getFpsCap() + " FPS");
        g.drawString("[F] FPS Cap: " + fpsStr, 65, ly); ly += gap;
        g.drawString("[Q] Shadows: " + getShadeString(com.miniv.core.Config.shadeQuality), 65, ly); ly += gap;
        g.drawString("[L] Night brightness: " + (int) (com.miniv.core.Config.brightness * 100)
            + "% (still darker at night)", 65, ly); ly += gap;
        g.drawString("[R] View distance: " + getRenderDistString(com.miniv.core.Config.renderDistance), 65, ly); ly += gap;
        g.drawString("[P] World loading: " + (com.miniv.core.Config.progressiveLoading
            ? "Smooth (recommended)" : "Load all at once"), 65, ly); ly += gap;
        g.drawString("Zoom: hold C + scroll  (" + String.format("%.1f", com.miniv.core.Config.zoom)
            + " / max " + (int) com.miniv.core.Config.getMaxZoom() + ")", 65, ly); ly += gap;

        g.setColor(new Color(150, 220, 255));
        g.drawString("— Building —", 65, ly); ly += gap;

        g.setColor(Color.ORANGE);
        g.drawString("[B] Block to place: " + com.miniv.core.Config.placeableNames[com.miniv.core.Config.activeBlockIndex], 65, ly); ly += gap;

        g.setColor(Color.WHITE);
        g.drawString("[↑/↓] Island height: " + com.miniv.core.Config.worldHeight
            + " blocks (restart game to apply)", 65, ly); ly += gap;

        g.setColor(Color.YELLOW);
        g.drawString("ESC = close", 65, ly + 12);
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
