package word_chain.gameLogic;

public class ScoreCalculator {
    public int calculateScore(String word) {
        // 모든 글자를 소문자로 변환
        word = word.toLowerCase();

        // 기본 점수는 글자의 길이로 점수 얻음
        int baseScore = word.length();

        // 추가 점수: 길이를 5로 나눈 몫을 점수에 추가
        baseScore += word.length() / 5;

        return baseScore;
    }
}
