package com.miniv.ui;

import com.miniv.core.QuizData;
import com.miniv.core.SupabaseMultiplayer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class QuizUI implements UIState {
    private static final int BOX_W = 500;
    private static final int BOX_H = 400;
    private static final int OPT_LEFT = 30;
    private static final int OPT_W = BOX_W - 60;
    private static final int OPT_H = 35;
    private static final int OPT_TOP = 160;
    private static final int OPT_STEP = 45;

    private final QuizData data;
    private int selectedOption = -1;
    private int activeField = 0; // 0=Q, 1-4=Opt, 5=CorrectIndex

    public QuizUI(QuizData data) {
        this.data = data;
    }

    private int boxX(int width) { return (width - BOX_W) / 2; }
    private int boxY(int height) { return (height - BOX_H) / 2; }

    @Override
    public void draw(Graphics2D g, int width, int height) {
        int bx = boxX(width);
        int by = boxY(height);

        g.setColor(new Color(30, 30, 40, 230));
        g.fillRoundRect(bx, by, BOX_W, BOX_H, 15, 15);
        g.setColor(Color.WHITE);
        g.drawRoundRect(bx, by, BOX_W, BOX_H, 15, 15);

        g.setFont(UIFont.bold(20));

        if (data.isLocked) {
            g.drawString("Quiz", bx + 220, by + 40);

            g.setFont(UIFont.plain(18));
            g.setColor(Color.CYAN);
            drawStringWrapped(g, data.question.isEmpty() ? "No question set." : data.question,
                    bx + OPT_LEFT, by + 80, OPT_W);

            for (int i = 0; i < 4; i++) {
                int rx = bx + OPT_LEFT;
                int ry = by + OPT_TOP + i * OPT_STEP - 20;
                boolean sel = selectedOption == i;
                if (sel) {
                    g.setColor(new Color(100, 200, 100));
                    g.fillRoundRect(rx, ry, OPT_W, OPT_H, 6, 6);
                    g.setColor(Color.BLACK);
                } else {
                    g.setColor(new Color(50, 50, 65));
                    g.fillRoundRect(rx, ry, OPT_W, OPT_H, 6, 6);
                    g.setColor(Color.WHITE);
                    g.drawRoundRect(rx, ry, OPT_W, OPT_H, 6, 6);
                }
                g.setFont(UIFont.plain(16));
                String opt = data.options[i].isEmpty() ? "(empty)" : data.options[i];
                g.drawString((char) ('A' + i) + ". " + opt, rx + 10, ry + 24);
            }

            g.setColor(Color.YELLOW);
            g.setFont(UIFont.plain(13));
            g.drawString("Pick your answer: click it or press 1, 2, 3, or 4.", bx + OPT_LEFT, by + 365);
            g.drawString("ESC to close.", bx + OPT_LEFT, by + 385);
        } else {
            g.drawString("Create a Quiz (Teacher)", bx + 130, by + 40);
            g.setFont(UIFont.plain(16));

            drawField(g, "Question:", data.question, bx + OPT_LEFT, by + 80, activeField == 0);
            for (int i = 0; i < 4; i++) {
                drawField(g, "Option " + (i + 1) + ":", data.options[i],
                        bx + OPT_LEFT, by + 140 + i * 40, activeField == i + 1);
            }
            drawField(g, "Correct (1-4):", String.valueOf(data.correctIndex + 1),
                    bx + OPT_LEFT, by + 310, activeField == 5);

            g.setColor(Color.GREEN);
            g.setFont(UIFont.plain(13));
            g.drawString("1) Click a row and type.  2) Click Correct row, press 1-4 for right answer.", bx + OPT_LEFT, by + 345);
            g.drawString("TAB = next field.  ENTER = save for students.  ESC = cancel.", bx + OPT_LEFT, by + 365);
        }
    }

    private void drawField(Graphics2D g, String label, String val, int x, int y, boolean isActive) {
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(label, x, y);
        g.setColor(isActive ? Color.YELLOW : Color.WHITE);
        String text = val.isEmpty() ? "(click here, then type)" : val;
        g.drawString(text + (isActive ? "_" : ""), x + 150, y);
    }

    private void drawStringWrapped(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        String line = "";
        for (String word : words) {
            if (fm.stringWidth(line + word) > maxWidth) {
                g.drawString(line.trim(), x, y);
                line = "";
                y += fm.getHeight();
            }
            line += word + " ";
        }
        if (!line.trim().isEmpty()) {
            g.drawString(line.trim(), x, y);
        }
    }

    @Override
    public void handleClick(int mouseX, int mouseY, int button, int width, int height) {
        if (button != 0) return;

        int bx = boxX(width);
        int by = boxY(height);

        if (data.isLocked) {
            for (int i = 0; i < 4; i++) {
                int rx = bx + OPT_LEFT;
                int ry = by + OPT_TOP + i * OPT_STEP - 20;
                if (hit(mouseX, mouseY, rx, ry, OPT_W, OPT_H)) {
                    submitAnswer(i);
                    return;
                }
            }
        } else {
            if (hit(mouseX, mouseY, bx + OPT_LEFT, by + 68, OPT_W, 28)) {
                activeField = 0;
                UIManager.isDirty = true;
                return;
            }
            for (int i = 0; i < 4; i++) {
                if (hit(mouseX, mouseY, bx + OPT_LEFT, by + 128 + i * 40, OPT_W, 28)) {
                    activeField = i + 1;
                    UIManager.isDirty = true;
                    return;
                }
            }
            if (hit(mouseX, mouseY, bx + OPT_LEFT, by + 298, OPT_W, 28)) {
                activeField = 5;
                UIManager.isDirty = true;
            }
        }
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void submitAnswer(int option) {
        selectedOption = option;
        UIManager.isDirty = true;
        if (option == data.correctIndex) {
            SupabaseMultiplayer.broadcastAction("quiz_correct",
                    data.bx + "," + data.by + "," + data.bz);
            UIManager.closeUI();
        } else {
            selectedOption = -1;
        }
    }

    @Override
    public void handleKey(int key, int action) {
        if (action != 1) return;

        if (key == 256) { // ESC
            UIManager.closeUI();
            return;
        }

        if (data.isLocked) {
            if (key >= 49 && key <= 52) { // 1-4
                submitAnswer(key - 49);
            }
        } else {
            // Teacher edit: 1-4 only sets correct answer when that field is selected
            if (activeField == 5 && key >= 49 && key <= 52) {
                data.correctIndex = key - 49;
                UIManager.isDirty = true;
                return;
            }

            if (key == 257 || key == 335) { // ENTER
                data.isLocked = true;
                com.miniv.core.QuizBlockManager.saveQuizToDB(data);
                UIManager.isDirty = true;
                return;
            }

            if (key == 258) { // TAB — cycle fields (1-4 are free for typing in options)
                activeField = (activeField + 1) % 6;
                UIManager.isDirty = true;
                return;
            }

            if (key == 259) { // BACKSPACE
                if (activeField == 0 && !data.question.isEmpty()) {
                    data.question = data.question.substring(0, data.question.length() - 1);
                } else if (activeField >= 1 && activeField <= 4) {
                    String opt = data.options[activeField - 1];
                    if (!opt.isEmpty()) {
                        data.options[activeField - 1] = opt.substring(0, opt.length() - 1);
                    }
                }
                UIManager.isDirty = true;
            }
        }
    }

    @Override
    public void handleChar(int codepoint) {
        if (data.isLocked) return;

        if (activeField == 5 && codepoint >= '1' && codepoint <= '4') {
            data.correctIndex = codepoint - '1';
        } else if (activeField == 0) {
            data.question += (char) codepoint;
        } else if (activeField >= 1 && activeField <= 4) {
            data.options[activeField - 1] += (char) codepoint;
        }
        UIManager.isDirty = true;
    }
}
