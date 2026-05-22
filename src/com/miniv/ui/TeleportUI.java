package com.miniv.ui;

import com.miniv.core.Player;
import com.miniv.core.TeleportBlockManager;
import com.miniv.core.TeleportBlockManager.TeleportTarget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class TeleportUI implements UIState {
    private final int bx, by, bz;
    private final TeleportTarget target;
    private final Player player;
    private int activeField = 0; // 0=X, 1=Y, 2=Z
    private final String[] values = new String[3];

    public TeleportUI(int bx, int by, int bz, TeleportTarget target, Player player) {
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.target = target;
        this.player = player;
        values[0] = fmt(target.tx);
        values[1] = fmt(target.ty);
        values[2] = fmt(target.tz);
    }

    private static String fmt(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    private void applyToTarget() {
        try {
            target.tx = Float.parseFloat(values[0].isEmpty() ? "0" : values[0]);
            target.ty = Float.parseFloat(values[1].isEmpty() ? "0" : values[1]);
            target.tz = Float.parseFloat(values[2].isEmpty() ? "0" : values[2]);
        } catch (NumberFormatException ignored) {
        }
    }

    private void save() {
        applyToTarget();
        TeleportBlockManager.setTeleport(bx, by, bz, target.tx, target.ty, target.tz);
        UIManager.closeUI();
    }

    @Override
    public void draw(Graphics2D g, int width, int height) {
        int boxW = 460;
        int boxH = 320;
        int x0 = (width - boxW) / 2;
        int y0 = (height - boxH) / 2;

        g.setColor(new Color(25, 35, 55, 240));
        g.fillRoundRect(x0, y0, boxW, boxH, 12, 12);
        g.setColor(new Color(100, 160, 255));
        g.drawRoundRect(x0, y0, boxW, boxH, 12, 12);

        g.setFont(UIFont.bold(20));
        g.setColor(Color.WHITE);
        g.drawString("Teleport Pad Setup", x0 + 115, y0 + 36);

        g.setFont(UIFont.plain(14));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Where should players go when they step on this pad?", x0 + 30, y0 + 58);
        g.drawString("Pad location: " + bx + ", " + by + ", " + bz, x0 + 30, y0 + 76);

        String[] labels = {"Dest X:", "Dest Y:", "Dest Z:"};
        for (int i = 0; i < 3; i++) {
            int fy = y0 + 100 + i * 45;
            boolean active = activeField == i;
            g.setColor(active ? Color.YELLOW : Color.LIGHT_GRAY);
            g.drawString(labels[i], x0 + 30, fy);
            g.setColor(active ? Color.WHITE : new Color(200, 200, 210));
            g.drawString(values[i] + (active ? "_" : ""), x0 + 120, fy);
        }

        g.setColor(new Color(180, 220, 255));
        g.setFont(UIFont.plain(12));
        g.drawString("Click X, Y, or Z and type coordinates.", x0 + 30, y0 + 248);
        g.drawString("P = use where you are standing now.", x0 + 30, y0 + 266);
        g.drawString("ENTER = save for everyone.  ESC = close.", x0 + 30, y0 + 284);
        g.drawString("Anyone can walk on the pad to teleport.", x0 + 30, y0 + 302);
    }

    @Override
    public void handleClick(int mouseX, int mouseY, int button, int width, int height) {
        if (button != 0) return;
        int boxW = 460;
        int boxH = 320;
        int x0 = (width - boxW) / 2;
        int y0 = (height - boxH) / 2;
        for (int i = 0; i < 3; i++) {
            int fy = y0 + 88 + i * 45;
            if (mouseX >= x0 + 25 && mouseX <= x0 + boxW - 25
                    && mouseY >= fy && mouseY <= fy + 32) {
                activeField = i;
                UIManager.isDirty = true;
                return;
            }
        }
    }

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
        if (key == 258) { // TAB
            activeField = (activeField + 1) % 3;
            UIManager.isDirty = true;
            return;
        }
        if (key == 80 && player != null) { // P — destination = current player position
            values[0] = fmt(player.position.x + 0.5f);
            values[1] = fmt(player.position.y);
            values[2] = fmt(player.position.z + 0.5f);
            UIManager.isDirty = true;
            return;
        }
        if (key == 259) { // BACKSPACE
            if (!values[activeField].isEmpty()) {
                values[activeField] = values[activeField].substring(0, values[activeField].length() - 1);
                UIManager.isDirty = true;
            }
        }
    }

    @Override
    public void handleChar(int codepoint) {
        char c = (char) codepoint;
        if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
            if (c == '-' && !values[activeField].isEmpty()) return;
            if (c == '.' && values[activeField].contains(".")) return;
            values[activeField] += c;
            UIManager.isDirty = true;
        }
    }
}
