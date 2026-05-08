package com.miniv.ui;

import java.util.ArrayList;
import java.util.List;

public class ChatSystem {
    public static class ChatMessage {
        public String text;
        public long timestamp;

        public ChatMessage(String text) {
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private List<ChatMessage> history = new ArrayList<>();
    public boolean isTyping = false;
    public boolean isSettingsOpen = false;
    public StringBuilder currentInput = new StringBuilder();
    public boolean dirty = true;

    // Check if we need to redraw because messages expired
    public void updateTimers() {
        if (!isTyping) {
            long now = System.currentTimeMillis();
            for (ChatMessage msg : history) {
                if (now - msg.timestamp > 30000 && now - msg.timestamp < 30500) {
                    dirty = true; // Briefly dirty when one expires
                }
            }
        }
    }

    public void addMessage(String msg) {
        history.add(new ChatMessage(msg));
        dirty = true;
    }

    public List<ChatMessage> getVisibleMessages() {
        if (isTyping) {
            return history;
        }
        long now = System.currentTimeMillis();
        List<ChatMessage> visible = new ArrayList<>();
        for (ChatMessage msg : history) {
            if (now - msg.timestamp <= 30000) {
                visible.add(msg);
            }
        }
        return visible;
    }
}
