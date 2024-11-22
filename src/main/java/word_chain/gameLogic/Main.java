package word_chain.gameLogic;

public class Main {
    public static void main(String[] args) {
        DictionaryAPI dictionaryAPI = new DictionaryAPI();

        String[] testWords = {"apple", "banana", "zzzzz", "grape"};
        for (String word : testWords) {
            System.out.println("Word: " + word + ", Valid: " + dictionaryAPI.isValidWord(word));
        }
    }

    // test DictionaryAPI
}
