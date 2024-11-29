package word_chain.server;

import word_chain.gameLogic.GameScore;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server_test 클래스
 * - 끝말잇기 게임의 서버 역할 수행
 * - 클라이언트 관리, 게임 진행, 제한 시간 관리, 점수 계산 등의 로직 포함
 */
public class Server_test {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_TURNS = 5;
    private static final int TURN_TIME_LIMIT = 30;

    private final List<ClientHandler_test> clients = Collections.synchronizedList(new ArrayList<>());
    private final GameScore gameScore = new GameScore();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private boolean isRunning = true;
    private boolean gameStarted = false;
    private int currentPlayerIndex = 0;
    private int remainingTurns = MAX_TURNS;
    private AtomicInteger remainingTime = new AtomicInteger(TURN_TIME_LIMIT);
    private String lastWord = null;
    private char startingLetter;
    private ScheduledFuture<?> timeUpdateTask;

    public static void main(String[] args) {
        new Server_test().startServer();
    }

    public synchronized void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.sendMessage(message);
            }
        }
    }

    public synchronized void broadcastParticipants() {
        StringBuilder participantsMessage = new StringBuilder("PARTICIPANTS_UPDATE|");
        synchronized (clients) {
            int count = 0;
            for (ClientHandler_test client : clients) {
                String nickname = client.getNickname();
                if (nickname != null && !nickname.trim().isEmpty()) {
                    participantsMessage.append(nickname).append(",");
                    count++;
                }
            }
            if (count > 0) {
                participantsMessage.setLength(participantsMessage.length() - 1); // 마지막 쉼표 제거
            } else {
                participantsMessage.append("-");
            }
            broadcast(participantsMessage.toString());
        }
    }

    public synchronized void removeClient(ClientHandler_test client) {
        synchronized (clients) {
            int removedIndex = clients.indexOf(client);
            clients.remove(client);
            client.sendMessage("당신은 게임에서 나갔습니다.");
            broadcast(client.getNickname() + " 님이 나갔습니다.");
            System.out.println("클라이언트 연결 종료: 닉네임 = " + client.getNickname());

            if (clients.isEmpty()) {
                endGame();
                return;
            }

            if (removedIndex == currentPlayerIndex) {
                currentPlayerIndex %= clients.size();
                nextTurn();
            } else if (removedIndex < currentPlayerIndex) {
                currentPlayerIndex--;
            }

            sendGameStatus();
        }
    }

    public synchronized void startGame() {
        if (gameStarted) return;
        gameStarted = true;

        // 5초 카운트다운
        new Thread(() -> {
            for (int countdown = 5; countdown > 0; countdown--) {
                broadcast("게임이 " + countdown + "초 후에 시작됩니다!");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("카운트다운 중 인터럽트 발생: " + e.getMessage());
                }
            }
            startingLetter = (char) ('a' + new Random().nextInt(26));
            broadcast("첫 번째 단어는 '" + startingLetter + "'로 시작해야 합니다.");
            nextTurn();
        }).start();
    }

    private synchronized void nextTurn() {
        if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
            timeUpdateTask.cancel(true);
        }

        if (remainingTurns == 0) {
            endGame();
            return;
        }

        synchronized (clients) {
            if (clients.isEmpty()) {
                broadcast("모든 플레이어가 나갔습니다. 게임을 종료합니다.");
                endGame();
                return;
            }

            remainingTime.set(TURN_TIME_LIMIT);
            ClientHandler_test currentPlayer = clients.get(currentPlayerIndex);

            broadcast("현재 차례: " + currentPlayer.getNickname());

            if (lastWord != null) {
                broadcast("다음 단어는 '" + lastWord.charAt(lastWord.length() - 1) + "'로 시작해야 합니다.");
            } else {
                broadcast("첫 번째 단어는 '" + startingLetter + "'로 시작해야 합니다.");
            }

            currentPlayer.sendMessage("당신의 차례입니다! 단어를 입력하세요.");
            sendGameStatus();
            startTimerForPlayer(currentPlayer);
        }
    }

    private void startTimerForPlayer(ClientHandler_test currentPlayer) {
        timeUpdateTask = scheduler.scheduleAtFixedRate(() -> {
            synchronized (Server_test.this) {
                int timeLeft = remainingTime.decrementAndGet();
                if (timeLeft >= 0) {
                    sendGameStatus();
                }
                if (timeLeft <= 0) {
                    broadcast(currentPlayer.getNickname() + " 님이 시간을 초과했습니다! 다음 차례로 넘어갑니다.");
                    if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
                        timeUpdateTask.cancel(true);
                    }
                    moveToNextPlayer();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private synchronized void sendGameStatus() {
        String statusMessage = "STATUS_UPDATE|TIME:" + remainingTime.get() + "|TURNS:" + remainingTurns;
        broadcast(statusMessage);
    }

    private synchronized void moveToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
        if (currentPlayerIndex == 0) {
            remainingTurns--;
            sendGameStatus();
        }
        nextTurn();
    }

    public synchronized void processWord(String word, ClientHandler_test player) {
        synchronized (clients) {
            if (clients.indexOf(player) != currentPlayerIndex) {
                player.sendMessage("현재 당신의 차례가 아닙니다.");
                return;
            }

            if (word.equalsIgnoreCase("end game")) {
                endGame();
                return;
            }

            if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
                timeUpdateTask.cancel(true);
            }

            remainingTime.set(TURN_TIME_LIMIT);

            String response = (lastWord == null)
                    ? gameScore.processFirstWord(word, startingLetter)
                    : gameScore.processNextWord(word, lastWord);

            if (response.startsWith("유효한 단어")) {
                lastWord = word;
                int points = Integer.parseInt(response.split(": ")[1]);
                player.addScore(points);
                response += ". 누적 점수: " + player.getScore();
            }

            player.sendMessage(response);
            broadcast(player.getNickname() + ": " + word);
            sendGameStatus();
            moveToNextPlayer();
        }
    }

    public synchronized void updateClientStates() {
        List<String> nicknames = new ArrayList<>();
        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                nicknames.add(client.getNickname());
            }
        }

        System.out.println("현재 연결된 클라이언트 닉네임 목록: " + nicknames);

        long validCount = nicknames.stream().filter(n -> n != null && !n.trim().isEmpty()).count();
        boolean allReady = validCount == MAX_PLAYERS;

        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.sendMessage(allReady ? "게임에 참여 중입니다!" : "대기 중입니다.");
            }
        }

        if (allReady && !gameStarted) {
            startGame();
        }
    }

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

    private synchronized void shutdownServer() {
        broadcast("서버가 종료됩니다.");
        scheduler.shutdown();
        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.closeConnection();
            }
        }
        System.out.println("서버가 종료되었습니다.");
    }

    public synchronized void endGame() {
        remainingTurns = 0;
        sendGameStatus();
        broadcast("게임이 종료되었습니다!");

        List<ClientHandler_test> sortedClients;
        synchronized (clients) {
            sortedClients = new ArrayList<>(clients);
        }
        sortedClients.sort(Comparator.comparingInt(ClientHandler_test::getScore).reversed());

        StringBuilder finalResults = new StringBuilder("최종 결과:\n");
        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            finalResults.append(client.getNickname())
                        .append(" : ")
                        .append(client.getScore())
                        .append("점 / ")
                        .append(rank + 1)
                        .append("위\n");
        }

        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            StringBuilder personalMessage = new StringBuilder(finalResults.toString());
            personalMessage.append("당신의 최종 점수: ")
                           .append(client.getScore())
                           .append("점, 순위: ")
                           .append(rank + 1)
                           .append("위\n");

            if (rank == 0) {
                personalMessage.append("축하드립니다! 우승입니다!\n");
            }

            client.sendMessage(personalMessage.toString());
        }

        broadcast("SERVER_SHUTDOWN");
        if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
            timeUpdateTask.cancel(true);
        }
        scheduler.shutdown();
        gameStarted = false;
        isRunning = false;

        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.closeConnection();
            }
            clients.clear();
        }

        System.out.println("게임이 종료되었습니다. 서버를 종료합니다.");
    }
}
