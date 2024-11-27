package word_chain.gameLogic;

public class IsSame {
    public boolean checkLastFirst(String lastWord, String currentWord) {
        return lastWord.charAt(lastWord.length() - 1) == currentWord.charAt(0);
    }
}
