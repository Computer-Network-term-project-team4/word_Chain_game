package word_chain.gameLogic;

import java.io.*;
import java.net.*;
import java.util.*;

public class DictionaryAPI {
    private static final String API_URL = "https://wordsapiv1.p.rapidapi.com/words/";
    private static final String API_KEY = "f6898f4349msh3cb249fb06e2fd7p1929d9jsn9304e0243449"; // WordsAPI API 키 입력
    private Map<String, Boolean> wordCache;

    public DictionaryAPI() {
        wordCache = new HashMap<>();
    }

    public boolean isValidWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false; // 비어있는 입력 처리
        }

        word = word.trim().toLowerCase(); // 입력 정리

        if (wordCache.containsKey(word)) {
            return wordCache.get(word); // 캐시된 결과 반환
        }

        try {
            URL url = new URL(API_URL + word);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-RapidAPI-Host", "wordsapiv1.p.rapidapi.com");
            connection.setRequestProperty("X-RapidAPI-Key", API_KEY);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) { // HTTP 200 (성공)
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                connection.disconnect();

                // 단어 유효성 확인
                boolean isValid = content.toString().contains("\"word\"");
                wordCache.put(word, isValid); // 결과 캐싱
                return isValid;
            }
        } catch (IOException e) {
            // 예외 발생 시 아무 것도 출력하지 않고 false 반환
        }

        return false; // 오류 발생 또는 응답 실패 시 false 반환
    }
}
