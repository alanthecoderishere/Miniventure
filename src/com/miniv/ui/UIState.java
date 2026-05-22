package com.miniv.ui;

import java.awt.Graphics2D;

public interface UIState {
    void draw(Graphics2D g, int width, int height);
    void handleClick(int mouseX, int mouseY, int button, int width, int height);
    void handleKey(int key, int action);
    void handleChar(int codepoint);
}
