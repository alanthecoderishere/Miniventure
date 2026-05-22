package com.miniv.core;

import com.miniv.ui.ChatSystem;

public class AttendanceBlockManager {
    public static void interact(ChatSystem chat) {
        // Log attendance using the standard broadcast pattern but with a custom "attendance" type
        String localName = SupabaseMultiplayer.localName;
        SupabaseMultiplayer.broadcastAction("attendance", "hadir");
        
        if (chat != null) {
            chat.addSystemMessage("Attendance recorded — you are marked present!");
            chat.dirty = true;
        }
    }
}
