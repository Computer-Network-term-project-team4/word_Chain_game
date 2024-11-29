package word_chain.server;

import word_chain.gameLogic.GameScore;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server_test 클래스
 * - 끝말잇기 게임의 서버 역할을 수행
 * - 클라이언트 관리, 게임 진행, 제한 시간 관리, 점수 계산 등의 주요 로직 포함
 */
public class Server_test {
    // 서버 포트 번호
    private static final int PORT = 12345;

    // 최대 플레이어 수
    private static final int MAX_PLAYERS = 4;

    // 최대 턴 수 (게임 종료 조건)
    private static final int MAX_TURNS = 5;

    // 각 턴의 제한 시간 (초)
    private static final int TURN_TIME_LIMIT = 30;

    // 클라이언트 목록 (현재 연결된 클라이언트 핸들러)
    private final List<ClientHandler_test> clients = new ArrayList<>();

    // 게임 점수 관리 객체
    private final GameScore gameScore = new GameScore();

    // 서버에서 제한 시간 타이머를 관리하기 위한 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 서버 실행 상태 플래그
    private boolean isRunning = true;

    // 게임 진행 상태 플래그
    private boolean gameStarted = false;

    // 현재 턴의 플레이어 인덱스
    private int currentPlayerIndex = 0;

    // 남은 턴 수
    private int remainingTurns = MAX_TURNS;

    // 남은 시간 관리 (AtomicInteger 사용)
    private AtomicInteger remainingTime = new AtomicInteger(TURN_TIME_LIMIT);

    // 마지막 단어 (게임 진행 중 유지됨)
    private String lastWord = null;

    // 첫 단어의 시작 알파벳
    private char startingLetter;

    // 제한 시간 타이머 작업 추적
    private ScheduledFuture<?> timeUpdateTask;

    /**
     * 서버의 메인 메서드
     * - 서버 실행
     */
    public static void main(String[] args) {
        new Server_test().startServer();
    }

    /**
     * 모든 클라이언트에게 메시지 전송
     * @param message 전송할 메시지
     */
    public synchronized void broadcast(String message) {
        for (ClientHandler_test client : clients) {
            client.sendMessage(message);
        }
    }

    /**
     * 클라이언트를 서버에서 제거
     * @param client 제거할 클라이언트 핸들러
     */
    public synchronized void removeClient(ClientHandler_test client) {
        int removedIndex = clients.indexOf(client);
        clients.remove(client); // 리스트에서 클라이언트 제거

        // 해당 클라이언트에게도 메시지 전송
        client.sendMessage("당신은 게임에서 나갔습니다.");

        broadcast(client.getNickname() + " 님이 나갔습니다."); // 클라이언트 퇴장 메시지 브로드캐스트
        System.out.println("클라이언트 연결 종료: 닉네임 = " + client.getNickname());

        // 모든 클라이언트가 나간 경우 게임 종료
        if (clients.isEmpty()) {
            endGame();
        } else {
            // 현재 턴의 플레이어가 나간 경우 다음 플레이어로 이동
            if (removedIndex == currentPlayerIndex) {
                currentPlayerIndex = currentPlayerIndex % clients.size();
                nextTurn();
            } else if (removedIndex < currentPlayerIndex) {
                currentPlayerIndex--; // 인덱스 조정
            }

            // 남은 턴 수 업데이트 및 클라이언트에게 전송
            sendGameStatus();
        }
    }

    /**
     * 게임 시작
     * - 모든 클라이언트가 준비되었을 때 호출
     */
    public synchronized void startGame() {
        if (gameStarted) return; // 이미 시작된 경우 실행하지 않음
        gameStarted = true; // 게임 시작 상태 설정

        // 5초 카운트다운
        for (int countdown = 5; countdown > 0; countdown--) {
            broadcast("게임이 " + countdown + "초 후에 시작됩니다!");
            try {
                Thread.sleep(1000); // 1초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("카운트다운 중 인터럽트 발생: " + e.getMessage());
            }
        }

        // 랜덤으로 첫 단어의 시작 알파벳 생성
        startingLetter = (char) ('a' + new Random().nextInt(26));
        broadcast("첫 번째 단어는 '" + startingLetter + "'로 시작해야 합니다.");
        nextTurn(); // 첫 번째 턴 시작
    }

    /**
     * 다음 턴으로 진행
     */
    private synchronized void nextTurn() {
        // 이전 타이머 취소
        if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
            timeUpdateTask.cancel(true);
        }

        if (remainingTurns == 0) {
            endGame();
            return;
        }

        if (clients.isEmpty()) {
            broadcast("모든 플레이어가 나갔습니다. 게임을 종료합니다.");
            endGame();
            return;
        }

        // 남은 시간을 초기화
        remainingTime.set(TURN_TIME_LIMIT);

        // 현재 턴의 플레이어
        ClientHandler_test currentPlayer = clients.get(currentPlayerIndex);

        // 현재 턴의 정보 브로드캐스트
        broadcast("현재 차례: " + currentPlayer.getNickname());

        // 다음 단어의 시작 문자 공지
        if (lastWord != null) {
            broadcast("다음 단어는 '" + lastWord.charAt(lastWord.length() - 1) + "'로 시작해야 합니다.");
        } else {
            broadcast("첫 번째 단어는 '" + startingLetter + "'로 시작해야 합니다.");
        }

        // 현재 플레이어에게 턴 시작 알림
        currentPlayer.sendMessage("당신의 차례입니다! 단어를 입력하세요.");

        // 남은 시간 업데이트 및 클라이언트에게 전송
        sendGameStatus();

        startTimerForPlayer(currentPlayer); // 제한 시간 설정
    }

    /**
     * 현재 플레이어의 제한 시간 설정
     * @param currentPlayer 제한 시간을 설정할 플레이어
     */
    private void startTimerForPlayer(ClientHandler_test currentPlayer) {
        // 타이머를 설정하여 1초마다 남은 시간 전송
        timeUpdateTask = scheduler.scheduleAtFixedRate(() -> {
            synchronized (Server_test.this) {
                int timeLeft = remainingTime.decrementAndGet();
                if (timeLeft >= 0) {
                    // 남은 시간 업데이트 및 클라이언트에게 전송
                    sendGameStatus();
                }
                if (timeLeft <= 0) {
                    // 제한 시간 초과 처리
                    broadcast(currentPlayer.getNickname() + " 님이 시간을 초과했습니다! 다음 차례로 넘어갑니다.");
                    if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
                        timeUpdateTask.cancel(true); // 타이머 취소
                    }
                    moveToNextPlayer(); // 다음 플레이어로 이동
                }
            }
        }, 1, 1, TimeUnit.SECONDS); // 1초마다 실행
    }

    /**
     * 게임 상태를 클라이언트에게 전송하는 메소드
     */
    private void sendGameStatus() {
        String statusMessage = "STATUS_UPDATE|TIME:" + remainingTime.get() + "|TURNS:" + remainingTurns;
        for (ClientHandler_test client : clients) {
            client.sendMessage(statusMessage);
        }
    }

    /**
     * 다음 플레이어로 이동
     */
    private synchronized void moveToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size(); // 다음 플레이어 계산
        if (currentPlayerIndex == 0) {
            remainingTurns--; // 모든 플레이어가 턴을 완료하면 턴 감소
            sendGameStatus(); // 남은 턴 수 업데이트
        }
        nextTurn(); // 다음 턴 시작
    }

    /**
     * 게임 종료
     */
    public synchronized void endGame() {
        remainingTurns = 0;
        sendGameStatus(); // 남은 턴 수를 0으로 업데이트하여 클라이언트에게 전송

        broadcast("게임이 종료되었습니다!");

        // 클라이언트들을 점수 순서대로 정렬
        List<ClientHandler_test> sortedClients = new ArrayList<>(clients);
        sortedClients.sort(Comparator.comparingInt(ClientHandler_test::getScore).reversed());

        // 전체 결과 메시지 생성
        StringBuilder finalResults = new StringBuilder("최종 결과:\n");
        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            finalResults.append(client.getNickname() + " : " + client.getScore() + "점 / " + (rank + 1) + "위\n");
        }

        // 각 클라이언트에게 개인 메시지 전송
        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            StringBuilder personalMessage = new StringBuilder();
            personalMessage.append(finalResults.toString());
            personalMessage.append("당신의 최종 점수: " + client.getScore() + "점, 순위: " + (rank + 1) + "위\n");

            if (rank == 0) {
                personalMessage.append("축하드립니다! 우승입니다!\n");
            }

            client.sendMessage(personalMessage.toString());
        }

        // 서버 종료 메시지 전송
        for (ClientHandler_test client : clients) {
            client.sendMessage("SERVER_SHUTDOWN");
        }

        // 스케줄러 종료 및 상태 초기화
        if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
            timeUpdateTask.cancel(true);
        }
        scheduler.shutdown();
        gameStarted = false;
        isRunning = false;

        // 모든 클라이언트의 연결 종료
        for (ClientHandler_test client : clients) {
            client.closeConnection();
        }

        System.out.println("게임이 종료되었습니다. 서버를 종료합니다.");
    }

    /**
     * 클라이언트로부터 단어를 처리
     * @param word 클라이언트가 입력한 단어
     * @param player 현재 단어를 입력한 플레이어
     */
    public synchronized void processWord(String word, ClientHandler_test player) {
        // 현재 턴의 플레이어가 아니면 처리 불가
        if (clients.indexOf(player) != currentPlayerIndex) {
            player.sendMessage("현재 당신의 차례가 아닙니다.");
            return;
        }

        // "end game" 명령어 처리
        if (word.equalsIgnoreCase("end game")) {
            endGame();
            return;
        }

        // 타이머 취소
        if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
            timeUpdateTask.cancel(true);
        }

        // 남은 시간을 초기화
        remainingTime.set(TURN_TIME_LIMIT);

        // 단어 유효성 검사 및 점수 계산
        String response = (lastWord == null)
                ? gameScore.processFirstWord(word, startingLetter)
                : gameScore.processNextWord(word, lastWord);

        if (response.startsWith("유효한 단어")) {
            lastWord = word; // 마지막 단어 갱신
            int points = Integer.parseInt(response.split(": ")[1]);
            player.addScore(points);

            // 누적 점수를 포함한 메시지 생성
            response += ". 누적 점수: " + player.getScore();
        }

        player.sendMessage(response);

        // 플레이어가 입력한 단어를 모든 플레이어에게 방송
        broadcast(player.getNickname() + ": " + word);

        // 남은 시간 업데이트 및 클라이언트에게 전송
        sendGameStatus();

        moveToNextPlayer(); // 다음 플레이어로 이동
    }

    /**
     * 현재 상태 업데이트 및 게임 시작 확인
     */
    public synchronized void updateClientStates() {
        List<String> nicknames = new ArrayList<>();

        for (ClientHandler_test client : clients) {
            nicknames.add(client.getNickname());
        }

        System.out.println("현재 연결된 클라이언트 닉네임 목록: " + nicknames);

        int count = 0;
        for (String nickname : nicknames) {
            if (nickname != null) {
                count++;
            }
        }

        boolean allReady = count == MAX_PLAYERS;

        for (ClientHandler_test client : clients) {
            client.sendMessage(allReady ? "게임에 참여 중입니다!" : "대기 중입니다.");
        }

        if (allReady && !gameStarted) {
            startGame();
        }
    }

    /**
     * 서버 실행
     */
    public void startServer() {
        System.out.println("서버가 시작되었습니다. 포트: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();

                synchronized (this) {
                    if (clients.size() >= MAX_PLAYERS) {
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println("현재 게임이 진행 중입니다. 접속할 수 없습니다.");
                        clientSocket.close();
                        continue;
                    }

                    ClientHandler_test clientHandler = new ClientHandler_test(clientSocket, this);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                    updateClientStates();
                }
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        } finally {
            shutdownServer();
        }
    }

    /**
     * 서버 종료
     */
    private void shutdownServer() {
        broadcast("서버가 종료됩니다.");
        scheduler.shutdown();
        for (ClientHandler_test client : clients) {
            client.closeConnection();
        }
    }
}
