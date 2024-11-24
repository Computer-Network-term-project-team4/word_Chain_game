package Test1;


import java.util.LinkedHashSet; //순서를 바꾸지 않기 위해

public class WordTrackerTest {
 private LinkedHashSet<String> usedWords;

 public WordTrackerTest() {
     usedWords = new LinkedHashSet<>();
 }

 public boolean addWord(String word) {
     if (usedWords.contains(word.toLowerCase())) {
         return false; // 중복 단어
     }
     usedWords.add(word.toLowerCase());
     return true;
 }

 public LinkedHashSet<String> getUsedWords() {
     return usedWords; // 사용된 단어 반환
 }
}
