package com.miniv.core;

public class QuizData {
    public String question = "";
    public String[] options = new String[]{"", "", "", ""};
    public int correctIndex = 0;
    public boolean isLocked = false;
    
    // Store block coordinates for reference during broadcast
    public int bx, by, bz;
    
    public QuizData(int x, int y, int z) {
        this.bx = x; this.by = y; this.bz = z;
    }
}
