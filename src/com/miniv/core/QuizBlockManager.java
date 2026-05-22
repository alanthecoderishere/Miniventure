package com.miniv.core;

import com.miniv.ui.UIManager;
import com.miniv.ui.QuizUI;
import java.util.HashMap;

public class QuizBlockManager {
    private static HashMap<String, QuizData> quizzes = new HashMap<>();

    private static String getKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static void interact(int x, int y, int z) {
        String key = getKey(x, y, z);
        QuizData data = quizzes.get(key);
        
        // If it doesn't exist, create it (newly placed block)
        if (data == null) {
            data = new QuizData(x, y, z);
            quizzes.put(key, data);
        }

        // Open UI
        UIManager.setActiveUI(new QuizUI(data));
    }

    public static void removeQuiz(int x, int y, int z) {
        quizzes.remove(getKey(x, y, z));
    }
}
