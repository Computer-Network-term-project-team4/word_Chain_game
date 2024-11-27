package word_chain.gameLogic;

public class GameScore {
    private DictionaryAPI dictionaryAPI;
    private WordTracker wordTracker;
    private ScoreCalculator scoreCalculator;
    private IsSame isSame;

    public GameScore() {
        dictionaryAPI = new DictionaryAPI();
        wordTracker = new WordTracker();
        scoreCalculator = new ScoreCalculator();
        isSame = new IsSame();
    }

    public String processFirstWord(String word, char startingLetter) {
        if (word == null || word.isEmpty() || word.charAt(0) != startingLetter) {
            return "첫 번째 단어는 '" + startingLetter + "'로 시작해야 합니다.";
        }
        if (!dictionaryAPI.isValidWord(word)) {
            return "유효하지 않은 단어입니다.";
        }
        if (!wordTracker.addWord(word)) {
            return "이미 사용된 단어입니다.";
        }

        int score = scoreCalculator.calculateScore(word);
        return "유효한 단어입니다! 점수: " + score;
    }

    public String processNextWord(String word, String lastWord) {
        if (word == null || word.isEmpty()) {
            return "단어를 입력해야 합니다.";
        }
        if (!isSame.checkLastFirst(lastWord, word)) {
            return "단어는 '" + lastWord.charAt(lastWord.length() - 1) + "'로 시작해야 합니다.";
        }
        if (!dictionaryAPI.isValidWord(word)) {
            return "유효하지 않은 단어입니다.";
        }
        if (!wordTracker.addWord(word)) {
            return "이미 사용된 단어입니다.";
        }

        int score = scoreCalculator.calculateScore(word);
        return "유효한 단어입니다! 점수: " + score;
    }
}
