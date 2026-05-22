package com.miniv.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ChatSystem {
    public enum Kind {
        PLAYER,
        SYSTEM,
        GUIDE
    }

    public static class ChatMessage {
        public final String text;
        public final long timestamp;
        public final long ttlMs;
        public final Kind kind;

        public ChatMessage(String text, long ttlMs, Kind kind) {
            this.text = text;
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
            this.kind = kind;
        }

        public Color textColor() {
            switch (kind) {
                case PLAYER: return new Color(255, 255, 255);
                case SYSTEM: return new Color(120, 220, 255);
                case GUIDE:    return new Color(255, 215, 100);
                default:       return Color.WHITE;
            }
        }

        public Color shadowColor() {
            switch (kind) {
                case PLAYER: return new Color(0, 0, 0);
                case SYSTEM: return new Color(10, 40, 60);
                case GUIDE:    return new Color(50, 35, 0);
                default:       return Color.BLACK;
            }
        }
    }

    /** How long each chat line stays visible (all types). */
    private static final long CHAT_TTL_MS = 30_000;

    private final List<ChatMessage> history = new ArrayList<>();
    public boolean isTyping = false;
    public boolean isSettingsOpen = false;
    public boolean showControlsHelp = true;
    public StringBuilder currentInput = new StringBuilder();
    public boolean dirty = true;

    public void updateTimers() {
        if (!isTyping) {
            long now = System.currentTimeMillis();
            for (ChatMessage msg : history) {
                long age = now - msg.timestamp;
                if (age > msg.ttlMs && age < msg.ttlMs + 500) {
                    dirty = true;
                }
            }
        }
    }

    public void addPlayerMessage(String msg) {
        history.add(new ChatMessage(msg, CHAT_TTL_MS, Kind.PLAYER));
        dirty = true;
    }

    public void addSystemMessage(String msg) {
        history.add(new ChatMessage(msg, CHAT_TTL_MS, Kind.SYSTEM));
        dirty = true;
    }

    public void addGuideMessage(String msg) {
        history.add(new ChatMessage(msg, CHAT_TTL_MS, Kind.GUIDE));
        dirty = true;
    }

    public void toggleHelp() {
        showControlsHelp = !showControlsHelp;
        dirty = true;
    }

    public List<ChatMessage> getVisibleMessages() {
        if (isTyping) {
            return history;
        }
        long now = System.currentTimeMillis();
        List<ChatMessage> visible = new ArrayList<>();
        for (ChatMessage msg : history) {
            if (now - msg.timestamp <= msg.ttlMs) {
                visible.add(msg);
            }
        }
        return visible;
    }
}
