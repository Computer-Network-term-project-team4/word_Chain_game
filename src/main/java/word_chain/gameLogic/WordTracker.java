package word_chain.gameLogic;

import java.util.LinkedHashSet; // 중복 적용 불가, 순서 바꾸지 않음

public class WordTracker {
	private LinkedHashSet<String> usedWords;
	
	public WordTracker(){
		usedWords= new LinkedHashSet<>();
	}
	
	public boolean addWord(String word) {
		if (word == null || word.trim().isEmpty()) {
            return false; // 빈 문자열 또는 null 적용 불가
        }
		
		if(usedWords.contains(word.toLowerCase())) {
			return false; //중복일때, 적용불가
		}
		usedWords.add(word.toLowerCase());
		return true; // 중복이 아닐때, 적용
	}
	
	public LinkedHashSet<String> getUsedWords(){
		return usedWords; // 사용된 단어 반환
	}

}
