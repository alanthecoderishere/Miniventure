package com.miniv.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Set;

/**
 * Screen-friendly UI fonts (prefers Verdana / Tahoma over generic SansSerif).
 */
public final class UIFont {
    private static final String[] PREFERRED = {
            "Verdana", "Tahoma", "Segoe UI", "Arial", "Helvetica Neue", "Lucida Grande", "Dialog"
    };

    private static final String FAMILY = resolveFamily();

    private UIFont() {}

    private static String resolveFamily() {
        Set<String> available = new HashSet<>();
        for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            available.add(name);
        }
        for (String pref : PREFERRED) {
            if (available.contains(pref)) {
                return pref;
            }
        }
        return Font.SANS_SERIF;
    }

    public static String family() {
        return FAMILY;
    }

    public static Font plain(int size) {
        return new Font(FAMILY, Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return new Font(FAMILY, Font.BOLD, size);
    }
}
