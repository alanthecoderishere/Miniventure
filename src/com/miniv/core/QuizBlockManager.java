package com.miniv.core;

import com.miniv.ui.UIManager;
import com.miniv.ui.QuizUI;
import java.util.HashMap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static void fetchQuizzesFromDB() {
        String url = "https://" + SupabaseMultiplayer.SUPABASE_PROJECT_REF + ".supabase.co/rest/v1/quiz_blocks";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SupabaseMultiplayer.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseMultiplayer.SUPABASE_ANON_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();
                
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenAccept(response -> {
                  if (response.statusCode() == 200) {
                      parseQuizzesJson(response.body());
                  } else {
                      System.err.println("[Quiz] Failed to fetch quizzes: " + response.statusCode());
                  }
              });
    }

    private static void parseQuizzesJson(String json) {
        Pattern p = Pattern.compile("\\{([^}]+)\\}");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String obj = m.group(1);
            String id = extractString(obj, "block_id");
            if (id == null) continue;
            
            String[] parts = id.split(",");
            if (parts.length != 3) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                
                QuizData data = new QuizData(x, y, z);
                data.question = extractString(obj, "question");
                if (data.question == null) data.question = "";
                
                String optsStr = extractString(obj, "options");
                if (optsStr != null) {
                    String[] opts = optsStr.split("\\|");
                    for (int i = 0; i < 4 && i < opts.length; i++) {
                        data.options[i] = opts[i];
                    }
                }
                
                String cIdxStr = extractPrimitive(obj, "correct_index");
                if (cIdxStr != null) data.correctIndex = (int) Double.parseDouble(cIdxStr);
                
                String locStr = extractPrimitive(obj, "locked");
                if (locStr != null) data.isLocked = locStr.contains("true");
                
                quizzes.put(id, data);
            } catch (Exception e) {
                // ignore
            }
        }
    }
    
    public static void saveQuizToDB(QuizData data) {
        String url = "https://" + SupabaseMultiplayer.SUPABASE_PROJECT_REF + ".supabase.co/rest/v1/quiz_blocks";
        HttpClient client = HttpClient.newHttpClient();
        
        String opts = String.join("|", data.options);
        
        // Escape quotes
        String q = data.question.replace("\"", "\\\"");
        String o = opts.replace("\"", "\\\"");
        
        String json = "{"
            + "\"block_id\":\"" + getKey(data.bx, data.by, data.bz) + "\","
            + "\"question\":\"" + q + "\","
            + "\"options\":\"" + o + "\","
            + "\"correct_index\":" + data.correctIndex + ","
            + "\"locked\":" + data.isLocked
            + "}";
            
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", SupabaseMultiplayer.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseMultiplayer.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
                
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenAccept(response -> {
                  if (response.statusCode() >= 200 && response.statusCode() < 300) {
                      System.out.println("[Quiz] Saved to DB: " + getKey(data.bx, data.by, data.bz));
                  } else {
                      System.err.println("[Quiz] Failed to save quiz: " + response.statusCode() + " " + response.body());
                  }
              });
    }
    
    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }
    
    private static String extractPrimitive(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([^,\\}]+)");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1).trim();
        return null;
    }
}
